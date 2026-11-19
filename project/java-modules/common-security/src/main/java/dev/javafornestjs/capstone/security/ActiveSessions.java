package dev.javafornestjs.capstone.security;

import java.time.Instant;

public interface ActiveSessions {
    boolean contains(SessionIdentity identity, Instant at);
}
