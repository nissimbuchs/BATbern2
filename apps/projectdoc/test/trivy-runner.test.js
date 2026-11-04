import { test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'fs-extra';
import os from 'os';
import path from 'path';
import { runTrivyScan, TRIVY_IMAGE } from '../src/sources/trivy-runner.js';

let baseDir;
let cacheDir;
const NOW = new Date('2026-11-04T12:00:00Z');
const silent = { log() {}, warn() {} };

beforeEach(async () => {
  baseDir = await fs.mkdtemp(path.join(os.tmpdir(), 'projectdoc-trivy-'));
  cacheDir = path.join(baseDir, '.cache-trivy');
});
afterEach(async () => {
  await fs.chmod(path.join(cacheDir), 0o755).catch(() => {});
  await fs.remove(baseDir);
});

/** Fake docker: `version` succeeds (unless told otherwise); `run` writes the --output file into the /out mount. */
function fakeDocker({ dockerAvailable = true, failScan = null } = {}) {
  const calls = [];
  const runner = (cmd, args) => {
    calls.push([cmd, ...args]);
    if (cmd !== 'docker') return { status: 1, stdout: '', stderr: '' };
    if (args[0] === 'version') {
      return dockerAvailable ? { status: 0, stdout: '29.0.0', stderr: '' } : { status: 1, stdout: '', stderr: 'Cannot connect to the Docker daemon' };
    }
    if (args[0] === 'run') {
      const mode = args[args.indexOf(TRIVY_IMAGE) + 1];
      if (failScan === mode) return { status: 2, stdout: '', stderr: `${mode} scan failed` };
      const outMount = args.find(a => a.endsWith(':/out'));
      const hostOut = outMount.slice(0, -':/out'.length);
      const output = args[args.indexOf('--output') + 1].replace('/out/', '');
      fs.outputJsonSync(path.join(hostOut, output), { version: '2.1.0', runs: [{ tool: { driver: { name: 'Trivy' } }, results: [] }] });
      return { status: 0, stdout: '', stderr: '' };
    }
    return { status: 1, stdout: '', stderr: '' };
  };
  return { runner, calls };
}

async function withJars() {
  await fs.outputFile(path.join(baseDir, 'services/event-management-service/build/libs/ems-1.0.0.jar'), 'jar');
  await fs.outputFile(path.join(baseDir, 'api-gateway/build/libs/api-gateway-1.0.0.jar'), 'jar');
  await fs.outputFile(path.join(baseDir, 'shared-kernel/build/libs/README.txt'), 'no jar here');
}

test('runs a pinned, read-only, non-root fs scan of the repo and a rootfs scan of the built jars', async () => {
  await withJars();
  const { runner, calls } = fakeDocker();

  const result = await runTrivyScan({ baseDir, runner, cacheDir, uid: 1000, gid: 1000, now: () => NOW, log: silent });

  assert.equal(result.status, 'fresh');
  assert.equal(result.measuredAt, NOW.toISOString());
  const runs = calls.filter(c => c[0] === 'docker' && c[1] === 'run');
  assert.equal(runs.length, 2);

  const [fsScan, jarScan] = runs;
  assert.ok(fsScan.includes(TRIVY_IMAGE));
  assert.equal(TRIVY_IMAGE, 'aquasec/trivy:0.58.1');
  assert.ok(fsScan.includes('1000:1000'), 'runs as the calling user so no root-owned files appear');
  assert.ok(fsScan.includes(`${baseDir}:/repo:ro`), 'repo is mounted read-only');
  assert.ok(fsScan.includes(`${cacheDir}:/cache`));
  assert.ok(fsScan.includes(`${path.join(baseDir, 'security-reports')}:/out`));
  const fsArgs = fsScan.slice(fsScan.indexOf(TRIVY_IMAGE) + 1);
  assert.equal(fsArgs[0], 'fs');
  assert.deepEqual(fsArgs.slice(fsArgs.indexOf('--scanners'), fsArgs.indexOf('--scanners') + 2), ['--scanners', 'vuln']);
  assert.ok(fsArgs.join(' ').includes('--skip-dirs **/node_modules'));
  assert.ok(fsArgs.join(' ').includes('--skip-dirs .snapshots'));
  assert.ok(fsArgs.includes('sarif'));
  assert.ok(fsArgs.includes('/out/trivy-results.sarif'));

  const jarArgs = jarScan.slice(jarScan.indexOf(TRIVY_IMAGE) + 1);
  assert.equal(jarArgs[0], 'rootfs');
  assert.ok(jarScan.includes(`${path.join(baseDir, 'services/event-management-service/build/libs')}:/jars/services/event-management-service/build/libs:ro`));
  assert.ok(jarScan.includes(`${path.join(baseDir, 'api-gateway/build/libs')}:/jars/api-gateway/build/libs:ro`));
  assert.ok(!jarScan.some(a => a.includes('shared-kernel')), 'directories without jars are not mounted');
  assert.ok(jarArgs.includes('/out/trivy-jars.sarif'));

  assert.ok(await fs.pathExists(path.join(baseDir, 'security-reports/trivy-results.sarif')));
  assert.ok(await fs.pathExists(path.join(baseDir, 'security-reports/trivy-jars.sarif')));
  const marker = await fs.readJson(path.join(baseDir, 'security-reports/sast-source.json'));
  assert.equal(marker.status, 'fresh');
  assert.equal(marker.measuredAt, NOW.toISOString());
});

test('Docker unavailable: SAST is missing (not measured), never an empty result', async () => {
  await fs.outputFile(path.join(baseDir, 'security-reports/trivy-results.sarif'), '{"runs":[]}');
  const { runner, calls } = fakeDocker({ dockerAvailable: false });

  const result = await runTrivyScan({ baseDir, runner, cacheDir, uid: 1000, gid: 1000, now: () => NOW, log: silent });

  assert.equal(result.status, 'missing');
  assert.match(result.note, /Docker/);
  assert.equal(calls.filter(c => c[1] === 'run').length, 0);
  assert.equal(await fs.pathExists(path.join(baseDir, 'security-reports/trivy-results.sarif')), false,
    'an old SARIF must not survive and be read as current');
});

test('no built jars: lockfiles are scanned but the result is partial, not fresh', async () => {
  const { runner } = fakeDocker();

  const result = await runTrivyScan({ baseDir, runner, cacheDir, uid: 1000, gid: 1000, now: () => NOW, log: silent });

  assert.equal(result.status, 'partial');
  assert.match(result.note, /jar/i);
});

test('a failed jar scan leaves the lockfile result and reports partial', async () => {
  await withJars();
  const { runner } = fakeDocker({ failScan: 'rootfs' });

  const result = await runTrivyScan({ baseDir, runner, cacheDir, uid: 1000, gid: 1000, now: () => NOW, log: silent });

  assert.equal(result.status, 'partial');
  assert.ok(await fs.pathExists(path.join(baseDir, 'security-reports/trivy-results.sarif')));
  assert.equal(await fs.pathExists(path.join(baseDir, 'security-reports/trivy-jars.sarif')), false);
});

test('a failed lockfile scan is missing', async () => {
  await withJars();
  const { runner } = fakeDocker({ failScan: 'fs' });

  const result = await runTrivyScan({ baseDir, runner, cacheDir, uid: 1000, gid: 1000, now: () => NOW, log: silent });

  assert.equal(result.status, 'missing');
  assert.match(result.note, /fs scan failed/);
});

test('a cache dir the user cannot write (left root-owned by an earlier run) is replaced by a per-user one', async (t) => {
  if (process.getuid && process.getuid() === 0) return t.skip('root can write anywhere');
  await fs.ensureDir(path.join(cacheDir, 'db'));
  await fs.chmod(path.join(cacheDir, 'db'), 0o555);
  const { runner, calls } = fakeDocker();

  await runTrivyScan({ baseDir, runner, cacheDir, uid: 1000, gid: 1000, now: () => NOW, log: silent });

  const fsScan = calls.find(c => c[1] === 'run');
  assert.ok(fsScan.includes(`${cacheDir}-1000:/cache`));
  await fs.chmod(path.join(cacheDir, 'db'), 0o755);
});

test('PROJECTDOC_SKIP_SAST skips the scan and reports SAST as missing', async () => {
  const { runner, calls } = fakeDocker();

  const result = await runTrivyScan({ baseDir, runner, cacheDir, uid: 1000, gid: 1000, now: () => NOW, log: silent, env: { PROJECTDOC_SKIP_SAST: '1' } });

  assert.equal(result.status, 'missing');
  assert.match(result.note, /PROJECTDOC_SKIP_SAST/);
  assert.equal(calls.length, 0);
});
