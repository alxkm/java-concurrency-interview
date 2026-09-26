# Contributing

Questions, corrections and better explanations are all welcome. Corrections most of all: JDK versions
move under these answers, and an answer that was right in Java 17 can be misleading in Java 25.

## What makes a good question here

- **Someone actually asks it.** If you have been asked it in an interview, or you ask it as an
  interviewer, it belongs. Invented trivia does not.
- **The answer teaches a mechanism.** If it can be answered by reciting one sentence from the javadoc,
  it is a definition, not a question. Aim for the reasoning that survives the follow-up.
- **The wrong options are plausible.** Every distractor should be something a competent person might
  believe. Comedy options make the quiz worthless.
- **It says when it stopped being true.** Biased locking, monitor pinning, the common pool's size: name
  the JDK version wherever the answer depends on one.

## The format

One file per topic in [questions/](./questions), plain markdown that renders on GitHub and is parsed
directly by the quiz. A file looks like this:

````markdown
# Topic title

One or two sentences on why this topic is asked, shown in the quiz and in the README.

```
   an optional ASCII diagram, the first fenced block in the file
```

## The question, worded the way an interviewer asks it
- id: unique-slug
- level: junior | mid | senior
- tags: volatile, visibility

* [ ] a wrong option that someone would plausibly pick
* [x] the right one

The explanation, in markdown, until the next `##` heading. Code blocks, lists and links all work.
````

Rules the parser and the tests enforce:

- `id` is unique across every file and never changes once merged, because progress files reference it.
- At least three options, at least one marked `[x]`, and not all of them marked.
- The explanation is longer than a sentence. If it is not, the question is not pulling its weight.
- The correct option is not conspicuously longer than the wrong ones. That is the oldest tell in
  multiple choice, and there is a test for it.

Several correct options are allowed. The quiz notices and asks for all of them.

## Before you open a pull request

```bash
./gradlew readme     # regenerates the catalogue in README.md from questions/
./gradlew test       # content checks, and the README-is-current check
```

Both are fast. The test suite will tell you exactly which question is malformed and why.

## Style

The prose here is plain English written for someone reading under pressure. Short sentences, no
exclamation marks, no "simply" or "just", and no em dashes. Numbers come with a source: if an answer
claims something is faster, say by how much and where it was measured.

Code samples are the shortest thing that shows the mechanism. Skip imports, skip boilerplate, keep the
comment that explains the line someone would get wrong.

## Adding a topic

A new file in `questions/`, numbered so it sorts into the right place, with at least eight questions.
Renumbering the existing files in the same pull request is fine, and the README regenerates itself
from the file order.
