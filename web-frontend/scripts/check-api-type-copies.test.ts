/**
 * Story 17.3c: the gate against hand-written copies of generated API types. Runs on the fixture
 * tree `scripts/__fixtures__/api-type-copies/` (excluded from Vitest and Prettier: read as text,
 * never compiled).
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cpSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  checkOwners,
  compare,
  copyKey,
  extractSchemaNames,
  formatBaseline,
  loadSchemaNames,
  parseBaseline,
  parseOwners,
  run,
  scanSource,
  scanTree,
  type Copy,
} from './check-api-type-copies.ts';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const fixtureRoot = join(scriptDir, '__fixtures__', 'api-type-copies');
const COPIES = 'src/services/copies.ts';

const EVENT = `${COPIES}\tEventResponse`;
const PAGED = `${COPIES}\tPagedThing`;
const DECLARED = `${COPIES}\tOptionalName`;
const ALL = [EVENT, DECLARED, PAGED];

const schemaNames = new Set(['EventResponse', 'PagedThing', 'Other']);
const scan = (source: string) => scanSource('src/x.ts', source, schemaNames);

describe('extractSchemaNames', () => {
  it('lists the members of components.schemas only, quoted and optional names included', () => {
    const source = readFileSync(
      join(fixtureRoot, 'src/types/generated/fixture-api.types.ts'),
      'utf8'
    );
    expect(extractSchemaNames(source)).toEqual([
      'EventResponse',
      'PagedThing',
      'AliasedOne',
      'HiddenOne',
      'CommentedOne',
      'UnionOne',
      'IntersectionOne',
      'Quoted-Name',
      'OptionalName',
      'TestOnly',
    ]);
  });

  it('finds nothing without a components interface', () => {
    expect(extractSchemaNames('export interface paths {}\n')).toEqual([]);
  });

  it('reads every generated file of the real tree', () => {
    const names = loadSchemaNames(join(scriptDir, '..'));
    expect(names.size).toBeGreaterThan(300);
    expect(names.has('Event')).toBe(true);
    expect(names.has('SessionResponse')).toBe(true);
  });
});

describe('scanSource', () => {
  it('reports an exported interface named like a schema', () => {
    expect(scan('export interface EventResponse { x: string }\n')).toEqual([
      { file: 'src/x.ts', name: 'EventResponse', line: 1, kind: 'interface' },
    ]);
  });

  it('reports an object-literal type, also generic, with its line', () => {
    const copies = scan(
      '\n\nexport type PagedThing<T extends { a: 1 } = { a: 1 }> =\n  { items: T[] };\nexport type Other = {};\n'
    );
    expect(copies.map((c) => [c.name, c.line, c.kind])).toEqual([
      ['PagedThing', 3, 'type'],
      ['Other', 5, 'type'],
    ]);
  });

  it('passes an alias, a union and an intersection', () => {
    expect(
      scan(
        [
          "export type EventResponse = components['schemas']['EventResponse'];",
          "export type PagedThing = 'A' | { b: 1 };",
          "export type Other = Omit<A, 'b'> & { c: string };",
        ].join('\n')
      )
    ).toEqual([]);
  });

  it('passes other names, non-exported and commented declarations, and strings', () => {
    expect(
      scan(
        [
          'export interface EventCardProps { x: 1 }',
          'interface EventResponse { x: 1 }',
          'type PagedThing = { x: 1 };',
          '// export interface EventResponse { x: 1 }',
          '/* export type Other = { x: 1 }; */',
          "const s = 'export interface Other {}';",
        ].join('\n')
      )
    ).toEqual([]);
  });

  it('reports a declared interface', () => {
    expect(scan('export declare interface Other { x: 1 }').map((c) => c.name)).toEqual(['Other']);
  });
});

describe('scanTree on the fixture', () => {
  it('finds the copies in production files, not in tests or the generated dir', () => {
    const { copies, schemaCount } = scanTree(fixtureRoot);
    expect(schemaCount).toBe(10);
    expect(copies.map(copyKey)).toEqual(ALL);
  });
});

describe('baseline', () => {
  const copies: Copy[] = [
    { file: 'src/b.ts', name: 'B', line: 1, kind: 'interface' },
    { file: 'src/a.ts', name: 'Z', line: 1, kind: 'type' },
    { file: 'src/a.ts', name: 'A', line: 9, kind: 'interface' },
  ];

  it('is written sorted, one file TAB name per line, under a header', () => {
    const text = formatBaseline(copies);
    expect(text.split('\n').filter((l) => l && !l.startsWith('#'))).toEqual([
      'src/a.ts\tA',
      'src/a.ts\tZ',
      'src/b.ts\tB',
    ]);
    expect(text).toContain('npm run check:api-type-copies -- --update');
    expect(parseBaseline(text)).toEqual({
      entries: [
        { file: 'src/a.ts', name: 'A' },
        { file: 'src/a.ts', name: 'Z' },
        { file: 'src/b.ts', name: 'B' },
      ],
      errors: [],
    });
  });

  it('rejects malformed and duplicate lines', () => {
    const { errors } = parseBaseline('src/a.ts\tA\nsrc/a.ts A\nsrc/a.ts\tA\n');
    expect(errors).toHaveLength(2);
    expect(errors[0]).toContain('BAD LINE');
    expect(errors[1]).toContain('duplicate');
  });

  it('passes when the copies equal the baseline', () => {
    expect(compare(copies, formatBaseline(copies))).toEqual({ exitCode: 0, problems: [] });
  });

  it('fails NEW for a copy the baseline lacks, naming file, line and type', () => {
    const result = compare(copies, formatBaseline(copies.slice(1)));
    expect(result.exitCode).toBe(1);
    expect(result.problems[0]).toMatch(/^NEW +src\/b\.ts:1 +B /);
  });

  it('fails STALE for a baselined copy that is gone, with the update hint', () => {
    const result = compare(copies.slice(1), formatBaseline(copies));
    expect(result.exitCode).toBe(1);
    expect(result.problems[0]).toMatch(/^STALE +src\/b\.ts +B /);
    expect(result.problems.join('\n')).toContain('--update');
  });
});

describe('owners', () => {
  const entries = [
    { file: 'src/services/newsletterService.ts', name: 'NewsletterSendRequest' },
    { file: 'src/services/newsletterService.ts', name: 'NewsletterSendResponse' },
    { file: 'src/types/x.ts', name: 'Thing' },
  ];
  const owners = (...lines: string[]) => lines.join('\n') + '\n';

  it('accepts groups that own every line exactly once', () => {
    expect(
      checkOwners(
        entries,
        owners(
          '# comment',
          'N1\t16.8c\tnewsletterService\\.ts$\tNewsletterSend(Request|Response)',
          'X1\texception: kept for a reason\t^src/types/x\\.ts$\tThing'
        )
      )
    ).toEqual([]);
  });

  it('matches the name regex against the whole name', () => {
    const problems = checkOwners(
      entries,
      owners('N1\t16.8c\tnewsletterService\\.ts$\tNewsletterSend', 'X1\t#12\tx\\.ts$\tThing')
    );
    expect(problems.filter((p) => p.startsWith('UNOWNED'))).toHaveLength(2);
  });

  it('reports UNOWNED, AMBIGUOUS and STALE OWNER', () => {
    const problems = checkOwners(
      entries,
      owners(
        'N1\t16.8c\tnewsletterService\\.ts$\tNewsletterSend.*',
        'N2\t17.10\tnewsletterService\\.ts$\tNewsletterSendRequest',
        'G1\t#99\tnowhere\\.ts$\t.*'
      )
    );
    expect(problems).toEqual(
      expect.arrayContaining([
        expect.stringMatching(
          /^AMBIGUOUS +src\/services\/newsletterService\.ts +NewsletterSendRequest +matches groups N1, N2/
        ),
        expect.stringMatching(/^UNOWNED +src\/types\/x\.ts +Thing/),
        expect.stringMatching(/^STALE OWNER +group G1/),
      ])
    );
  });

  it('reports BAD OWNER for a bad owner, a missing column, a bad regex and a duplicate id', () => {
    const { groups, errors } = parseOwners(
      owners(
        'A\tsoon\tx\tY',
        'B\t17.10\tx',
        'C\texception: \tx\tY',
        'D\t#1\t(\tY',
        'E\t17.10\tx\tY',
        'E\t17.10\tx\tY'
      )
    );
    expect(groups.map((g) => g.id)).toEqual(['E']);
    expect(errors).toHaveLength(5);
    expect(errors.every((e) => e.startsWith('BAD OWNER'))).toBe(true);
  });
});

describe('run', () => {
  let dir: string;
  let out: string[];

  beforeEach(() => {
    dir = mkdtempSync(join(tmpdir(), 'api-type-copies-'));
    cpSync(fixtureRoot, dir, { recursive: true });
    out = [];
    vi.spyOn(console, 'log').mockImplementation((...args: unknown[]) => {
      out.push(args.join(' '));
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    rmSync(dir, { recursive: true, force: true });
  });

  const paths = () => ({
    root: dir,
    baselinePath: join(dir, 'baseline.txt'),
    ownersPath: join(dir, 'owners.txt'),
  });
  const OWNERS = 'F1\t#1\tcopies\\.ts$\t(EventResponse|OptionalName|PagedThing)\n';

  it('--update writes a sorted baseline and checks the owners', () => {
    writeFileSync(paths().ownersPath, OWNERS);
    expect(run(['--update'], paths())).toBe(0);
    const lines = readFileSync(paths().baselinePath, 'utf8')
      .split('\n')
      .filter((l) => l && !l.startsWith('#'));
    expect(lines).toEqual(ALL);
    expect(out.join('\n')).toContain('3 (2 interface(s), 1 object type(s)) in 1 file(s)');
  });

  it('--update fails when the new baseline has an unowned line', () => {
    expect(run(['--update'], paths())).toBe(1);
    expect(out.join('\n')).toMatch(/UNOWNED +src\/services\/copies\.ts +EventResponse/);
  });

  it('passes on a matching, owned baseline and prints its size', () => {
    writeFileSync(paths().ownersPath, OWNERS);
    writeFileSync(paths().baselinePath, ALL.join('\n') + '\n');
    expect(run([], paths())).toBe(0);
    expect(out.join('\n')).toContain('Baseline: 3.');
  });

  it('fails NEW for a copy added to the tree', () => {
    writeFileSync(paths().ownersPath, OWNERS);
    writeFileSync(paths().baselinePath, ALL.join('\n') + '\n');
    writeFileSync(join(dir, 'src/services/new.ts'), 'export interface UnionOne { x: 1 }\n');
    expect(run([], paths())).toBe(1);
    expect(out.join('\n')).toMatch(/NEW +src\/services\/new\.ts:1 +UnionOne/);
  });

  it('fails STALE when a baselined copy becomes an alias', () => {
    writeFileSync(paths().ownersPath, OWNERS);
    writeFileSync(paths().baselinePath, ALL.join('\n') + '\n');
    const file = join(dir, COPIES);
    writeFileSync(
      file,
      readFileSync(file, 'utf8').replace(
        /export interface EventResponse \{[^}]*\}/,
        "export type EventResponse = components['schemas']['EventResponse'];"
      )
    );
    expect(run([], paths())).toBe(1);
    expect(out.join('\n')).toMatch(/STALE +src\/services\/copies\.ts +EventResponse/);
  });

  it('fails when no schema names are found', () => {
    rmSync(join(dir, 'src/types/generated'), { recursive: true });
    expect(run([], paths())).toBe(1);
    expect(out.join('\n')).toContain('generate:api-types');
  });
});
