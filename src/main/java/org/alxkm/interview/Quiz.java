package org.alxkm.interview;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * The interactive half of this repository: a terminal quiz over the questions in {@code questions/}.
 *
 * <p>Deliberately one file with no dependencies, so it runs two ways and neither needs a build:
 *
 * <pre>
 *   java src/main/java/org/alxkm/interview/Quiz.java
 *   ./gradlew quiz --console=plain -q
 * </pre>
 *
 * <p>The same class generates the question catalogue inside README.md ({@code --export-readme}),
 * which is why the parser lives here rather than in a tool of its own. One parser, one source of
 * truth, and no way for the README and the quiz to disagree about what a question says.
 */
public final class Quiz {

    /** Where prose wraps. Wide enough for a code line, narrow enough for half a screen. */
    private static final int WIDTH = 92;

    /** What the quiz writes your answers into, so a session can be picked up later. Git ignores it. */
    private static final String PROGRESS_FILE = ".progress";

    private Quiz() {
    }

    // ---------------------------------------------------------------- model

    /** Difficulty as an interviewer would pitch it, not a judgement about the reader. */
    enum Level {
        JUNIOR, MID, SENIOR;

        static Level parse(String raw) {
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "junior", "easy" -> JUNIOR;
                case "mid", "middle", "medium" -> MID;
                case "senior", "hard" -> SENIOR;
                default -> throw new IllegalArgumentException("unknown level: " + raw);
            };
        }

        String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One answer option. Several may be correct, and the quiz says so when they are. */
    record Option(String text, boolean correct) {
    }

    /** One question, with everything needed to ask it, mark it and explain it. */
    record Question(String id, String prompt, List<Option> options, String answer,
                    Level level, List<String> tags, Topic topic) {

        List<Integer> correctIndexes() {
            List<Integer> correct = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).correct()) {
                    correct.add(i);
                }
            }
            return correct;
        }
    }

    /** A topic file: its title, its opening paragraph, its ASCII diagram and its questions. */
    static final class Topic {
        private final String id;
        private final String title;
        private final String intro;
        private final String diagram;
        private final List<Question> questions = new ArrayList<>();

        Topic(String id, String title, String intro, String diagram) {
            this.id = id;
            this.title = title;
            this.intro = intro;
            this.diagram = diagram;
        }

        String id() {
            return id;
        }

        String title() {
            return title;
        }

        String intro() {
            return intro;
        }

        String diagram() {
            return diagram;
        }

        List<Question> questions() {
            return questions;
        }
    }

    // --------------------------------------------------------------- parser

    /**
     * Reads {@code questions/*.md}. The format is markdown that also renders on GitHub:
     *
     * <pre>
     *   # Topic title
     *   Intro paragraph.
     *   (a fenced block here becomes the topic's ASCII diagram)
     *   ## The question, worded the way an interviewer asks it
     *   - id: unique-slug
     *   - level: mid
     *   - tags: volatile, visibility
     *   * [ ] a wrong option
     *   * [x] the right one
     *   The explanation, in markdown, until the next question heading.
     * </pre>
     */
    static final class Parser {

        private Parser() {
        }

        static List<Topic> parseAll(Path dir) {
            try (var files = Files.list(dir)) {
                List<Path> paths = files
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .sorted()
                        .toList();
                List<Topic> topics = new ArrayList<>();
                for (Path path : paths) {
                    topics.add(parse(path));
                }
                return topics;
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + dir, e);
            }
        }

        static Topic parse(Path file) {
            List<String> lines;
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + file, e);
            }
            String name = file.getFileName().toString().replaceFirst("\\.md$", "");

            String title = name;
            StringBuilder intro = new StringBuilder();
            StringBuilder diagram = new StringBuilder();
            List<List<String>> blocks = new ArrayList<>();
            List<String> current = null;

            boolean inFence = false;
            boolean inDiagram = false;
            for (String line : lines) {
                if (line.startsWith("```")) {
                    // A heading marker inside a code block is code, not a heading, so fence state has
                    // to be tracked before anything else looks at the line.
                    if (current == null && !inFence && diagram.isEmpty()) {
                        inDiagram = true;
                        continue;
                    }
                    if (inDiagram) {
                        inDiagram = false;
                        continue;
                    }
                    inFence = !inFence;
                }
                if (inDiagram) {
                    diagram.append(line).append('\n');
                    continue;
                }
                if (!inFence && line.startsWith("## ")) {
                    current = new ArrayList<>();
                    blocks.add(current);
                    current.add(line);
                    continue;
                }
                if (current != null) {
                    current.add(line);
                } else if (!inFence && line.startsWith("# ")) {
                    title = line.substring(2).trim();
                } else if (!line.isBlank() || !intro.isEmpty()) {
                    intro.append(line).append('\n');
                }
            }

            Topic topic = new Topic(name, title, intro.toString().trim(), stripTrailingNewlines(diagram));
            for (List<String> block : blocks) {
                topic.questions().add(parseQuestion(block, topic, file));
            }
            if (topic.questions().isEmpty()) {
                throw new IllegalStateException(file + " has no questions");
            }
            return topic;
        }

        private static String stripTrailingNewlines(StringBuilder text) {
            String value = text.toString();
            while (value.endsWith("\n")) {
                value = value.substring(0, value.length() - 1);
            }
            return value;
        }

        private static Question parseQuestion(List<String> block, Topic topic, Path file) {
            String prompt = block.get(0).substring(3).trim();
            Map<String, String> meta = new LinkedHashMap<>();
            List<Option> options = new ArrayList<>();
            StringBuilder answer = new StringBuilder();

            boolean inFence = false;
            boolean answerStarted = false;
            for (String raw : block.subList(1, block.size())) {
                String line = raw.stripTrailing();
                if (line.startsWith("```")) {
                    inFence = !inFence;
                }
                if (!inFence && !answerStarted) {
                    if (options.isEmpty() && line.startsWith("- ") && line.contains(":")) {
                        int colon = line.indexOf(':');
                        meta.put(line.substring(2, colon).trim().toLowerCase(Locale.ROOT),
                                line.substring(colon + 1).trim());
                        continue;
                    }
                    if (line.startsWith("* [ ] ") || line.startsWith("* [x] ")) {
                        options.add(new Option(line.substring(6).trim(), line.charAt(3) == 'x'));
                        continue;
                    }
                    if (line.isBlank()) {
                        continue;
                    }
                }
                answerStarted = true;
                answer.append(line).append('\n');
            }

            String id = meta.getOrDefault("id", "").trim();
            if (id.isEmpty()) {
                throw new IllegalStateException(file + ": question has no id: " + prompt);
            }
            if (options.size() < 2) {
                throw new IllegalStateException(file + ": " + id + " needs at least two options");
            }
            if (options.stream().noneMatch(Option::correct)) {
                throw new IllegalStateException(file + ": " + id + " has no correct option");
            }
            if (answer.toString().isBlank()) {
                throw new IllegalStateException(file + ": " + id + " has no explanation");
            }
            return new Question(id, prompt, List.copyOf(options), answer.toString().strip(),
                    Level.parse(meta.getOrDefault("level", "mid")),
                    meta.containsKey("tags") ? List.of(meta.get("tags").split("\\s*,\\s*")) : List.of(),
                    topic);
        }
    }

    // ------------------------------------------------------------------ bank

    /** Every topic and question, loaded once, with the checks that keep the content honest. */
    static final class Bank {
        private final List<Topic> topics;
        private final List<Question> questions;

        Bank(List<Topic> topics) {
            this.topics = topics;
            this.questions = topics.stream().flatMap(t -> t.questions().stream()).toList();
            String duplicates = questions.stream()
                    .collect(Collectors.groupingBy(Question::id, Collectors.counting()))
                    .entrySet().stream()
                    .filter(e -> e.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .collect(Collectors.joining(", "));
            if (!duplicates.isEmpty()) {
                throw new IllegalStateException("duplicate question ids: " + duplicates);
            }
        }

        static Bank load() {
            return new Bank(Parser.parseAll(questionsDir()));
        }

        List<Topic> topics() {
            return topics;
        }

        List<Question> questions() {
            return questions;
        }
    }

    /**
     * Finds {@code questions/} without assuming where the process started.
     *
     * <p>Gradle sets the working directory to the project; a bare {@code java Quiz.java} inherits the
     * shell's. Walking up a few levels covers both, and {@code -Dquestions=...} covers the rest.
     */
    static Path questionsDir() {
        String override = System.getProperty("questions");
        if (override != null) {
            return Path.of(override);
        }
        Path candidate = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && candidate != null; i++) {
            Path dir = candidate.resolve("questions");
            if (Files.isDirectory(dir)) {
                return dir;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("cannot find the questions/ directory. Run from the repository "
                + "root, or pass -Dquestions=/path/to/questions");
    }

    // -------------------------------------------------------------- progress

    /** What you got right and wrong, in a small text file, so the quiz can point at weak spots. */
    static final class Progress {
        private final Map<String, int[]> counts = new LinkedHashMap<>();
        private final Path file;

        Progress(Path file) {
            this.file = file;
            if (!Files.isRegularFile(file)) {
                return;
            }
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String[] parts = line.split("\\|");
                    if (parts.length == 3) {
                        counts.put(parts[0].trim(), new int[] {
                                Integer.parseInt(parts[1].trim()),
                                Integer.parseInt(parts[2].trim())});
                    }
                }
            } catch (IOException | RuntimeException e) {
                counts.clear();
            }
        }

        void record(String id, boolean correct) {
            counts.computeIfAbsent(id, k -> new int[2])[correct ? 0 : 1]++;
        }

        int right(String id) {
            return counts.getOrDefault(id, new int[2])[0];
        }

        int wrong(String id) {
            return counts.getOrDefault(id, new int[2])[1];
        }

        boolean seen(String id) {
            return counts.containsKey(id);
        }

        void save() {
            String body = counts.entrySet().stream()
                    .map(e -> e.getKey() + "|" + e.getValue()[0] + "|" + e.getValue()[1])
                    .collect(Collectors.joining("\n"));
            try {
                Files.writeString(file, body + "\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                // Losing the progress file is not worth interrupting a quiz over.
            }
        }
    }

    // --------------------------------------------------------------------- ui

    /**
     * Terminal output and input.
     *
     * <p>Everything printed here is plain ASCII. Box-drawing characters look better until someone
     * opens the quiz in a console with a different code page, and then they look like garbage.
     */
    static final class Ui {
        private static final String RESET = "\u001B[0m";
        private static final String BOLD = "\u001B[1m";
        private static final String DIM = "\u001B[2m";
        private static final String RED = "\u001B[31m";
        private static final String GREEN = "\u001B[32m";
        private static final String YELLOW = "\u001B[33m";
        private static final String CYAN = "\u001B[36m";

        private final BufferedReader in =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        private final boolean color;

        Ui(boolean color) {
            this.color = color;
        }

        // -- input

        /** Reads one line. End of input counts as "q", so a piped run terminates instead of looping. */
        String ask(String prompt) {
            System.out.print("  " + paint(BOLD, prompt) + " ");
            System.out.flush();
            try {
                String line = in.readLine();
                return line == null ? "q" : line.trim();
            } catch (IOException e) {
                return "q";
            }
        }

        void pause(String prompt) {
            ask(prompt);
        }

        // -- output

        void line(String text) {
            System.out.println(text);
        }

        void blank() {
            System.out.println();
        }

        void rule() {
            System.out.println("  " + paint(DIM, "-".repeat(WIDTH - 2)));
        }

        String paint(String code, String text) {
            return color ? code + text + RESET : text;
        }

        String bold(String text) {
            return paint(BOLD, text);
        }

        String dim(String text) {
            return paint(DIM, text);
        }

        String green(String text) {
            return paint(GREEN, text);
        }

        String red(String text) {
            return paint(RED, text);
        }

        String yellow(String text) {
            return paint(YELLOW, text);
        }

        String cyan(String text) {
            return paint(CYAN, text);
        }

        /** A box whose sides line up regardless of what is in it. */
        void box(List<String> lines) {
            int inner = WIDTH - 6;
            System.out.println("  +" + "-".repeat(inner + 2) + "+");
            for (String line : lines) {
                String clipped = line.length() > inner ? line.substring(0, inner) : line;
                System.out.println("  | " + clipped + " ".repeat(inner - clipped.length()) + " |");
            }
            System.out.println("  +" + "-".repeat(inner + 2) + "+");
        }

        /** The header every screen starts with: where you are on the left, how far along on the right. */
        void header(String left, String right) {
            int inner = WIDTH - 6;
            String text = left.length() + right.length() + 2 > inner
                    ? left.substring(0, Math.max(0, inner - right.length() - 2)) + "  " + right
                    : left + " ".repeat(inner - left.length() - right.length()) + right;
            box(List.of(text));
        }

        /** [######----] with the marks in the right places whatever the totals are. */
        String bar(int done, int total, int width) {
            int filled = total == 0 ? 0 : (int) Math.round((double) done / total * width);
            return "[" + "#".repeat(filled) + "-".repeat(width - filled) + "]";
        }

        void banner(int questions, int topics) {
            blank();
            box(List.of(
                    "  J A V A   C O N C U R R E N C Y   I N T E R V I E W",
                    "",
                    "  writer   |--- write x=1 ---[ release ]-------------------->",
                    "                                    \\  happens-before",
                    "  reader   -----------------[ acquire ]--- reads x == 1 ---->",
                    "",
                    "  " + questions + " questions across " + topics + " topics, each answered with the"
                            + " reasoning,",
                    "  not just the keyword an interviewer is listening for"));
            blank();
        }

        /**
         * Renders a markdown answer for a terminal.
         *
         * <p>Prose is reflowed rather than re-wrapped. The source files wrap at 100 columns, so
         * wrapping each line on its own would leave a ragged trail of two-word lines; paragraphs are
         * joined first and wrapped once. Code blocks and tables are printed as they are, because their
         * line breaks carry meaning.
         */
        void markdown(String md, String indent) {
            boolean inFence = false;
            List<String> paragraph = new ArrayList<>();
            String hanging = indent;
            for (String raw : md.split("\n", -1)) {
                if (raw.startsWith("```")) {
                    hanging = flush(paragraph, indent, hanging);
                    inFence = !inFence;
                    continue;
                }
                if (inFence) {
                    System.out.println(indent + "    " + dim(raw));
                    continue;
                }
                if (raw.isBlank()) {
                    hanging = flush(paragraph, indent, hanging);
                    System.out.println();
                    continue;
                }
                if (raw.stripLeading().startsWith("|")) {
                    hanging = flush(paragraph, indent, hanging);
                    System.out.println(indent + raw);
                    continue;
                }
                String text = raw.strip();
                if (isListItem(text)) {
                    flush(paragraph, indent, hanging);
                    hanging = indent + "   ";
                }
                paragraph.add(text);
            }
            flush(paragraph, indent, hanging);
        }

        private static boolean isListItem(String text) {
            return text.startsWith("- ") || text.startsWith("* ") || text.matches("^\\d+\\. .*");
        }

        /** Prints what has been collected so far as one reflowed paragraph, and resets the indent. */
        private String flush(List<String> paragraph, String indent, String hanging) {
            if (!paragraph.isEmpty()) {
                paragraph(String.join(" ", paragraph), indent, hanging);
                paragraph.clear();
            }
            return indent;
        }

        /** One block of prose, wrapped, with its own indents for the first line and the rest. */
        void paragraph(String text, String firstIndent, String restIndent) {
            wrap(inline(text), firstIndent, restIndent);
        }

        /** Markdown inline markup, turned into colour or into nothing at all. */
        private String inline(String text) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '`') {
                    int end = text.indexOf('`', i + 1);
                    if (end > 0) {
                        out.append(cyan(text.substring(i + 1, end)));
                        i = end;
                        continue;
                    }
                }
                if (c == '*' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                    int end = text.indexOf("**", i + 2);
                    if (end > 0) {
                        out.append(bold(text.substring(i + 2, end)));
                        i = end + 1;
                        continue;
                    }
                }
                if (c == '[') {
                    int close = text.indexOf("](", i);
                    int end = close < 0 ? -1 : text.indexOf(')', close);
                    if (close > 0 && end > 0) {
                        out.append(text, i + 1, close).append(' ')
                                .append(dim("(" + shorten(text.substring(close + 2, end)) + ")"));
                        i = end;
                        continue;
                    }
                }
                out.append(c);
            }
            return out.toString();
        }

        /**
         * A long URL is noise in a terminal and useful in a browser, so the terminal gets the part
         * that says where to look. The README keeps the real link.
         */
        private static String shorten(String url) {
            String bare = url.replaceFirst("^https?://", "");
            if (bare.length() <= 56) {
                return bare;
            }
            String[] parts = bare.split("/");
            return parts.length < 3
                    ? bare
                    : parts[0] + "/.../" + parts[parts.length - 2] + "/" + parts[parts.length - 1];
        }

        /** Word wrapping that measures the printable text, not the escape codes inside it. */
        private void wrap(String text, String firstIndent, String restIndent) {
            int limit = WIDTH - firstIndent.length();
            StringBuilder current = new StringBuilder();
            int visible = 0;
            String indent = firstIndent;
            for (String word : text.split(" ")) {
                int wordWidth = printableLength(word);
                if (visible > 0 && visible + 1 + wordWidth > limit) {
                    System.out.println(indent + current);
                    current.setLength(0);
                    visible = 0;
                    indent = restIndent;
                    limit = WIDTH - restIndent.length();
                }
                if (visible > 0) {
                    current.append(' ');
                    visible++;
                }
                current.append(word);
                visible += wordWidth;
            }
            if (visible > 0) {
                System.out.println(indent + current);
            }
        }

        private static int printableLength(String text) {
            return text.replaceAll("\u001B\\[[0-9;]*m", "").length();
        }
    }

    // ------------------------------------------------------------------ modes

    /** One run of the program: the content, the terminal, the score sheet and the settings. */
    static final class Session {
        private final Bank bank;
        private final Ui ui;
        private final Progress progress;
        private final Random random = new Random();
        private final boolean shuffle;
        private Level filter;

        Session(Bank bank, Ui ui, Progress progress, boolean shuffle) {
            this.bank = bank;
            this.ui = ui;
            this.progress = progress;
            this.shuffle = shuffle;
        }

        void menu() {
            ui.banner(bank.questions().size(), bank.topics().size());
            while (true) {
                ui.line("  " + ui.bold("main menu"));
                ui.blank();
                ui.line("    [1] quiz one topic");
                ui.line("    [2] mock interview       12 mixed questions, hardest topics first");
                ui.line("    [3] random ten");
                ui.line("    [4] flashcards           no options, recall it yourself");
                ui.line("    [5] read a topic         questions and answers, no marking");
                ui.line("    [6] review my misses     the ones you got wrong before");
                ui.line("    [7] progress");
                ui.line("    [8] level filter         currently: "
                        + (filter == null ? "all" : filter.label()));
                ui.line("    [q] quit");
                ui.blank();
                String choice = ui.ask("choose >").toLowerCase(Locale.ROOT);
                ui.blank();
                switch (choice) {
                    case "1" -> quizTopic();
                    case "2" -> run(mockInterview(), "mock interview");
                    case "3" -> run(sample(pool(), 10), "random ten");
                    case "4" -> flashcards();
                    case "5" -> read();
                    case "6" -> reviewMisses();
                    case "7" -> progressScreen();
                    case "8" -> cycleFilter();
                    case "q", "quit", "exit" -> {
                        ui.line("  " + ui.dim("progress saved to " + PROGRESS_FILE + ". Good luck."));
                        ui.blank();
                        return;
                    }
                    default -> ui.line("  " + ui.yellow("pick a number from the list, or q to quit"));
                }
                ui.blank();
            }
        }

        private List<Question> pool() {
            return filter == null
                    ? bank.questions()
                    : bank.questions().stream().filter(q -> q.level() == filter).toList();
        }

        private void cycleFilter() {
            filter = switch (filter == null ? "all" : filter.label()) {
                case "all" -> Level.JUNIOR;
                case "junior" -> Level.MID;
                case "mid" -> Level.SENIOR;
                default -> null;
            };
            ui.line("  level filter: " + ui.bold(filter == null ? "all" : filter.label())
                    + ui.dim("   (" + pool().size() + " questions)"));
        }

        private Topic chooseTopic() {
            ui.line("  " + ui.bold("topics"));
            ui.blank();
            List<Topic> topics = bank.topics();
            for (int i = 0; i < topics.size(); i++) {
                Topic topic = topics.get(i);
                long known = topic.questions().stream()
                        .filter(q -> progress.right(q.id()) > 0)
                        .count();
                ui.line(String.format("    [%2d] %-46s %s %2d/%d", i + 1, topic.title(),
                        ui.bar((int) known, topic.questions().size(), 10),
                        known, topic.questions().size()));
            }
            ui.blank();
            String choice = ui.ask("topic number, or enter to go back >");
            ui.blank();
            try {
                int index = Integer.parseInt(choice.trim()) - 1;
                return index >= 0 && index < topics.size() ? topics.get(index) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private void quizTopic() {
            Topic topic = chooseTopic();
            if (topic == null) {
                return;
            }
            List<Question> questions = topic.questions().stream()
                    .filter(q -> filter == null || q.level() == filter)
                    .collect(Collectors.toCollection(ArrayList::new));
            if (questions.isEmpty()) {
                ui.line("  " + ui.yellow("no " + filter.label() + " questions in this topic"));
                return;
            }
            showDiagram(topic);
            run(questions, topic.title());
        }

        private void showDiagram(Topic topic) {
            if (topic.diagram().isBlank()) {
                return;
            }
            for (String line : topic.diagram().split("\n")) {
                ui.line("  " + ui.cyan(line));
            }
            ui.blank();
        }

        /**
         * Twelve questions weighted towards what you have not answered and what you got wrong, which
         * is roughly how a real interview finds the edge of what someone knows.
         */
        private List<Question> mockInterview() {
            List<Question> ranked = new ArrayList<>(pool());
            ranked.sort((a, b) -> Integer.compare(weight(b), weight(a)));
            List<Question> picked = new ArrayList<>(ranked.subList(0, Math.min(24, ranked.size())));
            Collections.shuffle(picked, random);
            List<Question> selected = picked.subList(0, Math.min(12, picked.size()));
            selected.sort((a, b) -> a.level().compareTo(b.level()));
            return new ArrayList<>(selected);
        }

        private int weight(Question q) {
            return progress.wrong(q.id()) * 3 + (progress.seen(q.id()) ? 0 : 2);
        }

        private List<Question> sample(List<Question> from, int count) {
            List<Question> copy = new ArrayList<>(from);
            Collections.shuffle(copy, random);
            return new ArrayList<>(copy.subList(0, Math.min(count, copy.size())));
        }

        private void reviewMisses() {
            List<Question> missed = bank.questions().stream()
                    .filter(q -> progress.wrong(q.id()) > 0)
                    .sorted((a, b) -> Integer.compare(progress.wrong(b.id()), progress.wrong(a.id())))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (missed.isEmpty()) {
                ui.line("  " + ui.dim("nothing missed yet. Take a quiz first."));
                return;
            }
            run(missed.subList(0, Math.min(15, missed.size())), "review");
        }

        // -- asking

        private void run(List<Question> questions, String title) {
            int score = 0;
            Map<String, int[]> byTopic = new LinkedHashMap<>();
            List<Question> missed = new ArrayList<>();

            for (int i = 0; i < questions.size(); i++) {
                Question question = questions.get(i);
                ui.blank();
                ui.header(title, "question " + (i + 1) + " of " + questions.size());
                ui.blank();
                Verdict verdict = ask(question, i + 1, questions.size(), score);
                if (verdict == Verdict.QUIT) {
                    break;
                }
                int[] tally = byTopic.computeIfAbsent(question.topic().title(), k -> new int[2]);
                tally[1]++;
                if (verdict == Verdict.CORRECT) {
                    score++;
                    tally[0]++;
                } else {
                    missed.add(question);
                }
                if (verdict != Verdict.SKIPPED) {
                    progress.record(question.id(), verdict == Verdict.CORRECT);
                    progress.save();
                }
            }
            summary(score, byTopic, missed);
        }

        private enum Verdict { CORRECT, WRONG, SKIPPED, QUIT }

        private Verdict ask(Question question, int number, int total, int score) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < question.options().size(); i++) {
                order.add(i);
            }
            if (shuffle) {
                Collections.shuffle(order, random);
            }

            ui.markdown("**Q.** " + question.prompt(), "  ");
            ui.line("  " + ui.dim("[" + question.level().label() + "] "
                    + String.join(", ", question.tags())));
            ui.blank();
            for (int i = 0; i < order.size(); i++) {
                Option option = question.options().get(order.get(i));
                ui.paragraph((char) ('a' + i) + ") " + option.text(), "    ", "       ");
            }
            ui.blank();
            long correctCount = question.options().stream().filter(Option::correct).count();
            String hint = correctCount > 1 ? "pick all that apply, e.g. ac" : "one letter";
            ui.line("  " + ui.dim(ui.bar(number - 1, total, 16) + "  " + (number - 1) + "/" + total
                    + "   score " + score));
            ui.blank();

            String input = ui.ask("answer (" + hint + ", s skip, q quit) >").toLowerCase(Locale.ROOT);
            if (input.equals("q")) {
                return Verdict.QUIT;
            }

            boolean correct;
            if (input.equals("s") || input.isBlank()) {
                correct = false;
                ui.blank();
                ui.line("  " + ui.yellow("skipped"));
            } else {
                List<Integer> chosen = new ArrayList<>();
                for (char c : input.toCharArray()) {
                    int index = c - 'a';
                    if (index >= 0 && index < order.size()) {
                        chosen.add(order.get(index));
                    }
                }
                correct = !chosen.isEmpty()
                        && chosen.size() == question.correctIndexes().size()
                        && question.correctIndexes().containsAll(chosen);
                ui.blank();
                ui.line(correct ? "  " + ui.green("correct") : "  " + ui.red("not quite"));
            }

            String right = order.stream()
                    .filter(i -> question.options().get(i).correct())
                    .map(i -> String.valueOf((char) ('a' + order.indexOf(i))))
                    .collect(Collectors.joining(", "));
            ui.line("  " + ui.dim("answer: " + right));
            ui.rule();
            ui.markdown(question.answer(), "  ");
            ui.blank();
            ui.pause(ui.dim("enter for the next question >"));
            return input.equals("s") || input.isBlank()
                    ? Verdict.SKIPPED
                    : (correct ? Verdict.CORRECT : Verdict.WRONG);
        }

        private void summary(int score, Map<String, int[]> byTopic, List<Question> missed) {
            int total = byTopic.values().stream().mapToInt(t -> t[1]).sum();
            if (total == 0) {
                return;
            }
            int percent = score * 100 / total;
            ui.blank();
            ui.box(List.of(
                    "  score  " + score + " / " + total + "   " + ui.bar(score, total, 24)
                            + "   " + percent + "%",
                    "  " + verdictLine(percent)));
            ui.blank();
            ui.line("  " + ui.bold("by topic"));
            byTopic.forEach((topic, tally) -> ui.line(String.format("    %-46s %s %2d/%d", topic,
                    ui.bar(tally[0], tally[1], 10), tally[0], tally[1])));
            if (!missed.isEmpty()) {
                ui.blank();
                ui.line("  " + ui.bold("worth a second look"));
                for (Question question : missed) {
                    ui.paragraph("- " + question.prompt(), "    ", "      ");
                }
            }
            ui.blank();
            ui.pause(ui.dim("enter for the menu >"));
        }

        private String verdictLine(int percent) {
            if (percent == 100) {
                return "spotless. Try the senior filter, or the puzzles topic.";
            }
            if (percent >= 80) {
                return "solid. The misses below are the ones to read twice.";
            }
            if (percent >= 50) {
                return "the shape is there. Read the explanations, then come back tomorrow.";
            }
            return "worth reading the topic end to end before quizzing it again.";
        }

        // -- reading modes

        private void flashcards() {
            Topic topic = chooseTopic();
            List<Question> questions = topic == null ? sample(pool(), 10) : topic.questions();
            for (Question question : questions) {
                ui.blank();
                ui.header("flashcard", question.level().label());
                ui.blank();
                ui.markdown("**Q.** " + question.prompt(), "  ");
                ui.blank();
                String input = ui.ask(ui.dim("enter to reveal, q to stop >"));
                if (input.equalsIgnoreCase("q")) {
                    return;
                }
                ui.rule();
                ui.markdown(question.answer(), "  ");
                ui.blank();
                String knew = ui.ask("did you have it? [y]es [n]o [q]uit >").toLowerCase(Locale.ROOT);
                if (knew.equals("q")) {
                    return;
                }
                progress.record(question.id(), knew.startsWith("y"));
                progress.save();
            }
        }

        private void read() {
            Topic topic = chooseTopic();
            if (topic == null) {
                return;
            }
            ui.header(topic.title(), topic.questions().size() + " questions");
            ui.blank();
            ui.markdown(topic.intro(), "  ");
            ui.blank();
            showDiagram(topic);
            for (Question question : topic.questions()) {
                ui.markdown("**Q.** " + question.prompt(), "  ");
                ui.line("  " + ui.dim("[" + question.level().label() + "]"));
                ui.blank();
                ui.markdown(question.answer(), "    ");
                ui.blank();
                ui.rule();
                if (ui.ask(ui.dim("enter to continue, q to stop >")).equalsIgnoreCase("q")) {
                    return;
                }
                ui.blank();
            }
        }

        private void progressScreen() {
            int seen = (int) bank.questions().stream().filter(q -> progress.seen(q.id())).count();
            int mastered = (int) bank.questions().stream()
                    .filter(q -> progress.right(q.id()) > progress.wrong(q.id()))
                    .count();
            ui.box(List.of(
                    "  seen      " + ui.bar(seen, bank.questions().size(), 30) + "  " + seen + "/"
                            + bank.questions().size(),
                    "  mastered  " + ui.bar(mastered, bank.questions().size(), 30) + "  " + mastered
                            + "/" + bank.questions().size()));
            ui.blank();
            for (Topic topic : bank.topics()) {
                long known = topic.questions().stream()
                        .filter(q -> progress.right(q.id()) > progress.wrong(q.id()))
                        .count();
                ui.line(String.format("    %-46s %s %2d/%d", topic.title(),
                        ui.bar((int) known, topic.questions().size(), 10), known,
                        topic.questions().size()));
            }
            ui.blank();
            ui.pause(ui.dim("enter for the menu >"));
        }
    }

    // ------------------------------------------------------------- generation

    /** Markers in README.md. Everything between them is generated; everything else is hand-written. */
    private static final String BEGIN = "<!-- BEGIN QUESTIONS -->";
    private static final String END = "<!-- END QUESTIONS -->";

    /**
     * Writes the question catalogue into README.md.
     *
     * <p>Questions are shown; answers are folded away behind {@code <details>}, so the README reads as
     * a self-test rather than a wall of prose. The options are left out on purpose: a checklist with a
     * ticked box gives the answer away before the reader has had a chance to think.
     */
    static String catalogue(Bank bank) {
        StringBuilder out = new StringBuilder();
        out.append(BEGIN).append("\n\n");
        out.append("| # | Topic | Questions | junior / mid / senior |\n");
        out.append("|---|---|---|---|\n");
        List<Topic> topics = bank.topics();
        for (int i = 0; i < topics.size(); i++) {
            Topic topic = topics.get(i);
            long junior = count(topic, Level.JUNIOR);
            long mid = count(topic, Level.MID);
            long senior = count(topic, Level.SENIOR);
            out.append("| ").append(i + 1).append(" | [").append(topic.title()).append("](#")
                    .append(anchor(topic.title())).append(") | ").append(topic.questions().size())
                    .append(" | ").append(junior).append(" / ").append(mid).append(" / ")
                    .append(senior).append(" |\n");
        }
        out.append("\n");

        int number = 0;
        for (Topic topic : topics) {
            out.append("### ").append(topic.title()).append("\n\n");
            if (!topic.intro().isBlank()) {
                out.append(topic.intro()).append("\n\n");
            }
            if (!topic.diagram().isBlank()) {
                out.append("```\n").append(topic.diagram()).append("\n```\n\n");
            }
            for (Question question : topic.questions()) {
                number++;
                out.append("<details>\n<summary><b>").append(number).append(". ")
                        .append(escapeHtml(question.prompt())).append("</b>  <sub>")
                        .append(question.level().label()).append("</sub></summary>\n\n")
                        .append(question.answer()).append("\n\n</details>\n\n");
            }
            out.append("[back to top](#contents)\n\n");
        }
        out.append(END);
        return out.toString();
    }

    private static long count(Topic topic, Level level) {
        return topic.questions().stream().filter(q -> q.level() == level).count();
    }

    /** GitHub's heading anchor rules, for the subset of characters these titles actually use. */
    static String anchor(String title) {
        return title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 -]", "")
                .replace(' ', '-');
    }

    /** The prompts contain code and the occasional angle bracket; the summary line is raw HTML. */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static void exportReadme(Bank bank) {
        Path readme = questionsDir().getParent().resolve("README.md");
        String generated = catalogue(bank);
        String body;
        try {
            String existing = Files.isRegularFile(readme)
                    ? Files.readString(readme, StandardCharsets.UTF_8)
                    : "";
            int begin = existing.indexOf(BEGIN);
            int end = existing.indexOf(END);
            if (begin < 0 || end < 0) {
                throw new IllegalStateException(
                        "README.md is missing the " + BEGIN + " / " + END + " markers");
            }
            body = existing.substring(0, begin) + generated + existing.substring(end + END.length());
            Files.writeString(readme, body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + readme, e);
        }
        System.out.println("README.md updated: " + bank.questions().size() + " questions across "
                + bank.topics().size() + " topics");
    }

    static void stats(Bank bank) {
        System.out.printf("%-48s %5s %7s %5s %7s%n", "topic", "all", "junior", "mid", "senior");
        for (Topic topic : bank.topics()) {
            System.out.printf("%-48s %5d %7d %5d %7d%n", topic.title(), topic.questions().size(),
                    count(topic, Level.JUNIOR), count(topic, Level.MID), count(topic, Level.SENIOR));
        }
        System.out.printf("%-48s %5d %7d %5d %7d%n", "total", bank.questions().size(),
                bank.questions().stream().filter(q -> q.level() == Level.JUNIOR).count(),
                bank.questions().stream().filter(q -> q.level() == Level.MID).count(),
                bank.questions().stream().filter(q -> q.level() == Level.SENIOR).count());
    }

    // -------------------------------------------------------------------- main

    public static void main(String[] args) {
        List<String> flags = List.of(args);
        if (flags.contains("--help") || flags.contains("-h")) {
            System.out.println("""
                    java-concurrency-interview

                      (no arguments)     the interactive quiz
                      --export-readme    regenerate the catalogue inside README.md
                      --stats            question counts by topic and level
                      --no-color         plain output, no ANSI escapes
                      --no-shuffle       keep options in the order the file lists them
                      -Dquestions=DIR    read questions from somewhere other than ./questions
                    """);
            return;
        }

        Bank bank = Bank.load();
        if (flags.contains("--export-readme")) {
            exportReadme(bank);
            return;
        }
        if (flags.contains("--stats")) {
            stats(bank);
            return;
        }

        boolean color = !flags.contains("--no-color") && System.getenv("NO_COLOR") == null;
        Progress progress = new Progress(questionsDir().getParent().resolve(PROGRESS_FILE));
        new Session(bank, new Ui(color), progress, !flags.contains("--no-shuffle")).menu();
    }
}
