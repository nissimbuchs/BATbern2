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
import { execSync } from 'child_process';
import Handlebars from 'handlebars';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

class ReportsBuilder {
  constructor(options = {}) {
    // Base directory for finding source files (project root)
    this.baseDir = options.baseDir || path.resolve(__dirname, '../../..');
    this.config = this.loadConfig();

    // Output directory should be in apps/projectdoc/dist/reports/
    const projectDocDir = path.resolve(__dirname, '..');
    this.outputDir = path.join(projectDocDir, 'dist', 'reports');

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
      // Step 1: Aggregate all reports
      console.log('Step 1: Aggregating reports...');
      const aggregator = new ReportAggregator({
        ...this.config,
        baseDir: this.baseDir
      });
      const reportData = await aggregator.aggregateAll();

      // Step 2: Save to history
      if (this.config.history.enabled) {
        console.log('\nStep 2: Saving to history...');
        const historyManager = new HistoryManager({
          historyFile: path.join(this.outputDir, 'data', 'history.json'),
          maxHistoryEntries: this.config.history.maxHistoryEntries
        });

        await historyManager.saveToHistory(reportData);

        // Get trend data and comparison
        reportData.trends = await historyManager.getTrendData(this.config.history.trendDataPoints);
        reportData.comparison = await historyManager.getComparison(reportData);
        reportData.statistics = await historyManager.getStatistics();
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
        summary: reportData.summary
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
    const dashboardTemplate = await fs.readFile(path.join(templatesDir, 'reports-dashboard.html'), 'utf8');
    const moduleTemplate = await fs.readFile(path.join(templatesDir, 'module-detail.html'), 'utf8');
    const coverageTemplate = await fs.readFile(path.join(templatesDir, 'coverage-report.html'), 'utf8');
    const securityTemplate = await fs.readFile(path.join(templatesDir, 'security-report.html'), 'utf8');
    const qualityTemplate = await fs.readFile(path.join(templatesDir, 'quality-report.html'), 'utf8');

    // Compile templates
    const compileDashboard = Handlebars.compile(dashboardTemplate);
    const compileModule = Handlebars.compile(moduleTemplate);
    const compileCoverage = Handlebars.compile(coverageTemplate);
    const compileSecurity = Handlebars.compile(securityTemplate);
    const compileQuality = Handlebars.compile(qualityTemplate);

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
      externalLinks: this.config.externalLinks
    });
    await fs.writeFile(path.join(this.outputDir, 'index.html'), dashboardHtml);

    // Generate module pages
    console.log('  - Generating module pages...');
    for (const module of reportData.modules) {
      const moduleName = module.name;

      // Get detailed module data
      const moduleData = await this.getModuleDetails(moduleName, reportData);

      const moduleHtml = compileModule({
        moduleName,
        moduleData: module,
        ...moduleData
      });

      // Create file path - ensure subdirectories exist for nested modules
      const modulePath = path.join(this.outputDir, 'modules', `${moduleName}.html`);
      await fs.ensureDir(path.dirname(modulePath));
      await fs.writeFile(modulePath, moduleHtml);
    }

    // Generate coverage page
    console.log('  - Generating coverage report...');
    const coverageHtml = compileCoverage({
      summary: reportData.summary.coverage,
      javaCoverage: reportData.coverage.java,
      frontendCoverage: reportData.coverage.frontend,
      thresholds: this.config.thresholds.coverage
    });
    await fs.writeFile(path.join(this.outputDir, 'coverage.html'), coverageHtml);

    // Generate security page
    console.log('  - Generating security report...');
    const securityHtml = compileSecurity({
      security: reportData.security,
      externalLinks: this.config.externalLinks
    });
    await fs.writeFile(path.join(this.outputDir, 'security.html'), securityHtml);

    // Generate quality page
    console.log('  - Generating quality report...');
    const qualityHtml = compileQuality({
      quality: reportData.quality,
      externalLinks: this.config.externalLinks
    });
    await fs.writeFile(path.join(this.outputDir, 'quality.html'), qualityHtml);
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

    // Get HTML report links - different paths for frontend vs backend
    if (moduleName === 'web-frontend') {
      details.htmlReports = {
        tests: null, // Vitest doesn't generate HTML test reports by default
        coverage: `../../web-frontend/coverage/index.html`
      };
    } else {
      details.htmlReports = {
        tests: `../../${moduleName}/build/reports/tests/test/index.html`,
        coverage: `../../${moduleName}/build/reports/jacoco/test/html/index.html`
      };
    }

    // Get module trend from history
    if (this.config.history.enabled) {
      const historyManager = new HistoryManager({
        historyFile: path.join(this.outputDir, 'data', 'history.json'),
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
   * Copy external HTML reports (e.g., web-frontend coverage)
   */
  async copyExternalReports() {
    // Copy web-frontend coverage report
    const frontendCoverageSource = path.join(this.baseDir, 'web-frontend', 'coverage');
    const frontendCoverageTarget = path.join(path.dirname(this.outputDir), 'web-frontend', 'coverage');

    console.log(`  - Source: ${frontendCoverageSource}`);
    console.log(`  - Target: ${frontendCoverageTarget}`);

    if (await fs.pathExists(frontendCoverageSource)) {
      await fs.ensureDir(path.dirname(frontendCoverageTarget));
      await fs.copy(frontendCoverageSource, frontendCoverageTarget);
      console.log('  - Copied web-frontend coverage report');
    } else {
      console.warn(`  ⚠️  Web-frontend coverage report not found at: ${frontendCoverageSource}`);
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
   * Generate badges data for shields.io or similar
   */
  generateBadgesData(summary) {
    return {
      tests: {
        label: 'tests',
        message: `${summary.tests.passed}/${summary.tests.total}`,
        color: summary.tests.successRate === 100 ? 'brightgreen' : 'red'
      },
      coverage: {
        label: 'coverage',
        message: `${summary.coverage.overall}%`,
        color: this.getCoverageColor(summary.coverage.overall)
      },
      security: {
        label: 'security',
        message: summary.security.critical === 0 && summary.security.high === 0
          ? 'passing'
          : `${summary.security.critical + summary.security.high} issues`,
        color: summary.security.critical === 0 && summary.security.high === 0
          ? 'brightgreen'
          : 'red'
      },
      quality: {
        label: 'quality',
        message: summary.quality.errors === 0 ? 'passing' : `${summary.quality.errors} errors`,
        color: summary.quality.errors === 0 ? 'brightgreen' : 'red'
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
  }
}

// Main execution
if (import.meta.url === `file://${process.argv[1]}`) {
  const builder = new ReportsBuilder();

  builder.build()
    .then(result => {
      console.log('\n📊 Build Summary:');
      console.log(`  Health Status: ${result.healthStatus}`);
      console.log(`  Tests: ${result.summary.tests.passed}/${result.summary.tests.total}`);
      console.log(`  Coverage: ${result.summary.coverage.overall}%`);
      console.log(`  Security Issues: ${result.summary.security.totalIssues}`);
      console.log(`  Quality Violations: ${result.summary.quality.totalViolations}`);

      // Exit with error code if build is failing
      if (result.healthStatus === 'failing') {
        console.error('\n⚠️  Build health status is FAILING');
        process.exit(1);
      }

      process.exit(0);
    })
    .catch(error => {
      console.error('\n❌ Fatal error:', error.message);
      process.exit(1);
    });
}

export default ReportsBuilder;
