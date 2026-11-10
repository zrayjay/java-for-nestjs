import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** JDK-only native HTTP check, independent of Spring, Docker and database drivers. */
class HttpClientProbe {
    public static void main(String[] args) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/probe", exchange -> {
            byte[] body = "native-http-ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var response = exchange.getResponseBody()) {
                response.write(body);
            }
        });
        server.start();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/probe");
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || !response.body().equals("native-http-ok")) {
                throw new AssertionError("Unexpected HTTP response: " + response);
            }
            System.out.println("NATIVE_HTTP_OK " + Runtime.version());
        } finally {
            server.stop(0);
        }
    }
}
