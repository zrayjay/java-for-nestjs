package dev.javafornestjs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.zaxxer.hikari.HikariDataSource;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.tools.ToolProvider;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.codegen.GenerationTool;
import org.jooq.impl.DSL;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = DataApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("data")
class GeneratedSchemaTest {
    @Autowired HikariDataSource source;
    @Autowired JdbcTemplate jdbc;
    @Test
    void realMysqlSchemaGeneratesCompilableTypedQueriesAndRejectsWrongFieldType() throws Exception {
        Path generated = Path.of("target", "generated-schema").toAbsolutePath();
        Path classes = Path.of("target", "generated-schema-classes").toAbsolutePath();
        Files.createDirectories(generated);
        Files.createDirectories(classes);
        GenerationTool.generate(new Configuration().withJdbc(new Jdbc()
                .withDriver("com.mysql.cj.jdbc.Driver").withUrl(DatabaseUrl.dedicated(System.getenv("DATA_JDBC_URL")))
                .withUser(System.getenv("DATA_DATABASE_USER")).withPassword(System.getenv("DATA_DATABASE_PASSWORD")))
                .withGenerator(new Generator().withDatabase(new Database().withName("org.jooq.meta.mysql.MySQLDatabase")
                        .withInputSchema("data_lab").withIncludes("data_tasks|data_owners"))
                        .withGenerate(new Generate().withGeneratedAnnotation(false))
                        .withTarget(new Target().withPackageName("dev.javafornestjs.generated").withDirectory(generated.toString()))));
        Path client = generated.resolve("SchemaQuery.java");
        Files.writeString(client, """
                import dev.javafornestjs.generated.Tables;
                public class SchemaQuery {
                    public static String title(org.jooq.DSLContext db, int id) {
                        return db.select(Tables.DATA_TASKS.TITLE).from(Tables.DATA_TASKS)
                            .where(Tables.DATA_TASKS.ID.eq(id)).fetchOne(Tables.DATA_TASKS.TITLE);
                    }
                }
                """);
        var arguments = new ArrayList<String>();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        arguments.addAll(java.util.List.of("--release", "21", "-classpath", classpath, "-d", classes.toString()));
        try (var files = Files.walk(generated)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> arguments.add(path.toString()));
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A full JDK is required");
        var diagnostics = new ByteArrayOutputStream();
        assertEquals(0, compiler.run(null, diagnostics, diagnostics, arguments.toArray(String[]::new)), diagnostics.toString());
        jdbc.update("DELETE FROM data_tasks");
        jdbc.update("INSERT INTO data_tasks(title,priority,owner_id) VALUES ('Typed query',2,1)");
        int id = jdbc.queryForObject("SELECT id FROM data_tasks WHERE title='Typed query'", Integer.class);
        try (var loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, getClass().getClassLoader());
             var connection = source.getConnection()) {
            DSLContext database = DSL.using(connection, SQLDialect.MYSQL);
            assertEquals("Typed query", loader.loadClass("SchemaQuery").getMethod("title", DSLContext.class, int.class)
                    .invoke(null, database, id));
        }
        Path wrong = generated.resolve("WrongQuery.java");
        Files.writeString(wrong, """
                import dev.javafornestjs.generated.Tables;
                class WrongQuery { Object wrong() { return Tables.DATA_TASKS.ID.eq("not-an-int"); } }
                """);
        diagnostics.reset();
        try {
            assertNotEquals(0, compiler.run(null, diagnostics, diagnostics, "--release", "21", "-classpath",
                    classpath + java.io.File.pathSeparator + classes, "-d", classes.toString(), wrong.toString()));
        } finally {
            // Prevent a previous negative source from entering the next positive compilation.
            Files.deleteIfExists(wrong);
        }
    }
}
