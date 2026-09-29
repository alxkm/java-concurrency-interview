# Changelog

Each release is a tag and a GitHub release with the runnable jar attached. The release notes are the
section below whose heading matches the tag, so add one before tagging.

## 1.0.0

The first release.

- 173 questions across 16 topics, from threads and the memory model to virtual threads, real
  systems and live coding, each with an answer that explains the mechanism.
- A terminal quiz in one dependency-free file: quiz by topic, mock interview, random ten,
  flashcards, reading mode, review of misses, progress, and a level filter.
- A runnable jar with the questions inside, for `java -jar` and
  `jbang quiz@alxkm/java-concurrency-interview`.
- Checks in CI: every question parses and is well formed, every Java block in the answers compiles
  or parses, and the README cannot drift from the questions.
