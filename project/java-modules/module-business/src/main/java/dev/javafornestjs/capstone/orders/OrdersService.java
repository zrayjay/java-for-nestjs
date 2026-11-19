package dev.javafornestjs.capstone.orders;

import dev.javafornestjs.capstone.core.AuditTrail;
import dev.javafornestjs.capstone.core.AuditTrail.Event;
import dev.javafornestjs.capstone.core.RequestFailure;
import dev.javafornestjs.capstone.orders.OrdersMapper.OrderRow;
import dev.javafornestjs.capstone.security.OrderAccess;
import dev.javafornestjs.capstone.security.OrderAccessLookup;
import dev.javafornestjs.capstone.security.OrderPermission;
import dev.javafornestjs.capstone.security.OrderScope;
import dev.javafornestjs.capstone.security.SessionIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.javafornestjs.capstone.core.RequestFailure.Code.FORBIDDEN;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.INVALID_TRANSITION;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.NOT_FOUND;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.SELF_APPROVAL_FORBIDDEN;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.VALIDATION_ERROR;
import static dev.javafornestjs.capstone.core.RequestFailure.Code.VERSION_CONFLICT;

@Service
public class OrdersService {
    private static final Pattern CUSTOMER_EDGE_SPACE = Pattern.compile("^\\s+|\\s+$",
            Pattern.UNICODE_CHARACTER_CLASS);
    private final OrdersMapper orders;
    private final OrderAccessLookup lookup;
    private final AuditTrail audit;
    private final Clock clock;

    OrdersService(OrdersMapper orders, OrderAccessLookup lookup, AuditTrail audit, Clock clock) {
        this.orders = orders;
        this.lookup = lookup;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page list(SessionIdentity identity, String status, int page, int size) {
        var actor = lookup.forSession(identity);
        var scope = require(actor, OrderPermission.READ);
        if (page < 1 || size < 1 || size > 100) {
            throw new RequestFailure(VALIDATION_ERROR, "Page must be positive and size must be between 1 and 100");
        }
        String state = status == null ? null : parseStatus(status).name();
        var items = orders.page(scope, actor.userId(), actor.departmentId(), state, size, (long) (page - 1) * size)
                .stream().map(row -> view(actor, row)).toList();
        return new Page(items, page, size, orders.count(scope, actor.userId(), actor.departmentId(), state));
    }

    @Transactional(readOnly = true)
    public Order detail(SessionIdentity identity, long id) {
        var actor = lookup.forSession(identity);
        return view(actor, find(id, actor, require(actor, OrderPermission.READ)));
    }

    @Transactional(readOnly = true)
    public List<History> history(SessionIdentity identity, long id) {
        var actor = lookup.forSession(identity);
        find(id, actor, require(actor, OrderPermission.READ));
        return orders.history(id).stream().map(row -> new History(Long.toString(row.actorId()), row.action(),
                row.fromStatus(), row.toStatus(), row.version(), Instant.ofEpochMilli(row.occurredAt()))).toList();
    }

    @Transactional
    public Order create(SessionIdentity identity, String name, Integer amount) {
        var actor = lookup.forSession(identity);
        require(actor, OrderPermission.CREATE);
        var input = draft(name, amount);
        long now = clock.millis();
        orders.create(input.customer(), input.amount(), actor.userId(), actor.departmentId(), now);
        long id = orders.lastInsertId();
        orders.append(id, actor.userId(), "CREATE", null, Status.DRAFT.name(), 1, now);
        audit.record(actor.userId(), Event.ORDER_CREATED, id);
        return view(actor, find(id, actor, OrderScope.OWN));
    }

    @Transactional
    public Order edit(SessionIdentity identity, long id, Integer version, String name, Integer amount) {
        return change(identity, id, version, Action.EDIT, name, amount);
    }

    @Transactional
    public Order transition(SessionIdentity identity, long id, Integer version, Action action) {
        if (action == Action.EDIT) {
            throw new IllegalArgumentException("Editing requires draft fields");
        }
        return change(identity, id, version, action, null, null);
    }

    private Order change(SessionIdentity identity, long id, Integer version, Action action, String name,
            Integer amount) {
        var actor = lookup.forSession(identity);
        var scope = require(actor, action.permission);
        var before = find(id, actor, scope);
        boolean owner = isOwner(actor, before);
        var beforeStatus = Status.valueOf(before.status());
        if (action == Action.CANCEL && beforeStatus == Status.DRAFT && !owner) {
            throw new RequestFailure(NOT_FOUND, "Order not found");
        }
        if (action == Action.APPROVE && actor.userId() == before.ownerId()) {
            throw new RequestFailure(SELF_APPROVAL_FORBIDDEN, "A different user must approve this order");
        }
        if (version == null || version < 1) {
            throw new RequestFailure(VALIDATION_ERROR, "Version must be a positive integer");
        }
        if (before.version() != version) {
            throw conflict();
        }
        if (!allowedState(action, beforeStatus, owner)) {
            throw new RequestFailure(INVALID_TRANSITION, "Order does not allow this action");
        }
        var after = action == Action.EDIT ? draft(name, amount)
                : new Draft(before.customerName(), before.totalAmountCents());
        long now = clock.millis();
        int count = orders.update(id, version, before.status(), action.target.name(), after.customer(), after.amount(),
                now, scope, actor.userId(), actor.departmentId());
        if (count != 1) {
            throw conflict();
        }
        orders.append(id, actor.userId(), action.name(), before.status(), action.target.name(), version + 1, now);
        audit.record(actor.userId(), action.event, id);
        return view(actor, find(id, actor, scope));
    }

    private OrderRow find(long id, OrderAccess actor, OrderScope scope) {
        var order = orders.find(id, scope, actor.userId(), actor.departmentId());
        if (order == null) {
            throw new RequestFailure(NOT_FOUND, "Order not found");
        }
        return order;
    }

    private static OrderScope require(OrderAccess actor, OrderPermission permission) {
        var scope = actor.grants().get(permission);
        if (scope == null) {
            throw new RequestFailure(FORBIDDEN, "Order action is not permitted");
        }
        return scope;
    }

    private static Status parseStatus(String status) {
        try {
            return Status.valueOf(status);
        } catch (IllegalArgumentException unknown) {
            throw new RequestFailure(VALIDATION_ERROR, "Unknown order status");
        }
    }

    private static Draft draft(String name, Integer amount) {
        String customer = name == null ? "" : CUSTOMER_EDGE_SPACE.matcher(name).replaceAll("");
        if (customer.isEmpty() || customer.codePointCount(0, customer.length()) > 100
                || amount == null || amount < 1 || amount > 999_999_999) {
            throw new RequestFailure(VALIDATION_ERROR, "Invalid customer name or total amount");
        }
        return new Draft(customer, amount);
    }

    private static RequestFailure conflict() {
        return new RequestFailure(VERSION_CONFLICT, "Order has changed, reload and retry");
    }

    private static boolean isOwner(OrderAccess actor, OrderRow row) {
        return actor.userId() == row.ownerId() && actor.departmentId() == row.departmentId();
    }

    private static boolean allowedState(Action action, Status status, boolean owner) {
        return switch (action) {
            case EDIT, SUBMIT -> status == Status.DRAFT && owner;
            case APPROVE -> status == Status.SUBMITTED;
            case CANCEL -> status == Status.SUBMITTED || (status == Status.DRAFT && owner);
        };
    }

    private static Order view(OrderAccess actor, OrderRow row) {
        var status = Status.valueOf(row.status());
        var actions = Arrays.stream(Action.values()).filter(action ->
                actor.allows(action.permission, row.ownerId(), row.departmentId())
                        && allowedState(action, status, isOwner(actor, row))
                        && (action != Action.APPROVE || actor.userId() != row.ownerId()))
                .toList();
        return new Order(Long.toString(row.id()), row.customerName(), row.totalAmountCents(),
                Long.toString(row.ownerId()), Long.toString(row.departmentId()), status, row.version(),
                Instant.ofEpochMilli(row.createdAt()), Instant.ofEpochMilli(row.updatedAt()), actions);
    }

    public enum Status {
        DRAFT, SUBMITTED, APPROVED, CANCELLED
    }

    public enum Action {
        EDIT(OrderPermission.EDIT, Status.DRAFT, Event.ORDER_EDITED),
        SUBMIT(OrderPermission.SUBMIT, Status.SUBMITTED, Event.ORDER_SUBMITTED),
        APPROVE(OrderPermission.APPROVE, Status.APPROVED, Event.ORDER_APPROVED),
        CANCEL(OrderPermission.CANCEL, Status.CANCELLED, Event.ORDER_CANCELLED);

        private final OrderPermission permission;
        private final Status target;
        private final Event event;

        Action(OrderPermission permission, Status target, Event event) {
            this.permission = permission;
            this.target = target;
            this.event = event;
        }
    }

    public record Order(String id, String customerName, int totalAmountCents, String ownerId, String departmentId,
            Status status, int version, Instant createdAt, Instant updatedAt, List<Action> allowedActions) {
    }

    public record Page(List<Order> items, int page, int size, long total) {
    }

    public record History(String actorId, String action, String fromStatus, String toStatus, int version,
            Instant occurredAt) {
    }

    private record Draft(String customer, int amount) {
    }
}
