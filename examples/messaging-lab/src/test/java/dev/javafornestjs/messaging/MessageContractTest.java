package dev.javafornestjs.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageContractTest {
    @Test
    void destructiveFixtureTargetsRequireDedicatedLoopbackSchemaAndVhost() {
        for (String value : List.of("jdbc:mysql://example.com:3306/message_lab", "jdbc:mysql://127.0.0.1:3306/production",
                "jdbc:mysql://127.0.0.1:3306/message_lab_backup", "jdbc:mysql://127.0.0.1:3306/message_lab?database=production")) {
            assertThrows(IllegalArgumentException.class, () -> MessageEnvironment.jdbc(value));
        }
        for (String value : List.of("amqp://lab:pass@example.com:5672/message_lab", "amqp://lab:pass@127.0.0.1:5672/",
                "amqp://lab:pass@127.0.0.1:5672/message_lab?other=1", "amqp://127.0.0.1:5672/message_lab")) {
            assertThrows(IllegalArgumentException.class, () -> MessageEnvironment.amqp(value));
        }
    }
    @Test
    void eventContractPreservesStableIdentityAndRejectsInvalidShapes() {
        var event = new TaskEvent(UUID.randomUUID().toString(), "task.created", "task-one");
        assertEquals(event, TaskEvent.decode(event.encode()));
        for (String value : List.of("{}", "[]", "null", "{", "{} {}",
                "{\"eventId\":\"bad\",\"kind\":\"task.created\",\"taskKey\":\"task-one\"}")) {
            assertThrows(TaskEvent.PermanentMessageFailure.class, () -> TaskEvent.decode(value.getBytes(StandardCharsets.UTF_8)));
        }
        assertThrows(TaskEvent.PermanentMessageFailure.class, () -> TaskEvent.decode(new byte[1025]));
    }
}
