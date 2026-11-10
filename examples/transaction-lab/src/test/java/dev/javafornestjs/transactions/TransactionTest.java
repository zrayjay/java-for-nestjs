package dev.javafornestjs.transactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.UnexpectedRollbackException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private TransactionLab.Actions actions;

    @BeforeAll
    void connectAndMigrate() {
        context = new AnnotationConfigApplicationContext(TransactionLab.Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        actions = context.getBean(TransactionLab.Actions.class);
        assertTrue(Proxy.isProxyClass(actions.getClass()), "Actual JDK transactional proxy required");
    }

    @AfterAll
    void close() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void resetDedicatedFixture() {
        jdbc.update("DELETE FROM tx_lab_entries");
        jdbc.update("UPDATE tx_lab_balances SET amount=100 WHERE id=1");
    }

    private List<String> labels() {
        return jdbc.queryForList("SELECT label FROM tx_lab_entries ORDER BY id", String.class);
    }

    @Test
    void normalReturnCommits() {
        actions.commit();
        assertEquals(List.of("commit"), labels());
    }

    @Test
    void runtimeExceptionRollsBack() {
        assertThrows(TransactionLab.Failure.class, actions::runtimeRollback);
        assertEquals(List.of(), labels());
    }

    @Test
    void checkedExceptionCommitsByDefault() {
        assertThrows(TransactionLab.CheckedFailure.class, actions::checkedDefault);
        assertEquals(List.of("checked-default"), labels());
    }

    @Test
    void explicitCheckedRollbackRuleRollsBack() {
        assertThrows(TransactionLab.CheckedFailure.class, actions::checkedExplicit);
        assertEquals(List.of(), labels());
    }

    @Test
    void selfInvocationBypassesTheDesiredTransaction() {
        assertThrows(TransactionLab.Failure.class, actions::selfInvocation);
        assertEquals(List.of("runtime"), labels());
    }

    @Test
    void caughtRequiredFailureStillMakesOuterCommitFail() {
        assertThrows(UnexpectedRollbackException.class, actions::requiredCaught);
        assertEquals(List.of(), labels());
    }

    @Test
    void independentInnerCommitSurvivesOuterRollback() {
        assertThrows(TransactionLab.Failure.class, actions::independentCommitSurvives);
        assertEquals(List.of("inner-independent"), labels());
    }

    @Test
    void readCommittedObservesTheOtherCommittedUpdate() {
        assertEquals(new TransactionLab.ReadPair(100, 200), actions.readCommitted());
    }

    @Test
    void repeatableReadKeepsTheFirstConsistentReadSnapshot() {
        assertEquals(new TransactionLab.ReadPair(100, 100), actions.repeatableRead());
    }
}
