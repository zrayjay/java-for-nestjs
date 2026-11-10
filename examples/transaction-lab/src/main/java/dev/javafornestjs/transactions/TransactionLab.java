package dev.javafornestjs.transactions;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class TransactionLab {
    private TransactionLab() { }

    public static final class Failure extends RuntimeException {
        public Failure(String message) { super(message); }
    }

    public static final class CheckedFailure extends Exception {
        public CheckedFailure() { super("checked-failure"); }
    }

    public record ReadPair(int first, int second) { }

    public interface Actions {
        void commit();
        void runtimeRollback();
        void checkedDefault() throws CheckedFailure;
        void checkedExplicit() throws CheckedFailure;
        void selfInvocation();
        void requiredCaught();
        void independentCommitSurvives();
        ReadPair readCommitted();
        ReadPair repeatableRead();
    }

    public interface InnerActions {
        void requiredFailure();
        void independentCommit();
        void changeBalance();
    }

    public static final class Service implements Actions {
        private final JdbcTemplate jdbc;
        private final InnerActions inner;

        public Service(JdbcTemplate jdbc, InnerActions inner) {
            this.jdbc = jdbc;
            this.inner = inner;
        }

        @Override
        @Transactional(timeout = 10)
        public void commit() { insert(jdbc, "commit"); }

        @Override
        @Transactional(timeout = 10)
        public void runtimeRollback() {
            insert(jdbc, "runtime");
            throw new Failure("runtime-failure");
        }

        @Override
        @Transactional(timeout = 10)
        public void checkedDefault() throws CheckedFailure {
            insert(jdbc, "checked-default");
            throw new CheckedFailure();
        }

        @Override
        @Transactional(rollbackFor = CheckedFailure.class, timeout = 10)
        public void checkedExplicit() throws CheckedFailure {
            insert(jdbc, "checked-explicit");
            throw new CheckedFailure();
        }

        @Override
        public void selfInvocation() {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("This experiment requires no ambient transaction");
            }
            runtimeRollback();
        }

        @Override
        @Transactional(timeout = 10)
        public void requiredCaught() {
            try {
                inner.requiredFailure();
            } catch (Failure expected) {
                // Deliberate negative example: catching does not clear Spring's rollback-only marker.
            }
            insert(jdbc, "outer-required");
        }

        @Override
        @Transactional(timeout = 10)
        public void independentCommitSurvives() {
            insert(jdbc, "outer-independent");
            inner.independentCommit();
            throw new Failure("outer-failure");
        }

        private ReadPair readTwice() {
            int first = jdbc.queryForObject("SELECT amount FROM tx_lab_balances WHERE id=1", Integer.class);
            inner.changeBalance();
            int second = jdbc.queryForObject("SELECT amount FROM tx_lab_balances WHERE id=1", Integer.class);
            return new ReadPair(first, second);
        }

        @Override
        @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
        public ReadPair readCommitted() { return readTwice(); }

        @Override
        @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 10)
        public ReadPair repeatableRead() { return readTwice(); }
    }

    public static final class InnerService implements InnerActions {
        private final JdbcTemplate jdbc;

        public InnerService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @Override
        @Transactional(timeout = 10)
        public void requiredFailure() {
            insert(jdbc, "inner-required");
            throw new Failure("inner-failure");
        }

        @Override
        @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
        public void independentCommit() { insert(jdbc, "inner-independent"); }

        @Override
        @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, timeout = 10)
        public void changeBalance() { jdbc.update("UPDATE tx_lab_balances SET amount=200 WHERE id=1"); }
    }

    private static void insert(JdbcTemplate jdbc, String label) {
        jdbc.update("INSERT INTO tx_lab_entries(label) VALUES (?)", label);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = false)
    public static class Config {
        @Bean(destroyMethod = "close")
        HikariDataSource dataSource() {
            String url = System.getenv("TX_JDBC_URL");
            if (url == null || !url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/transaction_lab(\\?.*)?")) {
                throw new IllegalArgumentException("TX_JDBC_URL must select the local transaction_lab test database");
            }
            var config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(System.getenv("TX_DATABASE_USER"));
            config.setPassword(System.getenv("TX_DATABASE_PASSWORD"));
            config.setMaximumPoolSize(4);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(5000);
            config.setInitializationFailTimeout(5000);
            config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
            config.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout=5");
            return new HikariDataSource(config);
        }

        @Bean
        Flyway flyway(DataSource source) {
            var flyway = Flyway.configure().dataSource(source).cleanDisabled(true).load();
            flyway.migrate();
            return flyway;
        }

        @Bean
        @DependsOn("flyway")
        JdbcTemplate jdbcTemplate(DataSource source) {
            var jdbc = new JdbcTemplate(source);
            jdbc.setQueryTimeout(10);
            return jdbc;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        InnerActions inner(JdbcTemplate jdbc) { return new InnerService(jdbc); }

        @Bean
        Actions actions(JdbcTemplate jdbc, InnerActions inner) { return new Service(jdbc, inner); }
    }
}
