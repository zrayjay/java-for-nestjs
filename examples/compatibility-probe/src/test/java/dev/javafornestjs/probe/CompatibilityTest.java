package dev.javafornestjs.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompatibilityTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.7");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4.6-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired ApplicationContext context;
    @Autowired ProbeUserMapper mapper;
    @Autowired TransactionTemplate transaction;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void cache() {
        redis.opsForValue().set("probe:status", "ready");
    }

    @Test
    void unauthorizedRequestIsRejected() throws Exception {
        assertThat(get("/probe", false).statusCode()).isEqualTo(401);
    }

    @Test
    void authenticatedRequestUsesMigratedDatabaseAndRedis() throws Exception {
        var response = get("/probe", true);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(((Number) JsonPath.read(response.body(), "$.users")).intValue()).isEqualTo(1);
        assertThat((String) JsonPath.read(response.body(), "$.cache")).isEqualTo("ready");
    }

    @Test
    void failedTransactionRollsBackMapperWrite() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            var user = new ProbeUser();
            user.setId(2L);
            user.setName("rollback");
            assertThat(mapper.insert(user)).isEqualTo(1);
            throw new IllegalStateException("intentional rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(mapper.selectById(2L)).isNull();
        assertThat(mapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    void openApiContainsProbeRoute() throws Exception {
        var response = get("/v3/api-docs", false);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((Object) JsonPath.read(response.body(), "$.paths['/probe'].get")).isNotNull();
    }

    private HttpResponse<String> get(String path, boolean authenticated) throws Exception {
        var port = context.getEnvironment().getRequiredProperty("local.server.port");
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (authenticated) {
            var basic = Base64.getEncoder().encodeToString(
                    "probe:probe-only-password".getBytes(StandardCharsets.UTF_8));
            request.header("Authorization", "Basic " + basic);
        }
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
