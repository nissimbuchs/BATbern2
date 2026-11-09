/**
 * Gate against hand-written copies of generated API types (Story 17.3c, ADR-006).
 *
 *   node scripts/check-api-type-copies.ts            (npm run check:api-type-copies, CI frontend-static)
 *   node scripts/check-api-type-copies.ts --update   rewrite the baseline after a copy was removed
 *   ... --update --allow-growth                       also baseline new copies (owner decision only)
 *
 * The wire types come from `src/types/generated/*.types.ts` (generated from `docs/api/`). A
 * hand-written `export interface X { ... }` or object-literal `export type X = { ... }` (also
 * generic) in `src/` whose name equals a generated schema is a copy: it drifts silently from the
 * spec. Use the generated type instead, e.g.
 *   export type X = components['schemas']['X'];
 * An alias, union, intersection or mapped type (anything after `=` that does not start with `{`)
 * passes; so do other names, non-exported declarations and commented-out code. The scan covers
 * the files of `scripts/lib/source-files.ts` (no tests, mocks, fixtures or generated types).
 *
 * Findings are compared with the committed baseline `scripts/api-type-copies-baseline.txt`,
 * keyed by file + type name (never by line). Any difference fails (exit 1):
 *   NEW      a copy the baseline does not list: alias the generated type, or rename the type if it
 *            is a frontend model and not the wire shape
 *   STALE    a baselined copy that is gone or now an alias (good): run `--update` and commit
 * so the baseline only ever shrinks. `--update` keeps it that way: it refuses (exit 1, baseline
 * untouched) to write a copy the committed baseline does not list, unless `--allow-growth` is
 * given, which is for an owner-accepted exception only.
 *
 * Every baseline line must also match exactly one group of the hand-maintained owner file
 * `scripts/api-type-copies-owners.txt` (the story or issue that removes it, or an
 * owner-accepted exception with its reason). Checked in both modes, also right after `--update`:
 *   UNOWNED      a baseline line no group matches: add or widen a group in the same PR
 *   AMBIGUOUS    a baseline line two or more groups match: narrow the groups
 *   STALE OWNER  a group that matches no baseline line any more (fixed away): remove it
 *   BAD OWNER    an owner-file line that does not parse
 *
 * Policy: docs/architecture/coding-standards.md, section "Frontend: generated API type copies".
 * Plain TypeScript for Node >= 22.18 type stripping (`.ts` imports, no enums).
 */
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { maskComments } from './lib/mask-comments.ts';
import { listSourceFiles } from './lib/source-files.ts';

export const POLICY_POINTER =
  'docs/architecture/coding-standards.md, section "Frontend: generated API type copies"';
export const UPDATE_COMMAND = 'cd web-frontend && npm run check:api-type-copies -- --update';
export const ALLOW_GROWTH_FLAG = '--allow-growth';
export const OWNERS_FILE = 'web-frontend/scripts/api-type-copies-owners.txt';
export const BASELINE_FILE = 'web-frontend/scripts/api-type-copies-baseline.txt';
export const GENERATED_DIR = 'src/types/generated';

export type CopyKind = 'interface' | 'type';

export interface Copy {
  /** Relative to the frontend root, `/` separators. */
  file: string;
  name: string;
  line: number;
  kind: CopyKind;
}

export type BaselineEntry = Pick<Copy, 'file' | 'name'>;

export const copyKey = (c: BaselineEntry) => `${c.file}\t${c.name}`;

const sortByKey = <T extends BaselineEntry>(items: T[]): T[] =>
  [...items].sort((a, b) => (copyKey(a) < copyKey(b) ? -1 : copyKey(a) > copyKey(b) ? 1 : 0));

// ---------------------------------------------------------------------------------------------
// Schema names
// ---------------------------------------------------------------------------------------------

/**
 * The members of `components.schemas` in one openapi-typescript output file. Line based on the
 * generator's fixed layout: the `schemas: {` block of `export interface components`, member names
 * at the next indent level (quoted names included).
 */
export function extractSchemaNames(generatedSource: string): string[] {
  const lines = maskComments(generatedSource).split('\n');
  const start = lines.findIndex((l) => /^export interface components \{/.test(l));
  if (start < 0) return [];
  const schemas = lines.findIndex((l, i) => i > start && /^(\s+)schemas: \{\s*$/.test(l));
  if (schemas < 0) return [];
  const outer = (/^(\s+)/.exec(lines[schemas]) ?? ['', ''])[1];
  const close = new RegExp(`^${outer}\\}`);
  const member = new RegExp(`^${outer}${outer}(?:"([^"]+)"|'([^']+)'|([A-Za-z_$][\\w$]*))\\??:`);
  const names: string[] = [];
  for (let i = schemas + 1; i < lines.length && !close.test(lines[i]); i++) {
    const m = member.exec(lines[i]);
    if (m) names.push(m[1] ?? m[2] ?? m[3]);
  }
  return names;
}

/** All schema names of the generated dir under `root`, sorted, unique. */
export function loadSchemaNames(root: string): Set<string> {
  const dir = join(root, GENERATED_DIR);
  const names = new Set<string>();
  if (!existsSync(dir)) return names;
  for (const f of readdirSync(dir).filter((n) => n.endsWith('.types.ts'))) {
    for (const n of extractSchemaNames(readFileSync(join(dir, f), 'utf8'))) names.add(n);
  }
  return names;
}

// ---------------------------------------------------------------------------------------------
// Scan
// ---------------------------------------------------------------------------------------------

const DECLARATION =
  /(^|[\n;{}])[ \t]*export[ \t]+(?:declare[ \t]+)?(interface|type)[ \t]+([A-Za-z_$][\w$]*)/g;

/** Skips a balanced `<...>` starting at `i`; returns the index after it. */
function skipGenerics(src: string, i: number): number {
  let depth = 0;
  for (; i < src.length; i++) {
    if (src[i] === '<') depth++;
    else if (src[i] === '>' && src[i - 1] !== '=') {
      depth--;
      if (depth === 0) return i + 1;
    }
  }
  return i;
}

/** The exported interfaces and object-literal types of one file whose name is a schema name. */
export function scanSource(file: string, source: string, schemaNames: Set<string>): Copy[] {
  const masked = maskComments(source, { strings: true });
  const copies: Copy[] = [];
  for (const m of masked.matchAll(DECLARATION)) {
    const kind = m[2] as CopyKind;
    const name = m[3];
    if (!schemaNames.has(name)) continue;
    let i = (m.index ?? 0) + m[0].length;
    if (kind === 'type') {
      while (/\s/.test(masked[i] ?? '')) i++;
      if (masked[i] === '<') i = skipGenerics(masked, i);
      while (/\s/.test(masked[i] ?? '')) i++;
      if (masked[i] !== '=') continue;
      i++;
      while (/\s/.test(masked[i] ?? '')) i++;
      if (masked[i] !== '{') continue;
    }
    const at = (m.index ?? 0) + m[1].length;
    const line = masked.slice(0, at).split('\n').length;
    copies.push({ file, name, line, kind });
  }
  return copies;
}

/** Scans every source file under `root`. */
export function scanTree(root: string): { copies: Copy[]; schemaCount: number } {
  const schemaNames = loadSchemaNames(root);
  const copies = listSourceFiles(root).flatMap((file) =>
    scanSource(file, readFileSync(join(root, file), 'utf8'), schemaNames)
  );
  return { copies: sortByKey(copies), schemaCount: schemaNames.size };
}

// ---------------------------------------------------------------------------------------------
// Baseline
// ---------------------------------------------------------------------------------------------

const BASELINE_HEADER = [
  '# Hand-written copies of generated API types (frontend). Generated, do not edit by hand:',
  `#   ${UPDATE_COMMAND}`,
  `# Policy: ${POLICY_POINTER}.`,
  `# Existing copies only. Every line has exactly one owner group in ${OWNERS_FILE}:`,
  '# the story or issue that removes it, or an accepted exception with its reason.',
  '# Format: <file> TAB <type name>, sorted.',
];

export function formatBaseline(entries: BaselineEntry[]): string {
  return [...BASELINE_HEADER, ...sortByKey(entries).map(copyKey)].join('\n') + '\n';
}

export function parseBaseline(text: string): { entries: BaselineEntry[]; errors: string[] } {
  const entries: BaselineEntry[] = [];
  const errors: string[] = [];
  const seen = new Set<string>();
  text.split('\n').forEach((raw, idx) => {
    const line = raw.trim();
    if (!line || line.startsWith('#')) return;
    const parts = raw.split('\t');
    if (parts.length !== 2 || !/^\S+$/.test(parts[0]) || !/^[A-Za-z_$][\w$]*$/.test(parts[1])) {
      errors.push(`BAD LINE     baseline line ${idx + 1}: ${line}  (want <file>\\t<type name>)`);
      return;
    }
    const entry = { file: parts[0], name: parts[1] };
    if (seen.has(copyKey(entry))) {
      errors.push(`BAD LINE     baseline line ${idx + 1}: duplicate ${line}`);
      return;
    }
    seen.add(copyKey(entry));
    entries.push(entry);
  });
  return { entries, errors };
}

export function summarize(copies: Copy[]): string {
  const interfaces = copies.filter((c) => c.kind === 'interface').length;
  const files = new Set(copies.map((c) => c.file)).size;
  return `Generated API type copies: ${copies.length} (${interfaces} interface(s), ${copies.length - interfaces} object type(s)) in ${files} file(s).`;
}

export interface CompareResult {
  exitCode: number;
  problems: string[];
}

export function compare(copies: Copy[], baselineText: string): CompareResult {
  const baseline = parseBaseline(baselineText);
  const problems = [...baseline.errors];
  const base = new Set(baseline.entries.map(copyKey));
  const now = new Set(copies.map(copyKey));
  const added = copies.filter((c) => !base.has(copyKey(c)));
  const gone = baseline.entries.filter((e) => !now.has(copyKey(e)));
  for (const c of added) {
    problems.push(
      `NEW          ${c.file}:${c.line}  ${c.name}  hand-written ${c.kind} named like a generated schema`
    );
  }
  for (const e of gone) {
    problems.push(`STALE        ${e.file}  ${e.name}  no longer a copy`);
  }
  if (added.length) {
    problems.push(
      `${added.length} new copy(ies) of a generated API type. Use the generated type (export type X = components['schemas']['X']), or rename a frontend model that is not the wire shape (${POLICY_POINTER}).`
    );
  }
  if (gone.length) {
    problems.push(
      `${gone.length} baselined copy(ies) removed (good). Lock the gain in: ${UPDATE_COMMAND}, and commit ${BASELINE_FILE}.`
    );
  }
  return { exitCode: problems.length ? 1 : 0, problems };
}

// ---------------------------------------------------------------------------------------------
// Owners
// ---------------------------------------------------------------------------------------------

/** A story id (`17.10`, `16.8c`), an issue (`#1070`), or `exception: <reason>`. */
const OWNER = /^(\d+\.\d+[a-z]?|#\d+|exception: \S.*)$/;

export interface OwnerGroup {
  id: string;
  owner: string;
  /** Searched in the file path (anchor with `^` / `$` where needed). */
  file: RegExp;
  /** Matched against the whole type name. */
  name: RegExp;
}

/** Parses the owner file: `<group id> TAB <owner> TAB <file regex> TAB <name regex>`. */
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
      bad('want <group id>\\t<owner>\\t<file regex>\\t<name regex>');
      return;
    }
    const [id, owner, fileSource, nameSource] = parts.map((p) => p.trim());
    if (ids.has(id)) {
      bad(`duplicate group id ${id}`);
      return;
    }
    if (!OWNER.test(owner)) {
      bad('owner must be a story id (17.10), an issue (#1070) or "exception: <reason>"');
      return;
    }
    try {
      groups.push({
        id,
        owner,
        file: new RegExp(fileSource),
        name: new RegExp(`^(?:${nameSource})$`),
      });
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
    const hits = groups.filter((g) => g.file.test(e.file) && g.name.test(e.name));
    hits.forEach((g) => used.add(g.id));
    if (hits.length === 0) {
      unowned++;
      problems.push(`UNOWNED      ${e.file}  ${e.name}  matches no owner group`);
    } else if (hits.length > 1) {
      problems.push(
        `AMBIGUOUS    ${e.file}  ${e.name}  matches groups ${hits.map((g) => g.id).join(', ')}`
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
      `${unowned} baseline line(s) without an owner. Add a group to ${OWNERS_FILE}: the story or issue that removes the line, or "exception: <reason>".`
    );
  }
  return problems;
}

// ---------------------------------------------------------------------------------------------
// Run
// ---------------------------------------------------------------------------------------------

export interface RunPaths {
  /** The frontend root: holds `src/` and `src/types/generated/`. */
  root: string;
  baselinePath: string;
  ownersPath: string;
}

export function defaultPaths(scriptDir: string): RunPaths {
  return {
    root: join(scriptDir, '..'),
    baselinePath: join(scriptDir, 'api-type-copies-baseline.txt'),
    ownersPath: join(scriptDir, 'api-type-copies-owners.txt'),
  };
}

/** The whole check: compare (or `--update`) plus the owner check; returns the exit code. */
export function run(argv: string[], paths: RunPaths): number {
  const { copies, schemaCount } = scanTree(paths.root);
  if (schemaCount === 0) {
    console.log(
      `No schema names found in ${GENERATED_DIR}/*.types.ts: run npm run generate:api-types.`
    );
    return 1;
  }
  const readText = (path: string) => (existsSync(path) ? readFileSync(path, 'utf8') : '');
  const ownersText = readText(paths.ownersPath);
  if (argv.includes('--update')) {
    const committed = new Set(parseBaseline(readText(paths.baselinePath)).entries.map(copyKey));
    const added = copies.filter((c) => !committed.has(copyKey(c)));
    if (added.length && !argv.includes(ALLOW_GROWTH_FLAG)) {
      for (const c of added) {
        console.log(
          `NEW          ${c.file}:${c.line}  ${c.name}  hand-written ${c.kind} named like a generated schema`
        );
      }
      console.log(
        `Baseline not written: --update only removes lines, and ${added.length} copy(ies) above are not in ${BASELINE_FILE}. Use the generated type or rename a frontend model (${POLICY_POINTER}). Only an owner-accepted exception is added with --update ${ALLOW_GROWTH_FLAG}, plus its owner group.`
      );
      return 1;
    }
    writeFileSync(paths.baselinePath, formatBaseline(copies));
    console.log(summarize(copies));
    console.log(`Baseline written: ${BASELINE_FILE} (${copies.length} lines).`);
    const ownerProblems = checkOwners(copies, ownersText);
    for (const p of ownerProblems) console.log(p);
    return ownerProblems.length ? 1 : 0;
  }
  const baselineText = readText(paths.baselinePath);
  const result = compare(copies, baselineText);
  const ownerProblems = checkOwners(parseBaseline(baselineText).entries, ownersText);
  for (const p of [...result.problems, ...ownerProblems]) console.log(p);
  console.log(`${summarize(copies)} Baseline: ${parseBaseline(baselineText).entries.length}.`);
  return result.exitCode || ownerProblems.length ? 1 : 0;
}

function main(): number {
  return run(process.argv.slice(2), defaultPaths(dirname(fileURLToPath(import.meta.url))));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = main();
}
