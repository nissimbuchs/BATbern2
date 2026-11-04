import { test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'fs-extra';
import os from 'os';
import path from 'path';
import HistoryManager from '../src/aggregators/history-manager.js';

let dir;
let historyFile;
const quiet = { log: console.log, warn: console.warn };

beforeEach(async () => {
  dir = await fs.mkdtemp(path.join(os.tmpdir(), 'projectdoc-history-'));
  historyFile = path.join(dir, 'history.json');
  console.log = () => {};
});
afterEach(async () => {
  console.log = quiet.log;
  await fs.remove(dir);
});

function report({ timestamp, commit = 'abc123', branch = 'develop', overall = 80, infrastructure = 90, security = 3 }) {
  return {
    metadata: { timestamp, buildNumber: 'local', buildId: 'local', branch, commit,
      sources: { sast: { status: security === null ? 'missing' : 'fresh', measuredAt: timestamp } } },
    summary: {
      healthStatus: 'warning',
      dataComplete: security !== null,
      incompleteSources: security === null ? ['sast'] : [],
      tests: { total: 10, passed: 10, failed: 0, skipped: 0, unhandledErrors: 0, successRate: 100 },
      coverage: { overall, java: overall, frontend: overall, infrastructure, target: 85 },
      security: { totalIssues: security, critical: security === null ? null : 0, high: security, medium: 0, low: 0 },
      dast: null,
      quality: { totalViolations: 0, errors: 0, warnings: 0 }
    },
    modules: []
  };
}

test('rebuilding the reports for the same commit and branch replaces the entry instead of adding one', async () => {
  const hm = new HistoryManager({ historyFile, maxHistoryEntries: 50 });
  await hm.saveToHistory(report({ timestamp: '2026-11-04T10:00:00Z', overall: 70 }));
  await hm.saveToHistory(report({ timestamp: '2026-11-04T12:00:00Z', overall: 75 }));

  const history = await hm.loadHistory();
  assert.equal(history.length, 1);
  assert.equal(history[0].summary.coverage.overall, 75);
  assert.equal(history[0].timestamp, '2026-11-04T12:00:00Z');
});

test('the same commit on another branch is a separate entry', async () => {
  const hm = new HistoryManager({ historyFile, maxHistoryEntries: 50 });
  await hm.saveToHistory(report({ timestamp: '2026-11-04T10:00:00Z', branch: 'develop' }));
  await hm.saveToHistory(report({ timestamp: '2026-11-04T11:00:00Z', branch: 'feature/x' }));
  await hm.saveToHistory(report({ timestamp: '2026-11-04T12:00:00Z', commit: 'def456' }));

  const history = await hm.loadHistory();
  assert.equal(history.length, 3);
  assert.equal(history[0].commit, 'def456');
});

test('the replaced entry moves to the top and an older duplicate deeper in the list is removed too', async () => {
  const hm = new HistoryManager({ historyFile, maxHistoryEntries: 50 });
  await hm.saveToHistory(report({ timestamp: '2026-11-01T10:00:00Z', commit: 'aaa' }));
  await hm.saveToHistory(report({ timestamp: '2026-11-02T10:00:00Z', commit: 'bbb' }));
  await hm.saveToHistory(report({ timestamp: '2026-11-03T10:00:00Z', commit: 'aaa' }));

  const history = await hm.loadHistory();
  assert.deepEqual(history.map(h => h.commit), ['aaa', 'bbb']);
});

test('history entries keep infrastructure coverage, DAST and data completeness', async () => {
  const hm = new HistoryManager({ historyFile, maxHistoryEntries: 50 });
  await hm.saveToHistory(report({ timestamp: '2026-11-04T10:00:00Z', security: null }));

  const [entry] = await hm.loadHistory();
  assert.equal(entry.summary.coverage.infrastructure, 90);
  assert.equal(entry.summary.security.totalIssues, null);
  assert.equal(entry.summary.dataComplete, false);
  assert.deepEqual(entry.summary.incompleteSources, ['sast']);
  assert.ok('dast' in entry.summary);
});

test('trend data carries an infrastructure series, null where an old entry has none', async () => {
  const hm = new HistoryManager({ historyFile, maxHistoryEntries: 50 });
  await hm.saveToHistory(report({ timestamp: '2026-11-03T10:00:00Z', commit: 'old' }));
  // Entries written before infrastructure coverage was collected have no such key
  const file = await fs.readJson(historyFile);
  delete file.history[0].summary.coverage.infrastructure;
  await fs.writeJson(historyFile, file);
  await hm.saveToHistory(report({ timestamp: '2026-11-04T10:00:00Z', commit: 'new', infrastructure: 91 }));

  const trends = await hm.getTrendData(8);
  assert.deepEqual(trends.coverage.infrastructure, [null, 91]);
});

test('comparison against a build with missing data yields null deltas, not fake changes', async () => {
  const hm = new HistoryManager({ historyFile, maxHistoryEntries: 50 });
  await hm.saveToHistory(report({ timestamp: '2026-11-03T10:00:00Z', commit: 'old', security: null }));

  const comparison = await hm.getComparison(report({ timestamp: '2026-11-04T10:00:00Z', commit: 'new', security: 5 }));
  assert.equal(comparison.changes.security.total, null);
  assert.equal(comparison.changes.coverage.overall, 0);
});

test('statistics ignore builds without a coverage figure', async () => {
  const hm = new HistoryManager({ historyFile, maxHistoryEntries: 50 });
  await hm.saveToHistory(report({ timestamp: '2026-11-03T10:00:00Z', commit: 'a', overall: null }));
  await hm.saveToHistory(report({ timestamp: '2026-11-04T10:00:00Z', commit: 'b', overall: 80 }));

  const stats = await hm.getStatistics();
  assert.equal(stats.coverage.min, 80);
  assert.equal(stats.coverage.average, 80);
});
