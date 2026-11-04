import { test } from 'node:test';
import assert from 'node:assert/strict';
import { classifySource, summarizeSources, SOURCE_KEYS } from '../src/sources/source-status.js';

const NOW = new Date('2026-11-04T12:00:00Z');
const hoursAgo = (h) => new Date(NOW.getTime() - h * 3600 * 1000);

test('a present source measured within its threshold is fresh', () => {
  const s = classifySource({ present: true, measuredAt: hoursAgo(2), now: NOW, maxAgeHours: 24, origin: 'file' });
  assert.equal(s.status, 'fresh');
  assert.equal(s.measuredAt, hoursAgo(2).toISOString());
  assert.equal(s.origin, 'file');
});

test('a present source older than its threshold is stale', () => {
  const s = classifySource({ present: true, measuredAt: hoursAgo(25), now: NOW, maxAgeHours: 24 });
  assert.equal(s.status, 'stale');
});

test('a present source without a measurement date is stale, never fresh', () => {
  const s = classifySource({ present: true, measuredAt: null, now: NOW, maxAgeHours: 24 });
  assert.equal(s.status, 'stale');
  assert.equal(s.measuredAt, null);
});

test('an absent source is missing', () => {
  const s = classifySource({ present: false, measuredAt: hoursAgo(1), now: NOW, maxAgeHours: 24 });
  assert.equal(s.status, 'missing');
  assert.equal(s.measuredAt, null, 'no data, no measurement date');
});

test('an unconfigured source is not-configured even when absent', () => {
  const s = classifySource({ present: false, configured: false, measuredAt: NOW, now: NOW, maxAgeHours: 24 });
  assert.equal(s.status, 'not-configured');
  assert.equal(s.measuredAt, null);
});

test('forceStatus overrides the age check (fallback data is stale even when recent)', () => {
  const s = classifySource({ present: true, measuredAt: hoursAgo(1), now: NOW, maxAgeHours: 24, forceStatus: 'stale' });
  assert.equal(s.status, 'stale');
});

test('forceStatus cannot make absent data look present', () => {
  const s = classifySource({ present: false, now: NOW, maxAgeHours: 24, forceStatus: 'stale' });
  assert.equal(s.status, 'missing');
});

test('summarizeSources: complete only when every configured source is fresh', () => {
  const sources = Object.fromEntries(SOURCE_KEYS.map(k => [k, { status: 'fresh', measuredAt: NOW.toISOString() }]));
  sources.sonar = { status: 'not-configured', measuredAt: null };
  let summary = summarizeSources(sources);
  assert.equal(summary.dataComplete, true);
  assert.deepEqual(summary.incomplete, []);
  assert.deepEqual(summary.notConfigured.map(s => s.key), ['sonar']);

  sources.sast = { status: 'missing', measuredAt: null, note: 'Docker unavailable' };
  sources.dast = { status: 'stale', measuredAt: hoursAgo(300).toISOString() };
  summary = summarizeSources(sources);
  assert.equal(summary.dataComplete, false);
  assert.deepEqual(summary.incomplete.map(s => [s.key, s.status]), [['sast', 'missing'], ['dast', 'stale']]);
  assert.equal(summary.incomplete[0].label, 'SAST (Trivy)');
  assert.equal(summary.incomplete[0].note, 'Docker unavailable');
});

test('summarizeSources treats partial as incomplete', () => {
  const summary = summarizeSources({ sast: { status: 'partial', measuredAt: NOW.toISOString() } });
  assert.equal(summary.dataComplete, false);
});
