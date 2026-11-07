/**
 * Story 17.4: the OpenAPI convention lint. Runs the real Spectral CLI with the repo ruleset
 * (`docs/api/.spectral.yaml`) over the fixture specs in `scripts/__fixtures__/api-lint/specs`
 * (excluded from Vitest and Prettier: read by Spectral, never run).
 */
import { beforeAll, describe, expect, it } from 'vitest';
import { cpSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  compare,
  defaultPaths,
  findingKey,
  formatBaseline,
  lintSpecs,
  parseBaseline,
  runSpectral,
  toFindings,
  type Finding,
} from './api-lint.ts';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const paths = defaultPaths(scriptDir);
const fixtureSpecs = join(scriptDir, '__fixtures__', 'api-lint', 'specs');
const FIXTURE = 'fixture-api.openapi.yml';

const KEBAB = `path-kebab-case\t${FIXTURE}\tGET /events/{eventCode}/speakerSlots`;
const VERB = `no-verb-segment\t${FIXTURE}\tPUT /tasks/{id}/complete`;
const INTERNAL = `no-internal-path\t${FIXTURE}\tGET /internal/foo`;
const EVENTS_LITERAL = `no-literal-under-events\t${FIXTURE}\tPOST /events/batch-things`;
const UUID_GET = `uuid-path-param\t${FIXTURE}\tGET /events/{eventCode}/photos/{photoId}`;
const UUID_DELETE = `uuid-path-param\t${FIXTURE}\tDELETE /events/{eventCode}/photos/{photoId}`;
const VERB_BEFORE_PARAM = `no-verb-segment\t${FIXTURE}\tPOST /events/{eventCode}/publish/{phase}`;
const VERB_BEFORE_QUALIFIER = `no-verb-segment\t${FIXTURE}\tPOST /registrations/deregister/by-email`;
const UUID_UNFORMATTED = `uuid-path-param\t${FIXTURE}\tGET /events/{eventCode}/uploads/{uploadId}`;
const UUID_INHERITED = `uuid-path-param\t${FIXTURE}\tDELETE /events/{eventCode}/teasers/{teaserId}`;
const ALL = [
  KEBAB,
  VERB,
  VERB_BEFORE_PARAM,
  VERB_BEFORE_QUALIFIER,
  INTERNAL,
  EVENTS_LITERAL,
  UUID_GET,
  UUID_DELETE,
  UUID_UNFORMATTED,
  UUID_INHERITED,
];

const baselineOf = (keys: string[]) => keys.join('\n') + '\n';

let findings: Finding[];

beforeAll(() => {
  const run = lintSpecs({ ...paths, specDir: fixtureSpecs });
  expect(run.problems).toEqual([]);
  findings = run.findings;
});

describe('the ruleset on the fixture specs', () => {
  it('reports each broken rule once per operation, nothing for the clean path or _shared', () => {
    expect(findings.map(findingKey).sort()).toEqual([...ALL].sort());
  });

  it('says what is wrong', () => {
    const byKey = new Map(findings.map((f) => [findingKey(f), f.message]));
    expect(byKey.get(KEBAB)).toContain('speakerSlots');
    expect(byKey.get(VERB)).toContain('complete');
    expect(byKey.get(UUID_GET)).toContain('photoId');
    expect(byKey.get(UUID_GET)).toContain('ADR-003');
  });
});

describe('rule details', () => {
  it('lets an operation parameter replace the path-level one with the same name + in', () => {
    const keys = findings.map(findingKey);
    expect(keys).toContain(UUID_INHERITED);
    expect(keys).not.toContain(
      `uuid-path-param\t${FIXTURE}\tGET /events/{eventCode}/teasers/{teaserId}`
    );
  });

  it('checks every segment of a write, only the last of a read', () => {
    expect(findings.map(findingKey).some((k) => k.includes('/publish/status'))).toBe(false);
  });
});

describe('compare', () => {
  it('passes when the baseline lists every finding (develop)', () => {
    const result = compare(findings, baselineOf(ALL));
    expect(result.problems).toEqual([]);
    expect(result.exitCode).toBe(0);
    expect(result.summary).toBe(
      'API lint: 10 finding(s): no-internal-path 1, no-literal-under-events 1, no-verb-segment 3, path-kebab-case 1, uuid-path-param 4.'
    );
  });

  it.each([
    ['kebab', KEBAB],
    ['verb', VERB],
    ['verb before a parameter (write)', VERB_BEFORE_PARAM],
    ['verb before a qualifier (write)', VERB_BEFORE_QUALIFIER],
    ['internal', INTERNAL],
    ['events literal', EVENTS_LITERAL],
    ['uuid', UUID_GET],
    ['unformatted string *Id', UUID_UNFORMATTED],
  ])('fails on a NEW finding: %s', (_name, key) => {
    const result = compare(findings, baselineOf(ALL.filter((k) => k !== key)));
    expect(result.exitCode).toBe(1);
    const [rule, file, op] = key.split('\t');
    expect(result.problems[0]).toMatch(new RegExp(`^NEW +${rule}  ${file}  `));
    expect(result.problems[0]).toContain(op);
    expect(result.problems.at(-1)).toContain('reason stated in the PR');
  });

  it('fails on a fixed violation still in the baseline (STALE) and says to run --update', () => {
    const fixed = `no-verb-segment\t${FIXTURE}\tPOST /tasks/{id}/reassign`;
    const result = compare(findings, baselineOf([...ALL, fixed]));
    expect(result.exitCode).toBe(1);
    expect(result.problems[0]).toMatch(
      /^STALE +no-verb-segment {2}fixture-api\.openapi\.yml {2}POST \/tasks\/\{id\}\/reassign/
    );
    expect(result.problems.at(-1)).toContain('npm run lint:api -- --update');
  });

  it('keys by rule, file, method and path, not by line: an edit elsewhere in the spec moves nothing', () => {
    const dir = mkdtempSync(join(tmpdir(), 'api-lint-'));
    try {
      cpSync(fixtureSpecs, dir, { recursive: true });
      const spec = join(dir, FIXTURE);
      const text = readFileSync(spec, 'utf8').replace(
        'paths:\n',
        "paths:\n  /events/{eventCode}/added-first:\n    get:\n      responses:\n        '200':\n          description: ok\n"
      );
      writeFileSync(spec, text);
      const moved = lintSpecs({ ...paths, specDir: dir });
      expect(moved.problems).toEqual([]);
      expect(compare(moved.findings, baselineOf(ALL)).exitCode).toBe(0);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  it('round-trips through formatBaseline', () => {
    const text = formatBaseline(findings);
    expect(text.startsWith('# OpenAPI convention lint baseline')).toBe(true);
    expect(compare(findings, text).exitCode).toBe(0);
  });
});

describe('parseBaseline', () => {
  it('rejects malformed and duplicate lines', () => {
    const { entries, errors } = parseBaseline(
      [
        '# comment',
        VERB,
        VERB,
        'unknown-rule\tx.openapi.yml\tGET /a',
        `no-verb-segment\t${FIXTURE}\tFETCH /a`,
        `no-verb-segment\t${FIXTURE}`,
      ].join('\n')
    );
    expect(entries.map(findingKey)).toEqual([VERB]);
    expect(errors).toHaveLength(4);
    expect(errors.every((e) => e.startsWith('BAD LINE'))).toBe(true);
  });
});

describe('toFindings', () => {
  it('turns a result outside the ruleset into a SPECTRAL problem', () => {
    const { findings: f, problems } = toFindings([
      {
        code: 'parser',
        path: [],
        message: 'Duplicate key',
        severity: 0,
        source: '/x/a.openapi.yml',
      },
      {
        code: 'no-verb-segment',
        path: ['paths', '/a/send', 'post'],
        message: 'verb',
        severity: 0,
        source: '/x/a.openapi.yml',
      },
    ]);
    expect(f.map(findingKey)).toEqual(['no-verb-segment\ta.openapi.yml\tPOST /a/send']);
    expect(problems).toEqual(['SPECTRAL     a.openapi.yml  parser  : Duplicate key']);
  });
});

describe('runSpectral', () => {
  it('reports an error when Spectral cannot run, instead of an empty pass', () => {
    const run = runSpectral(
      [join(fixtureSpecs, FIXTURE)],
      join(fixtureSpecs, 'no-such-ruleset.yaml'),
      paths.spectralBin
    );
    expect(run.error).toMatch(/^Spectral did not run/);
  });
});

describe('the committed baseline', () => {
  it('lists the known cases of Story 17.4 AC2', () => {
    const { entries, errors } = parseBaseline(readFileSync(paths.baselinePath, 'utf8'));
    expect(errors).toEqual([]);
    const keys = entries.map((e) => `${e.rule} ${e.method} ${e.path}`);
    for (const expected of [
      'path-kebab-case POST /events/batch_registrations',
      'no-literal-under-events POST /events/batch_registrations',
      'no-verb-segment POST /speakers/{speakerId}/ai/analyze-abstract',
      'no-verb-segment PUT /tasks/{taskId}/complete',
      'no-verb-segment PUT /tasks/{taskId}/reassign',
      'no-verb-segment POST /users/company-logo-requests/{requestId}/approve',
      'no-verb-segment POST /users/company-logo-requests/{requestId}/decline',
      'no-verb-segment POST /users/merge-requests/{requestId}/approve',
      'no-verb-segment POST /users/merge-requests/{requestId}/decline',
    ]) {
      expect(keys).toContain(expected);
    }
  });
});
