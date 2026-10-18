# Changelog

Each release is a tag and a GitHub release with the runnable jar attached. The release notes are the
section below whose heading matches the tag, so add one before tagging.

## 1.1.0

180 questions, up from 173, and a web version.

- Seven questions that are asked constantly and were missing: an uncaught exception in a thread,
  data race against race condition, what the memory model promises a correctly synchronized
  program, `volatile` arrays, `add`/`offer`/`put` on a `BlockingQueue`, `Timer` against
  `ScheduledExecutorService`, and the thread-safe ways to write a singleton.
- The web version at https://alxkm.github.io/java-concurrency-interview/: read and search every
  question, or take the quiz in the browser, with the same "two right in a row" review rule as the
  terminal. Every question has its own page, there is a sitemap, and the site works without
  JavaScript. `?mode=quiz&topic=...&level=...` opens a quiz on one topic.
- `docs/jdk-versions.md`: what changed in which JDK, from JSR-133 to `ScopedValue` going final in
  Java 25, each row linked to the question that explains it.
- `--export-json` writes the question bank as JSON.

## 1.0.0

The first release.

- 173 questions across 16 topics, from threads and the memory model to virtual threads, real
  systems and live coding, each with an answer that explains the mechanism.
- A terminal quiz in one dependency-free file: quiz by topic, mock interview, random ten,
  flashcards, reading mode, review of misses, progress, and a level filter. A missed question stays in
  review until it has been answered right twice in a row.
- A runnable jar with the questions inside, for `java -jar` and
  `jbang quiz@alxkm/java-concurrency-interview`.
- Checks in CI: every question parses and is well formed, every Java block in the answers compiles
  or parses, and the README cannot drift from the questions.
