import JacocoParser from '../utils/parsers/jacoco-parser.js';
import JUnitParser from '../utils/parsers/junit-parser.js';
import LcovParser from '../utils/parsers/lcov-parser.js';
import SarifParser from '../utils/parsers/sarif-parser.js';
import CheckstyleParser from '../utils/parsers/checkstyle-parser.js';
import LocParser from '../utils/parsers/loc-parser.js';
import SonarcloudParser from '../utils/parsers/sonarcloud-parser.js';
import GitStatsParser from '../utils/parsers/git-stats-parser.js';
import ProjectCountsParser from '../utils/parsers/project-counts-parser.js';
import ZapParser from '../utils/parsers/zap-parser.js';
import fs from 'fs-extra';
import path from 'path';
import { execSync } from 'child_process';
import { classifySource, summarizeSources, newestMtime, SOURCE_KEYS } from '../sources/source-status.js';

const DEFAULT_MAX_AGE_HOURS = {
  javaTests: 24, frontendTests: 24, javaCoverage: 24, frontendCoverage: 24, infraCoverage: 24,
  sast: 24, dast: 8 * 24, quality: 24, loc: 24, sonar: 24
};

/**
 * Aggregates all test and quality reports into a unified data structure
 */
export class ReportAggregator {
  constructor(config) {
    this.config = config;
    this.baseDir = config.baseDir || process.cwd();
    // Injectable for tests: report clock, environment, and hints from the fetchers that ran
    // before aggregation (ZAP download, Trivy scan) about where their data came from.
    this.now = config.now || (() => new Date());
    this.env = config.env || process.env;
    this.sourceHints = config.sourceHints || {};
  }

  /**
   * Collect and aggregate all reports
   * @returns {Promise<Object>} Aggregated report data
   */
  async aggregateAll() {
    console.log('Starting report aggregation...');

    const startTime = Date.now();
    const reportTime = this.now();

    // Collect all reports in parallel
    const [
      javaTestResults,
      frontendTestResults,
      javaCoverage,
      frontendCoverage,
      infrastructureCoverage,
      securityFindings,
      zapDastFindings,
      qualityViolations,
      locMetrics,
      sonarMetrics,
      gitStats,
      projectCounts
    ] = await Promise.all([
      this.collectJavaTests(),
      this.collectFrontendTests(),
      this.collectJavaCoverage(),
      this.collectFrontendCoverage(),
      this.collectInfrastructureCoverage(),
      this.collectSecurityFindings(),
      this.collectZapDastFindings(),
      this.collectQualityViolations(),
      this.collectLocMetrics(),
      this.collectSonarCloudMetrics(),
      this.collectGitStats(),
      this.collectProjectCounts()
    ]);

    const sources = await this.buildSources({
      reportTime,
      javaTestResults,
      frontendTestResults,
      javaCoverage,
      frontendCoverage,
      infrastructureCoverage,
      securityFindings,
      zapDastFindings,
      qualityViolations,
      locMetrics,
      sonarMetrics
    });

    // Build aggregated structure
    const aggregated = {
      metadata: {
        timestamp: reportTime.toISOString(),
        buildNumber: process.env.GITHUB_RUN_NUMBER || process.env.BUILD_NUMBER || 'local',
        buildId: process.env.GITHUB_RUN_ID || 'local',
        branch: process.env.GITHUB_REF_NAME || this.getCurrentBranch(),
        commit: process.env.GITHUB_SHA || this.getCurrentCommit(),
        environment: process.env.CI ? 'ci' : 'local',
        generationTime: 0,
        sources
      },
      summary: this.calculateOverallSummary({
        sources,
        zapDastFindings,
        javaTestResults,
        frontendTestResults,
        javaCoverage,
        frontendCoverage,
        infrastructureCoverage,
        securityFindings,
        qualityViolations,
        locMetrics,
        sonarMetrics,
        gitStats,
        projectCounts
      }),
      modules: this.buildModuleData({
        javaTestResults,
        frontendTestResults,
        javaCoverage,
        qualityViolations,
        frontendCoverage,
        infrastructureCoverage
      }),
      coverage: {
        java: javaCoverage,
        frontend: frontendCoverage,
        infrastructure: infrastructureCoverage
      },
      tests: {
        java: javaTestResults,
        frontend: frontendTestResults
      },
      security: securityFindings,
      dast: zapDastFindings,
      quality: qualityViolations,
      codebase: {
        loc: locMetrics,
        sonar: sonarMetrics,
        git: gitStats,
        counts: projectCounts
      }
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
   * Collect frontend test results from Vitest JUnit XML files
   * @returns {Promise<Object>} Frontend test results
   */
  async collectFrontendTests() {
    console.log('Collecting frontend test results...');

    const pattern = this.config.sources?.frontend?.testResultsPattern || 'web-frontend/test-results/**/*.xml';
    const reports = await JUnitParser.findAndParseReports(this.baseDir, pattern);

    if (reports.length === 0) {
      console.warn('No frontend test results found');
      return this.createEmptyFrontendTestResults();
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
      reportPath: mainReport.reportPath,
      coverage: mainReport.coverage,
      topUncovered: LcovParser.getTopUncoveredFiles(mainReport.coverage, 10),
      byDirectory: LcovParser.getCoverageByDirectory(mainReport.coverage)
    };
  }

  /**
   * Collect infrastructure (CDK) coverage from the Jest LCOV report.
   * The infrastructure has no JUnit reporter, so it contributes coverage only, no test counts.
   * @returns {Promise<Object>} Infrastructure coverage data, same shape as the frontend's
   */
  async collectInfrastructureCoverage() {
    console.log('Collecting infrastructure coverage data...');

    const pattern = this.config.sources?.infrastructure?.coveragePattern || 'infrastructure/coverage/lcov.info';
    const reports = await LcovParser.findAndParseReports(this.baseDir, pattern);

    if (reports.length === 0) {
      console.warn('No infrastructure coverage reports found');
      return this.createEmptyFrontendCoverage('infrastructure');
    }

    const mainReport = reports[0];

    return {
      module: 'infrastructure',
      reportPath: mainReport.reportPath,
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
   * Collect OWASP ZAP DAST scan results from JSON report files.
   * Returns null gracefully when no ZAP reports are present.
   * @returns {Promise<Object|null>} ZAP findings or null
   */
  async collectZapDastFindings() {
    console.log('Collecting ZAP DAST findings...');

    const pattern = this.config.sources?.security?.zapPattern || 'security-reports/zap-*.json';
    let reports;
    try {
      reports = await ZapParser.findAndParseReports(this.baseDir, pattern);
    } catch (error) {
      console.warn('ZAP DAST collection failed:', error.message);
      return null;
    }

    if (reports.length === 0) {
      console.warn('No ZAP DAST reports found — section will be hidden in dashboard');
      return null;
    }

    const summary      = ZapParser.calculateOverallSummary(reports);
    const byAlertType  = ZapParser.groupByAlertType(reports);

    // Per-rule breakdown: prefer log files (PASS/WARN/FAIL); fall back to JSON alert data
    const logPattern = this.config.sources?.security?.zapLogPattern || 'security-reports/zap-logs/*.log';
    let categoryMatrix = null;
    try {
      const logScans = await ZapParser.findAndParseLogFiles(this.baseDir, logPattern);
      if (logScans.length > 0) {
        categoryMatrix = ZapParser.buildCategoryMatrix(logScans);
      } else {
        console.log('  ZAP log files not found — building category matrix from JSON alerts instead.');
        categoryMatrix = ZapParser.buildCategoryMatrixFromJson(reports);
      }
    } catch (err) {
      console.warn('ZAP per-rule breakdown failed:', err.message);
      categoryMatrix = ZapParser.buildCategoryMatrixFromJson(reports);
    }

    return {
      reports,
      summary,
      byAlertType,
      categoryMatrix,
      scannedAt: reports[0]?.findings?.generatedDate || null
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
    const { sources = {}, zapDastFindings, javaTestResults, frontendTestResults, javaCoverage, frontendCoverage, infrastructureCoverage, securityFindings, qualityViolations, locMetrics, sonarMetrics, gitStats, projectCounts } = data;

    // A source without data contributes null, never 0 (#1226). Stale data still counts: it is
    // shown with a "stale since" badge.
    const has = (key) => sources[key] ? !['missing', 'not-configured'].includes(sources[key].status) : true;

    // Tests: combine the test sources that exist
    const testSets = [
      has('javaTests') ? javaTestResults.summary.overallStats : null,
      has('frontendTests') ? frontendTestResults.summary.overallStats : null
    ].filter(Boolean);
    let tests;
    if (testSets.length === 0) {
      tests = { total: null, passed: null, failed: null, skipped: null, unhandledErrors: null, successRate: null };
    } else {
      const sum = (f) => testSets.reduce((acc, t) => acc + (t[f] || 0), 0);
      const total = sum('totalTests');
      const passed = sum('passed');
      const skipped = sum('skipped');
      // Success rate = passed / (total - skipped) to exclude skipped tests
      const executed = total - skipped;
      tests = {
        total,
        passed,
        failed: sum('failures') + sum('errors'),
        skipped,
        unhandledErrors: sum('unhandledErrors'),
        successRate: executed > 0 ? Math.round((passed / executed) * 10000) / 100 : null
      };
    }

    // Coverage
    const javaCoveragePercent = has('javaCoverage') ? javaCoverage.summary.overallCoverage.line : null;
    const frontendCoveragePercent = has('frontendCoverage') ? (frontendCoverage.coverage?.summary.lines.percentage ?? null) : null;
    // Shown on its own; the overall figure stays Java + frontend so the history trend remains comparable
    const infrastructureCoveragePercent = has('infraCoverage') ? (infrastructureCoverage?.coverage?.summary.lines.percentage ?? null) : null;
    const overallCoverage = this.calculateWeightedCoverage(javaCoverage, frontendCoverage);

    // Security (SAST)
    const sast = has('sast');
    const sev = securityFindings.summary.bySeverity;
    const security = {
      totalIssues: sast ? securityFindings.summary.totalFindings : null,
      critical: sast ? sev.critical : null,
      high: sast ? sev.high : null,
      medium: sast ? sev.medium : null,
      low: sast ? sev.low : null
    };

    // DAST
    const dast = has('dast') && zapDastFindings ? {
      scansRun: zapDastFindings.summary.scansRun,
      high: zapDastFindings.summary.high,
      medium: zapDastFindings.summary.medium,
      low: zapDastFindings.summary.low,
      info: zapDastFindings.summary.info
    } : null;

    // Quality
    const qualityPresent = has('quality');
    const qs = qualityViolations.summary.overallStats;
    const quality = {
      totalViolations: qualityPresent ? qs.totalViolations : null,
      errors: qualityPresent ? qs.bySeverity.error : null,
      warnings: qualityPresent ? qs.bySeverity.warning : null
    };

    const completeness = summarizeSources(sources);

    // Determine overall health status
    let healthStatus = this.determineHealthStatus({
      testSuccessRate: tests.successRate,
      overallCoverage,
      criticalSecurity: security.critical,
      highSecurity: security.high,
      qualityErrors: quality.errors,
      unhandledErrors: tests.unhandledErrors
    });
    const healthReasons = [];
    if (!completeness.dataComplete) {
      // Missing or stale data is not evidence of health
      if (healthStatus === 'passing') healthStatus = 'warning';
      healthReasons.push(`incomplete data: ${completeness.incomplete.map(i => `${i.label} ${i.status}`).join(', ')}`);
    }
    if (tests.unhandledErrors > 0) {
      healthReasons.push(`${tests.unhandledErrors} unhandled test error(s)`);
    }

    return {
      healthStatus: healthStatus,
      healthReasons,
      dataComplete: completeness.dataComplete,
      incompleteSources: completeness.incomplete.map(i => i.key),
      incompleteDetails: completeness.incomplete,
      notConfiguredSources: completeness.notConfigured,
      tests,
      coverage: {
        overall: overallCoverage,
        java: javaCoveragePercent,
        frontend: frontendCoveragePercent,
        infrastructure: infrastructureCoveragePercent,
        target: this.config.thresholds?.coverage?.target || 85
      },
      security,
      dast,
      quality,
      codebase: {
        totalLoc: has('loc') ? (locMetrics?.summary?.totalLoc || 0) : null,
        prodLoc: locMetrics?.summary?.prodLoc || 0,
        testLoc: locMetrics?.summary?.testLoc || 0,
        testToCodeRatio: locMetrics?.summary?.testToCodeRatio || 0,
        techDebt: sonarMetrics?.techDebtFormatted || null,
        techDebtMinutes: sonarMetrics?.techDebtMinutes || null,
        duplicationPct: sonarMetrics?.duplicationPct || null,
        maintainabilityRating: sonarMetrics?.maintainabilityRating || null,
        commitsLast30Days: gitStats?.commitsLast30Days || 0,
        totalTodos: projectCounts?.todos?.total || 0,
        totalMigrations: projectCounts?.totalMigrations || 0
      }
    };
  }

  /**
   * Build per-module data
   * @param {Object} data - Collected data
   * @returns {Array} Module data
   */
  buildModuleData(data) {
    const { javaTestResults, frontendTestResults, javaCoverage, qualityViolations, frontendCoverage, infrastructureCoverage } = data;

    // Get list of all unique modules
    const moduleNames = new Set();
    javaTestResults.reports.forEach(r => moduleNames.add(r.module));
    javaCoverage.reports.forEach(r => moduleNames.add(r.module));
    qualityViolations.reports.forEach(r => moduleNames.add(r.module));

    // Build data for each Java module
    const modules = Array.from(moduleNames).map(moduleName => {
      // Aggregate ALL test reports for this module (not just the first one)
      const testReports = javaTestResults.reports.filter(r => r.module === moduleName);
      const coverageReport = javaCoverage.reports.find(r => r.module === moduleName);
      const qualityReport = qualityViolations.reports.find(r => r.module === moduleName);

      // Aggregate test data from all test report files for this module
      let aggregatedTests = null;
      if (testReports.length > 0) {
        const totals = {
          total: 0,
          passed: 0,
          failures: 0,
          errors: 0,
          skipped: 0
        };

        testReports.forEach(report => {
          totals.total += report.results.summary.totalTests;
          totals.passed += report.results.summary.passed;
          totals.failures += report.results.summary.failures;
          totals.errors += report.results.summary.errors;
          totals.skipped += report.results.summary.skipped;
        });

        const executedTests = totals.total - totals.skipped;
        aggregatedTests = {
          total: totals.total,
          passed: totals.passed,
          failures: totals.failures,
          errors: totals.errors,
          successRate: executedTests > 0
            ? Math.round((totals.passed / executedTests) * 10000) / 100
            : 0
        };
      }

      return {
        name: moduleName,
        tests: aggregatedTests,
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
          tests: aggregatedTests,
          coverage: coverageReport?.coverage.overall,
          quality: qualityReport?.violations.summary
        })
      };
    });

    // Add web-frontend module if coverage or test data exists
    if (frontendCoverage.coverage && frontendCoverage.coverage.summary.totalFiles > 0) {
      const frontendTestData = frontendTestResults.summary.overallStats.totalTests > 0 ? {
        total: frontendTestResults.summary.overallStats.totalTests,
        passed: frontendTestResults.summary.overallStats.passed,
        failures: frontendTestResults.summary.overallStats.failures,
        errors: frontendTestResults.summary.overallStats.errors,
        successRate: frontendTestResults.summary.overallStats.successRate
      } : null;

      modules.push({
        name: 'web-frontend',
        tests: frontendTestData,
        coverage: {
          line: frontendCoverage.coverage.summary.lines.percentage,
          branch: frontendCoverage.coverage.summary.branches.percentage,
          instruction: frontendCoverage.coverage.summary.functions.percentage
        },
        quality: null, // No Checkstyle for frontend
        status: this.determineModuleStatus({
          tests: frontendTestData ? {
            successRate: frontendTestData.successRate,
            totalTests: frontendTestData.total
          } : null,
          coverage: {
            line: { percentage: frontendCoverage.coverage.summary.lines.percentage }
          },
          quality: null
        })
      });
    }

    // Add infrastructure (CDK) module: coverage only, its Jest run has no JUnit reporter
    if (infrastructureCoverage?.coverage && infrastructureCoverage.coverage.summary.totalFiles > 0) {
      const infraSummary = infrastructureCoverage.coverage.summary;
      modules.push({
        name: 'infrastructure',
        tests: null,
        coverage: {
          line: infraSummary.lines.percentage,
          branch: infraSummary.branches.percentage,
          instruction: infraSummary.functions.percentage
        },
        quality: null,
        status: this.determineModuleStatus({
          tests: null,
          coverage: { line: { percentage: infraSummary.lines.percentage } },
          quality: null
        })
      });
    }

    return modules;
  }

  /**
   * Collect Lines of Code metrics using cloc
   * @returns {Promise<Object>} LOC report object
   */
  async collectLocMetrics() {
    console.log('Collecting LOC metrics...');

    const locConfig = this.config.loc;
    if (!locConfig || !locConfig.enabled) {
      console.warn('LOC collection disabled');
      return LocParser.emptyReport();
    }

    const startTime = Date.now();
    const excludeDirs = locConfig.excludeDirs || [];
    const javaModules = this.config.sources?.java?.modules || [];
    const zones = [];

    try {
      // Backend Services — derive prod/test dirs from java modules list
      const backendProd = javaModules.map(m => path.join(this.baseDir, m, 'src/main/java'));
      const backendTest = javaModules.map(m => path.join(this.baseDir, m, 'src/test/java'));
      zones.push(LocParser.analyzeZone(
        'backendServices', locConfig.zones.backendServices?.label || 'Backend Services',
        backendProd, backendTest, excludeDirs, this.baseDir
      ));

      // Other zones from config (regular + generated)
      const otherZones = ['frontend', 'infrastructure', 'apps', 'scripts', 'generated'];
      for (const zoneId of otherZones) {
        const zoneCfg = locConfig.zones[zoneId];
        if (!zoneCfg) continue;
        // Generated zones can override the excludeDirs list (e.g. to NOT exclude 'generated' dirname)
        const zoneExcludeDirs = zoneCfg.excludeDirsOverride || excludeDirs;
        const prodDirs = (zoneCfg.prod || []).map(d => path.join(this.baseDir, d));
        const testDirs = (zoneCfg.test || []).map(d => path.join(this.baseDir, d));
        const zone = LocParser.analyzeZone(
          zoneId, zoneCfg.label,
          prodDirs, testDirs, zoneExcludeDirs, this.baseDir,
          { notMatchFileProd: zoneCfg.notMatchFileProd, matchFileTest: zoneCfg.matchFileTest }
        );
        if (zoneCfg.isGenerated) zone.isGenerated = true;
        zones.push(zone);
      }

      const clocVersion = LocParser.getClocVersion(this.baseDir);
      return LocParser.buildReport(zones, clocVersion, Date.now() - startTime);
    } catch (error) {
      console.warn('LOC collection failed:', error.message);
      return LocParser.emptyReport();
    }
  }

  /**
   * Fetch quality metrics from SonarCloud API
   * @returns {Promise<Object|null>} SonarCloud metrics or null
   */
  async collectSonarCloudMetrics() {
    console.log('Collecting SonarCloud metrics...');
    if (!this.isSonarConfigured()) {
      console.warn('SonarCloud not configured (disabled or SONAR_TOKEN not set)');
      return null;
    }
    try {
      return await SonarcloudParser.fetchMetrics(this.config.sonarcloud);
    } catch (error) {
      console.warn('SonarCloud metrics fetch failed:', error.message);
      return null;
    }
  }

  /**
   * Collect git repository activity statistics
   * @returns {Promise<Object>} Git stats object
   */
  async collectGitStats() {
    console.log('Collecting git stats...');
    try {
      return GitStatsParser.collectStats(this.baseDir);
    } catch (error) {
      console.warn('Git stats collection failed:', error.message);
      return GitStatsParser.emptyStats();
    }
  }

  /**
   * Collect project counts: TODO/FIXME, dependencies, migrations
   * @returns {Promise<Object>} Project counts object
   */
  async collectProjectCounts() {
    try {
      const javaModules = this.config.sources?.java?.modules || [];
      const excludeDirs = this.config.loc?.excludeDirs || [];
      return await ProjectCountsParser.collectAll(this.baseDir, javaModules, excludeDirs);
    } catch (error) {
      console.warn('Project counts collection failed:', error.message);
      return ProjectCountsParser.emptyResult();
    }
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

    // No coverage measured at all: no figure, not 0%
    return null;
  }

  /**
   * Determine overall health status
   * @param {Object} metrics - Health metrics
   * @returns {string} Health status: passing | warning | failing
   */
  determineHealthStatus(metrics) {
    // A metric that was not measured (null) is skipped here; data completeness is judged
    // separately in calculateOverallSummary and caps the status at "warning".
    const num = (v) => typeof v === 'number';
    const testSuccessRate = num(metrics.testSuccessRate) ? metrics.testSuccessRate : 100;
    const overallCoverage = num(metrics.overallCoverage) ? metrics.overallCoverage : Infinity;
    const criticalSecurity = num(metrics.criticalSecurity) ? metrics.criticalSecurity : 0;
    const highSecurity = num(metrics.highSecurity) ? metrics.highSecurity : 0;
    const qualityErrors = num(metrics.qualityErrors) ? metrics.qualityErrors : 0;
    const unhandledErrors = num(metrics.unhandledErrors) ? metrics.unhandledErrors : 0;

    // Get thresholds from config
    const coverageMinimum = this.config.thresholds?.coverage?.minimum || 60;
    const coverageTarget = this.config.thresholds?.coverage?.target || 85;
    const minTestSuccessRate = this.config.thresholds?.tests?.minSuccessRate || 95;
    const maxQualityErrors = this.config.thresholds?.quality?.errors || 10;

    // Failing conditions (critical issues only)
    if (testSuccessRate < minTestSuccessRate) return 'failing';
    if (overallCoverage < coverageMinimum) return 'failing';
    if (criticalSecurity > 0) return 'failing';
    if (qualityErrors > maxQualityErrors) return 'failing';

    // Warning conditions (below target but above minimum)
    if (testSuccessRate < 100) return 'warning';
    if (overallCoverage < coverageTarget) return 'warning';
    if (highSecurity > 5) return 'warning';
    if (qualityErrors > 0) return 'warning';
    if (unhandledErrors > 0) return 'warning';

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

  isSonarConfigured() {
    return Boolean(this.config.sonarcloud?.enabled && this.env.SONAR_TOKEN);
  }

  /**
   * Freshness of every data source (#1226): status, measurement time, origin.
   * Report files are dated by their mtime; fetched sources (DAST, SAST) by the hint their
   * fetcher returned, or by the marker file it wrote.
   * @returns {Promise<Object>} key -> { status, measuredAt, origin, note? }
   */
  async buildSources(data) {
    const { reportTime } = data;
    const maxAge = { ...DEFAULT_MAX_AGE_HOURS, ...(this.config.freshness?.maxAgeHours || {}) };
    const abs = (rel) => path.join(this.baseDir, rel);
    const fromFiles = async (key, reportPaths, originLabel) => {
      const paths = reportPaths.filter(Boolean);
      return classifySource({
        present: paths.length > 0,
        measuredAt: await newestMtime(paths.map(abs)),
        now: reportTime,
        maxAgeHours: maxAge[key],
        origin: paths.length ? `${originLabel} (${paths.length} file${paths.length > 1 ? 's' : ''}, file time)` : originLabel
      });
    };
    const readMarker = async (name) => {
      try {
        return await fs.readJson(path.join(this.baseDir, 'security-reports', name));
      } catch {
        return null;
      }
    };

    const sources = {};
    sources.javaTests = await fromFiles('javaTests', data.javaTestResults.reports.map(r => r.reportPath), 'JUnit XML');
    sources.frontendTests = await fromFiles('frontendTests', data.frontendTestResults.reports.map(r => r.reportPath), 'Vitest JUnit XML');
    sources.javaCoverage = await fromFiles('javaCoverage', data.javaCoverage.reports.map(r => r.reportPath), 'JaCoCo XML');
    sources.frontendCoverage = await fromFiles('frontendCoverage',
      data.frontendCoverage.coverage?.summary.totalFiles > 0 ? [data.frontendCoverage.reportPath] : [], 'web-frontend LCOV');
    sources.infraCoverage = await fromFiles('infraCoverage',
      data.infrastructureCoverage?.coverage?.summary.totalFiles > 0 ? [data.infrastructureCoverage.reportPath] : [], 'infrastructure LCOV');
    sources.quality = await fromFiles('quality', data.qualityViolations.reports.map(r => r.reportPath), 'Checkstyle XML');

    // SAST: Trivy runner hint, else its marker, else SARIF file times
    const sarifPaths = data.securityFindings.reports.map(r => r.reportPath);
    const sastHint = this.sourceHints.sast || (await readMarker('sast-source.json'));
    sources.sast = classifySource({
      present: sarifPaths.length > 0,
      measuredAt: sastHint?.measuredAt ?? (await newestMtime(sarifPaths.map(abs))),
      now: reportTime,
      maxAgeHours: maxAge.sast,
      forceStatus: sastHint?.status,
      origin: sastHint?.origin || (sarifPaths.length ? 'SARIF files (file time)' : 'SARIF'),
      note: sastHint?.note
    });

    // DAST: ZAP fetcher hint, else its marker, else the reports' own @generated date
    const zap = data.zapDastFindings;
    const dastHint = this.sourceHints.dast || (await readMarker('dast-source.json'));
    let zapGenerated = null;
    for (const r of zap?.reports || []) {
      const d = r.findings?.generatedDate ? new Date(r.findings.generatedDate) : null;
      if (d && !Number.isNaN(d.getTime()) && (!zapGenerated || d > zapGenerated)) zapGenerated = d;
    }
    sources.dast = classifySource({
      present: Boolean(zap && zap.reports?.length),
      measuredAt: dastHint?.measuredAt ?? zapGenerated,
      now: reportTime,
      maxAgeHours: maxAge.dast,
      forceStatus: dastHint?.forceStatus,
      origin: dastHint?.origin || 'ZAP JSON (report date)',
      note: dastHint?.note
    });

    // LOC: measured live by cloc in this run
    sources.loc = classifySource({
      present: (data.locMetrics?.summary?.totalLoc || 0) > 0,
      measuredAt: reportTime,
      now: reportTime,
      maxAgeHours: maxAge.loc,
      configured: Boolean(this.config.loc?.enabled),
      origin: 'cloc (this run)'
    });

    // SonarCloud: API call in this run, needs SONAR_TOKEN
    const sonarConfigured = this.isSonarConfigured();
    sources.sonar = classifySource({
      present: Boolean(data.sonarMetrics),
      measuredAt: reportTime,
      now: reportTime,
      maxAgeHours: maxAge.sonar,
      configured: sonarConfigured,
      origin: 'SonarCloud API',
      note: sonarConfigured ? (data.sonarMetrics ? null : 'API call failed') : 'SONAR_TOKEN not set'
    });

    // Stable key order
    return Object.fromEntries(SOURCE_KEYS.map(k => [k, sources[k]]));
  }

  /**
   * Get current git branch
   * @returns {string} Branch name
   */
  getCurrentBranch() {
    try {
      return execSync('git rev-parse --abbrev-ref HEAD', {
        encoding: 'utf8',
        cwd: this.baseDir
      }).trim();
    } catch (error) {
      console.warn('Failed to get git branch:', error.message);
      return 'unknown';
    }
  }

  /**
   * Get current git commit
   * @returns {string} Commit SHA
   */
  getCurrentCommit() {
    try {
      return execSync('git rev-parse HEAD', {
        encoding: 'utf8',
        cwd: this.baseDir
      }).trim();
    } catch (error) {
      console.warn('Failed to get git commit:', error.message);
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

  createEmptyFrontendTestResults() {
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

  createEmptyFrontendCoverage(module = 'web-frontend') {
    return {
      module,
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
