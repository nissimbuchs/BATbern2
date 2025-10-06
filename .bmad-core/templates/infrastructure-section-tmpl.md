# Infrastructure Section Template

Use this template for all story infrastructure impact assessments to ensure consistency with architecture.md specifications.

## Infrastructure Impact Assessment ⚠️ REQUIRED

### Frontend Changes
- [ ] **New Components/Pages** - [List new Angular components and pages]
- [ ] **State Management** - RxJS service updates needed (BehaviorSubjects, observables):
  - [ ] [Specific state management changes]
  - [ ] [Loading and error state handling]
  - [ ] [Data synchronization requirements]
- [ ] **Routing** - Angular routing configuration changes:
  - [ ] [New routes and guards]
  - [ ] [Role-based access control updates]
  - [ ] [Lazy loading module changes]
- [ ] **API Integration** - HTTP client and interceptor modifications:
  - [ ] [New HTTP services]
  - [ ] [Authentication interceptor updates]
  - [ ] [Error handling modifications]

### Backend Changes  
- [ ] **New API Endpoints** - REST endpoints for deployment:
  - [ ] `[METHOD] /endpoint/path` - [Description]
  - [ ] [Additional endpoints as needed]
- [ ] **Lambda Functions** - AWS Lambda handler implementations:
  - [ ] Handler file: `apps/api/src/handlers/[path]/[handler].ts`
  - [ ] [Additional Lambda functions as needed]
- [ ] **Database Changes** - PostgreSQL + PostGIS schema modifications:
  - [ ] Migration script: `[###]_[description].sql`
  - [ ] [Table modifications, indexes, constraints]
  - [ ] [PostGIS geographic functions if applicable]
- [ ] **Authentication** - Cognito/JWT integration updates:
  - [ ] [Token validation changes]
  - [ ] [Role-based access control modifications]
  - [ ] [User group management updates]

### Infrastructure Changes (AWS CDK)
- [ ] **API Gateway** - CORS, authorizers, endpoints:
  - File: `infrastructure/lib/api-gateway-stack.ts`
  - [Specific API Gateway configuration changes]
- [ ] **Lambda Configuration** - Function deployments and permissions:
  - File: `infrastructure/lib/lambda-stack.ts`  
  - [Lambda function configurations and IAM permissions]
- [ ] **RDS PostgreSQL** - Database and PostGIS configuration:
  - [Database schema updates, connection settings]
  - [PostGIS extension requirements if geographic features involved]
- [ ] **ElastiCache Redis** - Caching layer modifications:
  - [Session storage requirements]
  - [Query result caching needs]
  - [Cache invalidation strategies]
- [ ] **AWS Cognito** - User pool and authentication setup:
  - [User pool configuration changes]
  - [User group modifications]
  - [JWT token settings]
- [ ] **AWS SES** - Email notification configuration:
  - [Email template updates]
  - [Notification triggers]
  - [Bounce/complaint handling]
- [ ] **AWS Location Service** - Geocoding and address validation:
  - [Place index configuration for European addresses]
  - [Swiss address geocoding requirements]
  - [Geographic calculation needs]
- [ ] **S3 + CloudFront** - Static hosting and CDN updates:
  - [Static asset deployment changes]
  - [CDN cache invalidation needs]
  - [Content type configurations]
- [ ] **Environment Variables** - Configuration management:
  - Development: `docker-compose.yml` - [Local environment changes]
  - Production: [AWS Parameter Store or environment variables]
- [ ] **IAM Permissions** - Security and access control:
  - [Lambda execution role updates]
  - [Cross-service permissions]
  - [Resource access policies]
- [ ] **CloudWatch** - Monitoring and logging setup:
  - [Custom metrics requirements]
  - [Log group configurations]
  - [Alarm thresholds]

### Development Environment
- [ ] **Docker Services** - docker-compose.yml modifications:
  - [New service containers]
  - [Port mappings and volume mounts]
  - [Service dependency updates]
- [ ] **LocalStack** - AWS service mocking (95% production parity):
  - [LocalStack service configurations]
  - [Initialization scripts in localstack/init/]
  - [Mock data and service setup]
- [ ] **Sample Data** - Development data and user management:
  - [Database seed data updates]
  - [Mock user accounts and roles]
  - [Test data for geographic calculations]
- [ ] **Environment Setup** - Development workflow changes:
  - [Package.json script updates]
  - [Development server configurations]
  - [Build and deployment script modifications]

### Performance Impact
- [ ] **Frontend Bundle Size** - Impact on <200KB target (NFR10):
  - [Bundle size analysis]
  - [Code splitting requirements]
  - [Lazy loading optimizations]
- [ ] **API Response Time** - Impact on <500ms target (NFR1):
  - [Database query optimization]
  - [API endpoint performance analysis]
  - [Caching strategy implementation]
- [ ] **Database Performance** - Query optimization and indexing:
  - [New database indexes required]
  - [Query performance considerations]
  - [Connection pool settings]
- [ ] **Cache Strategy** - ElastiCache integration and hit rates:
  - [Cacheable data identification]
  - [Cache key strategies]
  - [Cache expiration policies]

## Template Usage Instructions

### For Story Authors:
1. Copy this template into your story under "## Infrastructure Impact Assessment"
2. Fill out only the sections relevant to your story
3. Remove unused sections or mark as "No changes required"
4. Reference specific files in the CDK infrastructure directory
5. Ensure all changes align with architecture.md specifications

### Required Architecture Alignment:
- **Platform**: AWS Full Stack (S3+CloudFront frontend, Lambda+API Gateway backend)
- **Database**: RDS PostgreSQL 15+ with PostGIS extension
- **Authentication**: Amazon Cognito with JWT tokens
- **Caching**: ElastiCache Redis 7.0+
- **Region**: EU-Central-1 (Frankfurt) for Swiss data residency
- **Development**: LocalStack providing 95% AWS production parity

### Technology Stack Consistency:
- **Frontend**: Angular 17+, TypeScript 5.3+, Angular Material 17+
- **Backend**: Node.js 20+, TypeScript 5.3+, AWS Lambda + Fastify
- **Infrastructure**: AWS CDK 2.100+ in TypeScript
- **Testing**: Jest (frontend), Vitest (backend), Playwright (E2E)
- **Build**: Angular CLI, esbuild bundler

### Performance Requirements to Consider:
- **NFR1**: API response time <500ms
- **NFR10**: Frontend bundle size <200KB initial load
- **NFR3**: Mobile-responsive design
- **NFR4**: EU data residency compliance
- **NFR6**: HTTPS/TLS encryption
- **NFR7**: Secure JWT token management