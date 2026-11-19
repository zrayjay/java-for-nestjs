package dev.javafornestjs.capstone.system;

import dev.javafornestjs.capstone.core.RequestFailure;
import dev.javafornestjs.capstone.security.OrderPermission;
import dev.javafornestjs.capstone.security.OrderScope;
import dev.javafornestjs.capstone.security.SessionIdentity;
import dev.javafornestjs.capstone.system.AuthorizationMapper.RoleGrant;
import dev.javafornestjs.capstone.system.AuthorizationMapper.RoleRow;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.javafornestjs.capstone.core.RequestFailure.Code.NOT_FOUND;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.RESOURCE_CONFLICT;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.VALIDATION_ERROR;

@Service
public class AuthorizationService {
    private final AccountsService access;
    private final AccountsMapper accounts;
    private final AuthorizationMapper authorization;

    AuthorizationService(AccountsService access, AccountsMapper accounts, AuthorizationMapper authorization) {
        this.access = access;
        this.accounts = accounts;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public RolePage roles(SessionIdentity actor, int page, int size) {
        access.requireAdministrator(actor, false);
        if (page < 1 || size < 1 || size > 100) {
            throw new RequestFailure(VALIDATION_ERROR, "Page must be positive and size must be between 1 and 100");
        }
        var roles = authorization.pageRoles(size, (long) (page - 1) * size);
        var grants = grantsByRole(roles.stream().map(RoleRow::id).toList());
        var items = roles.stream().map(role -> view(role, grants.getOrDefault(role.id(), List.of()))).toList();
        return new RolePage(items, page, size, authorization.countRoles());
    }

    @Transactional
    public Role create(SessionIdentity actor, String code, String name) {
        access.requireAdministrator(actor, true);
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{2,39}")) {
            throw new RequestFailure(VALIDATION_ERROR, "Invalid role code");
        }
        try {
            authorization.insertRole(code, requireName(name));
        } catch (DuplicateKeyException duplicate) {
            throw new RequestFailure(RESOURCE_CONFLICT, "Role code already exists");
        }
        return view(authorization.roleByCode(code), List.of());
    }

    @Transactional
    public Role rename(SessionIdentity actor, long id, String name) {
        access.requireAdministrator(actor, true);
        requireRole(id);
        authorization.renameRole(id, requireName(name));
        return view(requireRole(id), grantsByRole(List.of(id)).getOrDefault(id, List.of()));
    }

    @Transactional
    public void replaceGrants(SessionIdentity actor, long roleId, List<Grant> input) {
        access.requireAdministrator(actor, true);
        requireRole(roleId);
        if (input == null) {
            throw new RequestFailure(VALIDATION_ERROR, "Provide a grant list");
        }
        var grants = new EnumMap<OrderPermission, OrderScope>(OrderPermission.class);
        for (var requested : input) {
            var permission = requested == null ? null : CurrentOrderAccess.permission(requested.permission());
            var scope = requested == null ? null : parseScope(requested.scope());
            if (permission == null || scope == null || !permission.supports(scope)
                    || grants.putIfAbsent(permission, scope) != null) {
                throw new RequestFailure(VALIDATION_ERROR, "Invalid or duplicate role grant");
            }
        }
        authorization.deleteGrants(roleId);
        grants.forEach((permission, scope) -> authorization.insertGrant(roleId, permission.code(), scope.name()));
    }

    @Transactional
    public void replaceRoles(SessionIdentity actor, long userId, List<Long> roleIds) {
        access.requireAdministrator(actor, true);
        AccountsService.requireAccount(accounts.lock(userId));
        if (roleIds == null || roleIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(roleIds).size() != roleIds.size()) {
            throw new RequestFailure(VALIDATION_ERROR, "Provide a role list without duplicates");
        }
        roleIds.forEach(this::requireRole);
        authorization.deleteUserRoles(userId);
        roleIds.forEach(id -> authorization.insertUserRole(userId, id));
    }

    @Transactional
    public void changeAdministrator(SessionIdentity actor, long userId, boolean enabled) {
        access.requireAdministrator(actor, true);
        var target = AccountsService.requireAccount(accounts.lock(userId));
        if (!enabled && target.platformAdmin() && target.enabled() && accounts.countActiveAdministrators() <= 1) {
            throw new RequestFailure(RESOURCE_CONFLICT, "At least one active administrator is required");
        }
        authorization.changeAdministrator(userId, enabled);
    }

    private Map<Long, List<Grant>> grantsByRole(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return authorization.grantsForRoles(ids).stream().collect(Collectors.groupingBy(RoleGrant::roleId,
                Collectors.mapping(row -> new Grant(row.permission(), row.scope()), Collectors.toList())));
    }

    private RoleRow requireRole(long id) {
        var role = authorization.findRole(id);
        if (role == null) {
            throw new RequestFailure(NOT_FOUND, "Role not found");
        }
        return role;
    }

    private static Role view(RoleRow role, List<Grant> grants) {
        return new Role(Long.toString(role.id()), role.code(), role.name(), grants);
    }

    private static String requireName(String value) {
        String name = value == null ? "" : value.strip();
        if (name.isEmpty() || name.length() > 100) {
            throw new RequestFailure(VALIDATION_ERROR, "Invalid role name");
        }
        return name;
    }

    private static OrderScope parseScope(String value) {
        return switch (value == null ? "" : value) {
            case "OWN" -> OrderScope.OWN;
            case "DEPARTMENT" -> OrderScope.DEPARTMENT;
            case "ALL" -> OrderScope.ALL;
            default -> null;
        };
    }

    public record Grant(String permission, String scope) {
    }

    public record Role(String id, String code, String name, List<Grant> grants) {
    }

    public record RolePage(List<Role> items, int page, int size, long total) {
    }
}
