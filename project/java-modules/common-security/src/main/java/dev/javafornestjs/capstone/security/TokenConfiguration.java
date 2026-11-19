package dev.javafornestjs.capstone.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
class TokenConfiguration {
    @Bean
    Clock capstoneClock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    TokenKeys capstoneKeys(
            @Value("${capstone.security.signing-key}") Resource signing,
            @Value("${capstone.security.verification-key}") Resource verification) throws IOException {
        try (var privateStream = signing.getInputStream(); var publicStream = verification.getInputStream()) {
            var privateKey = RsaKeyConverters.pkcs8().convert(privateStream);
            var publicKey = RsaKeyConverters.x509().convert(publicStream);
            if (privateKey == null || publicKey == null || publicKey.getModulus().bitLength() < 2048
                    || !privateKey.getModulus().equals(publicKey.getModulus())) {
                throw new IllegalArgumentException("Configure a matching RSA key pair of at least 2048 bits");
            }
            return new TokenKeys(privateKey, publicKey);
        }
    }

    @Bean
    JwtEncoder capstoneEncoder(TokenKeys keys) {
        var key = new RSAKey.Builder(keys.verification()).privateKey(keys.signing()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    JwtDecoder capstoneDecoder(TokenKeys keys, ActiveSessions sessions, Clock clock) {
        var decoder = NimbusJwtDecoder.withPublicKey(keys.verification()).build();
        var defaults = JwtValidators.createDefaultWithIssuer(AccessTokens.ISSUER);
        decoder.setJwtValidator(token -> {
            var validation = defaults.validate(token);
            if (validation.hasErrors()) {
                return validation;
            }
            Instant now = clock.instant();
            if (token.getAudience() == null || !token.getAudience().contains(AccessTokens.AUDIENCE)
                    || token.getExpiresAt() == null || !token.getExpiresAt().isAfter(now)) {
                return invalidAccess();
            }
            SessionIdentity identity;
            try {
                identity = SessionIdentity.from(token);
            } catch (IllegalArgumentException | NullPointerException invalidIdentity) {
                return invalidAccess();
            }
            return sessions.contains(identity, now) ? OAuth2TokenValidatorResult.success() : invalidAccess();
        });
        return decoder;
    }

    private static OAuth2TokenValidatorResult invalidAccess() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    }

    private record TokenKeys(RSAPrivateKey signing, RSAPublicKey verification) {
    }
}
