# Test & Quality Reports Dashboard

Comprehensive test and quality metrics dashboard for the BATbern platform, integrated into the projectdoc documentation site.

## Overview

The Test & Quality Dashboard aggregates all testing and quality assurance metrics into a single, accessible location:

- **Test Results**: JUnit results from all Java modules and the Vitest frontend suite
- **Code Coverage**: JaCoCo (Java), LCOV (frontend) and LCOV (infrastructure / CDK, shown separately)
- **SAST**: Trivy dependency scan (lockfiles + built service jars), run on every reports build
- **DAST**: OWASP ZAP results of the latest successful weekly `security-scan.yml` run
- **Code Quality**: Checkstyle violations
- **Data Sources**: status and age of every source above, so missing data reads "no data", never 0
- **Historical Trends**: last 50 builds, one entry per commit and branch
- **Per-Module Details**: drill down into individual module reports

## Quick Start

### One command (recommended)

```bash
make reports          # clean, install, build, all tests, CDK coverage, SAST, DAST, then the site
make reports-deploy   # the same, then deploy to project.batbern.ch
```

`make reports` runs, in this order and stopping at the first failure:

1. `make clean install build test-java`
2. `./gradlew --stop` and `rm -rf /tmp/cdk.out*` (memory guard: on rack `/tmp` is RAM)
3. `cd infrastructure && npm test -- --coverage --maxWorkers=2` (CDK coverage)
4. `rm -rf /tmp/cdk.out*`
5. `cd web-frontend && npm run test:coverage` (Vitest JUnit + LCOV)
6. `cd apps/projectdoc && npm ci && npm run build` (Trivy, ZAP download, aggregation, site)

Requirements: Docker (Testcontainers and Trivy) and `gh` logged in to the BATbern2 repository
(DAST download). Without them the report is still produced, with SAST/DAST marked as missing or stale.

### Only the dashboard

```bash
cd apps/projectdoc
npm run build:reports    # uses whatever test/coverage files are on disk; their age is shown
npm test                 # unit tests of the aggregator (node:test, offline, fast)
```

`npm run build` (reports + documentation site) fails when the reports aggregation throws. Missing
or stale sources and a failing health status are **not** build failures: they are shown on the
dashboard and listed at the end of the console output.

## Architecture

### Directory Structure

```
apps/projectdoc/
├── src/
│   ├── aggregators/
│   │   ├── report-aggregator.js      # Main aggregation logic
│   │   └── history-manager.js        # Historical data management
│   ├── sources/
│   │   ├── source-status.js          # fresh/stale/missing classification per source
│   │   ├── zap-fetcher.js            # DAST: download latest security-scan.yml artifacts
│   │   ├── trivy-runner.js           # SAST: Trivy fs + rootfs scan in Docker
│   │   └── command-runner.js         # injectable process runner (tests run offline)
│   ├── utils/parsers/
│   │   ├── jacoco-parser.js          # Java coverage (JaCoCo XML)
│   │   ├── junit-parser.js           # Java test results (JUnit XML)
│   │   ├── lcov-parser.js            # Frontend coverage (LCOV)
│   │   ├── sarif-parser.js           # Security findings (SARIF)
│   │   └── checkstyle-parser.js      # Code quality (Checkstyle XML)
│   └── templates/
│       ├── reports-dashboard.html    # Main dashboard
│       ├── module-detail.html        # Per-module page
│       ├── coverage-report.html      # Coverage details
│       ├── security-report.html      # Security findings
│       ├── quality-report.html       # Quality violations
│       └── styles/
│           └── reports.css           # Dashboard styling
├── config/
│   └── reports-config.js             # Report sources and thresholds
├── scripts/
│   └── build-reports.js              # Build orchestrator
└── dist/
    └── reports/                      # Generated dashboard
        ├── index.html                # Main dashboard
        ├── modules/                  # Per-module pages
        ├── coverage.html             # Coverage report
        ├── security.html             # Security report
        ├── quality.html              # Quality report
        ├── styles/                   # CSS files
        └── data/                     # JSON data files
            ├── latest.json           # Full report data
            ├── summary.json          # Summary only
            ├── trends.json           # Trend data
            ├── history.json          # Last 50 builds
            └── badges.json           # Badge data
```

### Report Sources

| Source | Where it comes from | Fresh if younger than |
|--------|---------------------|-----------------------|
| Java tests | `**/build/test-results/**/*.xml` (JUnit, `make test-java`) | 1 day (file time) |
| Frontend tests | `web-frontend/test-results/junit.xml` (Vitest) | 1 day (file time) |
| Java coverage | `**/build/reports/jacoco/test/jacocoTestReport.xml` | 1 day (file time) |
| Frontend coverage | `web-frontend/coverage/lcov.info` | 1 day (file time) |
| Infrastructure coverage | `infrastructure/coverage/lcov.info` (Jest, `--coverage`) | 1 day (file time) |
| SAST | Trivy in Docker, written to `security-reports/trivy-*.sarif` on every build | 1 day (scan time) |
| DAST | latest successful `security-scan.yml` run, downloaded to `security-reports/zap-*.json` | 8 days (run time) |
| Code quality | `**/build/reports/checkstyle/*.xml` | 1 day (file time) |
| Lines of code | `cloc`, run during the build | measured live |
| SonarCloud | API, only with `SONAR_TOKEN` set | measured live |

Thresholds live in `src/config/reports-config.js` → `freshness.maxAgeHours`.

### Data sources and freshness (#1226)

Every source gets an entry in `metadata.sources` of `data/latest.json`:

```json
"sast": { "status": "fresh", "measuredAt": "2026-11-04T22:04:57.759Z", "origin": "aquasec/trivy:0.58.1 (...)", "note": "..." }
```

| Status | Meaning | Dashboard shows |
|--------|---------|-----------------|
| `fresh` | measured within the threshold | the number |
| `stale` | older than the threshold, of unknown age, or taken from a fallback | the number + "stale since <date>" |
| `partial` | only part of the source ran (Trivy without built jars) | the number + "partial" |
| `missing` | no data | "no data" / "not measured"; the summary value is `null`, never 0 |
| `not-configured` | deliberately not set up (SonarCloud without `SONAR_TOKEN`, LOC disabled) | "not configured" |

`summary.dataComplete` is `false` when any configured source is not fresh; `summary.incompleteSources`
lists them and the dashboard shows a warning banner. Incomplete data caps the health status at
**warning**: missing data is never reported as passing. A "Data Sources" table on the dashboard
shows status, measurement time and origin of every source.

**DAST.** `src/sources/zap-fetcher.js` asks `gh` for the latest *successful* `security-scan.yml`
run on `develop`, downloads its combined `zap-reports` artifact (falls back to the per-scan
`zap-json-*` artifacts) and **replaces** `security-reports/zap-*.json` and `security-reports/zap-logs/`
every time, so a removed scan target disappears. The run's `createdAt` is the measurement date
(`security-reports/dast-source.json`). If `gh` is unavailable or not logged in, it falls back to S3
(`s3://batbern-content-staging/ci-reports/zap/`, last written 2026-03-05), then to the local files;
both fallbacks are marked **stale** with their own date. Artifacts are kept 30 days.

**SAST.** `src/sources/trivy-runner.js` runs `aquasec/trivy:0.58.1` in Docker as the calling user
(`--user uid:gid`, repo mounted read-only, cache in `~/.cache/trivy`, or `~/.cache/trivy-<uid>`
when an earlier root run left that unwritable). Two scans:

- `trivy fs` on the repo root → `trivy-results.sarif`: lockfiles, skipping `**/node_modules`,
  `.snapshots`, `**/build` and `**/cdk.out`.
- `trivy rootfs` on every `*/build/libs` and `services/*/build/libs` with jars → `trivy-jars.sarif`:
  the Spring Boot fat jars. Trivy's `fs` mode does not open jars, so without this scan every Java
  runtime dependency would be missed (checked 2026-11-04: 51 findings in the event-management jar
  alone, none in the fs scan).

Old SARIF files are deleted before the scan, so a failed scan cannot leave stale numbers behind.
Without Docker, SAST is `missing`; without built jars it is `partial`. `PROJECTDOC_SKIP_SAST=1`
skips the scan (reported as missing). Severity comes from Trivy's own rating (rule tags), so
CRITICAL is counted as critical, not folded into high.

**Vitest unhandled errors.** Errors Vitest catches outside any test (the
`vitest unhandled errors` pseudo-suite in the JUnit file, see #1225) are counted as
`tests.unhandledErrors` and shown as "errors (unhandled)", not as failed tests. They make the
health status at least **warning**.

**History.** Rebuilding for the same commit on the same branch replaces that history entry
instead of adding a second one.

### Data Flow

```
Test Execution (CI/CD)
  ↓
Report Files (JUnit, JaCoCo, LCOV, SARIF, Checkstyle)
  ↓
Report Parsers (Parse XML/LCOV/SARIF)
  ↓
Report Aggregator (Consolidate all data)
  ↓
History Manager (Store & retrieve trends)
  ↓
Template Engine (Generate HTML pages)
  ↓
Static Dashboard (dist/reports/)
  ↓
Deployment (S3 + CloudFront)
```

## Configuration

### Report Sources

Edit `config/reports-config.js` to customize report locations:

```javascript
export default {
  sources: {
    java: {
      testResultsPattern: '**/build/test-results/**/*.xml',
      coveragePattern: '**/build/reports/jacoco/test/jacocoTestReport.xml',
      modules: ['shared-kernel', 'api-gateway', ...]
    },
    frontend: {
      coveragePattern: '**/coverage/lcov.info',
      module: 'web-frontend'
    },
    security: {
      sarifPattern: '**/*.sarif'
    },
    quality: {
      checkstylePattern: '**/build/reports/checkstyle/*.xml'
    }
  }
}
```

### Quality Thresholds

Configure pass/fail thresholds:

```javascript
thresholds: {
  coverage: {
    target: 85,      // Target coverage percentage
    minimum: 60,     // Minimum acceptable coverage
    warning: 70      // Show warning below this
  },
  tests: {
    failureRate: 0,  // Maximum allowed failure rate
    minSuccessRate: 100
  },
  security: {
    critical: 0,     // Maximum critical vulnerabilities
    high: 5,         // Maximum high vulnerabilities
    medium: 20
  },
  quality: {
    errors: 0,       // Maximum quality errors
    warnings: 50     // Maximum warnings
  }
}
```

### Historical Data

Control historical tracking:

```javascript
history: {
  enabled: true,
  historyFile: 'dist/reports/data/history.json',
  maxHistoryEntries: 50,      // Keep last 50 builds
  trendDataPoints: 20         // Show 20 points in charts
}
```

## Features

### Main Dashboard

- **Overall Health Status**: At-a-glance passing/warning/failing indicator
- **Key Metrics Cards**: Tests, Coverage, Security, Quality
- **Trend Charts**: Visual trends over last 20 builds
- **Module Status Table**: Status for all modules
- **Build Comparison**: Compare with previous build

### Module Details

Each module has a detailed page showing:
- Test execution results with failures
- Package-level coverage breakdown
- Top quality violations
- Historical trend chart

### Coverage Report

- Overall coverage metrics (Java + Frontend)
- Per-module breakdown
- Files needing attention (low coverage)
- Coverage by directory (Frontend)
- Threshold compliance

### Security Dashboard

- Findings by severity (Critical, High, Medium, Low, Info)
- Top vulnerabilities with details
- Affected files
- Links to CVE details and fixes

### Quality Dashboard

- Violations by severity (Errors, Warnings, Info)
- Per-module quality breakdown
- Most common violations
- Violations by category

## CI/CD Integration

There is none at the moment: `build-reports.yml` was disabled on 2026-05-25 (`9f21453f`,
now `.github/workflows/build-reports.yml.disabled`). The reports are built locally with
`make reports` and deployed with `make reports-deploy`. The only CI input is the weekly
`security-scan.yml` (OWASP ZAP), whose artifacts the build downloads.

## API

### Programmatic Access

All report data is available as JSON:

```bash
# Full report data
curl https://project.batbern.ch/reports/data/latest.json

# Summary only
curl https://project.batbern.ch/reports/data/summary.json

# Trend data for charts
curl https://project.batbern.ch/reports/data/trends.json

# Historical data
curl https://project.batbern.ch/reports/data/history.json

# Badge data (shields.io format)
curl https://project.batbern.ch/reports/data/badges.json
```

### Badge Integration

Use badge data for README badges:

```json
{
  "tests": {
    "label": "tests",
    "message": "245/245",
    "color": "brightgreen"
  },
  "coverage": {
    "label": "coverage",
    "message": "82.5%",
    "color": "yellow"
  }
}
```

Create shields.io badges:

```markdown
![Tests](https://img.shields.io/endpoint?url=https://project.batbern.ch/reports/data/badges.json&query=$.tests)
![Coverage](https://img.shields.io/endpoint?url=https://project.batbern.ch/reports/data/badges.json&query=$.coverage)
```

## Troubleshooting

### "no data" or "stale since" on the dashboard

**Problem**: A tile shows "no data", "not measured" or "stale since <date>".

**Solution**: The Data Sources table names the source and why (the note). Usually `make reports`
fixes it. For DAST, check that `gh auth status` succeeds and that the last `security-scan.yml` run
succeeded; for SAST, that Docker is running and `make build` produced the service jars.

### Missing Module Data

**Problem**: Some modules don't appear in dashboard.

**Solution**: Check module is listed in `config/reports-config.js`:

```javascript
modules: [
  'shared-kernel',
  'api-gateway',
  'your-module-here'  // Add missing module
]
```

### Historical Trends Not Showing

**Problem**: Trend charts are empty.

**Solution**: Run the build multiple times to accumulate history:

```bash
# History builds up over multiple runs
npm run build:reports  # Run 1
npm run build:reports  # Run 2
npm run build:reports  # Run 3 (charts start appearing)
```

### Coverage Calculation Errors

**Problem**: `Cannot parse JaCoCo XML` errors.

**Solution**: Ensure JaCoCo reports are generated:

```bash
# From module directory
./gradlew test jacocoTestReport
```

## Customization

### Adding New Report Types

1. Create parser in `src/utils/parsers/`:

```javascript
export class MyReportParser {
  static async parseFile(filePath) {
    // Parse report file
    return parsedData;
  }

  static async findAndParseReports(baseDir, pattern) {
    // Find and parse all reports
    return reports;
  }
}
```

2. Integrate in `src/aggregators/report-aggregator.js`:

```javascript
import MyReportParser from '../utils/parsers/my-report-parser.js';

async collectMyReports() {
  const pattern = this.config.sources.myReports.pattern;
  return await MyReportParser.findAndParseReports(this.baseDir, pattern);
}
```

3. Add to config `config/reports-config.js`:

```javascript
sources: {
  myReports: {
    pattern: '**/my-reports/*.xml'
  }
}
```

### Custom Styling

Edit `src/templates/styles/reports.css` for custom branding:

```css
:root {
  --primary-blue: #1565C0;     /* Your primary color */
  --accent-orange: #FF6F00;    /* Your accent color */
  --success-green: #4CAF50;    /* Success indicators */
  /* ... customize other colors */
}
```

### Custom Templates

Modify HTML templates in `src/templates/`:

- `reports-dashboard.html` - Main dashboard layout
- `module-detail.html` - Per-module page
- `coverage-report.html` - Coverage visualization
- `security-report.html` - Security findings
- `quality-report.html` - Quality violations

Templates use Handlebars syntax with custom helpers defined in `scripts/build-reports.js`.

## Performance

- **Build Time**: ~5-10 seconds for typical project
- **File Size**: ~500KB for full report data
- **History Limit**: 50 builds (configurable)
- **Chart Rendering**: Client-side with Chart.js

## Browser Support

- Chrome/Edge: ✅ Full support
- Firefox: ✅ Full support
- Safari: ✅ Full support
- Mobile: ✅ Responsive design

## Dependencies

- **xml2js**: XML parsing (JUnit, JaCoCo, Checkstyle)
- **handlebars**: Template rendering
- **fs-extra**: File system operations
- **glob**: File pattern matching
- **Chart.js**: Trend charts (CDN)

## Future Enhancements

Potential additions:

- [ ] SonarCloud API integration
- [ ] Playwright test result parsing
- [ ] ESLint JSON report support
- [ ] Performance metrics (Lighthouse)
- [ ] Accessibility test results (axe-core)
- [ ] Dependency vulnerability tracking
- [ ] Email notifications on failures
- [ ] Slack/Discord webhooks
- [ ] Custom report plugins

## Support

For issues or questions:

- **GitHub Issues**: https://github.com/nissimbuchs/BATbern2/issues
- **Documentation**: https://project.batbern.ch/
- **Team**: BATbern Platform Team

---

**Generated by**: BATbern Test & Quality Dashboard v1.0.0
**License**: ISC
**Platform**: BATbern © 2025
