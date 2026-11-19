package dev.javafornestjs.capstone.system;

import dev.javafornestjs.capstone.core.AuditTrail;
import dev.javafornestjs.capstone.core.AuditTrail.Event;
import dev.javafornestjs.capstone.core.RequestFailure;
import dev.javafornestjs.capstone.security.SessionIdentity;
import dev.javafornestjs.capstone.system.AuthorizationMapper.MenuRow;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static dev.javafornestjs.capstone.core.RequestFailure.Code.NOT_FOUND;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.RESOURCE_CONFLICT;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.VALIDATION_ERROR;

@Service
public class MenuService {
    private static final Map<String, String> ROUTE_PERMISSIONS = Map.of(
            "orders", "orders:read",
            "users", "system:admin",
            "roles", "system:admin",
            "menus", "system:admin",
            "audit", "system:admin");

    private final AccountsService accounts;
    private final AccountSessions sessions;
    private final CurrentOrderAccess access;
    private final AuthorizationMapper menus;
    private final MenuCatalog catalog;
    private final AuditTrail audit;

    MenuService(AccountsService accounts, AccountSessions sessions, CurrentOrderAccess access,
            AuthorizationMapper menus, MenuCatalog catalog, AuditTrail audit) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.access = access;
        this.menus = menus;
        this.catalog = catalog;
        this.audit = audit;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Node> visible(SessionIdentity actor) {
        var capabilities = Set.copyOf(access.capabilities(sessions.currentAccount(actor, false)));
        var children = catalog.current().stream().collect(Collectors.groupingBy(
                row -> row.parentId() == null ? 0L : row.parentId()));
        return visibleChildren(0, children, capabilities);
    }

    @Transactional(readOnly = true)
    public List<Menu> all(SessionIdentity actor) {
        accounts.requireAdministrator(actor, false);
        return menus.menus().stream().map(MenuService::view).toList();
    }

    @Transactional
    public Menu create(SessionIdentity actor, MenuChange change) {
        accounts.requireAdministrator(actor, true);
        String label = validateChange(null, change);
        try {
            menus.insertMenu(change.parentId(), label, change.routeKey(), change.permission(), change.sortOrder());
        } catch (DuplicateKeyException duplicate) {
            throw new RequestFailure(RESOURCE_CONFLICT, "A menu already uses this route");
        }
        long id = menus.lastInsertId();
        menus.rotateMenuRevision();
        audit.record(actor.userId(), Event.MENU_CREATED, id);
        return view(requireMenu(id));
    }

    @Transactional
    public Menu update(SessionIdentity actor, long id, MenuPatch patch) {
        accounts.requireAdministrator(actor, true);
        var existing = requireMenu(id);
        var change = new MenuChange(patch.parentId().orElse(existing.parentId()),
                patch.label().orElse(existing.label()), patch.routeKey().orElse(existing.routeKey()),
                patch.permission().orElse(existing.permission()), patch.sortOrder().orElse(existing.sortOrder()));
        String label = validateChange(id, change);
        try {
            menus.updateMenu(id, change.parentId(), label, change.routeKey(), change.permission(), change.sortOrder());
        } catch (DuplicateKeyException duplicate) {
            throw new RequestFailure(RESOURCE_CONFLICT, "A menu already uses this route");
        }
        menus.rotateMenuRevision();
        audit.record(actor.userId(), Event.MENU_UPDATED, id);
        return view(requireMenu(id));
    }

    @Transactional
    public void delete(SessionIdentity actor, long id) {
        accounts.requireAdministrator(actor, true);
        requireMenu(id);
        if (menus.childCount(id) != 0) {
            throw new RequestFailure(RESOURCE_CONFLICT, "Delete child menus first");
        }
        menus.deleteMenu(id);
        menus.rotateMenuRevision();
        audit.record(actor.userId(), Event.MENU_DELETED, id);
    }

    private String validateChange(Long id, MenuChange change) {
        String label = change.label() == null ? "" : change.label().strip();
        if (label.isEmpty() || label.length() > 100 || change.sortOrder() == null || change.sortOrder() < 0) {
            throw new RequestFailure(VALIDATION_ERROR, "Invalid menu label or sort order");
        }
        if (change.routeKey() != null) {
            String required = ROUTE_PERMISSIONS.get(change.routeKey());
            if (required == null || !required.equals(change.permission())) {
                throw new RequestFailure(VALIDATION_ERROR, "Invalid route or required permission");
            }
        } else if (change.permission() != null && !change.permission().equals("system:admin")
                && CurrentOrderAccess.permission(change.permission()) == null) {
            throw new RequestFailure(VALIDATION_ERROR, "Invalid menu permission");
        }
        var visited = new HashSet<Long>();
        if (id != null) {
            visited.add(id);
        }
        Long ancestor = change.parentId();
        while (ancestor != null) {
            if (!visited.add(ancestor)) {
                throw new RequestFailure(RESOURCE_CONFLICT, "Menu nesting contains a cycle");
            }
            ancestor = requireMenu(ancestor).parentId();
        }
        return label;
    }

    private MenuRow requireMenu(long id) {
        var menu = menus.menu(id);
        if (menu == null) {
            throw new RequestFailure(NOT_FOUND, "Menu not found");
        }
        return menu;
    }

    private static List<Node> visibleChildren(long parentId, Map<Long, List<MenuRow>> children,
            Set<String> capabilities) {
        return children.getOrDefault(parentId, List.of()).stream()
                .filter(menu -> menu.permission() == null || capabilities.contains(menu.permission()))
                .map(menu -> new Node(Long.toString(menu.id()), menu.label(), menu.routeKey(),
                        visibleChildren(menu.id(), children, capabilities)))
                .filter(node -> node.routeKey() != null || !node.children().isEmpty())
                .toList();
    }

    private static Menu view(MenuRow menu) {
        return new Menu(Long.toString(menu.id()), menu.parentId() == null ? null : Long.toString(menu.parentId()),
                menu.label(), menu.routeKey(), menu.permission(), menu.sortOrder());
    }

    public record MenuChange(Long parentId, String label, String routeKey, String permission, Integer sortOrder) {
    }

    public record MenuPatch(Field<Long> parentId, Field<String> label, Field<String> routeKey, Field<String> permission,
            Field<Integer> sortOrder) {
    }

    public record Field<T>(boolean supplied, T value) {
        public static <T> Field<T> omitted() {
            return new Field<>(false, null);
        }

        public static <T> Field<T> supplied(T value) {
            return new Field<>(true, value);
        }

        T orElse(T fallback) {
            return supplied ? value : fallback;
        }
    }

    public record Menu(String id, String parentId, String label, String routeKey, String permission, int sortOrder) {
    }

    public record Node(String id, String label, String routeKey, List<Node> children) {
    }
}
