package dev.javafornestjs.capstone;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CapstoneApplicationTest {
    @Autowired
    private TestRestTemplate http;

    @Test
    void healthEndpointRespondsOverHttp() {
        var health = http.getForEntity("/actuator/health", Health.class);

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).isEqualTo(new Health("UP"));
    }

    private record Health(String status) {
    }
}
