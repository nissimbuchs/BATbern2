/**
 * Guard: no service-to-service client calls another service's public API (ADR-016 decision 2,
 * Epic 17 entry 6, epic "Services call in one direction over /internal", R1).
 *
 *   node scripts/check-s2s-client-urls.ts            (npm run check:s2s-client-urls, CI test-ratchet)
 *   node scripts/check-s2s-client-urls.ts --update   rewrite the baseline after a call site moved
 *   ... --update --allow-growth                       also baseline new call sites (owner decision only)
 *
 * Services call each other on `/internal/**` (VPC-only, denied by the gateway), never on the
 * public `/api/v1` API. The scan reads every Java file of the backend services whose path has a
 * `client` directory (`services/<service>/src/main/java/.../client/...`) and reports each string
 * literal (text blocks included) that contains `/api/v1`. Comments and Javadoc do not count. A
 * source scan, not ArchUnit: ArchUnit cannot see string literals.
 *
 * Findings are compared with the committed baseline `scripts/s2s-client-urls-baseline.txt`, keyed
 * by file + URL literal (never by line), one line per occurrence: a file with the same literal twice
 * has two identical lines, and the counts are compared. Any difference fails (exit 1):
 *   RAISED   a call site the baseline does not list (or one more occurrence than it lists): call
 *            the provider's `/internal` operation (docs/api/internal/<provider>-internal.openapi.yml)
 *   STALE    a baselined call site that is gone, or fewer occurrences (good): `--update`, commit
 * so the baseline only ever shrinks. `--update` refuses (exit 1, baseline untouched) to write a
 * call site the committed baseline does not list, unless `--allow-growth` is given, which is for
 * an owner-accepted exception only.
 *
 * Every baseline line must match exactly one group of the hand-maintained owner file
 * `scripts/s2s-client-urls-owners.txt` (the story that moves or deletes the call, or an
 * owner-accepted exception with its reason). Checked in both modes, also right after `--update`:
 *   UNOWNED      a baseline line no group matches
 *   AMBIGUOUS    a baseline line two or more groups match
 *   STALE OWNER  a group that matches no baseline line any more: remove it
 *   BAD OWNER    an owner-file line that does not parse
 *
 * Policy: docs/architecture/module-map.md, section "Gates"; ADR-016.
 * Plain TypeScript for Node >= 22.18 type stripping (`.ts` imports, no enums).
 */
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const POLICY_POINTER = 'ADR-016 decision 2, docs/architecture/module-map.md section "Gates"';
export const UPDATE_COMMAND = 'cd web-frontend && npm run check:s2s-client-urls -- --update';
export const ALLOW_GROWTH_FLAG = '--allow-growth';
export const OWNERS_FILE = 'web-frontend/scripts/s2s-client-urls-owners.txt';
export const BASELINE_FILE = 'web-frontend/scripts/s2s-client-urls-baseline.txt';
export const PUBLIC_API = '/api/v1';

export interface CallSite {
  /** Repo-relative, `/` separators. */
  file: string;
  /** The string literal's content (one line; a text block's lines joined with a space). */
  url: string;
  line: number;
}

export type BaselineEntry = Pick<CallSite, 'file' | 'url'>;

export const siteKey = (c: BaselineEntry) => `${c.file}\t${c.url}`;

const sortByKey = <T extends BaselineEntry>(items: T[]): T[] =>
  [...items].sort((a, b) => (siteKey(a) < siteKey(b) ? -1 : siteKey(a) > siteKey(b) ? 1 : 0));

// ---------------------------------------------------------------------------------------------
// Scan
// ---------------------------------------------------------------------------------------------

interface Literal {
  text: string;
  /** Index of the opening quote. */
  at: number;
}

/** The string and text-block literals of a Java source, outside comments. */
export function javaStringLiterals(src: string): Literal[] {
  const literals: Literal[] = [];
  let i = 0;
  while (i < src.length) {
    const c = src[i];
    if (c === '/' && src[i + 1] === '/') {
      const end = src.indexOf('\n', i);
      i = end < 0 ? src.length : end;
    } else if (c === '/' && src[i + 1] === '*') {
      const end = src.indexOf('*/', i + 2);
      i = end < 0 ? src.length : end + 2;
    } else if (src.startsWith('"""', i)) {
      let j = i + 3;
      while (j < src.length && !src.startsWith('"""', j)) j += src[j] === '\\' ? 2 : 1;
      literals.push({ text: src.slice(i + 3, j), at: i });
      i = Math.min(j + 3, src.length);
    } else if (c === '"') {
      let j = i + 1;
      while (j < src.length && src[j] !== '"' && src[j] !== '\n') j += src[j] === '\\' ? 2 : 1;
      literals.push({ text: src.slice(i + 1, j), at: i });
      i = j + 1;
    } else if (c === "'") {
      // char literal: skip, it cannot hold a URL
      let j = i + 1;
      while (j < src.length && src[j] !== "'" && src[j] !== '\n') j += src[j] === '\\' ? 2 : 1;
      i = j + 1;
    } else {
      i++;
    }
  }
  return literals;
}

/** The call sites of one Java source: every literal that contains `/api/v1`. */
export function scanSource(file: string, source: string): CallSite[] {
  return javaStringLiterals(source)
    .filter((l) => l.text.includes(PUBLIC_API))
    .map((l) => ({
      file,
      url: l.text
        .split('\n')
        .map((p) => p.trim())
        .filter(Boolean)
        .join(' '),
      line: source.slice(0, l.at).split('\n').length,
    }));
}

function walk(dir: string, out: string[]): void {
  if (!existsSync(dir)) return;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name.startsWith('.')) continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else if (entry.isFile() && entry.name.endsWith('.java')) out.push(full);
  }
}

/** Repo-relative paths of the client sources: `services/<svc>/src/main/java/**` with a `client` dir. */
export function listClientFiles(repoRoot: string): string[] {
  const servicesDir = join(repoRoot, 'services');
  const found: string[] = [];
  if (!existsSync(servicesDir)) return found;
  for (const e of readdirSync(servicesDir, { withFileTypes: true })) {
    if (e.isDirectory()) walk(join(servicesDir, e.name, 'src', 'main', 'java'), found);
  }
  const prefix = repoRoot.endsWith('/') ? repoRoot : `${repoRoot}/`;
  return found
    .map((f) => f.slice(prefix.length).split('\\').join('/'))
    .filter((f) => f.split('/').slice(0, -1).includes('client'))
    .sort();
}

/** Scans every client source under `repoRoot`. */
export function scanTree(repoRoot: string): { sites: CallSite[]; files: number } {
  const files = listClientFiles(repoRoot);
  const sites = files.flatMap((file) =>
    scanSource(file, readFileSync(join(repoRoot, file), 'utf8'))
  );
  return { sites: sortByKey(sites), files: files.length };
}

// ---------------------------------------------------------------------------------------------
// Baseline
// ---------------------------------------------------------------------------------------------

const BASELINE_HEADER = [
  '# Service-to-service client call sites of a public /api/v1 URL. Generated, do not edit by hand:',
  `#   ${UPDATE_COMMAND}`,
  `# Policy: ${POLICY_POINTER}.`,
  `# Existing call sites only. Every line has exactly one owner group in ${OWNERS_FILE}:`,
  '# the story that moves or deletes the call, or an accepted exception with its reason.',
  '# Format: <file> TAB <URL literal>, sorted, one line per occurrence.',
];

export function formatBaseline(entries: BaselineEntry[]): string {
  return [...BASELINE_HEADER, ...sortByKey(entries).map(siteKey)].join('\n') + '\n';
}

export function parseBaseline(text: string): { entries: BaselineEntry[]; errors: string[] } {
  const entries: BaselineEntry[] = [];
  const errors: string[] = [];
  text.split('\n').forEach((raw, idx) => {
    const line = raw.trim();
    if (!line || line.startsWith('#')) return;
    const parts = raw.split('\t');
    if (parts.length !== 2 || !/^\S+$/.test(parts[0]) || !parts[1].includes(PUBLIC_API)) {
      errors.push(`BAD LINE     baseline line ${idx + 1}: ${line}  (want <file>\\t<URL literal>)`);
      return;
    }
    entries.push({ file: parts[0], url: parts[1] });
  });
  return { entries, errors };
}

export function summarize(sites: CallSite[], files: number): string {
  const withSites = new Set(sites.map((s) => s.file)).size;
  return `S2S client call sites of ${PUBLIC_API}: ${sites.length} in ${withSites} of ${files} client file(s).`;
}

export interface CompareResult {
  exitCode: number;
  problems: string[];
}

const raisedLine = (c: CallSite) =>
  `RAISED       ${c.file}:${c.line}  "${c.url}"  a client calls another service's public API`;

/** Occurrences per file + URL literal. */
function countByKey(items: BaselineEntry[]): Map<string, number> {
  const counts = new Map<string, number>();
  for (const i of items) counts.set(siteKey(i), (counts.get(siteKey(i)) ?? 0) + 1);
  return counts;
}

/** The sites beyond what `baseline` allows for their key (the later occurrences of a key first). */
export function sitesAbove(sites: CallSite[], baseline: BaselineEntry[]): CallSite[] {
  const allowed = countByKey(baseline);
  const added: CallSite[] = [];
  for (const c of sites) {
    const left = allowed.get(siteKey(c)) ?? 0;
    if (left > 0) allowed.set(siteKey(c), left - 1);
    else added.push(c);
  }
  return added;
}

export function compare(sites: CallSite[], baselineText: string): CompareResult {
  const baseline = parseBaseline(baselineText);
  const problems = [...baseline.errors];
  const added = sitesAbove(sites, baseline.entries);
  const now = countByKey(sites);
  const gone: BaselineEntry[] = [];
  for (const e of baseline.entries) {
    const left = now.get(siteKey(e)) ?? 0;
    if (left > 0) now.set(siteKey(e), left - 1);
    else gone.push(e);
  }
  for (const c of added) problems.push(raisedLine(c));
  for (const e of gone)
    problems.push(`STALE        ${e.file}  "${e.url}"  occurrence no longer found`);
  if (added.length) {
    problems.push(
      `${added.length} new call site(s) of ${PUBLIC_API} in a client. Call the provider's /internal operation (docs/api/internal/) instead (${POLICY_POINTER}).`
    );
  }
  if (gone.length) {
    problems.push(
      `${gone.length} baselined call site(s) removed (good). Lock the gain in: ${UPDATE_COMMAND}, and commit ${BASELINE_FILE}.`
    );
  }
  return { exitCode: problems.length ? 1 : 0, problems };
}

// ---------------------------------------------------------------------------------------------
// Owners
// ---------------------------------------------------------------------------------------------

/** A story or entry id (`6b`, `8`, `17.10`, `16.8c`), an issue (`#1070`), or `exception: <reason>`. */
const OWNER = /^(\d+(?:\.\d+)?[a-z]?|#\d+|exception: \S.*)$/;

export interface OwnerGroup {
  id: string;
  owner: string;
  /** Searched in the file path (anchor with `^` / `$` where needed). */
  file: RegExp;
  /** Searched in the URL literal. */
  url: RegExp;
}

/** Parses the owner file: `<group id> TAB <owner> TAB <file regex> TAB <URL regex>`. */
export function parseOwners(text: string): { groups: OwnerGroup[]; errors: string[] } {
  const groups: OwnerGroup[] = [];
  const errors: string[] = [];
  const ids = new Set<string>();
  text.split('\n').forEach((raw, idx) => {
    const line = raw.trim();
    if (!line || line.startsWith('#')) return;
    const bad = (why: string) =>
      errors.push(`BAD OWNER    owner file line ${idx + 1}: ${why}: ${line}`);
    const parts = raw.split('\t');
    if (parts.length !== 4 || parts.some((p) => !p.trim())) {
      bad('want <group id>\\t<owner>\\t<file regex>\\t<URL regex>');
      return;
    }
    const [id, owner, fileSource, urlSource] = parts.map((p) => p.trim());
    if (ids.has(id)) {
      bad(`duplicate group id ${id}`);
      return;
    }
    if (!OWNER.test(owner)) {
      bad('owner must be a story id (6b, 17.10), an issue (#1070) or "exception: <reason>"');
      return;
    }
    try {
      groups.push({ id, owner, file: new RegExp(fileSource), url: new RegExp(urlSource) });
      ids.add(id);
    } catch (e) {
      bad(`invalid regex (${(e as Error).message})`);
    }
  });
  return { groups, errors };
}

/** Every entry must match exactly one group, and every group must match an entry. */
export function checkOwners(entries: BaselineEntry[], ownersText: string): string[] {
  const { groups, errors } = parseOwners(ownersText);
  const problems = [...errors];
  const used = new Set<string>();
  let unowned = 0;
  for (const e of entries) {
    const hits = groups.filter((g) => g.file.test(e.file) && g.url.test(e.url));
    hits.forEach((g) => used.add(g.id));
    if (hits.length === 0) {
      unowned++;
      problems.push(`UNOWNED      ${e.file}  "${e.url}"  matches no owner group`);
    } else if (hits.length > 1) {
      problems.push(
        `AMBIGUOUS    ${e.file}  "${e.url}"  matches groups ${hits.map((g) => g.id).join(', ')}`
      );
    }
  }
  for (const g of groups) {
    if (!used.has(g.id)) {
      problems.push(
        `STALE OWNER  group ${g.id} (${g.owner}) matches no baseline line: remove it from ${OWNERS_FILE}`
      );
    }
  }
  if (unowned) {
    problems.push(
      `${unowned} baseline line(s) without an owner. Add a group to ${OWNERS_FILE}: the story that moves the call, or "exception: <reason>".`
    );
  }
  return problems;
}

// ---------------------------------------------------------------------------------------------
// Run
// ---------------------------------------------------------------------------------------------

export interface RunPaths {
  /** The repository root: holds `services/`. */
  repoRoot: string;
  baselinePath: string;
  ownersPath: string;
}

export function defaultPaths(scriptDir: string): RunPaths {
  return {
    repoRoot: join(scriptDir, '..', '..'),
    baselinePath: join(scriptDir, 's2s-client-urls-baseline.txt'),
    ownersPath: join(scriptDir, 's2s-client-urls-owners.txt'),
  };
}

/** The whole check: compare (or `--update`) plus the owner check; returns the exit code. */
export function run(argv: string[], paths: RunPaths): number {
  const { sites, files } = scanTree(paths.repoRoot);
  if (files === 0) {
    console.log(`No client sources found under ${paths.repoRoot}/services/*/src/main/java.`);
    return 1;
  }
  const readText = (path: string) => (existsSync(path) ? readFileSync(path, 'utf8') : '');
  const ownersText = readText(paths.ownersPath);
  if (argv.includes('--update')) {
    const added = sitesAbove(sites, parseBaseline(readText(paths.baselinePath)).entries);
    if (added.length && !argv.includes(ALLOW_GROWTH_FLAG)) {
      for (const c of added) console.log(raisedLine(c));
      console.log(
        `Baseline not written: --update only removes lines, and ${added.length} call site(s) above are not in ${BASELINE_FILE}. Call the provider's /internal operation instead (${POLICY_POINTER}). Only an owner-accepted exception is added with --update ${ALLOW_GROWTH_FLAG}, plus its owner group.`
      );
      return 1;
    }
    writeFileSync(paths.baselinePath, formatBaseline(sites));
    console.log(summarize(sites, files));
    console.log(`Baseline written: ${BASELINE_FILE} (${sites.length} lines).`);
    const ownerProblems = checkOwners(sites, ownersText);
    for (const p of ownerProblems) console.log(p);
    return ownerProblems.length ? 1 : 0;
  }
  const baselineText = readText(paths.baselinePath);
  const result = compare(sites, baselineText);
  const ownerProblems = checkOwners(parseBaseline(baselineText).entries, ownersText);
  for (const p of [...result.problems, ...ownerProblems]) console.log(p);
  console.log(
    `${summarize(sites, files)} Baseline: ${parseBaseline(baselineText).entries.length}.`
  );
  return result.exitCode || ownerProblems.length ? 1 : 0;
}

function main(): number {
  return run(process.argv.slice(2), defaultPaths(dirname(fileURLToPath(import.meta.url))));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = main();
}
