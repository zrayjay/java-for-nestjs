package dev.javafornestjs.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public record TaskEvent(String eventId, String kind, String taskKey) {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public TaskEvent {
        if (eventId == null || !eventId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || !"task.created".equals(kind) || taskKey == null || !taskKey.matches("[a-z][a-z0-9-]{0,39}")) {
            throw new PermanentMessageFailure("Invalid event");
        }
    }
    public static TaskEvent decode(byte[] bytes) {
        if (bytes.length > 1024) { throw new PermanentMessageFailure("Event exceeds 1KiB"); }
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\uFEFF")) { text = text.substring(1); }
            var node = JSON.readTree(text);
            if (node == null || !node.isObject() || node.size() != 3) { throw new PermanentMessageFailure("Invalid event fields"); }
            var names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!Set.of("eventId", "kind", "taskKey").contains(name) || !node.path(name).isTextual()) {
                    throw new PermanentMessageFailure("Invalid event fields");
                }
            }
            return new TaskEvent(node.path("eventId").asText(), node.path("kind").asText(), node.path("taskKey").asText());
        } catch (PermanentMessageFailure error) { throw error; }
        catch (Exception error) { throw new PermanentMessageFailure("Invalid event JSON"); }
    }
    public byte[] encode() {
        try { return JSON.writeValueAsBytes(this); }
        catch (Exception error) { throw new IllegalStateException("Could not encode event", error); }
    }
    public static final class PermanentMessageFailure extends RuntimeException {
        public PermanentMessageFailure(String message) { super(message); }
    }
}
