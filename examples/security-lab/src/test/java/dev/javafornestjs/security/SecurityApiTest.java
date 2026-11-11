package dev.javafornestjs.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(classes = SecurityApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "security.ephemeral-keys=true")
@ActiveProfiles({"data", "security", "security-test"})
@Import(SecurityApiTest.TimeConfiguration.class)
class SecurityApiTest {
    private static final String PASSWORD = "fixture-password-only";
    static final class MutableClock extends Clock {
        private volatile Instant value = Instant.parse("2026-11-11T00:00:00Z");
        void reset() { value = Instant.parse("2026-11-11T00:00:00Z"); }
        void advance(Duration amount) { value = value.plus(amount); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(value, zone); }
        @Override public Instant instant() { return value; }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
    }
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder encoder;
    @Autowired dev.javafornestjs.web.TaskService tasks;
    @MockitoSpyBean AuthService auth;
    @LocalServerPort int port;
    private String passwordHash;
    @BeforeEach
    void fixture() {
        clock.reset();
        SecurityContextHolder.clearContext();
        for (String table : List.of("sec_audit", "sec_refresh_tokens", "sec_sessions", "sec_user_roles",
                "sec_role_permissions", "sec_permissions", "sec_roles", "sec_users", "sec_departments", "data_tasks", "data_owners")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO sec_departments(id,name) VALUES (1,'Engineering'),(2,'Sales')");
        jdbc.update("INSERT INTO data_owners(id,name) VALUES (1,'Alice'),(2,'Bob'),(3,'Cara'),(4,'Admin'),(5,'Erin')");
        passwordHash = passwords.encode(PASSWORD);
        String[] names = {"alice", "bob", "cara", "admin", "erin"};
        for (int index = 0; index < names.length; index++) {
            jdbc.update("INSERT INTO sec_users(id,username,password_hash,department_id) VALUES (?,?,?,?)",
                    index + 1, names[index], passwordHash, index == 1 ? 2 : 1);
        }
        jdbc.update("INSERT INTO sec_roles(id,name) VALUES (1,'member'),(2,'reader'),(3,'admin')");
        jdbc.update("INSERT INTO sec_permissions(id,code) VALUES (1,'task:read:department'),(2,'task:write:own'),"
                + "(3,'task:read:any'),(4,'task:write:any'),(5,'role:manage')");
        jdbc.update("INSERT INTO sec_role_permissions(role_id,permission_id) VALUES (1,1),(1,2),(2,1),(3,3),(3,4),(3,5)");
        jdbc.update("INSERT INTO sec_user_roles(user_id,role_id) VALUES (1,1),(2,1),(3,2),(4,3),(5,1)");
    }
    private String url(String path) { return "http://127.0.0.1:" + port + path; }
    private ResponseEntity<JsonNode> request(HttpMethod method, String path, String access, String body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (access != null) { headers.setBearerAuth(access); }
        return http.exchange(url(path), method, new HttpEntity<>(body, headers), JsonNode.class);
    }
    private JsonNode login(String username) {
        var response = request(HttpMethod.POST, "/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        return response.getBody();
    }
    private ResponseEntity<JsonNode> refresh(String token) {
        return request(HttpMethod.POST, "/auth/refresh", null, "{\"refreshToken\":\"" + token + "\"}");
    }
    private String access(JsonNode tokens) { return tokens.path("accessToken").asText(); }
    private int create(String token, String title) {
        var response = request(HttpMethod.POST, "/tasks", token,
                "{\"title\":\"" + title + "\",\"details\":{\"priority\":2}}");
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody().path("id").asInt();
    }
    private JsonNode claims(JsonNode tokens) throws Exception {
        return json.readTree(Base64.getUrlDecoder().decode(access(tokens).split("\\.")[1]));
    }
    private String signed(String session, String issuer, String audience, Instant notBefore) {
        var values = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject("1")
                .issuedAt(clock.instant()).expiresAt(clock.instant().plusSeconds(300)).id("test-jti")
                .claim("sid", session).claim("ver", 0);
        if (notBefore != null) { values.notBefore(notBefore); }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), values.build())).getTokenValue();
    }
    @Test
    void passwordsAreHashedAndOnlyExplicitBearerCredentialsAuthenticate() {
        assertNotEquals(PASSWORD, passwordHash);
        assertTrue(passwords.matches(PASSWORD, passwordHash));
        assertEquals(HttpStatus.UNAUTHORIZED, request(HttpMethod.GET, "/tasks", null, null).getStatusCode());
        var tokens = login("alice");
        assertEquals(HttpStatus.OK, request(HttpMethod.GET, "/me", access(tokens), null).getStatusCode());
        var cookie = new HttpHeaders();
        cookie.set(HttpHeaders.COOKIE, "accessToken=" + access(tokens));
        assertEquals(HttpStatus.UNAUTHORIZED, http.exchange(url("/me"), HttpMethod.GET, new HttpEntity<>(cookie), JsonNode.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, request(HttpMethod.GET, "/me", null, null).getStatusCode());
        assertThrows(AccessDeniedException.class, () -> tasks.list(20));
    }
    @Test
    void wrongCredentialsAreGenericAndDisabledUsersCannotLogin() {
        for (String username : List.of("alice", "unknown")) {
            var response = request(HttpMethod.POST, "/auth/login", null,
                    "{\"username\":\"" + username + "\",\"password\":\"wrong\"}");
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertEquals("unauthorized", response.getBody().path("code").asText());
            assertEquals(1, response.getBody().size());
        }
        jdbc.update("UPDATE sec_users SET enabled=FALSE WHERE id=1");
        assertEquals(HttpStatus.UNAUTHORIZED, request(HttpMethod.POST, "/auth/login", null,
                "{\"username\":\"alice\",\"password\":\"" + PASSWORD + "\"}").getStatusCode());
    }
    @Test
    void departmentAndOwnershipRestrictionsApplyBeforeLimitAndWrites() {
        String bob = access(login("bob"));
        int bobId = create(bob, "Bob task");
        String alice = access(login("alice"));
        int aliceId = create(alice, "Alice task");
        var list = request(HttpMethod.GET, "/tasks?limit=1", alice, null).getBody();
        assertEquals(1, list.size());
        assertEquals(aliceId, list.get(0).path("id").asInt());
        assertEquals(HttpStatus.NOT_FOUND, request(HttpMethod.GET, "/tasks/" + bobId, alice, null).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, request(HttpMethod.PUT, "/tasks/" + bobId, alice,
                "{\"title\":\"Stolen\",\"details\":{\"priority\":1}}").getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, request(HttpMethod.DELETE, "/tasks/" + bobId, alice, null).getStatusCode());
        String erin = access(login("erin"));
        assertEquals(HttpStatus.OK, request(HttpMethod.GET, "/tasks/" + aliceId, erin, null).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, request(HttpMethod.DELETE, "/tasks/" + aliceId, erin, null).getStatusCode());
        String admin = access(login("admin"));
        assertEquals(HttpStatus.OK, request(HttpMethod.GET, "/tasks/" + bobId, admin, null).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, request(HttpMethod.DELETE, "/tasks/" + bobId, admin, null).getStatusCode());
        assertEquals("Alice task", jdbc.queryForObject("SELECT title FROM data_tasks WHERE id=?", String.class, aliceId));
    }
    @Test
    void buttonPermissionMetadataDoesNotReplaceDirectServerRejection() {
        String reader = access(login("cara"));
        var me = request(HttpMethod.GET, "/me", reader, null).getBody();
        assertFalse(me.path("permissions").toString().contains("task:write"));
        assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.POST, "/tasks", reader,
                "{\"title\":\"Forbidden\",\"details\":{\"priority\":2}}").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.PUT, "/admin/users/3/roles", reader, "{\"roles\":[3]}").getStatusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM data_tasks", Integer.class));
    }
    @Test
    void permissionChangesApplyToTheExistingAccessToken() {
        String alice = access(login("alice"));
        String admin = access(login("admin"));
        assertEquals(HttpStatus.NO_CONTENT, request(HttpMethod.PUT, "/admin/users/1/roles", admin, "{\"roles\":[2]}").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.POST, "/tasks", alice,
                "{\"title\":\"Revoked permission\",\"details\":{\"priority\":2}}").getStatusCode());
        assertEquals(HttpStatus.OK, request(HttpMethod.GET, "/me", alice, null).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, request(HttpMethod.PUT, "/admin/users/1/roles", admin, "{\"roles\":[999]}").getStatusCode());
        assertEquals(2, jdbc.queryForObject("SELECT role_id FROM sec_user_roles WHERE user_id=1", Integer.class));
    }
    @Test
    void accessExpiryIsStrictButRefreshCanIssueANewAccessToken() {
        var first = login("alice");
        clock.advance(Duration.ofSeconds(300));
        assertEquals(HttpStatus.UNAUTHORIZED, request(HttpMethod.GET, "/me", access(first), null).getStatusCode());
        var next = refresh(first.path("refreshToken").asText());
        assertEquals(HttpStatus.OK, next.getStatusCode());
        assertEquals(HttpStatus.OK, request(HttpMethod.GET, "/me", access(next.getBody()), null).getStatusCode());
    }
    @Test
    void refreshRotationRejectsReplayAndCommitsFamilyRevocation() {
        var first = login("alice");
        var second = refresh(first.path("refreshToken").asText());
        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertNotEquals(first.path("refreshToken"), second.getBody().path("refreshToken"));
        assertEquals(HttpStatus.OK, request(HttpMethod.GET, "/me", access(second.getBody()), null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, refresh(first.path("refreshToken").asText()).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, request(HttpMethod.GET, "/me", access(second.getBody()), null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, refresh(second.getBody().path("refreshToken").asText()).getStatusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sec_sessions WHERE revoked_at IS NOT NULL", Integer.class));
        for (String value : jdbc.queryForList("SELECT token_hash FROM sec_refresh_tokens", String.class)) {
            assertTrue(value.matches("[0-9a-f]{64}"));
            assertNotEquals(first.path("refreshToken").asText(), value);
        }
    }
    @Test
    void concurrentRefreshHasOneWinnerAndReplayInvalidatesItsFamily() throws Exception {
        var first = login("alice");
        String token = first.path("refreshToken").asText();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> {
                start.await();
                return refresh(token);
            });
            var right = executor.submit(() -> {
                start.await();
                return refresh(token);
            });
            start.countDown();
            var a = left.get(10, TimeUnit.SECONDS);
            var b = right.get(10, TimeUnit.SECONDS);
            assertEquals(Set.of(HttpStatus.OK, HttpStatus.UNAUTHORIZED), Set.of(a.getStatusCode(), b.getStatusCode()));
            var winner = a.getStatusCode() == HttpStatus.OK ? a.getBody() : b.getBody();
            assertEquals(HttpStatus.UNAUTHORIZED, request(HttpMethod.GET, "/me", access(winner), null).getStatusCode());
        }
    }
    @Test
    void logoutAndDisabledAccountRevokeUseOfAccessAndRefresh() {
        var first = login("alice");
        assertEquals(HttpStatus.NO_CONTENT, request(HttpMethod.POST, "/auth/logout", access(first), null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, request(HttpMethod.GET, "/me", access(first), null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, refresh(first.path("refreshToken").asText()).getStatusCode());
        var second = login("alice");
        jdbc.update("UPDATE sec_users SET enabled=FALSE WHERE id=1");
        assertEquals(HttpStatus.UNAUTHORIZED, request(HttpMethod.GET, "/me", access(second), null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, refresh(second.path("refreshToken").asText()).getStatusCode());
    }
    @Test
    void refreshAndSessionExpiryRejectAtTheBoundary() {
        var first = login("alice");
        clock.advance(Duration.ofSeconds(604800));
        assertEquals(HttpStatus.UNAUTHORIZED, refresh(first.path("refreshToken").asText()).getStatusCode());
    }
    @Test
    void signatureAlgorithmIssuerAudienceAndRequiredClaimsAreValidated() throws Exception {
        var first = login("alice");
        String valid = access(first);
        String[] parts = valid.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        String unsigned = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "." + valid.split("\\.")[1] + ".";
        String session = claims(first).path("sid").asText();
        for (String invalid : List.of(String.join(".", parts), unsigned,
                signed(session, "wrong-issuer", "teaching-api", clock.instant()),
                signed(session, "https://java-for-nestjs.example", "wrong-audience", clock.instant()),
                signed(session, "https://java-for-nestjs.example", "teaching-api", null),
                signed(session, "https://java-for-nestjs.example", "teaching-api", clock.instant().plusSeconds(60)))) {
            assertEquals(HttpStatus.UNAUTHORIZED, request(HttpMethod.GET, "/me", invalid, null).getStatusCode());
        }
    }
    @Test
    void auditCorrelationDoesNotPersistPasswordsOrTokens() {
        var first = login("alice");
        var response = request(HttpMethod.POST, "/tasks", access(first), "{\"title\":\"Audited\",\"details\":{\"priority\":2}}");
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        String id = response.getBody().path("id").asText();
        assertEquals(response.getHeaders().getFirst("X-Request-Id"), jdbc.queryForObject(
                "SELECT request_id FROM sec_audit WHERE event_code='task.created' AND resource_id=?", String.class, id));
        String events = jdbc.queryForList("SELECT * FROM sec_audit").toString();
        assertFalse(events.contains(PASSWORD));
        assertFalse(events.contains(first.path("refreshToken").asText()));
        assertFalse(events.contains(access(first)));
    }
    @Test
    void auditFailureRollsBackTheTaskInsteadOfLeavingAnUnauditedWrite() {
        String alice = access(login("alice"));
        doThrow(new IllegalStateException("private-audit-detail")).when(auth).audit(eq(1), eq("task.created"), anyString());
        var response = request(HttpMethod.POST, "/tasks", alice, "{\"title\":\"Must rollback\",\"details\":{\"priority\":2}}");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("internal_error", response.getBody().path("code").asText());
        assertEquals(1, response.getBody().size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM data_tasks", Integer.class));
    }
}
