import { test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'fs-extra';
import os from 'os';
import path from 'path';
import { fetchZapReports } from '../src/sources/zap-fetcher.js';

let baseDir;
let outDir;

beforeEach(async () => {
  baseDir = await fs.mkdtemp(path.join(os.tmpdir(), 'projectdoc-zap-'));
  outDir = path.join(baseDir, 'security-reports');
});
afterEach(async () => {
  await fs.remove(baseDir);
});

const silent = { log() {}, warn() {} };
const zapJson = (generated = 'Mon, 2 Nov 2026 03:10:22') => JSON.stringify({ '@generated': generated, site: [] });

/**
 * Fake `gh`: answers `run list` with one run and materialises the given artifacts on `run download`.
 * artifacts: { artifactName: { 'relative/file': 'content' } }
 */
function fakeGh({ runs = [{ databaseId: 42, createdAt: '2026-11-02T09:56:58Z' }], artifacts = {}, listStatus = 0 } = {}) {
  const calls = [];
  const runner = (cmd, args) => {
    calls.push([cmd, ...args]);
    if (cmd !== 'gh') return { status: 127, stdout: '', stderr: 'not found' };
    if (args[0] === 'run' && args[1] === 'list') {
      return listStatus === 0
        ? { status: 0, stdout: JSON.stringify(runs), stderr: '' }
        : { status: listStatus, stdout: '', stderr: 'gh: not logged in' };
    }
    if (args[0] === 'run' && args[1] === 'download') {
      const dir = args[args.indexOf('-D') + 1];
      const nameIdx = args.indexOf('-n');
      const patternIdx = args.indexOf('-p');
      let selected = [];
      if (nameIdx >= 0) selected = artifacts[args[nameIdx + 1]] ? [[null, artifacts[args[nameIdx + 1]]]] : [];
      if (patternIdx >= 0) {
        const prefix = args[patternIdx + 1].replace('*', '');
        selected = Object.entries(artifacts).filter(([n]) => n.startsWith(prefix));
      }
      if (selected.length === 0) return { status: 1, stdout: '', stderr: 'no artifact matches' };
      for (const [name, files] of selected) {
        // gh extracts a single -n artifact into the dir itself, several -p artifacts into dir/<name>/
        const target = name ? path.join(dir, name) : dir;
        for (const [rel, content] of Object.entries(files)) {
          fs.outputFileSync(path.join(target, rel), content);
        }
      }
      return { status: 0, stdout: '', stderr: '' };
    }
    return { status: 1, stdout: '', stderr: 'unexpected' };
  };
  return { runner, calls };
}

test('downloads the combined artifact of the latest successful run and records its date', async () => {
  const { runner, calls } = fakeGh({
    artifacts: {
      'zap-reports': {
        'zap-events.json': zapJson(),
        'zap-frontend.json': zapJson(),
        'zap-logs/zap-events.log': 'PASS: x [1]'
      }
    }
  });

  const result = await fetchZapReports({ baseDir, runner, log: silent });

  const list = calls.find(c => c[1] === 'run' && c[2] === 'list');
  assert.ok(list.includes('security-scan.yml'));
  assert.ok(list.includes('success'));
  assert.ok(list.includes('develop'), 'restricted to the default branch');
  assert.equal(result.measuredAt, '2026-11-02T09:56:58Z');
  assert.equal(result.forceStatus, undefined);
  assert.match(result.origin, /run 42/);
  assert.deepEqual((await fs.readdir(outDir)).filter(f => f.startsWith('zap-') && f.endsWith('.json')).sort(),
    ['zap-events.json', 'zap-frontend.json']);
  assert.ok(await fs.pathExists(path.join(outDir, 'zap-logs', 'zap-events.log')));
  const marker = await fs.readJson(path.join(outDir, 'dast-source.json'));
  assert.equal(marker.measuredAt, '2026-11-02T09:56:58Z');
  assert.equal(marker.runId, 42);
});

test('always replaces local ZAP files, so removed scan targets disappear', async () => {
  await fs.outputFile(path.join(outDir, 'zap-retired-target.json'), zapJson('Thu, 5 Mar 2026 03:00:00'));
  await fs.outputFile(path.join(outDir, 'zap-logs', 'zap-retired-target.log'), 'old');
  await fs.outputFile(path.join(outDir, 'trivy-results.sarif'), '{}');
  const { runner } = fakeGh({ artifacts: { 'zap-reports': { 'zap-events.json': zapJson() } } });

  await fetchZapReports({ baseDir, runner, log: silent });

  assert.equal(await fs.pathExists(path.join(outDir, 'zap-retired-target.json')), false);
  assert.equal(await fs.pathExists(path.join(outDir, 'zap-logs', 'zap-retired-target.log')), false);
  assert.ok(await fs.pathExists(path.join(outDir, 'zap-events.json')));
  assert.ok(await fs.pathExists(path.join(outDir, 'trivy-results.sarif')), 'non-ZAP files are left alone');
});

test('falls back to the per-scan zap-json-* artifacts when the combined one is absent', async () => {
  const { runner } = fakeGh({
    artifacts: {
      'zap-json-events': { 'zap-events.json': zapJson(), 'zap-logs/zap-events.log': 'log' },
      'zap-json-frontend': { 'zap-frontend.json': zapJson() }
    }
  });

  const result = await fetchZapReports({ baseDir, runner, log: silent });

  assert.equal(result.measuredAt, '2026-11-02T09:56:58Z');
  assert.ok(await fs.pathExists(path.join(outDir, 'zap-events.json')));
  assert.ok(await fs.pathExists(path.join(outDir, 'zap-frontend.json')));
  assert.ok(await fs.pathExists(path.join(outDir, 'zap-logs', 'zap-events.log')));
});

test('gh unavailable: falls back to S3, cleans first, and marks the data stale with the S3 date', async () => {
  await fs.outputFile(path.join(outDir, 'zap-retired.json'), zapJson());
  const { runner } = fakeGh({ listStatus: 4 });
  const s3Fetch = async () => [
    { key: 'zap-events.json', body: Buffer.from(zapJson()), lastModified: new Date('2026-03-05T10:00:00Z') },
    { key: 'zap-logs/zap-events.log', body: Buffer.from('log'), lastModified: new Date('2026-03-04T10:00:00Z') }
  ];

  const result = await fetchZapReports({ baseDir, runner, s3Fetch, log: silent });

  assert.equal(result.forceStatus, 'stale');
  assert.equal(result.measuredAt, '2026-03-05T10:00:00.000Z');
  assert.match(result.origin, /S3/);
  assert.match(result.note, /not logged in/);
  assert.equal(await fs.pathExists(path.join(outDir, 'zap-retired.json')), false);
  assert.ok(await fs.pathExists(path.join(outDir, 'zap-logs', 'zap-events.log')));
});

test('gh and S3 unavailable: keeps local files but marks them stale with the recorded date', async () => {
  await fs.outputFile(path.join(outDir, 'zap-events.json'), zapJson());
  await fs.writeJson(path.join(outDir, 'dast-source.json'), { origin: 'GitHub Actions run 7', measuredAt: '2026-10-26T08:07:05Z' });
  const { runner } = fakeGh({ listStatus: 4 });
  const s3Fetch = async () => { throw new Error('no credentials'); };

  const result = await fetchZapReports({ baseDir, runner, s3Fetch, log: silent });

  assert.equal(result.forceStatus, 'stale');
  assert.equal(result.measuredAt, '2026-10-26T08:07:05Z');
  assert.match(result.origin, /local/);
  assert.ok(await fs.pathExists(path.join(outDir, 'zap-events.json')));
});

test('local fallback without a marker reads the date from the ZAP report itself', async () => {
  await fs.outputFile(path.join(outDir, 'zap-events.json'), zapJson('Thu, 5 Mar 2026 03:10:22'));
  const { runner } = fakeGh({ listStatus: 4 });

  const result = await fetchZapReports({ baseDir, runner, log: silent });

  assert.equal(result.forceStatus, 'stale');
  assert.equal(new Date(result.measuredAt).getUTCMonth(), 2);
});

test('nothing anywhere: DAST is missing', async () => {
  const { runner } = fakeGh({ runs: [] });

  const result = await fetchZapReports({ baseDir, runner, log: silent });

  assert.equal(result.forceStatus, 'missing');
  assert.equal(result.measuredAt, null);
});

test('a gh binary that is not installed is a fallback, not a crash', async () => {
  const runner = () => ({ status: null, error: new Error('spawn gh ENOENT'), stdout: '', stderr: '' });

  const result = await fetchZapReports({ baseDir, runner, log: silent });

  assert.equal(result.forceStatus, 'missing');
  assert.match(result.note, /ENOENT/);
});
