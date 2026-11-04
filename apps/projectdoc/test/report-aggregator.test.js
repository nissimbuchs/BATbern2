import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'fs-extra';
import os from 'os';
import path from 'path';
import ReportAggregator from '../src/aggregators/report-aggregator.js';
import ReportsBuilder from '../scripts/build-reports.js';
import reportsConfig from '../src/config/reports-config.js';

const NOW = new Date('2026-11-04T12:00:00Z');
const hoursAgo = (h) => new Date(NOW.getTime() - h * 3600 * 1000);
const quiet = { log: console.log, warn: console.warn, error: console.error };

const VITEST_JUNIT = `<?xml version="1.0" encoding="UTF-8" ?>
<testsuites name="vitest tests" tests="6" failures="0" errors="2">
  <testsuite name="src/a.test.tsx" tests="4" failures="0" errors="0" skipped="0" time="0.5">
    <testcase classname="a" name="t1"/><testcase classname="a" name="t2"/>
    <testcase classname="a" name="t3"/><testcase classname="a" name="t4"/>
  </testsuite>
  <testsuite name="vitest unhandled errors" tests="2" failures="0" errors="2" skipped="0" time="0">
    <testcase classname="vitest unhandled errors" name="Uncaught Exception: loop"><error message="loop"/></testcase>
    <testcase classname="vitest unhandled errors" name="Uncaught Exception: loop"><error message="loop"/></testcase>
  </testsuite>
</testsuites>`;

const lcov = (file, found, hit) =>
  `TN:\nSF:${file}\nFNF:1\nFNH:1\nLF:${found}\nLH:${hit}\nBRF:2\nBRH:1\nend_of_record\n`;

function configFor(baseDir, extra = {}) {
  return {
    ...reportsConfig,
    baseDir,
    loc: { ...reportsConfig.loc, enabled: false },
    sonarcloud: { ...reportsConfig.sonarcloud, enabled: true },
    now: () => NOW,
    env: {},               // no SONAR_TOKEN
    ...extra
  };
}

async function touch(file, date) {
  await fs.utimes(file, date, date);
}

let baseDir;
let reportData;
let outputDir;

before(async () => {
  console.log = () => {};
  console.warn = () => {};
  baseDir = await fs.mkdtemp(path.join(os.tmpdir(), 'projectdoc-agg-'));

  // Frontend tests + coverage: fresh. Infrastructure coverage: 2 days old (stale).
  const junit = path.join(baseDir, 'web-frontend/test-results/junit.xml');
  await fs.outputFile(junit, VITEST_JUNIT);
  await touch(junit, hoursAgo(1));
  const feLcov = path.join(baseDir, 'web-frontend/coverage/lcov.info');
  await fs.outputFile(feLcov, lcov('src/app.ts', 100, 80));
  await touch(feLcov, hoursAgo(1));
  const infraLcov = path.join(baseDir, 'infrastructure/coverage/lcov.info');
  await fs.outputFile(infraLcov, lcov('lib/stack.ts', 100, 90));
  await touch(infraLcov, hoursAgo(48));

  // No Java results, no Checkstyle, no SARIF, no ZAP files at all.
  const aggregator = new ReportAggregator(configFor(baseDir, {
    sourceHints: { sast: { status: 'missing', note: 'Docker unavailable', origin: 'trivy (docker)' } }
  }));
  reportData = await aggregator.aggregateAll();
});

after(async () => {
  Object.assign(console, quiet);
  await fs.remove(baseDir);
  if (outputDir) await fs.remove(outputDir);
});

test('every source gets a status, a measurement date and an origin', () => {
  const s = reportData.metadata.sources;
  assert.deepEqual(Object.keys(s).sort(),
    ['dast', 'frontendCoverage', 'frontendTests', 'infraCoverage', 'javaCoverage', 'javaTests', 'loc', 'quality', 'sast', 'sonar']);
  assert.equal(s.frontendTests.status, 'fresh');
  assert.equal(s.frontendTests.measuredAt, hoursAgo(1).toISOString());
  assert.equal(s.frontendCoverage.status, 'fresh');
  assert.equal(s.infraCoverage.status, 'stale');
  assert.equal(s.javaTests.status, 'missing');
  assert.equal(s.javaCoverage.status, 'missing');
  assert.equal(s.quality.status, 'missing');
  assert.equal(s.sast.status, 'missing');
  assert.equal(s.sast.note, 'Docker unavailable');
  assert.equal(s.dast.status, 'missing');
  assert.equal(s.sonar.status, 'not-configured');
  assert.equal(s.loc.status, 'not-configured');
});

test('missing sources produce null summary numbers, not zeros', () => {
  const { summary } = reportData;
  assert.equal(summary.security.totalIssues, null);
  assert.equal(summary.security.critical, null);
  assert.equal(summary.security.high, null);
  assert.equal(summary.quality.totalViolations, null);
  assert.equal(summary.quality.errors, null);
  assert.equal(summary.coverage.java, null);
  assert.equal(summary.coverage.frontend, 80);
  assert.equal(summary.coverage.infrastructure, 90);
  assert.equal(summary.coverage.overall, 80, 'overall is computed from the coverage that exists');
  assert.equal(summary.dast, null);
});

test('Vitest unhandled errors are reported separately from failed tests', () => {
  const { tests } = reportData.summary;
  assert.equal(tests.total, 4);
  assert.equal(tests.failed, 0);
  assert.equal(tests.unhandledErrors, 2);
  assert.equal(tests.successRate, 100);
});

test('incomplete data is never reported as passing', () => {
  const { summary } = reportData;
  assert.equal(summary.dataComplete, false);
  assert.ok(summary.incompleteSources.includes('sast'));
  assert.ok(summary.incompleteSources.includes('javaTests'));
  assert.ok(summary.incompleteSources.includes('infraCoverage'));
  assert.ok(!summary.incompleteSources.includes('sonar'));
  assert.notEqual(summary.healthStatus, 'passing');
});

test('determineHealthStatus ignores null metrics instead of treating them as 0', () => {
  const agg = new ReportAggregator(configFor(baseDir));
  assert.equal(agg.determineHealthStatus({
    testSuccessRate: 100, overallCoverage: null, criticalSecurity: null, highSecurity: null, qualityErrors: null, unhandledErrors: 0
  }), 'passing');
  assert.equal(agg.determineHealthStatus({
    testSuccessRate: 100, overallCoverage: 90, criticalSecurity: 1, highSecurity: 0, qualityErrors: 0, unhandledErrors: 0
  }), 'failing');
  assert.equal(agg.determineHealthStatus({
    testSuccessRate: 100, overallCoverage: 90, criticalSecurity: 0, highSecurity: 0, qualityErrors: 0, unhandledErrors: 3
  }), 'warning');
});

test('a fresh SARIF and ZAP source produce numbers and a DAST summary', async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'projectdoc-agg2-'));
  try {
    await fs.outputJson(path.join(dir, 'security-reports/trivy-results.sarif'), {
      runs: [{ tool: { driver: { name: 'Trivy', version: '0.58.1', rules: [
        { id: 'CVE-1', properties: { tags: ['CRITICAL'] } }] } },
      results: [{ ruleId: 'CVE-1', level: 'error', message: { text: 'x' } }] }]
    });
    await fs.outputJson(path.join(dir, 'security-reports/zap-events.json'), {
      '@generated': 'Mon, 2 Nov 2026 03:10:22',
      site: [{ '@name': 'https://api', alerts: [{ pluginid: '1', alert: 'X', riskcode: '2', instances: [] }] }]
    });
    const agg = new ReportAggregator(configFor(dir, {
      sourceHints: {
        sast: { status: 'fresh', measuredAt: hoursAgo(0.1).toISOString(), origin: 'trivy' },
        dast: { measuredAt: hoursAgo(50).toISOString(), origin: 'GitHub Actions run 42' }
      }
    }));
    const data = await agg.aggregateAll();
    assert.equal(data.metadata.sources.sast.status, 'fresh');
    assert.equal(data.summary.security.critical, 1);
    assert.equal(data.metadata.sources.dast.status, 'fresh', '50 h is within the 8-day DAST threshold');
    assert.equal(data.metadata.sources.dast.origin, 'GitHub Actions run 42');
    assert.equal(data.summary.dast.medium, 1);
    assert.equal(data.summary.dast.scansRun, 1);
  } finally {
    await fs.remove(dir);
  }
});

test('rendered pages say "no data" / "stale" instead of showing zeros', async () => {
  outputDir = await fs.mkdtemp(path.join(os.tmpdir(), 'projectdoc-out-'));
  const builder = new ReportsBuilder({ baseDir, outputDir });
  builder.config = { ...builder.config, history: { ...builder.config.history, enabled: false } };
  await builder.generatePages(reportData);

  const index = await fs.readFile(path.join(outputDir, 'index.html'), 'utf8');
  assert.match(index, /Incomplete data/);
  assert.match(index, /SAST \(Trivy\)[\s\S]*not measured|no data/i);
  assert.match(index, /errors \(unhandled\)/);
  assert.match(index, /stale since/);
  assert.match(index, /not configured/);
  assert.doesNotMatch(index, /No Issues/, 'security tile must not claim "No Issues" without a scan');

  const security = await fs.readFile(path.join(outputDir, 'security.html'), 'utf8');
  assert.match(security, /not measured/i);
  assert.match(security, /Docker unavailable/);

  const zap = await fs.readFile(path.join(outputDir, 'zap-report.html'), 'utf8');
  assert.match(zap, /no data/i);

  const coverage = await fs.readFile(path.join(outputDir, 'coverage.html'), 'utf8');
  assert.match(coverage, /Java: <span[^>]*>no data/);
  assert.match(coverage, /Infrastructure: 90%/);
});
