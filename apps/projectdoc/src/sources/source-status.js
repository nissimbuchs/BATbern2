import fs from 'fs-extra';

/**
 * Freshness bookkeeping for every data source of the reports dashboard (#1226).
 *
 * Status vocabulary:
 *   fresh           measured within the source's threshold
 *   stale           data present but older than the threshold, of unknown age, or from a fallback
 *   partial         measured now, but only part of the source ran (e.g. Trivy without the jars)
 *   missing         no data; the dashboard shows "no data", never 0
 *   not-configured  deliberately not set up (e.g. SonarCloud without SONAR_TOKEN)
 */

export const SOURCE_KEYS = [
  'javaTests', 'frontendTests', 'javaCoverage', 'frontendCoverage', 'infraCoverage',
  'sast', 'dast', 'quality', 'loc', 'sonar'
];

export const SOURCE_LABELS = {
  javaTests: 'Java tests',
  frontendTests: 'Frontend tests',
  javaCoverage: 'Java coverage',
  frontendCoverage: 'Frontend coverage',
  infraCoverage: 'Infrastructure (CDK) coverage',
  sast: 'SAST (Trivy)',
  dast: 'DAST (OWASP ZAP)',
  quality: 'Code quality (Checkstyle)',
  loc: 'Lines of code (cloc)',
  sonar: 'SonarCloud'
};

const toIso = (d) => {
  if (!d) return null;
  const date = d instanceof Date ? d : new Date(d);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
};

/**
 * Classify one source.
 * @param {Object} p
 * @param {boolean} p.present       data exists
 * @param {Date|string|null} p.measuredAt when the data was produced
 * @param {Date} p.now              report time
 * @param {number} p.maxAgeHours    freshness threshold
 * @param {string} [p.forceStatus]  set by a fetcher that knows better (fallback → stale, scan failed → missing)
 * @param {boolean} [p.configured]  false → not-configured
 */
export function classifySource({ present, measuredAt = null, now, maxAgeHours, forceStatus, configured = true, origin = null, note = null }) {
  const iso = toIso(measuredAt);
  let status;
  if (!configured) {
    status = 'not-configured';
  } else if (!present) {
    status = 'missing';
  } else if (forceStatus && forceStatus !== 'fresh') {
    status = forceStatus === 'not-configured' ? 'missing' : forceStatus;
  } else if (!iso) {
    status = 'stale';
  } else {
    const ageHours = (now.getTime() - new Date(iso).getTime()) / 3600000;
    status = ageHours > maxAgeHours ? 'stale' : 'fresh';
  }
  const hasData = status !== 'missing' && status !== 'not-configured';
  const entry = { status, measuredAt: hasData ? iso : null, origin };
  if (note) entry.note = note;
  return entry;
}

/**
 * @param {Object} sources map key → classified entry
 * @returns {{dataComplete: boolean, incomplete: Array, notConfigured: Array}}
 */
export function summarizeSources(sources) {
  const incomplete = [];
  const notConfigured = [];
  for (const [key, s] of Object.entries(sources)) {
    const item = { key, label: SOURCE_LABELS[key] || key, status: s.status, measuredAt: s.measuredAt || null, note: s.note || null };
    if (s.status === 'not-configured') notConfigured.push(item);
    else if (s.status !== 'fresh') incomplete.push(item);
  }
  return { dataComplete: incomplete.length === 0, incomplete, notConfigured };
}

/**
 * Newest modification time of a set of files (absolute paths).
 * @returns {Promise<Date|null>}
 */
export async function newestMtime(files) {
  let newest = null;
  for (const f of files) {
    try {
      const { mtime } = await fs.stat(f);
      if (!newest || mtime > newest) newest = mtime;
    } catch {
      // vanished between glob and stat: ignore
    }
  }
  return newest;
}
