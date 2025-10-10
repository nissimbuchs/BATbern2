# BATbern Scripts

Central location for all operational scripts used in development, CI/CD, and configuration management.

## Quick Reference

### Configuration Management

```bash
# Generate backend configuration for development
./scripts/config/sync-backend-config.sh development

# Generate for staging
./scripts/config/sync-backend-config.sh staging

# Generate for production
./scripts/config/sync-backend-config.sh production

# Validate configuration
./scripts/config/validate-backend-config.sh
```

### Testing

```bash
# Test AWS connectivity
./scripts/test/test-aws-connectivity.sh

# Test Docker Compose startup
./scripts/test/test-docker-compose-startup.sh

# Test service health
./scripts/test/test-service-health.sh

# Test environment setup
./scripts/test/test-setup-env.sh
```

### CI/CD

```bash
# Build and push Docker images
./scripts/build-and-push-services.sh

# Setup ECR repositories
./scripts/ci/setup-ecr-repositories.sh

# Setup GitHub OIDC provider
./scripts/ci/setup-github-oidc-provider.sh

# Run smoke tests
./scripts/ci/smoke-tests.sh

# Run regression suite
./scripts/ci/regression-suite.sh
```

---

## Directory Structure

```
scripts/
├── README.md                          # This file
│
├── config/                            # Configuration management
│   ├── sync-backend-config.sh         # Generate .env from CDK outputs
│   └── validate-backend-config.sh     # Validate .env completeness
│
├── test/                              # Testing scripts
│   ├── test-aws-connectivity.sh       # Test AWS access
│   ├── test-docker-compose-startup.sh # Test Docker Compose
│   ├── test-service-health.sh         # Test service endpoints
│   └── test-setup-env.sh              # Test environment setup
│
├── ci/                                # CI/CD automation
│   ├── setup-ecr-repositories.sh      # Setup container registries
│   ├── setup-github-oidc-provider.sh  # Setup GitHub Actions OIDC
│   ├── smoke-tests.sh                 # Post-deployment smoke tests
│   ├── regression-suite.sh            # Full regression tests
│   ├── performance-tests.sh           # Performance benchmarks
│   ├── security-scan.sh               # Security scanning
│   ├── rollback-deployment.sh         # Rollback to previous version
│   ├── monitor-canary.sh              # Monitor canary deployments
│   └── validate-*.sh                  # Various validation scripts
│
└── build-and-push-services.sh         # Build & push all Docker images
```

---

## Script Categories

### 1. Configuration Management (`config/`)

**Purpose:** Generate and validate environment-specific configuration files.

#### sync-backend-config.sh

Fetches configuration from AWS CDK stack outputs and generates `.env` file.

**Usage:**
```bash
./scripts/config/sync-backend-config.sh [environment]
```

**Arguments:**
- `environment`: `development` (default), `staging`, or `production`

**What it does:**
1. Connects to AWS using environment-specific profile
2. Fetches CloudFormation stack outputs (Database, Cognito, etc.)
3. Retrieves secrets from AWS Secrets Manager
4. Populates `.env` template with actual values
5. Generates `.env` file in project root

**Prerequisites:**
- AWS CLI installed and configured
- jq installed (`brew install jq`)
- AWS credentials for target environment

**Example:**
```bash
# Development (uses batbern-dev profile)
./scripts/config/sync-backend-config.sh development

# Staging (uses batbern-staging profile)
./scripts/config/sync-backend-config.sh staging
```

---

#### validate-backend-config.sh

Validates that all required environment variables are present in `.env`.

**Usage:**
```bash
./scripts/config/validate-backend-config.sh
```

**What it checks:**
- Required variables exist
- Variables have non-empty values
- No placeholder values (like `NOT_DEPLOYED`)

**Exit codes:**
- `0`: All required variables present and valid
- `1`: Missing or invalid variables

**Example output:**
```
✅ All required variables present and have values

Configuration Summary:
  Environment:    development
  Database:       batbern-dev-db.xxxxx.rds.amazonaws.com
  Cognito Pool:   eu-central-1_xxxxx
```

---

### 2. Testing Scripts (`test/`)

**Purpose:** Verify local development environment setup and connectivity.

#### test-aws-connectivity.sh

Tests AWS credentials and access to required services.

**Usage:**
```bash
./scripts/test/test-aws-connectivity.sh
```

---

#### test-docker-compose-startup.sh

Tests that all Docker Compose services start successfully.

**Usage:**
```bash
./scripts/test/test-docker-compose-startup.sh
```

---

#### test-service-health.sh

Tests that all services respond to health check endpoints.

**Usage:**
```bash
./scripts/test/test-service-health.sh
```

---

#### test-setup-env.sh

Tests the complete environment setup process.

**Usage:**
```bash
./scripts/test/test-setup-env.sh
```

---

### 3. CI/CD Scripts (`ci/`)

**Purpose:** Automation for continuous integration and deployment pipelines.

See individual script headers for detailed usage. Most are called by GitHub Actions workflows.

---

## Common Workflows

### Initial Development Setup

```bash
# 1. Clone repository
git clone https://github.com/nissimbuchs/BATbern2.git
cd BATbern2

# 2. Deploy infrastructure (if needed)
cd infrastructure
npx cdk deploy --all --profile batbern-dev
cd ..

# 3. Generate configuration
./scripts/config/sync-backend-config.sh development

# 4. Validate configuration
./scripts/config/validate-backend-config.sh

# 5. Start services
docker-compose up -d

# 6. View logs
docker-compose logs -f api-gateway
```

---

### After CDK Changes

```bash
# 1. Deploy updated infrastructure
cd infrastructure
npx cdk deploy --all --profile batbern-dev
cd ..

# 2. Regenerate configuration
./scripts/config/sync-backend-config.sh development

# 3. Restart services to pick up new config
docker-compose restart
```

---

### Before Committing

```bash
# 1. Validate configuration is correct
./scripts/config/validate-backend-config.sh

# 2. Run tests
npm run test  # Unit tests

# 3. Test E2E
npm run test:e2e  # Playwright tests
```

---

### Deploying to Staging

```bash
# 1. Generate staging configuration
./scripts/config/sync-backend-config.sh staging

# 2. Validate
./scripts/config/validate-backend-config.sh

# 3. Build Docker images
./scripts/build-and-push-services.sh

# 4. Deploy via CDK
cd infrastructure
npx cdk deploy --all --profile batbern-staging
```

---

## Environment Variables

Scripts use environment variables for configuration:

| Variable | Description | Default |
|----------|-------------|---------|
| `AWS_PROFILE` | AWS CLI profile to use | `batbern-{environment}` |
| `AWS_REGION` | AWS region | `eu-central-1` |
| `ENVIRONMENT` | Target environment | `development` |

**Example:**
```bash
# Override default profile
AWS_PROFILE=my-custom-profile ./scripts/config/sync-backend-config.sh development
```

---

## Best Practices

### ✅ DO

- **Run validation** after generating configuration
- **Use the sync script** - Don't manually edit `.env`
- **Check AWS credentials** before running config scripts
- **Review generated config** before starting services
- **Keep scripts executable** - `chmod +x scripts/**/*.sh`

### ❌ DON'T

- **Don't commit .env files** - Contains sensitive credentials
- **Don't hardcode credentials** in scripts
- **Don't skip validation** - Catch issues early
- **Don't manually edit generated files** - Always regenerate
- **Don't use `setup-env.sh`** - It no longer exists (use sync script directly)

---

## Troubleshooting

### "AWS credentials not configured"

```bash
# Configure AWS credentials for development
aws configure --profile batbern-dev
```

### "Could not fetch stack output"

```bash
# Deploy missing stack
cd infrastructure
npx cdk deploy BATbern-development-Database --profile batbern-dev
```

### "Command not found: jq"

```bash
# Install jq
brew install jq  # macOS
# or
apt-get install jq  # Linux
```

### "Missing required variables"

```bash
# Check what's in .env
cat .env

# Regenerate
./scripts/config/sync-backend-config.sh development
```

---

## Migration Notes

### Removed Scripts (Obsolete)

The following scripts have been removed as they are no longer needed:

- ❌ `scripts/dev/setup-env.sh` - Use `scripts/config/sync-backend-config.sh` directly
- ❌ `scripts/dev/update-cognito-env.sh` - Obsolete (updated VITE_ variables that no longer exist)

**Why:** Frontend now uses runtime configuration (loads from `/api/v1/config`), so VITE_ variables are no longer needed in `.env` files.

---

## Documentation

- [Backend Configuration Guide](../docs/BACKEND_CONFIGURATION.md)
- [Frontend Configuration Guide](../web-frontend/CONFIG.md)
- [Configuration Migration Guide](../docs/CONFIGURATION_MIGRATION.md)

---

## Support

For issues or questions:

1. Check the [Backend Configuration Guide](../docs/BACKEND_CONFIGURATION.md)
2. Review script output for error messages
3. Validate AWS credentials and permissions
4. Check CloudFormation stacks are deployed
5. Consult the relevant documentation above

---

**Remember: Configuration is auto-generated from CDK - always use the sync scripts, never edit `.env` manually!**
