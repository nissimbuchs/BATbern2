/**
 * i18n key gate (Epic 17 test fidelity).
 *
 *   node scripts/check-i18n-keys.ts            (npm run check:i18n-keys, make check-i18n-keys)
 *   node scripts/check-i18n-keys.ts --update   rewrite the baseline after a count fell
 *
 * Every `t('key')` in `src/**\/*.{ts,tsx}` (tests excluded) must name a key that exists in the
 * English bundles `public/locales/en/<namespace>.json`. With `fallbackLng: 'de'` a key missing in
 * `en` shows an English visitor the German text or the inline default, and no test sees it: the
 * tests run on the real `en` bundles, where a missing key renders as the key or the default. The
 * keys missing today are frozen in `scripts/i18n-keys-baseline.txt` (`<path> TAB <count>`); the
 * count per file may only fall:
 *   RAISED  a file has more missing keys than its baseline (a new file: more than 0)
 *   STALE   a file has fewer, or is gone: run `--update` and commit the baseline
 *
 * What it reads, per file, in order:
 *   - bindings `const { t } = useTranslation(ns)`, also `{ t: tX }`, with `ns` a string, an
 *     array of strings (i18next looks the key up in each, in order) or nothing (`common`, the
 *     default namespace), and an optional `{ keyPrefix: 'a.b' }`; a binding holds from its
 *     position to the next binding of the same name;
 *   - calls `t('key')`, `tX("key")`, `i18n.t('key')` / `i18next.t('key')` (namespace `common`);
 *     a `ns:key` prefix or a `{ ns: 'x' }` option picks the namespace;
 *   - a call whose function has no binding before it in the file (a `t` passed in as a
 *     parameter) counts as found when the key exists in any `en` namespace;
 *   - a key built at run time (a variable, a template with `${}`, a conditional) is skipped, not
 *     counted.
 * A key is found when its dotted path exists in the namespace, or a plural or context form
 * `<last segment>_<suffix>` does (`items_one`, `items_other`, `role_male`).
 *
 * Deterministic and dependency-free: plain TypeScript for Node >= 22.18 type stripping, no npm
 * packages, like `test-ratchet.ts`.
 */
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { maskComments } from './lib/mask-comments.ts';

export const DEFAULT_NS = 'common';
export const RULES_POINTER =
  'docs/architecture/coding-standards.md, section "Frontend: i18n keys" (add the key to public/locales/en and the other 9 locales)';
export const UPDATE_COMMAND = 'cd web-frontend && npm run check:i18n-keys -- --update';

type Json = string | number | boolean | null | Json[] | { [key: string]: Json };
export type Bundles = Map<string, Record<string, Json>>;

export interface Missing {
  file: string;
  line: number;
  key: string;
  /** The namespaces looked in; empty for an unbound `t` (looked in all). */
  namespaces: string[];
}

export interface Count {
  file: string;
  count: number;
}

// ---------------------------------------------------------------------------------------------
// Bundles and key lookup
// ---------------------------------------------------------------------------------------------

/** The `en` bundles under `localesDir` (`<ns>.json` each). */
export function loadBundles(localesDir: string): Bundles {
  const bundles: Bundles = new Map();
  for (const name of readdirSync(localesDir).sort()) {
    if (!name.endsWith('.json')) continue;
    bundles.set(name.slice(0, -5), JSON.parse(readFileSync(join(localesDir, name), 'utf8')));
  }
  return bundles;
}

const isObject = (v: Json | undefined): v is { [key: string]: Json } =>
  typeof v === 'object' && v !== null && !Array.isArray(v);

/** True when `key` (dotted path, i18next keySeparator `.`) resolves in `node`. */
export function hasKey(node: Json, key: string): boolean {
  if (!isObject(node)) return false;
  if (key in node) return true;
  // A plural or context form of the last segment: `items` -> `items_one`, `items_other`.
  if (!key.includes('.') && Object.keys(node).some((k) => k.startsWith(`${key}_`))) return true;
  // Try every split, so a JSON key that itself contains a dot is found too.
  for (let i = key.indexOf('.'); i > 0; i = key.indexOf('.', i + 1)) {
    const head = key.slice(0, i);
    if (head in node && hasKey(node[head], key.slice(i + 1))) return true;
  }
  return false;
}

// ---------------------------------------------------------------------------------------------
// Source scan
// ---------------------------------------------------------------------------------------------

interface Binding {
  name: string;
  at: number;
  namespaces: string[];
  keyPrefix?: string;
}

const STRING = String.raw`(?:'([^'\\\n]*)'|"([^"\\\n]*)")`;
const BINDING = new RegExp(
  String.raw`const\s*\{([^}]*)\}\s*=\s*useTranslation\(\s*([^)]*?)\s*\)`,
  'g'
);

/** Finds the matching `)` for the `(` at `open`; -1 when unbalanced. Strings are skipped. */
function closingParen(src: string, open: number): number {
  let depth = 0;
  for (let i = open; i < src.length; i++) {
    const c = src[i];
    if (c === "'" || c === '"' || c === '`') {
      const quote = c;
      for (i++; i < src.length && src[i] !== quote; i++) if (src[i] === '\\') i++;
      continue;
    }
    if (c === '(' || c === '{' || c === '[') depth++;
    else if (c === ')' || c === '}' || c === ']') {
      depth--;
      if (depth === 0) return i;
    }
  }
  return -1;
}

/** The namespaces a `useTranslation(...)` argument list names; null when not literal. */
function parseNamespaces(args: string): { namespaces: string[]; keyPrefix?: string } | null {
  const trimmed = args.trim();
  const prefix = /keyPrefix\s*:\s*(?:'([^']*)'|"([^"]*)")/.exec(trimmed);
  const keyPrefix = prefix ? (prefix[1] ?? prefix[2]) : undefined;
  if (trimmed === '' || trimmed.startsWith('undefined'))
    return { namespaces: [DEFAULT_NS], keyPrefix };
  const first = /^(?:'([^']*)'|"([^"]*)")/.exec(trimmed);
  if (first) return { namespaces: [first[1] ?? first[2]], keyPrefix };
  const array = /^\[([^\]]*)\]/.exec(trimmed);
  if (array) {
    const names = [...array[1].matchAll(/'([^']*)'|"([^"]*)"/g)].map((m) => m[1] ?? m[2]);
    if (names.length) return { namespaces: names, keyPrefix };
  }
  return null; // a variable: the namespace is not known here
}

function bindingsOf(src: string): Binding[] {
  const bindings: Binding[] = [];
  for (const m of src.matchAll(BINDING)) {
    const parsed = parseNamespaces(m[2]);
    for (const part of m[1].split(',')) {
      const [prop, alias] = part.split(':').map((s) => s.trim());
      if (prop !== 't') continue;
      bindings.push({
        name: alias || 't',
        at: m.index,
        namespaces: parsed ? parsed.namespaces : [],
        keyPrefix: parsed?.keyPrefix,
      });
    }
  }
  return bindings;
}

const lineOf = (src: string, index: number) => src.slice(0, index).split('\n').length;

/** The missing keys of one source file. */
export function scanSource(file: string, source: string, bundles: Bundles): Missing[] {
  const src = maskComments(source);
  const bindings = bindingsOf(src);
  const names = new Set(['t', ...bindings.map((b) => b.name)]);
  const missing: Missing[] = [];
  const allNamespaces = [...bundles.keys()];
  const callee = [...names].map((n) => n.replace(/[$]/g, '\\$')).join('|');
  const call = new RegExp(
    String.raw`(?<![\w$.])(?:(i18n|i18next)\.t|(${callee}))\(\s*${STRING}`,
    'g'
  );
  for (const m of src.matchAll(call)) {
    const open = m.index + m[0].indexOf('(');
    const close = closingParen(src, open);
    if (close < 0) continue;
    const afterKey = src.slice(m.index + m[0].length, close).trimStart();
    // `t('a' + x)` or `t('a', ...)`: only a whole literal is a key; the rest is dynamic.
    if (afterKey !== '' && !afterKey.startsWith(',')) continue;
    let key = m[3] ?? m[4];
    if (!key) continue;
    const nsOption = /\bns\s*:\s*(?:'([^']*)'|"([^"]*)")/.exec(afterKey);
    let namespaces: string[];
    if (m[1]) {
      namespaces = [DEFAULT_NS];
    } else {
      const binding = bindings.filter((b) => b.name === m[2] && b.at < m.index).pop();
      namespaces = binding ? binding.namespaces : [];
      if (binding?.keyPrefix) key = `${binding.keyPrefix}.${key}`;
    }
    const colon = key.indexOf(':');
    if (colon > 0 && bundles.has(key.slice(0, colon))) {
      namespaces = [key.slice(0, colon)];
      key = key.slice(colon + 1);
    } else if (nsOption) {
      namespaces = [nsOption[1] ?? nsOption[2]];
    }
    const lookIn = namespaces.length ? namespaces : allNamespaces;
    if (!lookIn.some((ns) => bundles.has(ns) && hasKey(bundles.get(ns) as Json, key))) {
      missing.push({ file, line: lineOf(src, m.index), key, namespaces });
    }
  }
  return missing;
}

// ---------------------------------------------------------------------------------------------
// Files, baseline, compare
// ---------------------------------------------------------------------------------------------

function walk(dir: string, out: string[]): void {
  if (!existsSync(dir)) return;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name !== '__tests__' && entry.name !== '__mocks__') walk(full, out);
    } else if (/\.tsx?$/.test(entry.name) && !/\.(test|spec)\.tsx?$/.test(entry.name)) {
      out.push(full);
    }
  }
}

/** Frontend-relative paths (forward slashes) of the sources the gate reads, sorted. */
export function listSources(frontendRoot: string): string[] {
  const root = resolve(frontendRoot);
  const found: string[] = [];
  walk(join(root, 'src'), found);
  const prefix = `${root}/`;
  return found
    .map((f) => f.slice(prefix.length).split('\\').join('/'))
    .filter((f) => !f.startsWith('src/test/') && !f.startsWith('src/types/generated/'))
    .sort();
}

export function scanFrontend(frontendRoot: string): { files: string[]; missing: Missing[] } {
  const bundles = loadBundles(join(frontendRoot, 'public', 'locales', 'en'));
  const files = listSources(frontendRoot);
  const missing = files.flatMap((file) =>
    scanSource(file, readFileSync(join(frontendRoot, file), 'utf8'), bundles)
  );
  return { files, missing };
}

export function countsOf(missing: Missing[]): Count[] {
  const byFile = new Map<string, number>();
  for (const m of missing) byFile.set(m.file, (byFile.get(m.file) ?? 0) + 1);
  return [...byFile]
    .map(([file, count]) => ({ file, count }))
    .sort((a, b) => (a.file < b.file ? -1 : 1));
}

const BASELINE_HEADER = [
  '# i18n key gate baseline (Epic 17). Generated, do not edit by hand:',
  `#   ${UPDATE_COMMAND}`,
  '# Per source file: t() calls whose key is in no public/locales/en bundle. Counts may only fall.',
  '# Format: <path> TAB <count>, sorted, zero counts omitted.',
];

export function formatBaseline(counts: Count[]): string {
  return [...BASELINE_HEADER, ...counts.map((c) => `${c.file}\t${c.count}`)].join('\n') + '\n';
}

export function parseBaseline(text: string): { counts: Count[]; errors: string[] } {
  const counts: Count[] = [];
  const errors: string[] = [];
  const seen = new Set<string>();
  text.split('\n').forEach((raw, idx) => {
    const line = raw.trim();
    if (!line || line.startsWith('#')) return;
    const parts = raw.split('\t');
    const count = Number(parts[1]);
    if (parts.length !== 2 || !parts[0] || !Number.isInteger(count) || count < 1) {
      errors.push(`BAD LINE  baseline line ${idx + 1}: ${line}  (want <path>\\t<count>)`);
      return;
    }
    if (seen.has(parts[0])) {
      errors.push(`BAD LINE  baseline line ${idx + 1}: duplicate ${parts[0]}`);
      return;
    }
    seen.add(parts[0]);
    counts.push({ file: parts[0], count });
  });
  return { counts, errors };
}

const describeMissing = (m: Missing) =>
  `          ${m.file}:${m.line}  '${m.key}' not in ${
    m.namespaces.length ? m.namespaces.map((ns) => `en/${ns}.json`).join(', ') : 'any en bundle'
  }`;

export interface CompareResult {
  exitCode: number;
  problems: string[];
  summary: string;
}

export function compare(
  missing: Missing[],
  baselineText: string,
  scannedFiles: string[]
): CompareResult {
  const baseline = parseBaseline(baselineText);
  const problems = [...baseline.errors];
  const actual = countsOf(missing);
  const before = new Map(baseline.counts.map((c) => [c.file, c.count]));
  const now = new Map(actual.map((c) => [c.file, c.count]));
  const present = new Set(scannedFiles);
  let raised = 0;
  let stale = 0;
  for (const c of actual) {
    const was = before.get(c.file) ?? 0;
    if (c.count > was) {
      raised++;
      problems.push(`RAISED  ${c.file}  baseline ${was}, actual ${c.count}; its missing keys:`);
      problems.push(...missing.filter((m) => m.file === c.file).map(describeMissing));
    }
  }
  for (const c of baseline.counts) {
    const after = now.get(c.file) ?? 0;
    if (after < c.count) {
      stale++;
      const why = present.has(c.file) ? '' : '  (file gone)';
      problems.push(`STALE   ${c.file}  baseline ${c.count}, actual ${after}${why}`);
    }
  }
  if (raised) {
    problems.push(`${raised} file(s) gained a key that is in no en bundle: ${RULES_POINTER}.`);
  }
  if (stale) {
    problems.push(
      `${stale} count(s) fell (good). Lock the gain in: ${UPDATE_COMMAND}, and commit web-frontend/scripts/i18n-keys-baseline.txt.`
    );
  }
  const total = actual.reduce((n, c) => n + c.count, 0);
  return {
    exitCode: problems.length ? 1 : 0,
    problems,
    summary: `i18n keys: ${total} t() call(s) in ${actual.length} file(s) name a key that is in no en bundle (frozen in the baseline).`,
  };
}

function main(): number {
  const scriptDir = dirname(fileURLToPath(import.meta.url));
  const frontendRoot = join(scriptDir, '..');
  const baselinePath = join(scriptDir, 'i18n-keys-baseline.txt');
  const { files, missing } = scanFrontend(frontendRoot);
  if (process.argv.includes('--update')) {
    const counts = countsOf(missing);
    writeFileSync(baselinePath, formatBaseline(counts));
    console.log(
      `Baseline written: web-frontend/scripts/i18n-keys-baseline.txt (${counts.length} lines).`
    );
    return 0;
  }
  const baselineText = existsSync(baselinePath) ? readFileSync(baselinePath, 'utf8') : '';
  const result = compare(missing, baselineText, files);
  for (const p of result.problems) console.log(p);
  console.log(result.summary);
  return result.exitCode;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = main();
}
