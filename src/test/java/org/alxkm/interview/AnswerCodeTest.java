package org.alxkm.interview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.util.JavacTask;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks the Java in the answers.
 *
 * <p>A wrong answer in an interview repository costs more than a missing one, and code is where it
 * hides: a misplaced brace or a field that is never assigned reads fine in a code block. So every
 * {@code ```java} block is checked, as strictly as it can be:
 *
 * <ul>
 *   <li>A block made only of whole types (a live coding solution, say) must <em>compile</em>, with the
 *       usual {@code java.util.concurrent} imports supplied.</li>
 *   <li>Anything else is a fragment that leans on names from the surrounding prose ({@code lock},
 *       {@code map}), so it can only be <em>parsed</em>. Each blank-line-separated chunk must parse as
 *       either class members or statements, which is still enough to catch the typos.</li>
 * </ul>
 *
 * <p>{@code ...} stands for elided code, as it does in the answers, and is dropped before checking.
 * A block that is deliberately not Java, such as a wrong-on-purpose example that does not even parse,
 * belongs in a plain {@code ```} fence instead.
 */
class AnswerCodeTest {

    private static final Pattern JAVA_BLOCK = Pattern.compile("(?ms)^```java[^\\n]*\\n(.*?)^```");

    /** A block is a compilation unit when nothing but types and imports sits at column zero. */
    private static final Pattern TOP_LEVEL = Pattern.compile("(?m)^(?![ \\t}]|//|$)(.*)$");
    private static final Pattern TYPE_OR_IMPORT = Pattern.compile(
            "^(import |package |((public|final|abstract|sealed|static) )*(class|record|interface|enum) ).*");

    private static final String IMPORTS = """
            import java.time.*;
            import java.util.*;
            import java.util.concurrent.*;
            import java.util.concurrent.atomic.*;
            import java.util.concurrent.locks.*;
            import java.util.function.*;
            import static java.util.concurrent.TimeUnit.*;
            """;

    @TempDir
    static Path classes;

    record Block(Path file, int line, String code) {
        @Override
        public String toString() {
            return file.getFileName() + ":" + line;
        }
    }

    static Stream<Arguments> blocks() {
        List<Block> blocks = new ArrayList<>();
        try (Stream<Path> files = Files.list(Quiz.questionsDir())) {
            for (Path file : files.filter(f -> f.toString().endsWith(".md")).sorted().toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
                Matcher m = JAVA_BLOCK.matcher(text);
                while (m.find()) {
                    int line = (int) text.substring(0, m.start()).chars().filter(c -> c == '\n').count() + 1;
                    blocks.add(new Block(file, line, m.group(1)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return blocks.stream().map(b -> Arguments.of(Named.of(b.toString(), b)));
    }

    @Test
    @DisplayName("the answers contain Java to check, so an empty run cannot pass by accident")
    void thereIsCodeToCheck() {
        assertTrue(blocks().count() >= 40, "expected at least forty java blocks in the answers");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blocks")
    @DisplayName("every java block in an answer compiles, or at least parses")
    void codeIsValid(Block block) {
        String code = withoutElisions(block.code());
        if (isCompilationUnit(code)) {
            List<String> errors = check(IMPORTS + code, true);
            assertTrue(errors.isEmpty(), () -> block + " does not compile:\n" + String.join("\n", errors));
            return;
        }
        for (String chunk : code.split("\n\\s*\n")) {
            if (chunk.isBlank()) {
                continue;
            }
            List<String> asMembers = check("class Snippet {\n" + chunk + "\n}", false);
            if (asMembers.isEmpty()) {
                continue;
            }
            List<String> asStatements = check(
                    "class Snippet { void snippet() throws Exception {\n" + chunk + "\n}}", false);
            assertTrue(asStatements.isEmpty(), () -> block + " does not parse as members or statements:\n"
                    + chunk + "\n" + String.join("\n", asStatements));
        }
    }

    @Test
    @DisplayName("the checker itself rejects broken code, so a green run means something")
    void checkerRejectsBrokenCode() {
        assertFalse(check("class Snippet { void m() { int x = ; } }", false).isEmpty());
        assertFalse(check(IMPORTS + "class A { final int n; }", true).isEmpty());
        assertTrue(isCompilationUnit("class A {\n}\n"));
        assertFalse(isCompilationUnit("record S(int a) { }\n\nvoid touch() {\n}\n"));
    }

    static String withoutElisions(String code) {
        return code
                .replaceAll("\\{\\s*\\.\\.\\.\\s*}", "{ }")
                .replaceAll("(?m)^\\s*\\.\\.\\.\\s*(//.*)?$", "");
    }

    static boolean isCompilationUnit(String code) {
        Matcher m = TOP_LEVEL.matcher(code);
        boolean any = false;
        while (m.find()) {
            any = true;
            if (!TYPE_OR_IMPORT.matcher(m.group(1)).matches()) {
                return false;
            }
        }
        return any;
    }

    /** Errors only: a fragment that parses cleanly but would warn is still a good fragment. */
    static List<String> check(String source, boolean compile) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///Snippet.java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        List<String> options = List.of("-proc:none", "-Xlint:none", "-d", classes.toString());
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostics, options, null, List.of(file));
        try {
            if (compile) {
                task.call();
            } else {
                task.parse();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> "  line " + d.getLineNumber() + ": " + d.getMessage(null))
                .collect(Collectors.toList());
    }
}
