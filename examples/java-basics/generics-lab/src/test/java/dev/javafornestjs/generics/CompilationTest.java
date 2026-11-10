package dev.javafornestjs.generics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilationTest {
    @TempDir Path directory;

    private boolean compile(String body) throws Exception {
        return compile(body, null);
    }

    private boolean compile(String body, String diagnosticPrefix) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A full JDK is required");
        Path output = Files.createTempDirectory(directory, "compile-");
        Path source = output.resolve("Fixture.java");
        Files.writeString(source, "import java.util.*; class Fixture { " + body + " }", StandardCharsets.UTF_8);
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var units = manager.getJavaFileObjects(source.toFile());
            boolean success = compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "21", "-proc:none", "-d", output.toString()), null, units).call();
            if (!success) {
                assertTrue(diagnostics.getDiagnostics().stream()
                        .anyMatch(item -> item.getKind() == Diagnostic.Kind.ERROR), "Real compiler error required");
                if (diagnosticPrefix != null) {
                    assertTrue(diagnostics.getDiagnostics().stream().anyMatch(item ->
                            item.getKind() == Diagnostic.Kind.ERROR
                                    && item.getCode().startsWith(diagnosticPrefix)
                                    && item.getLineNumber() == 1 && item.getColumnNumber() > 0
                                    && item.getSource() != null
                                    && item.getSource().toUri().equals(source.toUri())),
                            () -> "Expected diagnostic " + diagnosticPrefix + ": "
                                    + diagnostics.getDiagnostics().stream().map(Diagnostic::getCode).toList()
                                    + " " + diagnostics.getDiagnostics());
                }
            }
            return success;
        }
    }

    @Test
    void wildcardCopyPositiveFixtureReallyCompiles() throws Exception {
        assertTrue(compile("<T> void copy(List<? extends T> a, List<? super T> b) { "
                + "for (T x : a) b.add(x); } void run() { List<Number> b = new ArrayList<>(); "
                + "copy(List.of(1, 2), b); }"));
    }

    @Test
    void invariantListAssignmentIsRejected() throws Exception {
        assertFalse(compile("void run() { List<Number> n = new ArrayList<Integer>(); }",
                "compiler.err.prob.found.req"));
    }

    @Test
    void extendsProducerCannotAcceptAnArbitraryNumber() throws Exception {
        assertFalse(compile("void run(List<? extends Number> n) { n.add(Integer.valueOf(1)); }",
                "compiler.err.prob.found.req"));
    }

    @Test
    void overloadsWithSameErasureAreRejected() throws Exception {
        assertFalse(compile("void save(List<String> a) { } void save(List<Integer> a) { }",
                "compiler.err.name.clash.same.erasure"));
    }

    @Test
    void primitiveTypeArgumentsAreRejected() throws Exception {
        assertFalse(compile("List<int> values;", "compiler.err.type.found.req"));
    }

    @Test
    void boundedMethodRejectsStringArguments() throws Exception {
        assertFalse(compile("<T extends Number & Comparable<T>> T max(T a, T b) { return a; } "
                + "void run() { max(\"a\", \"b\"); }", "compiler.err.cant.apply.symbol"));
    }
}
