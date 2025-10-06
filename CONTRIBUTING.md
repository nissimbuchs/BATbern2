# Contributing to BATbern Platform

Thank you for contributing to the BATbern Platform! This guide will help you get started with development, testing, and submitting contributions.

## Table of Contents

- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Build System](#build-system)
- [Development Workflow](#development-workflow)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Pull Request Process](#pull-request-process)
- [Architecture Guidelines](#architecture-guidelines)

## Getting Started

### Prerequisites

Before you begin, ensure you have the following installed:

- **Java 21 LTS** - [Download](https://adoptium.net/)
- **Node.js 20+** - [Download](https://nodejs.org/)
- **Docker Desktop** - [Download](https://www.docker.com/products/docker-desktop)
- **AWS CLI v2** - [Installation Guide](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html)
- **AWS CDK v2.110+** - Install: `npm install -g aws-cdk`
- **Make** - Usually pre-installed on macOS/Linux. For Windows, use WSL or install via Chocolatey
- **jq** - JSON processor: `brew install jq` (macOS) or `apt-get install jq` (Linux)

### AWS Credentials

Configure AWS credentials for local development:

```bash
# Configure AWS profiles
aws configure --profile batbern-mgmt
aws configure --profile batbern-dev
aws configure --profile batbern-staging
```

Contact the team lead for AWS access credentials.

## Development Setup

### 1. Clone the Repository

```bash
git clone https://github.com/nisimbuchs/BATbern2.git
cd BATbern
```

### 2. Install Dependencies

**Option A: Install Everything (Recommended)**
```bash
make install
```

**Option B: Install Selectively**
```bash
make install-java    # Java/Gradle projects only
make install-node    # Node.js projects only
```

### 3. Set Up Local Environment

Generate `.env` configuration from AWS:

```bash
AWS_PROFILE=batbern-mgmt ./scripts/dev/setup-env.sh
```

### 4. Start Services

**Option A: Docker Compose (Recommended)**
```bash
make docker-up
# or
docker-compose up -d
```

**Option B: Manual Setup**
```bash
# Terminal 1: Start API Gateway
cd api-gateway
./gradlew bootRun

# Terminal 2: Start Frontend
cd web-frontend
npm run dev
```

### 5. Verify Setup

```bash
# Check if services are running
docker-compose ps

# Test API Gateway
curl http://localhost:8080/actuator/health

# Open frontend in browser
open http://localhost:3000
```

## Build System

The project uses a **unified Makefile** that orchestrates builds across Java/Gradle and Node.js/npm projects.

### Quick Reference

```bash
make help              # Show all available commands
make install           # Install all dependencies
make build             # Build all projects
make test              # Run all tests
make verify            # Pre-commit checks (lint + test)
make all               # Complete workflow (clean + install + build + test)
```

### Common Commands

**Building:**
```bash
make build             # Build everything
make build-java        # Build Java projects only
make build-node        # Build Node.js projects only
```

**Testing:**
```bash
make test              # Run all tests
make test-java         # Java tests only
make test-node         # Node.js tests only
make test-coverage     # Generate coverage reports
```

**Code Quality:**
```bash
make lint              # Run all linters
make lint-java         # Java linters only
make lint-node         # Node.js linters only
make format            # Format all code
make format-check      # Check formatting without changes
```

**Cleanup:**
```bash
make clean             # Clean all build artifacts
make clean-java        # Clean Java builds only
make clean-node        # Clean Node.js builds only
```

### NPM Alternative

If you prefer npm commands:

```bash
npm run build          # Same as: make build
npm test               # Same as: make test
npm run lint           # Same as: make lint
npm run verify         # Same as: make verify
```

Run `npm run` to see all available scripts.

## Development Workflow

### 1. Create Feature Branch

```bash
# Always branch from develop
git checkout develop
git pull origin develop

# Create feature branch
git checkout -b feature/your-feature-name
```

### 2. Make Changes

Follow the **Test-Driven Development (TDD)** cycle:

1. **RED Phase**: Write failing tests first
2. **GREEN Phase**: Write minimal code to pass tests
3. **REFACTOR Phase**: Improve code while keeping tests green

### 3. Run Tests Frequently

```bash
# Run tests during development
make test

# Run with coverage
make test-coverage
```

### 4. Verify Before Commit

```bash
# Run linting and tests
make verify

# Check formatting
make format-check
```

### 5. Commit Changes

Follow the conventional commit format:

```
type(scope): description

feat(event): add automated speaker invitations
fix(frontend): resolve pagination bug
docs(api): update OpenAPI spec
test(integration): add contract tests
refactor(auth): simplify token validation
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `test`: Adding or updating tests
- `refactor`: Code refactoring
- `style`: Code style changes
- `perf`: Performance improvements
- `chore`: Build process or tooling changes

### 6. Push and Create PR

```bash
# Push your branch
git push origin feature/your-feature-name

# Create PR via GitHub UI
# Target: develop branch
```

## Coding Standards

### Java/Spring Boot

**General Principles:**
- Follow **Domain-Driven Design (DDD)** patterns
- Use **SOLID principles**
- Prefer **composition over inheritance**
- Keep methods small and focused (< 20 lines)
- One class per file

**Package Structure:**
```
src/main/java/ch/batbern/{service}/
├── api/           # REST controllers
├── domain/        # Domain models and business logic
├── application/   # Application services
├── infrastructure/# Infrastructure concerns
└── config/        # Configuration classes
```

**Code Style:**
- **Line length**: Max 120 characters
- **Indentation**: 4 spaces (no tabs)
- **Naming**:
  - Classes: `PascalCase`
  - Methods: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
- **Annotations**: Use Lombok sparingly, prefer explicit code

**Testing:**
- Unit tests in `src/test/java`
- Integration tests use `@SpringBootTest`
- Test class names end with `Test` (e.g., `EventServiceTest`)

### TypeScript/React

**General Principles:**
- Use **functional components** with hooks
- Prefer **composition** over complex inheritance
- Keep components small and focused
- Use **TypeScript strict mode**

**File Structure:**
```
src/
├── components/     # Reusable UI components
├── pages/          # Page components
├── features/       # Feature modules
├── hooks/          # Custom React hooks
├── services/       # API services
├── utils/          # Utility functions
└── types/          # TypeScript types
```

**Code Style:**
- **Line length**: Max 100 characters
- **Indentation**: 2 spaces
- **Naming**:
  - Components: `PascalCase`
  - Functions: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
- **Imports**: Use absolute imports from `src/`

**Testing:**
- Component tests use **React Testing Library**
- Unit tests use **Vitest**
- Test files: `*.test.tsx` or `*.spec.tsx`

### Documentation

- **Code Comments**: Explain *why*, not *what*
- **JSDoc/JavaDoc**: Document public APIs
- **README**: Update when adding features
- **OpenAPI Specs**: Keep API specs in sync with code

## Testing Guidelines

### Coverage Requirements

- **Unit Tests**: 90% coverage for business logic
- **Integration Tests**: 80% coverage for APIs
- **Overall**: 85% line coverage minimum

### Running Tests

**All tests:**
```bash
make test
```

**With coverage:**
```bash
make test-coverage

# View reports:
# Java:     build/reports/jacoco/test/html/index.html
# CDK:      infrastructure/coverage/index.html
# Frontend: web-frontend/coverage/index.html
```

**Individual components:**
```bash
# Shared kernel
cd shared-kernel && ./gradlew test

# API Gateway
cd api-gateway && ./gradlew test

# Domain service
cd services/event-management-service && ./gradlew test

# Infrastructure
cd infrastructure && npm test

# Frontend
cd web-frontend && npm test
```

### Test Structure

**Java (JUnit 5):**
```java
@Test
void shouldCreateEventSuccessfully() {
    // Given (Arrange)
    EventRequest request = new EventRequest("BATbern 2025");

    // When (Act)
    Event event = eventService.createEvent(request);

    // Then (Assert)
    assertThat(event.getName()).isEqualTo("BATbern 2025");
}
```

**TypeScript (Vitest):**
```typescript
describe('EventList', () => {
  it('should render events successfully', () => {
    // Arrange
    const events = [{ id: 1, name: 'BATbern 2025' }];

    // Act
    render(<EventList events={events} />);

    // Assert
    expect(screen.getByText('BATbern 2025')).toBeInTheDocument();
  });
});
```

### Test Best Practices

1. **Test behavior, not implementation**
2. **Use descriptive test names** (should/when/given format)
3. **One assertion per test** (when possible)
4. **Use test fixtures** for complex setup
5. **Mock external dependencies**
6. **Test edge cases and error handling**

## Pull Request Process

### 1. Pre-PR Checklist

Before creating a PR, ensure:

- ✅ All tests pass: `make test`
- ✅ Linting passes: `make lint`
- ✅ Coverage meets requirements: `make test-coverage`
- ✅ Code is formatted: `make format`
- ✅ No compiler warnings
- ✅ Documentation is updated
- ✅ Commits follow convention

**Quick verification:**
```bash
make verify
```

### 2. Create Pull Request

1. **Push your branch:**
   ```bash
   git push origin feature/your-feature-name
   ```

2. **Create PR on GitHub:**
   - Target: `develop` branch
   - Use descriptive title
   - Fill out PR template
   - Link related issues

3. **PR Description Template:**
   ```markdown
   ## Description
   Brief description of changes

   ## Type of Change
   - [ ] Bug fix
   - [ ] New feature
   - [ ] Breaking change
   - [ ] Documentation update

   ## Testing
   - [ ] Unit tests added/updated
   - [ ] Integration tests added/updated
   - [ ] Manual testing performed

   ## Checklist
   - [ ] Tests pass locally
   - [ ] Code follows style guidelines
   - [ ] Coverage meets requirements (90%)
   - [ ] Documentation updated
   - [ ] No new warnings
   ```

### 3. Code Review

- Address reviewer feedback promptly
- Keep discussions constructive
- Update PR based on feedback
- Request re-review when ready

### 4. Merge

Once approved:
- **Squash and merge** for feature branches
- **Delete branch** after merge
- Deployment to dev happens automatically

## Architecture Guidelines

### Microservices Architecture

Each service follows **Domain-Driven Design (DDD)** principles:

- **Bounded Contexts**: Each service owns its domain
- **Shared Kernel**: Common types via `shared-kernel` module
- **Event-Driven**: Services communicate via EventBridge
- **API Gateway**: Single entry point for external clients

### Technology Choices

**Backend:**
- **Spring Boot 3.5+** for microservices
- **PostgreSQL 15** for relational data
- **Redis 7.2** for caching
- **AWS EventBridge** for event-driven communication

**Frontend:**
- **React 18** with TypeScript
- **Material-UI (MUI)** for components
- **React Query** for data fetching
- **Zustand** for state management

**Infrastructure:**
- **AWS CDK** for Infrastructure as Code
- **ECS Fargate** for container orchestration
- **RDS** for managed databases
- **CloudFront + S3** for frontend hosting

### Service Communication

1. **Synchronous**: REST APIs via API Gateway
2. **Asynchronous**: Domain events via EventBridge
3. **Shared Data**: Shared Kernel types only

### Security

- **Authentication**: AWS Cognito
- **Authorization**: JWT tokens with role-based access
- **API Security**: Rate limiting, CORS, input validation
- **Secrets**: AWS Secrets Manager (never commit secrets)

## Additional Resources

- [Architecture Documentation](docs/architecture/)
- [API Documentation](docs/api/)
- [Deployment Guide](docs/deployment/)
- [Troubleshooting Guide](docs/guides/)

## Getting Help

- **Technical Questions**: Ask in team Slack channel
- **Issues**: Create GitHub issue
- **Security Issues**: Email security@berner-architekten-treffen.ch

## License

Copyright © 2025 Berner Architekten Treffen (BATbern). All rights reserved.
