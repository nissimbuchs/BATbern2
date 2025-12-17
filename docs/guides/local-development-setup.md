# Local Development Setup with Docker PostgreSQL

## Overview

This guide describes how to run BATbern locally using:
- **Local PostgreSQL** in Docker (eliminates AWS RDS costs)
- **Development Cognito** for authentication (can be switched to staging)
- **Local microservices** running natively or in Docker

**Cost Savings**: This setup eliminates the need for a dedicated AWS development environment, saving **$600-720/year**.

## Architecture

```
Local Development Environment:
├─ PostgreSQL 15.4 (Docker container with persistent volume)
├─ Development/Staging Cognito (AWS - shared)
├─ All microservices (running natively on your machine)
└─ Frontend (Vite dev server)
```

## Prerequisites

- Docker Desktop installed and running
- Java 21 installed
- Node.js 20+ installed
- AWS CLI configured with `batbern-dev` profile (for Cognito access)
- PostgreSQL client tools (psql) - optional but recommended

## Quick Start

### 1. Start Local PostgreSQL

```bash
# Start PostgreSQL container
docker compose -f docker-compose-dev.yml up -d

# Verify it's running
docker ps --filter "name=batbern-dev-postgres"

# Check health
docker exec batbern-dev-postgres pg_isready -U postgres
```

### 2. Configure Environment

The `.env` file is already configured for local PostgreSQL:

```bash
# Database - Local Docker PostgreSQL
DB_HOST=localhost
DB_PORT=5432
DB_NAME=batbern_development
DB_USER=postgres
DB_PASSWORD=devpass123

# Cognito - Development (or Staging)
COGNITO_USER_POOL_ID=eu-central-1_camJHQhZ8
COGNITO_CLIENT_ID=5h9421vo002bi7udjdu5orp7u3
```

**Frontend configuration** is in `web-frontend/.env.local`:

```bash
VITE_COGNITO_USER_POOL_ID=eu-central-1_camJHQhZ8
VITE_COGNITO_WEB_CLIENT_ID=5h9421vo002bi7udjdu5orp7u3
VITE_COGNITO_DOMAIN=batbern-development-auth.auth.eu-central-1.amazoncognito.com
```

### 3. Start Services

```bash
# Start all services natively (recommended)
make dev-native-up

# OR start services in Docker
docker compose up -d
```

Services will start on their default ports:
- API Gateway: http://localhost:8080
- Company/User Management: http://localhost:8081
- Event Management: http://localhost:8082
- Speaker Coordination: http://localhost:8083
- Partner Coordination: http://localhost:8084
- Attendee Experience: http://localhost:8085
- Frontend: http://localhost:3000

### 4. Database Migrations

**Migrations run automatically** when services start (via Spring Boot Flyway).

To manually verify migrations:

```bash
# Connect to PostgreSQL
docker exec -it batbern-dev-postgres psql -U postgres -d batbern_development

# Check tables
\dt

# Check migration history
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

# Exit
\q
```

### 5. Sync Users from Cognito

After first startup, sync existing Cognito users to your local database:

```bash
# Prerequisites:
# 1. Services must be running (make dev-native-up)
# 2. You need a JWT token with ORGANIZER role

# Get JWT token:
# 1. Login to http://localhost:3000 with organizer credentials
# 2. Open DevTools > Application > Local Storage
# 3. Copy the JWT token

# Run sync
JWT_TOKEN='your-jwt-here' ./scripts/dev/sync-users-from-cognito.sh
```

The script will:
- Check sync status (Cognito vs Database user count)
- Create missing users in local database
- Deactivate orphaned users

## Database Operations

### Reset Database

```bash
# Stop and remove container + volume
docker compose -f docker-compose-dev.yml down -v

# Start fresh
docker compose -f docker-compose-dev.yml up -d
```

### Backup Database

```bash
# Create backup
docker exec batbern-dev-postgres pg_dump -U postgres batbern_development > backup.sql

# Restore backup
docker exec -i batbern-dev-postgres psql -U postgres -d batbern_development < backup.sql
```

### Direct SQL Access

```bash
# Using Docker exec
docker exec -it batbern-dev-postgres psql -U postgres -d batbern_development

# Using psql from host
PGPASSWORD=devpass123 psql -h localhost -p 5432 -U postgres -d batbern_development

# Example queries
SELECT COUNT(*) FROM user_profiles;
SELECT * FROM flyway_schema_history;
SELECT email, username, is_active FROM user_profiles LIMIT 10;
```

## User Management

### Approach: Local DB as Read-Only Mirror

The local database is a **read-only mirror** of **staging Cognito** users:

1. **User Registration**: Users register via Staging Cognito
2. **Lambda Triggers**: Cognito Lambda writes to staging RDS
3. **Sync to Local**: Run sync script to copy users to local PostgreSQL
4. **JWT Tokens**: Contain roles from staging database
5. **Local Services**: Read from local PostgreSQL (which mirrors staging)

**Important**: Local development always syncs with **staging Cognito** (not development).

### Syncing Users from Staging Cognito

The sync script automatically loads authentication tokens from `~/.batbern/staging.json`:

```bash
# First, get a staging authentication token (one-time setup)
./scripts/auth/get-token.sh staging your-email@example.com your-password

# Then sync users from staging (automatically uses stored token)
./scripts/dev/sync-users-from-cognito.sh
```

**How it works**:
1. Script automatically loads token from `~/.batbern/staging.json`
2. Auto-refreshes token if expired
3. Connects to **staging Cognito** using AWS profile `batbern-staging`
4. Calls local API Gateway on `localhost:8000` to trigger reconciliation
5. Syncs all staging Cognito users to local PostgreSQL

**Manual token override** (if needed):
```bash
JWT_TOKEN='your-token-here' ./scripts/dev/sync-users-from-cognito.sh
```

### Managing User Roles

**Important**: Since Lambda triggers write to staging RDS, all user role changes must be done in **staging**:

**Option 1**: Direct SQL (via SSH tunnel to staging RDS)

```bash
# Start tunnel to staging RDS (if needed)
./scripts/dev/start-db-tunnel.sh staging

# Connect to staging database
psql "postgresql://postgres:password@localhost:5433/batbern"

# Assign ORGANIZER role
INSERT INTO role_assignments (user_profile_id, role, granted_at)
SELECT id, 'ORGANIZER', CURRENT_TIMESTAMP
FROM user_profiles
WHERE email = 'user@example.com'
ON CONFLICT DO NOTHING;

# Then sync to local (uses stored staging token automatically)
./scripts/dev/sync-users-from-cognito.sh
```

**Option 2**: Use staging web UI (if deployed)

1. Login to staging web app as admin
2. Use Role Management UI to assign roles
3. Sync to local: `./scripts/dev/sync-users-from-cognito.sh`

## Troubleshooting

### PostgreSQL Won't Start

```bash
# Check if port 5432 is already in use
lsof -i :5432

# Stop conflicting PostgreSQL
brew services stop postgresql@15
```

### Services Can't Connect to Database

```bash
# Verify PostgreSQL is accessible
PGPASSWORD=devpass123 psql -h localhost -p 5432 -U postgres -d batbern_development -c "SELECT 1;"

# Check .env configuration
cat .env | grep DB_

# Restart services
make dev-native-restart
```

### Migrations Fail

```bash
# Check Flyway history
docker exec -it batbern-dev-postgres psql -U postgres -d batbern_development -c "SELECT * FROM flyway_schema_history;"

# Repair Flyway (if migrations are out of sync)
./gradlew :shared-kernel:flywayRepair
./gradlew :services:company-user-management-service:flywayRepair
# ... repeat for each service
```

### User Sync Fails

```bash
# Check API Gateway health
curl http://localhost:8000/actuator/health

# Verify authentication token is valid
cat ~/.batbern/staging.json | jq '.'

# Refresh token if expired
./scripts/auth/refresh-token.sh staging

# Verify AWS credentials (for staging Cognito access)
aws sts get-caller-identity --profile batbern-staging

# Check staging Cognito User Pool
aws cognito-idp list-users \
  --user-pool-id eu-central-1_camJHQhZ8 \
  --profile batbern-staging \
  --max-results 10

# Test sync with verbose output
./scripts/dev/sync-users-from-cognito.sh
```

## AWS Credentials Setup

To sync users from staging Cognito, you need AWS credentials for the `batbern-staging` profile:

```bash
# Configure AWS CLI with staging credentials
aws configure --profile batbern-staging

# Verify credentials work
aws sts get-caller-identity --profile batbern-staging

# Expected output should show staging account (954163570305)
```

**Note**: The `.env` file should already be configured for staging Cognito (configured during initial setup). If not, update it:

```bash
COGNITO_USER_POOL_ID=eu-central-1_camJHQhZ8
COGNITO_CLIENT_ID=5h9421vo002bi7udjdu5orp7u3
COGNITO_DOMAIN_URL=https://batbern-staging-auth.auth.eu-central-1.amazoncognito.com
```

## Cost Comparison

**Before (AWS Development Environment)**:
- RDS t4g.micro: $7-9/month
- Bastion t4g.nano: $1/month
- NAT Gateway: $32/month
- Other: $5-10/month
- **Total**: ~$50-60/month

**After (Local PostgreSQL)**:
- Development Environment: **$0/month** ✅
- **Annual Savings**: $600-720/year

## Next Steps

- **Production Parity**: While local PostgreSQL is convenient, remember that production uses AWS RDS
- **Test Migrations**: Always test database migrations against a cloud PostgreSQL instance before deploying
- **Backup Regularly**: Your local database data is only as safe as your Docker volume backups
- **Consider Staging**: For final testing before production, use the staging environment

## Related Documentation

- [CLAUDE.md](../../CLAUDE.md) - Development workflow and commands
- [Architecture: Database](../architecture/03-data-architecture.md) - Database design
- [Cost Reduction Plan](/Users/nissim/.claude/plans/wobbly-tickling-dewdrop.md) - Detailed analysis and options
