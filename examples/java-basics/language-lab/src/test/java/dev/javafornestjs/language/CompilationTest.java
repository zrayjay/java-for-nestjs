package dev.javafornestjs.language;

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

    private void rejected(String members, String code) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Full JDK required");
        Path source = directory.resolve("Fixture.java");
        Files.writeString(source, "class Fixture { " + members + " }", StandardCharsets.UTF_8);
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            boolean result = compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "21", "-proc:none", "-d", directory.toString()), null,
                    manager.getJavaFileObjects(source.toFile())).call();
            assertFalse(result, "Expected real compilation rejection");
            assertTrue(diagnostics.getDiagnostics().stream().anyMatch(item ->
                    item.getKind() == Diagnostic.Kind.ERROR && item.getCode().startsWith(code)
                            && item.getLineNumber() == 1 && item.getColumnNumber() > 0
                            && item.getSource() != null && item.getSource().toUri().equals(source.toUri())),
                    () -> diagnostics.getDiagnostics().toString());
        }
    }

    @Test
    void varNeedsAnInferableInitializer() throws Exception {
        rejected("void f() { var value = null; }", "compiler.err.cant.infer.local.var.type");
    }

    @Test
    void recordsCannotBeExtended() throws Exception {
        rejected("record A(int x) { } class B extends A { B() { super(1); } }",
                "compiler.err.cant.inherit.from.final");
    }

    @Test
    void sealedHierarchyRejectsAnUnpermittedImplementation() throws Exception {
        rejected("sealed interface P permits A { } final class A implements P { } final class B implements P { }",
                "compiler.err.cant.inherit.from.sealed");
    }

    @Test
    void nominalInterfacesRequireDeclaration() throws Exception {
        rejected("interface Named { String name(); } class LooksNamed { public String name() { return \"n\"; } } "
                + "Named value = new LooksNamed();", "compiler.err.prob.found.req");
    }

    @Test
    void checkedExceptionCannotBeSilentlyIgnored() throws Exception {
        rejected("void risky() throws java.io.IOException { } void caller() { risky(); }",
                "compiler.err.unreported.exception.need.to.catch.or.throw");
    }

    @Test
    void capturedLocalCannotBeReassigned() throws Exception {
        rejected("void f() { int n=1; Runnable r=()->System.out.println(n); n=2; }",
                "compiler.err.cant.ref.non.effectively.final.var");
    }
}
