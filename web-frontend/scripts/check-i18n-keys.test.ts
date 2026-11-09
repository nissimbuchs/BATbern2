/**
 * The i18n key gate (Epic 17). Fixture frontend under `scripts/__fixtures__/i18n-keys` (excluded
 * from Vitest and Prettier: its files are read as text, never run).
 */
import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  compare,
  countsOf,
  formatBaseline,
  hasKey,
  type KeyOptions,
  listSources,
  loadBundles,
  parseBaseline,
  scanFrontend,
  scanSource,
} from './check-i18n-keys.ts';

const scripts = dirname(fileURLToPath(import.meta.url));
const fixture = join(scripts, '__fixtures__', 'i18n-keys');
const bundles = loadBundles(join(fixture, 'public', 'locales', 'en'));
const read = (file: string) => readFileSync(join(fixture, file), 'utf8');

describe('hasKey', () => {
  const common = bundles.get('common')!;

  it.each<[string, KeyOptions, boolean]>([
    ['actions.save', {}, true],
    // An object: i18next renders "key 'actions' returned an object instead of string".
    ['actions', {}, false],
    ['actions', { returnObjects: true }, true],
    ['actions.delete', {}, false],
    // Plural forms count only for a call that passes `count`.
    ['items', {}, false],
    ['items', { count: true }, true],
    ['item', { count: true }, false],
    // Context forms count only for a call that passes `context`; a literal one names the form.
    ['greeting', {}, false],
    ['greeting', { context: 'male' }, true],
    ['greeting', { context: 'female' }, false],
    ['greeting', { context: true }, true],
    ['flat.key.with.dots', {}, true], // a JSON key that contains dots
    ['flat.key', {}, false],
  ])('%s %o -> %s', (key, options, expected) => {
    expect(hasKey(common, key, options)).toBe(expected);
  });
});

describe('listSources', () => {
  it('reads src/**/*.ts(x) without tests, __tests__ and src/test', () => {
    expect(listSources(fixture)).toEqual([
      'src/components/Broken.tsx',
      'src/components/Clean.tsx',
      'src/components/helpers.ts',
      'src/components/mixed.ts',
    ]);
  });
});

describe('scanSource', () => {
  it('resolves namespaces, prefixes, options, plural and context forms; skips dynamic keys', () => {
    expect(
      scanSource('src/components/Clean.tsx', read('src/components/Clean.tsx'), bundles)
    ).toEqual([]);
  });

  it('reports a missing key with file, line and the namespaces looked in', () => {
    expect(
      scanSource('src/components/Broken.tsx', read('src/components/Broken.tsx'), bundles)
    ).toEqual([
      { file: 'src/components/Broken.tsx', line: 5, key: 'does.not.exist', namespaces: ['events'] },
      { file: 'src/components/Broken.tsx', line: 5, key: 'form.missing', namespaces: ['events'] },
      // A second binding of `t` in the same file holds from its position on.
      { file: 'src/components/Broken.tsx', line: 10, key: 'title', namespaces: ['common'] },
      // Plural and context forms need `count` / `context`; an object needs `returnObjects`.
      { file: 'src/components/Broken.tsx', line: 10, key: 'items', namespaces: ['common'] },
      { file: 'src/components/Broken.tsx', line: 10, key: 'greeting', namespaces: ['common'] },
      { file: 'src/components/Broken.tsx', line: 10, key: 'actions', namespaces: ['common'] },
    ]);
  });

  it('looks a key up in every namespace when its t has no binding in the file', () => {
    expect(
      scanSource('src/components/helpers.ts', read('src/components/helpers.ts'), bundles)
    ).toEqual([
      { file: 'src/components/helpers.ts', line: 4, key: 'nowhere.at.all', namespaces: [] },
    ]);
  });

  it('uses the any-namespace rule for a t that is a parameter of the enclosing function', () => {
    expect(scanSource('src/components/mixed.ts', read('src/components/mixed.ts'), bundles)).toEqual(
      [
        { file: 'src/components/mixed.ts', line: 13, key: 'nowhere.either', namespaces: [] },
        // Outside the helpers the next binding holds again.
        {
          file: 'src/components/mixed.ts',
          line: 18,
          key: 'actions.cancel',
          namespaces: ['events'],
        },
      ]
    );
  });

  it('does not count a call in a comment', () => {
    expect(scanSource('x.ts', "// t('gone')\n/* t('gone') */", bundles)).toEqual([]);
  });
});

describe('compare', () => {
  const { files, missing } = scanFrontend(fixture);
  const baseline = read('baseline.txt');

  it('reports RAISED with the missing keys of the file, and STALE for a file that is gone', () => {
    const result = compare(missing, baseline, files);

    expect(result.exitCode).toBe(1);
    expect(result.problems).toContain(
      'RAISED  src/components/Broken.tsx  baseline 2, actual 6; its missing keys:'
    );
    expect(result.problems.join('\n')).toContain(
      "src/components/Broken.tsx:5  'does.not.exist' not in en/events.json"
    );
    expect(result.problems).toContain(
      'STALE   src/components/Gone.tsx  baseline 1, actual 0  (file gone)'
    );
    expect(result.problems.some((p) => p.includes('helpers.ts  baseline'))).toBe(false);
  });

  it('reports STALE when a count fell', () => {
    const result = compare(
      missing,
      'src/components/Broken.tsx\t7\nsrc/components/helpers.ts\t1\nsrc/components/mixed.ts\t2\n',
      files
    );

    expect(result.exitCode).toBe(1);
    expect(result.problems[0]).toBe('STALE   src/components/Broken.tsx  baseline 7, actual 6');
  });

  it('passes on the baseline --update writes', () => {
    const written = formatBaseline(countsOf(missing));

    expect(parseBaseline(written).counts).toEqual([
      { file: 'src/components/Broken.tsx', count: 6 },
      { file: 'src/components/helpers.ts', count: 1 },
      { file: 'src/components/mixed.ts', count: 2 },
    ]);
    expect(compare(missing, written, files)).toMatchObject({ exitCode: 0, problems: [] });
  });

  it('rejects a malformed baseline line', () => {
    expect(
      parseBaseline('src/a.tsx\t0\nsrc/b.tsx\nsrc/a.tsx\t1\nsrc/a.tsx\t2\n').errors
    ).toHaveLength(3);
  });
});

describe('the real frontend', () => {
  it('is at its committed baseline', () => {
    const frontend = join(scripts, '..');
    const { files, missing } = scanFrontend(frontend);
    const result = compare(missing, read('../../i18n-keys-baseline.txt'), files);

    expect(result.problems).toEqual([]);
  });
});
