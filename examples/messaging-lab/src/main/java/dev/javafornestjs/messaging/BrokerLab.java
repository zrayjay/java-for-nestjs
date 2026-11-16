package dev.javafornestjs.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BrokerLab implements AutoCloseable {
    public enum Outcome { APPLIED, DUPLICATE, RETRY, DEAD, COMMITTED_UNACKED }
    public record PublishReceipt(boolean confirmed, boolean returned) { }
    public record DeliveryResult(Outcome outcome, int attempt, boolean redelivered, String eventId) { }
    private final Connection connection;
    private final String prefix = "teaching." + UUID.randomUUID();
    private final String work = prefix + ".work";
    private final String dead = prefix + ".dead";
    private final String dlx = prefix + ".dlx";
    public BrokerLab(String url) throws Exception {
        var factory = new ConnectionFactory();
        factory.setUri(MessageEnvironment.amqp(url));
        factory.setAutomaticRecoveryEnabled(false);
        factory.setConnectionTimeout(3000);
        factory.setHandshakeTimeout(3000);
        factory.setChannelRpcTimeout(3000);
        factory.setRequestedHeartbeat(5);
        connection = factory.newConnection("teaching-messaging");
        try (var channel = connection.createChannel()) {
            channel.exchangeDeclare(dlx, "direct", true);
            channel.queueDeclare(dead, true, false, false, null);
            channel.queueBind(dead, dlx, "failed");
            channel.queueDeclare(work, true, false, false, Map.of(
                    "x-dead-letter-exchange", dlx, "x-dead-letter-routing-key", "failed"));
        } catch (Exception error) {
            connection.close();
            throw error;
        }
    }
    public Channel consumer() throws IOException { return connection.createChannel(); }
    private PublishReceipt publish(byte[] bytes, Object attempt, String routing) throws Exception {
        try (var channel = connection.createChannel()) {
            var returned = new AtomicBoolean();
            channel.confirmSelect();
            channel.addReturnListener((code, text, exchange, key, properties, body) -> returned.set(true));
            var properties = new AMQP.BasicProperties.Builder().deliveryMode(2).contentType("application/json")
                    .contentEncoding("utf-8").messageId(UUID.randomUUID().toString())
                    .headers(Map.of("attempt", attempt)).build();
            channel.basicPublish("", routing, true, properties, bytes);
            channel.waitForConfirmsOrDie(5000);
            return new PublishReceipt(true, returned.get());
        }
    }
    public void send(TaskEvent event) throws Exception { sendRaw(event.encode(), 0); }
    public void sendRaw(byte[] bytes, Object attempt) throws Exception {
        var receipt = publish(bytes, attempt, work);
        if (receipt.returned()) { throw new IOException("Message was confirmed but unroutable"); }
    }
    public PublishReceipt probeUnroutable(TaskEvent event) throws Exception { return publish(event.encode(), 0, prefix + ".missing"); }
    private static GetResponse get(Channel channel, String queue, boolean autoAck) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            var delivery = channel.basicGet(queue, autoAck);
            if (delivery != null) { return delivery; }
            Thread.sleep(20);
        }
        throw new IllegalStateException("No broker delivery within deadline");
    }
    private static int attempt(GetResponse delivery) {
        Object value = delivery.getProps().getHeaders() == null ? null : delivery.getProps().getHeaders().get("attempt");
        if (value == null) { return 0; }
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue() || number.intValue() < 0 || number.intValue() > 2) {
            throw new TaskEvent.PermanentMessageFailure("Invalid retry attempt header");
        }
        return ((Number) value).intValue();
    }
    public DeliveryResult consume(Channel channel, MessageStore store, Runnable beforeCommit, boolean closeAfterCommit) throws Exception {
        var delivery = get(channel, work, false);
        long tag = delivery.getEnvelope().getDeliveryTag();
        boolean redelivered = delivery.getEnvelope().isRedeliver();
        int current;
        TaskEvent event;
        try {
            current = attempt(delivery);
            event = TaskEvent.decode(delivery.getBody());
        } catch (TaskEvent.PermanentMessageFailure error) {
            channel.basicReject(tag, false);
            return observed(Outcome.DEAD, 0, redelivered);
        }
        MessageStore.Applied result;
        try { result = store.apply(event, beforeCommit); }
        catch (TaskEvent.PermanentMessageFailure error) {
            channel.basicReject(tag, false);
            return observed(Outcome.DEAD, current, redelivered, event.eventId());
        } catch (RuntimeException error) {
            if (current >= 2) {
                channel.basicReject(tag, false);
                return observed(Outcome.DEAD, current, redelivered, event.eventId());
            }
            // Publish/confirm first. A failure leaves the original unacked; duplicate retries remain possible.
            sendRaw(delivery.getBody(), current + 1);
            channel.basicAck(tag, false);
            return observed(Outcome.RETRY, current, redelivered, event.eventId());
        }
        // These operations are deliberately outside the SQL failure handler: commit already succeeded.
        if (closeAfterCommit) {
            channel.close();
            return observed(Outcome.COMMITTED_UNACKED, current, redelivered, event.eventId());
        }
        channel.basicAck(tag, false);
        return observed(result == MessageStore.Applied.APPLIED ? Outcome.APPLIED : Outcome.DUPLICATE, current, redelivered, event.eventId());
    }
    private DeliveryResult observed(Outcome outcome, int attempt, boolean redelivered) {
        return observed(outcome, attempt, redelivered, null);
    }
    private DeliveryResult observed(Outcome outcome, int attempt, boolean redelivered, String eventId) {
        var result = new DeliveryResult(outcome, attempt, redelivered, eventId);
        System.out.println("MESSAGE_RESULT " + result);
        return result;
    }
    public GetResponse readDead() throws Exception {
        try (var channel = connection.createChannel()) { return get(channel, dead, true); }
    }
    @Override public void close() throws Exception {
        try (var channel = connection.createChannel()) {
            channel.queueDelete(work);
            channel.queueDelete(dead);
            channel.exchangeDelete(dlx);
        } finally { connection.close(); }
    }
}
