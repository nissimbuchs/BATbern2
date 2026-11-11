/**
 * Story 17.4: the OpenAPI convention lint (Story 4.10 added `speaker-path-key`). Runs the real Spectral CLI with the repo ruleset
 * (`docs/api/.spectral.yaml`) over the fixture specs in `scripts/__fixtures__/api-lint/specs`
 * (excluded from Vitest and Prettier: read by Spectral, never run).
 */
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { cpSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  checkOwners,
  compare,
  defaultPaths,
  findingKey,
  formatBaseline,
  lintSpecs,
  parseBaseline,
  parseOwners,
  run,
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
const VERB_AFTER_LITERAL = `no-verb-segment\t${FIXTURE}\tPOST /users/me/profile-claims/confirm`;
const VERB_ON_COLLECTION = `no-verb-segment\t${FIXTURE}\tPOST /users/provision`;
const READ_VERB = `no-verb-segment\t${FIXTURE}\tGET /events/{eventCode}/attachments/{index}/download`;
const PERSONAL_DATA = `no-personal-data-path-param\t${FIXTURE}\tDELETE /users/me/additional-emails/{email}`;
const SPEAKER_BY_USERNAME = `speaker-path-key\t${FIXTURE}\tPOST /events/{eventCode}/speakers/{username}/send-invitation`;
const SPEAKER_POOL_LITERAL = `speaker-path-key\t${FIXTURE}\tPATCH /events/{eventCode}/speaker-pool/{speakerPoolId}/reminders`;
const SPEAKERS_POOL_LITERAL = `speaker-path-key\t${FIXTURE}\tGET /events/{eventCode}/speakers/pool`;
const UUID_SPEAKER_POOL = `uuid-path-param\t${FIXTURE}\tPATCH /events/{eventCode}/speaker-pool/{speakerPoolId}/reminders`;
const UUID_SPEAKER = `uuid-path-param\t${FIXTURE}\tPOST /events/{eventCode}/speakers/{speakerId}/promote`;
const ALL = [
  KEBAB,
  VERB,
  VERB_BEFORE_PARAM,
  VERB_BEFORE_QUALIFIER,
  VERB_AFTER_LITERAL,
  VERB_ON_COLLECTION,
  READ_VERB,
  PERSONAL_DATA,
  INTERNAL,
  EVENTS_LITERAL,
  UUID_GET,
  UUID_DELETE,
  UUID_UNFORMATTED,
  UUID_INHERITED,
  SPEAKER_BY_USERNAME,
  SPEAKER_POOL_LITERAL,
  SPEAKERS_POOL_LITERAL,
  UUID_SPEAKER_POOL,
  UUID_SPEAKER,
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

  it('reports an {email} path parameter as personal data, not a {username} one', () => {
    const byKey = new Map(findings.map((f) => [findingKey(f), f.message]));
    expect(byKey.get(PERSONAL_DATA)).toContain('email');
    expect(byKey.get(PERSONAL_DATA)).toContain('ADR-003');
    expect(findings.some((f) => f.path === '/users/{username}/profile')).toBe(false);
  });

  it('checks every segment of a write, only the last of a read', () => {
    expect(findings.map(findingKey).some((k) => k.includes('/publish/status'))).toBe(false);
  });

  it('allows the ADR-013 action shape POST /{id}/{verb}, after a parameter or `me`', () => {
    const keys = findings.map(findingKey);
    expect(keys.some((k) => k.includes('/companies/{name}/verify'))).toBe(false);
    expect(keys.some((k) => k.includes('/users/me/merge'))).toBe(false);
  });

  it('keys a speaker by {speakerId} (uuid) under /events/{eventCode}/speakers, one collection', () => {
    const byKey = new Map(findings.map((f) => [findingKey(f), f.message]));
    expect(byKey.get(SPEAKER_BY_USERNAME)).toContain('{username}');
    expect(byKey.get(SPEAKER_POOL_LITERAL)).toContain('speaker-pool');
    expect(byKey.get(SPEAKERS_POOL_LITERAL)).toContain('speakers/pool');
    const speakerKeyFindings = findings
      .filter((f) => f.rule === 'speaker-path-key')
      .map((f) => f.path);
    expect(speakerKeyFindings).not.toContain('/events/{eventCode}/speakers/{speakerId}/promote');
    expect(speakerKeyFindings).not.toContain('/events/{eventCode}/speakers/review-queue');
    expect(speakerKeyFindings).not.toContain(
      '/events/{eventCode}/sessions/{sessionSlug}/speakers/{username}'
    );
  });

  it.each([
    ['a verb after a literal', VERB_AFTER_LITERAL],
    ['a verb with another method than POST', VERB],
    ['a verb on a collection', VERB_ON_COLLECTION],
    ['a verb followed by a parameter', VERB_BEFORE_PARAM],
    ['a read verb as the last segment', READ_VERB],
  ])('flags %s', (_name, key) => {
    expect(findings.map(findingKey)).toContain(key);
  });
});

describe('compare', () => {
  it('passes when the baseline lists every finding (develop)', () => {
    const result = compare(findings, baselineOf(ALL));
    expect(result.problems).toEqual([]);
    expect(result.exitCode).toBe(0);
    expect(result.summary).toBe(
      'API lint: 19 finding(s): no-internal-path 1, no-literal-under-events 1, no-personal-data-path-param 1, no-verb-segment 6, path-kebab-case 1, speaker-path-key 3, uuid-path-param 6.'
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
    ['personal data in a path', PERSONAL_DATA],
    ['unformatted string *Id', UUID_UNFORMATTED],
    ['a username as the speaker key', SPEAKER_BY_USERNAME],
    ['a second speaker collection', SPEAKER_POOL_LITERAL],
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
      'no-verb-segment POST /events/{eventCode}/mails/preview',
      'no-verb-segment POST /speaker-portal/events/{eventCode}/content/submit',
      'no-verb-segment POST /logos/upload-from-url',
      'uuid-path-param POST /users/company-logo-requests/{requestId}/approve',
      'uuid-path-param POST /users/merge-requests/{requestId}/decline',
    ]) {
      expect(keys).toContain(expected);
    }
    // ADR-013 actions are no verb violations
    for (const action of [
      'no-verb-segment POST /users/company-logo-requests/{requestId}/approve',
      'no-verb-segment POST /users/merge-requests/{requestId}/decline',
      'no-verb-segment POST /companies/{name}/verify',
    ]) {
      expect(keys).not.toContain(action);
    }
  });

  it('gives every line exactly one owner group in the committed owner file', () => {
    const { entries } = parseBaseline(readFileSync(paths.baselinePath, 'utf8'));
    expect(checkOwners(entries, readFileSync(paths.ownersPath, 'utf8'))).toEqual([]);
  });
});

describe('owners', () => {
  const entries = parseBaseline(baselineOf([VERB, KEBAB])).entries;
  const owners = (...lines: string[]) => lines.join('\n') + '\n';
  const VERB_GROUP = 'A1\t17.9\tno-verb-segment\tPUT /tasks/';
  const KEBAB_GROUP =
    'A2\texception: legacy path kept for a reason\tpath-kebab-case\t/speakerSlots$';

  it('passes when every line matches exactly one group and every group a line', () => {
    expect(checkOwners(entries, owners('# comment', VERB_GROUP, KEBAB_GROUP))).toEqual([]);
  });

  it('reports a line without owner (UNOWNED)', () => {
    const problems = checkOwners(entries, owners(VERB_GROUP));
    expect(problems[0]).toMatch(/^UNOWNED +path-kebab-case {2}fixture-api\.openapi\.yml {2}GET /);
    expect(problems.at(-1)).toContain('without an owner');
  });

  it('reports a line in two groups (AMBIGUOUS), naming the line and the groups', () => {
    const problems = checkOwners(
      entries,
      owners(VERB_GROUP, KEBAB_GROUP, 'A3\t#1070\t(no-verb-segment|uuid-path-param)\t/complete$')
    );
    expect(problems).toEqual([
      `AMBIGUOUS    no-verb-segment  ${FIXTURE}  PUT /tasks/{id}/complete  matches groups A1, A3`,
    ]);
  });

  it('reports a group that matches nothing (STALE OWNER)', () => {
    const problems = checkOwners(
      entries,
      owners(VERB_GROUP, KEBAB_GROUP, 'A4\t17.10\tno-verb-segment\t/fixed-away$')
    );
    expect(problems).toHaveLength(1);
    expect(problems[0]).toMatch(/^STALE OWNER +group A4 \(17\.10\)/);
  });

  it('matches the rule regex against the whole rule name', () => {
    const problems = checkOwners(entries, owners('A1\t17.9\tverb\tPUT /tasks/', KEBAB_GROUP));
    expect(problems.some((p) => p.startsWith('UNOWNED'))).toBe(true);
  });

  it('rejects an owner without a story, an issue or a reasoned exception', () => {
    const { groups, errors } = parseOwners(
      owners(
        'B1\texception:\tno-verb-segment\t/a',
        'B2\tsomeone\tno-verb-segment\t/a',
        'B3\t17.9\tno-verb-segment\t(',
        'B4\t17.9\tno-verb-segment',
        'B5\t17.9b\tno-verb-segment\t/a',
        'B5\t#12\tno-verb-segment\t/b'
      )
    );
    expect(groups.map((g) => g.id)).toEqual(['B5']);
    expect(errors).toHaveLength(5);
    expect(errors.every((e) => e.startsWith('BAD OWNER'))).toBe(true);
  });
});

describe('run (the exit code of npm run lint:api)', () => {
  const ALL_OWNED = 'R1\t17.9\t.*\t.\n';
  const KEBAB_UNOWNED =
    'R1\t17.9\t(no-verb-segment|no-personal-data-path-param|no-internal-path|no-literal-under-events|uuid-path-param)\t.\n';
  let dir: string;
  const runIn = (argv: string[], baseline: string | null, owners: string) => {
    dir = mkdtempSync(join(tmpdir(), 'api-lint-run-'));
    const baselinePath = join(dir, 'baseline.txt');
    const ownersPath = join(dir, 'owners.txt');
    if (baseline !== null) writeFileSync(baselinePath, baseline);
    writeFileSync(ownersPath, owners);
    vi.spyOn(console, 'log').mockImplementation(() => {});
    const code = run(argv, { ...paths, specDir: fixtureSpecs, baselinePath, ownersPath });
    return { code, baselinePath, logs: vi.mocked(console.log).mock.calls.map((c) => String(c[0])) };
  };

  afterEach(() => {
    vi.restoreAllMocks();
    rmSync(dir, { recursive: true, force: true });
  });

  it('exits 0 when the baseline matches and every line is owned', () => {
    expect(runIn([], baselineOf(ALL), ALL_OWNED).code).toBe(0);
  });

  it('exits 1 on an UNOWNED baseline line', () => {
    const { code, logs } = runIn([], baselineOf(ALL), KEBAB_UNOWNED);
    expect(code).toBe(1);
    expect(logs.some((l) => l.startsWith('UNOWNED') && l.includes('speakerSlots'))).toBe(true);
  });

  it('--update writes the baseline and exits 1 when a written line is unowned', () => {
    const { code, baselinePath, logs } = runIn(['--update'], null, KEBAB_UNOWNED);
    expect(code).toBe(1);
    expect(
      parseBaseline(readFileSync(baselinePath, 'utf8')).entries.map(findingKey).sort()
    ).toEqual([...ALL].sort());
    expect(logs.some((l) => l.startsWith('UNOWNED'))).toBe(true);
  });

  it('--update exits 0 when every written line is owned', () => {
    expect(runIn(['--update'], null, ALL_OWNED).code).toBe(0);
  });
});
