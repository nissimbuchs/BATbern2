/**
 * Configuration for test and quality reports aggregation
 */
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

      // Modules to scan (empty means scan all)
      modules: [
        'shared-kernel',
        'api-gateway',
        'event-management-service',
        'speaker-coordination-service',
        'partner-coordination-service',
        'attendee-experience-service',
        'company-user-management-service'
      ]
    },

    frontend: {
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
      // SARIF security scan results
      sarifPattern: '**/*.sarif',

      // Trivy results
      trivyPattern: 'trivy-results.sarif',

      // Snyk results (if available)
      snykPattern: '**/snyk-*.json'
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
      target: 85,    // Target coverage percentage
      minimum: 60,   // Minimum acceptable coverage
      warning: 70    // Coverage below this shows warning
    },

    tests: {
      failureRate: 0,           // Maximum allowed test failure rate (0 = no failures)
      minSuccessRate: 100       // Minimum test success rate percentage
    },

    security: {
      critical: 0,   // Maximum critical vulnerabilities
      high: 5,       // Maximum high vulnerabilities
      medium: 20     // Maximum medium vulnerabilities
    },

    quality: {
      errors: 0,     // Maximum quality errors (Checkstyle, ESLint errors)
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
      'event-management-service',
      'attendee-experience-service',
      'company-user-management-service',
      'partner-coordination-service',
      'speaker-coordination-service',
      'web-frontend'
    ]
  },

  // Historical data configuration
  history: {
    enabled: true,
    historyFile: 'dist/reports/data/history.json',
    maxHistoryEntries: 50,      // Keep last 50 builds
    trendDataPoints: 20         // Number of data points for trend charts
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
