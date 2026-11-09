/**
 * The shrinking exclude list of the test type-check. `run` compiles the fixture tree
 * `scripts/__fixtures__/test-typecheck/` (excluded from Vitest and Prettier, never part of the app).
 */
import { describe, expect, it } from 'vitest';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { compare, failingFiles, run } from './check-test-typecheck.ts';

const fixtureRoot = join(dirname(fileURLToPath(import.meta.url)), '__fixtures__', 'test-typecheck');
const always = () => true;

describe('compare', () => {
  it('passes when every excluded test still fails to compile', () => {
    expect(compare(['src/a.test.ts'], new Set(['src/a.test.ts']), always)).toEqual({
      stale: [],
      notTests: [],
    });
  });

  it('reports an excluded test that compiles now, or is gone, as STALE', () => {
    const verdict = compare(['src/a.test.ts', 'src/gone.test.tsx'], new Set<string>(), (p) =>
      p.endsWith('a.test.ts')
    );
    expect(verdict.stale).toEqual(['src/a.test.ts', 'src/gone.test.tsx']);
  });

  it('refuses production files in the exclude list', () => {
    const failing = new Set(['src/x.ts', 'src/test/setup.ts', 'src/c/__tests__/d.tsx']);
    const verdict = compare(
      ['src/x.ts', 'src/test/setup.ts', 'src/c/__tests__/d.tsx'],
      failing,
      always
    );
    expect(verdict.notTests).toEqual(['src/x.ts']);
  });
});

describe('run on the fixture tree', () => {
  it('finds the file that does not compile', () => {
    expect([...failingFiles(fixtureRoot)]).toEqual(['src/broken.test.ts']);
  });

  it('fails on the excluded test that compiles now', () => {
    const lines: string[] = [];
    expect(run(fixtureRoot, (l) => lines.push(l))).toBe(1);
    expect(lines).toEqual([
      'STALE     src/fixed.test.ts: compiles now (or is gone); remove it from "exclude" in tsconfig.test-harness.json',
    ]);
  });
});
