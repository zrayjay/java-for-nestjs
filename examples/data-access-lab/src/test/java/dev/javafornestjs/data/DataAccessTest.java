package dev.javafornestjs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariConfig;
import dev.javafornestjs.data.jpa.TaskEntity;
import java.sql.SQLTransientConnectionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = DataApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("data")
class DataAccessTest {
    @Autowired HikariDataSource source;
    @Autowired JdbcTemplate jdbc;
    @Autowired TaskMapper mapper;
    @Autowired SqlCounter counter;
    @Autowired Flyway migration;
    @Autowired TestRestTemplate http;
    @LocalServerPort int port;
    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM data_tasks");
        counter.reset();
    }
    private String url(String path) { return "http://127.0.0.1:" + port + path; }
    private TaskRow row(String title, int owner) {
        var row = new TaskRow();
        row.setTitle(title);
        row.setPriority(2);
        row.setOwnerId(owner);
        mapper.insert(row);
        return row;
    }
    @Test
    void controllerServiceMapperAndMysqlPreserveTheHttpContract() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var input = new HttpEntity<>("{\"title\":\"Learn Java\",\"details\":{\"priority\":2}}", headers);
        var created = http.postForEntity(url("/tasks"), input, JsonNode.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        int id = created.getBody().path("id").asInt();
        assertTrue(id > 0);
        assertEquals("/tasks/" + id, created.getHeaders().getLocation().toString());
        assertEquals("Learn Java", jdbc.queryForObject("SELECT title FROM data_tasks WHERE id=?", String.class, id));
        assertEquals(2, http.getForObject(url("/tasks/" + id), JsonNode.class).path("details").path("priority").asInt());
        var replaced = http.exchange(url("/tasks/" + id), HttpMethod.PUT,
                new HttpEntity<>("{\"title\":\"Learn SQL\",\"details\":{\"priority\":5}}", headers), JsonNode.class);
        assertEquals(HttpStatus.OK, replaced.getStatusCode());
        assertEquals("Learn SQL", replaced.getBody().path("title").asText());
        assertEquals(5, jdbc.queryForObject("SELECT priority FROM data_tasks WHERE id=?", Integer.class, id));
        var deleted = http.exchange(url("/tasks/" + id), HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM data_tasks WHERE id=?", Integer.class, id));
        assertEquals(HttpStatus.NOT_FOUND, http.getForEntity(url("/tasks/" + id), JsonNode.class).getStatusCode());
    }
    @Test
    void wrapperBindsInputAndPaginationExecutesRealMysqlSql() {
        row("Alpha", 1);
        row("Beta", 2);
        row("Gamma", 3);
        var attack = new LambdaQueryWrapper<TaskRow>().eq(TaskRow::getTitle, "x' OR 1=1 --");
        assertTrue(mapper.selectList(attack).isEmpty());
        var page = mapper.selectPage(new Page<TaskRow>(2, 2),
                new LambdaQueryWrapper<TaskRow>().orderByAsc(TaskRow::getId));
        assertEquals(3, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals("Gamma", page.getRecords().getFirst().getTitle());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM data_tasks", Integer.class));
    }
    @Test
    void distinctOwnerNPlusOneUsesFourStatementsAndJoinUsesOne() {
        row("Alpha", 1);
        row("Beta", 2);
        row("Gamma", 3);
        counter.reset();
        var names = new ArrayList<String>();
        for (var task : mapper.listLimited(100)) { names.add(mapper.ownerName(task.getOwnerId())); }
        assertEquals(List.of("Alice", "Bob", "Cara"), names);
        assertEquals(4, counter.count());
        counter.reset();
        var joined = mapper.joinedOwners();
        assertEquals(names, joined.stream().map(TaskMapper.OwnedTask::owner).toList());
        assertEquals(1, counter.count());
    }
    @Test
    void exhaustedPoolTimesOutAndClosedLogicalConnectionsReturnToPool() throws Exception {
        try (var first = source.getConnection(); var second = source.getConnection()) {
            assertEquals(2, source.getHikariPoolMXBean().getActiveConnections());
            assertThrows(SQLTransientConnectionException.class, source::getConnection);
            assertFalse(first.isClosed());
            assertFalse(second.isClosed());
        }
        assertEquals(0, source.getHikariPoolMXBean().getActiveConnections());
        try (var reused = source.getConnection(); var statement = reused.prepareStatement("SELECT 1")) {
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }
    }
    @Test
    void migrationsAreVersionedValidatedAndExposeAnActualMysqlExecutionPlan() throws Exception {
        row("Alpha", 1);
        migration.validate();
        assertEquals(2, migration.info().applied().length);
        assertEquals(0, migration.migrate().migrationsExecuted);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM data_schema_history WHERE success=1 AND version IS NOT NULL", Integer.class));
        var plan = jdbc.queryForList("EXPLAIN SELECT id,title FROM data_tasks WHERE title=?", "Alpha");
        assertEquals(1, plan.size());
        assertNotNull(plan.getFirst().get("possible_keys"));
        assertTrue(plan.getFirst().get("possible_keys").toString().contains("ix_data_tasks_title"));
        var index = jdbc.queryForList("SHOW INDEX FROM data_tasks WHERE Key_name='ix_data_tasks_title'");
        assertEquals(2, index.size());
        var rows = java.util.stream.IntStream.range(0, 4000)
                .mapToObj(number -> new Object[]{"Plan " + number, 2, 1}).toList();
        jdbc.batchUpdate("INSERT INTO data_tasks(title,priority,owner_id) VALUES (?,?,?)", rows);
        jdbc.execute("ANALYZE TABLE data_tasks");
        String indexed = "SELECT id,title FROM data_tasks WHERE title=?";
        String ignored = "SELECT id,title FROM data_tasks IGNORE INDEX (ix_data_tasks_title) WHERE title=?";
        var availablePlan = jdbc.queryForMap("EXPLAIN " + indexed, "Plan 1999");
        var ignoredPlan = jdbc.queryForMap("EXPLAIN " + ignored, "Plan 1999");
        assertEquals("ix_data_tasks_title", availablePlan.get("key"));
        assertEquals("ALL", ignoredPlan.get("type"));
        assertTrue(((Number) availablePlan.get("rows")).longValue() < ((Number) ignoredPlan.get("rows")).longValue());
        assertEquals(jdbc.queryForList(indexed, "Plan 1999"), jdbc.queryForList(ignored, "Plan 1999"));
        String availableActual = jdbc.queryForObject("EXPLAIN ANALYZE " + indexed, String.class, "Plan 1999");
        String ignoredActual = jdbc.queryForObject("EXPLAIN ANALYZE " + ignored, String.class, "Plan 1999");
        assertTrue(availableActual.contains("actual time="));
        assertTrue(ignoredActual.contains("actual time="));
        Files.writeString(Path.of("target", "sql-plan-comparison.txt"), "Fixture: 4001 tasks; one title match.\n"
                + "Available index EXPLAIN: " + availablePlan + "\n" + availableActual + "\n"
                + "Ignored index EXPLAIN: " + ignoredPlan + "\n" + ignoredActual + "\n"
                + "Controlled access-path comparison, not a production speed guarantee.\n");
    }

    @Test
    void jpaIdentityMapDirtyCheckingDetachAndMergeUseTheRealDatabase() {
        var factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setDataSource(source);
        factoryBean.setPackagesToScan("dev.javafornestjs.data.jpa");
        factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factoryBean.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
        factoryBean.afterPropertiesSet();
        try (var factory = factoryBean.getObject(); var manager = factory.createEntityManager()) {
            var task = new TaskEntity();
            task.setTitle("Managed");
            task.setPriority(2);
            task.setOwnerId(1);
            manager.getTransaction().begin();
            manager.persist(task);
            manager.getTransaction().commit();
            assertSame(task, manager.find(TaskEntity.class, task.getId()));
            manager.getTransaction().begin();
            task.setTitle("Dirty checked");
            manager.getTransaction().commit();
            assertEquals("Dirty checked", jdbc.queryForObject("SELECT title FROM data_tasks WHERE id=?", String.class, task.getId()));
            manager.detach(task);
            assertFalse(manager.contains(task));
            manager.getTransaction().begin();
            task.setTitle("Detached change");
            manager.getTransaction().commit();
            assertEquals("Dirty checked", jdbc.queryForObject("SELECT title FROM data_tasks WHERE id=?", String.class, task.getId()));
            manager.getTransaction().begin();
            var merged = manager.merge(task);
            assertNotSame(task, merged);
            assertTrue(manager.contains(merged));
            manager.getTransaction().commit();
            assertEquals("Detached change", jdbc.queryForObject("SELECT title FROM data_tasks WHERE id=?", String.class, task.getId()));
        }
    }

    @Test
    void modifyingAnAppliedMigrationFailsChecksumValidation(@TempDir Path directory) throws Exception {
        for (String file : List.of("V1__tasks.sql", "V2__task_title_index.sql")) {
            try (var resource = getClass().getResourceAsStream("/db/data-migration/" + file)) {
                assertNotNull(resource);
                String sql = new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                Files.writeString(directory.resolve(file), sql.replace("VARCHAR(40)", "VARCHAR(41)"));
            }
        }
        var changed = Flyway.configure().dataSource(source).locations("filesystem:" + directory.toAbsolutePath())
                .table("data_schema_history").cleanDisabled(true).load();
        var result = changed.validateWithResult();
        assertFalse(result.validationSuccessful);
        assertEquals(1, result.invalidMigrations.size());
        var invalid = result.invalidMigrations.getFirst();
        assertEquals("1", invalid.version);
        assertEquals(CoreErrorCode.CHECKSUM_MISMATCH, invalid.errorDetails.errorCode);
        assertTrue(invalid.filepath.endsWith("V1__tasks.sql"));
        assertThrows(FlywayValidateException.class, changed::validate);
        migration.validate();
    }

    @Test
    void closingAnOwnedPoolReleasesItsLifecycleAndRejectsFurtherBorrows() throws Exception {
        var configuration = new HikariConfig();
        configuration.setJdbcUrl(DatabaseUrl.dedicated(System.getenv("DATA_JDBC_URL")));
        configuration.setUsername(System.getenv("DATA_DATABASE_USER"));
        configuration.setPassword(System.getenv("DATA_DATABASE_PASSWORD"));
        configuration.setMaximumPoolSize(1);
        configuration.setMinimumIdle(0);
        var owned = new HikariDataSource(configuration);
        try (owned; var connection = owned.getConnection()) {
            assertFalse(owned.isClosed());
            assertFalse(connection.isClosed());
        }
        assertTrue(owned.isClosed());
        assertThrows(java.sql.SQLException.class, owned::getConnection);
    }
}
