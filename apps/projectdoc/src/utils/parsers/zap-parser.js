import fs from 'fs-extra';
import path from 'path';
import { globSync } from 'glob';

/**
 * Parser for OWASP ZAP JSON report files (report_json.json).
 *
 * ZAP JSON structure:
 *   { site: [{ "@name": "https://...", alerts: [{ pluginid, alert, riskcode, confidence,
 *              desc, solution, instances: [{uri, method}], count }] }] }
 *
 * riskcode values: 0 = Informational, 1 = Low, 2 = Medium, 3 = High
 */
export class ZapParser {
  static RISK_NAMES = { '0': 'info', '1': 'low', '2': 'medium', '3': 'high' };
  static RISK_LABELS = { '0': 'Informational', '1': 'Low', '2': 'Medium', '3': 'High' };

  /**
   * Parse a single ZAP JSON report file.
   * @param {string} filePath - Absolute path to the JSON file
   * @param {string} [scanName]  - Human-readable name (defaults to filename without extension)
   * @returns {Promise<Object|null>} Parsed scan result or null on error
   */
  static async parseFile(filePath, scanName) {
    try {
      const content = await fs.readFile(filePath, 'utf8');
      const report = JSON.parse(content);
      const name = scanName || path.basename(filePath, '.json').replace(/^zap-/, '');
      return this.extractFindings(report, name);
    } catch (error) {
      console.error(`Failed to parse ZAP report: ${filePath}`, error.message);
      return null;
    }
  }

  /**
   * Extract findings from a parsed ZAP JSON object.
   * @param {Object} report - Parsed ZAP JSON
   * @param {string} scanName - Scan identifier
   * @returns {Object} Structured findings
   */
  static extractFindings(report, scanName) {
    const sites = report.site || [];
    const allAlerts = [];

    for (const site of sites) {
      const alerts = site.alerts || [];
      for (const alert of alerts) {
        const riskCode = String(alert.riskcode);
        allAlerts.push({
          pluginId: alert.pluginid,
          name: alert.alert || alert.name || 'Unknown',
          riskCode,
          risk: this.RISK_NAMES[riskCode] || 'info',
          riskLabel: this.RISK_LABELS[riskCode] || 'Informational',
          confidence: alert.confidence,
          description: alert.desc,
          solution: alert.solution,
          reference: alert.reference,
          instances: (alert.instances || []).slice(0, 5).map(i => ({
            uri: i.uri,
            method: i.method || 'GET'
          })),
          count: parseInt(alert.count || '0', 10)
        });
      }
    }

    // Sort by risk descending (high first)
    allAlerts.sort((a, b) => parseInt(b.riskCode) - parseInt(a.riskCode));

    const summary = {
      high:   allAlerts.filter(a => a.riskCode === '3').length,
      medium: allAlerts.filter(a => a.riskCode === '2').length,
      low:    allAlerts.filter(a => a.riskCode === '1').length,
      info:   allAlerts.filter(a => a.riskCode === '0').length,
      total:  allAlerts.length
    };

    return {
      scanName,
      target: sites[0]?.['@name'] || 'unknown',
      generatedDate: report['@generated'] || null,
      alerts: allAlerts,
      summary
    };
  }

  /**
   * Find and parse all ZAP JSON reports matching the given glob pattern.
   * @param {string} baseDir   - Root directory for glob search
   * @param {string} [pattern] - Glob pattern (relative to baseDir)
   * @returns {Promise<Array>} Array of { reportPath, scanName, findings }
   */
  static async findAndParseReports(baseDir, pattern = 'security-reports/zap-*.json') {
    const reports = [];
    const files = globSync(pattern, {
      cwd: baseDir,
      absolute: true,
      ignore: '**/node_modules/**'
    });

    for (const file of files) {
      const scanName = path.basename(file, '.json').replace(/^zap-/, '');
      const findings = await this.parseFile(file, scanName);
      if (findings) {
        reports.push({
          reportPath: path.relative(baseDir, file),
          scanName,
          findings
        });
      }
    }

    // Sort by scan name for stable display order
    reports.sort((a, b) => a.scanName.localeCompare(b.scanName));
    return reports;
  }

  /**
   * Calculate aggregate summary across multiple ZAP scan results.
   * @param {Array} reports - Array of { findings } from findAndParseReports
   * @returns {Object} Overall summary
   */
  static calculateOverallSummary(reports) {
    const overall = { high: 0, medium: 0, low: 0, info: 0, total: 0, scansRun: reports.length };

    for (const report of reports) {
      const s = report.findings.summary;
      overall.high   += s.high;
      overall.medium += s.medium;
      overall.low    += s.low;
      overall.info   += s.info;
      overall.total  += s.total;
    }

    // "clean" = no High or Medium alerts
    overall.clean = overall.high === 0 && overall.medium === 0;
    overall.status = overall.high > 0 ? 'fail'
      : overall.medium > 0 ? 'warn'
      : 'pass';

    return overall;
  }

  /**
   * Group all alerts across scans by alert type (pluginId), de-duplicating.
   * @param {Array} reports - Array of { scanName, findings }
   * @returns {Array} Sorted list of unique alert types with affected scans
   */
  static groupByAlertType(reports) {
    const alertMap = new Map();

    for (const report of reports) {
      for (const alert of report.findings.alerts) {
        const key = alert.pluginId;
        if (!alertMap.has(key)) {
          alertMap.set(key, {
            pluginId: alert.pluginId,
            name: alert.name,
            riskCode: alert.riskCode,
            risk: alert.risk,
            riskLabel: alert.riskLabel,
            scans: [],
            totalInstances: 0,
            solution: alert.solution,
            reference: alert.reference
          });
        }
        const entry = alertMap.get(key);
        entry.scans.push(report.scanName);
        entry.totalInstances += alert.count || alert.instances.length;
      }
    }

    return Array.from(alertMap.values())
      .sort((a, b) => parseInt(b.riskCode) - parseInt(a.riskCode));
  }
}

export default ZapParser;
