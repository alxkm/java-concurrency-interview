package org.alxkm.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks the code rather than the content: the parser, the marking and the progress file.
 *
 * <p>{@link QuestionContentTest} proves the real questions parse, which says nothing about a file that
 * is almost right. These feed the parser the near misses a contributor actually writes.
 */
class QuizTest {

    private static final String TOPIC = """
            # Locks

            Why this topic is asked.

            ```
              a diagram
            ```

            ## What does tryLock return when the lock is free?
            - id: trylock-free
            - level: junior
            - tags: locks, tryLock

            * [ ] it blocks
            * [x] true, and the caller now holds the lock
            * [ ] false

            It returns true at once. A code block in the answer is part of the answer:

            ```java
            ## not a heading, because it is inside a fence
            ```

            ## Which of these are reentrant?
            - id: reentrant-which

            * [x] ReentrantLock
            * [x] synchronized
            * [ ] Semaphore

            Both count holds per thread.
            """;

    private static Quiz.Topic parse(Path dir, String content) throws IOException {
        Path file = dir.resolve("04-locks.md");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return Quiz.Parser.parse(file);
    }

    @Nested
    @DisplayName("the parser")
    class ParserTest {

        @Test
        @DisplayName("reads the title, intro, diagram and every question")
        void readsATopic(@TempDir Path dir) throws IOException {
            Quiz.Topic topic = parse(dir, TOPIC);

            assertEquals("Locks", topic.title());
            assertEquals("Why this topic is asked.", topic.intro());
            assertEquals("  a diagram", topic.diagram());
            assertEquals(2, topic.questions().size());

            Quiz.Question first = topic.questions().get(0);
            assertEquals("trylock-free", first.id());
            assertEquals(Quiz.Level.JUNIOR, first.level());
            assertEquals(List.of("locks", "tryLock"), first.tags());
            assertEquals(List.of(1), first.correctIndexes());
        }

        @Test
        @DisplayName("a ## line inside a code block stays in the answer")
        void headingInsideAFenceIsCode(@TempDir Path dir) throws IOException {
            Quiz.Question first = parse(dir, TOPIC).questions().get(0);
            assertTrue(first.answer().contains("## not a heading"), first.answer());
        }

        @Test
        @DisplayName("a question without a level is pitched at mid, and several answers can be right")
        void defaultsAndMultipleAnswers(@TempDir Path dir) throws IOException {
            Quiz.Question second = parse(dir, TOPIC).questions().get(1);
            assertEquals(Quiz.Level.MID, second.level());
            assertEquals(List.of(), second.tags());
            assertEquals(List.of(0, 1), second.correctIndexes());
        }

        @Test
        @DisplayName("a question with no id is rejected, naming the question")
        void missingId(@TempDir Path dir) {
            var e = assertThrows(IllegalStateException.class,
                    () -> parse(dir, TOPIC.replace("- id: trylock-free\n", "")));
            assertTrue(e.getMessage().contains("tryLock return"), e.getMessage());
        }

        @Test
        @DisplayName("a question with nothing marked correct is rejected")
        void noCorrectOption(@TempDir Path dir) {
            var e = assertThrows(IllegalStateException.class,
                    () -> parse(dir, TOPIC.replace("* [x] true", "* [ ] true")));
            assertTrue(e.getMessage().contains("trylock-free"), e.getMessage());
        }

        @Test
        @DisplayName("an unknown level is an error, not a silent default")
        void unknownLevel(@TempDir Path dir) {
            assertThrows(IllegalArgumentException.class,
                    () -> parse(dir, TOPIC.replace("level: junior", "level: principal")));
        }

        @Test
        @DisplayName("the same id in two files is rejected when the bank is built")
        void duplicateIdsAcrossTopics(@TempDir Path dir) throws IOException {
            Quiz.Topic one = parse(dir, TOPIC);
            Quiz.Topic two = parse(dir, TOPIC);
            var e = assertThrows(IllegalStateException.class, () -> new Quiz.Bank(List.of(one, two)));
            assertTrue(e.getMessage().contains("trylock-free"), e.getMessage());
        }
    }

    @Nested
    @DisplayName("marking an answer")
    class MarkingTest {

        private Quiz.Question question(boolean... correct) {
            List<Quiz.Option> options = new java.util.ArrayList<>();
            for (int i = 0; i < correct.length; i++) {
                options.add(new Quiz.Option("option " + i, correct[i]));
            }
            return new Quiz.Question("q", "prompt?", options, "answer", Quiz.Level.MID, List.of(), null);
        }

        private static final List<Integer> IN_FILE_ORDER = List.of(0, 1, 2);

        @Test
        @DisplayName("one right letter is right, any other letter is wrong")
        void singleAnswer() {
            Quiz.Question q = question(false, true, false);
            assertTrue(Quiz.Session.marks(q, IN_FILE_ORDER, "b"));
            assertFalse(Quiz.Session.marks(q, IN_FILE_ORDER, "a"));
            assertFalse(Quiz.Session.marks(q, IN_FILE_ORDER, "bc"));
        }

        @Test
        @DisplayName("several answers must all be picked, in any order")
        void multipleAnswers() {
            Quiz.Question q = question(true, true, false);
            assertTrue(Quiz.Session.marks(q, IN_FILE_ORDER, "ab"));
            assertTrue(Quiz.Session.marks(q, IN_FILE_ORDER, "ba"));
            assertFalse(Quiz.Session.marks(q, IN_FILE_ORDER, "a"));
            assertFalse(Quiz.Session.marks(q, IN_FILE_ORDER, "abc"));
        }

        @Test
        @DisplayName("typing one letter twice picks one option, not two")
        void repeatedLetterIsNotTwoAnswers() {
            Quiz.Question q = question(true, true, false);
            assertFalse(Quiz.Session.marks(q, IN_FILE_ORDER, "aa"));
        }

        @Test
        @DisplayName("letters refer to the shuffled order on screen, not the order in the file")
        void shuffledOrder() {
            Quiz.Question q = question(false, false, true);
            List<Integer> shown = List.of(2, 0, 1);   // the correct option is shown first, as "a"
            assertTrue(Quiz.Session.marks(q, shown, "a"));
            assertFalse(Quiz.Session.marks(q, shown, "c"));
        }

        @Test
        @DisplayName("letters past the last option and stray characters are ignored")
        void noise() {
            Quiz.Question q = question(false, true, false);
            assertTrue(Quiz.Session.marks(q, IN_FILE_ORDER, " b, z"));
            assertFalse(Quiz.Session.marks(q, IN_FILE_ORDER, "z"));
        }
    }

    @Nested
    @DisplayName("the progress file")
    class ProgressTest {

        @Test
        @DisplayName("survives a save and a reload")
        void roundTrip(@TempDir Path dir) {
            Path file = dir.resolve(".progress");
            Quiz.Progress progress = new Quiz.Progress(file);
            progress.record("a", true);
            progress.record("a", false);
            progress.record("a", false);
            progress.record("b", true);
            progress.save();

            Quiz.Progress reloaded = new Quiz.Progress(file);
            assertEquals(1, reloaded.right("a"));
            assertEquals(2, reloaded.wrong("a"));
            assertEquals(1, reloaded.right("b"));
            assertTrue(reloaded.seen("b"));
            assertFalse(reloaded.seen("never-asked"));
        }

        @Test
        @DisplayName("lives next to a checkout, and in the home directory when run from the jar")
        void location(@TempDir Path dir) throws IOException {
            Path checkout = Files.createDirectories(dir.resolve("repo/questions"));
            assertEquals(dir.resolve("repo/.progress").toAbsolutePath(), Quiz.progressFile(checkout));

            Path jar = dir.resolve("quiz.jar");
            try (var zip = java.nio.file.FileSystems.newFileSystem(jar, java.util.Map.of("create", "true"))) {
                Path bundled = Files.createDirectories(zip.getPath("/questions"));
                Path progress = Quiz.progressFile(bundled);
                assertEquals(Path.of(System.getProperty("user.home")), progress.getParent());
            }
        }

        @Test
        @DisplayName("a corrupted file starts fresh instead of crashing the quiz")
        void corruptFile(@TempDir Path dir) throws IOException {
            Path file = dir.resolve(".progress");
            Files.writeString(file, "a|1|1\nb|not-a-number|0\n", StandardCharsets.UTF_8);
            Quiz.Progress progress = new Quiz.Progress(file);
            assertFalse(progress.seen("a"));
            assertEquals(0, progress.wrong("b"));
        }
    }

    @Test
    @DisplayName("levels accept the words people actually type")
    void levelAliases() {
        assertEquals(Quiz.Level.JUNIOR, Quiz.Level.parse("Easy"));
        assertEquals(Quiz.Level.MID, Quiz.Level.parse(" middle "));
        assertEquals(Quiz.Level.SENIOR, Quiz.Level.parse("hard"));
    }

    @Test
    @DisplayName("heading anchors follow GitHub's rules, so the README's links land")
    void anchors() {
        assertEquals("synchronized-monitors-wait-and-notify",
                Quiz.anchor("synchronized, monitors, wait and notify"));
        assertEquals("forkjoin-and-parallel-streams", Quiz.anchor("Fork/join and parallel streams"));
    }

    @Test
    @DisplayName("every line of the banner fits its box, whatever the question count")
    void bannerFitsItsBox() {
        List<String> box = Quiz.boxLines(Quiz.bannerLines(1234, 99));
        int width = box.get(0).length();
        box.forEach(line -> assertEquals(width, line.length(), () -> "'" + line + "'"));
    }
}
