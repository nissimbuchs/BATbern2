/**
 * Frontend API path contract check (Story 17.3).
 *
 *   node scripts/check-api-paths.ts            (npm run check:api-paths)
 *
 * Scans `src/` (no tests, mocks or generated types) for HTTP calls through the API client,
 * `axios` or any `*Client` receiver, resolves each call's method and path, and matches it
 * against `docs/api/` with the shared spec matcher. Fails (exit 1) on:
 *   NOT IN SPEC   a resolved call no spec operation defines
 *   UNRESOLVED    a call whose path cannot be resolved and is not allow-listed
 *   STALE         an allow-list or layer-baseline entry that no longer matches anything
 *   OUT OF LAYER  a file outside `src/services/` with an HTTP call that is not in the baseline
 *
 * Resolution is deliberately simple (regex, same file only): string and template literals,
 * same-file constants (`const X = '...'`, class fields via `this.X`), same-file path helpers
 * (`const f = (a) => \`...\``, `function f(a) { return \`...\`; }`, methods via `this.f()`),
 * `+` concatenation and `?:` (both branches are checked). Each other `${...}` becomes `{*}`, a
 * value the matcher does not check. Query parameters built in a params object are left to the
 * runtime validator (17.0c). Anything else is UNRESOLVED: allow-list it with a reason rather
 * than grow this parser (fallback, if that list grows: the TypeScript compiler API).
 *
 * Plain TypeScript for Node 22 type stripping: `.ts` import extensions, no enums/namespaces.
 */
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, relative, sep } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import {
  WILDCARD,
  loadSpecIndex,
  matchOperation,
  type SpecIndex,
} from '../src/test/contract/specMatcher.ts';
import { maskComments, skipQuoted } from './lib/mask-comments.ts';

export { maskComments };

export interface ScannedCall {
  file: string;
  line: number;
  receiver: string;
  method: string;
  /** The first argument as written, whitespace collapsed. */
  expression: string;
  /** Resolved URLs (more than one for a `?:`), or null when unresolved. */
  urls: string[] | null;
  /** Set when the call is out of scope for the spec check (absolute or presigned URL). */
  ignored?: string;
}

// ---------------------------------------------------------------------------------------------
// Lexing helpers
// ---------------------------------------------------------------------------------------------

/** Skips a template literal starting at the backtick at `i`; returns the index after it. */
function skipTemplate(src: string, i: number): number {
  i++;
  while (i < src.length) {
    const c = src[i];
    if (c === '\\') {
      i += 2;
    } else if (c === '`') {
      return i + 1;
    } else if (c === '$' && src[i + 1] === '{') {
      i = skipBalanced(src, i + 1, '{', '}');
    } else {
      i++;
    }
  }
  return i;
}

/** From an opening bracket at `i`, returns the index after its matching close. */
function skipBalanced(src: string, i: number, open: string, close: string): number {
  let depth = 0;
  while (i < src.length) {
    const c = src[i];
    if (c === '"' || c === "'") {
      i = skipQuoted(src, i);
      continue;
    }
    if (c === '`') {
      i = skipTemplate(src, i);
      continue;
    }
    if (c === open) depth++;
    else if (c === close) {
      depth--;
      if (depth === 0) return i + 1;
    }
    i++;
  }
  return i;
}

/** Reads an expression from `i` up to a depth-0 `,` `)` `;` or `}`. Returns [text, end]. */
export function readExpression(src: string, i: number): [string, number] {
  const start = i;
  let depth = 0;
  while (i < src.length) {
    const c = src[i];
    if (c === '"' || c === "'") {
      i = skipQuoted(src, i);
      continue;
    }
    if (c === '`') {
      i = skipTemplate(src, i);
      continue;
    }
    if (c === '(' || c === '[' || c === '{') depth++;
    else if (c === ')' || c === ']' || c === '}') {
      if (depth === 0) break;
      depth--;
    } else if ((c === ',' || c === ';') && depth === 0) break;
    i++;
  }
  return [src.slice(start, i).trim(), i];
}

/** Splits `expr` at depth-0 occurrences of `sep` (a single character). */
function splitTopLevel(expr: string, separator: string): string[] {
  const parts: string[] = [];
  let depth = 0;
  let last = 0;
  let i = 0;
  while (i < expr.length) {
    const c = expr[i];
    if (c === '"' || c === "'") {
      i = skipQuoted(expr, i);
      continue;
    }
    if (c === '`') {
      i = skipTemplate(expr, i);
      continue;
    }
    if (c === '(' || c === '[' || c === '{') depth++;
    else if (c === ')' || c === ']' || c === '}') depth--;
    else if (c === separator && depth === 0) {
      parts.push(expr.slice(last, i).trim());
      last = i + 1;
    }
    i++;
  }
  parts.push(expr.slice(last).trim());
  return parts;
}

/** Splits `a ? b : c` at depth 0; null when `expr` is not a conditional. */
function splitConditional(expr: string): [string, string] | null {
  let depth = 0;
  let q = -1;
  let nested = 0;
  let i = 0;
  while (i < expr.length) {
    const c = expr[i];
    if (c === '"' || c === "'") {
      i = skipQuoted(expr, i);
      continue;
    }
    if (c === '`') {
      i = skipTemplate(expr, i);
      continue;
    }
    if (c === '(' || c === '[' || c === '{') depth++;
    else if (c === ')' || c === ']' || c === '}') depth--;
    else if (depth === 0 && c === '?' && expr[i + 1] !== '.' && expr[i + 1] !== '?') {
      if (q < 0) q = i;
      else nested++;
    } else if (depth === 0 && c === ':' && q >= 0) {
      if (nested > 0) nested--;
      else return [expr.slice(q + 1, i).trim(), expr.slice(i + 1).trim()];
    }
    i++;
  }
  return null;
}

/** Parses a template literal's source (with backticks) into text and expression parts. */
function templateParts(tpl: string): Array<{ text: string } | { expr: string }> {
  const parts: Array<{ text: string } | { expr: string }> = [];
  let i = 1;
  let text = '';
  while (i < tpl.length - 1) {
    const c = tpl[i];
    if (c === '\\') {
      text += tpl[i + 1];
      i += 2;
    } else if (c === '$' && tpl[i + 1] === '{') {
      const end = skipBalanced(tpl, i + 1, '{', '}');
      if (text) parts.push({ text });
      text = '';
      parts.push({ expr: tpl.slice(i + 2, end - 1).trim() });
      i = end;
    } else {
      text += c;
      i++;
    }
  }
  if (text) parts.push({ text });
  return parts;
}

// ---------------------------------------------------------------------------------------------
// Same-file context: constants and path helpers
// ---------------------------------------------------------------------------------------------

interface Helper {
  params: string[];
  locals: string[];
  body: string; // the returned expression
}

interface FileContext {
  /** Constant name → initializer expressions (one per declaration; >1 means ambiguous). */
  constants: Map<string, string[]>;
  helpers: Map<string, Helper[]>;
}

const IDENT = '[A-Za-z_$][\\w$]*';

function paramNames(params: string): string[] {
  return splitTopLevel(params, ',')
    .map((p) => p.replace(/^\.\.\./, '').match(new RegExp(`^${IDENT}`))?.[0])
    .filter((p): p is string => Boolean(p));
}

function localNames(body: string): string[] {
  return [...body.matchAll(new RegExp(`\\b(?:const|let|var)\\s+(${IDENT})`, 'g'))].map((m) => m[1]);
}

export function buildFileContext(masked: string): FileContext {
  const constants = new Map<string, string[]>();
  const helpers = new Map<string, Helper[]>();
  const add = <T>(map: Map<string, T[]>, key: string, value: T) => {
    map.set(key, [...(map.get(key) ?? []), value]);
  };

  // Arrow helpers: const f = (a, b): string => <expr>
  const arrow = new RegExp(
    `\\b(?:const|let)\\s+(${IDENT})\\s*(?::[^=]+)?=\\s*(?:async\\s*)?\\(([^)]*)\\)\\s*(?::\\s*[\\w<>\\[\\] |]+)?\\s*=>\\s*`,
    'g'
  );
  const arrowNames = new Set<string>();
  for (const m of masked.matchAll(arrow)) {
    const start = (m.index ?? 0) + m[0].length;
    if (masked[start] === '{') continue; // block body: not a simple helper
    const [body] = readExpression(masked, start);
    add(helpers, m[1], { params: paramNames(m[2]), locals: [], body });
    arrowNames.add(m[1]);
  }

  // Function and method helpers with a single `return <expr>`.
  const fn = new RegExp(
    `(?:\\bfunction\\s+|^\\s*(?:(?:private|public|protected|static|async|readonly)\\s+)*)(${IDENT})\\s*\\(([^()]*)\\)\\s*(?::\\s*[\\w<>\\[\\] |]+)?\\s*\\{`,
    'gm'
  );
  for (const m of masked.matchAll(fn)) {
    const name = m[1];
    if (['if', 'for', 'while', 'switch', 'catch', 'function', 'return'].includes(name)) continue;
    const open = (m.index ?? 0) + m[0].length - 1;
    const end = skipBalanced(masked, open, '{', '}');
    const body = masked.slice(open + 1, end - 1);
    const returns = [...body.matchAll(/\breturn\b/g)];
    if (returns.length !== 1) continue;
    const [expr] = readExpression(body, (returns[0].index ?? 0) + 'return'.length);
    if (!/^[`'"]/.test(expr)) continue;
    add(helpers, name, { params: paramNames(m[2]), locals: localNames(body), body: expr });
  }

  // Constants: const X = <expr>, and class fields `readonly X = <expr>` (read as this.X).
  const decl = new RegExp(
    `(?:\\b(?:const|let|var)\\s+|^\\s*(?:(?:private|public|protected|static|readonly)\\s+)+)(${IDENT})\\s*(?::\\s*[\\w<>\\[\\] |]+)?\\s*=(?![=>])\\s*`,
    'gm'
  );
  for (const m of masked.matchAll(decl)) {
    if (arrowNames.has(m[1])) continue;
    const [expr] = readExpression(masked, (m.index ?? 0) + m[0].length);
    add(constants, m[1], expr.replace(/\s+as\s+const$/, ''));
  }
  return { constants, helpers };
}

// ---------------------------------------------------------------------------------------------
// Resolution
// ---------------------------------------------------------------------------------------------

const MAX_VARIANTS = 8;

interface Scope {
  ctx: FileContext;
  /** Names that are parameters or locals of the helper being expanded: always `{*}`. */
  shadowed: Set<string>;
  depth: number;
}

/** Resolves an expression to URL variants, or null when it cannot. */
export function resolveExpression(expr: string, scope: Scope): string[] | null {
  if (scope.depth > 8) return null;
  const e = stripParens(expr.trim().replace(/\s+as\s+[\w<>[\]|' ]+$/, ''));
  if (/^'[^']*'$|^"[^"]*"$/.test(e)) return [e.slice(1, -1)];
  if (e.startsWith('`') && skipTemplate(e, 0) === e.length) return resolveTemplate(e, scope);

  const cond = splitConditional(e);
  if (cond) {
    const a = resolveExpression(cond[0], { ...scope, depth: scope.depth + 1 });
    const b = resolveExpression(cond[1], { ...scope, depth: scope.depth + 1 });
    return a && b ? dedupe([...a, ...b]) : null;
  }

  const plus = splitTopLevel(e, '+');
  if (plus.length > 1) {
    let acc: string[] = [''];
    for (let k = 0; k < plus.length; k++) {
      const part = resolveExpression(plus[k], { ...scope, depth: scope.depth + 1 });
      if (!part) {
        if (k === 0) return null; // an unknown base is not a value
        acc = acc.map((a) => a + WILDCARD);
        continue;
      }
      acc = cartesian(acc, part);
      if (!acc.length) return null;
    }
    return acc;
  }

  const ident = e.match(new RegExp(`^(?:this\\.)?(${IDENT})$`));
  if (ident) {
    if (scope.shadowed.has(ident[1])) return null;
    const inits = scope.ctx.constants.get(ident[1]);
    if (!inits || inits.length !== 1) return null;
    return resolveExpression(inits[0], { ...scope, depth: scope.depth + 1 });
  }

  const call = e.match(new RegExp(`^(?:this\\.)?(${IDENT})\\s*\\(`));
  if (call && skipBalanced(e, e.indexOf('('), '(', ')') === e.length) {
    const helpers = scope.ctx.helpers.get(call[1]);
    if (!helpers || helpers.length !== 1 || scope.shadowed.has(call[1])) return null;
    const h = helpers[0];
    return resolveExpression(h.body, {
      ctx: scope.ctx,
      shadowed: new Set([...h.params, ...h.locals]),
      depth: scope.depth + 1,
    });
  }
  return null;
}

function stripParens(e: string): string {
  while (e.startsWith('(') && skipBalanced(e, 0, '(', ')') === e.length) e = e.slice(1, -1).trim();
  return e;
}

function dedupe(values: string[]): string[] {
  return [...new Set(values)].slice(0, MAX_VARIANTS);
}

function cartesian(a: string[], b: string[]): string[] {
  const out: string[] = [];
  for (const x of a) for (const y of b) out.push(x + y);
  return dedupe(out);
}

function resolveTemplate(tpl: string, scope: Scope): string[] | null {
  let acc: string[] = [''];
  const parts = templateParts(tpl);
  for (let k = 0; k < parts.length; k++) {
    const part = parts[k];
    if ('text' in part) {
      acc = acc.map((a) => a + part.text);
      continue;
    }
    const resolved = resolveExpression(part.expr, { ...scope, depth: scope.depth + 1 });
    if (resolved) {
      acc = cartesian(acc, resolved);
    } else if (k === 0) {
      return null; // a template that starts with an unknown expression has an unknown base
    } else {
      acc = acc.map((a) => a + WILDCARD);
    }
  }
  return acc;
}

// ---------------------------------------------------------------------------------------------
// Scanning
// ---------------------------------------------------------------------------------------------

const CALL =
  /(?<![\w$])(apiClient|axios|[A-Za-z_$][\w$]*Client)\s*\.\s*(get|post|put|patch|delete)\s*(?=[<(])/g;

const ABSOLUTE = /^(?:https?:)?\/\//i;
const PRESIGNED = /upload|presign/i;

function hasPartialWildcardSegment(url: string): boolean {
  const path = url.split('?')[0];
  return path.split('/').some((seg) => seg.includes(WILDCARD) && seg !== WILDCARD);
}

export function scanSource(file: string, source: string): ScannedCall[] {
  const masked = maskComments(source);
  const ctx = buildFileContext(masked);
  const calls: ScannedCall[] = [];
  for (const m of masked.matchAll(CALL)) {
    let i = (m.index ?? 0) + m[0].length;
    if (masked[i] === '<') i = skipBalanced(masked, i, '<', '>');
    while (/\s/.test(masked[i] ?? '')) i++;
    if (masked[i] !== '(') continue;
    i++;
    while (/\s/.test(masked[i] ?? '')) i++;
    const [rawExpr] = readExpression(masked, i);
    if (!rawExpr) continue;
    const expression = rawExpr.replace(/\s+/g, ' ');
    const line = masked.slice(0, m.index).split('\n').length;
    const receiver = m[1];
    const method = m[2].toUpperCase();
    const resolved = resolveExpression(rawExpr, { ctx, shadowed: new Set(), depth: 0 });
    // An unknown part glued into a path segment (`/events{*}`) could be any sub-path: unresolved.
    const urls = resolved && resolved.some(hasPartialWildcardSegment) ? null : resolved;
    const call: ScannedCall = { file, line, receiver, method, expression, urls };
    if (urls && urls.every((u) => ABSOLUTE.test(u))) {
      call.ignored = 'absolute URL';
    } else if (receiver === 'axios' && !urls && PRESIGNED.test(expression)) {
      // Bare axios has no API base URL; an upload/presigned URL argument goes straight to S3.
      call.ignored = 'presigned upload URL through bare axios';
    }
    calls.push(call);
  }
  return calls;
}

const EXCLUDED = [
  /\.test\.[jt]sx?$/,
  /\.spec\.[jt]sx?$/,
  /\.stories\.[jt]sx?$/,
  /\.d\.ts$/,
  /(^|\/)__tests__\//,
  /(^|\/)__mocks__\//,
  /(^|\/)__fixtures__\//,
  /^src\/mocks\//,
  /^src\/test\//,
  /^src\/types\/generated\//,
];

/** Lists the source files to scan, as paths relative to `root` with `/` separators. */
export function listSourceFiles(root: string, dir = 'src'): string[] {
  const files: string[] = [];
  const walk = (abs: string) => {
    for (const name of readdirSync(abs)) {
      const full = join(abs, name);
      if (statSync(full).isDirectory()) {
        if (name !== 'node_modules') walk(full);
      } else if (/\.(ts|tsx)$/.test(name)) {
        const rel = relative(root, full).split(sep).join('/');
        if (!EXCLUDED.some((re) => re.test(rel))) files.push(rel);
      }
    }
  };
  walk(join(root, dir));
  return files.sort();
}

// ---------------------------------------------------------------------------------------------
// Allow-list and layer baseline
// ---------------------------------------------------------------------------------------------

export interface AllowEntry {
  line: number;
  file: string;
  method: string;
  expression: string;
  reason: string;
}

export interface BaselineEntry {
  line: number;
  file: string;
  reason: string;
}

/** `file:METHOD:expression  # reason` per line; `#` lines and blank lines are skipped. */
export function parseAllowList(text: string): { entries: AllowEntry[]; errors: string[] } {
  const entries: AllowEntry[] = [];
  const errors: string[] = [];
  text.split('\n').forEach((raw, idx) => {
    const lineText = raw.trim();
    if (!lineText || lineText.startsWith('#')) return;
    const m = lineText.match(/^([^:\s]+):(GET|POST|PUT|PATCH|DELETE):(.+?)\s+#\s*(.*)$/);
    if (!m || !m[4].trim()) {
      errors.push(
        `allow-list line ${idx + 1} needs 'file:METHOD:expression  # reason': ${lineText}`
      );
      return;
    }
    entries.push({
      line: idx + 1,
      file: m[1],
      method: m[2],
      expression: m[3].trim().replace(/\s+/g, ' '),
      reason: m[4].trim(),
    });
  });
  return { entries, errors };
}

/** `file  # reason` per line. */
export function parseBaseline(text: string): { entries: BaselineEntry[]; errors: string[] } {
  const entries: BaselineEntry[] = [];
  const errors: string[] = [];
  text.split('\n').forEach((raw, idx) => {
    const lineText = raw.trim();
    if (!lineText || lineText.startsWith('#')) return;
    const m = lineText.match(/^(\S+)\s+#\s*(.*)$/);
    if (!m || !m[2].trim()) {
      errors.push(`layer baseline line ${idx + 1} needs 'file  # reason': ${lineText}`);
      return;
    }
    entries.push({ line: idx + 1, file: m[1], reason: m[2].trim() });
  });
  return { entries, errors };
}

// ---------------------------------------------------------------------------------------------
// The check
// ---------------------------------------------------------------------------------------------

export interface CheckInput {
  sources: Array<{ file: string; source: string }>;
  index: SpecIndex;
  allowListText: string;
  baselineText: string;
  /** Files under this prefix are the service layer. */
  serviceLayerPrefix?: string;
}

export interface CheckResult {
  exitCode: number;
  problems: string[];
  summary: string;
  calls: ScannedCall[];
  outOfLayerFiles: string[];
}

export function runCheck(input: CheckInput): CheckResult {
  const servicePrefix = input.serviceLayerPrefix ?? 'src/services/';
  const problems: string[] = [];
  const allow = parseAllowList(input.allowListText);
  const baseline = parseBaseline(input.baselineText);
  problems.push(...allow.errors, ...baseline.errors);

  const calls = input.sources.flatMap(({ file, source }) => scanSource(file, source));
  const usedAllow = new Set<AllowEntry>();
  const findAllow = (c: ScannedCall) =>
    allow.entries.find(
      (e) => e.file === c.file && e.method === c.method && e.expression === c.expression
    );

  let resolved = 0;
  let matched = 0;
  let allowListed = 0;
  for (const c of calls) {
    if (c.ignored) continue;
    const where = `${c.file}:${c.line}`;
    if (!c.urls) {
      const entry = findAllow(c);
      if (entry) {
        usedAllow.add(entry);
        allowListed++;
      } else {
        problems.push(`UNRESOLVED   ${where}  ${c.method} ${c.expression}`);
      }
      continue;
    }
    resolved++;
    const failures = c.urls
      .map((u) => ({ u, r: matchOperation(input.index, c.method, u) }))
      .filter((x) => !x.r.ok);
    if (failures.length === 0) {
      matched++;
      continue;
    }
    const entry = findAllow(c);
    if (entry) {
      usedAllow.add(entry);
      allowListed++;
      continue;
    }
    for (const { u, r } of failures) {
      problems.push(`NOT IN SPEC  ${where}  ${c.method} ${u}  (${r.ok ? '' : r.reason})`);
    }
  }
  for (const e of allow.entries) {
    if (!usedAllow.has(e)) {
      problems.push(
        `STALE        allow-list line ${e.line}: ${e.file}:${e.method}:${e.expression} matches no failing call`
      );
    }
  }

  const outOfLayerFiles = [
    ...new Set(calls.filter((c) => !c.file.startsWith(servicePrefix)).map((c) => c.file)),
  ].sort();
  const baselineFiles = new Set(baseline.entries.map((e) => e.file));
  for (const f of outOfLayerFiles) {
    if (!baselineFiles.has(f)) {
      problems.push(
        `OUT OF LAYER ${f}  makes HTTP calls outside ${servicePrefix}; move them into a service`
      );
    }
  }
  for (const e of baseline.entries) {
    if (!outOfLayerFiles.includes(e.file)) {
      problems.push(
        `STALE        layer baseline line ${e.line}: ${e.file} no longer makes HTTP calls; remove the line`
      );
    }
  }

  const summary = `Frontend API calls: ${resolved} resolved, ${matched} match the spec, ${allowListed} allow-listed.`;
  return { exitCode: problems.length ? 1 : 0, problems, summary, calls, outOfLayerFiles };
}

function main(): number {
  const scriptDir = dirname(fileURLToPath(import.meta.url));
  const root = join(scriptDir, '..');
  const specDir = join(root, '..', 'docs', 'api');
  const read = (p: string) => (existsSync(p) ? readFileSync(p, 'utf8') : '');
  const result = runCheck({
    sources: listSourceFiles(root).map((file) => ({
      file,
      source: readFileSync(join(root, file), 'utf8'),
    })),
    index: loadSpecIndex(specDir),
    allowListText: read(join(scriptDir, 'api-paths-allowlist.txt')),
    baselineText: read(join(scriptDir, 'api-layer-baseline.txt')),
  });
  if (process.argv.includes('--list-out-of-layer')) {
    for (const f of result.outOfLayerFiles) console.log(f);
    return 0;
  }
  for (const p of result.problems) console.log(p);
  console.log(result.summary);
  if (result.exitCode !== 0) {
    console.log(
      `\n${result.problems.length} problem(s). Fix the call or the spec; allow-list only what the scan cannot resolve, with a reason (web-frontend/scripts/api-paths-allowlist.txt).`
    );
  }
  return result.exitCode;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = main();
}
