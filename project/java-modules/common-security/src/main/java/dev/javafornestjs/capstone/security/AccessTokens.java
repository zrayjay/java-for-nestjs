package dev.javafornestjs.capstone.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public final class AccessTokens {
    static final String ISSUER = "capstone-admin";
    static final String AUDIENCE = "capstone-api";
    public static final int LIFETIME_SECONDS = 300;

    private final JwtEncoder encoder;
    private final Clock clock;

    AccessTokens(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    public String issue(SessionIdentity identity) {
        Instant now = clock.instant();
        var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(Long.toString(identity.userId()))
                .id(UUID.randomUUID().toString())
                .claim("sid", identity.familyId())
                .claim("ver", identity.credentialVersion())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(LIFETIME_SECONDS))
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}
