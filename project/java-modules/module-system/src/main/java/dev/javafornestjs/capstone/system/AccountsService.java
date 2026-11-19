package dev.javafornestjs.capstone.system;

import dev.javafornestjs.capstone.core.RequestFailure;
import dev.javafornestjs.capstone.security.SessionIdentity;
import dev.javafornestjs.capstone.system.AccountsMapper.Account;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.javafornestjs.capstone.core.RequestFailure.Code.FORBIDDEN;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.NOT_FOUND;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.RESOURCE_CONFLICT;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.VALIDATION_ERROR;

@Service
public class AccountsService {
    private final AccountsMapper accounts;
    private final AccountSessions sessions;
    private final AuthorizationMapper authorization;
    private final CurrentOrderAccess access;
    private final PasswordEncoder passwords;

    AccountsService(AccountsMapper accounts, AccountSessions sessions, AuthorizationMapper authorization,
            CurrentOrderAccess access, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.authorization = authorization;
        this.access = access;
        this.passwords = passwords;
    }

    @Transactional
    public void bootstrap(String username, String password) {
        accounts.lockAdministration();
        if (accounts.count() > 0) {
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Configure the initial administrator for an empty database");
        }
        AccountInputs.validateUsername(username);
        AccountInputs.validatePassword(password);
        accounts.insert(username, "Administrator", accounts.initialDepartment(), passwords.encode(password), true);
    }

    @Transactional(readOnly = true)
    public Me me(SessionIdentity actor) {
        Account account = sessions.currentAccount(actor, false);
        return new Me(Long.toString(account.id()), account.username(), account.displayName(),
                Long.toString(account.departmentId()), access.capabilities(account));
    }

    @Transactional(readOnly = true)
    public List<Department> departments(SessionIdentity actor) {
        requireAdministrator(actor, false);
        return accounts.departments().stream()
                .map(department -> new Department(Long.toString(department.id()), department.code(), department.name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page users(SessionIdentity actor, int page, int size) {
        requireAdministrator(actor, false);
        if (page < 1 || size < 1 || size > 100) {
            throw new RequestFailure(VALIDATION_ERROR, "Page must be positive and size must be between 1 and 100");
        }
        var records = accounts.page(size, (long) (page - 1) * size);
        var roles = rolesByUser(records.stream().map(Account::id).toList());
        var items = records.stream().map(account -> view(account, roles.getOrDefault(account.id(), List.of()))).toList();
        return new Page(items, page, size, accounts.count());
    }

    @Transactional(readOnly = true)
    public User user(SessionIdentity actor, long id) {
        requireAdministrator(actor, false);
        return view(requireAccount(accounts.find(id)));
    }

    @Transactional
    public User create(SessionIdentity actor, String username, String name, long department, String password) {
        requireAdministrator(actor, true);
        AccountInputs.validateUsername(username);
        AccountInputs.validatePassword(password);
        var displayName = displayName(name);
        checkDepartment(department);
        try {
            accounts.insert(username, displayName, department, passwords.encode(password), false);
        } catch (DuplicateKeyException duplicate) {
            throw new RequestFailure(RESOURCE_CONFLICT, "Username already exists");
        }
        return view(accounts.byUsername(username));
    }

    @Transactional
    public User update(SessionIdentity actor, long id, String displayName, Long department, String status) {
        requireAdministrator(actor, true);
        Account account = requireAccount(accounts.lock(id));
        if (displayName == null && department == null && status == null) {
            throw new RequestFailure(VALIDATION_ERROR, "Provide at least one field to update");
        }
        return updateAccount(account, displayName, department, status);
    }

    @Transactional
    public void disable(SessionIdentity actor, long id) {
        requireAdministrator(actor, true);
        updateAccount(requireAccount(accounts.lock(id)), null, null, "DISABLED");
    }

    @Transactional
    public void changePassword(SessionIdentity actor, long id, String password) {
        requireAdministrator(actor, true);
        requireAccount(accounts.lock(id));
        AccountInputs.validatePassword(password);
        accounts.replacePassword(id, passwords.encode(password));
    }

    private User updateAccount(Account account, String name, Long department, String requestedStatus) {
        String status = requestedStatus == null ? account.status() : requestedStatus;
        if (!status.equals("ACTIVE") && !status.equals("DISABLED")) {
            throw new RequestFailure(VALIDATION_ERROR, "Unsupported user status");
        }
        if (account.enabled() && account.platformAdmin() && status.equals("DISABLED")
                && accounts.countActiveAdministrators() <= 1) {
            throw new RequestFailure(RESOURCE_CONFLICT, "At least one active administrator is required");
        }
        long departmentId = department == null ? account.departmentId() : department;
        if (department != null) {
            checkDepartment(departmentId);
        }
        accounts.update(account.id(), name == null ? account.displayName() : displayName(name), departmentId, status);
        return view(accounts.find(account.id()));
    }

    void requireAdministrator(SessionIdentity actor, boolean mutation) {
        if (mutation) {
            accounts.lockAdministration();
        }
        var account = sessions.currentAccount(actor, mutation);
        if (!account.platformAdmin()) {
            throw new RequestFailure(FORBIDDEN, "Platform administrator required");
        }
    }

    static Account requireAccount(Account account) {
        if (account == null) {
            throw new RequestFailure(NOT_FOUND, "User not found");
        }
        return account;
    }

    private void checkDepartment(long id) {
        if (accounts.countDepartment(id) == 0) {
            throw new RequestFailure(VALIDATION_ERROR, "Unknown department");
        }
    }

    private static String displayName(String value) {
        String name = value == null ? "" : value.strip();
        if (name.isEmpty() || name.length() > 100) {
            throw new RequestFailure(VALIDATION_ERROR, "Invalid display name");
        }
        return name;
    }

    private Map<Long, List<String>> rolesByUser(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return authorization.rolesForUsers(ids).stream().collect(Collectors.groupingBy(
                AuthorizationMapper.UserRole::userId,
                Collectors.mapping(row -> Long.toString(row.roleId()), Collectors.toList())));
    }

    private User view(Account account) {
        return view(account, rolesByUser(List.of(account.id())).getOrDefault(account.id(), List.of()));
    }

    private static User view(Account account, List<String> roleIds) {
        return new User(Long.toString(account.id()), account.username(), account.displayName(),
                Long.toString(account.departmentId()), account.status(), roleIds, account.platformAdmin());
    }

    public record Me(String id, String username, String displayName, String departmentId, List<String> permissions) {
    }

    public record User(String id, String username, String displayName, String departmentId, String status,
            List<String> roleIds, boolean platformAdmin) {
    }

    public record Page(List<User> items, int page, int size, long total) {
    }

    public record Department(String id, String code, String name) {
    }
}
