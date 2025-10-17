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
        return data.history || [];
      }
    } catch (error) {
      console.warn(`Failed to load history from ${this.historyFile}:`, error.message);
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

    // Add new entry
    history.unshift(entry);

    // Trim to max entries
    if (history.length > this.maxHistoryEntries) {
      history.splice(this.maxHistoryEntries);
    }

    // Save back to file
    await this.saveHistory(history);

    console.log(`Saved report to history (${history.length} entries)`);
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
        tests: { ...report.summary.tests },
        coverage: { ...report.summary.coverage },
        security: { ...report.summary.security },
        quality: { ...report.summary.quality }
      },
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
  }

  /**
   * Get trend data for charts
   * @param {number} limit - Number of entries to include
   * @returns {Promise<Object>} Trend data
   */
  async getTrendData(limit = 20) {
    const history = await this.loadHistory();
    const entries = history.slice(0, limit).reverse(); // Oldest to newest

    return {
      labels: entries.map(e => this.formatBuildLabel(e)),
      coverage: {
        overall: entries.map(e => e.summary.coverage.overall),
        java: entries.map(e => e.summary.coverage.java),
        frontend: entries.map(e => e.summary.coverage.frontend)
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
   * Get comparison with previous build
   * @param {Object} currentReport - Current report
   * @returns {Promise<Object>} Comparison data
   */
  async getComparison(currentReport) {
    const history = await this.loadHistory();

    if (history.length === 0) {
      return null;
    }

    const previous = history[0];
    const current = this.createHistoryEntry(currentReport);

    return {
      previous: {
        buildNumber: previous.buildNumber,
        timestamp: previous.timestamp
      },
      changes: {
        tests: {
          total: current.summary.tests.total - previous.summary.tests.total,
          failed: current.summary.tests.failed - previous.summary.tests.failed
        },
        coverage: {
          overall: this.roundDelta(current.summary.coverage.overall - previous.summary.coverage.overall),
          java: this.roundDelta(current.summary.coverage.java - previous.summary.coverage.java),
          frontend: this.roundDelta(current.summary.coverage.frontend - previous.summary.coverage.frontend)
        },
        security: {
          total: current.summary.security.totalIssues - previous.summary.security.totalIssues,
          critical: current.summary.security.critical - previous.summary.security.critical,
          high: current.summary.security.high - previous.summary.security.high
        },
        quality: {
          violations: current.summary.quality.totalViolations - previous.summary.quality.totalViolations,
          errors: current.summary.quality.errors - previous.summary.quality.errors
        }
      }
    };
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

    const coverages = history.map(h => h.summary.coverage.overall);
    const testRates = history.map(h => h.summary.tests.successRate);

    return {
      totalBuilds: history.length,
      dateRange: {
        from: history[history.length - 1].timestamp,
        to: history[0].timestamp
      },
      coverage: {
        current: coverages[0],
        average: this.average(coverages),
        min: Math.min(...coverages),
        max: Math.max(...coverages),
        trend: this.calculateTrend(coverages)
      },
      testSuccessRate: {
        current: testRates[0],
        average: this.average(testRates),
        min: Math.min(...testRates),
        max: Math.max(...testRates)
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
