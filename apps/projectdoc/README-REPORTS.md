# Test & Quality Reports Dashboard

Comprehensive test and quality metrics dashboard for the BATbern platform, integrated into the projectdoc documentation site.

## Overview

The Test & Quality Dashboard aggregates all testing and quality assurance metrics into a single, accessible location:

- **Test Results**: JUnit test execution results from all Java modules
- **Code Coverage**: JaCoCo (Java) and LCOV (Frontend) coverage reports
- **Security Findings**: Trivy SARIF security scan results
- **Code Quality**: Checkstyle violations and linting issues
- **Historical Trends**: Track metrics over last 50 builds
- **Per-Module Details**: Drill down into individual module reports

## Quick Start

### Local Development

```bash
cd apps/projectdoc

# Install dependencies
npm install

# Build reports from workspace test results
npm run build:reports

# View reports locally
npm run dev:reports
# Opens http://localhost:3001
```

### Build Full Site with Reports

```bash
# Build reports + documentation site
npm run build:all

# View everything
npm run dev
# Opens http://localhost:3000
```

## Architecture

### Directory Structure

```
apps/projectdoc/
├── src/
│   ├── aggregators/
│   │   ├── report-aggregator.js      # Main aggregation logic
│   │   └── history-manager.js        # Historical data management
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

The dashboard automatically scans for these report formats:

| Type | Pattern | Format |
|------|---------|--------|
| Java Tests | `**/build/test-results/**/*.xml` | JUnit XML |
| Java Coverage | `**/build/reports/jacoco/test/jacocoTestReport.xml` | JaCoCo XML |
| Frontend Coverage | `**/coverage/lcov.info` | LCOV |
| Security Scans | `**/*.sarif` | SARIF |
| Code Quality | `**/build/reports/checkstyle/*.xml` | Checkstyle XML |

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

### GitHub Actions Workflow

The `.github/workflows/build-reports.yml` workflow:

1. Triggers after successful build
2. Collects all test reports from workspace
3. Builds the reports dashboard
4. Uploads as artifact (30-day retention)
5. Deploys to S3 (on main/develop branches)
6. Comments on PRs with summary

### Usage in CI

The workflow automatically runs after the main build workflow completes. Reports are available:

- **As Artifacts**: Download from GitHub Actions (30 days)
- **On S3**: https://project.batbern.ch/reports/ (persistent)
- **In PR Comments**: Summary posted to pull requests

### Local Testing

To test the full pipeline locally:

```bash
# Run tests (from project root)
./gradlew test
cd web-frontend && npm test

# Build reports
cd apps/projectdoc
npm run build:reports

# View results
npm run dev:reports
```

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

### No Reports Generated

**Problem**: `npm run build:reports` completes but no reports found.

**Solution**: Ensure tests have been run first:

```bash
# From project root
./gradlew test
cd web-frontend && npm test
cd ../apps/projectdoc
npm run build:reports
```

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
