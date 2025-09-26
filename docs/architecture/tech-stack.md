# Technology Stack

## Technology Stack Table

| Category | Technology | Version | Purpose | Rationale |
|----------|------------|---------|----------|-----------|
| Frontend Language | TypeScript | 5.3+ | Type-safe frontend development | Essential for large-scale enterprise app with multiple developer teams |
| Frontend Framework | React | 18.2+ | Role-adaptive user interface | Mature ecosystem, excellent TypeScript support, component reusability |
| UI Component Library | Material-UI (MUI) | 5.14+ | Swiss-standard design components | Consistent with frontend spec, enterprise-grade accessibility |
| State Management | Zustand + React Query | 4.4+ / 4.36+ | Client state + server state | Lightweight, TypeScript-first, excellent caching for content discovery |
| Backend Language | Java | 21 LTS | Enterprise service development | Long-term support, enterprise ecosystem, team expertise |
| Backend Framework | Spring Boot | 3.2+ | Microservices and REST APIs | Industry standard for Java enterprise, excellent cloud integration |
| API Style | REST + OpenAPI | 3.1 | Service communication | Clear documentation, tooling support, enterprise standard |
| Database | PostgreSQL | 15+ | Primary data storage | ACID compliance, JSON support, excellent performance |
| Cache | Redis | 7.2+ | Session and content caching | High performance, supports complex data structures |
| File Storage | AWS S3 | Latest | Document and media storage | Enterprise-grade, CDN integration, Swiss compliance capable |
| Authentication | AWS Cognito | Latest | Multi-role user management | Enterprise SSO, fine-grained permissions, AWS ecosystem |
| Frontend Testing | Vitest + React Testing Library | 1.0+ / 13.4+ | Component and integration testing | Fast, TypeScript native, React best practices |
| Backend Testing | JUnit + Testcontainers | 5.10+ / 1.19+ | Service and integration testing | Java standard, real database testing |
| E2E Testing | Playwright | 1.40+ | Full application workflows | Cross-browser, reliable, excellent CI integration |
| Build Tool | Gradle | 8.5+ | Java service builds | Superior dependency management, enterprise standard |
| Bundler | Vite | 5.0+ | Frontend build and dev server | Fast HMR, TypeScript support, modern tooling |
| IaC Tool | AWS CDK | 2.110+ | Infrastructure as code | Type-safe infrastructure, AWS native, versioned stacks |
| CI/CD | GitHub Actions | Latest | Automated testing and deployment | Integrated with repository, excellent AWS integration |
| Monitoring | AWS CloudWatch + Grafana | Latest / 10.2+ | Application and infrastructure monitoring | Enterprise observability, custom dashboards |
| Logging | SLF4J + Logback | 2.0+ / 1.4+ | Structured application logging | Java standard, structured JSON logging |
| CSS Framework | Tailwind CSS | 3.3+ | Utility-first styling | Rapid development, consistent design system |

## Frontend Technology Details

### React Ecosystem
- **React 18.2+**: Concurrent features, automatic batching, Suspense improvements
- **TypeScript 5.3+**: Advanced type inference, decorators, import attributes
- **Material-UI 5.14+**: Theming system, accessibility features, Swiss design compliance
- **Zustand 4.4+**: Minimal boilerplate, TypeScript-first state management
- **React Query 4.36+**: Server state management, caching, background updates

### Development Tools
- **Vite 5.0+**: Fast build times, hot module replacement, optimized bundling
- **ESLint**: Code quality and consistency enforcement
- **Prettier**: Code formatting standardization
- **Tailwind CSS 3.3+**: Utility-first CSS framework for rapid UI development

### Testing Framework
- **Vitest 1.0+**: Fast unit testing with TypeScript support
- **React Testing Library 13.4+**: Component testing best practices
- **Playwright 1.40+**: End-to-end testing across browsers

## Backend Technology Details

### Java Enterprise Stack
- **Java 21 LTS**: Long-term support, virtual threads, pattern matching
- **Spring Boot 3.2+**: Auto-configuration, embedded servers, production-ready features
- **Spring Security 6**: OAuth2, JWT, method-level security
- **Spring Data JPA**: Repository pattern, query generation, transaction management

### Database & Caching
- **PostgreSQL 15+**: Advanced SQL features, JSON support, full-text search
- **Redis 7.2+**: In-memory caching, pub/sub, data structures
- **Flyway**: Database migration and versioning

### Build & Testing
- **Gradle 8.5+**: Dependency management, multi-project builds, performance optimization
- **JUnit 5**: Modern testing framework with parameterized tests
- **Testcontainers 1.19+**: Integration testing with real databases
- **MockMvc**: Spring MVC testing framework

## Infrastructure Technology

### AWS Cloud Services
- **AWS ECS Fargate**: Serverless container orchestration
- **AWS Cognito**: User authentication and authorization
- **AWS S3**: Object storage with CDN integration
- **AWS CloudFront**: Global content delivery network
- **AWS ElastiCache**: Managed Redis caching service
- **AWS RDS**: Managed PostgreSQL database service

### Infrastructure as Code
- **AWS CDK 2.110+**: Type-safe infrastructure definition
- **TypeScript**: CDK language for infrastructure code
- **CloudFormation**: AWS native infrastructure templates

### CI/CD Pipeline
- **GitHub Actions**: Automated workflows and deployments
- **Docker**: Containerization for consistent deployments
- **AWS ECR**: Container image registry

## Monitoring & Observability

### Application Monitoring
- **AWS CloudWatch**: Native AWS service monitoring and logging
- **Grafana 10.2+**: Advanced visualization and alerting
- **AWS X-Ray**: Distributed tracing and performance insights
- **Micrometer**: Application metrics collection

### Logging Framework
- **SLF4J 2.0+**: Simple Logging Facade for Java
- **Logback 1.4+**: Structured JSON logging
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
- **Chrome 120+**: Primary development and testing browser
- **Firefox 121+**: Cross-browser compatibility testing
- **Safari 17+**: macOS compatibility testing
- **Edge 120+**: Windows compatibility testing

## Technology Decision Rationale

### Frontend Choices
- **React over Vue/Angular**: Mature ecosystem, excellent TypeScript support, component reusability
- **Material-UI over Custom CSS**: Swiss design compliance, accessibility features, rapid development
- **Zustand over Redux**: Simpler API, less boilerplate, better TypeScript integration
- **React Query over SWR**: More comprehensive caching, better error handling, request deduplication

### Backend Choices
- **Java over Node.js/Python**: Enterprise ecosystem, long-term support, team expertise
- **Spring Boot over Micronaut/Quarkus**: Industry standard, extensive documentation, cloud integration
- **PostgreSQL over MySQL/MongoDB**: ACID compliance, advanced features, JSON support
- **Redis over Memcached**: Richer data structures, pub/sub capabilities, clustering support

### Infrastructure Choices
- **AWS over Azure/GCP**: Comprehensive service offering, CDK support, Swiss region availability
- **ECS Fargate over EKS**: Simpler operations, serverless scaling, lower management overhead
- **CDK over Terraform**: Type safety, AWS native, better IDE support