package dev.javafornestjs.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.transaction.support.TransactionTemplate;

public class AuthService {
    public static final class Failure extends RuntimeException { }
    public static final class UnknownUser extends RuntimeException { }
    public record Tokens(String accessToken, String refreshToken, long expiresIn, String tokenType) { }
    private record User(int id, String username, String hash, int department, boolean enabled, int version) { }
    private record Session(int userId, long expires, Long revoked) { }
    private record Refresh(String sessionId, long expires, Long consumed, Long revoked) { }
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final Clock clock;
    private final SecurityProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthService(JdbcTemplate jdbc, TransactionTemplate transactions, PasswordEncoder passwords,
                       JwtEncoder encoder, Clock clock, SecurityProperties properties) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.passwords = passwords;
        this.encoder = encoder;
        this.clock = clock;
        this.properties = properties;
        dummyHash = passwords.encode(UUID.randomUUID().toString());
    }
    private long now() { return clock.instant().getEpochSecond(); }
    private User user(String condition, Object parameter) {
        // condition is selected only by the two fixed internal call sites below.
        var users = jdbc.query("SELECT id,username,password_hash,department_id,enabled,auth_version FROM sec_users WHERE "
                + condition, (row, index) -> new User(row.getInt("id"), row.getString("username"),
                row.getString("password_hash"), row.getInt("department_id"), row.getBoolean("enabled"),
                row.getInt("auth_version")), parameter);
        return users.isEmpty() ? null : users.getFirst();
    }
    private Session session(String id, boolean lock) {
        var sessions = jdbc.query("SELECT user_id,expires_at,revoked_at FROM sec_sessions WHERE id=?"
                + (lock ? " FOR UPDATE" : ""), (row, index) -> new Session(row.getInt("user_id"),
                row.getLong("expires_at"), row.getObject("revoked_at", Long.class)), id);
        return sessions.isEmpty() ? null : sessions.getFirst();
    }
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private String opaque() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public void audit(Integer actor, String event, String resource) {
        String request = MDC.get("requestId");
        jdbc.update("INSERT INTO sec_audit(actor_id,event_code,resource_id,request_id,occurred_at) VALUES (?,?,?,?,?)",
                actor, event, resource, request == null ? "non-http" : request, now());
    }
    private Tokens issue(User user, String sessionId, long sessionExpiry) {
        String refresh = opaque();
        jdbc.update("INSERT INTO sec_refresh_tokens(token_hash,session_id,expires_at) VALUES (?,?,?)",
                hash(refresh), sessionId, sessionExpiry);
        long current = now();
        long lifetime = Math.min(properties.accessSeconds(), sessionExpiry - current);
        Instant issued = Instant.ofEpochSecond(current);
        var claims = JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
                .subject(Integer.toString(user.id())).issuedAt(issued).notBefore(issued).expiresAt(issued.plusSeconds(lifetime))
                .id(UUID.randomUUID().toString()).claim("sid", sessionId).claim("ver", user.version()).build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("teaching-rsa").build();
        String access = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new Tokens(access, refresh, lifetime, "Bearer");
    }
    public Tokens login(String username, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) { throw new IllegalArgumentException("Password is too long"); }
        User user = user("username=?", username);
        boolean matched = passwords.matches(password, user == null ? dummyHash : user.hash());
        if (user == null || !user.enabled() || !matched) {
            audit(null, "auth.login.denied", null);
            throw new Failure();
        }
        return transactions.execute(status -> {
            String sessionId = UUID.randomUUID().toString();
            long expiry = Math.addExact(now(), properties.refreshSeconds());
            jdbc.update("INSERT INTO sec_sessions(id,user_id,expires_at) VALUES (?,?,?)", sessionId, user.id(), expiry);
            Tokens tokens = issue(user, sessionId, expiry);
            audit(user.id(), "auth.login", null);
            return tokens;
        });
    }
    private void revoke(String sessionId, int actor, String event) {
        jdbc.update("UPDATE sec_sessions SET revoked_at=? WHERE id=? AND revoked_at IS NULL", now(), sessionId);
        jdbc.update("UPDATE sec_refresh_tokens SET revoked_at=? WHERE session_id=? AND revoked_at IS NULL", now(), sessionId);
        audit(actor, event, null);
    }
    public Tokens rotate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) { throw new Failure(); }
        String digest = hash(token);
        Tokens result = transactions.execute(status -> {
            var ids = jdbc.queryForList("SELECT session_id FROM sec_refresh_tokens WHERE token_hash=?", String.class, digest);
            if (ids.isEmpty()) { return null; }
            String id = ids.getFirst();
            // Every operation in a family locks the session before token rows.
            Session family = session(id, true);
            var rows = jdbc.query("SELECT session_id,expires_at,consumed_at,revoked_at FROM sec_refresh_tokens WHERE token_hash=? FOR UPDATE",
                    (row, index) -> new Refresh(row.getString("session_id"), row.getLong("expires_at"),
                    row.getObject("consumed_at", Long.class), row.getObject("revoked_at", Long.class)), digest);
            if (family == null || rows.isEmpty()) { return null; }
            Refresh old = rows.getFirst();
            User user = user("id=?", family.userId());
            if (old.consumed() != null || old.revoked() != null || old.expires() <= now()
                    || family.revoked() != null || family.expires() <= now() || user == null || !user.enabled()) {
                revoke(id, family.userId(), "auth.refresh.denied");
                return null;
            }
            jdbc.update("UPDATE sec_refresh_tokens SET consumed_at=? WHERE token_hash=?", now(), digest);
            Tokens next = issue(user, id, family.expires());
            audit(user.id(), "auth.refresh", null);
            return next;
        });
        // A rejected replay must commit its revocation before returning an error.
        if (result == null) { throw new Failure(); }
        return result;
    }
    public boolean valid(Jwt jwt) {
        try {
            int id = Integer.parseInt(jwt.getSubject());
            String sessionId = jwt.getClaimAsString("sid");
            if (id < 1 || sessionId == null || !UUID.fromString(sessionId).toString().equals(sessionId)
                    || jwt.getId() == null || jwt.getIssuedAt() == null || jwt.getNotBefore() == null
                    || jwt.getExpiresAt() == null || jwt.getExpiresAt().getEpochSecond() <= now()
                    || jwt.getIssuedAt().getEpochSecond() > now()) { return false; }
            Session family = session(sessionId, false);
            User user = user("id=?", id);
            Object version = jwt.getClaims().get("ver");
            return family != null && family.userId() == id && family.revoked() == null && family.expires() > now()
                    && user != null && user.enabled() && version instanceof Number number && number.doubleValue() == user.version();
        } catch (IllegalArgumentException error) { return false; }
    }
    public UsernamePasswordAuthenticationToken authenticate(Jwt jwt) {
        if (!valid(jwt)) { throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_token"); }
        User user = user("id=?", Integer.parseInt(jwt.getSubject()));
        var values = jdbc.queryForList("SELECT DISTINCT p.code FROM sec_permissions p JOIN sec_role_permissions rp ON rp.permission_id=p.id "
                + "JOIN sec_user_roles ur ON ur.role_id=rp.role_id WHERE ur.user_id=?", String.class, user.id());
        Set<String> permissions = Set.copyOf(values);
        List<GrantedAuthority> authorities = permissions.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
        var principal = new SecurityUser(user.id(), user.username(), user.department(), permissions, jwt.getClaimAsString("sid"));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }
    public void logout() {
        var user = AuthContext.current();
        transactions.executeWithoutResult(status -> {
            session(user.sessionId(), true);
            revoke(user.sessionId(), user.id(), "auth.logout");
        });
    }
    public void assignRoles(int id, List<Integer> roles) {
        AuthContext.require("role:manage");
        if (new HashSet<>(roles).size() != roles.size()) { throw new IllegalArgumentException("Duplicate roles"); }
        transactions.executeWithoutResult(status -> {
            if (user("id=?", id) == null) { throw new UnknownUser(); }
            for (int role : roles) {
                if (jdbc.queryForObject("SELECT COUNT(*) FROM sec_roles WHERE id=?", Integer.class, role) != 1) {
                    throw new IllegalArgumentException("Unknown role");
                }
            }
            jdbc.update("DELETE FROM sec_user_roles WHERE user_id=?", id);
            for (int role : roles) { jdbc.update("INSERT INTO sec_user_roles(user_id,role_id) VALUES (?,?)", id, role); }
            audit(AuthContext.current().id(), "roles.changed", Integer.toString(id));
        });
    }
}
