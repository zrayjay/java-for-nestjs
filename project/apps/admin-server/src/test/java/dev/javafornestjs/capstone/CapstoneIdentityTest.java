package dev.javafornestjs.capstone;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CapstoneIdentityTest {
    private static final String ADMIN = "test-admin";
    private static final String ADMIN_PASSWORD = "test-only-admin-password";
    private static final String USER_PASSWORD = "test-only-user-password";
    private static Path signingKey;
    private static Path verificationKey;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestClock clock;

    @DynamicPropertySource
    static void runtimeProperties(DynamicPropertyRegistry properties) throws GeneralSecurityException, IOException {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        signingKey = pemFile("PRIVATE KEY", keys.getPrivate().getEncoded());
        verificationKey = pemFile("PUBLIC KEY", keys.getPublic().getEncoded());
        int port = Integer.parseInt(System.getenv().getOrDefault("CAPSTONE_TEST_DB_PORT", "13319"));
        properties.add("spring.datasource.url",
                () -> "jdbc:mysql://127.0.0.1:" + port + "/capstone_test?allowPublicKeyRetrieval=true&useSSL=false");
        properties.add("spring.datasource.username", () -> "capstone_test");
        properties.add("spring.datasource.password", () -> "capstone-test-password");
        properties.add("capstone.bootstrap.username", () -> ADMIN);
        properties.add("capstone.bootstrap.password", () -> ADMIN_PASSWORD);
        properties.add("capstone.security.signing-key", () -> signingKey.toUri().toString());
        properties.add("capstone.security.verification-key", () -> verificationKey.toUri().toString());
    }

    @BeforeEach
    void resetClock() {
        clock.set(Instant.now());
    }

    @AfterAll
    static void removeTestKeyFiles() throws IOException {
        if (signingKey != null) {
            Files.deleteIfExists(signingKey);
        }
        if (verificationKey != null) {
            Files.deleteIfExists(verificationKey);
        }
    }

    @Test
    void startupAndLoginUseRealMysqlAndHttp() {
        var health = request(HttpMethod.GET, "/actuator/health", null, null);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody().path("status").asText()).isEqualTo("UP");

        var unauthorized = get("/me", null);
        assertError(unauthorized, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertError(request(HttpMethod.POST, "/api/v1/auth/login", null,
                Map.of("username", ADMIN, "password", "wrong-password")),
                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        var tokens = login(ADMIN, ADMIN_PASSWORD);
        var me = get("/me", access(tokens));
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().path("username").asText()).isEqualTo(ADMIN);
        assertThat(me.getBody().path("permissions").get(0).asText()).isEqualTo("system:admin");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE username = ?", String.class, ADMIN))
                .startsWith("$2a$").doesNotContain(ADMIN_PASSWORD);
    }

    @Test
    void accessExpiresAtBoundaryWhileRefreshRemainsUsable() {
        var tokens = login(ADMIN, ADMIN_PASSWORD);
        assertThat(tokens.path("expiresIn").asInt()).isEqualTo(300);
        clock.set(clock.instant().plusSeconds(300));
        assertError(get("/me", access(tokens)), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        var refresh = refresh(tokens);
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        clock.set(clock.instant().plusSeconds(7 * 24 * 60 * 60));
        assertError(refresh(refresh.getBody()), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }

    @Test
    void replayCommitsRevocationAndLogoutTargetsOnlyTheMatchingSession() {
        var original = login(ADMIN, ADMIN_PASSWORD);
        var other = login(ADMIN, ADMIN_PASSWORD);
        var rotated = refresh(original);
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshToken(rotated.getBody())).isNotEqualTo(refreshToken(original));
        assertThat(access(rotated.getBody())).isNotEqualTo(access(original));
        assertError(refresh(original), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertError(refresh(rotated.getBody()), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertError(get("/me", access(rotated.getBody())), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertThat(get("/me", access(other)).getStatusCode()).isEqualTo(HttpStatus.OK);

        var unrelated = login(ADMIN, ADMIN_PASSWORD);
        var wrongLogout = request(HttpMethod.POST, "/api/v1/auth/logout", access(other),
                Map.of("refreshToken", refreshToken(unrelated)));
        assertError(wrongLogout, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        var logout = request(HttpMethod.POST, "/api/v1/auth/logout", access(other),
                Map.of("refreshToken", refreshToken(other)));
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertError(refresh(other), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertThat(get("/me", access(unrelated)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void concurrentReuseIssuesAtMostOneTokenPairAndRevokesTheFamily() throws Exception {
        var tokens = login(ADMIN, ADMIN_PASSWORD);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> simultaneousRefresh(tokens, ready, start));
            var second = workers.submit(() -> simultaneousRefresh(tokens, ready, start));
            boolean reachedStart = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertThat(reachedStart).isTrue();
            var left = first.get(10, TimeUnit.SECONDS);
            var right = second.get(10, TimeUnit.SECONDS);
            assertThat(List.of(left.getStatusCode(), right.getStatusCode()))
                    .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.UNAUTHORIZED);
            var accepted = left.getStatusCode() == HttpStatus.OK ? left : right;
            assertError(refresh(accepted.getBody()), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
            assertError(get("/me", access(tokens)), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        }
    }

    @Test
    void adminCanManageUsersWhileNonAdminCannotAndCredentialChangesRevokeBothTokens() {
        var admin = access(login(ADMIN, ADMIN_PASSWORD));
        var departments = get("/departments", admin);
        String department = departments.getBody().get(0).path("id").asText();
        String username = "user-" + UUID.randomUUID().toString().replace("-", "");
        var body = Map.of("username", username, "displayName", "New user", "departmentId", department,
                "password", USER_PASSWORD);
        assertError(request(HttpMethod.POST, "/api/v1/users", admin, Map.of("username", username,
                        "displayName", "New user", "departmentId", Long.parseLong(department),
                        "password", USER_PASSWORD)), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        var created = request(HttpMethod.POST, "/api/v1/users", admin, body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().has("passwordHash")).isFalse();
        String route = "/api/v1/users/" + created.getBody().path("id").asText();
        assertError(request(HttpMethod.POST, "/api/v1/users", admin, body),
                HttpStatus.CONFLICT, "RESOURCE_CONFLICT");
        assertThat(get("/users?size=1", admin).getBody().path("items").size()).isEqualTo(1);
        assertThat(request(HttpMethod.GET, route, admin, null).getBody().path("username").asText()).isEqualTo(username);

        var user = login(username, USER_PASSWORD);
        assertError(get("/users", access(user)), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertError(request(HttpMethod.PUT, route + "/password", access(user), Map.of("password", "forbidden-change")),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
        var edited = request(HttpMethod.PATCH, route, admin, Map.of("displayName", "Edited"));
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody().path("displayName").asText()).isEqualTo("Edited");

        String newPassword = "test-only-changed-password";
        var changed = request(HttpMethod.PUT, route + "/password", admin, Map.of("password", newPassword));
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertError(get("/me", access(user)), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertError(refresh(user), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        var current = login(username, newPassword);
        assertThat(request(HttpMethod.DELETE, route, admin, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertError(get("/me", access(current)), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertError(refresh(current), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        String adminId = get("/me", admin).getBody().path("id").asText();
        assertError(request(HttpMethod.DELETE, "/api/v1/users/" + adminId, admin, null),
                HttpStatus.CONFLICT, "RESOURCE_CONFLICT");
    }

    private ResponseEntity<JsonNode> get(String route, String token) {
        return request(HttpMethod.GET, "/api/v1" + route, token, null);
    }

    private JsonNode login(String username, String password) {
        var login = request(HttpMethod.POST, "/api/v1/auth/login", null,
                Map.of("username", username, "password", password));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody();
    }

    private ResponseEntity<JsonNode> refresh(JsonNode source) {
        return request(HttpMethod.POST, "/api/v1/auth/refresh", null, Map.of("refreshToken", refreshToken(source)));
    }

    private ResponseEntity<JsonNode> simultaneousRefresh(JsonNode tokens, CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Refresh test did not start");
        }
        return refresh(tokens);
    }

    private ResponseEntity<JsonNode> request(HttpMethod method, String path, String bearer, Object body) {
        var headers = new HttpHeaders();
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private static void assertError(ResponseEntity<JsonNode> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody().path("code").asText()).isEqualTo(code);
        assertThat(response.getBody().path("requestId").asText()).isNotBlank()
                .isEqualTo(response.getHeaders().getFirst("X-Request-Id"));
    }

    private static String access(JsonNode tokens) {
        return tokens.path("accessToken").asText();
    }

    private static String refreshToken(JsonNode tokens) {
        return tokens.path("refreshToken").asText();
    }

    private static Path pemFile(String label, byte[] data) throws IOException {
        var file = Files.createTempFile("capstone-test-", ".pem");
        var base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(data);
        return Files.writeString(file, "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControlledTime {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    static final class TestClock extends Clock {
        private volatile Instant now = Instant.now();

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new UnsupportedOperationException("The test clock uses UTC");
            }
            return this;
        }
    }
}
