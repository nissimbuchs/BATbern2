# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**BATbern Project Documentation Portal** - A static documentation site generator that transforms markdown files and OpenAPI specifications into a comprehensive, publicly accessible website at `project.batbern.ch`. Built for the BATbern event management platform development project.

## Core Architecture

This is a Node.js-based **static site generator** with three main subsystems:

### 1. Documentation Site Builder
- **Source**: `/docs` folder from parent project
- **Output**: Static HTML site in `dist/`
- **Processors**:
  - `markdown-processor.js` - Converts markdown to HTML with syntax highlighting, TOC, and task lists
  - `html-generator.js` - Applies Handlebars templates with BATbern branding
  - `asset-processor.js` - Handles images, PDFs, and other assets
  - `openapi-processor.js` - Generates interactive API documentation with Redoc

### 2. Test & Quality Reports Dashboard
- **Source**: Test artifacts from workspace (`**/build/test-results/`, `**/coverage/`)
- **Output**: Unified dashboard at `dist/reports/`
- **Parsers**: JUnit XML, JaCoCo XML, LCOV, SARIF, Checkstyle XML
- **Features**: Historical trends (50 builds), per-module breakdowns, threshold violations

### 3. AWS Deployment System
- **Target**: S3 bucket with CloudFront CDN
- **Features**: SSL certificate management (ACM), Route 53 DNS, HTTPS redirect
- **Config**: `src/config/aws-config.js`

## Essential Commands

### Development Workflow

```bash
# Install dependencies
npm install

# Build documentation site only
npm run build:doc

# Build test reports only (requires tests run first)
npm run build:reports

# Build everything (reports + docs)
npm run build

# Local preview - documentation site
npm run dev                    # Opens http://localhost:3099

# Deploy to AWS S3 + CloudFront
npm run deploy                 # Requires AWS_PROFILE=batbern-mgmt

# Clean generated files
npm run clean
```

### Working with Reports

```bash
# First, generate test artifacts from project root
cd ../../
./gradlew test                 # Java tests + coverage
cd web-frontend && npm test    # Frontend tests + coverage
cd ../apps/projectdoc

# Then build reports dashboard
npm run build:reports

# Preview at http://localhost:3001
npm run dev:reports
```

## Key Architectural Patterns

### Document Discovery and Processing

The build system auto-discovers documents through **category-based configuration** in `src/config/site-config.js`:

```javascript
categories: {
  'architecture': {
    folder: 'architecture',      // Scan entire folder
    pattern: '*.md'
  },
  'api': {
    folder: 'api',
    pattern: '*.openapi.yml'      // OpenAPI specs
  },
  'prd-enhanced': {
    files: ['prd-enhanced.md']    // Specific files
  }
}
```

**Important**: Documents are NOT manually listed. The system discovers them at build time based on folder/pattern/files configuration.

### Template System

Uses **Handlebars** templates with layouts in `src/templates/`:
- `layout.html` - Main page wrapper with navigation
- `openapi-page.html` - Interactive API docs (Redoc)
- `api-index.html` - API landing page
- `reports-dashboard.html` - Test/quality metrics dashboard
- `module-detail.html` - Per-module report details
- `coverage-report.html`, `security-report.html`, `quality-report.html`

### Report Aggregation Pattern

Reports flow through a **parser → aggregator → template** pipeline:

```
Test Execution (CI/CD)
  ↓
Report Files (JUnit, JaCoCo, LCOV, SARIF, Checkstyle)
  ↓
Parsers (src/utils/parsers/*.js)
  ↓
ReportAggregator (src/aggregators/report-aggregator.js)
  ↓
HistoryManager (stores last 50 builds)
  ↓
Template Engine (Handlebars)
  ↓
Static HTML + JSON data files
```

**Data Persistence**: Historical data stored in `dist/reports/data/history.json` (50-build limit).

## Configuration Files

### Site Configuration (`src/config/site-config.js`)

Controls document categories, navigation, theming:

```javascript
{
  docsPath: '../../docs',           // Source documents
  outputPath: './dist',             // Build output
  markdownExploder: true,           // Split large markdown files
  categories: { /* ... */ },        // Document organization
  theme: {
    primaryColor: '#1565C0',        // BATbern blue
    accentColor: '#FF6F00'          // Orange
  }
}
```

### Reports Configuration (`src/config/reports-config.js`)

Controls report sources, modules, and quality thresholds:

```javascript
{
  sources: {
    java: {
      modules: ['shared-kernel', 'api-gateway', 'services/...'],
      testResultsPattern: '**/build/test-results/**/*.xml',
      coveragePattern: '**/build/reports/jacoco/test/jacocoTestReport.xml'
    },
    frontend: {
      coveragePattern: '**/coverage/lcov.info'
    }
  },
  thresholds: {
    coverage: { target: 85, minimum: 60 },
    tests: { failureRate: 0 },
    security: { critical: 0, high: 5 }
  },
  history: {
    enabled: true,
    historyFile: process.env.BATBERN_HISTORY_FILE || '~/.batbern/projectdoc-history.json',
    maxHistoryEntries: 50,
    trendDataPoints: 8
  }
}
```

**Shared History Across Instances**:

By default, all local BATbern project instances share a single history file at `~/.batbern/projectdoc-history.json`. This enables:
- Continuous trend analysis across different working directories
- Branch comparison across project clones
- Unified build quality history

**Override Location**:

Set `BATBERN_HISTORY_FILE` environment variable to use a custom location:

```bash
export BATBERN_HISTORY_FILE=/path/to/custom/history.json
npm run build:reports
```

**Migration**:

To merge existing histories from multiple project instances:

```bash
npm run migrate-history
```

This will:
1. Find all history.json files in your BATbern project instances
2. Merge and deduplicate entries by timestamp, branch, and commit
3. Save to `~/.batbern/projectdoc-history.json`
4. Apply 50-entry limit, keeping most recent builds

### AWS Configuration (`src/config/aws-config.js`)

Deployment settings for S3, CloudFront, Route 53:

```javascript
{
  s3: {
    bucket: process.env.S3_BUCKET_NAME || 'project-batbern-ch',
    region: 'eu-central-1'
  },
  cloudfront: { /* ... */ },
  route53: {
    hostedZoneId: process.env.ROUTE53_HOSTED_ZONE_ID
  }
}
```

## Adding New Features

### Adding a New Document Category

1. Edit `src/config/site-config.js`:
   ```javascript
   'my-category': {
     title: 'My Category',
     icon: '📚',
     order: 9,
     folder: 'my-folder',    // or files: ['specific.md']
     pattern: '*.md'
   }
   ```
2. Run `npm run build` - auto-discovered and rendered

### Adding a New Report Type

1. Create parser in `src/utils/parsers/my-parser.js`:
   ```javascript
   export class MyParser {
     static async findAndParseReports(baseDir, pattern) {
       // Implement parsing logic
       return reports;
     }
   }
   ```

2. Integrate in `src/aggregators/report-aggregator.js`:
   ```javascript
   import MyParser from '../utils/parsers/my-parser.js';

   async collectMyReports() {
     return await MyParser.findAndParseReports(
       this.baseDir,
       this.config.sources.myReports.pattern
     );
   }
   ```

3. Add config in `src/config/reports-config.js`:
   ```javascript
   sources: {
     myReports: {
       pattern: '**/my-reports/*.xml'
     }
   }
   ```

### Adding a New API Specification

1. Create OpenAPI spec: `../../docs/api/my-api.openapi.yml`
2. Run `npm run build` - automatically discovered and rendered
3. View at `http://localhost:3099/api/my-api.html`

**No code changes required** - the OpenAPI processor auto-discovers `*.openapi.yml` files.

## Build System Internals

### Build Script (`scripts/build.js`)

Main orchestrator - executes these phases:

1. **Initialize** - Load processors and config
2. **Discover** - Scan for documents based on category config
3. **Process** - Parse markdown/OpenAPI, extract metadata
4. **Generate** - Apply templates, create HTML pages
5. **Assets** - Copy images, PDFs, CSS, JS
6. **Manifest** - Generate `manifest.json` with build info

### Reports Build Script (`scripts/build-reports.js`)

Separate orchestrator for test/quality reports:

1. **Collect** - Find and parse all test artifacts
2. **Aggregate** - Consolidate into single dataset
3. **History** - Load previous builds, append current
4. **Generate** - Render dashboard templates
5. **Export** - Write JSON data files for programmatic access

### Deployment Script (`scripts/deploy.js`)

AWS deployment pipeline:

1. **Build Validation** - Ensure `dist/` exists
2. **S3 Upload** - Sync files with content-type headers
3. **ACM Certificate** - Request/validate SSL cert (if needed)
4. **CloudFront** - Create/update distribution
5. **Route 53** - Configure DNS A/AAAA records
6. **Invalidation** - Clear CloudFront cache

## Important Context

### Parent Project Integration

This app lives in the **BATbern monorepo** at `apps/projectdoc/`:
- **Source docs**: `../../docs/` (shared with main project)
- **Test artifacts**: `../../**/build/`, `../../web-frontend/coverage/`
- **Deployment**: Separate AWS infrastructure (management account)

### CI/CD Integration

GitHub Actions workflow (`.github/workflows/build-reports.yml`):
1. Triggers after main build completes
2. Collects test artifacts from workspace
3. Builds reports dashboard
4. Uploads as artifact (30-day retention)
5. Deploys to S3 (main/develop branches only)
6. Comments on PRs with summary

### URL Structure After Deployment

```
https://project.batbern.ch/
├── index.html                    # Homepage
├── architecture/                 # Architecture docs
├── prd/                         # Epic documents
├── wireframes/                  # UI wireframes
├── stories/                     # User stories
├── api/                         # API documentation
│   ├── index.html              # API landing page
│   ├── companies-api.html      # Companies API (Redoc)
│   ├── events-api.html         # Events API (Redoc)
│   └── *.openapi.yml           # Downloadable specs
└── reports/                     # Test/quality dashboard
    ├── index.html              # Dashboard home
    ├── modules/                # Per-module details
    ├── coverage.html           # Coverage report
    ├── security.html           # Security findings
    └── data/                   # JSON data files
        ├── latest.json         # Full report data
        ├── summary.json        # Summary only
        ├── trends.json         # Trend data
        └── badges.json         # Badge data (shields.io)
```

## Critical Development Notes

### Markdown Enhancement Features

The markdown processor applies these transformations:
- **Syntax highlighting** (highlight.js, GitHub theme)
- **Auto-generated TOC** (markdown-it-table-of-contents)
- **Task lists** (markdown-it-task-lists, GitHub-style)
- **Footnotes** (markdown-it-footnote)
- **Custom containers** (markdown-it-container)
- **Mermaid diagrams** (markdown-it-mermaid)
- **Anchor links** (markdown-it-anchor)

### Report Data Access

All report data available as JSON for programmatic access:

```bash
# Full report data
curl https://project.batbern.ch/reports/data/latest.json

# Summary only (smaller payload)
curl https://project.batbern.ch/reports/data/summary.json

# Badge data (shields.io format)
curl https://project.batbern.ch/reports/data/badges.json
```

### Environment Variables for Deployment

Required:
- `AWS_PROFILE=batbern-mgmt` (or AWS credentials configured)

Optional (with defaults):
- `S3_BUCKET_NAME=project-batbern-ch`
- `AWS_REGION=eu-central-1`
- `ROUTE53_HOSTED_ZONE_ID=Z04921951F6B818JF0POD`
- `CLOUDFRONT_ENABLED=true`

### BATbern Branding

Color scheme (in `src/templates/styles.css`):
- Primary Blue: `#1565C0`
- Dark Blue: `#0D47A1`
- Orange Accent: `#FF6F00`
- Light Gray BG: `#FAFAFA`
- Text: `#212121`

Fonts:
- Body: Inter (Google Fonts)
- Code: JetBrains Mono (Google Fonts)

## Troubleshooting

### Build Issues

**Problem**: `npm run build:reports` completes but no reports found.

**Solution**: Run tests first to generate artifacts:
```bash
cd ../../
./gradlew test
cd web-frontend && npm test
cd ../apps/projectdoc
npm run build:reports
```

**Problem**: OpenAPI specs not appearing.

**Solution**: Ensure files:
- End with `.openapi.yml` or `.openapi.yaml`
- Located in `../../docs/api/`
- Valid YAML syntax (check with `npx js-yaml ../../docs/api/my-api.openapi.yml`)

### Deployment Issues

**Problem**: SSL certificate validation stuck.

**Solution**: Check Route 53 hosted zone and ensure DNS propagation:
```bash
dig project.batbern.ch
# Should point to CloudFront distribution
```

**Problem**: S3 upload permissions denied.

**Solution**: Verify AWS profile has S3, CloudFront, ACM, Route 53 permissions:
```bash
aws sts get-caller-identity --profile batbern-mgmt
```

## Performance Considerations

- **Build Time**: ~10-15 seconds for full site (38 docs)
- **Reports Build**: ~5-10 seconds (parsing XML/LCOV)
- **Deploy Time**: ~2-3 minutes (includes CloudFront invalidation)
- **History Limit**: 50 builds (keeps `history.json` under 1MB)
- **Asset Optimization**: Images copied as-is (no compression)

## Dependencies

Core:
- `handlebars` - Template engine
- `markdown-it` + plugins - Markdown processing
- `highlight.js` - Code syntax highlighting
- `redoc` - OpenAPI documentation renderer
- `fs-extra` - File system operations
- `glob` - File pattern matching

AWS SDK:
- `@aws-sdk/client-s3` - S3 operations
- `@aws-sdk/client-cloudfront` - CDN management
- `@aws-sdk/client-route-53` - DNS configuration
- `@aws-sdk/client-acm` - SSL certificates

Parsers:
- `xml2js` - XML parsing (JUnit, JaCoCo, Checkstyle)
- `js-yaml` - YAML parsing (OpenAPI specs)
- `cheerio` - HTML parsing

Utilities:
- `mermaid` - Diagram rendering
- `mime-types` - Content-type detection
- `marked` - Markdown parsing (legacy)
