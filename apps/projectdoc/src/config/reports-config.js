/**
 * Configuration for test and quality reports aggregation
 */
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default {
  // Base directory for report scanning (defaults to project root)
  baseDir: process.cwd(),

  // Source patterns for different report types
  sources: {
    java: {
      // JUnit test results
      testResultsPattern: '**/build/test-results/**/*.xml',

      // JaCoCo coverage reports
      coveragePattern: '**/build/reports/jacoco/test/jacocoTestReport.xml',

      // HTML reports (for linking)
      htmlReportsPattern: '**/build/reports/tests/test/index.html',
      htmlCoveragePattern: '**/build/reports/jacoco/test/html/index.html',

      // Modules to scan (with correct paths for nested services)
      modules: [
        'shared-kernel',
        'api-gateway',
        'services/event-management-service',
        'services/speaker-coordination-service',
        'services/partner-coordination-service',
        'services/attendee-experience-service',
        'services/company-user-management-service'
      ]
    },

    frontend: {
      // Vitest JUnit test results
      testResultsPattern: 'web-frontend/test-results/junit.xml',

      // LCOV coverage reports
      coveragePattern: '**/coverage/lcov.info',

      // HTML coverage reports
      htmlCoveragePattern: '**/coverage/index.html',

      // Playwright test results
      playwrightPattern: '**/playwright-report/**',
      playwrightJsonPattern: '**/test-results/**/*.json',

      // Vitest results (if available)
      vitestPattern: '**/coverage/coverage-final.json',

      // Module path
      module: 'web-frontend'
    },

    security: {
      // SARIF security scan results (Trivy generates these)
      sarifPattern: 'security-reports/**/*.sarif',

      // Trivy results (fallback pattern)
      trivyPattern: 'trivy-results.sarif',

      // Snyk results (if available)
      snykPattern: '**/snyk-*.json',

      // OWASP ZAP DAST scan results (report_json.json renamed to zap-{scan}.json)
      zapPattern: 'security-reports/zap-*.json'
    },

    quality: {
      // Checkstyle reports
      checkstylePattern: '**/build/reports/checkstyle/*.xml',

      // ESLint results (if available)
      eslintPattern: '**/eslint-report.json',

      // PMD/SpotBugs (if available)
      pmdPattern: '**/build/reports/pmd/*.xml',
      spotbugsPattern: '**/build/reports/spotbugs/*.xml'
    }
  },

  // Quality thresholds
  thresholds: {
    coverage: {
      target: 85,    // Target coverage percentage (CLAUDE.md goal)
      minimum: 60,   // Minimum enforced in Gradle (all Java modules)
      warning: 75    // Frontend enforced threshold (Vitest statements)
    },

    tests: {
      failureRate: 0,           // Maximum allowed test failure rate (0 = no failures) - for "passing" status
      minSuccessRate: 95        // Minimum test success rate for "warning" (< 95% = failing, 95-99% = warning, 100% = passing)
    },

    security: {
      critical: 0,   // Maximum critical vulnerabilities
      high: 5,       // Maximum high vulnerabilities
      medium: 20     // Maximum medium vulnerabilities
    },

    quality: {
      errors: 10,    // Maximum quality errors for "warning" (> 10 = failing, 1-10 = warning, 0 = passing)
      warnings: 50   // Maximum quality warnings
    }
  },

  // SonarCloud integration (optional)
  sonarcloud: {
    enabled: true,
    organization: 'nissimbuchs',
    projectKey: 'nissimbuchs_BATbern2',
    apiUrl: 'https://sonarcloud.io/api',
    // API token should be provided via environment variable: SONAR_TOKEN
    modules: [
      'shared-kernel',
      'api-gateway',
      'services/event-management-service',
      'services/attendee-experience-service',
      'services/company-user-management-service',
      'services/partner-coordination-service',
      'services/speaker-coordination-service',
      'web-frontend'
    ]
  },

  // Lines of Code analysis configuration
  loc: {
    enabled: true,
    excludeDirs: [
      'node_modules', 'build', 'dist', '.gradle', 'coverage',
      'test-results', 'playwright-report', '.git', 'generated', 'out'
    ],
    // Zone definitions — backendServices paths are derived at runtime from sources.java.modules
    zones: {
      backendServices: {
        label: 'Backend Services'
        // prodDirs: derived from sources.java.modules + '/src/main/java'
        // testDirs: derived from sources.java.modules + '/src/test/java'
      },
      frontend: {
        label: 'Web Frontend',
        prod: ['web-frontend/src'],
        test: ['web-frontend/e2e'],
        // Exclude inline unit test files from prod count, count them as test
        notMatchFileProd: '\\.(test|spec)\\.(ts|tsx)$',
        matchFileTest: '\\.(test|spec)\\.(ts|tsx)$'
      },
      infrastructure: {
        label: 'Infrastructure (CDK)',
        prod: ['infrastructure/lib', 'infrastructure/bin'],
        test: ['infrastructure/test']
      },
      apps: {
        label: 'Companion Apps',
        prod: [
          'apps/BATbern-watch/BATbern-watch Watch App',
          'apps/workhours',
          'apps/migration-analysis/src',
          'apps/projectdoc/src'
        ],
        test: [
          'apps/BATbern-watch/BATbern-watch Watch AppTests'
        ]
      },
      scripts: {
        label: 'Scripts & Automation',
        prod: ['scripts'],
        test: []
      },
      generated: {
        label: 'Generated Code (OpenAPI Types)',
        isGenerated: true,
        prod: ['web-frontend/src/types/generated'],
        test: [],
        // 'generated' dir name intentionally omitted so cloc can enter the directory
        excludeDirsOverride: [
          'node_modules', 'build', 'dist', '.gradle', 'coverage',
          'test-results', 'playwright-report', '.git', 'out'
        ]
      }
    }
  },

  // Historical data configuration
  history: {
    enabled: true,
    // Shared history location: environment variable override > shared user location > local fallback
    historyFile: process.env.BATBERN_HISTORY_FILE
      || path.join(process.env.HOME || process.env.USERPROFILE, '.batbern', 'projectdoc-history.json')
      || 'dist/reports/data/history.json',
    maxHistoryEntries: 50,      // Keep last 50 builds
    trendDataPoints: 8          // Number of days to show in trend charts (one build per day)
  },

  // Output configuration
  output: {
    distDir: 'dist/reports',
    dataDir: 'dist/reports/data',
    archivesDir: 'dist/reports/archives',

    // Generate these output files
    files: {
      latest: 'dist/reports/data/latest.json',           // Latest report data
      summary: 'dist/reports/data/summary.json',         // Summary only
      trends: 'dist/reports/data/trends.json',           // Trend data for charts
      badges: 'dist/reports/data/badges.json'            // Badge data
    }
  },

  // Dashboard configuration
  dashboard: {
    title: 'BATbern Test & Quality Dashboard',
    description: 'Comprehensive test results, coverage, security, and quality metrics',

    // Sections to include in dashboard
    sections: {
      overview: true,        // Overall health and key metrics
      tests: true,           // Test results
      coverage: true,        // Code coverage
      security: true,        // Security findings
      quality: true,         // Code quality violations
      trends: true,          // Historical trends
      modules: true          // Per-module details
    },

    // Chart configuration
    charts: {
      enabled: true,
      library: 'chartjs',    // chartjs | d3
      responsive: true,
      animations: true
    },

    // Table configuration
    tables: {
      pageSize: 10,
      sortable: true,
      filterable: true
    }
  },

  // Badge generation
  badges: {
    enabled: true,
    style: 'flat',           // flat | flat-square | plastic | for-the-badge | social

    // Generate these badges
    generate: [
      'tests',               // Test pass/fail badge
      'coverage',            // Coverage percentage badge
      'security',            // Security status badge
      'quality'              // Quality grade badge
    ]
  },

  // Reporting options
  reporting: {
    // Generate detailed reports for debugging
    verbose: process.env.CI === 'true',

    // Include raw report data in output
    includeRawData: false,

    // Generate archives of full reports
    archiveReports: true,

    // Retention period for archives (days)
    archiveRetentionDays: 30
  },

  // Link to external tools
  externalLinks: {
    sonarcloud: 'https://sonarcloud.io/project/overview?id=nissimbuchs_BATbern2',
    github: 'https://github.com/nissimbuchs/BATbern2',
    cicd: process.env.GITHUB_SERVER_URL && process.env.GITHUB_REPOSITORY
      ? `${process.env.GITHUB_SERVER_URL}/${process.env.GITHUB_REPOSITORY}/actions`
      : null
  }
};
