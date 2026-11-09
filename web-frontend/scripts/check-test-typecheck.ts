/**
 * Shrinking exclude list of the test type-check (Epic 17.0 follow-up, orchestrator decision
 * 2026-11-09).
 *
 *   node scripts/check-test-typecheck.ts      (part of npm run type-check)
 *
 * `tsconfig.test-harness.json` type-checks all of `src`, tests included, except the test files in
 * its `exclude` list: the tests that did not compile when the switch was made. That list only
 * shrinks. This script compiles every file of `src` with the harness options, nothing excluded,
 * and fails (exit 1) when
 *   STALE     an excluded file compiles now (or no longer exists): remove it from `exclude`
 *   NOT TEST  an excluded entry is not a test file (only tests may be excluded)
 * A file that does not compile and is not excluded fails `tsc -p tsconfig.test-harness.json`
 * itself. Adding a file to `exclude` is review matter: fix the test instead (wire-shape fixtures,
 * see docs/architecture/coding-standards.md "Test fidelity rules").
 */
import ts from 'typescript';
import { existsSync } from 'node:fs';
import { dirname, join, relative, sep } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const HARNESS = 'tsconfig.test-harness.json';

const TEST_FILE = /(\.(test|spec)\.tsx?$)|(\/__tests__\/)|(^src\/test\/)/;

export interface Verdict {
  stale: string[];
  notTests: string[];
}

/** Compares the exclude list with the files that fail to compile (paths relative to the frontend). */
export function compare(
  excluded: readonly string[],
  failing: ReadonlySet<string>,
  exists: (path: string) => boolean
): Verdict {
  const stale = excluded.filter((p) => !exists(p) || !failing.has(p)).sort();
  const notTests = excluded.filter((p) => !TEST_FILE.test(p)).sort();
  return { stale, notTests };
}

/** Files of `src` (relative, `/`-separated) with at least one diagnostic, nothing excluded. */
export function failingFiles(root: string): Set<string> {
  const configPath = join(root, HARNESS);
  const read = ts.readConfigFile(configPath, ts.sys.readFile);
  if (read.error) {
    throw new Error(ts.flattenDiagnosticMessageText(read.error.messageText, '\n'));
  }
  const config = { ...read.config, include: ['src'], exclude: [] };
  const parsed = ts.parseJsonConfigFileContent(config, ts.sys, root, undefined, configPath);
  const program = ts.createProgram({ rootNames: parsed.fileNames, options: parsed.options });
  const out = new Set<string>();
  for (const d of ts.getPreEmitDiagnostics(program)) {
    if (d.file) {
      const rel = relative(root, d.file.fileName).split(sep).join('/');
      if (rel.startsWith('src/')) {
        out.add(rel);
      }
    }
  }
  return out;
}

/** The `exclude` entries of the harness config. */
export function excludedFiles(root: string): string[] {
  const read = ts.readConfigFile(join(root, HARNESS), ts.sys.readFile);
  if (read.error) {
    throw new Error(ts.flattenDiagnosticMessageText(read.error.messageText, '\n'));
  }
  return (read.config.exclude ?? []) as string[];
}

export function run(root: string, log: (line: string) => void = console.log): number {
  const excluded = excludedFiles(root);
  const verdict = compare(excluded, failingFiles(root), (p) => existsSync(join(root, p)));
  for (const p of verdict.stale) {
    log(`STALE     ${p}: compiles now (or is gone); remove it from "exclude" in ${HARNESS}`);
  }
  for (const p of verdict.notTests) {
    log(`NOT TEST  ${p}: only test files may be excluded from the type-check`);
  }
  if (verdict.stale.length > 0 || verdict.notTests.length > 0) {
    return 1;
  }
  log(`Test type-check: ${excluded.length} test file(s) still excluded (the list only shrinks).`);
  return 0;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  process.exit(run(join(dirname(fileURLToPath(import.meta.url)), '..')));
}
