import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseStringPromise } from 'xml2js';
import JUnitParser from '../src/utils/parsers/junit-parser.js';
import SarifParser from '../src/utils/parsers/sarif-parser.js';

// Shape of web-frontend/test-results/junit.xml when Vitest catches unhandled errors (#1225)
const VITEST_JUNIT = `<?xml version="1.0" encoding="UTF-8" ?>
<testsuites name="vitest tests" tests="7" failures="1" errors="2" time="1">
  <testsuite name="src/a.test.tsx" tests="5" failures="1" errors="0" skipped="1" time="0.5">
    <testcase classname="a" name="ok1" time="0"/>
    <testcase classname="a" name="ok2" time="0"/>
    <testcase classname="a" name="ok3" time="0"/>
    <testcase classname="a" name="bad" time="0"><failure message="expected 1"/></testcase>
    <testcase classname="a" name="skip" time="0"><skipped/></testcase>
  </testsuite>
  <testsuite name="vitest unhandled errors" tests="2" failures="0" errors="2" skipped="0" time="0">
    <testcase classname="vitest unhandled errors" name="Uncaught Exception: Maximum update depth exceeded." time="0">
      <error message="Maximum update depth exceeded." type="Error">stack</error>
    </testcase>
    <testcase classname="vitest unhandled errors" name="Uncaught Exception: Maximum update depth exceeded." time="0">
      <error message="Maximum update depth exceeded." type="Error">stack</error>
    </testcase>
  </testsuite>
</testsuites>`;

test('JUnit: Vitest unhandled errors are counted separately, not as tests or failures', async () => {
  const xml = await parseStringPromise(VITEST_JUNIT);
  const data = JUnitParser.extractTestData(xml, 'junit.xml');
  assert.equal(data.summary.totalTests, 5);
  assert.equal(data.summary.passed, 3);
  assert.equal(data.summary.failures, 1);
  assert.equal(data.summary.errors, 0);
  assert.equal(data.summary.skipped, 1);
  assert.equal(data.summary.unhandledErrors, 2);
  assert.equal(data.summary.successRate, 75);

  const summary = JUnitParser.calculateSummary([{ module: 'web-frontend', results: data }]);
  assert.equal(summary.overallStats.unhandledErrors, 2);
  assert.equal(summary.overallStats.errors, 0);
  assert.equal(summary.overallStats.failures, 1);
  // Failed tests list holds real failures only; unhandled errors have their own list
  assert.deepEqual(summary.failedTests.map(t => t.testName), ['bad']);
  assert.equal(summary.unhandledErrors.length, 2);
  assert.equal(summary.unhandledErrors[0].message, 'Maximum update depth exceeded.');
});

test('JUnit: a Gradle report without unhandled errors reports unhandledErrors 0', async () => {
  const xml = await parseStringPromise(
    '<testsuite name="X" tests="2" failures="0" errors="1" skipped="0" time="1">' +
    '<testcase name="a"/><testcase name="b"><error message="boom"/></testcase></testsuite>');
  const data = JUnitParser.extractTestData(xml, 'TEST-X.xml');
  assert.equal(data.summary.errors, 1);
  assert.equal(data.summary.unhandledErrors, 0);
});

function trivyRun(results, rules) {
  return { runs: [{ tool: { driver: { name: 'Trivy', version: '0.58.1', rules } }, results }] };
}

test('SARIF: Trivy severity is read from the rule tags, so CRITICAL is not folded into high', () => {
  const rules = [
    { id: 'CVE-1', properties: { tags: ['vulnerability', 'security', 'CRITICAL'] } },
    { id: 'CVE-2', properties: { tags: ['vulnerability', 'security', 'HIGH'] } },
    { id: 'CVE-3', properties: { tags: ['vulnerability', 'security', 'MEDIUM'] } },
    { id: 'CVE-4', properties: { tags: ['vulnerability', 'security', 'LOW'] } }
  ];
  const results = [
    { ruleId: 'CVE-1', level: 'error', message: { text: 'x' } },
    { ruleId: 'CVE-2', level: 'error', message: { text: 'x' } },
    { ruleId: 'CVE-3', level: 'warning', message: { text: 'x' } },
    { ruleId: 'CVE-4', level: 'note', message: { text: 'x' } }
  ];
  const findings = SarifParser.extractFindings(trivyRun(results, rules));
  assert.deepEqual(findings.summary.bySeverity, { critical: 1, high: 1, medium: 1, low: 1, info: 0 });
});

test('SARIF: falls back to "Severity:" in the message, then to the SARIF level', () => {
  const results = [
    { ruleId: 'A', level: 'error', message: { text: 'Package: x\nSeverity: CRITICAL\n' } },
    { ruleId: 'B', level: 'error', message: { text: 'no severity here' } }
  ];
  const findings = SarifParser.extractFindings(trivyRun(results, []));
  assert.equal(findings.summary.bySeverity.critical, 1);
  assert.equal(findings.summary.bySeverity.high, 1);
});
