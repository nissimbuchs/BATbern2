import { parseStringPromise } from 'xml2js';
import fs from 'fs-extra';
import path from 'path';
import glob from 'glob';

/**
 * Parser for Checkstyle XML reports
 * Extracts code quality violations and style issues
 */
export class CheckstyleParser {
  /**
   * Parse a Checkstyle XML report file
   * @param {string} filePath - Path to checkstyle report XML
   * @returns {Promise<Object>} Parsed violations
   */
  static async parseFile(filePath) {
    try {
      const xmlContent = await fs.readFile(filePath, 'utf8');
      const result = await parseStringPromise(xmlContent);

      return this.extractViolations(result);
    } catch (error) {
      console.error(`Failed to parse Checkstyle report: ${filePath}`, error.message);
      return null;
    }
  }

  /**
   * Extract violations from parsed XML
   * @param {Object} xmlData - Parsed XML object
   * @returns {Object} Structured violations
   */
  static extractViolations(xmlData) {
    if (!xmlData.checkstyle) {
      throw new Error('Invalid Checkstyle XML structure');
    }

    const checkstyle = xmlData.checkstyle;
    const version = checkstyle.$.version || 'Unknown';
    const files = checkstyle.file || [];

    const parsedFiles = files.map(file => this.parseFile Violations(file));

    return {
      type: 'checkstyle',
      version: version,
      files: parsedFiles,
      summary: this.calculateSummary(parsedFiles)
    };
  }

  /**
   * Parse violations for a single file
   * @param {Object} file - File element from XML
   * @returns {Object} File violations
   */
  static parseFileViolations(file) {
    const errors = file.error || [];

    return {
      name: file.$.name,
      violations: errors.map(error => ({
        line: parseInt(error.$.line || '0', 10),
        column: parseInt(error.$.column || '0', 10),
        severity: error.$.severity || 'warning',
        message: error.$.message || 'No message',
        source: error.$.source || 'unknown',
        rule: this.extractRuleName(error.$.source)
      }))
    };
  }

  /**
   * Extract rule name from source
   * @param {string} source - Full source identifier
   * @returns {string} Rule name
   */
  static extractRuleName(source) {
    if (!source) return 'unknown';

    // Extract last part after final dot
    // e.g., "com.puppycrawl.tools.checkstyle.checks.naming.TypeNameCheck" -> "TypeNameCheck"
    const parts = source.split('.');
    return parts[parts.length - 1] || source;
  }

  /**
   * Calculate summary statistics
   * @param {Array} files - Array of parsed files
   * @returns {Object} Summary statistics
   */
  static calculateSummary(files) {
    const summary = {
      totalFiles: files.length,
      filesWithViolations: 0,
      totalViolations: 0,
      bySeverity: {
        error: 0,
        warning: 0,
        info: 0,
        ignore: 0
      },
      byRule: {},
      topViolations: []
    };

    files.forEach(file => {
      if (file.violations.length > 0) {
        summary.filesWithViolations++;
      }

      file.violations.forEach(violation => {
        summary.totalViolations++;

        // Count by severity
        const severity = violation.severity.toLowerCase();
        if (summary.bySeverity[severity] !== undefined) {
          summary.bySeverity[severity]++;
        }

        // Count by rule
        const rule = violation.rule;
        if (!summary.byRule[rule]) {
          summary.byRule[rule] = {
            count: 0,
            severity: violation.severity,
            message: violation.message
          };
        }
        summary.byRule[rule].count++;
      });
    });

    // Sort rules by frequency
    summary.topViolations = Object.entries(summary.byRule)
      .map(([rule, data]) => ({ rule, ...data }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 10);

    return summary;
  }

  /**
   * Find and parse all Checkstyle reports
   * @param {string} baseDir - Base directory to search
   * @param {string} pattern - Glob pattern for Checkstyle files
   * @returns {Promise<Array>} Array of parsed reports
   */
  static async findAndParseReports(baseDir, pattern = '**/build/reports/checkstyle/*.xml') {
    const reports = [];
    const files = glob.sync(pattern, { cwd: baseDir, absolute: true });

    for (const file of files) {
      const violations = await this.parseFile(file);
      if (violations) {
        const moduleName = this.extractModuleName(file, baseDir);
        reports.push({
          module: moduleName,
          reportPath: path.relative(baseDir, file),
          violations: violations
        });
      }
    }

    return reports;
  }

  /**
   * Extract module name from file path
   * @param {string} filePath - Full file path
   * @param {string} baseDir - Base directory
   * @returns {string} Module name
   */
  static extractModuleName(filePath, baseDir) {
    const relativePath = path.relative(baseDir, filePath);
    const parts = relativePath.split(path.sep);

    // Typically: module-name/build/reports/checkstyle/*.xml
    return parts[0] || 'unknown';
  }

  /**
   * Get files with most violations
   * @param {Object} violations - Parsed checkstyle data
   * @param {number} limit - Maximum number to return
   * @returns {Array} Files with most violations
   */
  static getWorstFiles(violations, limit = 10) {
    return violations.files
      .filter(file => file.violations.length > 0)
      .map(file => ({
        path: file.name,
        violationCount: file.violations.length,
        errors: file.violations.filter(v => v.severity === 'error').length,
        warnings: file.violations.filter(v => v.severity === 'warning').length
      }))
      .sort((a, b) => b.violationCount - a.violationCount)
      .slice(0, limit);
  }

  /**
   * Group violations by rule category
   * @param {Object} violations - Parsed checkstyle data
   * @returns {Object} Violations grouped by category
   */
  static groupByCategory(violations) {
    const categories = {
      naming: { rules: [], count: 0 },
      whitespace: { rules: [], count: 0 },
      imports: { rules: [], count: 0 },
      javadoc: { rules: [], count: 0 },
      coding: { rules: [], count: 0 },
      design: { rules: [], count: 0 },
      other: { rules: [], count: 0 }
    };

    Object.entries(violations.summary.byRule).forEach(([rule, data]) => {
      const ruleLower = rule.toLowerCase();
      let category = 'other';

      if (ruleLower.includes('name')) category = 'naming';
      else if (ruleLower.includes('whitespace') || ruleLower.includes('indentation')) category = 'whitespace';
      else if (ruleLower.includes('import')) category = 'imports';
      else if (ruleLower.includes('javadoc')) category = 'javadoc';
      else if (ruleLower.includes('design') || ruleLower.includes('complexity')) category = 'design';
      else category = 'coding';

      categories[category].rules.push({ rule, ...data });
      categories[category].count += data.count;
    });

    return categories;
  }

  /**
   * Calculate summary across multiple modules
   * @param {Array} reports - Array of parsed reports
   * @returns {Object} Combined summary
   */
  static calculateMultiModuleSummary(reports) {
    const summary = {
      totalModules: reports.length,
      overallStats: {
        totalFiles: 0,
        filesWithViolations: 0,
        totalViolations: 0,
        bySeverity: { error: 0, warning: 0, info: 0, ignore: 0 }
      },
      modules: [],
      topRules: {}
    };

    reports.forEach(report => {
      const stats = report.violations.summary;

      summary.overallStats.totalFiles += stats.totalFiles;
      summary.overallStats.filesWithViolations += stats.filesWithViolations;
      summary.overallStats.totalViolations += stats.totalViolations;

      Object.keys(stats.bySeverity).forEach(severity => {
        summary.overallStats.bySeverity[severity] += stats.bySeverity[severity];
      });

      summary.modules.push({
        name: report.module,
        violations: stats.totalViolations,
        files: stats.totalFiles,
        errors: stats.bySeverity.error,
        warnings: stats.bySeverity.warning
      });

      // Aggregate rules across modules
      Object.entries(stats.byRule).forEach(([rule, data]) => {
        if (!summary.topRules[rule]) {
          summary.topRules[rule] = { count: 0, severity: data.severity };
        }
        summary.topRules[rule].count += data.count;
      });
    });

    // Sort top rules
    summary.topRules = Object.entries(summary.topRules)
      .map(([rule, data]) => ({ rule, ...data }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 10);

    return summary;
  }
}

export default CheckstyleParser;
