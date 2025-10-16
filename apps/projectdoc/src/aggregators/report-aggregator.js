import JacocoParser from '../utils/parsers/jacoco-parser.js';
import JUnitParser from '../utils/parsers/junit-parser.js';
import LcovParser from '../utils/parsers/lcov-parser.js';
import SarifParser from '../utils/parsers/sarif-parser.js';
import CheckstyleParser from '../utils/parsers/checkstyle-parser.js';
import fs from 'fs-extra';
import path from 'path';

/**
 * Aggregates all test and quality reports into a unified data structure
 */
export class ReportAggregator {
  constructor(config) {
    this.config = config;
    this.baseDir = config.baseDir || process.cwd();
  }

  /**
   * Collect and aggregate all reports
   * @returns {Promise<Object>} Aggregated report data
   */
  async aggregateAll() {
    console.log('Starting report aggregation...');

    const startTime = Date.now();

    // Collect all reports in parallel
    const [
      javaTestResults,
      javaCoverage,
      frontendCoverage,
      securityFindings,
      qualityViolations
    ] = await Promise.all([
      this.collectJavaTests(),
      this.collectJavaCoverage(),
      this.collectFrontendCoverage(),
      this.collectSecurityFindings(),
      this.collectQualityViolations()
    ]);

    // Build aggregated structure
    const aggregated = {
      metadata: {
        timestamp: new Date().toISOString(),
        buildNumber: process.env.GITHUB_RUN_NUMBER || process.env.BUILD_NUMBER || 'local',
        buildId: process.env.GITHUB_RUN_ID || 'local',
        branch: process.env.GITHUB_REF_NAME || this.getCurrentBranch(),
        commit: process.env.GITHUB_SHA || this.getCurrentCommit(),
        environment: process.env.CI ? 'ci' : 'local',
        generationTime: 0
      },
      summary: this.calculateOverallSummary({
        javaTestResults,
        javaCoverage,
        frontendCoverage,
        securityFindings,
        qualityViolations
      }),
      modules: this.buildModuleData({
        javaTestResults,
        javaCoverage,
        qualityViolations
      }),
      coverage: {
        java: javaCoverage,
        frontend: frontendCoverage
      },
      tests: {
        java: javaTestResults,
        frontend: {} // Will be populated by Playwright parser
      },
      security: securityFindings,
      quality: qualityViolations
    };

    aggregated.metadata.generationTime = Date.now() - startTime;

    console.log(`Report aggregation completed in ${aggregated.metadata.generationTime}ms`);

    return aggregated;
  }

  /**
   * Collect Java test results from JUnit XML files
   * @returns {Promise<Object>} Java test results
   */
  async collectJavaTests() {
    console.log('Collecting Java test results...');

    const pattern = this.config.sources?.java?.testResultsPattern || '**/build/test-results/**/*.xml';
    const reports = await JUnitParser.findAndParseReports(this.baseDir, pattern);

    if (reports.length === 0) {
      console.warn('No Java test results found');
      return this.createEmptyJavaTestResults();
    }

    const summary = JUnitParser.calculateSummary(reports);

    return {
      reports: reports,
      summary: summary
    };
  }

  /**
   * Collect Java coverage from JaCoCo reports
   * @returns {Promise<Object>} Java coverage data
   */
  async collectJavaCoverage() {
    console.log('Collecting Java coverage data...');

    const pattern = this.config.sources?.java?.coveragePattern || '**/build/reports/jacoco/test/jacocoTestReport.xml';
    const reports = await JacocoParser.findAndParseReports(this.baseDir, pattern);

    if (reports.length === 0) {
      console.warn('No Java coverage reports found');
      return this.createEmptyJavaCoverage();
    }

    const summary = JacocoParser.calculateSummary(reports);

    return {
      reports: reports,
      summary: summary
    };
  }

  /**
   * Collect frontend coverage from LCOV reports
   * @returns {Promise<Object>} Frontend coverage data
   */
  async collectFrontendCoverage() {
    console.log('Collecting frontend coverage data...');

    const pattern = this.config.sources?.frontend?.coveragePattern || '**/coverage/lcov.info';
    const reports = await LcovParser.findAndParseReports(this.baseDir, pattern);

    if (reports.length === 0) {
      console.warn('No frontend coverage reports found');
      return this.createEmptyFrontendCoverage();
    }

    // For frontend, typically just one report
    const mainReport = reports[0];

    return {
      module: mainReport.module,
      coverage: mainReport.coverage,
      topUncovered: LcovParser.getTopUncoveredFiles(mainReport.coverage, 10),
      byDirectory: LcovParser.getCoverageByDirectory(mainReport.coverage)
    };
  }

  /**
   * Collect security findings from SARIF reports
   * @returns {Promise<Object>} Security findings
   */
  async collectSecurityFindings() {
    console.log('Collecting security findings...');

    const pattern = this.config.sources?.security?.sarifPattern || '**/*.sarif';
    const reports = await SarifParser.findAndParseReports(this.baseDir, pattern);

    if (reports.length === 0) {
      console.warn('No security reports found');
      return this.createEmptySecurityFindings();
    }

    // Consolidate all SARIF reports
    const allFindings = reports.map(r => r.findings);
    const consolidated = this.consolidateSarifReports(allFindings);

    return {
      reports: reports,
      summary: consolidated.summary,
      topVulnerabilities: consolidated.topVulnerabilities,
      byFile: consolidated.byFile
    };
  }

  /**
   * Collect quality violations from Checkstyle reports
   * @returns {Promise<Object>} Quality violations
   */
  async collectQualityViolations() {
    console.log('Collecting quality violations...');

    const pattern = this.config.sources?.quality?.checkstylePattern || '**/build/reports/checkstyle/*.xml';
    const reports = await CheckstyleParser.findAndParseReports(this.baseDir, pattern);

    if (reports.length === 0) {
      console.warn('No Checkstyle reports found');
      return this.createEmptyQualityViolations();
    }

    const summary = CheckstyleParser.calculateMultiModuleSummary(reports);

    return {
      reports: reports,
      summary: summary
    };
  }

  /**
   * Calculate overall summary across all reports
   * @param {Object} data - All collected data
   * @returns {Object} Overall summary
   */
  calculateOverallSummary(data) {
    const { javaTestResults, javaCoverage, frontendCoverage, securityFindings, qualityViolations } = data;

    // Calculate test health
    const totalTests = javaTestResults.summary.overallStats.totalTests;
    const failedTests = javaTestResults.summary.overallStats.failures + javaTestResults.summary.overallStats.errors;
    const testSuccessRate = javaTestResults.summary.overallStats.successRate;

    // Calculate coverage health
    const javaCoveragePercent = javaCoverage.summary.overallCoverage.line;
    const frontendCoveragePercent = frontendCoverage.coverage?.summary.lines.percentage || 0;
    const overallCoverage = this.calculateWeightedCoverage(javaCoverage, frontendCoverage);

    // Calculate security health
    const criticalSecurity = securityFindings.summary.bySeverity.critical;
    const highSecurity = securityFindings.summary.bySeverity.high;
    const totalSecurityIssues = securityFindings.summary.totalFindings;

    // Calculate quality health
    const qualityErrors = qualityViolations.summary.overallStats.bySeverity.error;
    const qualityWarnings = qualityViolations.summary.overallStats.bySeverity.warning;

    // Determine overall health status
    const healthStatus = this.determineHealthStatus({
      testSuccessRate,
      overallCoverage,
      criticalSecurity,
      highSecurity,
      qualityErrors
    });

    return {
      healthStatus: healthStatus,
      tests: {
        total: totalTests,
        passed: totalTests - failedTests,
        failed: failedTests,
        successRate: testSuccessRate
      },
      coverage: {
        overall: overallCoverage,
        java: javaCoveragePercent,
        frontend: frontendCoveragePercent,
        target: this.config.thresholds?.coverage?.target || 85
      },
      security: {
        totalIssues: totalSecurityIssues,
        critical: criticalSecurity,
        high: highSecurity,
        medium: securityFindings.summary.bySeverity.medium,
        low: securityFindings.summary.bySeverity.low
      },
      quality: {
        totalViolations: qualityViolations.summary.overallStats.totalViolations,
        errors: qualityErrors,
        warnings: qualityWarnings
      }
    };
  }

  /**
   * Build per-module data
   * @param {Object} data - Collected data
   * @returns {Array} Module data
   */
  buildModuleData(data) {
    const { javaTestResults, javaCoverage, qualityViolations } = data;

    // Get list of all unique modules
    const moduleNames = new Set();
    javaTestResults.reports.forEach(r => moduleNames.add(r.module));
    javaCoverage.reports.forEach(r => moduleNames.add(r.module));
    qualityViolations.reports.forEach(r => moduleNames.add(r.module));

    // Build data for each module
    return Array.from(moduleNames).map(moduleName => {
      const testReport = javaTestResults.reports.find(r => r.module === moduleName);
      const coverageReport = javaCoverage.reports.find(r => r.module === moduleName);
      const qualityReport = qualityViolations.reports.find(r => r.module === moduleName);

      return {
        name: moduleName,
        tests: testReport ? {
          total: testReport.results.summary.totalTests,
          passed: testReport.results.summary.passed,
          failures: testReport.results.summary.failures,
          errors: testReport.results.summary.errors,
          successRate: testReport.results.summary.successRate
        } : null,
        coverage: coverageReport ? {
          line: coverageReport.coverage.overall.line.percentage,
          branch: coverageReport.coverage.overall.branch.percentage,
          instruction: coverageReport.coverage.overall.instruction.percentage
        } : null,
        quality: qualityReport ? {
          violations: qualityReport.violations.summary.totalViolations,
          errors: qualityReport.violations.summary.bySeverity.error,
          warnings: qualityReport.violations.summary.bySeverity.warning
        } : null,
        status: this.determineModuleStatus({
          tests: testReport?.results.summary,
          coverage: coverageReport?.coverage.overall,
          quality: qualityReport?.violations.summary
        })
      };
    });
  }

  /**
   * Consolidate multiple SARIF reports
   * @param {Array} findings - Array of SARIF findings
   * @returns {Object} Consolidated findings
   */
  consolidateSarifReports(findings) {
    const allRuns = [];
    findings.forEach(finding => {
      allRuns.push(...finding.runs);
    });

    // Recalculate summary
    const summary = SarifParser.calculateSummary(allRuns);

    // Get top vulnerabilities
    const consolidated = { runs: allRuns, summary };
    const topVulnerabilities = SarifParser.getTopVulnerabilities(consolidated, 20);
    const byFile = SarifParser.groupByFile(consolidated);

    return {
      summary,
      topVulnerabilities,
      byFile
    };
  }

  /**
   * Calculate weighted overall coverage
   * @param {Object} javaCoverage - Java coverage data
   * @param {Object} frontendCoverage - Frontend coverage data
   * @returns {number} Weighted coverage percentage
   */
  calculateWeightedCoverage(javaCoverage, frontendCoverage) {
    const javaLines = javaCoverage.summary.overallCoverage.line || 0;
    const frontendLines = frontendCoverage.coverage?.summary.lines.percentage || 0;

    // Simple average for now (could be weighted by LOC in future)
    const hasJava = javaCoverage.summary.totalModules > 0;
    const hasFrontend = frontendCoverage.coverage?.summary.totalFiles > 0;

    if (hasJava && hasFrontend) {
      return Math.round((javaLines + frontendLines) / 2 * 100) / 100;
    } else if (hasJava) {
      return javaLines;
    } else if (hasFrontend) {
      return frontendLines;
    }

    return 0;
  }

  /**
   * Determine overall health status
   * @param {Object} metrics - Health metrics
   * @returns {string} Health status: passing | warning | failing
   */
  determineHealthStatus(metrics) {
    const { testSuccessRate, overallCoverage, criticalSecurity, highSecurity, qualityErrors } = metrics;

    // Failing conditions
    if (testSuccessRate < 100) return 'failing';
    if (criticalSecurity > 0) return 'failing';
    if (qualityErrors > 0) return 'failing';

    // Warning conditions
    const coverageTarget = this.config.thresholds?.coverage?.target || 85;
    if (overallCoverage < coverageTarget) return 'warning';
    if (highSecurity > 5) return 'warning';

    return 'passing';
  }

  /**
   * Determine module status
   * @param {Object} data - Module data
   * @returns {string} Status: passing | warning | failing | unknown
   */
  determineModuleStatus(data) {
    const { tests, coverage, quality } = data;

    // Failing conditions
    if (tests && tests.successRate < 100) return 'failing';
    if (quality && quality.errors > 0) return 'failing';

    // Warning conditions
    const coverageMin = this.config.thresholds?.coverage?.minimum || 60;
    if (coverage && coverage.line.percentage < coverageMin) return 'warning';

    if (tests || coverage || quality) {
      return 'passing';
    }

    return 'unknown';
  }

  /**
   * Get current git branch
   * @returns {string} Branch name
   */
  getCurrentBranch() {
    try {
      const { execSync } = require('child_process');
      return execSync('git rev-parse --abbrev-ref HEAD', { encoding: 'utf8' }).trim();
    } catch (error) {
      return 'unknown';
    }
  }

  /**
   * Get current git commit
   * @returns {string} Commit SHA
   */
  getCurrentCommit() {
    try {
      const { execSync } = require('child_process');
      return execSync('git rev-parse HEAD', { encoding: 'utf8' }).trim();
    } catch (error) {
      return 'unknown';
    }
  }

  // Empty data structure creators
  createEmptyJavaTestResults() {
    return {
      reports: [],
      summary: {
        totalModules: 0,
        overallStats: {
          totalTests: 0,
          passed: 0,
          failures: 0,
          errors: 0,
          skipped: 0,
          totalTime: 0,
          successRate: 100
        },
        modules: [],
        failedTests: []
      }
    };
  }

  createEmptyJavaCoverage() {
    return {
      reports: [],
      summary: {
        totalModules: 0,
        overallCoverage: { line: 0, branch: 0, instruction: 0 },
        totalClasses: 0,
        totalMethods: 0,
        modules: []
      }
    };
  }

  createEmptyFrontendCoverage() {
    return {
      module: 'web-frontend',
      coverage: {
        type: 'lcov',
        files: [],
        summary: {
          totalFiles: 0,
          lines: { found: 0, hit: 0, percentage: 0 },
          functions: { found: 0, hit: 0, percentage: 0 },
          branches: { found: 0, hit: 0, percentage: 0 }
        }
      },
      topUncovered: [],
      byDirectory: []
    };
  }

  createEmptySecurityFindings() {
    return {
      reports: [],
      summary: {
        totalRuns: 0,
        totalFindings: 0,
        bySeverity: { critical: 0, high: 0, medium: 0, low: 0, info: 0 },
        byTool: {},
        uniqueRules: 0,
        affectedFiles: 0
      },
      topVulnerabilities: [],
      byFile: []
    };
  }

  createEmptyQualityViolations() {
    return {
      reports: [],
      summary: {
        totalModules: 0,
        overallStats: {
          totalFiles: 0,
          filesWithViolations: 0,
          totalViolations: 0,
          bySeverity: { error: 0, warning: 0, info: 0, ignore: 0 }
        },
        modules: [],
        topRules: []
      }
    };
  }
}

export default ReportAggregator;
