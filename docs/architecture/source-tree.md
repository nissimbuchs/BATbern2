# Source Tree Structure

## Unified Project Structure

```
BATbern-Platform/
├── shared-kernel/                     # Shared types, events, utilities
├── services/                          # Domain services
│   ├── event-management/              # Event Management Service
│   ├── speaker-coordination/          # Speaker Coordination Service
│   ├── partner-coordination/          # Partner Coordination Service
│   ├── attendee-experience/           # Attendee Experience Service
│   └── company-management/            # Company Management Service
├── api-gateway/                       # Unified API Gateway
├── web-frontend/                      # React Frontend Application
├── infrastructure/                    # Infrastructure as Code
│   ├── global-stacks/                 # Region-independent resources
│   ├── core-infrastructure/           # Regional shared resources
│   ├── domain-stacks/                 # Per-domain infrastructure
│   └── environments/                  # Environment-specific configs
├── e2e-tests/                         # End-to-end tests
├── scripts/                           # Build and deployment scripts
├── docs/                              # Documentation
└── README.md                          # Project overview
```

## Service Structure Template

```
services/{domain-service}/
├── src/main/java/ch/batbern/{domain}/
│   ├── controller/                     # REST API controllers
│   ├── service/                        # Business logic layer
│   ├── repository/                     # Data access layer
│   ├── domain/                         # Domain models and entities
│   ├── dto/                           # Data transfer objects
│   ├── exception/                     # Custom exceptions
│   └── security/                      # Security components
├── src/main/resources/
│   ├── application.yml                # Configuration
│   └── db/migration/                  # Flyway migrations
├── src/test/java/                     # Unit and integration tests
├── build.gradle                      # Build configuration
└── README.md                         # Service documentation
```

## Frontend Structure

```
web-frontend/
├── src/
│   ├── components/                    # Reusable UI components
│   │   ├── common/                    # Shared components
│   │   ├── organizer/                 # Organizer-specific components
│   │   ├── speaker/                   # Speaker-specific components
│   │   ├── partner/                   # Partner-specific components
│   │   └── attendee/                  # Attendee-specific components
│   ├── hooks/                         # Custom React hooks
│   ├── services/                      # API client services
│   ├── stores/                        # Zustand state stores
│   ├── types/                         # TypeScript type definitions
│   ├── utils/                         # Utility functions
│   └── pages/                         # Page components
├── public/                            # Static assets
├── tests/                             # Component and integration tests
├── e2e/                              # End-to-end tests
├── package.json                       # Dependencies and scripts
├── vite.config.ts                     # Vite configuration
└── tailwind.config.js                # Tailwind CSS configuration
```

## Shared Kernel Structure

```
shared-kernel/
├── src/main/java/ch/batbern/shared/
│   ├── events/                        # Domain events
│   │   ├── EventCreatedEvent.java
│   │   ├── SpeakerInvitedEvent.java
│   │   └── PartnerAnalyticsEvent.java
│   ├── types/                         # Shared value objects
│   │   ├── CompanyId.java
│   │   ├── EventId.java
│   │   └── UserId.java
│   ├── exceptions/                    # Common exceptions
│   │   ├── DomainException.java
│   │   └── ValidationException.java
│   └── utils/                         # Utility classes
│       ├── DateTimeUtils.java
│       └── ValidationUtils.java
├── src/main/resources/
│   └── application-shared.yml         # Shared configuration
└── build.gradle                      # Shared dependencies
```

## Infrastructure Structure

```
infrastructure/
├── lib/                               # CDK construct library
│   ├── core-infrastructure-stack.ts  # Core AWS resources
│   ├── domain-service-stack.ts       # Service-specific resources
│   ├── frontend-stack.ts             # Frontend deployment
│   └── monitoring-stack.ts           # Observability resources
├── bin/                               # CDK applications
│   ├── core-app.ts                   # Core infrastructure app
│   ├── domain-app.ts                 # Domain services app
│   └── frontend-app.ts               # Frontend app
├── environments/                      # Environment configs
│   ├── dev.ts                        # Development environment
│   ├── staging.ts                    # Staging environment
│   └── prod.ts                       # Production environment
├── cdk.json                          # CDK configuration
├── package.json                      # CDK dependencies
└── tsconfig.json                     # TypeScript configuration
```

## API Gateway Structure

```
api-gateway/
├── src/main/java/ch/batbern/gateway/
│   ├── config/                        # Gateway configuration
│   │   ├── SecurityConfig.java
│   │   ├── CorsConfig.java
│   │   └── RateLimitConfig.java
│   ├── filter/                        # Request/response filters
│   │   ├── AuthenticationFilter.java
│   │   ├── LoggingFilter.java
│   │   └── RateLimitFilter.java
│   ├── route/                         # Route configurations
│   │   ├── EventRoutes.java
│   │   ├── SpeakerRoutes.java
│   │   └── PartnerRoutes.java
│   └── exception/                     # Gateway exceptions
│       └── GatewayException.java
├── src/main/resources/
│   ├── application.yml                # Gateway configuration
│   └── routes/                        # Route definitions
└── build.gradle                      # Gateway dependencies
```

## End-to-End Tests Structure

```
e2e-tests/
├── tests/                             # Test specifications
│   ├── organizer/                     # Organizer workflows
│   │   ├── event-creation.spec.ts
│   │   └── speaker-management.spec.ts
│   ├── speaker/                       # Speaker workflows
│   │   ├── invitation-response.spec.ts
│   │   └── material-upload.spec.ts
│   ├── partner/                       # Partner workflows
│   │   ├── analytics-dashboard.spec.ts
│   │   └── roi-reports.spec.ts
│   └── attendee/                      # Attendee workflows
│       ├── event-registration.spec.ts
│       └── content-search.spec.ts
├── fixtures/                          # Test data
│   ├── events.json
│   ├── speakers.json
│   └── companies.json
├── page-objects/                      # Page object models
│   ├── EventPage.ts
│   ├── SpeakerPage.ts
│   └── DashboardPage.ts
├── utils/                            # Test utilities
│   ├── auth-helper.ts
│   └── data-factory.ts
├── playwright.config.ts              # Playwright configuration
└── package.json                      # Test dependencies
```

## Scripts Structure

```
scripts/
├── development/                       # Development scripts
│   ├── setup-local-env.sh           # Local environment setup
│   ├── start-services.sh            # Start all services
│   └── generate-test-data.sh        # Generate test data
├── deployment/                       # Deployment scripts
│   ├── deploy-to-staging.sh         # Staging deployment
│   ├── deploy-to-production.sh      # Production deployment
│   └── rollback.sh                  # Rollback script
├── maintenance/                      # Maintenance scripts
│   ├── backup-database.sh           # Database backup
│   ├── clear-cache.sh               # Cache clearing
│   └── health-check.sh              # System health check
└── ci/                               # CI/CD scripts
    ├── build-and-test.sh            # Build and test pipeline
    ├── security-scan.sh             # Security scanning
    └── performance-test.sh          # Performance testing
```

## Documentation Structure

```
docs/
├── architecture/                      # Architecture documentation
│   ├── 01-system-overview.md
│   ├── 02-infrastructure-deployment.md
│   ├── 03-data-architecture.md
│   ├── 04-api-design.md
│   ├── 05-frontend-architecture.md
│   ├── 06-backend-architecture.md
│   ├── 07-development-standards.md
│   ├── 08-operations-security.md
│   ├── 09-aiml-architecture.md
│   ├── coding-standards.md           # Coding standards and conventions
│   ├── tech-stack.md                 # Technology stack details
│   ├── source-tree.md                # This file - source tree structure
│   └── index.md                      # Architecture overview
├── api/                              # API documentation
│   ├── openapi.yml                   # OpenAPI specification
│   ├── endpoints/                    # Endpoint documentation
│   └── examples/                     # Request/response examples
├── deployment/                       # Deployment guides
│   ├── local-setup.md                # Local development setup
│   ├── staging-deployment.md         # Staging deployment guide
│   └── production-deployment.md      # Production deployment guide
├── guides/                           # Developer guides
│   ├── getting-started.md            # Getting started guide
│   ├── testing-guide.md              # Testing best practices
│   └── troubleshooting.md            # Common issues and solutions
└── prd/                              # Product requirements
    ├── epic-*.md                     # Epic specifications
    └── user-stories/                 # User story details
```

## Key Directory Conventions

### Naming Conventions
- **Directories**: `kebab-case` for multi-word directories
- **Java packages**: `lowercase` following Java conventions
- **TypeScript files**: `camelCase.ts` or `PascalCase.tsx` for components
- **Configuration files**: `kebab-case.yml` or `camelCase.json`

### File Organization Principles
1. **Domain Separation**: Each bounded context has its own service directory
2. **Layered Architecture**: Clear separation of concerns within services
3. **Shared Resources**: Common code in shared-kernel, reusable infrastructure
4. **Test Proximity**: Tests close to the code they test
5. **Configuration Management**: Environment-specific configs in dedicated directories

### Integration Points
- **API Gateway**: Central entry point routing to domain services
- **Shared Kernel**: Common types and events shared across services
- **Infrastructure**: Shared AWS resources and deployment configurations
- **Frontend**: Single React application consuming all domain APIs

This structure supports the Domain-Driven Design approach with clear boundaries between bounded contexts while maintaining shared infrastructure and common patterns across the platform.