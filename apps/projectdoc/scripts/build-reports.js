#!/usr/bin/env node

/**
 * BATbern Test & Quality Reports Builder
 *
 * This script aggregates test results, coverage, security, and quality reports
 * and generates a comprehensive dashboard.
 */

import ReportAggregator from '../src/aggregators/report-aggregator.js';
import HistoryManager from '../src/aggregators/history-manager.js';
import reportsConfig from '../src/config/reports-config.js';
import fs from 'fs-extra';
import path from 'path';
import { fileURLToPath } from 'url';
import Handlebars from 'handlebars';
import { fetchZapReports } from '../src/sources/zap-fetcher.js';
import { runTrivyScan } from '../src/sources/trivy-runner.js';
import { SOURCE_LABELS } from '../src/sources/source-status.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

class ReportsBuilder {
  constructor(options = {}) {
    // Base directory for finding source files (project root)
    this.baseDir = options.baseDir || path.resolve(__dirname, '../../..');
    this.config = this.loadConfig();

    // Output directory should be in apps/projectdoc/dist/reports/
    const projectDocDir = path.resolve(__dirname, '..');
    this.outputDir = options.outputDir || path.join(projectDocDir, 'dist', 'reports');

    console.log('Reports Builder initialized');
    console.log('Base directory:', this.baseDir);
    console.log('Output directory:', this.outputDir);
  }

  /**
   * Load reports configuration
   */
  loadConfig() {
    return reportsConfig;
  }

  /**
   * Main build process
   */
  async build() {
    console.log('\n=== BATbern Reports Builder ===\n');

    try {
      // Step 0: Refresh the fetched sources. Each returns a hint saying where its data came
      // from and when it was measured; a failure here is reported on the dashboard, not fatal.
      const dastHint = await fetchZapReports({ baseDir: this.baseDir, s3Fetch: () => this.listZapFromS3() });
      const sastHint = await runTrivyScan({ baseDir: this.baseDir });

      // Step 1: Aggregate all reports
      console.log('Step 1: Aggregating reports...');
      const aggregator = new ReportAggregator({
        ...this.config,
        baseDir: this.baseDir,
        sourceHints: { dast: dastHint, sast: sastHint }
      });
      const reportData = await aggregator.aggregateAll();

      // Step 2: Save to history
      if (this.config.history.enabled) {
        console.log('\nStep 2: Saving to history...');
        const historyManager = new HistoryManager({
          historyFile: this.config.history.historyFile,
          maxHistoryEntries: this.config.history.maxHistoryEntries
        });

        // Get comparison BEFORE saving current build to history
        reportData.comparison = await historyManager.getComparison(reportData);
        reportData.trends = await historyManager.getTrendData(this.config.history.trendDataPoints);
        reportData.statistics = await historyManager.getStatistics();

        // Now save current build to history
        await historyManager.saveToHistory(reportData);
      }

      // Step 3: Generate HTML pages
      console.log('\nStep 3: Generating HTML pages...');
      await this.generatePages(reportData);

      // Step 4: Copy static assets
      console.log('\nStep 4: Copying static assets...');
      await this.copyAssets();

      // Step 4a: Copy external HTML reports
      await this.copyExternalReports();

      // Step 5: Save data files
      console.log('\nStep 5: Saving data files...');
      await this.saveDataFiles(reportData);

      console.log('\n✅ Reports build completed successfully!');
      console.log(`📁 Output directory: ${this.outputDir}`);
      console.log(`🌐 Open: ${path.join(this.outputDir, 'index.html')}\n`);

      // Return summary for CI/CD
      return {
        success: true,
        healthStatus: reportData.summary.healthStatus,
        outputDir: this.outputDir,
        summary: reportData.summary,
        sources: reportData.metadata.sources
      };

    } catch (error) {
      console.error('\n❌ Build failed:', error.message);
      console.error(error.stack);
      throw error;
    }
  }

  /**
   * Generate all HTML pages
   */
  async generatePages(reportData) {
    // Register Handlebars helpers
    this.registerHandlebarsHelpers();

    // Load templates
    const templatesDir = path.join(__dirname, '../src/templates');
    const partialsDir = path.join(templatesDir, 'partials');

    // Register partials
    const headerNavPartial = await fs.readFile(path.join(partialsDir, 'header-nav.html'), 'utf8');
    Handlebars.registerPartial('headerNav', headerNavPartial);

    const dashboardTemplate = await fs.readFile(path.join(templatesDir, 'reports-dashboard.html'), 'utf8');
    const moduleTemplate = await fs.readFile(path.join(templatesDir, 'module-detail.html'), 'utf8');
    const coverageTemplate = await fs.readFile(path.join(templatesDir, 'coverage-report.html'), 'utf8');
    const securityTemplate = await fs.readFile(path.join(templatesDir, 'security-report.html'), 'utf8');
    const qualityTemplate = await fs.readFile(path.join(templatesDir, 'quality-report.html'), 'utf8');
    const codebaseTemplate = await fs.readFile(path.join(templatesDir, 'codebase-report.html'), 'utf8');
    const zapTemplate = await fs.readFile(path.join(templatesDir, 'zap-report.html'), 'utf8');

    // Compile templates
    const compileDashboard = Handlebars.compile(dashboardTemplate);
    const compileModule = Handlebars.compile(moduleTemplate);
    const compileCoverage = Handlebars.compile(coverageTemplate);
    const compileSecurity = Handlebars.compile(securityTemplate);
    const compileQuality = Handlebars.compile(qualityTemplate);
    const compileCodebase = Handlebars.compile(codebaseTemplate);
    const compileZap = Handlebars.compile(zapTemplate);

    // Ensure output directories exist
    await fs.ensureDir(this.outputDir);
    await fs.ensureDir(path.join(this.outputDir, 'modules'));

    // Generate main dashboard
    console.log('  - Generating main dashboard...');
    const dashboardHtml = compileDashboard({
      title: this.config.dashboard.title,
      metadata: reportData.metadata,
      summary: reportData.summary,
      modules: reportData.modules,
      trends: reportData.trends,
      comparison: reportData.comparison,
      dast: reportData.dast,
      sources: reportData.metadata.sources,
      sourceList: this.sourceList(reportData.metadata.sources),
      externalLinks: this.config.externalLinks,
      category: 'reports'
    });
    await fs.writeFile(path.join(this.outputDir, 'index.html'), dashboardHtml);

    // Generate module pages
    console.log('  - Generating module pages...');
    for (const module of reportData.modules) {
      const moduleName = module.name;

      // Get detailed module data
      const moduleData = await this.getModuleDetails(moduleName, reportData);

      // Flatten module names for file paths: services/company-user-management-service -> company-user-management-service
      const flatModuleName = moduleName.split('/').pop();

      // All module pages are at /reports/modules/{flatModuleName}.html - single level, no nesting
      const pathPrefix = '../';

      const moduleHtml = compileModule({
        moduleName,
        moduleData: module,
        cssBasePath: `${pathPrefix}styles/base.css`,
        cssReportsPath: `${pathPrefix}styles/reports.css`,
        faviconPath: `../../assets/favicon.ico`,
        logoPath: `../../assets/BATbern_color_logo_transparent.png`,
        homeLink: `../../index.html`,
        userGuideLink: `../../user-guide/README.html`,
        reportsLink: `${pathPrefix}index.html`,
        apiLink: `../../api/index.html`,
        ...moduleData
      });

      // Create file path - use flattened name so all modules are at /reports/modules/{flatName}.html
      const modulePath = path.join(this.outputDir, 'modules', `${flatModuleName}.html`);
      await fs.writeFile(modulePath, moduleHtml);
    }

    // Generate coverage page
    console.log('  - Generating coverage report...');
    const coverageHtml = compileCoverage({
      summary: reportData.summary.coverage,
      javaCoverage: reportData.coverage.java,
      frontendCoverage: reportData.coverage.frontend,
      infrastructureCoverage: reportData.coverage.infrastructure,
      sources: reportData.metadata.sources,
      thresholds: this.config.thresholds.coverage
    });
    await fs.writeFile(path.join(this.outputDir, 'coverage.html'), coverageHtml);

    // Generate security page
    console.log('  - Generating security report...');
    const securityHtml = compileSecurity({
      security: reportData.security,
      securitySummary: reportData.summary.security,
      sastMeasured: typeof reportData.summary.security.totalIssues === 'number',
      dast: reportData.dast,
      sources: reportData.metadata.sources,
      externalLinks: this.config.externalLinks
    });
    await fs.writeFile(path.join(this.outputDir, 'security.html'), securityHtml);

    // Generate ZAP DAST detail page
    console.log('  - Generating ZAP DAST report...');
    const zapHtml = compileZap({ dast: reportData.dast, sources: reportData.metadata.sources });
    await fs.writeFile(path.join(this.outputDir, 'zap-report.html'), zapHtml);

    // Generate quality page
    console.log('  - Generating quality report...');
    const qualityHtml = compileQuality({
      quality: reportData.quality,
      externalLinks: this.config.externalLinks
    });
    await fs.writeFile(path.join(this.outputDir, 'quality.html'), qualityHtml);

    // Generate codebase analysis page
    console.log('  - Generating codebase analysis report...');
    const codebaseHtml = compileCodebase({
      codebase: reportData.codebase,
      summary: reportData.summary,
      externalLinks: this.config.externalLinks
    });
    await fs.writeFile(path.join(this.outputDir, 'codebase.html'), codebaseHtml);
  }


  /**
   * Get detailed module data including history
   */
  async getModuleDetails(moduleName, reportData) {
    const details = {
      failedTests: [],
      packages: [],
      topViolations: [],
      htmlReports: {},
      moduleTrend: null
    };

    // Get failed tests for this module
    if (reportData.tests.java && reportData.tests.java.summary.failedTests) {
      details.failedTests = reportData.tests.java.summary.failedTests
        .filter(test => test.module === moduleName);
    }

    // Get package coverage
    const coverageReport = reportData.coverage.java.reports.find(r => r.module === moduleName);
    if (coverageReport && coverageReport.coverage.packages) {
      details.packages = coverageReport.coverage.packages;
    }

    // Get top violations
    const qualityReport = reportData.quality.reports.find(r => r.module === moduleName);
    if (qualityReport && qualityReport.violations.summary.topViolations) {
      details.topViolations = qualityReport.violations.summary.topViolations;
    }

    // Get HTML report links - link directly to the actual reports
    // Flatten module names: services/company-user-management-service -> company-user-management-service
    const flatModuleName = moduleName.split('/').pop();

    if (moduleName === 'infrastructure') {
      details.htmlReports = {
        coverage: `../${flatModuleName}/coverage/index.html`
      };
    } else if (moduleName === 'web-frontend') {
      details.htmlReports = {
        tests: `../${flatModuleName}/test-results/index.html`,
        coverage: `../${flatModuleName}/coverage/index.html`
      };
    } else {
      details.htmlReports = {
        tests: `../${flatModuleName}/build/reports/tests/test/index.html`,
        coverage: `../${flatModuleName}/build/reports/jacoco/test/html/index.html`
      };
    }

    // Get module trend from history
    if (this.config.history.enabled) {
      const historyManager = new HistoryManager({
        historyFile: this.config.history.historyFile,
        maxHistoryEntries: this.config.history.maxHistoryEntries
      });
      details.moduleTrend = await historyManager.getModuleTrend(moduleName, 20);
    }

    return details;
  }

  /**
   * Copy static assets (CSS, images, etc.)
   */
  async copyAssets() {
    const assetsDir = path.join(__dirname, '../src/templates/styles');
    const targetDir = path.join(this.outputDir, 'styles');

    await fs.ensureDir(targetDir);
    await fs.copy(assetsDir, targetDir);

    console.log('  - Copied CSS files');
  }

  /**
   * Copy external HTML reports (test and coverage reports for all modules)
   * Flattens the structure - services/foo-service becomes foo-service
   */
  async copyExternalReports() {
    // Copy Java module HTML reports (test and coverage) to reports directory
    const javaModules = this.config.sources?.java?.modules || [];
    for (const moduleName of javaModules) {
      const moduleSourceDir = path.join(this.baseDir, moduleName, 'build', 'reports');

      // Flatten the path: services/company-user-management-service -> company-user-management-service
      const flatModuleName = moduleName.split('/').pop();
      const moduleTargetDir = path.join(this.outputDir, flatModuleName, 'build', 'reports');

      if (await fs.pathExists(moduleSourceDir)) {
        await fs.ensureDir(path.dirname(moduleTargetDir));
        await fs.copy(moduleSourceDir, moduleTargetDir);
        console.log(`  - Copied ${moduleName} HTML reports to ${flatModuleName}/`);
      } else {
        console.warn(`  ⚠️  ${moduleName} HTML reports not found at: ${moduleSourceDir}`);
      }
    }

    // Copy web-frontend test results HTML report to reports directory
    const frontendTestSource = path.join(this.baseDir, 'web-frontend', 'test-results');
    const frontendTestTarget = path.join(this.outputDir, 'web-frontend', 'test-results');

    if (await fs.pathExists(frontendTestSource)) {
      await fs.ensureDir(path.dirname(frontendTestTarget));
      await fs.copy(frontendTestSource, frontendTestTarget);
      console.log('  - Copied web-frontend test results report');
    } else {
      console.warn(`  ⚠️  Web-frontend test results report not found at: ${frontendTestSource}`);
    }

    // Copy web-frontend coverage report to reports directory
    const frontendCoverageSource = path.join(this.baseDir, 'web-frontend', 'coverage');
    const frontendCoverageTarget = path.join(this.outputDir, 'web-frontend', 'coverage');

    if (await fs.pathExists(frontendCoverageSource)) {
      await fs.ensureDir(path.dirname(frontendCoverageTarget));
      await fs.copy(frontendCoverageSource, frontendCoverageTarget);
      console.log('  - Copied web-frontend coverage report');
    } else {
      console.warn(`  ⚠️  Web-frontend coverage report not found at: ${frontendCoverageSource}`);
    }

    // Copy infrastructure (CDK) coverage HTML; Jest writes it under coverage/lcov-report/
    const infraCoverageSource = path.join(this.baseDir,
      this.config.sources?.infrastructure?.htmlCoverageDir || 'infrastructure/coverage/lcov-report');
    const infraCoverageTarget = path.join(this.outputDir, 'infrastructure', 'coverage');

    if (await fs.pathExists(infraCoverageSource)) {
      await fs.ensureDir(path.dirname(infraCoverageTarget));
      await fs.copy(infraCoverageSource, infraCoverageTarget);
      console.log('  - Copied infrastructure coverage report');
    } else {
      console.warn(`  ⚠️  Infrastructure coverage report not found at: ${infraCoverageSource}`);
    }
  }

  /**
   * Save JSON data files
   */
  async saveDataFiles(reportData) {
    // Data directory should also be in apps/projectdoc/dist/reports/data/
    const dataDir = path.join(this.outputDir, 'data');
    await fs.ensureDir(dataDir);

    // Save latest report
    await fs.writeJson(
      path.join(dataDir, 'latest.json'),
      reportData,
      { spaces: 2 }
    );
    console.log('  - Saved latest.json');

    // Save summary only
    await fs.writeJson(
      path.join(dataDir, 'summary.json'),
      {
        metadata: reportData.metadata,
        summary: reportData.summary
      },
      { spaces: 2 }
    );
    console.log('  - Saved summary.json');

    // Save trends data
    if (reportData.trends) {
      await fs.writeJson(
        path.join(dataDir, 'trends.json'),
        reportData.trends,
        { spaces: 2 }
      );
      console.log('  - Saved trends.json');
    }

    // Generate badges data
    const badgesData = this.generateBadgesData(reportData.summary);
    await fs.writeJson(
      path.join(dataDir, 'badges.json'),
      badgesData,
      { spaces: 2 }
    );
    console.log('  - Saved badges.json');
  }

  /**
   * Sources as a list for the "Data sources" table
   */
  sourceList(sources = {}) {
    return Object.entries(sources).map(([key, s]) => ({ key, label: SOURCE_LABELS[key] || key, ...s }));
  }

  /**
   * Generate badges data for shields.io or similar. A source without data says "no data".
   */
  generateBadgesData(summary) {
    const num = (v) => typeof v === 'number';
    const noData = { message: 'no data', color: 'lightgrey' };
    const securityKnown = num(summary.security.critical) && num(summary.security.high);
    return {
      tests: {
        label: 'tests',
        ...(num(summary.tests.total)
          ? { message: `${summary.tests.passed}/${summary.tests.total}`, color: summary.tests.successRate === 100 ? 'brightgreen' : 'red' }
          : noData)
      },
      coverage: {
        label: 'coverage',
        ...(num(summary.coverage.overall)
          ? { message: `${summary.coverage.overall}%`, color: this.getCoverageColor(summary.coverage.overall) }
          : noData)
      },
      security: {
        label: 'security',
        ...(securityKnown
          ? {
            message: summary.security.critical === 0 && summary.security.high === 0
              ? 'passing'
              : `${summary.security.critical + summary.security.high} issues`,
            color: summary.security.critical === 0 && summary.security.high === 0 ? 'brightgreen' : 'red'
          }
          : noData)
      },
      quality: {
        label: 'quality',
        ...(num(summary.quality.errors)
          ? { message: summary.quality.errors === 0 ? 'passing' : `${summary.quality.errors} errors`, color: summary.quality.errors === 0 ? 'brightgreen' : 'red' }
          : noData)
      },
      data: {
        label: 'data',
        message: summary.dataComplete ? 'complete' : `incomplete (${(summary.incompleteSources || []).length})`,
        color: summary.dataComplete ? 'brightgreen' : 'orange'
      },
      codebase: {
        label: 'codebase',
        message: summary.codebase?.totalLoc
          ? `${(summary.codebase.totalLoc / 1000).toFixed(1)}K loc${summary.codebase.maintainabilityRating ? ' | ' + summary.codebase.maintainabilityRating : ''}`
          : 'n/a',
        color: 'blue'
      }
    };
  }

  /**
   * Get color for coverage badge
   */
  getCoverageColor(coverage) {
    if (coverage >= 85) return 'brightgreen';
    if (coverage >= 70) return 'yellow';
    if (coverage >= 60) return 'orange';
    return 'red';
  }

  /**
   * Register Handlebars helpers
   */
  registerHandlebarsHelpers() {
    // Format date
    Handlebars.registerHelper('formatDate', (isoDate) => {
      const date = new Date(isoDate);
      return date.toLocaleString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });
    });

    // JSON stringify for embedding data in templates
    Handlebars.registerHelper('json', (context) => {
      return JSON.stringify(context);
    });

    // Conditional equality
    Handlebars.registerHelper('if_eq', function(a, b, options) {
      return a === b ? options.fn(this) : options.inverse(this);
    });

    // Greater than
    Handlebars.registerHelper('gt', (a, b) => a > b);

    // Greater than or equal
    Handlebars.registerHelper('gte', (a, b) => a >= b);

    // Less than
    Handlebars.registerHelper('lt', (a, b) => a < b);

    // Less than or equal
    Handlebars.registerHelper('lte', (a, b) => a <= b);

    // Equal
    Handlebars.registerHelper('eq', (a, b) => a === b);

    // Logical AND
    Handlebars.registerHelper('and', (a, b) => a && b);

    // Logical OR
    Handlebars.registerHelper('or', function() {
      return Array.prototype.slice.call(arguments, 0, -1).some(Boolean);
    });

    // Add numbers
    Handlebars.registerHelper('add', (a, b) => a + b);

    // Lowercase string
    Handlebars.registerHelper('lowercase', (str) => str.toLowerCase());

    // Capitalize string
    Handlebars.registerHelper('capitalize', (str) =>
      str.charAt(0).toUpperCase() + str.slice(1)
    );

    // Get object keys
    Handlebars.registerHelper('objectKeys', (obj) => Object.keys(obj));

    // Coverage class based on percentage
    Handlebars.registerHelper('coverageClass', (percentage) => {
      if (percentage >= 85) return 'coverage-good';
      if (percentage >= 70) return 'coverage-ok';
      if (percentage >= 60) return 'coverage-low';
      return 'coverage-bad';
    });

    // Flatten module name: services/company-user-management-service -> company-user-management-service
    Handlebars.registerHelper('flattenModuleName', (moduleName) => {
      if (!moduleName) return '';
      return moduleName.split('/').pop();
    });

    // Calculate skipped tests: total - passed - failures - errors
    Handlebars.registerHelper('calculateSkipped', (tests) => {
      if (!tests) return 0;
      return tests.total - tests.passed - tests.failures - tests.errors;
    });

    // Format LOC as K (e.g. 87432 → '87.4K', 1200 → '1.2K', 800 → '800')
    Handlebars.registerHelper('formatKLoc', (value) => {
      if (value === null || value === undefined) return 'no data';
      if (value === 0) return '0';
      if (value >= 1000) return `${(value / 1000).toFixed(1)}K`;
      return String(value);
    });

    // Round a float to 2 decimal places
    Handlebars.registerHelper('toFixed2', (value) => {
      if (value === null || value === undefined) return '-';
      return parseFloat(value).toFixed(2);
    });

    // Format a ratio as percentage string (0.28 → '28%')
    Handlebars.registerHelper('ratioToPercent', (value) => {
      if (value === null || value === undefined) return '-';
      return `${Math.round(parseFloat(value) * 100)}%`;
    });

    // SonarCloud rating CSS class (A → rating-a, etc.)
    Handlebars.registerHelper('ratingClass', (letter) => {
      if (!letter) return 'rating-unknown';
      const map = { A: 'rating-a', B: 'rating-b', C: 'rating-c', D: 'rating-d', E: 'rating-e' };
      return map[letter] || 'rating-unknown';
    });

    // Number with thousands separator
    Handlebars.registerHelper('formatNumber', (value) => {
      if (value === null || value === undefined) return '0';
      return parseInt(value).toLocaleString('en-US');
    });

    // ── Data-source freshness (#1226) ──────────────────────────────────────
    const esc = Handlebars.Utils.escapeExpression;
    const day = (d) => (d ? new Date(d).toISOString().slice(0, 10) : 'unknown date');
    const NO_DATA = '<span class="no-data">no data</span>';

    Handlebars.registerHelper('isNum', (v) => typeof v === 'number');

    // A number, or "no data" when the source was not measured (never 0)
    Handlebars.registerHelper('numOr', (value) =>
      new Handlebars.SafeString(typeof value === 'number' ? esc(value) : NO_DATA));

    // A percentage, or "no data"
    Handlebars.registerHelper('pct', (value) =>
      new Handlebars.SafeString(typeof value === 'number' ? `${esc(value)}%` : NO_DATA));

    Handlebars.registerHelper('formatDay', (d) => day(d));

    const badge = (source, { showFresh }) => {
      if (!source) return '';
      const title = esc([source.origin, source.note].filter(Boolean).join(' — '));
      switch (source.status) {
        case 'fresh':
          return showFresh ? `<span class="badge badge-success source-badge" title="${title}">fresh</span>` : '';
        case 'stale':
          return `<span class="badge badge-warning source-badge" title="${title}">stale since ${esc(day(source.measuredAt))}</span>`;
        case 'partial':
          return `<span class="badge badge-warning source-badge" title="${title}">partial</span>`;
        case 'missing':
          return `<span class="badge badge-error source-badge" title="${title}">no data</span>`;
        case 'not-configured':
          return `<span class="badge badge-info source-badge" title="${title}">not configured</span>`;
        default:
          return '';
      }
    };
    // Badge for a tile: nothing when fresh, otherwise stale / partial / no data / not configured
    Handlebars.registerHelper('sourceBadge', (source) => new Handlebars.SafeString(badge(source, { showFresh: false })));
    // Badge for the sources table: always shown
    Handlebars.registerHelper('sourceStatus', (source) => new Handlebars.SafeString(badge(source, { showFresh: true })));
  }

  /**
   * Fallback DAST source: the S3 prefix CI wrote to until March 2026. Returns the objects for
   * fetchZapReports, which cleans security-reports/ and marks the result as stale.
   * Uses the batbern-staging AWS profile (STAGING_PROFILE overrides).
   */
  async listZapFromS3() {
    const ZAP_BUCKET = 'batbern-content-staging';
    const ZAP_PREFIX = 'ci-reports/zap/';
    const profile = process.env.STAGING_PROFILE || 'batbern-staging';
    const { S3Client, ListObjectsV2Command, GetObjectCommand } = await import('@aws-sdk/client-s3');
    const { fromIni } = await import('@aws-sdk/credential-providers');
    const s3 = new S3Client({ region: 'eu-central-1', credentials: fromIni({ profile }) });

    const listed = await s3.send(new ListObjectsV2Command({ Bucket: ZAP_BUCKET, Prefix: ZAP_PREFIX }));
    const objects = (listed.Contents || []).filter(o => o.Key !== ZAP_PREFIX);
    const result = [];
    for (const obj of objects) {
      const res = await s3.send(new GetObjectCommand({ Bucket: ZAP_BUCKET, Key: obj.Key }));
      result.push({
        key: obj.Key.replace(ZAP_PREFIX, ''),
        body: Buffer.from(await res.Body.transformToByteArray()),
        lastModified: obj.LastModified
      });
    }
    return result;
  }
}

// Main execution
if (import.meta.url === `file://${process.argv[1]}`) {
  const builder = new ReportsBuilder();

  builder.build()
    .then(result => {
      const show = (v, suffix = '') => (typeof v === 'number' ? `${v}${suffix}` : 'no data');
      const t = result.summary.tests;
      console.log('\n📊 Build Summary:');
      console.log(`  Health Status: ${result.healthStatus}`);
      console.log(`  Tests: ${show(t.passed)}/${show(t.total)}${t.unhandledErrors ? ` (+${t.unhandledErrors} errors (unhandled))` : ''}`);
      console.log(`  Coverage: ${show(result.summary.coverage.overall, '%')} (infrastructure: ${show(result.summary.coverage.infrastructure, '%')})`);
      console.log(`  Security Issues (SAST): ${show(result.summary.security.totalIssues)}`);
      console.log(`  DAST: ${result.summary.dast ? `${result.summary.dast.scansRun} scans, ${result.summary.dast.high} high / ${result.summary.dast.medium} medium` : 'no data'}`);
      console.log(`  Quality Violations: ${show(result.summary.quality.totalViolations)}`);
      if (result.summary.codebase?.totalLoc > 0) {
        const loc = result.summary.codebase;
        console.log(`  Codebase: ${(loc.totalLoc / 1000).toFixed(1)}K LOC (prod: ${(loc.prodLoc / 1000).toFixed(1)}K, test: ${(loc.testLoc / 1000).toFixed(1)}K, ratio: ${loc.testToCodeRatio}x)${loc.techDebt ? `, debt: ${loc.techDebt}` : ''}`);
      }

      console.log('\n🗂  Data sources:');
      for (const [key, src] of Object.entries(result.sources || {})) {
        const when = src.measuredAt ? src.measuredAt.slice(0, 16).replace('T', ' ') : '-';
        console.log(`  ${(SOURCE_LABELS[key] || key).padEnd(32)} ${src.status.padEnd(15)} ${when}${src.note ? `  (${src.note})` : ''}`);
      }
      if (!result.summary.dataComplete) {
        console.warn(`\n⚠️  Data incomplete: ${result.summary.incompleteSources.join(', ')} — shown as such on the dashboard`);
      }

      // The report itself was produced. Failing health and missing sources are findings shown on
      // the dashboard, not build errors; only an exception (below) fails the command.
      if (result.healthStatus === 'failing') {
        console.warn('\n⚠️  Build health status is FAILING (see dashboard)');
      }

      process.exit(0);
    })
    .catch(error => {
      console.error('\n❌ Fatal error:', error.message);
      process.exit(1);
    });
}

export default ReportsBuilder;
