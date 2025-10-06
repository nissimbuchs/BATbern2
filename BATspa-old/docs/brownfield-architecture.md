# BATbern Brownfield Architecture Document

## Introduction

This document captures the CURRENT STATE of the BATbern Angular SPA codebase, including its real-world patterns, constraints, and implementation details. It serves as a reference for AI agents working on enhancements to this Swiss IT architecture conference website.

### Document Scope

Comprehensive documentation of the entire system with focus on areas relevant to PRD-identified enhancements:
- Registration system implementation
- Enhanced mobile experience
- Content management workflow improvements
- Backend content management system potential
- User account and community features

### Change Log

| Date       | Version | Description                 | Author  |
| ---------- | ------- | --------------------------- | ------- |
| 2024-12-19 | 1.0     | Initial brownfield analysis | Winston |

## Quick Reference - Key Files and Entry Points

### Critical Files for Understanding the System

- **Main Entry**: `src/main.ts` (Angular bootstrap)
- **App Module**: `src/app/app.module.ts` (Root module configuration)
- **Routing**: `src/app/router.ts` (Application routes - NOTE: Most routes commented out)
- **Layout**: `src/app/ui/layout/layout.component.ts` (Clarity-based main layout)
- **Data Services**:
  - `src/app/events/topic.service.ts` (Topic/event data)
  - `src/app/events/session.service.ts` (Session/presentation data)
  - `src/app/events/picture.service.ts` (Event photography)
- **Data Sources**:
  - `src/api/topics.json` (54 historical events + future planned)
  - `src/api/sessions.json` (Presentation details with speaker info)
  - `src/api/pictures.json` (Event photos by BAT number)
- **Styling**: `src/styles.css` (Global beige theme), individual component CSS files

### Enhancement Impact Areas

Based on the PRD, these files will be affected by planned enhancements:

**Registration System**:
- `src/app/events/anmeldung/anmeldung.component.ts` (Currently basic component)
- New service layer will be needed for backend integration

**Content Management**:
- All JSON files in `src/api/` (Currently static, needs dynamic backend)
- Service layer (`*.service.ts` files) for API integration

**Mobile Enhancement**:
- `src/styles.css` and component-specific CSS files
- Layout components for responsive improvements

## High Level Architecture

### Technical Summary

BATbern is a **client-only Angular SPA** with no backend services. All data is served as static JSON files, making it extremely reliable but limiting dynamic functionality.

### Actual Tech Stack (from package.json)

| Category       | Technology           | Version | Notes                                    |
| -------------- | -------------------- | ------- | ---------------------------------------- |
| Framework      | Angular              | 14.2.7  | Recent stable version                    |
| UI Library     | Clarity Design       | 13.9.0  | VMware's enterprise UI components        |
| UI Enhancement | Bootstrap            | 5.2.2   | Additional styling and responsive grid   |
| UI Components  | ng-bootstrap         | 12.1.2  | Bootstrap Angular components             |
| HTTP Client    | Angular HttpClient   | 14.2.7  | Built-in, for JSON file loading          |
| Build Tool     | Angular CLI          | 14.2.6  | Standard Angular build pipeline          |
| Testing        | Jasmine + Karma      | 3.99.1  | Standard Angular testing setup           |
| Icons          | Clarity Icons        | 5.8.4   | Icon system                              |
| Additional     | jQuery               | 3.6.1   | Legacy dependency for Bootstrap          |

### Repository Structure Reality Check

- **Type**: Single Angular application (not monorepo)
- **Package Manager**: npm (standard package-lock.json)
- **Build Output**: `dist/BATbern2/` (note the "2" suffix - historical naming)
- **Deployment**: Static file hosting with Apache .htaccess for SPA routing

## Source Tree and Module Organization

### Project Structure (Actual)

```text
BATbern/
├── src/
│   ├── app/
│   │   ├── app.component.ts          # Root component (minimal - just layout)
│   │   ├── app.module.ts             # Root module with all imports
│   │   ├── router.ts                 # Routes (NOTE: most routes commented out!)
│   │   ├── events/                   # Main feature module
│   │   │   ├── events.module.ts      # Event-related components
│   │   │   ├── aktuell/              # Current event display (main landing page)
│   │   │   ├── archiv/               # Event listing page
│   │   │   ├── archiv-detail/        # Individual event details
│   │   │   ├── anmeldung/            # Registration component (basic)
│   │   │   ├── kontakt/              # Contact information
│   │   │   ├── themen/               # Topics component (disabled)
│   │   │   ├── verein/               # Organization info component
│   │   │   ├── *.service.ts          # Data services for JSON loading
│   │   │   └── *.ts                  # Type definitions
│   │   └── ui/                       # Shared UI components
│   │       └── layout/               # Header and layout components
│   ├── api/                          # Static JSON data files (served as assets)
│   │   ├── topics.json               # 54+ BAT events (2005-2024+)
│   │   ├── sessions.json             # Presentation details (~37K tokens!)
│   │   └── pictures.json             # Event photos metadata
│   ├── assets/                       # Images, logos, backgrounds
│   ├── environments/                 # Environment configs + .htaccess
│   └── styles.css                    # Global styling (beige theme)
├── dist/BATbern2/                    # Build output directory
├── angular.json                      # Angular CLI configuration
├── package.json                      # Dependencies and scripts
└── README.md                         # Basic project info
```

### Key Modules and Their Purpose

- **Events Module** (`src/app/events/`): Core business functionality for conference data
- **UI Module** (`src/app/ui/`): Shared layout and navigation components
- **Static Data** (`src/api/`): JSON files treated as API endpoints
- **Assets** (`src/assets/`): Conference branding, venue photos, backgrounds

### Routing Reality Check

**CRITICAL CONSTRAINT**: Most routes are commented out in `router.ts`! Current active routes:
- `/` (root) → `AktuellComponent` (current event landing page)
- `/event` → `ArchivComponent` (event listing)
- `/event/:bat` → `ArchivDetailComponent` (specific event details)

Commented out routes suggest intended navigation that isn't implemented:
- Registration, Topics, Organization, Contact (all components exist but not routed)

## Data Models and APIs

### Data Models (TypeScript Interfaces)

Located in `src/app/events/`:

**Topic/Event Model** (`topic.ts`):
```typescript
interface ITopic {
  bat: number;        // Event sequence number (1-54+)
  topic: string;      // Event theme/title
  datum: string;      // Date as string (inconsistent formats!)
  next?: boolean;     // Flag for current event
  planned?: boolean;  // Flag for future events
  eventType?: string; // Format: "Abend-BAT", "Ganztages-BAT", etc.
}
```

**Session/Presentation Model** (`session.ts`):
```typescript
interface ISession {
  bat: number;           // Associated event number
  pdf: string;           // Presentation link/path (mixed internal/external)
  author: string;        // Presenter name
  title: string;         // Presentation title
  abstract: string;      // Content description
  referenten: IReferent[]; // Speaker details array
}

interface IReferent {
  name: string;     // Speaker name
  bio: string;      // Professional background
  portrait: string; // Photo filename
  company: string;  // Organization affiliation
}
```

**Picture Model** (`picture.ts`):
```typescript
interface IPicture {
  bat: number;   // Associated event number
  image: string; // Image filename
}
```

### API Specifications (Static JSON)

**Data Sources**:
- `./api/topics.json` - 290 lines, complete event history
- `./api/sessions.json` - **LARGE FILE** (37K+ tokens), detailed presentation data
- `./api/pictures.json` - Event photography metadata

**Service Layer** (`*.service.ts`):
- Uses Angular HttpClient to load JSON files as if they were API endpoints
- Client-side filtering, sorting, and mapping operations
- No authentication or state management needed (read-only data)

## Technical Debt and Known Issues

### Critical Technical Debt

1. **Static Data Architecture**: All data in JSON files limits dynamic content management
   - No ability to update events without code deployment
   - Large `sessions.json` file (37K+ tokens) impacts initial load
   - Inconsistent date formats across historical data

2. **Routing Inconsistency**: Most navigation commented out
   - Components exist but aren't accessible via direct URLs
   - Navigation relies on anchor links and single-page scrolling
   - Back/forward browser functionality limited

3. **Limited Mobile Optimization**:
   - Basic responsive design with some mobile-specific hiding (e.g., slideshareframe)
   - Hardcoded viewport calculations in components
   - Fixed-size images and layouts

4. **No Registration Backend**: Registration component exists but non-functional
   - Form may exist but no processing capability
   - No integration with payment or confirmation systems

5. **Testing Coverage**: Minimal test implementation
   - Only basic component creation tests
   - No business logic or integration testing
   - Tests may be outdated (reference to 'BATbern' title not matching implementation)

### Workarounds and Gotchas

- **Build Output Directory**: Uses `dist/BATbern2` (note the "2") - historical naming
- **Asset Serving**: JSON files in `src/api/` are served as static assets via Angular CLI configuration
- **External Integration**: Hardcoded Slideshare URLs for presentation hosting
- **PDF Links**: Mixed internal/external URL handling in `getUrl()` method requires base URL construction
- **Image Paths**: Event photos use inconsistent naming conventions across historical data

### Performance Considerations

- **Large JSON Files**: `sessions.json` is extremely large, should consider pagination or lazy loading
- **Image Loading**: No lazy loading implemented for event galleries
- **Bundle Size**: Multiple UI libraries (Clarity + Bootstrap) may cause bloat

## Integration Points and External Dependencies

### External Services

| Service   | Purpose            | Integration Type | Key Files/Components                |
| --------- | ------------------ | ---------------- | ----------------------------------- |
| Slideshare| Presentation hosting| iframe embed    | `archiv.component.html` (hardcoded)|
| Twitter   | Social media       | External link   | Header navigation                   |
| Paul Klee | Venue information  | External link   | Hardcoded venue details             |

### Internal Integration Points

- **Component Communication**: Parent-child component data passing via `@Input()` decorators
- **Service Layer**: Singleton services injected throughout component tree
- **Asset Management**: Static images served from `assets/` directory
- **Navigation**: Mix of Angular routing and anchor-based scrolling

### Content Dependencies

- **Presentation Storage**: External Slideshare account for BAT40+ presentations
- **Historical Archives**: Legacy presentation files may be hosted on external domain
- **Image Assets**: Event photos stored locally in organized directory structure

## Development and Deployment

### Local Development Setup

**Prerequisites**:
- Node.js (version not specified in package.json - assumes compatible with Angular 14)
- npm package manager

**Setup Commands**:
```bash
npm install          # Install dependencies
npm run start        # Start development server (ng serve -o)
npm run build        # Production build
npm test            # Run unit tests
npm run lint        # Run TSLint checks
npm run e2e         # End-to-end tests (Protractor)
```

**Development Server**: Runs on default Angular CLI port with auto-browser opening

### Build and Deployment Process

**Build Configuration** (from `angular.json`):
- **Output**: `dist/BATbern2/` directory
- **Assets**: Includes `src/api/` JSON files and `src/environments/.htaccess`
- **Styles**: Bootstrap + Clarity CSS bundled
- **Scripts**: jQuery + Bootstrap JS included
- **Production Optimizations**: Bundling, minification, source map removal

**Deployment Requirements**:
- **Static Hosting**: Apache server with mod_rewrite support
- **SPA Routing**: `.htaccess` file handles client-side routing fallback
- **No Backend**: Pure static file hosting sufficient

## Testing Reality

### Current Test Coverage

**Unit Tests**:
- **Framework**: Jasmine + Karma (standard Angular setup)
- **Coverage**: Minimal - only basic component creation tests
- **Files**: 3 spec files found (app.component, ui.module, events.module)
- **Test Quality**: Basic scaffolded tests, may not match current implementation

**Integration Tests**: None identified

**E2E Tests**: Protractor configured but likely minimal implementation

**Manual Testing**: Appears to be primary QA method

### Running Tests

```bash
npm test           # Unit tests with Karma
npm run e2e       # E2E tests with Protractor (if implemented)
```

**Test Configuration**:
- **Karma**: Chrome browser, HTML reporter, Istanbul coverage
- **Coverage Reports**: Generated to `../coverage` directory

## Enhancement PRD Impact Analysis

### Registration System Implementation

**Files That Will Need Modification**:
- `src/app/events/anmeldung/anmeldung.component.ts` - Add form handling and validation
- `src/app/events/anmeldung/anmeldung.component.html` - Create registration form UI
- `src/app/router.ts` - Uncomment and activate registration route
- New service: `src/app/events/registration.service.ts` - Handle registration logic

**New Backend Requirements**:
- Registration API endpoints
- User data storage
- Email confirmation system
- Payment integration (if required)

### Content Management System

**Current Constraint**: All content in static JSON files

**Files Requiring Backend Integration**:
- `src/app/events/topic.service.ts` - Convert to REST API calls
- `src/app/events/session.service.ts` - Convert to REST API calls
- `src/app/events/picture.service.ts` - Convert to REST API calls
- `src/api/*.json` files - Would be replaced by database-backed APIs

**New Architecture Requirements**:
- RESTful API backend
- Database for event/session/speaker data
- File upload system for presentations and photos
- Admin interface for content management

### Enhanced Mobile Experience

**Files for Mobile Optimization**:
- `src/styles.css` - Global responsive improvements
- `src/app/events/aktuell/aktuell.component.css` - Landing page mobile layout
- `src/app/events/archiv-detail/archiv-detail.component.css` - Event detail mobile view
- `src/app/ui/layout/header/header.component.html` - Mobile navigation improvements

**Considerations**:
- Image lazy loading implementation
- Touch-friendly interaction patterns
- Performance optimization for mobile networks

### User Account and Community Features

**Major Architecture Change Required**:
- User authentication system
- User profile management
- Community interaction features (comments, favorites, etc.)
- Session state management

**New Services Needed**:
- Authentication service
- User profile service
- Community interaction services
- State management (NgRx or similar)

## Appendix - Useful Commands and Scripts

### Frequently Used Commands

```bash
npm run start         # Development server with auto-open
npm run build         # Production build to dist/BATbern2
npm test             # Unit tests
npm run lint         # Code linting
npm run e2e          # End-to-end tests
```

### Project-Specific Notes

**Build Output**: Always outputs to `dist/BATbern2/` due to historical configuration

**Asset Handling**: JSON files in `src/api/` are automatically copied to build output

**Debugging**: Use browser dev tools - no special debugging configuration

### Development Workflow

1. **Local Changes**: Modify components/services as needed
2. **Data Updates**: Edit JSON files in `src/api/` for content changes
3. **Testing**: Run `npm test` for unit tests
4. **Build**: Run `npm run build` for production bundle
5. **Deployment**: Copy `dist/BATbern2/` contents to web server

### Common Issues and Solutions

**Large JSON File**: `sessions.json` may cause memory issues in development - consider chunking for enhancements

**Routing Problems**: Remember most routes are commented out - activate in `router.ts` as needed

**Asset Loading**: Ensure new assets are placed in `src/assets/` and referenced correctly

**External Dependencies**: Be cautious with external service integrations (Slideshare, Twitter) - they may change without notice