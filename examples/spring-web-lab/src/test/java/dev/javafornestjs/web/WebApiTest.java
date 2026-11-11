package dev.javafornestjs.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lab.storage=memory")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebApiTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @MockitoSpyBean InMemoryTasks repository;
    @Autowired RequestTrace traces;
    @Autowired MockMvc mvc;

    @BeforeEach
    void clear() { repository.clearForTest(); }

    private String url(String path) { return "http://127.0.0.1:" + port + path; }
    private ResponseEntity<JsonNode> send(HttpMethod method, String path, String json) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url(path), method, new HttpEntity<>(json, headers), JsonNode.class);
    }
    private ResponseEntity<JsonNode> create() {
        return send(HttpMethod.POST, "/tasks", "{\"title\":\"Learn Java\",\"details\":{\"priority\":2}}");
    }

    @Test
    void actualHttpCrudPreservesFieldsStatusesAndLocation() {
        var created = create();
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        int id = created.getBody().path("id").asInt();
        assertTrue(id > 0);
        assertEquals("/tasks/" + id, created.getHeaders().getLocation().toString());
        var found = http.getForEntity(url("/tasks/" + id), JsonNode.class);
        assertEquals("Learn Java", found.getBody().path("title").asText());
        assertEquals(2, found.getBody().path("details").path("priority").asInt());
        var updated = send(HttpMethod.PUT, "/tasks/" + id,
                "{\"title\":\"Learn Spring\",\"details\":{\"priority\":3}}");
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertEquals("Learn Spring", updated.getBody().path("title").asText());
        var removed = http.exchange(url("/tasks/" + id), HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
        assertEquals(HttpStatus.NO_CONTENT, removed.getStatusCode());
        assertTrue(removed.getBody() == null || removed.getBody().isEmpty());
        var missing = http.getForEntity(url("/tasks/" + id), JsonNode.class);
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals("not_found", missing.getBody().path("code").asText());
    }

    @Test
    void unexpectedRepositoryFailureIsSanitizedOnRealHttp() {
        doThrow(new IllegalStateException("private-database-detail"))
                .when(repository).create("Crash", 1);
        var result = send(HttpMethod.POST, "/tasks", "{\"title\":\"Crash\",\"details\":{\"priority\":1}}");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertEquals("internal_error", result.getBody().path("code").asText());
        assertEquals(1, result.getBody().size());
    }

    @Test
    void nestedAndUnknownInputIsRejectedWithoutLeakingMessages() {
        for (String json : List.of("{}", "{",
                "{\"title\":\" \" ,\"details\":{\"priority\":2}}",
                "{\"title\":\"Learn\",\"details\":{\"priority\":0}}",
                "{\"title\":\"Learn\",\"details\":{\"priority\":1.5}}",
                "{\"title\":\"Learn\",\"details\":{\"priority\":\"1\"}}",
                "{\"title\":17,\"details\":{\"priority\":1}}",
                "{\"title\":\"Learn\",\"details\":null}",
                "{\"title\":\"Learn\",\"details\":{\"priority\":1,\"integral\":true}}",
                "{\"title\":\"Learn\",\"details\":{\"priority\":1},\"extra\":true}")) {
            var result = send(HttpMethod.POST, "/tasks", json);
            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode(), json);
            assertEquals("invalid_request", result.getBody().path("code").asText());
            assertEquals(1, result.getBody().size());
        }
    }

    @Test
    void integralJsonNumberIsAcceptedRegardlessOfDecimalTokenSpelling() {
        var result = send(HttpMethod.POST, "/tasks", "{\"title\":\"Learn\",\"details\":{\"priority\":1.0}}");
        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(1, result.getBody().path("details").path("priority").asInt());
    }

    @Test
    void canonicalIdAndQueryBoundsHaveNegativeCases() {
        for (String path : List.of("/tasks/0", "/tasks/01", "/tasks/2147483648", "/tasks/abc",
                "/tasks?limit=0", "/tasks?limit=101", "/tasks?limit=1e1", "/tasks?limit=abc")) {
            var response = http.getForEntity(url(path), JsonNode.class);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), path);
        }
    }

    @Test
    void validationGroupsDistinguishDraftAndPublishOnRealHttp() {
        assertEquals(HttpStatus.NO_CONTENT, send(HttpMethod.POST, "/validation/draft", "{\"title\":\"\"}").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, send(HttpMethod.POST, "/validation/publish", "{\"title\":\"\"}").getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, send(HttpMethod.POST, "/validation/publish", "{\"title\":\"Learn\"}").getStatusCode());
    }

    @Test
    void actualFilterSecurityAndMvcSequenceHasPerRequestEvidence() throws Exception {
        var response = http.getForEntity(url("/tasks"), JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("filter.before", "security.before", "interceptor.pre", "controller",
                "interceptor.post", "interceptor.after", "security.after", "filter.after"),
                traces.take(response.getHeaders().getFirst("X-Lab-Request-Id")));
        var invalid = send(HttpMethod.POST, "/tasks", "{}");
        var errorTrace = traces.take(invalid.getHeaders().getFirst("X-Lab-Request-Id"));
        assertFalse(errorTrace.contains("controller"));
        assertTrue(errorTrace.contains("interceptor.after"));
        assertEquals("filter.after", errorTrace.getLast());
    }

    @Test
    void profileBindingActuatorExposureAndOpenApiAreObservable() throws Exception {
        assertEquals("test-profile", http.getForObject(url("/lab-info"), JsonNode.class).path("label").asText());
        assertEquals("UP", http.getForObject(url("/actuator/health"), JsonNode.class).path("status").asText());
        assertEquals(HttpStatus.NOT_FOUND, http.getForEntity(url("/actuator/env"), JsonNode.class).getStatusCode());
        var schema = http.getForObject(url("/openapi.json"), JsonNode.class);
        assertTrue(schema.path("paths").has("/tasks"));
        assertTrue(schema.path("paths").path("/tasks").path("post").path("responses").has("201"));
        mvc.perform(get("/tasks")).andExpect(status().isOk());
    }
}
