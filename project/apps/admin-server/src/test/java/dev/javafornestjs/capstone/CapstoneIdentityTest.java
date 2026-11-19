package dev.javafornestjs.capstone;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javafornestjs.capstone.security.OrderAccessLookup;
import dev.javafornestjs.capstone.security.OrderPermission;
import dev.javafornestjs.capstone.security.OrderScope;
import dev.javafornestjs.capstone.security.SessionIdentity;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
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

    @Autowired
    private OrderAccessLookup orderAccess;

    @Autowired
    private JwtDecoder jwtDecoder;

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
        assertThat(me.getBody().path("permissions")).extracting(JsonNode::asText)
                .contains("system:admin", "orders:read");
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
        var results = simultaneousRequests(() -> refresh(tokens), () -> refresh(tokens));
        assertThat(results).extracting(ResponseEntity::getStatusCode)
                .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.UNAUTHORIZED);
        var accepted = results.stream().filter(response -> response.getStatusCode() == HttpStatus.OK)
                .findFirst().orElseThrow();
        assertError(refresh(accepted.getBody()), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertError(get("/me", access(tokens)), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
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

    @Test
    void rolesRespectActionScopesAndMenuUpdatesAreVisibleInExistingSessions() {
        var admin = access(login(ADMIN, ADMIN_PASSWORD));
        var member = newUser(admin);
        String memberToken = access(login(member.path("username").asText(), USER_PASSWORD));
        String memberId = member.path("id").asText();
        long department = member.path("departmentId").asLong();
        var unrelated = newUser(admin);
        String outsideCode = "other-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO sys_department(code, name) VALUES (?, ?)", outsideCode, "Outside department");
        long outsideDepartment = jdbc.queryForObject(
                "SELECT id FROM sys_department WHERE code = ?", Long.class, outsideCode);

        assertError(get("/roles", null), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertError(get("/roles", memberToken), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(get("/me/menus", memberToken).getBody().isEmpty()).isTrue();
        var reader = createRole(admin, "Reader");
        var handler = createRole(admin, "Handler");
        String readerId = reader.path("id").asText();
        String handlerId = handler.path("id").asText();
        replaceGrants(admin, readerId, List.of(Map.of("permission", "orders:read", "scope", "DEPARTMENT")));
        replaceGrants(admin, handlerId, List.of(Map.of("permission", "orders:cancel", "scope", "OWN"),
                Map.of("permission", "orders:read", "scope", "OWN")));
        var invalidGrant = request(HttpMethod.PUT, "/api/v1/roles/" + handlerId + "/grants", admin,
                Map.of("grants", List.of(Map.of("permission", "orders:submit", "scope", "ALL"))));
        assertError(invalidGrant, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        var requestRoles = Map.of("roleIds", List.of(readerId, handlerId));
        var rolesRoute = "/api/v1/users/" + memberId + "/roles";
        assertError(request(HttpMethod.PUT, rolesRoute, memberToken, requestRoles),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(request(HttpMethod.PUT, rolesRoute, admin, requestRoles).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/users/" + memberId, admin).getBody().path("roleIds")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder(readerId, handlerId);
        assertThat(get("/roles", admin).getBody().path("items")).anySatisfy(role -> {
            assertThat(role.path("id").asText()).isEqualTo(handlerId);
            assertThat(role.path("grants").get(0).path("permission").asText()).isEqualTo("orders:cancel");
        });

        var identity = SessionIdentity.from(jwtDecoder.decode(memberToken));
        var permissions = orderAccess.forSession(identity);
        long anotherUser = unrelated.path("id").asLong();
        assertThat(permissions.grants()).containsEntry(OrderPermission.READ, OrderScope.DEPARTMENT)
                .containsEntry(OrderPermission.CANCEL, OrderScope.OWN);
        assertThat(permissions.allows(OrderPermission.READ, anotherUser, department)).isTrue();
        assertThat(permissions.allows(OrderPermission.CANCEL, anotherUser, department)).isFalse();
        assertThat(permissions.allows(OrderPermission.CANCEL, identity.userId(), department)).isTrue();
        assertThat(permissions.allows(OrderPermission.READ, anotherUser, outsideDepartment)).isFalse();
        assertThat(get("/me/menus", memberToken).getBody().get(0).path("routeKey").asText()).isEqualTo("orders");

        assertThat(request(HttpMethod.PUT, rolesRoute, admin, Map.of("roleIds", List.of(handlerId))).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        var narrowed = orderAccess.forSession(identity);
        assertThat(narrowed.grants()).containsEntry(OrderPermission.READ, OrderScope.OWN);
        assertThat(narrowed.allows(OrderPermission.READ, anotherUser, department)).isFalse();
        assertThat(request(HttpMethod.PUT, rolesRoute, admin, Map.of("roleIds", List.of())).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/me/menus", memberToken).getBody().isEmpty()).isTrue();
    }

    @Test
    void menusRejectCyclesAndAdministratorFlagCannotBeSelfAssigned() {
        var admin = access(login(ADMIN, ADMIN_PASSWORD));
        var member = newUser(admin);
        String memberId = member.path("id").asText();
        String memberToken = access(login(member.path("username").asText(), USER_PASSWORD));
        String toggle = "/api/v1/users/" + memberId + "/platform-admin";
        assertError(request(HttpMethod.PUT, toggle, memberToken, Map.of("enabled", true)),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertError(request(HttpMethod.PUT, toggle, admin, Map.of("enabled", "true")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertError(get("/menus", memberToken), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertError(request(HttpMethod.POST, "/api/v1/menus", admin, Map.of("label", "Fraction", "sortOrder", 1.5)),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        var first = request(HttpMethod.POST, "/api/v1/menus", admin, Map.of("label", "Group", "sortOrder", 80));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String parent = first.getBody().path("id").asText();
        var childMenu = request(HttpMethod.POST, "/api/v1/menus", admin,
                Map.of("label", "Child", "sortOrder", 80, "parentId", parent));
        assertThat(childMenu.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String child = childMenu.getBody().path("id").asText();
        var parentRoute = "/api/v1/menus/" + parent;
        assertError(request(HttpMethod.PATCH, parentRoute, admin,
                        Map.of("label", "Group", "sortOrder", 80, "parentId", child)),
                HttpStatus.CONFLICT, "RESOURCE_CONFLICT");
        assertError(request(HttpMethod.DELETE, parentRoute, admin, null),
                HttpStatus.CONFLICT, "RESOURCE_CONFLICT");
        var orderMenu = get("/menus", admin).getBody().get(0);
        String orderMenuRoute = "/api/v1/menus/" + orderMenu.path("id").asText();
        var moved = request(HttpMethod.PATCH, orderMenuRoute, admin, Map.of("parentId", parent));
        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            var renamed = request(HttpMethod.PATCH, orderMenuRoute, admin, Map.of("label", "Edited order menu"));
            assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(renamed.getBody().path("parentId").asText()).isEqualTo(parent);
            assertThat(renamed.getBody().path("routeKey").asText()).isEqualTo("orders");
            assertThat(renamed.getBody().path("permission").asText()).isEqualTo("orders:read");
            assertThat(get("/me/menus", admin).getBody()).anySatisfy(node -> {
                assertThat(node.path("id").asText()).isEqualTo(parent);
                assertThat(node.path("children").get(0).path("label").asText()).isEqualTo("Edited order menu");
            });
        } finally {
            var restored = request(HttpMethod.PATCH, orderMenuRoute, admin,
                    Map.of("label", orderMenu.path("label").asText()));
            assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
            var root = request(HttpMethod.PATCH, orderMenuRoute, admin,
                    new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().putNull("parentId"));
            assertThat(root.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(request(HttpMethod.DELETE, "/api/v1/menus/" + child, admin, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(request(HttpMethod.DELETE, parentRoute, admin, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(request(HttpMethod.PUT, toggle, admin, Map.of("enabled", true)).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/menus", memberToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.PUT, toggle, admin, Map.of("enabled", false)).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertError(get("/menus", memberToken), HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    @Test
    void simultaneousAdministratorRevocationsPreserveOneActiveAdministrator() throws Exception {
        String firstToken = access(login(ADMIN, ADMIN_PASSWORD));
        String firstId = get("/me", firstToken).getBody().path("id").asText();
        var second = newUser(firstToken);
        String secondId = second.path("id").asText();
        String secondToken = access(login(second.path("username").asText(), USER_PASSWORD));
        String firstRoute = "/api/v1/users/" + firstId + "/platform-admin";
        String secondRoute = "/api/v1/users/" + secondId + "/platform-admin";
        assertThat(request(HttpMethod.PUT, secondRoute, firstToken, Map.of("enabled", true)).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        try {
            var outcomes = simultaneousRequests(
                    () -> request(HttpMethod.PUT, firstRoute, firstToken, Map.of("enabled", false)),
                    () -> request(HttpMethod.PUT, secondRoute, secondToken, Map.of("enabled", false)));
            assertThat(outcomes).extracting(ResponseEntity::getStatusCode)
                    .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.CONFLICT);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_user WHERE platform_admin AND status = 'ACTIVE'", Long.class))
                    .isEqualTo(1);
        } finally {
            var firstStillAdmin = get("/roles", firstToken).getStatusCode() == HttpStatus.OK;
            if (!firstStillAdmin) {
                assertThat(request(HttpMethod.PUT, firstRoute, secondToken, Map.of("enabled", true)).getStatusCode())
                        .isEqualTo(HttpStatus.NO_CONTENT);
            }
            assertThat(request(HttpMethod.PUT, secondRoute, firstToken, Map.of("enabled", false)).getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    private JsonNode newUser(String admin) {
        String department = get("/departments", admin).getBody().get(0).path("id").asText();
        var created = request(HttpMethod.POST, "/api/v1/users", admin, Map.of(
                "username", "user-" + UUID.randomUUID().toString().replace("-", ""),
                "displayName", "Authorization test", "departmentId", department, "password", USER_PASSWORD));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody();
    }

    private JsonNode createRole(String admin, String label) {
        String code = "TEST_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        var created = request(HttpMethod.POST, "/api/v1/roles", admin, Map.of("code", code, "name", label));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody();
    }

    private void replaceGrants(String admin, String id, List<Map<String, String>> grants) {
        var response = request(HttpMethod.PUT, "/api/v1/roles/" + id + "/grants", admin, Map.of("grants", grants));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
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

    private List<ResponseEntity<JsonNode>> simultaneousRequests(Supplier<ResponseEntity<JsonNode>> first,
            Supplier<ResponseEntity<JsonNode>> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var left = workers.submit(() -> awaitStart(first, ready, start));
            var right = workers.submit(() -> awaitStart(second, ready, start));
            boolean reachedStart = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertThat(reachedStart).isTrue();
            return List.of(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS));
        }
    }

    private ResponseEntity<JsonNode> awaitStart(Supplier<ResponseEntity<JsonNode>> operation,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test did not start");
        }
        return operation.get();
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
