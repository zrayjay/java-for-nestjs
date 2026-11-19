package dev.javafornestjs.capstone.security;

import java.util.Map;

public record OrderAccess(long userId, long departmentId, Map<OrderPermission, OrderScope> grants) {
    public OrderAccess {
        grants = Map.copyOf(grants);
    }

    public boolean allows(OrderPermission permission, long ownerId, long orderDepartment) {
        OrderScope scope = grants.get(permission);
        return scope != null && scope.includes(userId, departmentId, ownerId, orderDepartment);
    }
}
