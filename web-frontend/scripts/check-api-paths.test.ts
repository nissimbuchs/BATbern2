/**
 * Story 17.3: the frontend API path scan. Fixture specs (shared with the matcher tests) and
 * fixture sources under `scripts/__fixtures__/src`.
 */
import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadSpecIndex } from '../src/test/contract/specMatcher.ts';
import {
  listSourceFiles,
  maskComments,
  parseAllowList,
  runCheck,
  scanSource,
} from './check-api-paths.ts';

const here = dirname(fileURLToPath(import.meta.url));
const fixtureRoot = join(here, '__fixtures__');
const index = loadSpecIndex(join(here, '..', 'src', 'test', 'contract', '__fixtures__', 'specs'));

const fixture = (file: string) => ({
  file,
  source: readFileSync(join(fixtureRoot, file), 'utf8'),
});
const eventService = fixture('src/services/eventService.ts');
const driftService = fixture('src/services/driftService.ts');
const useThing = fixture('src/hooks/useThing.ts');

const check = (
  sources: Array<{ file: string; source: string }>,
  allowListText = '',
  baselineText = ''
) => runCheck({ sources, index, allowListText, baselineText });

describe('scanSource', () => {
  const calls = scanSource(eventService.file, eventService.source);
  const urlsOf = (line: number) => calls.find((c) => c.line === line)?.urls;
  const lineOf = (needle: string) =>
    eventService.source.split('\n').findIndex((l) => l.includes(needle)) + 1;

  it('skips calls inside comments', () => {
    expect(calls.some((c) => c.expression.includes('commented'))).toBe(false);
  });

  it('resolves a same-file path helper (arrow) with its parameter as a wildcard', () => {
    expect(urlsOf(lineOf('registrations(eventCode)'))).toEqual(['/events/{*}/registrations']);
  });

  it('resolves a same-file function helper and a class constant', () => {
    expect(urlsOf(lineOf('schedulePath(eventCode))'))).toEqual(['/events/{*}/publish/schedule']);
    expect(urlsOf(lineOf('this.basePath'))).toEqual(['/users/me']);
  });

  it('resolves a constant with both branches of a conditional', () => {
    expect(urlsOf(lineOf('apiClient.get(url)'))).toEqual(['/events/current', '/events/BATbern1/']);
  });

  it('ignores a presigned upload URL and an absolute URL', () => {
    const ignored = calls.filter((c) => c.ignored).map((c) => c.expression);
    expect(ignored).toEqual(['uploadUrl', "'https://example.com/absolute'"]);
  });

  it('masks comments without touching strings', () => {
    expect(maskComments("const a = '//x'; // gone")).toBe("const a = '//x';        ");
    expect(maskComments('const t = `a${b /* c */}d`;')).toBe(
      `const t = \`a\${b ${' '.repeat(7)}}d\`;`
    );
  });
});

describe('runCheck', () => {
  it('passes the clean fixture and prints the summary line', () => {
    const r = check([eventService]);
    expect(r.problems).toEqual([]);
    expect(r.exitCode).toBe(0);
    expect(r.summary).toBe('Frontend API calls: 4 resolved, 4 match the spec, 0 allow-listed.');
  });

  it('fails the drift fixture with one line per case', () => {
    const r = check([driftService]);
    expect(r.exitCode).toBe(1);
    const text = r.problems.join('\n');
    // getPublishPreview repro
    expect(text).toMatch(/NOT IN SPEC .*GET \/events\/\{\*\}\/publish\/\{\*\}\/preview/);
    // wrong method
    expect(text).toMatch(/NOT IN SPEC .*DELETE .*method DELETE is not defined/);
    // unknown literal query parameter
    expect(text).toMatch(/NOT IN SPEC .*'foo' is not declared/);
    // exceeded maximum (#1072)
    expect(text).toMatch(/NOT IN SPEC .*limit=1000.*exceeds the maximum 100/);
    // unresolvable
    expect(text).toMatch(/UNRESOLVED .*POST endpoint/);
    // bare axios with a computed argument that is not an upload URL
    expect(text).toMatch(/UNRESOLVED .*GET target/);
    // an unknown part glued into a segment (`/events{*}`) is not a resolved path
    expect(text).toMatch(/UNRESOLVED .*GET `\$\{EVENTS_API_PATH\}\$\{suffix\}`/);
  });

  it('accepts an unresolvable call that is allow-listed with a reason', () => {
    const r = check(
      [driftService],
      'src/services/driftService.ts:POST:endpoint  # caller passes the endpoint'
    );
    expect(r.problems.some((p) => p.includes('POST endpoint'))).toBe(false);
    expect(r.summary).toContain('1 allow-listed');
  });

  it('fails a stale allow-list entry', () => {
    const r = check([eventService], 'src/services/eventService.ts:GET:gone  # was here once');
    expect(r.exitCode).toBe(1);
    expect(r.problems.join('\n')).toMatch(/STALE .*allow-list line 1/);
  });

  it('rejects an allow-list line without a reason', () => {
    expect(parseAllowList('src/a.ts:GET:url').errors).toHaveLength(1);
    expect(parseAllowList('src/a.ts:GET:url  # ').errors).toHaveLength(1);
    expect(parseAllowList('# comment\n\nsrc/a.ts:GET:url  # why').entries).toHaveLength(1);
  });

  it('fails a new file outside the service layer', () => {
    const r = check([eventService, useThing]);
    expect(r.exitCode).toBe(1);
    expect(r.problems.join('\n')).toMatch(/OUT OF LAYER src\/hooks\/useThing\.ts/);
  });

  it('accepts a baselined file outside the service layer', () => {
    const r = check([eventService, useThing], '', 'src/hooks/useThing.ts  # frozen 17.3');
    expect(r.problems).toEqual([]);
  });

  it('fails a stale layer baseline entry', () => {
    const r = check([eventService], '', 'src/hooks/useThing.ts  # frozen 17.3');
    expect(r.exitCode).toBe(1);
    expect(r.problems.join('\n')).toMatch(
      /STALE .*layer baseline line 1: src\/hooks\/useThing\.ts/
    );
  });
});

describe('listSourceFiles', () => {
  it('lists source files relative to the root', () => {
    expect(listSourceFiles(fixtureRoot)).toEqual([
      'src/hooks/useThing.ts',
      'src/services/driftService.ts',
      'src/services/eventService.ts',
    ]);
  });
});
