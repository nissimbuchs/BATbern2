import fs from 'fs-extra';
import os from 'os';
import path from 'path';
import { globSync } from 'glob';
import { defaultRunner, failureReason } from './command-runner.js';

/**
 * DAST (OWASP ZAP) source for the reports dashboard (#1226).
 *
 * Primary: the latest *successful* run of the weekly `security-scan.yml` on the default branch.
 * Its combined `zap-reports` artifact (zap-*.json + zap-logs/*.log) is downloaded with `gh` and
 * replaces security-reports/zap-*.json and security-reports/zap-logs/ on every build, so a scan
 * target that was removed from the workflow also disappears from the dashboard.
 *
 * Fallbacks, both reported as stale with their own date:
 *   1. S3 (s3://batbern-content-staging/ci-reports/zap/, last written 2026-03-05)
 *   2. whatever is already in security-reports/
 *
 * A marker security-reports/dast-source.json records where the files came from and when the
 * scan ran (the run's createdAt), because file mtimes only tell when they were downloaded.
 */

export const DAST_MARKER = 'dast-source.json';

const isZapJson = (name) => /^zap-.*\.json$/.test(name);

async function cleanZapFiles(outDir) {
  if (!(await fs.pathExists(outDir))) return;
  for (const f of await fs.readdir(outDir)) {
    if (isZapJson(f)) await fs.remove(path.join(outDir, f));
  }
  await fs.remove(path.join(outDir, 'zap-logs'));
}

/** Copy zap-*.json and zap-logs/*.log found anywhere below srcDir into outDir. */
async function installFrom(srcDir, outDir) {
  const jsons = globSync('**/zap-*.json', { cwd: srcDir, absolute: true });
  const logs = globSync('**/zap-logs/*.log', { cwd: srcDir, absolute: true });
  if (jsons.length === 0) return 0;
  await cleanZapFiles(outDir);
  await fs.ensureDir(path.join(outDir, 'zap-logs'));
  for (const f of jsons) await fs.copy(f, path.join(outDir, path.basename(f)));
  for (const f of logs) await fs.copy(f, path.join(outDir, 'zap-logs', path.basename(f)));
  return jsons.length;
}

async function fromGitHub({ baseDir, outDir, runner, workflow, branch, log }) {
  const list = runner('gh', [
    'run', 'list', '--workflow', workflow, '--branch', branch, '--status', 'success',
    '--limit', '1', '--json', 'databaseId,createdAt'
  ], { cwd: baseDir });
  if (list.status !== 0 || list.error) {
    return { ok: false, reason: `gh run list failed (${failureReason(list)})` };
  }
  let runs;
  try {
    runs = JSON.parse(list.stdout || '[]');
  } catch {
    return { ok: false, reason: 'gh run list returned no JSON' };
  }
  if (!runs.length) return { ok: false, reason: `no successful ${workflow} run on ${branch}` };
  const { databaseId, createdAt } = runs[0];

  const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'projectdoc-zap-'));
  try {
    // Prefer the combined artifact; fall back to the per-scan ones (same content, one per scan)
    const attempts = [['-n', 'zap-reports'], ['-p', 'zap-json-*']];
    for (const sel of attempts) {
      const dir = path.join(tmp, sel[0] === '-n' ? 'combined' : 'per-scan');
      await fs.ensureDir(dir);
      const dl = runner('gh', ['run', 'download', String(databaseId), ...sel, '-D', dir], { cwd: baseDir });
      if (dl.status !== 0 || dl.error) {
        log.warn(`  ⚠️  gh run download ${sel.join(' ')} failed (${failureReason(dl)})`);
        continue;
      }
      const count = await installFrom(dir, outDir);
      if (count > 0) {
        return { ok: true, runId: databaseId, createdAt, count, artifact: sel.join(' ') };
      }
    }
    return { ok: false, reason: `run ${databaseId} has no downloadable ZAP JSON artifacts (expired?)` };
  } finally {
    await fs.remove(tmp);
  }
}

async function fromS3({ outDir, s3Fetch }) {
  const objects = await s3Fetch();
  const files = (objects || []).filter(o => o.key && !o.key.endsWith('/'));
  if (!files.some(o => isZapJson(path.basename(o.key)))) return { ok: false, reason: 'no ZAP reports in S3' };
  await cleanZapFiles(outDir);
  let newest = null;
  for (const o of files) {
    const target = path.join(outDir, o.key);
    await fs.outputFile(target, o.body);
    const lm = o.lastModified ? new Date(o.lastModified) : null;
    if (lm && (!newest || lm > newest)) newest = lm;
  }
  return { ok: true, measuredAt: newest ? newest.toISOString() : null };
}

/** Date of the local ZAP files: marker first, then the reports' own @generated, newest wins. */
async function localDate(outDir) {
  const markerPath = path.join(outDir, DAST_MARKER);
  if (await fs.pathExists(markerPath)) {
    try {
      const m = await fs.readJson(markerPath);
      if (m.measuredAt) return { measuredAt: m.measuredAt, origin: m.origin || null };
    } catch { /* fall through */ }
  }
  let newest = null;
  for (const f of (await fs.readdir(outDir)).filter(isZapJson)) {
    try {
      const generated = (await fs.readJson(path.join(outDir, f)))['@generated'];
      const d = generated ? new Date(generated) : null;
      if (d && !Number.isNaN(d.getTime()) && (!newest || d > newest)) newest = d;
    } catch { /* unparseable report: no date */ }
  }
  return { measuredAt: newest ? newest.toISOString() : null, origin: null };
}

/**
 * @returns {Promise<{origin: string, measuredAt: string|null, forceStatus?: string, note?: string, runId?: number}>}
 *   a source hint for ReportAggregator (sources.dast)
 */
export async function fetchZapReports({
  baseDir,
  runner = defaultRunner,
  s3Fetch = null,
  workflow = 'security-scan.yml',
  branch = 'develop',
  log = console
}) {
  const outDir = path.join(baseDir, 'security-reports');
  await fs.ensureDir(outDir);
  log.log(`Step 0a: Fetching DAST (ZAP) reports from the latest successful ${workflow} run on ${branch}...`);

  const gh = await fromGitHub({ baseDir, outDir, runner, workflow, branch, log });
  if (gh.ok) {
    const hint = {
      origin: `GitHub Actions run ${gh.runId} (${workflow} on ${branch}, artifact ${gh.artifact})`,
      measuredAt: gh.createdAt,
      runId: gh.runId
    };
    await fs.writeJson(path.join(outDir, DAST_MARKER), hint, { spaces: 2 });
    log.log(`  ✅ ${gh.count} ZAP report(s) from run ${gh.runId} (${gh.createdAt})\n`);
    return hint;
  }
  log.warn(`  ⚠️  ${gh.reason}`);
  const reasons = [gh.reason];

  if (s3Fetch) {
    try {
      const s3 = await fromS3({ outDir, s3Fetch });
      if (s3.ok) {
        const hint = {
          origin: 'S3 ci-reports/zap/ (fallback)',
          measuredAt: s3.measuredAt,
          forceStatus: 'stale',
          note: `GitHub artifacts unavailable: ${gh.reason}`
        };
        await fs.writeJson(path.join(outDir, DAST_MARKER), { origin: hint.origin, measuredAt: hint.measuredAt }, { spaces: 2 });
        log.warn(`  ⚠️  Using S3 fallback, data from ${s3.measuredAt} (marked stale)\n`);
        return hint;
      }
      reasons.push(s3.reason);
    } catch (err) {
      reasons.push(`S3: ${err.message}`);
    }
    log.warn(`  ⚠️  ${reasons[reasons.length - 1]}`);
  }

  const local = (await fs.readdir(outDir)).filter(isZapJson);
  if (local.length > 0) {
    const { measuredAt, origin } = await localDate(outDir);
    log.warn(`  ⚠️  Using ${local.length} local ZAP file(s) from ${measuredAt || 'an unknown date'} (marked stale)\n`);
    return {
      origin: `local security-reports/ (fallback${origin ? `, originally ${origin}` : ''})`,
      measuredAt,
      forceStatus: 'stale',
      note: `could not check for a newer scan: ${reasons.join('; ')}`
    };
  }

  log.warn('  ⚠️  No DAST data anywhere — dashboard will show "no data"\n');
  return { origin: 'none', measuredAt: null, forceStatus: 'missing', note: reasons.join('; ') };
}
