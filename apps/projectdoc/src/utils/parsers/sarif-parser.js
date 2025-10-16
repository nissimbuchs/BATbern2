import fs from 'fs-extra';
import path from 'path';
import glob from 'glob';

/**
 * Parser for SARIF (Static Analysis Results Interchange Format) files
 * Used by security scanners like Trivy, CodeQL, etc.
 */
export class SarifParser {
  /**
   * Parse a SARIF format file
   * @param {string} filePath - Path to SARIF JSON file
   * @returns {Promise<Object>} Parsed security findings
   */
  static async parseFile(filePath) {
    try {
      const content = await fs.readFile(filePath, 'utf8');
      const sarif = JSON.parse(content);

      return this.extractFindings(sarif);
    } catch (error) {
      console.error(`Failed to parse SARIF report: ${filePath}`, error.message);
      return null;
    }
  }

  /**
   * Extract security findings from SARIF structure
   * @param {Object} sarif - Parsed SARIF JSON
   * @returns {Object} Structured security findings
   */
  static extractFindings(sarif) {
    if (!sarif.runs || sarif.runs.length === 0) {
      return {
        type: 'sarif',
        version: sarif.version || '2.1.0',
        runs: [],
        summary: this.createEmptySummary()
      };
    }

    const runs = sarif.runs.map(run => this.parseRun(run));
    const summary = this.calculateSummary(runs);

    return {
      type: 'sarif',
      version: sarif.version || '2.1.0',
      runs: runs,
      summary: summary
    };
  }

  /**
   * Parse a single SARIF run
   * @param {Object} run - SARIF run object
   * @returns {Object} Parsed run data
   */
  static parseRun(run) {
    const tool = run.tool?.driver || {};
    const results = run.results || [];

    return {
      toolName: tool.name || 'Unknown',
      toolVersion: tool.version || 'Unknown',
      informationUri: tool.informationUri,
      results: results.map(result => this.parseResult(result, run)),
      invocations: run.invocations?.map(inv => ({
        executionSuccessful: inv.executionSuccessful,
        exitCode: inv.exitCode,
        startTimeUtc: inv.startTimeUtc,
        endTimeUtc: inv.endTimeUtc
      }))
    };
  }

  /**
   * Parse a single result (finding)
   * @param {Object} result - SARIF result object
   * @param {Object} run - Parent run for rule lookup
   * @returns {Object} Parsed result
   */
  static parseResult(result, run) {
    const ruleId = result.ruleId || 'unknown';
    const rule = this.findRule(ruleId, run);

    const location = result.locations?.[0];
    const physicalLocation = location?.physicalLocation;

    return {
      ruleId: ruleId,
      level: result.level || 'warning',
      message: result.message?.text || 'No message',
      locations: result.locations?.map(loc => ({
        uri: loc.physicalLocation?.artifactLocation?.uri,
        startLine: loc.physicalLocation?.region?.startLine,
        endLine: loc.physicalLocation?.region?.endLine,
        snippet: loc.physicalLocation?.region?.snippet?.text
      })),
      severity: this.mapLevelToSeverity(result.level),
      rule: rule ? {
        name: rule.name || ruleId,
        shortDescription: rule.shortDescription?.text,
        fullDescription: rule.fullDescription?.text,
        helpUri: rule.helpUri,
        properties: rule.properties
      } : null,
      fingerprints: result.fingerprints,
      fixes: result.fixes?.map(fix => ({
        description: fix.description?.text,
        artifactChanges: fix.artifactChanges
      }))
    };
  }

  /**
   * Find rule definition by ID
   * @param {string} ruleId - Rule identifier
   * @param {Object} run - SARIF run containing rules
   * @returns {Object|null} Rule definition
   */
  static findRule(ruleId, run) {
    const rules = run.tool?.driver?.rules || [];
    return rules.find(rule => rule.id === ruleId) || null;
  }

  /**
   * Map SARIF level to common severity
   * @param {string} level - SARIF level (note, warning, error)
   * @returns {string} Severity label
   */
  static mapLevelToSeverity(level) {
    const mapping = {
      'none': 'info',
      'note': 'low',
      'warning': 'medium',
      'error': 'high'
    };
    return mapping[level] || 'medium';
  }

  /**
   * Calculate summary statistics
   * @param {Array} runs - Array of parsed runs
   * @returns {Object} Summary statistics
   */
  static calculateSummary(runs) {
    const summary = {
      totalRuns: runs.length,
      totalFindings: 0,
      bySeverity: {
        critical: 0,
        high: 0,
        medium: 0,
        low: 0,
        info: 0
      },
      byTool: {},
      uniqueRules: new Set(),
      affectedFiles: new Set()
    };

    runs.forEach(run => {
      if (!summary.byTool[run.toolName]) {
        summary.byTool[run.toolName] = {
          version: run.toolVersion,
          findings: 0
        };
      }

      run.results.forEach(result => {
        summary.totalFindings++;
        summary.byTool[run.toolName].findings++;
        summary.uniqueRules.add(result.ruleId);

        // Count by severity
        const severity = result.severity;
        if (summary.bySeverity[severity] !== undefined) {
          summary.bySeverity[severity]++;
        }

        // Track affected files
        result.locations?.forEach(loc => {
          if (loc.uri) {
            summary.affectedFiles.add(loc.uri);
          }
        });
      });
    });

    return {
      ...summary,
      uniqueRules: summary.uniqueRules.size,
      affectedFiles: summary.affectedFiles.size
    };
  }

  /**
   * Create empty summary for reports with no findings
   * @returns {Object} Empty summary
   */
  static createEmptySummary() {
    return {
      totalRuns: 0,
      totalFindings: 0,
      bySeverity: {
        critical: 0,
        high: 0,
        medium: 0,
        low: 0,
        info: 0
      },
      byTool: {},
      uniqueRules: 0,
      affectedFiles: 0
    };
  }

  /**
   * Find and parse all SARIF reports
   * @param {string} baseDir - Base directory to search
   * @param {string} pattern - Glob pattern for SARIF files
   * @returns {Promise<Array>} Array of parsed reports
   */
  static async findAndParseReports(baseDir, pattern = '**/*.sarif') {
    const reports = [];
    const files = glob.sync(pattern, { cwd: baseDir, absolute: true, ignore: '**/node_modules/**' });

    for (const file of files) {
      const findings = await this.parseFile(file);
      if (findings) {
        reports.push({
          reportPath: path.relative(baseDir, file),
          findings: findings
        });
      }
    }

    return reports;
  }

  /**
   * Get top vulnerabilities by severity
   * @param {Object} findings - Parsed SARIF findings
   * @param {number} limit - Maximum number to return
   * @returns {Array} Top vulnerabilities
   */
  static getTopVulnerabilities(findings, limit = 10) {
    const allResults = [];

    findings.runs.forEach(run => {
      run.results.forEach(result => {
        allResults.push({
          tool: run.toolName,
          ruleId: result.ruleId,
          severity: result.severity,
          message: result.message,
          locations: result.locations,
          rule: result.rule
        });
      });
    });

    // Sort by severity (critical > high > medium > low > info)
    const severityOrder = { critical: 0, high: 1, medium: 2, low: 3, info: 4 };

    return allResults
      .sort((a, b) => severityOrder[a.severity] - severityOrder[b.severity])
      .slice(0, limit);
  }

  /**
   * Group findings by file
   * @param {Object} findings - Parsed SARIF findings
   * @returns {Object} Findings grouped by file path
   */
  static groupByFile(findings) {
    const fileMap = new Map();

    findings.runs.forEach(run => {
      run.results.forEach(result => {
        result.locations?.forEach(loc => {
          if (!loc.uri) return;

          if (!fileMap.has(loc.uri)) {
            fileMap.set(loc.uri, {
              path: loc.uri,
              findings: [],
              severityCounts: { critical: 0, high: 0, medium: 0, low: 0, info: 0 }
            });
          }

          const fileData = fileMap.get(loc.uri);
          fileData.findings.push({
            tool: run.toolName,
            ruleId: result.ruleId,
            severity: result.severity,
            message: result.message,
            line: loc.startLine,
            rule: result.rule
          });
          fileData.severityCounts[result.severity]++;
        });
      });
    });

    return Array.from(fileMap.values());
  }
}

export default SarifParser;
