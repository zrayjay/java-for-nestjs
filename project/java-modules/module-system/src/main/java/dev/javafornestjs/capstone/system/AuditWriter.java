package dev.javafornestjs.capstone.system;

import dev.javafornestjs.capstone.core.AuditTrail;
import dev.javafornestjs.capstone.core.RequestCorrelation;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class AuditWriter implements AuditTrail {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditWriter.class);
    private final AuditMapper events;
    private final RequestCorrelation request;
    private final Clock clock;

    AuditWriter(AuditMapper events, RequestCorrelation request, Clock clock) {
        this.events = events;
        this.request = request;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(long actorId, Event event, long resourceId) {
        events.insert(request.currentId(), actorId, event.resource().name(), resourceId, event.name(), clock.millis());
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void recordCommittedReplay(long actorId) {
        String requestId = request.currentId();
        try {
            events.insert(requestId, actorId, Event.REFRESH_REPLAY.resource().name(), actorId,
                    Event.REFRESH_REPLAY.name(), clock.millis());
        } catch (DataAccessException unavailable) {
            LOGGER.warn("Replay revoked but audit insert failed requestId={} actorId={}", requestId, actorId);
        }
    }
}
