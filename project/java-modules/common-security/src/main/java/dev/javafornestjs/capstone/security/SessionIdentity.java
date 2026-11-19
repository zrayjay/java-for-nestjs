package dev.javafornestjs.capstone.security;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public record SessionIdentity(long userId, String familyId, long credentialVersion) {
    public static SessionIdentity from(Jwt token) {
        long userId = Long.parseLong(token.getSubject());
        String family = UUID.fromString(token.getClaimAsString("sid")).toString();
        Object rawVersion = token.getClaim("ver");
        if (userId < 1 || !(rawVersion instanceof Integer || rawVersion instanceof Long)) {
            throw new IllegalArgumentException("Invalid access token identity");
        }
        long version = ((Number) rawVersion).longValue();
        if (version < 1) {
            throw new IllegalArgumentException("Invalid credential version");
        }
        return new SessionIdentity(userId, family, version);
    }
}
