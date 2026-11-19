package dev.javafornestjs.capstone.system;

import dev.javafornestjs.capstone.core.RequestFailure;
import dev.javafornestjs.capstone.security.AccessTokens;
import dev.javafornestjs.capstone.security.SessionIdentity;
import dev.javafornestjs.capstone.system.AccountsMapper.Account;
import dev.javafornestjs.capstone.system.SessionsMapper.RefreshState;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.javafornestjs.capstone.core.RequestFailure.Code.UNAUTHENTICATED;

@Service
public class SessionService {
    private static final long FAMILY_SECONDS = Duration.ofDays(7).toSeconds();

    private final AccountsMapper accounts;
    private final SessionsMapper sessions;
    private final AccessTokens accessTokens;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String missingUserHash;

    SessionService(AccountsMapper accounts, SessionsMapper sessions, AccessTokens accessTokens,
            PasswordEncoder passwords, Clock clock) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.accessTokens = accessTokens;
        this.passwords = passwords;
        this.clock = clock;
        this.missingUserHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public TokenPair login(String username, String password) {
        if (username == null || username.length() > 40 || !AccountInputs.usablePassword(password)) {
            throw invalidAuthentication();
        }
        Account candidate = accounts.byUsername(username);
        String expected = candidate == null ? missingUserHash : candidate.passwordHash();
        if (!passwords.matches(password, expected) || candidate == null || !candidate.enabled()) {
            throw invalidAuthentication();
        }
        Account account = accounts.lock(candidate.id());
        if (account == null || !account.enabled() || !account.passwordHash().equals(expected)) {
            throw invalidAuthentication();
        }
        String family = UUID.randomUUID().toString();
        sessions.insertFamily(family, account.id(), account.credentialVersion(), now() + FAMILY_SECONDS);
        return issue(account, family);
    }

    @Transactional
    public Optional<TokenPair> refresh(String token) {
        if (!wellFormed(token)) {
            return Optional.empty();
        }
        String digest = digest(token);
        Long userId = sessions.tokenOwner(digest);
        if (userId == null) {
            return Optional.empty();
        }
        // All state-changing session paths lock the user before the family.
        Account account = accounts.lock(userId);
        RefreshState current = sessions.lockToken(digest);
        long now = now();
        if (!active(account, current, now)) {
            return Optional.empty();
        }
        if (current.consumedAt() != null) {
            sessions.revoke(current.id(), now);
            // Return rejection as a value so the revocation commits.
            return Optional.empty();
        }
        sessions.consume(digest, now);
        return Optional.of(issue(account, current.id()));
    }

    @Transactional
    public void logout(SessionIdentity caller, String token) {
        if (!wellFormed(token)) {
            throw invalidAuthentication();
        }
        String digest = digest(token);
        Long owner = sessions.tokenOwner(digest);
        if (owner == null || owner != caller.userId()) {
            throw invalidAuthentication();
        }
        Account account = accounts.lock(caller.userId());
        RefreshState current = sessions.lockToken(digest);
        long now = now();
        if (!active(account, current, now) || !current.id().equals(caller.familyId())
                || current.credentialVersion() != caller.credentialVersion()) {
            throw invalidAuthentication();
        }
        sessions.revoke(current.id(), now);
    }

    private TokenPair issue(Account account, String family) {
        byte[] entropy = new byte[32];
        random.nextBytes(entropy);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        sessions.insertToken(digest(refresh), family);
        String access = accessTokens.issue(new SessionIdentity(account.id(), family, account.credentialVersion()));
        return new TokenPair(access, refresh, AccessTokens.LIFETIME_SECONDS);
    }

    private static boolean active(Account account, RefreshState state, long now) {
        return account != null && account.enabled() && state != null && state.userId() == account.id()
                && state.credentialVersion() == account.credentialVersion()
                && state.revokedAt() == null && state.expiresAt() > now;
    }

    private static boolean wellFormed(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private static String digest(String refresh) {
        try {
            var sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(refresh.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private long now() {
        return clock.instant().getEpochSecond();
    }

    public static RequestFailure invalidAuthentication() {
        return new RequestFailure(UNAUTHENTICATED, "Invalid credentials or session");
    }

    public record TokenPair(String accessToken, String refreshToken, int expiresIn) {
    }
}
