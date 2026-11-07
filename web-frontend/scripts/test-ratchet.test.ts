/**
 * Story 17.0a: the test fidelity ratchet. Fixture repository under
 * `scripts/__fixtures__/test-ratchet/repo` (excluded from Vitest and Prettier: its files are
 * read as text, never run).
 */
import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  classifySpecifier,
  compare,
  formatBaseline,
  listTestFiles,
  maskJava,
  parseBaseline,
  scanFrontendTest,
  scanJavaTest,
  scanRepo,
  scanSources,
  type Count,
} from './test-ratchet.ts';

const repo = join(dirname(fileURLToPath(import.meta.url)), '__fixtures__', 'test-ratchet', 'repo');
const read = (file: string) => readFileSync(join(repo, file), 'utf8');

const EVENT_PAGE = 'web-frontend/src/components/events/EventPage.test.tsx';
const FORMAT = 'web-frontend/src/utils/format.test.ts';
const DEMO_IT = 'services/demo-service/src/test/java/ch/demo/DemoIntegrationTest.java';
const READ_ONLY_IT = 'services/demo-service/src/test/java/ch/demo/ReadOnlyIT.java';
const SLUG_CONFIG = 'shared-kernel/src/testFixtures/java/ch/shared/TestSlugConfig.java';
const UNIT = 'services/demo-service/src/test/java/ch/demo/SomeUnitTest.java';

const asObject = (m: Map<string, number>) => Object.fromEntries([...m].sort());

describe('listTestFiles', () => {
  it('reads frontend *.test.ts(x) and *.spec.ts(x), and Java under src/test*, nothing else', () => {
    expect(listTestFiles(repo)).toEqual([
      'services/demo-service/src/test/java/ch/demo/DemoIntegrationTest.java',
      'services/demo-service/src/test/java/ch/demo/MethodLevelIntegrationTest.java',
      'services/demo-service/src/test/java/ch/demo/ReadOnlyIT.java',
      'services/demo-service/src/test/java/ch/demo/SomeUnitTest.java',
      SLUG_CONFIG,
      EVENT_PAGE,
      'web-frontend/src/hooks/clean.test.ts',
      'web-frontend/src/pages/Home.spec.tsx',
      FORMAT,
    ]);
  });
});

describe('classifySpecifier', () => {
  it.each([
    ['@/services/eventService', 'fe-mock-services'],
    ['@services/auth/authService', 'fe-mock-services'],
    ['@/hooks/useEvents', 'fe-mock-hooks'],
    ['@hooks/useAuth', 'fe-mock-hooks'],
    ['@/components/shared/Loader', 'fe-mock-components'],
    ['@components/user/UserProfileTab/UserProfileTab', 'fe-mock-components'],
    ['@pages/Home', 'fe-mock-components'],
    ['@/contexts/useConfig', 'fe-mock-hooks'],
    ['@/stores/uiStore', null],
    ['@/utils/date/dateFormat', null],
    ['react-i18next', null],
    ['aws-amplify/auth', null],
    ['@tanstack/react-query', null],
    ['@mui/material', null],
  ])('alias or package %s -> %s', (specifier, expected) => {
    expect(classifySpecifier(specifier, EVENT_PAGE)).toBe(expected);
  });

  it.each([
    ['../services/api/apiClient', 'fe-mock-services'],
    ['../hooks/usePaging', 'fe-mock-hooks'],
    ['./useClock', 'fe-mock-hooks'],
    ['./DateBadge/index', 'fe-mock-components'],
    ['../pages/Home', 'fe-mock-components'],
    ['./helpers', null],
    ['../../outside/Thing', null],
  ])('relative %s from src/utils -> %s', (specifier, expected) => {
    expect(classifySpecifier(specifier, FORMAT)).toBe(expected);
  });

  it('resolves a relative path inside src/components by directory', () => {
    expect(classifySpecifier('./tabUtils', EVENT_PAGE)).toBe('fe-mock-components');
  });
});

describe('scanFrontendTest', () => {
  it('counts own-code mocks per category, multi-line calls included', () => {
    expect(asObject(scanFrontendTest(EVENT_PAGE, read(EVENT_PAGE)))).toEqual({
      'fe-mock-components': 4,
      'fe-mock-hooks': 2,
      'fe-mock-services': 3,
    });
  });

  it('classifies relative specifiers and vi.mock(import(...))', () => {
    expect(asObject(scanFrontendTest(FORMAT, read(FORMAT)))).toEqual({
      'fe-mock-components': 1,
      'fe-mock-hooks': 3,
      'fe-mock-services': 1,
    });
  });

  it('does not count vi.mock in line, block or JSDoc comments, nor vi.mocked', () => {
    const src = [
      "// vi.mock('@/services/a');",
      "/* vi.mock('@/services/b'); */",
      "/** vi.mock('@/services/c') */",
      'vi.mocked(x);',
    ].join('\n');
    expect(scanFrontendTest(EVENT_PAGE, src).size).toBe(0);
  });

  it('counts vi.doMock like vi.mock', () => {
    const src = "vi.doMock('@/hooks/useSpeakerPool', () => ({}));\nvi.doMock('react-i18next');";
    expect(asObject(scanFrontendTest(EVENT_PAGE, src))).toEqual({ 'fe-mock-hooks': 1 });
  });

  it('does not count boundary mocks', () => {
    const src = "vi.mock('aws-amplify');\nvi.mock('aws-amplify/auth');\nvi.mock('msw');";
    expect(scanFrontendTest(EVENT_PAGE, src).size).toBe(0);
  });
});

describe('scanJavaTest', () => {
  it('splits @MockitoBean into S2S clients and in-process beans, skips the boundary', () => {
    // UserApiClient + CompanyServiceClient (with @Qualifier); EmailService (answers = ...).
    // SesClient, S3Client (qualified), SecretsManagerClient (AWS SDK import) and Clock are boundary.
    expect(asObject(scanJavaTest(read(DEMO_IT)))).toEqual({
      'be-mock-in-process': 1,
      'be-mock-s2s-client': 2,
      'be-transactional-it': 1,
    });
  });

  it('ignores @MockitoBean and @Transactional in comments, Javadoc, strings and text blocks', () => {
    const masked = maskJava(read(DEMO_IT));
    expect(masked).not.toMatch(/inString|inTextBlock|commentedOut|Javadoc/);
    expect(masked.split('\n').length).toBe(read(DEMO_IT).split('\n').length);
  });

  it('counts a class-level @Transactional(readOnly = true) on an *IT class', () => {
    expect(asObject(scanJavaTest(read(READ_ONLY_IT)))).toEqual({ 'be-transactional-it': 1 });
  });

  it('does not count a method-level @Transactional', () => {
    const file = 'services/demo-service/src/test/java/ch/demo/MethodLevelIntegrationTest.java';
    expect(scanJavaTest(read(file)).size).toBe(0);
  });

  it('counts class-level @MockitoBean(types = ...) but not @Transactional on a unit test', () => {
    expect(asObject(scanJavaTest(read(UNIT)))).toEqual({
      'be-mock-in-process': 1,
      'be-mock-s2s-client': 1,
    });
  });

  it('reads testFixtures source sets too', () => {
    expect(asObject(scanJavaTest(read(SLUG_CONFIG)))).toEqual({ 'be-mock-in-process': 1 });
  });
});

describe('baseline', () => {
  const { files, counts } = scanRepo(repo);

  it('is sorted, tab-separated, without zero counts, and round-trips', () => {
    const text = formatBaseline(counts);
    const lines = text.split('\n').filter((l) => l && !l.startsWith('#'));
    expect(lines).toEqual([...lines].sort());
    expect(lines).toContain(`be-mock-s2s-client\t${DEMO_IT}\t2`);
    expect(lines.some((l) => l.includes('clean.test.ts'))).toBe(false);
    expect(parseBaseline(text)).toEqual({ counts, errors: [] });
  });

  it('rejects malformed lines, unknown categories and duplicates', () => {
    const { errors } = parseBaseline(
      [
        'fe-mock-services\ta.test.ts',
        'fe-mock-x\ta.test.ts\t1',
        'fe-mock-hooks\ta\t0',
        'fe-mock-hooks\tb\t1',
        'fe-mock-hooks\tb\t1',
      ].join('\n')
    );
    expect(errors).toHaveLength(4);
    expect(compare(counts, formatBaseline(counts) + 'garbage\n', files).exitCode).toBe(1);
  });
});

describe('compare (I/O matrix)', () => {
  const { files, counts } = scanRepo(repo);
  const baseline = formatBaseline(counts);
  const sourcesWith = (file: string, extra: string) =>
    files.map((f) => ({ file: f, source: read(f) + (f === file ? extra : '') }));

  it('Equal: exit 0 and one summary line with totals per category', () => {
    const result = compare(counts, baseline, files);
    expect(result.exitCode).toBe(0);
    expect(result.problems).toEqual([]);
    expect(result.summary).toBe(
      'Test ratchet: be-mock-in-process 3 files/3, be-mock-s2s-client 2 files/3, ' +
        'be-transactional-it 2 files/2, fe-mock-components 2 files/5, fe-mock-hooks 3 files/6, ' +
        'fe-mock-services 2 files/4.'
    );
  });

  it('Rise: a file gains a vi.mock of a service', () => {
    const raised = scanSources(sourcesWith(FORMAT, "\nvi.mock('@/services/x');\n"));
    const result = compare(raised, baseline, files);
    expect(result.exitCode).toBe(1);
    expect(result.problems[0]).toBe(
      `RAISED       fe-mock-services  ${FORMAT}  baseline 1, actual 2`
    );
    expect(result.problems.join('\n')).toContain('docs/architecture/coding-standards.md');
  });

  it('Rise: a new file with a counted mock', () => {
    const newFile = 'web-frontend/src/hooks/useNew.test.ts';
    const raised = [...counts, { category: 'fe-mock-services', file: newFile, count: 1 } as Count];
    const result = compare(raised, baseline, [...files, newFile]);
    expect(result.exitCode).toBe(1);
    expect(result.problems).toContain(
      `RAISED       fe-mock-services  ${newFile}  baseline 0, actual 1`
    );
  });

  it('Rise: a new @MockitoBean UserApiClient', () => {
    const extra = DEMO_IT; // appended text lands after the class; the scan still sees it
    const raised = scanSources(
      sourcesWith(extra, '\nclass X { @MockitoBean private UserApiClient another; }\n')
    );
    expect(compare(raised, baseline, files).problems[0]).toBe(
      `RAISED       be-mock-s2s-client  ${DEMO_IT}  baseline 2, actual 3`
    );
  });

  it('Fall without update: stale, and says to run --update', () => {
    const fallen = scanSources(
      files.map((f) => ({
        file: f,
        source: f === EVENT_PAGE ? read(f).replace("vi.mock('@/hooks/useEvents');", '') : read(f),
      }))
    );
    const result = compare(fallen, baseline, files);
    expect(result.exitCode).toBe(1);
    expect(result.problems[0]).toBe(
      `STALE        fe-mock-hooks  ${EVENT_PAGE}  baseline 2, actual 1`
    );
    expect(result.problems.join('\n')).toContain('--update');
  });

  it('Fall with update: exit 0', () => {
    const fallen = counts.filter((c) => c.file !== READ_ONLY_IT);
    expect(compare(fallen, formatBaseline(fallen), files).exitCode).toBe(0);
  });

  it('File deleted: stale, marked as gone', () => {
    const remaining = files.filter((f) => f !== READ_ONLY_IT);
    const after = counts.filter((c) => c.file !== READ_ONLY_IT);
    const result = compare(after, baseline, remaining);
    expect(result.exitCode).toBe(1);
    expect(result.problems[0]).toBe(
      `STALE        be-transactional-it  ${READ_ONLY_IT}  baseline 1, actual 0  (file gone)`
    );
  });

  it('Comment and boundary mocks added: no change, exit 0', () => {
    const extra =
      "\n// vi.mock('@/services/x');\n/** vi.mock('@/hooks/useX') */\nvi.mock('aws-amplify');\n";
    const same = scanSources(sourcesWith(FORMAT, extra));
    expect(compare(same, baseline, files).exitCode).toBe(0);
  });
});
