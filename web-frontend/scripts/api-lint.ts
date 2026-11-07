/**
 * OpenAPI convention lint with a baseline (Story 17.4).
 *
 *   node scripts/api-lint.ts            (npm run lint:api, make lint-api, CI frontend-static)
 *   node scripts/api-lint.ts --update   rewrite the baseline after a violation was fixed
 *
 * Runs Spectral with the repo ruleset `docs/api/.spectral.yaml` over the public specs
 * `docs/api/*.openapi.yml` (not the `_`-prefixed shared components) and compares the findings
 * with the committed baseline `scripts/api-lint-baseline.txt`. A finding is keyed by rule + spec
 * file + HTTP method + path, never by line, so a spec edit elsewhere does not move it. Any
 * difference fails (exit 1):
 *   NEW      a finding the baseline does not list: fix the spec, or (only with the reason stated
 *            in the PR, e.g. the ADR-003 case of a UUID path parameter) run `--update`
 *   STALE    a baselined finding that is gone (good): run `--update` and commit the baseline
 *   SPECTRAL Spectral could not run, or reported a result from outside the ruleset
 * so the baseline only ever shrinks unless a PR says why it grows.
 *
 * Plain TypeScript for Node >= 22.18 type stripping (`.ts` imports, no enums). Needs `npm ci`
 * for the Spectral CLI.
 */
import { spawnSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { basename, dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const RULES = [
  'no-internal-path',
  'no-literal-under-events',
  'no-verb-segment',
  'path-kebab-case',
  'uuid-path-param',
] as const;
export type Rule = (typeof RULES)[number];

export const POLICY_POINTER =
  'docs/architecture/coding-standards.md, section "API conventions lint"';
export const UPDATE_COMMAND = 'cd web-frontend && npm run lint:api -- --update';

const HTTP_METHODS = new Set(['get', 'put', 'post', 'patch', 'delete', 'head', 'options']);

export interface Finding {
  rule: Rule;
  /** Spec file name, e.g. `event-tasks-api.openapi.yml`. */
  file: string;
  /** Upper case, e.g. `PUT`. */
  method: string;
  path: string;
  message: string;
}

/** One Spectral JSON result (the fields this script reads). */
export interface SpectralResult {
  code: string | number;
  path: Array<string | number>;
  message: string;
  severity: number;
  source?: string;
}

export const findingKey = (f: Pick<Finding, 'rule' | 'file' | 'method' | 'path'>) =>
  `${f.rule}\t${f.file}\t${f.method} ${f.path}`;

// ---------------------------------------------------------------------------------------------
// Spectral
// ---------------------------------------------------------------------------------------------

/** The public specs of `specDir`, absolute paths, sorted. */
export function listSpecFiles(specDir: string): string[] {
  return readdirSync(specDir)
    .filter((f) => /\.openapi\.ya?ml$/.test(f) && !f.startsWith('_'))
    .sort()
    .map((f) => join(specDir, f));
}

/** Runs the Spectral CLI; returns its JSON results, or an error text when it could not run. */
export function runSpectral(
  specFiles: string[],
  rulesetPath: string,
  spectralBin: string
): { results: SpectralResult[]; error?: string } {
  if (specFiles.length === 0) return { results: [] };
  const run = spawnSync(
    spectralBin,
    ['lint', '--ruleset', rulesetPath, '--format', 'json', '--quiet', ...specFiles],
    { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }
  );
  const stdout = (run.stdout ?? '').trim();
  try {
    const parsed = JSON.parse(stdout || '[]');
    if (!Array.isArray(parsed)) throw new Error('not an array');
    if (run.status !== 0 && run.status !== 1) throw new Error(`exit ${run.status}`);
    return { results: parsed as SpectralResult[] };
  } catch (e) {
    const why = run.error ? run.error.message : (e as Error).message;
    return {
      results: [],
      error: `Spectral did not run (${why}). stderr: ${(run.stderr ?? '').trim() || '(empty)'}`,
    };
  }
}

/** Maps Spectral results to findings; anything outside the ruleset becomes a problem. */
export function toFindings(results: SpectralResult[]): {
  findings: Finding[];
  problems: string[];
} {
  const findings: Finding[] = [];
  const problems: string[] = [];
  const seen = new Set<string>();
  for (const r of results) {
    const code = String(r.code);
    const file = r.source ? basename(r.source) : '(unknown file)';
    const [root, path, method] = r.path;
    const isRule = (RULES as readonly string[]).includes(code);
    if (
      !isRule ||
      root !== 'paths' ||
      typeof path !== 'string' ||
      typeof method !== 'string' ||
      !HTTP_METHODS.has(method)
    ) {
      problems.push(`SPECTRAL     ${file}  ${code}  ${r.path.join('.')}: ${r.message}`);
      continue;
    }
    const finding: Finding = {
      rule: code as Rule,
      file,
      method: method.toUpperCase(),
      path,
      message: r.message,
    };
    const key = findingKey(finding);
    if (seen.has(key)) continue;
    seen.add(key);
    findings.push(finding);
  }
  return { findings: sortFindings(findings), problems };
}

function sortFindings<T extends Pick<Finding, 'rule' | 'file' | 'method' | 'path'>>(
  findings: T[]
): T[] {
  return [...findings].sort((a, b) =>
    findingKey(a) < findingKey(b) ? -1 : findingKey(a) > findingKey(b) ? 1 : 0
  );
}

// ---------------------------------------------------------------------------------------------
// Baseline
// ---------------------------------------------------------------------------------------------

const BASELINE_HEADER = [
  '# OpenAPI convention lint baseline (Story 17.4). Generated, do not edit by hand:',
  `#   ${UPDATE_COMMAND}`,
  `# Rules: docs/api/.spectral.yaml. Policy: ${POLICY_POINTER}.`,
  '# Existing violations only. It shrinks with Stories 17.6, 17.9, 17.10; a PR that grows it',
  '# states why (for uuid-path-param: the ADR-003 amendment case).',
  '# Format: <rule> TAB <spec file> TAB <METHOD> <path>, sorted.',
];

export function formatBaseline(
  findings: Array<Pick<Finding, 'rule' | 'file' | 'method' | 'path'>>
) {
  return [...BASELINE_HEADER, ...sortFindings(findings).map(findingKey)].join('\n') + '\n';
}

export type BaselineEntry = Pick<Finding, 'rule' | 'file' | 'method' | 'path'>;

export function parseBaseline(text: string): { entries: BaselineEntry[]; errors: string[] } {
  const entries: BaselineEntry[] = [];
  const errors: string[] = [];
  const seen = new Set<string>();
  text.split('\n').forEach((raw, idx) => {
    const line = raw.trim();
    if (!line || line.startsWith('#')) return;
    const parts = raw.split('\t');
    const op = /^([A-Z]+) (\/\S*)$/.exec(parts[2] ?? '');
    if (
      parts.length !== 3 ||
      !(RULES as readonly string[]).includes(parts[0]) ||
      !parts[1] ||
      !op ||
      !HTTP_METHODS.has(op[1].toLowerCase())
    ) {
      errors.push(
        `BAD LINE     baseline line ${idx + 1}: ${line}  (want <rule>\\t<spec file>\\t<METHOD> <path>)`
      );
      return;
    }
    const entry: BaselineEntry = {
      rule: parts[0] as Rule,
      file: parts[1],
      method: op[1],
      path: op[2],
    };
    const key = findingKey(entry);
    if (seen.has(key)) {
      errors.push(`BAD LINE     baseline line ${idx + 1}: duplicate ${line}`);
      return;
    }
    seen.add(key);
    entries.push(entry);
  });
  return { entries, errors };
}

export function summarize(findings: Array<Pick<Finding, 'rule'>>): string {
  const parts = RULES.map((rule) => `${rule} ${findings.filter((f) => f.rule === rule).length}`);
  return `API lint: ${findings.length} finding(s): ${parts.join(', ')}.`;
}

export interface CompareResult {
  exitCode: number;
  problems: string[];
  summary: string;
}

export function compare(findings: Finding[], baselineText: string): CompareResult {
  const baseline = parseBaseline(baselineText);
  const problems = [...baseline.errors];
  const base = new Set(baseline.entries.map(findingKey));
  const now = new Set(findings.map(findingKey));
  const added = findings.filter((f) => !base.has(findingKey(f)));
  const gone = baseline.entries.filter((e) => !now.has(findingKey(e)));
  for (const f of added) {
    problems.push(`NEW          ${f.rule}  ${f.file}  ${f.method} ${f.path}  (${f.message})`);
  }
  for (const e of gone) {
    problems.push(`STALE        ${e.rule}  ${e.file}  ${e.method} ${e.path}  no longer reported`);
  }
  if (added.length) {
    problems.push(
      `${added.length} new API convention violation(s). Fix the spec (${POLICY_POINTER}); accept one only with the reason stated in the PR, then ${UPDATE_COMMAND}.`
    );
  }
  if (gone.length) {
    problems.push(
      `${gone.length} baselined violation(s) fixed (good). Lock the gain in: ${UPDATE_COMMAND}, and commit web-frontend/scripts/api-lint-baseline.txt.`
    );
  }
  return { exitCode: problems.length ? 1 : 0, problems, summary: summarize(findings) };
}

// ---------------------------------------------------------------------------------------------
// Run
// ---------------------------------------------------------------------------------------------

export interface LintInput {
  specDir: string;
  rulesetPath: string;
  spectralBin: string;
}

/** Lints `specDir`; Spectral problems are returned beside the findings. */
export function lintSpecs(input: LintInput): { findings: Finding[]; problems: string[] } {
  const run = runSpectral(listSpecFiles(input.specDir), input.rulesetPath, input.spectralBin);
  if (run.error) return { findings: [], problems: [`SPECTRAL     ${run.error}`] };
  return toFindings(run.results);
}

export function defaultPaths(scriptDir: string) {
  const frontend = join(scriptDir, '..');
  const specDir = join(frontend, '..', 'docs', 'api');
  return {
    specDir,
    rulesetPath: join(specDir, '.spectral.yaml'),
    spectralBin: join(frontend, 'node_modules', '.bin', 'spectral'),
    baselinePath: join(scriptDir, 'api-lint-baseline.txt'),
  };
}

function main(): number {
  const paths = defaultPaths(dirname(fileURLToPath(import.meta.url)));
  const { findings, problems } = lintSpecs(paths);
  if (problems.length) {
    for (const p of problems) console.log(p);
    console.log(`${problems.length} problem(s) running the lint; the baseline was not compared.`);
    return 1;
  }
  if (process.argv.includes('--update')) {
    writeFileSync(paths.baselinePath, formatBaseline(findings));
    console.log(summarize(findings));
    console.log(
      `Baseline written: web-frontend/scripts/api-lint-baseline.txt (${findings.length} lines).`
    );
    return 0;
  }
  const baselineText = existsSync(paths.baselinePath)
    ? readFileSync(paths.baselinePath, 'utf8')
    : '';
  const result = compare(findings, baselineText);
  for (const p of result.problems) console.log(p);
  console.log(result.summary);
  return result.exitCode;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = main();
}
