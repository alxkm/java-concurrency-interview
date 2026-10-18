// Builds the web version into _site/ (or the directory given as the first argument).
//
//   cd tools/site && npm ci && node build.mjs        (Node 20 or newer)
//   python3 -m http.server -d ../../_site 8000
//
// 1. Exports questions/ to JSON with the quiz's own parser, so the site reads the same files the
//    terminal quiz does.
// 2. Renders every prompt, option and answer from markdown to HTML once, here, with Java highlighted.
//    The interactive page and the static pages use that same HTML, and the browser needs no markdown
//    or highlighting library at all.
// 3. Writes a page per question, a static list of them into index.html, and a sitemap, so the content
//    is readable without JavaScript and findable by search engines. The interactive page alone is
//    an empty <main> to anything that does not run its script.

import { execFileSync } from 'node:child_process';
import { copyFileSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import hljs from 'highlight.js';
import { Marked } from 'marked';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '../..');
const out = resolve(process.cwd(), process.argv[2] ?? join(root, '_site'));
const BASE = (process.env.SITE_URL ?? 'https://alxkm.github.io/java-concurrency-interview/').replace(/\/?$/, '/');
const REPO = 'https://github.com/alxkm/java-concurrency-interview';

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

const marked = new Marked({
  renderer: {
    // The questions are markdown, never HTML, so raw HTML in them is shown as text and never run.
    html: ({ text }) => esc(text),
    // Only fences marked as a language are highlighted; the plain ones in the answers are ASCII diagrams.
    code({ text, lang }) {
      const language = lang && hljs.getLanguage(lang) ? lang : null;
      const body = language ? hljs.highlight(text, { language }).value : esc(text);
      return `<pre><code class="hljs${language ? ' language-' + language : ''}">${body}</code></pre>\n`;
    },
  },
});
const block = (md) => marked.parse(md);
const inline = (md) => marked.parseInline(md);

/** Plain text of rendered HTML, for titles and meta descriptions. */
const plain = (html) => html.replace(/<[^>]+>/g, '')
  .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&amp;/g, '&')
  .replace(/\s+/g, ' ').trim();

const clip = (text, max) => text.length <= max ? text : text.slice(0, text.lastIndexOf(' ', max - 1)) + '...';

// -- 1. export

const exported = join(tmpdir(), `questions-${process.pid}.json`);
execFileSync('java', [join(root, 'src/main/java/org/alxkm/interview/Quiz.java'), '--export-json', exported],
  { cwd: root, stdio: ['ignore', 'ignore', 'inherit'] });
const data = JSON.parse(readFileSync(exported, 'utf8'));
rmSync(exported);

// -- 2. render

const all = [];
for (const topic of data.topics) {
  topic.introHtml = topic.intro ? block(topic.intro.split('\n\n')[0]) : '';
  for (const q of topic.questions) {
    q.promptHtml = inline(q.prompt);
    q.answerHtml = block(q.answer);
    for (const o of q.options) o.html = inline(o.text);
    all.push({ q, topic });
  }
}

// -- 3. write

rmSync(out, { recursive: true, force: true });
mkdirSync(join(out, 'vendor'), { recursive: true });
for (const file of readdirSync(join(root, 'site'))) {
  if (file !== 'index.html') copyFileSync(join(root, 'site', file), join(out, file));
}
const styles = join(here, 'node_modules/highlight.js/styles');
copyFileSync(join(styles, 'github.min.css'), join(out, 'vendor/hljs-light.css'));
copyFileSync(join(styles, 'github-dark.min.css'), join(out, 'vendor/hljs-dark.css'));
writeFileSync(join(out, 'questions.json'), JSON.stringify(data));

// The static list inside index.html: every question, linked to its page.
let number = 0;
const list = data.topics.map((topic) => {
  const start = number + 1;
  const items = topic.questions.map((q) => {
    number++;
    return `<li><a href="q/${q.id}/">${q.promptHtml}</a> <span class="level ${q.level}">${q.level}</span></li>`;
  }).join('\n');
  return `<section class="topic"><h2 id="${topic.id}">${esc(topic.title)}</h2>\n${topic.introHtml}`
    + `<ol class="static-list" start="${start}">\n${items}\n</ol></section>`;
}).join('\n');
const index = readFileSync(join(root, 'site/index.html'), 'utf8');
if (!index.includes('<!-- STATIC -->')) throw new Error('site/index.html has lost its <!-- STATIC --> marker');
writeFileSync(join(out, 'index.html'), index.replace('<!-- STATIC -->', list));

// One page per question.
const head = index.match(/<link rel="icon" href="[^"]*">/)[0];
all.forEach(({ q, topic }, i) => {
  const url = `${BASE}q/${q.id}/`;
  const title = plain(q.promptHtml);
  // The answer's prose paragraphs without its code, cut at a word near the length search results show.
  const description = clip(plain((q.answerHtml.match(/<p>[\s\S]*?<\/p>/g) ?? []).join(' ')), 155);
  const prev = all[i - 1]?.q;
  const next = all[i + 1]?.q;
  const options = q.options.map((o) => o.correct
    ? `<li class="opt correct"><span class="sr-only">Correct: </span>${o.html}</li>`
    : `<li class="opt">${o.html}</li>`).join('\n');
  const page = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)} | Java Concurrency Interview</title>
<meta name="description" content="${esc(description)}">
<link rel="canonical" href="${url}">
<meta property="og:type" content="article">
<meta property="og:title" content="${esc(title)}">
<meta property="og:description" content="${esc(description)}">
<meta property="og:url" content="${url}">
<meta property="og:image" content="${BASE}social-card.png">
<meta property="og:image:width" content="1280">
<meta property="og:image:height" content="640">
<meta name="twitter:card" content="summary_large_image">
${head}
<link rel="stylesheet" href="../../vendor/hljs-light.css" media="(prefers-color-scheme: light)">
<link rel="stylesheet" href="../../vendor/hljs-dark.css" media="(prefers-color-scheme: dark)">
<link rel="stylesheet" href="../../style.css">
</head>
<body>
<header class="site-bar"><div class="wrap"><a class="home" href="../../">Java Concurrency <span>Interview</span></a></div></header>
<main class="wrap article">
<p class="crumbs"><a href="../../?mode=read&amp;topic=${topic.id}">${esc(topic.title)}</a>
  <span>question ${i + 1} of ${all.length}</span> <span class="level ${q.level}">${q.level}</span></p>
<h1>${q.promptHtml}</h1>
<ul class="opts static">
${options}
</ul>
<div class="md">
${q.answerHtml}
</div>
<p class="tags">${q.tags.map((t) => `<span class="tag">${esc(t)}</span>`).join(' ')}</p>
<p class="actions">
  <a class="btn" href="../../?mode=quiz&amp;topic=${topic.id}">Quiz yourself on ${esc(topic.title)}</a>
  <a href="${REPO}/blob/master/questions/${topic.id}.md">Suggest a correction</a>
</p>
<nav class="pager">
  ${prev ? `<a rel="prev" href="../${prev.id}/"><small>Previous</small>${prev.promptHtml}</a>` : '<span></span>'}
  ${next ? `<a rel="next" href="../${next.id}/"><small>Next</small>${next.promptHtml}</a>` : '<span></span>'}
</nav>
</main>
<footer><div class="wrap">From <a href="${REPO}">java-concurrency-interview</a>.
  <a href="../../">All questions</a>, or the terminal quiz: <code>jbang quiz@alxkm/java-concurrency-interview</code></div></footer>
</body>
</html>
`;
  mkdirSync(join(out, 'q', q.id), { recursive: true });
  writeFileSync(join(out, 'q', q.id, 'index.html'), page);
});

const urls = [BASE, ...all.map(({ q }) => `${BASE}q/${q.id}/`)];
writeFileSync(join(out, 'sitemap.xml'), '<?xml version="1.0" encoding="UTF-8"?>\n'
  + '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
  + urls.map((u) => `  <url><loc>${u}</loc></url>`).join('\n') + '\n</urlset>\n');

const pages = readdirSync(join(out, 'q')).length;
if (pages !== all.length) throw new Error(`wrote ${pages} question pages for ${all.length} questions`);
console.log(`${out}: ${data.topics.length} topics, ${pages} question pages, sitemap with ${urls.length} urls`);
