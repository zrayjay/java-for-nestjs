package dev.javafornestjs.capstone.system;

import dev.javafornestjs.capstone.core.RequestFailure;
import dev.javafornestjs.capstone.security.ActiveSessions;
import dev.javafornestjs.capstone.security.SessionIdentity;
import dev.javafornestjs.capstone.system.AccountsMapper.Account;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

import static dev.javafornestjs.capstone.core.RequestFailure.Code.UNAUTHENTICATED;

@Component
class AccountSessions implements ActiveSessions {
    private final AccountsMapper accounts;
    private final SessionsMapper sessions;
    private final Clock clock;

    AccountSessions(AccountsMapper accounts, SessionsMapper sessions, Clock clock) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    public boolean contains(SessionIdentity identity, Instant at) {
        return sessions.active(identity.userId(), identity.familyId(), identity.credentialVersion(),
                at.getEpochSecond());
    }

    Account currentAccount(SessionIdentity identity, boolean lock) {
        var account = lock ? accounts.lock(identity.userId()) : accounts.find(identity.userId());
        if (account == null || !account.enabled() || account.credentialVersion() != identity.credentialVersion()
                || !contains(identity, clock.instant())) {
            throw new RequestFailure(UNAUTHENTICATED, "Authentication required");
        }
        return account;
    }
}
