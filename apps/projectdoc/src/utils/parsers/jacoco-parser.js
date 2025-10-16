import { parseStringPromise } from 'xml2js';
import fs from 'fs-extra';
import path from 'path';

/**
 * Parser for JaCoCo XML coverage reports
 * Extracts line coverage, branch coverage, and detailed metrics
 */
export class JacocoParser {
  /**
   * Parse a JaCoCo XML report file
   * @param {string} filePath - Path to jacocoTestReport.xml
   * @returns {Promise<Object>} Parsed coverage data
   */
  static async parseFile(filePath) {
    try {
      const xmlContent = await fs.readFile(filePath, 'utf8');
      const result = await parseStringPromise(xmlContent);

      return this.extractCoverageData(result);
    } catch (error) {
      console.error(`Failed to parse JaCoCo report: ${filePath}`, error.message);
      return null;
    }
  }

  /**
   * Extract coverage metrics from parsed XML
   * @param {Object} xmlData - Parsed XML object
   * @returns {Object} Coverage metrics
   */
  static extractCoverageData(xmlData) {
    if (!xmlData.report) {
      throw new Error('Invalid JaCoCo XML structure');
    }

    const report = xmlData.report;
    const counters = report.counter || [];

    // Extract overall metrics
    const metrics = this.parseCounters(counters);

    // Extract package-level data
    const packages = this.parsePackages(report.package || []);

    return {
      type: 'jacoco',
      name: report.$.name || 'Unknown',
      overall: metrics,
      packages: packages,
      classes: this.countClasses(report.package || []),
      methods: this.countMethods(report.package || [])
    };
  }

  /**
   * Parse counter elements to extract coverage percentages
   * @param {Array} counters - Counter elements from XML
   * @returns {Object} Coverage metrics
   */
  static parseCounters(counters) {
    const metrics = {
      instruction: { covered: 0, missed: 0, percentage: 0 },
      branch: { covered: 0, missed: 0, percentage: 0 },
      line: { covered: 0, missed: 0, percentage: 0 },
      complexity: { covered: 0, missed: 0 },
      method: { covered: 0, missed: 0, percentage: 0 },
      class: { covered: 0, missed: 0, percentage: 0 }
    };

    counters.forEach(counter => {
      const type = counter.$.type.toLowerCase();
      const covered = parseInt(counter.$.covered, 10);
      const missed = parseInt(counter.$.missed, 10);
      const total = covered + missed;
      const percentage = total > 0 ? (covered / total) * 100 : 0;

      if (metrics[type]) {
        metrics[type] = {
          covered,
          missed,
          total,
          percentage: Math.round(percentage * 100) / 100
        };
      }
    });

    return metrics;
  }

  /**
   * Parse package-level coverage data
   * @param {Array} packages - Package elements from XML
   * @returns {Array} Package coverage data
   */
  static parsePackages(packages) {
    return packages.map(pkg => {
      const counters = pkg.counter || [];
      const metrics = this.parseCounters(counters);

      return {
        name: pkg.$.name || 'default',
        coverage: metrics,
        classes: (pkg.class || []).length,
        sourceFiles: (pkg.sourcefile || []).length
      };
    });
  }

  /**
   * Count total classes in report
   * @param {Array} packages - Package elements
   * @returns {number} Total class count
   */
  static countClasses(packages) {
    return packages.reduce((sum, pkg) => {
      return sum + (pkg.class || []).length;
    }, 0);
  }

  /**
   * Count total methods in report
   * @param {Array} packages - Package elements
   * @returns {number} Total method count
   */
  static countMethods(packages) {
    return packages.reduce((sum, pkg) => {
      const classes = pkg.class || [];
      return sum + classes.reduce((classSum, cls) => {
        return classSum + (cls.method || []).length;
      }, 0);
    }, 0);
  }

  /**
   * Find and parse all JaCoCo reports in a directory tree
   * @param {string} baseDir - Base directory to search
   * @param {string} pattern - Glob pattern for report files
   * @returns {Promise<Array>} Array of parsed reports with metadata
   */
  static async findAndParseReports(baseDir, pattern = '**/build/reports/jacoco/test/jacocoTestReport.xml') {
    const glob = await import('glob');
    const reports = [];

    const files = glob.sync(pattern, { cwd: baseDir, absolute: true });

    for (const file of files) {
      const coverage = await this.parseFile(file);
      if (coverage) {
        // Extract module name from path
        const moduleName = this.extractModuleName(file, baseDir);
        reports.push({
          module: moduleName,
          reportPath: path.relative(baseDir, file),
          coverage: coverage
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

    // Typically: module-name/build/reports/jacoco/test/jacocoTestReport.xml
    return parts[0] || 'unknown';
  }

  /**
   * Calculate summary statistics from multiple module reports
   * @param {Array} reports - Array of parsed reports
   * @returns {Object} Summary statistics
   */
  static calculateSummary(reports) {
    const summary = {
      totalModules: reports.length,
      overallCoverage: {
        line: 0,
        branch: 0,
        instruction: 0
      },
      totalClasses: 0,
      totalMethods: 0,
      modules: []
    };

    let totalLines = 0;
    let coveredLines = 0;
    let totalBranches = 0;
    let coveredBranches = 0;
    let totalInstructions = 0;
    let coveredInstructions = 0;

    reports.forEach(report => {
      const coverage = report.coverage.overall;

      // Accumulate totals
      totalLines += coverage.line.total || 0;
      coveredLines += coverage.line.covered || 0;
      totalBranches += coverage.branch.total || 0;
      coveredBranches += coverage.branch.covered || 0;
      totalInstructions += coverage.instruction.total || 0;
      coveredInstructions += coverage.instruction.covered || 0;

      summary.totalClasses += report.coverage.classes || 0;
      summary.totalMethods += report.coverage.methods || 0;

      summary.modules.push({
        name: report.module,
        lineCoverage: coverage.line.percentage,
        branchCoverage: coverage.branch.percentage,
        classes: report.coverage.classes,
        methods: report.coverage.methods
      });
    });

    // Calculate weighted averages
    summary.overallCoverage.line = totalLines > 0
      ? Math.round((coveredLines / totalLines) * 10000) / 100
      : 0;
    summary.overallCoverage.branch = totalBranches > 0
      ? Math.round((coveredBranches / totalBranches) * 10000) / 100
      : 0;
    summary.overallCoverage.instruction = totalInstructions > 0
      ? Math.round((coveredInstructions / totalInstructions) * 10000) / 100
      : 0;

    return summary;
  }
}

export default JacocoParser;
