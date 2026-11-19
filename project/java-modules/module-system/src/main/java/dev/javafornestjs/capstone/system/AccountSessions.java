package dev.javafornestjs.capstone.system;

import dev.javafornestjs.capstone.security.ActiveSessions;
import dev.javafornestjs.capstone.security.SessionIdentity;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
class AccountSessions implements ActiveSessions {
    private final SessionsMapper sessions;

    AccountSessions(SessionsMapper sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean contains(SessionIdentity identity, Instant at) {
        return sessions.active(identity.userId(), identity.familyId(), identity.credentialVersion(),
                at.getEpochSecond());
    }
}
