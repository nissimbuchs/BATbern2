/**
 * Epic 17 entry 6: the S2S client URL guard. Fixture repository under
 * `scripts/__fixtures__/s2s-client-urls/repo` (read as text, never compiled).
 */
import { describe, expect, it } from 'vitest';
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  checkOwners,
  compare,
  formatBaseline,
  javaStringLiterals,
  listClientFiles,
  parseBaseline,
  parseOwners,
  run,
  scanSource,
  scanTree,
} from './check-s2s-client-urls.ts';

const repo = join(
  dirname(fileURLToPath(import.meta.url)),
  '__fixtures__',
  's2s-client-urls',
  'repo'
);
const CLIENT = 'services/demo-service/src/main/java/ch/demo/client/impl/DemoClientImpl.java';
const DUP = 'services/demo-service/src/main/java/ch/demo/client/impl/DupClientImpl.java';

const BASELINE_ALL = formatBaseline([
  { file: CLIENT, url: '/api/v1/x' },
  { file: CLIENT, url: '/api/v1/partners?include=company&size=200' },
  { file: CLIENT, url: 'GET /api/v1/in-text-block' },
  { file: CLIENT, url: '/api/v1/after-char' },
  { file: DUP, url: '/api/v1/dup' },
  { file: DUP, url: '/api/v1/dup' },
]);

/** BASELINE_ALL with one of the two identical /api/v1/dup lines removed. */
const BASELINE_ONE_DUP = BASELINE_ALL.replace(`${DUP}\t/api/v1/dup\n`, '');

const OWNERS_ALL = `# comment\nD1\t6b\tdemo-service/.*/client/\t/api/v1/\n`;

function tempPaths(baseline: string, owners: string) {
  const dir = mkdtempSync(join(tmpdir(), 's2s-urls-'));
  const baselinePath = join(dir, 'baseline.txt');
  const ownersPath = join(dir, 'owners.txt');
  writeFileSync(baselinePath, baseline);
  writeFileSync(ownersPath, owners);
  return { repoRoot: repo, baselinePath, ownersPath };
}

describe('listClientFiles', () => {
  it('reads main Java sources under a client directory only, not services or tests', () => {
    expect(listClientFiles(repo)).toEqual([CLIENT, DUP]);
  });
});

describe('javaStringLiterals', () => {
  it('skips comments and char literals, keeps strings and text blocks', () => {
    const texts = javaStringLiterals(
      `// "/a"\n/* "/b" */\nchar c = '"'; String s = "/c"; String t = """\n  /d\n  """;`
    ).map((l) => l.text.trim());
    expect(texts).toEqual(['/c', '/d']);
  });
});

describe('scanSource', () => {
  it('finds every /api/v1 literal with its line, ignores /internal and comments', () => {
    const sites = scanSource(CLIENT, readFileSync(join(repo, CLIENT), 'utf8'));
    expect(sites.map((s) => [s.url, s.line])).toEqual([
      ['/api/v1/x', 12],
      ['/api/v1/partners?include=company&size=200', 20],
      ['GET /api/v1/in-text-block', 24],
      ['/api/v1/after-char', 34],
    ]);
  });
});

describe('compare', () => {
  const { sites } = scanTree(repo);

  it('passes when the baseline lists every call site', () => {
    expect(compare(sites, BASELINE_ALL)).toEqual({ exitCode: 0, problems: [] });
  });

  it('fails with RAISED for a call site the baseline does not list', () => {
    const baseline = formatBaseline(
      parseBaseline(BASELINE_ALL).entries.filter((e) => e.url !== '/api/v1/x')
    );
    const result = compare(sites, baseline);
    expect(result.exitCode).toBe(1);
    expect(result.problems[0]).toBe(
      `RAISED       ${CLIENT}:12  "/api/v1/x"  a client calls another service's public API`
    );
  });

  it('fails with STALE for a baselined call site that is gone', () => {
    const baseline = BASELINE_ALL + `${CLIENT}\t/api/v1/moved-away\n`;
    const result = compare(sites, baseline);
    expect(result.exitCode).toBe(1);
    expect(result.problems[0]).toBe(
      `STALE        ${CLIENT}  "/api/v1/moved-away"  occurrence no longer found`
    );
  });

  it('reports a malformed baseline line', () => {
    const result = compare(sites, BASELINE_ALL + 'no-tab-here\n');
    expect(result.problems[0]).toMatch(/^BAD LINE/);
  });
});

describe('owners', () => {
  const entries = parseBaseline(BASELINE_ALL).entries;

  it('accepts a baseline whose every line has exactly one group', () => {
    expect(checkOwners(entries, OWNERS_ALL)).toEqual([]);
  });

  it('reports UNOWNED, AMBIGUOUS and STALE OWNER', () => {
    const owners = `A\t6b\tdemo-service\t/api/v1/x\nB\t8\tdemo-service\t/api/v1/(x|partners)\nC\t17.6\tnowhere\t/api/v1/\n`;
    const problems = checkOwners(entries, owners);
    expect(problems.filter((p) => p.startsWith('AMBIGUOUS'))).toHaveLength(1);
    expect(problems.filter((p) => p.startsWith('UNOWNED'))).toHaveLength(4);
    expect(problems).toContain(
      'STALE OWNER  group C (17.6) matches no baseline line: remove it from web-frontend/scripts/s2s-client-urls-owners.txt'
    );
  });

  it('rejects an owner without a story, issue or exception reason', () => {
    expect(parseOwners('A\tsomeone\t.\t.\n').errors[0]).toMatch(/^BAD OWNER/);
    expect(
      parseOwners('A\texception: needs a public read, owner 2026-11-10\t.\t.\n').errors
    ).toEqual([]);
  });
});

describe('run', () => {
  it('passes on a complete baseline', () => {
    expect(run([], tempPaths(BASELINE_ALL, OWNERS_ALL))).toBe(0);
  });

  it('fails on a fixture client with "/api/v1/x" missing from the baseline (RAISED)', () => {
    const baseline = formatBaseline(
      parseBaseline(BASELINE_ALL).entries.filter((e) => e.url !== '/api/v1/x')
    );
    expect(run([], tempPaths(baseline, OWNERS_ALL))).toBe(1);
  });

  it('--update removes a gone line but refuses to add a new one', () => {
    const shrink = tempPaths(BASELINE_ALL + `${CLIENT}\t/api/v1/moved-away\n`, OWNERS_ALL);
    expect(run(['--update'], shrink)).toBe(0);
    expect(readFileSync(shrink.baselinePath, 'utf8')).toBe(BASELINE_ALL);

    const before = formatBaseline(
      parseBaseline(BASELINE_ALL).entries.filter((e) => e.url !== '/api/v1/x')
    );
    const grow = tempPaths(before, OWNERS_ALL);
    expect(run(['--update'], grow)).toBe(1);
    expect(readFileSync(grow.baselinePath, 'utf8')).toBe(before);
    expect(run(['--update', '--allow-growth'], grow)).toBe(0);
    expect(readFileSync(grow.baselinePath, 'utf8')).toBe(BASELINE_ALL);
  });
});

describe('a literal that occurs twice in one file', () => {
  const { sites } = scanTree(repo);

  it('is two sites, the baseline holds a line per occurrence and parses cleanly', () => {
    expect(sites.filter((c) => c.file === DUP).map((c) => [c.url, c.line])).toEqual([
      ['/api/v1/dup', 7],
      ['/api/v1/dup', 11],
    ]);
    expect(parseBaseline(BASELINE_ALL).errors).toEqual([]);
    expect(compare(sites, BASELINE_ALL)).toEqual({ exitCode: 0, problems: [] });
  });

  it('a second occurrence of a baselined literal is RAISED', () => {
    const result = compare(sites, BASELINE_ONE_DUP);
    expect(result.exitCode).toBe(1);
    expect(result.problems[0]).toBe(
      `RAISED       ${DUP}:11  "/api/v1/dup"  a client calls another service's public API`
    );
  });

  it('one occurrence fewer than baselined is STALE', () => {
    const result = compare(
      sites.filter((c) => !(c.file === DUP && c.line === 11)),
      BASELINE_ALL
    );
    expect(result.exitCode).toBe(1);
    expect(result.problems[0]).toBe(
      `STALE        ${DUP}  "/api/v1/dup"  occurrence no longer found`
    );
  });

  it('--update writes both lines, the result parses and passes, and growth is refused', () => {
    const paths = tempPaths(BASELINE_ALL, OWNERS_ALL);
    expect(run(['--update'], paths)).toBe(0);
    const written = readFileSync(paths.baselinePath, 'utf8');
    expect(written).toBe(BASELINE_ALL);
    expect(parseBaseline(written).errors).toEqual([]);
    expect(run([], paths)).toBe(0);

    const grow = tempPaths(BASELINE_ONE_DUP, OWNERS_ALL);
    expect(run(['--update'], grow)).toBe(1);
    expect(readFileSync(grow.baselinePath, 'utf8')).toBe(BASELINE_ONE_DUP);
  });
});
