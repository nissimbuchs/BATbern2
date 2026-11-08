/**
 * Test fidelity ratchet (Story 17.0a).
 *
 *   node scripts/test-ratchet.ts            (npm run check:test-ratchet, make check-test-ratchet)
 *   node scripts/test-ratchet.ts --update   rewrite the baseline after a count fell
 *
 * Counts, per test file, what the Test fidelity rules (docs/architecture/coding-standards.md)
 * forbid in new tests, and compares the counts with the committed baseline
 * `scripts/test-ratchet-baseline.txt`. Any difference fails (exit 1):
 *   RAISED  a count is above its baseline (a new file with a counted mock is above 0)
 *   STALE   a count is below its baseline, or the file is gone: run `--update` and commit
 * so the baseline only ever falls, and always says exactly where the old tests are.
 *
 * Categories (baseline line `<category>\t<path>\t<count>`, sorted, zero counts omitted):
 *   fe-mock-services, fe-mock-hooks, fe-mock-components
 *       `vi.mock(` / `vi.doMock(` of own code in `web-frontend/src/**\/*.{test,spec}.ts(x)`, and
       `vi.spyOn(x, ...)` where `x` is imported from own code (same classification). The specifier is resolved
 *       (aliases from vite.config.ts, relative paths against the test file) to a path under
 *       `src/`: under `src/services` -> services; under `src/hooks` or basename `use[A-Z]...` ->
 *       hooks; under `src/components` / `src/pages` or a PascalCase basename -> components;
 *       anything else (packages, stores, utils, types) is not counted.
 *   be-mock-s2s-client
 *       `@MockitoBean` whose type ends in `Client` and is not a boundary type (UserApiClient, ...),
 *       and `mock(XClient.class)` / `Mockito.mock(XClient.class)` / a `@Mock XClient` field of such a type in test
 *       sources, so a shared `@Primary` mock config (EMS TestUserApiClientConfig) counts too
 *       (Story 17.0b1). Retire these onto the S2S contract harness (`ContractServer`).
 *   be-mock-in-process
 *       any other `@MockitoBean` that is not a boundary type.
 *   be-transactional-it
 *       class-level `@Transactional` on a test class that extends `AbstractIntegrationTest` or is
 *       named `*IntegrationTest` / `*IT`.
 * Boundary types (not counted): BOUNDARY_TYPES below, and any type imported from
 * `software.amazon.awssdk.*` (for `mock(...)` calls also from `java.*`). Comments and Javadoc
 * are never counted; in Java, string and text-block literals are skipped too (the frontend scan
 * masks comments only).
 *
 * Deterministic and dependency-free: plain TypeScript for Node >= 22.18 type stripping, no npm
 * packages, so CI can run it without `npm ci`.
 */
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, posix } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { maskComments } from './lib/mask-comments.ts';

export const CATEGORIES = [
  'be-mock-in-process',
  'be-mock-s2s-client',
  'be-transactional-it',
  'fe-mock-components',
  'fe-mock-hooks',
  'fe-mock-services',
] as const;
export type Category = (typeof CATEGORIES)[number];

/** Types that leave the process: mocking them is what the rule asks for. */
export const BOUNDARY_TYPES = new Set([
  'SesClient',
  'SesV2Client',
  'S3Client',
  'S3Presigner',
  'CognitoIdentityProviderClient',
  'SqsClient',
  'Clock',
  'JwtDecoder',
]);

export const RULES_POINTER = 'docs/architecture/coding-standards.md, section "Test fidelity rules"';
export const UPDATE_COMMAND = 'cd web-frontend && npm run check:test-ratchet -- --update';

const FRONTEND_SRC = 'web-frontend/src/';
/** vite.config.ts / tsconfig.json path aliases, repo-relative. */
const ALIASES: Array<[string, string]> = [
  ['@/', 'web-frontend/src/'],
  ['@components/', 'web-frontend/src/components/'],
  ['@hooks/', 'web-frontend/src/hooks/'],
  ['@services/', 'web-frontend/src/services/'],
  ['@stores/', 'web-frontend/src/stores/'],
  ['@types/', 'web-frontend/src/types/'],
  ['@utils/', 'web-frontend/src/utils/'],
  ['@pages/', 'web-frontend/src/pages/'],
];

export interface Count {
  category: Category;
  file: string;
  count: number;
}

// ---------------------------------------------------------------------------------------------
// Frontend
// ---------------------------------------------------------------------------------------------

/** Resolves a `vi.mock` specifier to a repo-relative path, or null for a package. */
export function resolveSpecifier(specifier: string, testFile: string): string | null {
  if (specifier.startsWith('./') || specifier.startsWith('../')) {
    return posix.normalize(posix.join(posix.dirname(testFile), specifier));
  }
  for (const [alias, target] of ALIASES) {
    const bare = alias.slice(0, -1);
    if (specifier === bare) return target.slice(0, -1);
    if (specifier.startsWith(alias)) return target + specifier.slice(alias.length);
  }
  return null;
}

/** The ratchet category of a `vi.mock` specifier in `testFile`, or null when not counted. */
export function classifySpecifier(specifier: string, testFile: string): Category | null {
  const resolved = resolveSpecifier(specifier, testFile);
  if (!resolved || !resolved.startsWith(FRONTEND_SRC)) return null;
  const segments = resolved.slice(FRONTEND_SRC.length).split('/').filter(Boolean);
  if (segments.length === 0) return null;
  const top = segments[0];
  const names = segments.map((s) => s.replace(/\.(tsx?|jsx?|mjs)$/, ''));
  const base =
    names[names.length - 1] === 'index' ? names[names.length - 2] : names[names.length - 1];
  if (top === 'services') return 'fe-mock-services';
  if (top === 'hooks' || /^use[A-Z]/.test(base ?? '')) return 'fe-mock-hooks';
  if (top === 'components' || top === 'pages' || /^[A-Z][A-Za-z0-9]*$/.test(base ?? '')) {
    return 'fe-mock-components';
  }
  return null;
}

const VI_MOCK =
  /\bvi\s*\.\s*(?:mock|doMock)\s*\(\s*(?:import\s*\(\s*)?(?:'([^'\n]*)'|"([^"\n]*)"|`([^`$\n]*)`)/g;

const IMPORT = /\bimport\s+(?!type\b)([^'";]*?)\s+from\s+(?:'([^'\n]*)'|"([^"\n]*)")/g;
const VI_SPY_ON = /\bvi\s*\.\s*spyOn\s*\(\s*([A-Za-z_$][\w$]*)\s*,/g;

/** Local binding name -> module specifier, for every value import of a test file. */
export function importedBindings(masked: string): Map<string, string> {
  const bindings = new Map<string, string>();
  for (const m of masked.matchAll(IMPORT)) {
    const clause = m[1];
    const specifier = m[2] ?? m[3] ?? '';
    const namespace = /\*\s*as\s+([A-Za-z_$][\w$]*)/.exec(clause);
    if (namespace) bindings.set(namespace[1], specifier);
    const named = /\{([^}]*)\}/.exec(clause);
    for (const part of named ? named[1].split(',') : []) {
      const name = part
        .trim()
        .replace(/^type\s+/, '')
        .split(/\s+as\s+/)
        .pop()
        ?.trim();
      if (name && !part.trim().startsWith('type ')) bindings.set(name, specifier);
    }
    const defaultName = /^\s*([A-Za-z_$][\w$]*)\s*(?:,|$)/.exec(clause.replace(/\{[^}]*\}/, ''));
    if (defaultName) bindings.set(defaultName[1], specifier);
  }
  return bindings;
}

/**
 * Counted own-code mocks of one frontend test file, by category: `vi.mock(` / `vi.doMock(` of a
 * module, and `vi.spyOn(<binding>, ...)` where the binding is imported from that module.
 */
export function scanFrontendTest(file: string, source: string): Map<Category, number> {
  const counts = new Map<Category, number>();
  const add = (category: Category | null) => {
    if (category) counts.set(category, (counts.get(category) ?? 0) + 1);
  };
  const masked = maskComments(source);
  for (const m of masked.matchAll(VI_MOCK))
    add(classifySpecifier(m[1] ?? m[2] ?? m[3] ?? '', file));
  const bindings = importedBindings(masked);
  for (const m of masked.matchAll(VI_SPY_ON)) {
    const specifier = bindings.get(m[1]);
    if (specifier) add(classifySpecifier(specifier, file));
  }
  return counts;
}

// ---------------------------------------------------------------------------------------------
// Backend
// ---------------------------------------------------------------------------------------------

/**
 * Blanks Java comments and the contents of string, text-block and char literals (quotes and
 * newlines kept), so annotations are only found in code.
 */
export function maskJava(src: string): string {
  const out = src.split('');
  const blank = (from: number, to: number) => {
    for (let k = from; k < to && k < out.length; k++) if (out[k] !== '\n') out[k] = ' ';
  };
  let i = 0;
  while (i < src.length) {
    const c = src[i];
    if (c === '/' && src[i + 1] === '/') {
      const end = src.indexOf('\n', i);
      const stop = end < 0 ? src.length : end;
      blank(i, stop);
      i = stop;
    } else if (c === '/' && src[i + 1] === '*') {
      const end = src.indexOf('*/', i + 2);
      const stop = end < 0 ? src.length : end + 2;
      blank(i, stop);
      i = stop;
    } else if (src.startsWith('"""', i)) {
      let j = i + 3;
      while (j < src.length && !src.startsWith('"""', j)) j += src[j] === '\\' ? 2 : 1;
      blank(i + 3, j);
      i = Math.min(j + 3, src.length);
    } else if (c === '"' || c === "'") {
      let j = i + 1;
      while (j < src.length && src[j] !== c && src[j] !== '\n') j += src[j] === '\\' ? 2 : 1;
      blank(i + 1, j);
      i = j + 1;
    } else {
      i++;
    }
  }
  return out.join('');
}

/** From an opening bracket at `i`, the index after its matching close (masked source). */
function skipBalanced(src: string, i: number, open: string, close: string): number {
  let depth = 0;
  for (; i < src.length; i++) {
    if (src[i] === open) depth++;
    else if (src[i] === close && --depth === 0) return i + 1;
  }
  return i;
}

function skipSpace(src: string, i: number): number {
  while (i < src.length && /\s/.test(src[i])) i++;
  return i;
}

const MODIFIERS = new Set([
  'private',
  'protected',
  'public',
  'static',
  'final',
  'transient',
  'volatile',
]);
const TYPE_KEYWORDS = new Set(['class', 'interface', 'record', 'enum']);

/** Simple type names of every `@MockitoBean` in a masked Java source (field or class level). */
export function mockitoBeanTypes(masked: string): string[] {
  return annotatedTypes(masked, /@(?:[\w.]+\.)?MockitoBean\b/g);
}

/** Simple type names of every Mockito `@Mock` field in a masked Java source. */
export function mockFieldTypes(masked: string): string[] {
  return annotatedTypes(masked, /@(?:[\w.]+\.)?Mock\b/g);
}

function annotatedTypes(masked: string, re: RegExp): string[] {
  const types: string[] = [];
  for (const m of masked.matchAll(re)) {
    let i = skipSpace(masked, m.index + m[0].length);
    let args = '';
    if (masked[i] === '(') {
      const end = skipBalanced(masked, i, '(', ')');
      args = masked.slice(i, end);
      i = end;
    }
    // Skip further annotations and modifiers up to the declaration.
    for (;;) {
      i = skipSpace(masked, i);
      if (masked[i] === '@') {
        const a = /^@[\w.]+/.exec(masked.slice(i));
        i += a ? a[0].length : 1;
        i = skipSpace(masked, i);
        if (masked[i] === '(') i = skipBalanced(masked, i, '(', ')');
        continue;
      }
      const word = /^\w+/.exec(masked.slice(i))?.[0];
      if (word && (MODIFIERS.has(word) || word === 'abstract')) {
        i += word.length;
        continue;
      }
      break;
    }
    const typeMatch = /^([\w.]+)/.exec(masked.slice(i));
    if (!typeMatch) continue;
    if (TYPE_KEYWORDS.has(typeMatch[1])) {
      // Class level: @MockitoBean(types = { A.class, B.class })
      for (const t of args.matchAll(/([\w.]+)\s*\.\s*class\b/g)) types.push(simpleName(t[1]));
      continue;
    }
    types.push(simpleName(typeMatch[1]));
  }
  return types;
}

function simpleName(qualified: string): string {
  const parts = qualified.split('.');
  return parts[parts.length - 1];
}

/** Simple name -> fully qualified name, from the import statements. */
function importsOf(masked: string): Map<string, string> {
  const imports = new Map<string, string>();
  for (const m of masked.matchAll(/^\s*import\s+([\w.]+)\s*;/gm)) {
    imports.set(simpleName(m[1]), m[1]);
  }
  return imports;
}

/** True when the top-level class carries `@Transactional` and is an integration test. */
export function isTransactionalIt(masked: string): boolean {
  let depth = 0;
  let decl: RegExpExecArray | null = null;
  const re = /[{}]|\b(class|interface|record|enum)\s+(\w+)/g;
  for (let m = re.exec(masked); m; m = re.exec(masked)) {
    if (m[0] === '{') depth++;
    else if (m[0] === '}') depth--;
    else if (depth === 0) {
      decl = m;
      break;
    }
  }
  if (!decl || decl[1] !== 'class') return false;
  const header = masked.slice(decl.index, masked.indexOf('{', decl.index));
  const name = decl[2];
  const isIt =
    /(IntegrationTest|IT)$/.test(name) ||
    /\bextends\s+(?:[\w.]+\.)?AbstractIntegrationTest\b/.test(header);
  if (!isIt) return false;
  const annotations = masked.slice(masked.lastIndexOf(';', decl.index) + 1, decl.index);
  return /@(?:[\w.]+\.)?Transactional\b/.test(annotations);
}

const MOCK_CALL = /\b(?:Mockito\s*\.\s*)?mock\s*\(\s*([\w.]+)\s*\.\s*class\b/g;

/** Simple type names of every `mock(X.class)` / `Mockito.mock(X.class)` in a masked Java source. */
export function mockCallTypes(masked: string): string[] {
  return [...masked.matchAll(MOCK_CALL)].map((m) => simpleName(m[1]));
}

/** Counts of one Java test file, by category. */
export function scanJavaTest(source: string): Map<Category, number> {
  const counts = new Map<Category, number>();
  const add = (c: Category) => counts.set(c, (counts.get(c) ?? 0) + 1);
  const masked = maskJava(source);
  const imports = importsOf(masked);
  for (const type of mockitoBeanTypes(masked)) {
    const fqn = imports.get(type) ?? '';
    if (BOUNDARY_TYPES.has(type) || fqn.startsWith('software.amazon.awssdk.')) continue;
    add(type.endsWith('Client') ? 'be-mock-s2s-client' : 'be-mock-in-process');
  }
  for (const type of [...mockCallTypes(masked), ...mockFieldTypes(masked)]) {
    const fqn = imports.get(type) ?? '';
    // JDK types (java.net.http.HttpClient) leave the process: boundary, like the AWS SDK.
    if (
      BOUNDARY_TYPES.has(type) ||
      fqn.startsWith('software.amazon.awssdk.') ||
      fqn.startsWith('java.')
    ) {
      continue;
    }
    if (type.endsWith('Client')) add('be-mock-s2s-client');
  }
  if (isTransactionalIt(masked)) add('be-transactional-it');
  return counts;
}

// ---------------------------------------------------------------------------------------------
// Scan, baseline, compare
// ---------------------------------------------------------------------------------------------

export function scanSources(sources: Array<{ file: string; source: string }>): Count[] {
  const counts: Count[] = [];
  for (const { file, source } of sources) {
    const perFile = file.endsWith('.java') ? scanJavaTest(source) : scanFrontendTest(file, source);
    for (const [category, count] of perFile) {
      if (count > 0) counts.push({ category, file, count });
    }
  }
  return sortCounts(counts);
}

function sortCounts(counts: Count[]): Count[] {
  const key = (c: Count) => `${c.category}\t${c.file}`;
  return [...counts].sort((a, b) => (key(a) < key(b) ? -1 : key(a) > key(b) ? 1 : 0));
}

const BASELINE_HEADER = [
  '# Test fidelity ratchet baseline (Story 17.0a). Generated, do not edit by hand:',
  `#   ${UPDATE_COMMAND}`,
  `# Rules: ${RULES_POINTER}.`,
  '# Per test file: own-code mocks and class-level @Transactional ITs. Counts may only fall.',
  '# Format: <category> TAB <path> TAB <count>, sorted, zero counts omitted.',
];

export function formatBaseline(counts: Count[]): string {
  const lines = sortCounts(counts).map((c) => `${c.category}\t${c.file}\t${c.count}`);
  return [...BASELINE_HEADER, ...lines].join('\n') + '\n';
}

export function parseBaseline(text: string): { counts: Count[]; errors: string[] } {
  const counts: Count[] = [];
  const errors: string[] = [];
  const seen = new Set<string>();
  text.split('\n').forEach((raw, idx) => {
    const line = raw.trim();
    if (!line || line.startsWith('#')) return;
    const parts = raw.split('\t');
    const count = Number(parts[2]);
    if (
      parts.length !== 3 ||
      !(CATEGORIES as readonly string[]).includes(parts[0]) ||
      !parts[1] ||
      !Number.isInteger(count) ||
      count < 1
    ) {
      errors.push(
        `BAD LINE     baseline line ${idx + 1}: ${line}  (want <category>\\t<path>\\t<count>)`
      );
      return;
    }
    const key = `${parts[0]}\t${parts[1]}`;
    if (seen.has(key)) {
      errors.push(`BAD LINE     baseline line ${idx + 1}: duplicate ${parts[0]} ${parts[1]}`);
      return;
    }
    seen.add(key);
    counts.push({ category: parts[0] as Category, file: parts[1], count });
  });
  return { counts, errors };
}

export function summarize(counts: Count[]): string {
  const parts = CATEGORIES.map((category) => {
    const rows = counts.filter((c) => c.category === category);
    const total = rows.reduce((n, c) => n + c.count, 0);
    return `${category} ${rows.length} files/${total}`;
  });
  return `Test ratchet: ${parts.join(', ')}.`;
}

export interface CompareResult {
  exitCode: number;
  problems: string[];
  summary: string;
}

/**
 * Compares scanned counts with the baseline. `scannedFiles` are all files the scan read, so a
 * baseline entry for a file that is not among them is reported as gone.
 */
export function compare(
  actual: Count[],
  baselineText: string,
  scannedFiles: string[]
): CompareResult {
  const baseline = parseBaseline(baselineText);
  const problems = [...baseline.errors];
  const raised: string[] = [];
  const stale: string[] = [];
  const key = (c: { category: string; file: string }) => `${c.category}\t${c.file}`;
  const base = new Map(baseline.counts.map((c) => [key(c), c.count]));
  const now = new Map(actual.map((c) => [key(c), c.count]));
  const present = new Set(scannedFiles);
  for (const c of actual) {
    const before = base.get(key(c)) ?? 0;
    if (c.count > before) {
      raised.push(`RAISED       ${c.category}  ${c.file}  baseline ${before}, actual ${c.count}`);
    }
  }
  for (const c of baseline.counts) {
    const after = now.get(key(c)) ?? 0;
    if (after < c.count) {
      const why = present.has(c.file) ? '' : '  (file gone)';
      stale.push(
        `STALE        ${c.category}  ${c.file}  baseline ${c.count}, actual ${after}${why}`
      );
    }
  }
  problems.push(...raised, ...stale);
  if (raised.length) {
    problems.push(
      `${raised.length} count(s) rose. New tests mock at the process boundary only: ${RULES_POINTER}.`
    );
  }
  if (stale.length) {
    problems.push(
      `${stale.length} count(s) fell (good). Lock the gain in: ${UPDATE_COMMAND}, and commit web-frontend/scripts/test-ratchet-baseline.txt.`
    );
  }
  return { exitCode: problems.length ? 1 : 0, problems, summary: summarize(actual) };
}

// ---------------------------------------------------------------------------------------------
// Files
// ---------------------------------------------------------------------------------------------

function walk(dir: string, keep: (name: string) => boolean, out: string[]): void {
  if (!existsSync(dir)) return;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) walk(full, keep, out);
    else if (entry.isFile() && keep(entry.name)) out.push(full);
  }
}

/** Repo-relative paths (forward slashes) of every test file the ratchet reads, sorted. */
export function listTestFiles(repoRoot: string): string[] {
  const found: string[] = [];
  walk(join(repoRoot, 'web-frontend', 'src'), (n) => /\.(test|spec)\.tsx?$/.test(n), found);
  const modules = ['api-gateway', 'shared-kernel'];
  const servicesDir = join(repoRoot, 'services');
  if (existsSync(servicesDir)) {
    for (const e of readdirSync(servicesDir, { withFileTypes: true })) {
      if (e.isDirectory()) modules.push(`services/${e.name}`);
    }
  }
  for (const mod of modules) {
    const src = join(repoRoot, mod, 'src');
    if (!existsSync(src)) continue;
    for (const e of readdirSync(src, { withFileTypes: true })) {
      if (e.isDirectory() && e.name.startsWith('test')) {
        walk(join(src, e.name), (n) => n.endsWith('.java'), found);
      }
    }
  }
  const prefix = repoRoot.endsWith('/') ? repoRoot : `${repoRoot}/`;
  return found.map((f) => f.slice(prefix.length).split('\\').join('/')).sort();
}

export function scanRepo(repoRoot: string): { files: string[]; counts: Count[] } {
  const files = listTestFiles(repoRoot);
  const counts = scanSources(
    files.map((file) => ({ file, source: readFileSync(join(repoRoot, file), 'utf8') }))
  );
  return { files, counts };
}

function main(): number {
  const scriptDir = dirname(fileURLToPath(import.meta.url));
  const repoRoot = join(scriptDir, '..', '..');
  const baselinePath = join(scriptDir, 'test-ratchet-baseline.txt');
  const { files, counts } = scanRepo(repoRoot);
  if (process.argv.includes('--update')) {
    writeFileSync(baselinePath, formatBaseline(counts));
    console.log(summarize(counts));
    console.log(
      `Baseline written: web-frontend/scripts/test-ratchet-baseline.txt (${counts.length} lines).`
    );
    return 0;
  }
  const baselineText = existsSync(baselinePath) ? readFileSync(baselinePath, 'utf8') : '';
  const result = compare(counts, baselineText, files);
  for (const p of result.problems) console.log(p);
  console.log(result.summary);
  return result.exitCode;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = main();
}
