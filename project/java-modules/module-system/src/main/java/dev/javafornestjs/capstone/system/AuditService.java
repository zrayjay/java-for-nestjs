package dev.javafornestjs.capstone.system;

import dev.javafornestjs.capstone.core.AuditTrail.Resource;
import dev.javafornestjs.capstone.core.RequestFailure;
import dev.javafornestjs.capstone.security.SessionIdentity;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.javafornestjs.capstone.core.RequestFailure.Code.VALIDATION_ERROR;

@Service
public class AuditService {
    private final AccountsService accounts;
    private final AuditMapper events;

    AuditService(AccountsService accounts, AuditMapper events) {
        this.accounts = accounts;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page page(SessionIdentity actor, String resource, Long id, int page, int size) {
        accounts.requireAdministrator(actor, false);
        if (page < 1 || size < 1 || size > 100) {
            throw new RequestFailure(VALIDATION_ERROR, "Page must be positive and size must be between 1 and 100");
        }
        String type = parseResource(resource);
        var items = events.page(type, id, size, (long) (page - 1) * size).stream().map(row -> new Event(
                Long.toString(row.id()), row.requestId(), Long.toString(row.actorId()), row.resourceType(),
                Long.toString(row.resourceId()), row.action(), Instant.ofEpochMilli(row.occurredAt()))).toList();
        return new Page(items, page, size, events.count(type, id));
    }

    private static String parseResource(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Resource.valueOf(value).name();
        } catch (IllegalArgumentException invalid) {
            throw new RequestFailure(VALIDATION_ERROR, "Unknown audit resource type");
        }
    }

    public record Page(List<Event> items, int page, int size, long total) {
    }

    public record Event(String id, String requestId, String actorId, String resourceType, String resourceId,
            String action, Instant occurredAt) {
    }
}
