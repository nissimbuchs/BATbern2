# Docker Compose Troubleshooting Guide

This guide helps resolve common issues when running the BATbern platform with Docker Compose.

## Quick Diagnostics

```bash
# Check local service status
docker-compose ps

# View all local service logs
docker-compose logs

# View specific service logs
docker-compose logs -f {service-name}

# Check service health
docker-compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Health}}"

# Verify .env file exists and has AWS credentials
cat .env | grep -E "DB_HOST|COGNITO_USER_POOL_ID"

# Test AWS RDS connection
AWS_PROFILE=batbern-mgmt psql -h $(grep DB_HOST .env | cut -d '=' -f2) -U $(grep DB_USER .env | cut -d '=' -f2) -d batbern
```

## Common Issues

### 1. Services Won't Start

**Symptoms:**
- `docker-compose up` hangs or fails
- Services show as "starting" indefinitely
- Exit code 1 or other error codes

**Solutions:**

```bash
# Check for port conflicts
lsof -i :8080  # API Gateway
lsof -i :3000  # Web Frontend
lsof -i :6379  # Redis

# Verify .env file was generated
if [ ! -f .env ]; then
  echo "ERROR: .env file missing! Run: AWS_PROFILE=batbern-mgmt ./scripts/dev/setup-env.sh"
fi

# Clean up and restart
docker-compose down -v
docker-compose up -d

# Rebuild images
docker-compose build --no-cache
docker-compose up -d

# Check Docker resources
docker system df
docker system prune  # Clean up unused resources
```

### 2. "Cannot connect to AWS RDS database" Error

**Symptoms:**
- Services can't reach AWS RDS PostgreSQL
- Connection refused errors
- JDBC connection errors
- Authentication failed errors

**Solutions:**

```bash
# 1. Verify .env has correct RDS endpoint
cat .env | grep DB_HOST
# Should show: DB_HOST=batbern-development-datab-...rds.amazonaws.com

# 2. Regenerate .env from AWS if stale
AWS_PROFILE=batbern-mgmt ./scripts/dev/setup-env.sh

# 3. Test direct connection to AWS RDS
AWS_PROFILE=batbern-mgmt psql \
  -h $(grep DB_HOST .env | cut -d '=' -f2) \
  -U $(grep DB_USER .env | cut -d '=' -f2) \
  -d batbern

# 4. Check AWS credentials
aws sts get-caller-identity --profile batbern-mgmt

# 5. Verify security group allows your IP
# Go to AWS Console → RDS → batbern-development-Database
# Check security group inbound rules for port 5432

# 6. Check environment variables in container
docker-compose exec api-gateway env | grep DB_
```

**Common Causes:**
- ❌ Forgot to run `setup-env.sh` script
- ❌ AWS credentials expired or invalid
- ❌ Security group blocks connection from your IP
- ❌ Database credentials rotated in Secrets Manager
- ❌ Wrong AWS profile selected

### 3. "Cannot connect to Redis" Error

**Symptoms:**
- Redis connection errors
- Cache not working

**Solutions:**

```bash
# 1. Check Redis is running
docker-compose ps redis
docker-compose logs redis

# 2. Test Redis connection
docker exec -it batbern-redis redis-cli ping
# Should return: PONG

# 3. Verify network connectivity
docker-compose exec api-gateway ping redis

# 4. Restart Redis
docker-compose restart redis
```

### 4. AWS Cognito Authentication Errors

**Symptoms:**
- "Invalid User Pool ID" errors
- JWT token validation failures
- Authentication redirects not working
- "COGNITO_USER_POOL_ID=NOT_DEPLOYED" in .env

**Solutions:**

```bash
# 1. Verify Cognito was deployed to AWS
aws cognito-idp list-user-pools --max-results 10 --profile batbern-mgmt --region eu-central-1

# 2. Deploy Cognito stack if not deployed
cd infrastructure
AWS_PROFILE=batbern-mgmt npx cdk deploy BATbernCognitoStack \
  --app "npx ts-node bin/infrastructure.ts" \
  --require-approval never

# 3. Regenerate .env after Cognito deployment
cd ..
AWS_PROFILE=batbern-mgmt ./scripts/dev/setup-env.sh

# 4. Verify Cognito configuration in .env
cat .env | grep -E "COGNITO_USER_POOL_ID|COGNITO_CLIENT_ID"

# 5. Restart services to pick up new config
docker-compose down
docker-compose up -d
```

**Common Causes:**
- ❌ Cognito stack not deployed to AWS
- ❌ Forgot to run `setup-env.sh` after Cognito deployment
- ❌ Wrong AWS region selected
- ❌ Invalid callback URLs in Cognito configuration
```

### 5. "Port already in use" Error

**Symptoms:**
- `Error: bind: address already in use`
- Services fail to start

**Solutions:**

```bash
# 1. Find what's using the port
lsof -i :8080  # Replace with your port
netstat -an | grep 8080

# 2. Kill the process (macOS/Linux)
kill -9 $(lsof -t -i:8080)

# 3. Change port in docker-compose.yml
ports:
  - "8081:8080"  # Use different external port

# 4. Or change .env
API_GATEWAY_PORT=8081
```

### 6. Hot Reload Not Working

**Symptoms:**
- Code changes not reflected
- Need to rebuild manually

**Solutions:**

```bash
# 1. Verify volumes are mounted
docker-compose config | grep -A 10 "api-gateway:"

# 2. Check Gradle continuous build
docker-compose logs api-gateway | grep "continuous"

# 3. Restart with rebuild
docker-compose up -d --build api-gateway

# 4. For frontend, check Vite HMR
docker-compose logs web-frontend | grep "HMR"
```

### 7. "No space left on device" Error

**Symptoms:**
- Build failures
- Container start failures

**Solutions:**

```bash
# 1. Check Docker disk usage
docker system df

# 2. Clean up images and containers
docker system prune -a --volumes

# 3. Increase Docker Desktop resources
# Docker Desktop → Settings → Resources → Disk image size

# 4. Remove unused volumes
docker volume prune
```

### 8. Slow Startup Times

**Symptoms:**
- Services take minutes to start
- Health checks timing out

**Solutions:**

```bash
# 1. Increase health check intervals
healthcheck:
  interval: 30s  # Increase from 10s
  retries: 5
  start_period: 120s  # Give more time to start

# 2. Use BuildKit for faster builds
export DOCKER_BUILDKIT=1
docker-compose build

# 3. Cache dependencies
# Already configured in Dockerfile.dev

# 4. Allocate more resources to Docker
# Docker Desktop → Settings → Resources
```

### 9. AWS RDS Database Issues

**Symptoms:**
- Tables don't exist in AWS RDS
- Schema not initialized
- Test data missing

**Solutions:**

```bash
# 1. Connect to AWS RDS database directly
AWS_PROFILE=batbern-mgmt psql \
  -h $(grep DB_HOST .env | cut -d '=' -f2) \
  -U $(grep DB_USER .env | cut -d '=' -f2) \
  -d batbern

# 2. Verify tables exist
\dt

# 3. If schema needs initialization, run migrations
# (Migrations should be handled by CDK/application startup)
```

### 10. Services Can't Communicate

**Symptoms:**
- Service-to-service calls fail
- Network errors

**Solutions:**

```bash
# 1. Verify all services on same network
docker network inspect batbern_batbern-network

# 2. Test connectivity to local services
docker-compose exec api-gateway ping redis

# 3. Test connectivity to AWS services (requires AWS credentials)
# RDS connectivity tested via application logs
docker-compose logs api-gateway | grep -i "database"

# 4. Check DNS resolution for local services
docker-compose exec api-gateway nslookup redis

# 5. Verify network in docker-compose.yml
networks:
  - batbern-network  # Must be same across all services
```

## Platform-Specific Issues

### macOS

**Docker Desktop File Sharing:**
```bash
# Ensure project directory is shared
# Docker Desktop → Settings → Resources → File Sharing
# Add: /Users/{username}/dev/bat/BATbern
```

**Performance:**
```bash
# Use gRPC FUSE for better performance
# Docker Desktop → Settings → General
# Enable "Use the new Virtualization framework"
# Enable "VirtioFS"
```

### Windows (WSL2)

**Path Issues:**
```bash
# Use WSL2 paths, not Windows paths
cd /home/{username}/BATbern  # ✅ Correct
cd /mnt/c/Users/.../BATbern   # ❌ Slower

# Enable WSL2 integration
# Docker Desktop → Settings → Resources → WSL Integration
```

**Line Endings:**
```bash
# Ensure LF line endings for scripts
git config core.autocrlf input
dos2unix scripts/localstack/init-aws.sh
```

### Linux

**Permission Issues:**
```bash
# Add user to docker group
sudo usermod -aG docker $USER
newgrp docker

# Fix volume permissions
sudo chown -R $USER:$USER ./api-gateway
```

## Environment-Specific Issues

### .env File Not Loaded

```bash
# 1. Verify .env exists
ls -la .env

# 2. Copy from template
cp .env.example .env

# 3. Restart docker-compose
docker-compose down
docker-compose up -d

# 4. Verify variables loaded
docker-compose config | grep POSTGRES
```

### Wrong Environment Variables

```bash
# View resolved configuration
docker-compose config

# Check specific service environment
docker-compose exec api-gateway printenv | grep DB_
```

## Debugging Commands

### Interactive Shell Access

```bash
# Redis (local container)
docker exec -it batbern-redis redis-cli

# API Gateway
docker exec -it batbern-api-gateway /bin/bash

# Web Frontend
docker exec -it batbern-frontend /bin/sh

# AWS RDS PostgreSQL (requires AWS credentials and network access)
AWS_PROFILE=batbern-mgmt psql \
  -h $(grep DB_HOST .env | cut -d '=' -f2) \
  -U $(grep DB_USER .env | cut -d '=' -f2) \
  -d batbern
```

### Network Debugging

```bash
# List all networks
docker network ls

# Inspect batbern network
docker network inspect batbern_batbern-network

# Test connectivity between local containers
docker run --network batbern_batbern-network --rm alpine ping redis
```

### Volume Management

```bash
# List volumes
docker volume ls | grep batbern

# Inspect volume
docker volume inspect batbern_postgres-data

# Remove all volumes (⚠️ DELETES DATA)
docker-compose down -v
```

## Clean Slate Restart

When all else fails, start fresh:

```bash
# 1. Stop everything
docker-compose down -v

# 2. Remove all BATbern containers
docker ps -a | grep batbern | awk '{print $1}' | xargs docker rm -f

# 3. Remove all images
docker images | grep batbern | awk '{print $3}' | xargs docker rmi -f

# 4. Prune system
docker system prune -a --volumes

# 5. Start fresh
docker-compose build --no-cache
docker-compose up -d
```

## Getting Help

If you're still stuck:

1. **Check logs with timestamps:**
   ```bash
   docker-compose logs --timestamps | grep ERROR
   ```

2. **Create debug info package:**
   ```bash
   docker-compose config > debug-compose-config.yml
   docker-compose ps > debug-services.txt
   docker-compose logs > debug-logs.txt
   ```

3. **Report issue with:**
   - Docker version: `docker --version`
   - Docker Compose version: `docker-compose --version`
   - OS/Platform
   - Full error message
   - Steps to reproduce

## Useful Resources

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [LocalStack Documentation](https://docs.localstack.cloud/)
- [BATbern Service Template](./DOCKER-COMPOSE-SERVICE-TEMPLATE.md)
- [Project README](../README.md)
