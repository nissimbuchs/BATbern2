import fs from 'fs-extra';
import os from 'os';
import path from 'path';
import { globSync } from 'glob';
import { defaultRunner, failureReason } from './command-runner.js';

/**
 * SAST (dependency vulnerabilities) source for the reports dashboard (#1226).
 *
 * CI stopped producing Trivy SARIF in March 2026 (53acd452), so the scan runs here, on every
 * reports build, against the tree the preceding `make build` left behind:
 *
 *   1. `trivy fs`     on the repo root: lockfiles (package-lock.json, ...). Trivy's fs mode does
 *                     NOT open JAR files, so on its own it misses every Java runtime dependency.
 *   2. `trivy rootfs` on the built service jars (`*\/build/libs`, `services/*\/build/libs`):
 *                     reads the nested BOOT-INF/lib jars of the Spring Boot fat jars.
 *
 * Both run in Docker (pinned image), as the calling user, with the repo mounted read-only, so no
 * root-owned files appear. Without Docker nothing is produced and SAST is reported as missing,
 * never as "0 issues".
 */

export const TRIVY_IMAGE = 'aquasec/trivy:0.58.1';
export const SAST_MARKER = 'sast-source.json';
export const FS_SARIF = 'trivy-results.sarif';
export const JARS_SARIF = 'trivy-jars.sarif';

// Directories the lockfile scan skips. `**/build` holds generated OpenAPI pom.xml files that are
// not what ships; the shipped dependencies are covered by the jar scan.
const FS_SKIP_DIRS = ['**/node_modules', '.snapshots', '**/build', '**/cdk.out', '.git'];

async function isWritableTree(dir) {
  try {
    await fs.ensureDir(dir);
    await fs.access(dir, fs.constants.W_OK);
    for (const sub of ['db', 'java-db', 'fanal']) {
      const p = path.join(dir, sub);
      if (!(await fs.pathExists(p))) continue;
      await fs.access(p, fs.constants.W_OK);
      for (const f of await fs.readdir(p)) await fs.access(path.join(p, f), fs.constants.W_OK);
    }
    return true;
  } catch {
    return false;
  }
}

function findJarDirs(baseDir) {
  const dirs = globSync(['*/build/libs', 'services/*/build/libs'], { cwd: baseDir });
  return dirs
    .filter(d => globSync('*.jar', { cwd: path.join(baseDir, d) }).length > 0)
    .sort();
}

async function writeMarker(outDir, data) {
  await fs.writeJson(path.join(outDir, SAST_MARKER), data, { spaces: 2 });
}

/**
 * @returns {Promise<{status: string, measuredAt: string|null, origin: string, note?: string}>}
 *   a source hint for ReportAggregator (sources.sast)
 */
export async function runTrivyScan({
  baseDir,
  runner = defaultRunner,
  image = TRIVY_IMAGE,
  cacheDir = path.join(os.homedir(), '.cache', 'trivy'),
  uid = process.getuid ? process.getuid() : 1000,
  gid = process.getgid ? process.getgid() : 1000,
  now = () => new Date(),
  log = console,
  env = process.env
}) {
  const outDir = path.join(baseDir, 'security-reports');
  const origin = `${image} (fs: lockfiles, rootfs: service jars)`;
  log.log(`Step 0b: SAST — Trivy dependency scan (${image})...`);

  if (env.PROJECTDOC_SKIP_SAST) {
    log.warn('  ⚠️  PROJECTDOC_SKIP_SAST set — SAST not measured\n');
    return { status: 'missing', measuredAt: null, origin, note: 'skipped (PROJECTDOC_SKIP_SAST set)' };
  }

  // Old results must never be read as current: remove them before anything can fail.
  await fs.ensureDir(outDir);
  for (const f of await fs.readdir(outDir)) {
    if (/^trivy-.*\.sarif$/.test(f) || f === SAST_MARKER) await fs.remove(path.join(outDir, f));
  }

  const version = runner('docker', ['version', '--format', '{{.Server.Version}}']);
  if (version.status !== 0 || version.error) {
    const note = `Docker unavailable (${failureReason(version)}) — SAST not measured`;
    log.warn(`  ⚠️  ${note}\n`);
    return { status: 'missing', measuredAt: null, origin, note };
  }

  let cache = cacheDir;
  if (!(await isWritableTree(cache))) {
    const fallback = `${cacheDir}-${uid}`;
    log.warn(`  ⚠️  ${cacheDir} is not writable by uid ${uid} (left root-owned by an earlier run?).`);
    log.warn(`      Using ${fallback}. To reuse the old cache: sudo chown -R ${uid}:${gid} ${cacheDir}`);
    cache = fallback;
    await fs.ensureDir(cache);
  }

  const dockerBase = (extraMounts) => [
    'run', '--rm',
    '--user', `${uid}:${gid}`,
    '-e', 'HOME=/tmp',
    ...extraMounts.flatMap(m => ['-v', m]),
    '-v', `${cache}:/cache`,
    '-v', `${outDir}:/out`,
    image
  ];
  const common = ['--cache-dir', '/cache', '--scanners', 'vuln', '--no-progress', '--timeout', '20m', '--format', 'sarif'];

  const measuredAt = now().toISOString();

  // 1. Lockfiles
  const fsArgs = [
    ...dockerBase([`${baseDir}:/repo:ro`]),
    'fs', ...common,
    ...FS_SKIP_DIRS.flatMap(d => ['--skip-dirs', d]),
    '--output', `/out/${FS_SARIF}`,
    '/repo'
  ];
  const fsRun = runner('docker', fsArgs, { stdio: ['ignore', 'pipe', 'pipe'] });
  if (fsRun.status !== 0 || fsRun.error || !(await fs.pathExists(path.join(outDir, FS_SARIF)))) {
    const note = `trivy fs scan failed (${failureReason(fsRun)})`;
    log.warn(`  ⚠️  ${note}\n`);
    await fs.remove(path.join(outDir, FS_SARIF));
    return { status: 'missing', measuredAt: null, origin, note };
  }
  log.log(`  ✅ Lockfile scan → security-reports/${FS_SARIF}`);

  // 2. Built jars
  const jarDirs = findJarDirs(baseDir);
  let status = 'fresh';
  let note;
  if (jarDirs.length === 0) {
    status = 'partial';
    note = 'no built service jars found (*/build/libs) — Java runtime dependencies not scanned; run make build first';
  } else {
    const mounts = jarDirs.map(d => `${path.join(baseDir, d)}:/jars/${d}:ro`);
    const jarRun = runner('docker', [
      ...dockerBase(mounts),
      'rootfs', ...common,
      '--output', `/out/${JARS_SARIF}`,
      '/jars'
    ], { stdio: ['ignore', 'pipe', 'pipe'] });
    if (jarRun.status !== 0 || jarRun.error || !(await fs.pathExists(path.join(outDir, JARS_SARIF)))) {
      await fs.remove(path.join(outDir, JARS_SARIF));
      status = 'partial';
      note = `jar scan failed (${failureReason(jarRun)}) — Java runtime dependencies not scanned`;
    } else {
      log.log(`  ✅ Jar scan (${jarDirs.length} dirs) → security-reports/${JARS_SARIF}`);
    }
  }
  if (note) log.warn(`  ⚠️  ${note}`);
  log.log('');

  const result = { status, measuredAt, origin, jarDirs };
  if (note) result.note = note;
  await writeMarker(outDir, result);
  return result;
}
