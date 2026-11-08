/**
 * Spec-diff similarity report (Story 17.4).
 *
 *   node scripts/api-similarity.ts --base <git ref>    (npm run report:api-similarity -- --base origin/develop)
 *
 * Lists the operations the working tree's public specs (`docs/api/*.openapi.yml`) add over the
 * specs at `<git ref>`, and for each one the 3 most similar operations that already exist there,
 * as Markdown on stdout. CI posts it as one PR comment (job `api-similarity`); it is informative
 * and never fails a build: duplicates become visible before merge, the reviewer decides.
 *
 * An operation is identified by HTTP method + path with every parameter name blanked
 * (`GET /events/{}/speakers`), so renaming `{code}` to `{eventCode}` adds nothing.
 * Similarity (0..1) = 0.55 * path + 0.4 * summary + 0.05 * same HTTP method:
 *   path     0.5 * shared leading segments / longer path + 0.5 * Jaccard of the path words
 *            (literal segments split at `-`, `_`, `.`, `:`; parameter names count as words)
 *   summary  Jaccard of the summary words (lower case, 3+ letters, minus stop words), or of the
 *            operationId split at camelCase when there is no summary
 * Ties are broken by method + path.
 *
 * Plain TypeScript for Node >= 22.18 type stripping. Reuses `loadSpecDocuments` of the shared
 * spec matcher (Story 17.3).
 */
import { execFileSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { loadSpecDocuments, type SpecDocument } from '../src/test/contract/specMatcher.ts';

export const MARKER = '<!-- api-similarity-report -->';
export const NO_ADDED = 'API similarity: no added operations.';

const HTTP_METHODS = ['get', 'put', 'post', 'patch', 'delete', 'head', 'options'];
const STOP_WORDS = new Set([
  'the',
  'and',
  'for',
  'from',
  'with',
  'all',
  'its',
  'via',
  'into',
  'this',
  'that',
  'per',
  'one',
  'are',
  'not',
]);

export interface Operation {
  file: string;
  method: string;
  path: string;
  /** Method + path with parameter names blanked. */
  key: string;
  summary: string;
}

export interface Neighbour {
  op: Operation;
  score: number;
}

const isParam = (segment: string) => /^\{[^}]*\}$/.test(segment);

export function operationKey(method: string, path: string): string {
  return `${method.toUpperCase()} ${path.replace(/\{[^}]*\}/g, '{}')}`;
}

export function listOperations(documents: SpecDocument[]): Operation[] {
  const ops: Operation[] = [];
  for (const { file, doc } of documents) {
    for (const [path, item] of Object.entries(doc?.paths ?? {})) {
      if (!item || typeof item !== 'object') continue;
      for (const method of HTTP_METHODS) {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const op = (item as any)[method];
        if (!op) continue;
        ops.push({
          file,
          method: method.toUpperCase(),
          path,
          key: operationKey(method, path),
          summary: String(op.summary ?? splitCamel(op.operationId ?? '')),
        });
      }
    }
  }
  return ops;
}

/** Operations of `head` whose key `base` does not have, sorted by method + path. */
export function addedOperations(base: Operation[], head: Operation[]): Operation[] {
  const known = new Set(base.map((o) => o.key));
  const added = new Map<string, Operation>();
  for (const o of head) if (!known.has(o.key) && !added.has(o.key)) added.set(o.key, o);
  return [...added.values()].sort(byKey);
}

function byKey(a: Operation, b: Operation): number {
  return a.key < b.key ? -1 : a.key > b.key ? 1 : 0;
}

function splitCamel(s: string): string {
  return s.replace(/([a-z0-9])([A-Z])/g, '$1 $2');
}

function segments(path: string): string[] {
  return path.split('/').filter((s) => s.length > 0);
}

export function pathWords(path: string): Set<string> {
  const words = new Set<string>();
  for (const seg of segments(path)) {
    const raw = isParam(seg) ? seg.slice(1, -1) : seg;
    for (const w of raw.split(/[-_.:]/)) if (w) words.add(w.toLowerCase());
  }
  return words;
}

export function summaryWords(summary: string): Set<string> {
  return new Set(
    summary
      .toLowerCase()
      .split(/[^a-z0-9äöü]+/)
      .filter((w) => w.length >= 3 && !STOP_WORDS.has(w))
  );
}

function jaccard(a: Set<string>, b: Set<string>): number {
  if (a.size === 0 && b.size === 0) return 0;
  let common = 0;
  for (const x of a) if (b.has(x)) common++;
  return common / (a.size + b.size - common);
}

function sharedPrefix(a: string[], b: string[]): number {
  let n = 0;
  while (n < a.length && n < b.length) {
    const same = (isParam(a[n]) && isParam(b[n])) || a[n] === b[n];
    if (!same) break;
    n++;
  }
  return n;
}

export function similarity(a: Operation, b: Operation): number {
  const sa = segments(a.path);
  const sb = segments(b.path);
  const longer = Math.max(sa.length, sb.length) || 1;
  const path =
    0.5 * (sharedPrefix(sa, sb) / longer) + 0.5 * jaccard(pathWords(a.path), pathWords(b.path));
  const summary = jaccard(summaryWords(a.summary), summaryWords(b.summary));
  const method = a.method === b.method ? 1 : 0;
  return Math.round((0.55 * path + 0.4 * summary + 0.05 * method) * 1000) / 1000;
}

export function nearest(op: Operation, existing: Operation[], count = 3): Neighbour[] {
  return existing
    .filter((e) => e.key !== op.key)
    .map((e) => ({ op: e, score: similarity(op, e) }))
    .sort((x, y) => y.score - x.score || byKey(x.op, y.op))
    .slice(0, count);
}

const cell = (s: string) => s.replace(/\|/g, '\\|').replace(/\s+/g, ' ').trim();

export function renderMarkdown(added: Operation[], existing: Operation[]): string {
  const lines = [MARKER, '### API similarity report (Story 17.4)', ''];
  if (added.length === 0) {
    lines.push(NO_ADDED);
    return lines.join('\n') + '\n';
  }
  lines.push(
    `This PR adds ${added.length} operation(s). Each is shown with the 3 most similar existing ` +
      'operations. Informative only: check that the new one is not a near-duplicate, and that ' +
      'it follows the API conventions (docs/architecture/coding-standards.md).',
    ''
  );
  for (const op of added) {
    lines.push(`#### \`${op.method} ${op.path}\` (${op.file})`, '');
    if (op.summary) lines.push(`> ${cell(op.summary)}`, '');
    lines.push('| Score | Existing operation | Spec | Summary |', '|---|---|---|---|');
    for (const n of nearest(op, existing)) {
      lines.push(
        `| ${n.score.toFixed(2)} | \`${n.op.method} ${n.op.path}\` | ${n.op.file} | ${cell(n.op.summary)} |`
      );
    }
    lines.push('');
  }
  return lines.join('\n');
}

// ---------------------------------------------------------------------------------------------
// Git
// ---------------------------------------------------------------------------------------------

/** The public specs under `docs/api/` at `ref`, parsed (via a temporary copy). */
export function loadSpecDocumentsAt(repoRoot: string, ref: string): SpecDocument[] {
  const git = (...args: string[]) =>
    execFileSync('git', args, { cwd: repoRoot, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  const names = git('ls-tree', '--name-only', `${ref}:docs/api`)
    .split('\n')
    .filter((f) => /\.openapi\.ya?ml$/.test(f));
  const dir = mkdtempSync(join(tmpdir(), 'api-similarity-'));
  try {
    for (const name of names)
      writeFileSync(join(dir, name), git('show', `${ref}:docs/api/${name}`));
    return loadSpecDocuments(dir);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

function main(): number {
  const i = process.argv.indexOf('--base');
  const ref = i > 0 ? process.argv[i + 1] : undefined;
  if (!ref) {
    console.error('usage: node scripts/api-similarity.ts --base <git ref>');
    return 2;
  }
  const scriptDir = dirname(fileURLToPath(import.meta.url));
  const repoRoot = join(scriptDir, '..', '..');
  const base = listOperations(loadSpecDocumentsAt(repoRoot, ref));
  const head = listOperations(loadSpecDocuments(join(repoRoot, 'docs', 'api')));
  process.stdout.write(renderMarkdown(addedOperations(base, head), base));
  return 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = main();
}
