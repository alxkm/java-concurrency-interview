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
// 4. Checks the result: every internal link resolves, nothing is escaped twice, and every question
//    page has a title, a heading and a description. Any problem fails the build.

import { execFileSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, relative, resolve } from 'node:path';
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

// Related questions: the ones sharing the most tags, a rare tag counting for more than a common one
// (sharing "varhandle" says more than sharing "executors"), the same topic breaking ties. A tag no
// other question has cannot relate anything, and the previous and next questions are already linked.
// Tags that say what kind of question it is rather than what it is about: two "basics" questions are
// not related to each other.
const GENERIC = new Set(['basics', 'theory', 'design', 'api', 'puzzle']);
const df = new Map();
for (const { q } of all) for (const t of new Set(q.tags)) df.set(t, (df.get(t) ?? 0) + 1);
const idf = (t) => Math.log(all.length / df.get(t));
all.forEach(({ q, topic }, i) => {
  const skip = new Set([q.id, all[i - 1]?.q.id, all[i + 1]?.q.id]);
  q.related = all
    .filter(({ q: o }) => !skip.has(o.id))
    .map(({ q: o, topic: ot }, j) => {
      const shared = o.tags.filter((t) => q.tags.includes(t) && df.get(t) > 1 && !GENERIC.has(t));
      return { id: o.id, shared: shared.reduce((s, t) => s + idf(t), 0), bonus: ot === topic ? 0.5 : 0, j };
    })
    .filter((x) => x.shared > 0)
    .sort((a, b) => (b.shared + b.bonus) - (a.shared + a.bonus) || a.j - b.j)
    .slice(0, 4)
    .map((x) => x.id);
});
const byId = new Map(all.map(({ q }) => [q.id, q]));

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
${q.related.length ? `<section class="related">
<h2>Related questions</h2>
<ul>
${q.related.map((id) => byId.get(id)).map((r) => `<li><a href="../${r.id}/">${r.promptHtml}</a> <span class="level ${r.level}">${r.level}</span></li>`).join('\n')}
</ul>
</section>` : ''}
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

// -- 4. check what was written
//
// A site that builds can still be broken: a link to a page that was never written, or text escaped
// twice, which once showed CompletableFuture<Void> as CompletableFuture&lt;Void&gt; on every quiz card.

const problems = [];
const pages = readdirSync(join(out, 'q')).length;
if (pages !== all.length) problems.push(`wrote ${pages} question pages for ${all.length} questions`);

const twice = /&amp;(?:lt|gt|amp|quot|#39);/;
if (twice.test(JSON.stringify(data))) problems.push('questions.json: HTML escaped twice');

const sitePath = new URL(BASE).pathname;
const walk = (dir) => readdirSync(dir, { withFileTypes: true })
  .flatMap((e) => e.isDirectory() ? walk(join(dir, e.name)) : [join(dir, e.name)]);
let links = 0;
for (const file of walk(out).filter((f) => f.endsWith('.html'))) {
  const html = readFileSync(file, 'utf8');
  const name = relative(out, file).replaceAll('\\', '/');
  if (twice.test(html)) problems.push(`${name}: HTML escaped twice`);
  if (name.startsWith('q/')) {
    if (!/<title>[^<|]+\S \| /.test(html)) problems.push(`${name}: no title`);
    if (!/<h1>[\s\S]+?<\/h1>/.test(html)) problems.push(`${name}: no heading`);
    const description = html.match(/<meta name="description" content="([^"]*)">/)?.[1] ?? '';
    if (description.length < 50) problems.push(`${name}: description too short: "${description}"`);
  }
  for (const [, href] of html.matchAll(/\b(?:href|src)="([^"]+)"/g)) {
    if (/^(?:[a-z]+:|#)/.test(href)) continue;
    links++;
    const path = decodeURI(href.split(/[?#]/)[0]);
    const target = path.startsWith('/')
      ? join(out, path.startsWith(sitePath) ? path.slice(sitePath.length) : path)
      : resolve(dirname(file), path);
    const found = existsSync(target) && (statSync(target).isFile() || existsSync(join(target, 'index.html')));
    if (!found) problems.push(`${name}: broken link ${href}`);
  }
}
if (problems.length) {
  console.error(problems.slice(0, 50).join('\n'));
  throw new Error(`${problems.length} problem(s) in the built site`);
}
const withRelated = all.filter(({ q }) => q.related.length).length;
console.log(`${out}: ${data.topics.length} topics, ${pages} question pages (${withRelated} with related questions), `
  + `sitemap with ${urls.length} urls, ${links} internal links checked`);
