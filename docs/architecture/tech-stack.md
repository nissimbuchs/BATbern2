# Technology Stack

## Technology Stack Table

> **Note:** Version numbers use major.minor notation (e.g., `5.x` means version 5.x+). For exact versions, see [versions.json](../versions.json) or dependency files (`package.json`, `build.gradle`).

| Category | Technology | Version | Purpose | Rationale |
|----------|------------|---------|----------|-----------|
| Frontend Language | TypeScript | 5.x | Type-safe frontend development | Essential for large-scale enterprise app with multiple developer teams |
| Frontend Framework | React | 19.x | Role-adaptive user interface | Mature ecosystem, excellent TypeScript support, component reusability |
| UI Component Library | Material-UI (MUI) | 7.x | Swiss-standard design components | Consistent with frontend spec, enterprise-grade accessibility |
| Data Visualization | Recharts | 2.x | Interactive charts and heat maps | React-native, TypeScript support, Material-UI compatible, declarative API |
| State Management | Zustand + React Query | 5.x | Client state + server state | Lightweight, TypeScript-first, excellent caching for content discovery |
| Backend Language | Java | 21 LTS | Enterprise service development | Long-term support, enterprise ecosystem, team expertise |
| Backend Framework | Spring Boot | 3.x | Microservices and REST APIs | Industry standard for Java enterprise, excellent cloud integration |
| API Style | REST + OpenAPI | 3.x | Service communication | Clear documentation, tooling support, enterprise standard |
| Database | PostgreSQL | 15+ | Primary data storage | ACID compliance, JSON support, excellent performance |
| Cache | Caffeine | 3.x | Application-level in-memory caching | High performance, cost-effective, zero external dependencies |
| File Storage | AWS S3 | Latest | Document and media storage | Enterprise-grade, CDN integration, Swiss compliance capable |
| Authentication | AWS Cognito | Latest | Multi-role user management | Enterprise SSO, fine-grained permissions, AWS ecosystem |
| Frontend Testing | Vitest + React Testing Library | 3.x / 16.x | Component and integration testing | Fast, TypeScript native, React best practices |
| Backend Testing | JUnit + Testcontainers | 5.x / 1.x | Service and integration testing | Java standard, real database testing |
| E2E Testing | Playwright | 1.x | Full application workflows | Cross-browser, reliable, excellent CI integration |
| Build Tool | Gradle | 8.x | Java service builds | Superior dependency management, enterprise standard |
| Bundler | Vite | 7.x | Frontend build and dev server | Fast HMR, TypeScript support, modern tooling |
| IaC Tool | AWS CDK | 2.x | Infrastructure as code | Type-safe infrastructure, AWS native, versioned stacks |
| CI/CD | GitHub Actions | Latest | Automated testing and deployment | Integrated with repository, excellent AWS integration |
| Monitoring | AWS CloudWatch + Grafana | Latest / 10.x | Application and infrastructure monitoring | Enterprise observability, custom dashboards |
| Logging | SLF4J + Logback | 2.x / 1.x | Structured application logging | Java standard, structured JSON logging |
| CSS Framework | Tailwind CSS | 3.x | Utility-first styling | Rapid development, consistent design system |

## Frontend Technology Details

### React Ecosystem
- **React 19.x**: Concurrent features, automatic batching, Suspense improvements
- **TypeScript 5.x**: Advanced type inference, decorators, import attributes
- **Material-UI 7.x**: Theming system, accessibility features, Swiss design compliance
- **Recharts 2.x**: Declarative charting library for React with TypeScript support
- **Zustand 5.x**: Minimal boilerplate, TypeScript-first state management
- **React Query 5.x**: Server state management, caching, background updates

### Development Tools
- **Vite 7.x**: Fast build times, hot module replacement, optimized bundling
- **ESLint 9.x**: Code quality and consistency enforcement
- **Prettier 3.x**: Code formatting standardization
- **Tailwind CSS 3.x**: Utility-first CSS framework for rapid UI development

### Testing Framework
- **Vitest 3.x**: Fast unit testing with TypeScript support
- **React Testing Library 16.x**: Component testing best practices
- **Playwright 1.x**: End-to-end testing across browsers

## Backend Technology Details

### Java Enterprise Stack
- **Java 21 LTS**: Long-term support, virtual threads, pattern matching
- **Spring Boot 3.x**: Auto-configuration, embedded servers, production-ready features
- **Spring Security 6.x**: OAuth2, JWT, method-level security
- **Spring Data JPA**: Repository pattern, query generation, transaction management

### Database & Caching
- **PostgreSQL 15+ (db.t4g.micro ARM-based, Single-AZ)**: Advanced SQL features, JSON support, full-text search, cost-optimized
- **Caffeine 3.x**: Application-level in-memory caching (replaces Redis ElastiCache for cost savings)
- **Flyway**: Database migration and versioning

### Build & Testing
- **Gradle 8.x**: Dependency management, multi-project builds, performance optimization
- **JUnit 5.x**: Modern testing framework with parameterized tests
- **Testcontainers 1.x**: Integration testing with real databases
- **MockMvc**: Spring MVC testing framework

## Infrastructure Technology

### AWS Cloud Services (Cost-Optimized)
- **AWS ECS Fargate**: Serverless container orchestration
- **AWS Cognito**: User authentication and authorization
- **AWS S3**: Object storage with CDN integration
- **AWS CloudFront**: Global content delivery network
- **AWS RDS (Single-AZ, db.t4g.micro)**: Managed PostgreSQL database service (cost-optimized)
- **AWS VPC (Single AZ, 1 NAT Gateway)**: Cost-optimized networking infrastructure
- **Note**: ElastiCache Redis removed; using application-level Caffeine caching for 83% cost reduction

### Infrastructure as Code
- **AWS CDK 2.x**: Type-safe infrastructure definition
- **TypeScript 5.x**: CDK language for infrastructure code
- **CloudFormation**: AWS native infrastructure templates

### CI/CD Pipeline
- **GitHub Actions**: Automated workflows and deployments
- **Docker**: Containerization for consistent deployments
- **AWS ECR**: Container image registry

## Monitoring & Observability

### Application Monitoring
- **AWS CloudWatch**: Native AWS service monitoring and logging
- **Grafana 10.x**: Advanced visualization and alerting
- **AWS X-Ray**: Distributed tracing and performance insights
- **Micrometer**: Application metrics collection

### Logging Framework
- **SLF4J 2.x**: Simple Logging Facade for Java
- **Logback 1.x**: Structured JSON logging
- **MDC**: Mapped Diagnostic Context for request correlation

## Development Environment

### Prerequisites
```bash
java --version          # Java 21 LTS
node --version         # Node.js 20+
docker --version       # Docker Desktop
aws --version          # AWS CLI v2
cdk --version          # AWS CDK v2.110+
gradle --version       # Gradle 8.5+
```

### IDE Recommendations
- **IntelliJ IDEA**: Java/Spring Boot development
- **VS Code**: Frontend development with TypeScript
- **DataGrip**: Database development and management

### Browser Support
- **Chrome**: Latest stable version (primary development and testing)
- **Firefox**: Latest stable version (cross-browser compatibility)
- **Safari**: Latest stable version (macOS compatibility)
- **Edge**: Latest stable version (Windows compatibility)

## Technology Decision Rationale

### Frontend Choices
- **React over Vue/Angular**: Mature ecosystem, excellent TypeScript support, component reusability
- **Material-UI over Custom CSS**: Swiss design compliance, accessibility features, rapid development
- **Recharts over D3.js**: React-native components, simpler API, better TypeScript integration, Material-UI compatible
- **Zustand over Redux**: Simpler API, less boilerplate, better TypeScript integration
- **React Query over SWR**: More comprehensive caching, better error handling, request deduplication

### Backend Choices
- **Java over Node.js/Python**: Enterprise ecosystem, long-term support, team expertise
- **Spring Boot over Micronaut/Quarkus**: Industry standard, extensive documentation, cloud integration
- **PostgreSQL over MySQL/MongoDB**: ACID compliance, advanced features, JSON support
- **Caffeine (in-memory) over Redis ElastiCache**: Cost-effective ($149/month savings), zero network latency, simplified architecture

### Infrastructure Choices
- **AWS over Azure/GCP**: Comprehensive service offering, CDK support, Swiss region availability
- **ECS Fargate over EKS**: Simpler operations, serverless scaling, lower management overhead
- **CDK over Terraform**: Type safety, AWS native, better IDE support
- **Single-AZ over Multi-AZ**: Cost-effective for low traffic (1000 users/month), acceptable 5-minute manual failover
- **ARM-based T4G instances over x86 T3**: Better price/performance ratio, 20% cost savings