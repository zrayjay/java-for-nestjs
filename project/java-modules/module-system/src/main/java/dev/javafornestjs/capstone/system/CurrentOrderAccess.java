package dev.javafornestjs.capstone.system;

import dev.javafornestjs.capstone.security.OrderAccess;
import dev.javafornestjs.capstone.security.OrderAccessLookup;
import dev.javafornestjs.capstone.security.OrderPermission;
import dev.javafornestjs.capstone.security.OrderScope;
import dev.javafornestjs.capstone.security.SessionIdentity;
import dev.javafornestjs.capstone.system.AccountsMapper.Account;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class CurrentOrderAccess implements OrderAccessLookup {
    private static final Map<String, OrderPermission> PERMISSIONS = Arrays.stream(OrderPermission.values())
            .collect(Collectors.toUnmodifiableMap(OrderPermission::code, permission -> permission));

    private final AccountSessions accounts;
    private final AuthorizationMapper authorization;

    CurrentOrderAccess(AccountSessions accounts, AuthorizationMapper authorization) {
        this.accounts = accounts;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    @Override
    public OrderAccess forSession(SessionIdentity identity) {
        return forAccount(accounts.currentAccount(identity, false));
    }

    OrderAccess forAccount(Account account) {
        var grants = new EnumMap<OrderPermission, OrderScope>(OrderPermission.class);
        for (var grant : authorization.grantsForUser(account.id())) {
            var permission = permission(grant.permission());
            var scope = OrderScope.valueOf(grant.scope());
            if (permission != null && permission.supports(scope)) {
                grants.merge(permission, scope, OrderScope::union);
            }
        }
        if (account.platformAdmin()) {
            grants.put(OrderPermission.READ, OrderScope.ALL);
        }
        return new OrderAccess(account.id(), account.departmentId(), grants);
    }

    List<String> capabilities(Account account) {
        var codes = new ArrayList<>(forAccount(account).grants().keySet().stream()
                .map(OrderPermission::code).sorted().toList());
        if (account.platformAdmin()) {
            codes.add("system:admin");
        }
        return List.copyOf(codes);
    }

    static OrderPermission permission(String code) {
        return code == null ? null : PERMISSIONS.get(code);
    }
}
