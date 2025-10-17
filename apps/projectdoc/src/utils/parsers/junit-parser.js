import { parseStringPromise } from 'xml2js';
import fs from 'fs-extra';
import path from 'path';
import { glob } from 'glob';

/**
 * Parser for JUnit XML test result files
 * Extracts test counts, failures, errors, and timing information
 */
export class JUnitParser {
  /**
   * Parse a JUnit XML test results file
   * @param {string} filePath - Path to TEST-*.xml file
   * @returns {Promise<Object>} Parsed test results
   */
  static async parseFile(filePath) {
    try {
      const xmlContent = await fs.readFile(filePath, 'utf8');
      const result = await parseStringPromise(xmlContent);

      return this.extractTestData(result, filePath);
    } catch (error) {
      console.error(`Failed to parse JUnit report: ${filePath}`, error.message);
      return null;
    }
  }

  /**
   * Extract test metrics from parsed XML
   * @param {Object} xmlData - Parsed XML object
   * @param {string} filePath - Original file path
   * @returns {Object} Test metrics
   */
  static extractTestData(xmlData, filePath) {
    // JUnit XML can have either <testsuite> or <testsuites> as root
    let testsuites = [];

    if (xmlData.testsuite) {
      testsuites = [xmlData.testsuite];
    } else if (xmlData.testsuites && xmlData.testsuites.testsuite) {
      testsuites = xmlData.testsuites.testsuite;
    } else {
      throw new Error('Invalid JUnit XML structure');
    }

    const suites = testsuites.map(suite => this.parseTestSuite(suite));

    // Calculate totals
    const totals = this.calculateTotals(suites);

    return {
      type: 'junit',
      fileName: path.basename(filePath),
      suites: suites,
      summary: totals
    };
  }

  /**
   * Parse a single test suite
   * @param {Object} suite - Test suite element
   * @returns {Object} Parsed suite data
   */
  static parseTestSuite(suite) {
    const attrs = suite.$ || {};
    const testcases = suite.testcase || [];

    const parsedTestCases = testcases.map(tc => this.parseTestCase(tc));

    return {
      name: attrs.name || 'Unknown',
      tests: parseInt(attrs.tests || '0', 10),
      failures: parseInt(attrs.failures || '0', 10),
      errors: parseInt(attrs.errors || '0', 10),
      skipped: parseInt(attrs.skipped || '0', 10),
      time: parseFloat(attrs.time || '0'),
      timestamp: attrs.timestamp,
      hostname: attrs.hostname,
      testcases: parsedTestCases,
      systemOut: suite['system-out'] ? suite['system-out'][0] : null,
      systemErr: suite['system-err'] ? suite['system-err'][0] : null
    };
  }

  /**
   * Parse a single test case
   * @param {Object} testcase - Test case element
   * @returns {Object} Parsed test case
   */
  static parseTestCase(testcase) {
    const attrs = testcase.$ || {};

    const tc = {
      name: attrs.name || 'Unknown',
      classname: attrs.classname || '',
      time: parseFloat(attrs.time || '0'),
      status: 'passed'
    };

    // Check for failures
    if (testcase.failure && testcase.failure.length > 0) {
      const failure = testcase.failure[0];
      tc.status = 'failed';
      tc.failure = {
        message: failure.$?.message || '',
        type: failure.$?.type || '',
        details: failure._ || failure
      };
    }

    // Check for errors
    if (testcase.error && testcase.error.length > 0) {
      const error = testcase.error[0];
      tc.status = 'error';
      tc.error = {
        message: error.$?.message || '',
        type: error.$?.type || '',
        details: error._ || error
      };
    }

    // Check for skipped
    if (testcase.skipped && testcase.skipped.length > 0) {
      tc.status = 'skipped';
      tc.skipped = {
        message: testcase.skipped[0].$?.message || 'Test skipped'
      };
    }

    return tc;
  }

  /**
   * Calculate totals across all test suites
   * @param {Array} suites - Parsed test suites
   * @returns {Object} Summary totals
   */
  static calculateTotals(suites) {
    const totals = {
      totalTests: 0,
      passed: 0,
      failures: 0,
      errors: 0,
      skipped: 0,
      totalTime: 0,
      successRate: 0
    };

    suites.forEach(suite => {
      totals.totalTests += suite.tests;
      totals.failures += suite.failures;
      totals.errors += suite.errors;
      totals.skipped += suite.skipped;
      totals.totalTime += suite.time;
    });

    totals.passed = totals.totalTests - totals.failures - totals.errors - totals.skipped;
    totals.successRate = totals.totalTests > 0
      ? Math.round((totals.passed / totals.totalTests) * 10000) / 100
      : 0;

    return totals;
  }

  /**
   * Find and parse all JUnit reports in a directory tree
   * @param {string} baseDir - Base directory to search
   * @param {string} pattern - Glob pattern for report files
   * @returns {Promise<Array>} Array of parsed reports with metadata
   */
  static async findAndParseReports(baseDir, pattern = '**/build/test-results/**/*.xml') {
    const reports = [];
    const { globSync } = await import('glob');
    const files = globSync(pattern, { cwd: baseDir, absolute: true });

    for (const file of files) {
      const testResults = await this.parseFile(file);
      if (testResults) {
        const moduleName = this.extractModuleName(file, baseDir);
        reports.push({
          module: moduleName,
          reportPath: path.relative(baseDir, file),
          results: testResults
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

    // Handle nested services: services/event-management-service/build/...
    // or top-level: shared-kernel/build/...
    if (parts[0] === 'services' && parts.length > 1) {
      return `${parts[0]}/${parts[1]}`;
    }

    // Typically: module-name/build/... or module-name/coverage/...
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
      overallStats: {
        totalTests: 0,
        passed: 0,
        failures: 0,
        errors: 0,
        skipped: 0,
        totalTime: 0,
        successRate: 0
      },
      modules: [],
      failedTests: []
    };

    reports.forEach(report => {
      const stats = report.results.summary;

      summary.overallStats.totalTests += stats.totalTests;
      summary.overallStats.passed += stats.passed;
      summary.overallStats.failures += stats.failures;
      summary.overallStats.errors += stats.errors;
      summary.overallStats.skipped += stats.skipped;
      summary.overallStats.totalTime += stats.totalTime;

      summary.modules.push({
        name: report.module,
        tests: stats.totalTests,
        passed: stats.passed,
        failures: stats.failures,
        errors: stats.errors,
        successRate: stats.successRate,
        time: stats.totalTime
      });

      // Collect failed tests
      report.results.suites.forEach(suite => {
        suite.testcases.forEach(tc => {
          if (tc.status === 'failed' || tc.status === 'error') {
            summary.failedTests.push({
              module: report.module,
              suite: suite.name,
              testName: tc.name,
              className: tc.classname,
              status: tc.status,
              message: tc.failure?.message || tc.error?.message || 'Unknown error',
              time: tc.time
            });
          }
        });
      });
    });

    // Calculate overall success rate
    summary.overallStats.successRate = summary.overallStats.totalTests > 0
      ? Math.round((summary.overallStats.passed / summary.overallStats.totalTests) * 10000) / 100
      : 0;

    return summary;
  }
}

export default JUnitParser;
