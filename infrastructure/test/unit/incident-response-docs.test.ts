import * as fs from 'fs';
import * as path from 'path';

/**
 * #1003: incident-response AC14 (runbooks) and AC15 (post-mortem process).
 *
 * AC13 (PagerDuty) and AC16 (StatusPage) were dropped as over-specified — see
 * docs/operations/runbooks.md for the scope decision.
 *
 * These replace four `describe.skip` blocks in test/e2e/monitoring/incident-response.spec.ts
 * that asserted auto-remediation Lambdas which were never built. Those blocks were also
 * un-assertable by construction: `expect(true).toBe(true)` in three places and
 * `expect(requiredSections.length).toBe(6)` on an array literal declared three lines above.
 *
 * These run in `test/unit`, so unlike the e2e suite they actually execute in CI, and they need
 * no AWS credentials.
 */

const REPO_ROOT = path.resolve(__dirname, '../../..');
const LIB_DIR = path.join(REPO_ROOT, 'infrastructure/lib');
const RUNBOOKS = path.join(REPO_ROOT, 'docs/operations/runbooks.md');
const POST_MORTEM_MD = path.join(REPO_ROOT, 'docs/operations/post-mortem-template.md');
const POST_MORTEM_FORM = path.join(REPO_ROOT, '.github/ISSUE_TEMPLATE/post-mortem.yml');

/** Every .ts file under infrastructure/lib. */
function sourceFiles(dir: string): string[] {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      return sourceFiles(full);
    }
    return entry.isFile() && entry.name.endsWith('.ts') ? [full] : [];
  });
}

/**
 * The distinctive part of a declared alarm name: everything after the
 * `batbern-${environment}-` prefix, with any remaining interpolation (the service name in
 * per-service alarms) removed. `batbern-${env}-${svc}-High-CPU` -> `High-CPU`.
 */
function declaredAlarmSuffixes(): string[] {
  const found = new Set<string>();
  for (const file of sourceFiles(LIB_DIR)) {
    const src = fs.readFileSync(file, 'utf8');
    for (const match of src.matchAll(/alarmName:\s*`([^`]+)`/g)) {
      const suffix = match[1]
        .replace(/^batbern-\$\{[^}]*\}-/, '')
        .replace(/\$\{[^}]*\}-?/g, '')
        .trim();
      if (suffix.length > 0) {
        found.add(suffix);
      }
    }
  }
  return [...found].sort();
}

describe('Incident runbooks (AC14)', () => {
  test('should_exist_when_repositoryCheckedOut', () => {
    expect(fs.existsSync(RUNBOOKS)).toBe(true);
  });

  test('should_findAlarmsInCdkSource_when_scanningLib', () => {
    // Guards the scanner itself: if the alarmName declaration style changes, the coverage
    // test below would silently pass against an empty list.
    expect(declaredAlarmSuffixes().length).toBeGreaterThanOrEqual(20);
  });

  test('should_documentEveryDeclaredAlarm_when_runbooksRead', () => {
    const runbook = fs.readFileSync(RUNBOOKS, 'utf8');
    const undocumented = declaredAlarmSuffixes().filter((s) => !runbook.includes(s));

    expect(undocumented).toEqual([]);
  });

  test('should_nameTheKillSwitch_when_triageLoopMisbehaves', () => {
    const runbook = fs.readFileSync(RUNBOOKS, 'utf8');

    expect(runbook).toContain('CLAUDE_TRIAGE_ENABLED=false');
  });
});

describe('Post-mortem process (AC15)', () => {
  // The six sections AC15 specified.
  const REQUIRED_SECTIONS = [
    'incident_summary',
    'timeline',
    'root_cause',
    'resolution',
    'action_items',
    'lessons_learned',
  ];

  test('should_exposeAllRequiredSections_when_issueFormRendered', () => {
    const form = fs.readFileSync(POST_MORTEM_FORM, 'utf8');
    const missing = REQUIRED_SECTIONS.filter((s) => !form.includes(`id: ${s}`));

    expect(missing).toEqual([]);
  });

  test('should_requireEveryRequiredSection_when_issueFormSubmitted', () => {
    const form = fs.readFileSync(POST_MORTEM_FORM, 'utf8');

    // An optional field is a field that gets left blank. Every specified section is required.
    const requiredCount = (form.match(/required:\s*true/g) ?? []).length;
    expect(requiredCount).toBeGreaterThanOrEqual(REQUIRED_SECTIONS.length);
  });

  test('should_documentAllRequiredSections_when_templateRead', () => {
    const template = fs.readFileSync(POST_MORTEM_MD, 'utf8');
    const headings = (template.match(/^##\s+(.+)$/gm) ?? []).map((h) =>
      h
        .replace(/^##\s+/, '')
        .toLowerCase()
        .replace(/[^a-z]+/g, '_')
    );
    const missing = REQUIRED_SECTIONS.filter((s) => !headings.some((h) => h.includes(s)));

    expect(missing).toEqual([]);
  });

  test('should_requireActionItemsToBeTracked_when_templateRead', () => {
    const template = fs.readFileSync(POST_MORTEM_MD, 'utf8');

    // The failure mode a post-mortem process dies of: follow-ups that live only in the document.
    expect(template).toMatch(/owner/i);
    expect(template).toMatch(/issue/i);
  });
});
