package dev.javafornestjs.messaging;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Optional;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class MessageStore implements AutoCloseable {
    public enum Applied { APPLIED, DUPLICATE }
    private final HikariDataSource pool;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public MessageStore(String url, String user, String password) {
        pool = new HikariDataSource();
        pool.setJdbcUrl(MessageEnvironment.jdbc(url));
        pool.setUsername(user);
        pool.setPassword(password);
        pool.setMaximumPoolSize(4);
        pool.setConnectionTimeout(3000);
        jdbc = new JdbcTemplate(pool);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(pool));
        try {
            Flyway.configure().dataSource(pool).locations("classpath:db/message-migration").cleanDisabled(true).load().migrate();
        } catch (RuntimeException error) {
            pool.close();
            throw error;
        }
    }
    public Applied apply(TaskEvent event, Runnable beforeCommit) {
        return transactions.execute(status -> {
            int inserted = jdbc.update("INSERT IGNORE INTO processed_events(consumer_name,event_id,task_key) VALUES ('projection-v1',?,?)", event.eventId(), event.taskKey());
            if (inserted == 0) {
                String original = jdbc.queryForObject("SELECT task_key FROM processed_events WHERE consumer_name='projection-v1' AND event_id=? FOR UPDATE", String.class, event.eventId());
                if (!event.taskKey().equals(original)) { throw new TaskEvent.PermanentMessageFailure("Event identity conflicts with stored payload"); }
                return Applied.DUPLICATE;
            }
            jdbc.update("INSERT INTO projection_effects(task_key,applied_count) VALUES (?,1) ON DUPLICATE KEY UPDATE applied_count=applied_count+1", event.taskKey());
            beforeCommit.run();
            return Applied.APPLIED;
        });
    }
    public int effects(String taskKey) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(applied_count),0) FROM projection_effects WHERE task_key=?", Integer.class, taskKey);
    }
    public int markers() { return jdbc.queryForObject("SELECT COUNT(*) FROM processed_events", Integer.class); }
    public record Claim(TaskEvent event, String token) { }
    public void enqueueTask(TaskEvent event, Runnable beforeCommit) {
        transactions.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO outbox_tasks(task_key,event_id) VALUES (?,?)", event.taskKey(), event.eventId());
            jdbc.update("INSERT INTO message_outbox(event_id,task_key) VALUES (?,?)", event.eventId(), event.taskKey());
            beforeCommit.run();
        });
    }
    public Optional<Claim> claim(int leaseMillis) {
        if (leaseMillis < 100 || leaseMillis > 5000) { throw new IllegalArgumentException("Teaching lease must be 100..5000ms"); }
        return transactions.execute(status -> {
            var events = jdbc.query("SELECT event_id,task_key FROM message_outbox WHERE sent_at IS NULL AND (lease_until IS NULL OR lease_until<=CURRENT_TIMESTAMP(6)) ORDER BY event_id LIMIT 1 FOR UPDATE SKIP LOCKED",
                    (row, index) -> new TaskEvent(row.getString("event_id"), "task.created", row.getString("task_key")));
            if (events.isEmpty()) { return Optional.empty(); }
            var event = events.getFirst();
            String token = UUID.randomUUID().toString();
            jdbc.update("UPDATE message_outbox SET lease_token=?,lease_until=TIMESTAMPADD(MICROSECOND,?,CURRENT_TIMESTAMP(6)) WHERE event_id=?", token, leaseMillis * 1000, event.eventId());
            return Optional.of(new Claim(event, token));
        });
    }
    public boolean markSent(Claim claim) {
        return jdbc.update("UPDATE message_outbox SET sent_at=CURRENT_TIMESTAMP(6),lease_token=NULL,lease_until=NULL WHERE event_id=? AND lease_token=? AND sent_at IS NULL AND lease_until>CURRENT_TIMESTAMP(6)",
                claim.event().eventId(), claim.token()) == 1;
    }
    public int businessTasks() { return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_tasks", Integer.class); }
    public int pending() { return jdbc.queryForObject("SELECT COUNT(*) FROM message_outbox WHERE sent_at IS NULL", Integer.class); }
    public void resetFixture() {
        jdbc.update("DELETE FROM message_outbox");
        jdbc.update("DELETE FROM outbox_tasks");
        jdbc.update("DELETE FROM processed_events");
        jdbc.update("DELETE FROM projection_effects");
    }
    @Override public void close() { pool.close(); }
}
