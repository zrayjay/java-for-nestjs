package dev.javafornestjs.capstone.security;

public enum OrderScope {
    OWN, DEPARTMENT, ALL;

    public OrderScope union(OrderScope other) {
        return compareTo(other) >= 0 ? this : other;
    }

    public boolean includes(long userId, long departmentId, long ownerId, long orderDepartment) {
        return switch (this) {
            case OWN -> userId == ownerId && departmentId == orderDepartment;
            case DEPARTMENT -> departmentId == orderDepartment;
            case ALL -> true;
        };
    }
}
