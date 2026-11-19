package dev.javafornestjs.capstone.core;

public interface AuditTrail {
    void record(long actorId, Event event, long resourceId);

    void recordCommittedReplay(long actorId);

    enum Resource {
        USER, ROLE, MENU, ORDER
    }

    enum Event {
        LOGIN(Resource.USER),
        REFRESH_REPLAY(Resource.USER),
        LOGOUT(Resource.USER),
        USER_CREATED(Resource.USER),
        USER_UPDATED(Resource.USER),
        USER_DISABLED(Resource.USER),
        PASSWORD_CHANGED(Resource.USER),
        ROLES_ASSIGNED(Resource.USER),
        ADMIN_GRANTED(Resource.USER),
        ADMIN_REVOKED(Resource.USER),
        ROLE_CREATED(Resource.ROLE),
        ROLE_RENAMED(Resource.ROLE),
        GRANTS_REPLACED(Resource.ROLE),
        MENU_CREATED(Resource.MENU),
        MENU_UPDATED(Resource.MENU),
        MENU_DELETED(Resource.MENU),
        ORDER_CREATED(Resource.ORDER),
        ORDER_EDITED(Resource.ORDER),
        ORDER_SUBMITTED(Resource.ORDER),
        ORDER_APPROVED(Resource.ORDER),
        ORDER_CANCELLED(Resource.ORDER);

        private final Resource resource;

        Event(Resource resource) {
            this.resource = resource;
        }

        public Resource resource() {
            return resource;
        }
    }
}
