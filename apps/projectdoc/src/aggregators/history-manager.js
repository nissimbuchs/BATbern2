import fs from 'fs-extra';
import path from 'path';

/**
 * Manages historical report data for trend analysis
 */
export class HistoryManager {
  constructor(config) {
    this.config = config;
    this.historyFile = config.historyFile || 'dist/reports/data/history.json';
    this.maxHistoryEntries = config.maxHistoryEntries || 50;
  }

  /**
   * Load historical data
   * @returns {Promise<Array>} Historical entries
   */
  async loadHistory() {
    try {
      if (await fs.pathExists(this.historyFile)) {
        const data = await fs.readJson(this.historyFile);
        console.log(`  ℹ Loaded history from: ${this.historyFile} (${data.totalEntries || 0} entries)`);
        return data.history || [];
      } else {
        console.log(`  ℹ No existing history found at: ${this.historyFile}`);
      }
    } catch (error) {
      console.warn(`  ⚠️  Failed to load history from ${this.historyFile}:`, error.message);
    }

    return [];
  }

  /**
   * Save current report to history
   * @param {Object} report - Aggregated report data
   * @returns {Promise<void>}
   */
  async saveToHistory(report) {
    // Load existing history
    const history = await this.loadHistory();

    // Create history entry (extract only summary data, not full reports)
    const entry = this.createHistoryEntry(report);

    // Rebuilding the reports for the same commit on the same branch replaces that build's entry
    // instead of adding a second one (#1226). An unknown commit is never deduplicated.
    const sameBuild = (h) => entry.commit && entry.commit !== 'unknown'
      && h.commit === entry.commit && h.branch === entry.branch;
    const kept = history.filter(h => !sameBuild(h));
    if (kept.length !== history.length) {
      console.log(`  ℹ Replacing ${history.length - kept.length} history entry for ${entry.branch}@${String(entry.commit).slice(0, 8)}`);
    }
    history.length = 0;
    history.push(entry, ...kept);

    // Trim to max entries
    if (history.length > this.maxHistoryEntries) {
      history.splice(this.maxHistoryEntries);
    }

    // Save back to file
    await this.saveHistory(history);
  }

  /**
   * Create a history entry from full report
   * @param {Object} report - Full aggregated report
   * @returns {Object} History entry
   */
  createHistoryEntry(report) {
    return {
      timestamp: report.metadata.timestamp,
      buildNumber: report.metadata.buildNumber,
      buildId: report.metadata.buildId,
      branch: report.metadata.branch,
      commit: report.metadata.commit,
      summary: {
        healthStatus: report.summary.healthStatus,
        dataComplete: report.summary.dataComplete ?? null,
        incompleteSources: report.summary.incompleteSources ? [...report.summary.incompleteSources] : [],
        tests: { ...report.summary.tests },
        coverage: { ...report.summary.coverage },
        security: { ...report.summary.security },
        dast: report.summary.dast ? { ...report.summary.dast } : null,
        quality: { ...report.summary.quality }
      },
      sources: report.metadata.sources
        ? Object.fromEntries(Object.entries(report.metadata.sources)
          .map(([k, v]) => [k, { status: v.status, measuredAt: v.measuredAt || null }]))
        : undefined,
      modules: report.modules.map(m => ({
        name: m.name,
        status: m.status,
        testSuccessRate: m.tests?.successRate || null,
        lineCoverage: m.coverage?.line || null,
        violations: m.quality?.violations || null
      }))
    };
  }

  /**
   * Save history to file
   * @param {Array} history - History entries
   * @returns {Promise<void>}
   */
  async saveHistory(history) {
    await fs.ensureDir(path.dirname(this.historyFile));

    const data = {
      version: '1.0',
      lastUpdated: new Date().toISOString(),
      totalEntries: history.length,
      history: history
    };

    await fs.writeJson(this.historyFile, data, { spaces: 2 });
    console.log(`  ✓ Saved history to: ${this.historyFile} (${history.length} entries)`);
  }

  /**
   * Get trend data for charts - one build per day for last N days
   * @param {number} limit - Number of days to include (default 8)
   * @returns {Promise<Object>} Trend data
   */
  async getTrendData(limit = 8) {
    const history = await this.loadHistory();

    // Group builds by day and keep only the last (most recent) build per day
    const buildsByDay = new Map();

    for (const entry of history) {
      const date = new Date(entry.timestamp).toISOString().split('T')[0]; // YYYY-MM-DD

      // Only keep the first entry we see for each day (history is newest first)
      if (!buildsByDay.has(date)) {
        buildsByDay.set(date, entry);
      }
    }

    // Get the last N days, sorted oldest to newest
    const entries = Array.from(buildsByDay.values())
      .slice(0, limit)
      .reverse(); // Oldest to newest for chart display

    return {
      labels: entries.map(e => this.formatBuildLabel(e)),
      coverage: {
        overall: entries.map(e => e.summary.coverage.overall ?? null),
        java: entries.map(e => e.summary.coverage.java ?? null),
        frontend: entries.map(e => e.summary.coverage.frontend ?? null),
        infrastructure: entries.map(e => e.summary.coverage.infrastructure ?? null)
      },
      tests: {
        total: entries.map(e => e.summary.tests.total),
        passed: entries.map(e => e.summary.tests.passed),
        failed: entries.map(e => e.summary.tests.failed),
        successRate: entries.map(e => e.summary.tests.successRate)
      },
      security: {
        total: entries.map(e => e.summary.security.totalIssues),
        critical: entries.map(e => e.summary.security.critical),
        high: entries.map(e => e.summary.security.high)
      },
      quality: {
        violations: entries.map(e => e.summary.quality.totalViolations),
        errors: entries.map(e => e.summary.quality.errors),
        warnings: entries.map(e => e.summary.quality.warnings)
      }
    };
  }

  /**
   * Format build label for charts
   * @param {Object} entry - History entry
   * @returns {string} Label
   */
  formatBuildLabel(entry) {
    if (entry.buildNumber && entry.buildNumber !== 'local') {
      return `#${entry.buildNumber}`;
    }
    const date = new Date(entry.timestamp);
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }

  /**
   * Get comparison with previous build from a different day
   * @param {Object} currentReport - Current report
   * @returns {Promise<Object>} Comparison data
   */
  async getComparison(currentReport) {
    const history = await this.loadHistory();

    if (history.length === 0) {
      return null;
    }

    const current = this.createHistoryEntry(currentReport);
    const currentDate = new Date(current.timestamp).toISOString().split('T')[0]; // YYYY-MM-DD

    // Find the last build from a previous day
    const previous = history.find(entry => {
      const entryDate = new Date(entry.timestamp).toISOString().split('T')[0];
      return entryDate !== currentDate;
    });

    // If no build from previous day, return null (don't compare with same day)
    if (!previous) {
      return null;
    }

    const c = current.summary;
    const p = previous.summary;
    const d = (a, b) => this.delta(a, b);

    return {
      previous: {
        buildNumber: previous.buildNumber,
        timestamp: previous.timestamp
      },
      changes: {
        tests: {
          total: d(c.tests.total, p.tests.total),
          failed: d(c.tests.failed, p.tests.failed)
        },
        coverage: {
          overall: d(c.coverage.overall, p.coverage.overall),
          java: d(c.coverage.java, p.coverage.java),
          frontend: d(c.coverage.frontend, p.coverage.frontend),
          infrastructure: d(c.coverage.infrastructure, p.coverage.infrastructure)
        },
        security: {
          total: d(c.security.totalIssues, p.security.totalIssues),
          critical: d(c.security.critical, p.security.critical),
          high: d(c.security.high, p.security.high)
        },
        quality: {
          violations: d(c.quality.totalViolations, p.quality.totalViolations),
          errors: d(c.quality.errors, p.quality.errors)
        }
      }
    };
  }

  /**
   * Difference of two measurements, or null when either side was not measured
   * (a missing value is not 0, so it cannot produce a change).
   */
  delta(current, previous) {
    if (typeof current !== 'number' || typeof previous !== 'number') return null;
    return this.roundDelta(current - previous);
  }

  /**
   * Round delta to 2 decimal places
   * @param {number} value - Value to round
   * @returns {number} Rounded value
   */
  roundDelta(value) {
    return Math.round(value * 100) / 100;
  }

  /**
   * Get module-specific trends
   * @param {string} moduleName - Module name
   * @param {number} limit - Number of entries
   * @returns {Promise<Object>} Module trend data
   */
  async getModuleTrend(moduleName, limit = 20) {
    const history = await this.loadHistory();
    const entries = history.slice(0, limit).reverse();

    const moduleData = entries.map(entry => {
      const module = entry.modules.find(m => m.name === moduleName);
      return {
        buildNumber: entry.buildNumber,
        timestamp: entry.timestamp,
        status: module?.status || 'unknown',
        testSuccessRate: module?.testSuccessRate || null,
        lineCoverage: module?.lineCoverage || null,
        violations: module?.violations || null
      };
    });

    return {
      module: moduleName,
      labels: entries.map(e => this.formatBuildLabel(e)),
      data: moduleData,
      coverage: moduleData.map(d => d.lineCoverage),
      testSuccessRate: moduleData.map(d => d.testSuccessRate),
      violations: moduleData.map(d => d.violations)
    };
  }

  /**
   * Calculate statistics over time
   * @returns {Promise<Object>} Statistics
   */
  async getStatistics() {
    const history = await this.loadHistory();

    if (history.length === 0) {
      return null;
    }

    // Builds without a measurement are left out, not counted as 0
    const isNum = (v) => typeof v === 'number';
    const coverages = history.map(h => h.summary.coverage.overall).filter(isNum);
    const testRates = history.map(h => h.summary.tests.successRate).filter(isNum);

    return {
      totalBuilds: history.length,
      dateRange: {
        from: history[history.length - 1].timestamp,
        to: history[0].timestamp
      },
      coverage: {
        current: coverages[0] ?? null,
        average: this.average(coverages),
        min: coverages.length ? Math.min(...coverages) : null,
        max: coverages.length ? Math.max(...coverages) : null,
        trend: this.calculateTrend([...coverages].reverse())
      },
      testSuccessRate: {
        current: testRates[0] ?? null,
        average: this.average(testRates),
        min: testRates.length ? Math.min(...testRates) : null,
        max: testRates.length ? Math.max(...testRates) : null
      },
      builds: {
        passing: history.filter(h => h.summary.healthStatus === 'passing').length,
        warning: history.filter(h => h.summary.healthStatus === 'warning').length,
        failing: history.filter(h => h.summary.healthStatus === 'failing').length
      }
    };
  }

  /**
   * Calculate average
   * @param {Array<number>} values - Values
   * @returns {number} Average
   */
  average(values) {
    if (values.length === 0) return 0;
    const sum = values.reduce((a, b) => a + b, 0);
    return Math.round((sum / values.length) * 100) / 100;
  }

  /**
   * Calculate trend (simple linear regression slope)
   * @param {Array<number>} values - Values (oldest to newest)
   * @returns {string} Trend: improving | stable | declining
   */
  calculateTrend(values) {
    if (values.length < 2) return 'stable';

    // Take last 10 values for trend
    const recentValues = values.slice(-10);
    const n = recentValues.length;

    // Calculate slope
    let sumX = 0;
    let sumY = 0;
    let sumXY = 0;
    let sumXX = 0;

    for (let i = 0; i < n; i++) {
      sumX += i;
      sumY += recentValues[i];
      sumXY += i * recentValues[i];
      sumXX += i * i;
    }

    const slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);

    // Classify trend
    if (slope > 0.5) return 'improving';
    if (slope < -0.5) return 'declining';
    return 'stable';
  }
}

export default HistoryManager;
