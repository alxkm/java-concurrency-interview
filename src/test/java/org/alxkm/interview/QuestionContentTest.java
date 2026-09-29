package org.alxkm.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks the content rather than the code.
 *
 * <p>A question with a duplicate id, a missing answer or no correct option would break the quiz at
 * runtime, in front of whoever cloned the repository. These run in CI so that never happens, and so
 * that a pull request adding questions fails if the README was not regenerated.
 */
class QuestionContentTest {

    private static final Quiz.Bank BANK = Quiz.Bank.load();

    /** Named so a failure names the question rather than printing the whole record. */
    static Stream<Arguments> questions() {
        return BANK.questions().stream().map(q -> Arguments.of(Named.of(q.id(), q)));
    }

    @Test
    @DisplayName("every topic file parses and the bank is not suspiciously small")
    void bankLoads() {
        assertTrue(BANK.topics().size() >= 10, "expected at least ten topics");
        assertTrue(BANK.questions().size() >= 100, "expected at least a hundred questions");
    }

    @Test
    @DisplayName("question ids are unique across every file")
    void idsAreUnique() {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = BANK.questions().stream()
                .map(Quiz.Question::id)
                .filter(id -> !seen.add(id))
                .toList();
        assertTrue(duplicates.isEmpty(), () -> "duplicate ids: " + duplicates);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("questions")
    @DisplayName("each question has options, exactly one marked answer set, and a real explanation")
    void questionIsWellFormed(Quiz.Question question) {
        assertTrue(question.options().size() >= 3,
                () -> question.id() + ": at least three options, otherwise guessing is free");
        assertFalse(question.correctIndexes().isEmpty(), () -> question.id() + ": nothing is correct");
        assertTrue(question.correctIndexes().size() < question.options().size(),
                () -> question.id() + ": every option is correct, which teaches nothing");
        assertTrue(question.answer().length() > 200,
                () -> question.id() + ": the explanation is a sentence, not an answer");
        assertTrue(question.prompt().endsWith("?") || question.prompt().endsWith("."),
                () -> question.id() + ": the prompt should read as a question an interviewer asks");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("questions")
    @DisplayName("no option gives itself away by being much longer than the others")
    void optionsAreComparableInLength(Quiz.Question question) {
        // The oldest tell in multiple choice: the longest option is the right one. If that holds here,
        // the quiz measures test-taking rather than knowledge.
        int longestWrong = question.options().stream()
                .filter(option -> !option.correct())
                .mapToInt(option -> option.text().length())
                .max()
                .orElse(0);
        int longestCorrect = question.options().stream()
                .filter(Quiz.Option::correct)
                .mapToInt(option -> option.text().length())
                .max()
                .orElse(0);
        assertTrue(longestCorrect <= longestWrong * 3 + 40,
                () -> question.id() + ": the correct option is far longer than every wrong one");
    }

    @Test
    @DisplayName("README.md matches the questions, so the catalogue cannot drift")
    void readmeIsUpToDate() throws IOException {
        Path readme = Quiz.questionsDir().getParent().resolve("README.md");
        String content = Files.readString(readme, StandardCharsets.UTF_8);
        // render() covers the screen, the catalogue and the question count, and throws if a marker
        // has gone missing.
        assertEquals(Quiz.render(content, BANK), content,
                "README.md is out of date. Run ./gradlew readme");
    }

    @Test
    @DisplayName("a README checked out with CRLF line endings still counts as up to date")
    void readmeCheckIgnoresLineEndings() throws IOException {
        // Git for Windows defaults to core.autocrlf=true, so a fresh clone there has CRLF everywhere.
        // CI runs on Linux and never sees that, which is how this once broke every Windows clone
        // while the badge stayed green. Both spellings of the same README must pass.
        Path readme = Quiz.questionsDir().getParent().resolve("README.md");
        String lf = Files.readString(readme, StandardCharsets.UTF_8).replace("\r\n", "\n");
        String crlf = lf.replace("\n", "\r\n");
        assertEquals(lf, Quiz.render(lf, BANK), "LF checkout reported as out of date");
        assertEquals(crlf, Quiz.render(crlf, BANK), "CRLF checkout reported as out of date");
    }
}
