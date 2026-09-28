// Ticket breakdown agent — enhanced Stage 1, no coding-agent hand-off.
//
// Runs entirely inside the coding repo's own GitHub Actions. Touches:
//   - Jira: READ ONLY (search/GET). No comments, no labels, no field writes.
//   - Reference repo: READ ONLY checkout, persist-credentials:false in the
//     workflow, so this job has no credential that could push there.
//   - Coding repo (this repo): creates one issue per new ticket — that's the
//     only write anywhere in this pipeline, and it's the repo's own default
//     Actions token doing it (issues: write permission), nothing fancier.
//
// No Copilot coding agent required — this produces a rich, grounded issue
// for a human to pick up: real file references, a starter code sketch,
// suggested subtasks, and test cases, instead of just a text explanation.
//
// LLM: Google Gemini free tier (no Anthropic subscription/API billing
// needed). Model names shift — check
// https://ai.google.dev/gemini-api/docs/models if GEMINI_MODEL needs updating.
//
// Requires Node 20+ (global fetch).

import { readFile, readdir, stat } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";

const {
  JIRA_BASE_URL,
  JIRA_EMAIL,
  JIRA_API_TOKEN,
  JIRA_JQL,
  GEMINI_API_KEY,
  GEMINI_MODEL = "gemini-flash-latest",
  GITHUB_TOKEN,           // the default Actions token is enough — no PAT needed anywhere in this version
  GITHUB_REPOSITORY,      // this repo (owner/coding-repo), auto-provided by Actions
  REFERENCE_DOCS_PATH = "reference-repo",
} = process.env;

for (const [name, val] of Object.entries({
  JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN, GEMINI_API_KEY, GITHUB_TOKEN, GITHUB_REPOSITORY,
})) {
  if (!val) throw new Error(`Missing required env var: ${name}`);
}

const [OWNER, REPO] = GITHUB_REPOSITORY.split("/");

// ---------- Jira: READ ONLY ----------

const jiraAuth = Buffer.from(`${JIRA_EMAIL}:${JIRA_API_TOKEN}`).toString("base64");

async function jiraSearch() {
  const jql = JIRA_JQL || `status = "Ready for Dev" ORDER BY created ASC`;
  const res = await fetch(`${JIRA_BASE_URL}/rest/api/3/search`, {
    method: "POST", // query endpoint, despite the verb — reads only, never mutates
    headers: {
      Authorization: `Basic ${jiraAuth}`,
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ jql, maxResults: 10, fields: ["summary", "description"] }),
  });
  if (!res.ok) throw new Error(`Jira search failed: ${res.status} ${await res.text()}`);
  return (await res.json()).issues;
}

function adfToPlainText(adf) {
  if (!adf || !adf.content) return "";
  let text = "";
  const walk = (node) => {
    if (node.type === "text") text += node.text;
    if (node.type === "paragraph" || node.type === "heading") text += "\n";
    if (node.content) node.content.forEach(walk);
  };
  adf.content.forEach(walk);
  return text.trim();
}

// ---------- gather real context (grounding, not guessing) ----------

async function walkFiles(dir, {
  maxFiles = 4000,
  skip = [".git", "node_modules", "dist", "build", ".next", "target", ".gradle", "venv", ".venv", "__pycache__", "coverage", "reference-repo"],
} = {}) {
  const results = [];
  async function recurse(current) {
    if (results.length >= maxFiles) return;
    let entries;
    try {
      entries = await readdir(current, { withFileTypes: true });
    } catch {
      return;
    }
    for (const entry of entries) {
      if (skip.includes(entry.name)) continue;
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) await recurse(full);
      else {
        results.push(full);
        if (results.length >= maxFiles) return;
      }
    }
  }
  if (existsSync(dir)) await recurse(dir);
  return results;
}

function keywordsFromTicket(summary, description) {
  return `${summary} ${description}`
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, " ")
    .split(/\s+/)
    .filter((w) => w.length > 3)
    .slice(0, 15);
}

// Extensions we never bother reading content for — either binary or not
// useful to keyword-search. Path matching still applies to these.
const CONTENT_SCAN_SKIP_EXTS = new Set([
  ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".svg", ".pdf",
  ".zip", ".gz", ".tar", ".jar", ".war", ".class",
  ".woff", ".woff2", ".ttf", ".eot", ".mp4", ".mp3", ".mov", ".avi",
  ".exe", ".dll", ".so", ".lock",
]);
const MAX_CONTENT_SCAN_BYTES = 200 * 1024; // skip content-scanning very large files; path score still counts

async function scoreFile(f, keywords) {
  const lower = f.toLowerCase();
  const pathScore = keywords.filter((k) => lower.includes(k)).length * 3; // path hits weighted higher — a filename match is a stronger signal than one word buried in a big file

  if (CONTENT_SCAN_SKIP_EXTS.has(path.extname(lower))) return pathScore;

  try {
    const info = await stat(f);
    if (info.size > MAX_CONTENT_SCAN_BYTES) return pathScore;

    const content = (await readFile(f, "utf8")).toLowerCase();
    let contentScore = 0;
    for (const k of keywords) {
      const occurrences = content.split(k).length - 1;
      contentScore += Math.min(occurrences, 3); // cap per keyword so one repeated word can't dominate the ranking
    }
    return pathScore + contentScore;
  } catch {
    // binary file that slipped past the extension check, or a permissions/race issue — path score only
    return pathScore;
  }
}

async function findRelevant(dir, keywords, { exts = null, max = 5, snippetLen = 1500 } = {}) {
  const all = (await walkFiles(dir)).filter((f) => !exts || exts.some((e) => f.toLowerCase().endsWith(e)));
  const scores = await Promise.all(all.map((f) => scoreFile(f, keywords)));
  const scored = all
    .map((f, i) => ({ f, score: scores[i] }))
    .filter((x) => x.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, max);

  const out = [];
  for (const { f } of scored) {
    try {
      const content = await readFile(f, "utf8");
      out.push({ path: path.relative(dir, f), snippet: content.slice(0, snippetLen) });
    } catch {
      out.push({ path: path.relative(dir, f), snippet: "" });
    }
  }
  return out;
}

// Sprint docs are few (1-2 per sprint) and small — cheaper and more reliable
// to just include all of them, up to a total character budget, than to try
// to guess relevance from filenames (which rarely mention ticket keywords).
async function readAllDocs(dir, { exts = [".md"], totalCharBudget = 8000 } = {}) {
  const all = (await walkFiles(dir)).filter((f) => exts.some((e) => f.toLowerCase().endsWith(e)));
  const out = [];
  let used = 0;
  for (const f of all) {
    if (used >= totalCharBudget) break;
    try {
      const content = await readFile(f, "utf8");
      const remaining = totalCharBudget - used;
      const snippet = content.slice(0, Math.max(0, remaining));
      out.push({ path: path.relative(dir, f), snippet });
      used += snippet.length;
    } catch {
      // skip unreadable files
    }
  }
  return out;
}

// ---------- Gemini (free tier) ----------

async function callGemini(prompt) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${GEMINI_API_KEY}`;
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contents: [{ parts: [{ text: prompt }] }],
      generationConfig: { responseMimeType: "application/json", temperature: 0.2 },
    }),
  });
  if (!res.ok) throw new Error(`Gemini call failed: ${res.status} ${await res.text()}`);
  const data = await res.json();
  const text = data.candidates?.[0]?.content?.parts?.map((p) => p.text).join("") ?? "{}";
  try {
    return JSON.parse(text);
  } catch {
    throw new Error(`Gemini did not return valid JSON: ${text.slice(0, 300)}`);
  }
}

function buildPrompt(ticket, summary, description, ownRepoFiles, referenceDocs) {
  return `You are a senior engineer triaging a Jira ticket for a human developer who will implement it manually. You have no write access to Jira or the codebase — your output is read-only analysis and scaffolding.

Ticket ${ticket.key}: ${summary}
Description:
${description || "(no description provided)"}

Relevant existing files in the codebase (grounded — real files, use them, don't invent paths):
${ownRepoFiles.length ? ownRepoFiles.map((f) => `--- ${f.path} ---\n${f.snippet}`).join("\n\n") : "(no obviously matching files found — say so, don't guess)"}

Relevant excerpts from past sprint documentation (context on prior decisions/patterns):
${referenceDocs.length ? referenceDocs.map((d) => `--- ${d.path} ---\n${d.snippet}`).join("\n\n") : "(none found)"}

Respond with ONLY a JSON object, no markdown fences, matching exactly this shape:
{
  "complexity": "low" | "medium" | "high",
  "acceptance_criteria": string[],
  "relevant_existing_files": string[],
  "duplicate_or_related_work": string,
  "suggested_subtasks": string[],
  "suggested_test_cases": string[],
  "clarifying_questions": string[],
  "starter_snippet": { "filename": string, "language": string, "code": string, "note": string }
}
starter_snippet should be a best-effort, clearly incomplete sketch (function/class signature, rough shape) that gives the developer a starting point — not a full implementation, not meant to be applied directly. If nothing sensible can be sketched from the given context, set "code" to an empty string and explain why in "note".`;
}

// ---------- GitHub REST — write only to THIS repo, default token is enough ----------

async function ghRest(pathname, options = {}) {
  const res = await fetch(`https://api.github.com${pathname}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${GITHUB_TOKEN}`,
      Accept: "application/vnd.github+json",
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
  });
  if (!res.ok) throw new Error(`GitHub API ${pathname} failed: ${res.status} ${await res.text()}`);
  return res.json();
}

async function alreadyHasIssue(ticketKey) {
  const q = encodeURIComponent(`repo:${OWNER}/${REPO} in:title "[${ticketKey}]"`);
  const data = await ghRest(`/search/issues?q=${q}`);
  return data.total_count > 0;
}

function formatIssueBody(ticket, breakdown) {
  const bullets = (items) => (items?.length ? items.map((i) => `- ${i}`).join("\n") : "_None_");
  const checklist = (items) => (items?.length ? items.map((i) => `- [ ] ${i}`).join("\n") : "_None_");
  const snippet = breakdown.starter_snippet || {};

  return `**Jira ticket:** [${ticket.key}](${JIRA_BASE_URL}/browse/${ticket.key}) _(read-only reference — nothing written back to Jira)_
**Complexity:** ${breakdown.complexity}

### Acceptance criteria
${checklist(breakdown.acceptance_criteria)}

### Suggested subtasks
${checklist(breakdown.suggested_subtasks)}

### Relevant existing files
${bullets(breakdown.relevant_existing_files)}

### Related / duplicate work
${breakdown.duplicate_or_related_work || "_None noted_"}

### Suggested test cases
${bullets(breakdown.suggested_test_cases)}

### Clarifying questions
${bullets(breakdown.clarifying_questions)}

### Starter snippet (draft — review before using)
${snippet.code
    ? `\`${snippet.filename || "untitled"}\`\n\`\`\`${snippet.language || ""}\n${snippet.code}\n\`\`\`\n${snippet.note || ""}`
    : `_${snippet.note || "No snippet generated."}_`}
`;
}

async function createIssue(ticket, breakdown) {
  return ghRest(`/repos/${OWNER}/${REPO}/issues`, {
    method: "POST",
    body: JSON.stringify({
      title: `[${ticket.key}] ${ticket.fields.summary}`,
      body: formatIssueBody(ticket, breakdown),
      labels: ["agent-analyzed"],
    }),
  });
}

// ---------- main ----------

async function main() {
  const tickets = await jiraSearch();
  console.log(`Found ${tickets.length} ticket(s) in Jira (read-only)`);

  for (const ticket of tickets) {
    if (await alreadyHasIssue(ticket.key)) {
      console.log(`Skipping ${ticket.key} — issue already exists`);
      continue;
    }

    console.log(`Analyzing ${ticket.key}...`);
    try {
      const summary = ticket.fields.summary;
      const description = adfToPlainText(ticket.fields.description);
      const keywords = keywordsFromTicket(summary, description);

      const [ownRepoFiles, referenceDocs] = await Promise.all([
        findRelevant(".", keywords),
        readAllDocs(REFERENCE_DOCS_PATH),
      ]);

      const breakdown = await callGemini(buildPrompt(ticket, summary, description, ownRepoFiles, referenceDocs));
      const issue = await createIssue(ticket, breakdown);
      console.log(`Opened issue #${issue.number} for ${ticket.key}`);
    } catch (err) {
      console.error(`Failed on ${ticket.key}:`, err.message);
    }
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
