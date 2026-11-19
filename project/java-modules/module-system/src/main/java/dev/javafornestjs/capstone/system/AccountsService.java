package dev.javafornestjs.capstone.system;

import dev.javafornestjs.capstone.core.RequestFailure;
import dev.javafornestjs.capstone.security.SessionIdentity;
import dev.javafornestjs.capstone.system.AccountsMapper.Account;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.javafornestjs.capstone.core.RequestFailure.Code.FORBIDDEN;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.NOT_FOUND;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.RESOURCE_CONFLICT;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.UNAUTHENTICATED;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.VALIDATION_ERROR;

@Service
public class AccountsService {
    private final AccountsMapper accounts;
    private final AccountSessions sessions;
    private final PasswordEncoder passwords;
    private final Clock clock;

    AccountsService(AccountsMapper accounts, AccountSessions sessions, PasswordEncoder passwords, Clock clock) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwords = passwords;
        this.clock = clock;
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
        Account account = authenticatedAccount(actor, accounts.find(actor.userId()));
        return new Me(Long.toString(account.id()), account.username(), account.displayName(),
                Long.toString(account.departmentId()), account.platformAdmin() ? List.of("system:admin") : List.of());
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
        var items = accounts.page(size, (long) (page - 1) * size).stream().map(AccountsService::view).toList();
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

    private void requireAdministrator(SessionIdentity actor, boolean mutation) {
        if (mutation) {
            accounts.lockAdministration();
        }
        var account = authenticatedAccount(actor,
                mutation ? accounts.lock(actor.userId()) : accounts.find(actor.userId()));
        if (!account.platformAdmin()) {
            throw new RequestFailure(FORBIDDEN, "Platform administrator required");
        }
    }

    private Account authenticatedAccount(SessionIdentity actor, Account account) {
        if (account == null || !account.enabled() || account.credentialVersion() != actor.credentialVersion()
                || !sessions.contains(actor, clock.instant())) {
            throw new RequestFailure(UNAUTHENTICATED, "Authentication required");
        }
        return account;
    }

    private static Account requireAccount(Account account) {
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

    private static User view(Account account) {
        return new User(Long.toString(account.id()), account.username(), account.displayName(),
                Long.toString(account.departmentId()), account.status(), List.of(), account.platformAdmin());
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
