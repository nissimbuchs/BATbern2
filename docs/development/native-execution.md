# Native Development Execution Guide

## Overview

The BATbern platform supports two local development modes:

1. **Docker Compose** - Full containerization (default)
2. **Native Execution** - Direct process execution (60-70% less resources)

This guide covers **Native Execution**, a lightweight alternative to Docker Compose that runs all services directly on your laptop without containerization overhead.

## Why Native Execution?

### Benefits

- **60-70% Less Memory** - No Docker daemon or container overhead (~4-6GB savings)
- **Faster Startup** - 20-30 seconds vs 60-90 seconds with Docker
- **Better Hot Reload** - Direct filesystem access for instant code changes
- **Easier Debugging** - Attach debuggers directly to processes
- **Native Performance** - No virtualization overhead

### When to Use

✅ **Use Native Execution for:**
- Daily development work
- Rapid iteration on code changes
- Debugging specific services
- Resource-constrained laptops (< 32GB RAM)

⚠️ **Use Docker for:**
- Testing container-specific behavior
- Verifying deployment configurations
- Pre-PR integration testing
- Debugging containerization issues

## Prerequisites

### Required Software

- **Java 21 LTS** - [Download from Adoptium](https://adoptium.net/)
- **Node.js 20+** - [Download from nodejs.org](https://nodejs.org/)
- **Gradle 8.5+** - Usually bundled via `./gradlew`
- **npm 10+** - Bundled with Node.js
- **AWS CLI v2** - [Installation Guide](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)

### AWS Configuration

You must have AWS credentials configured for the development environment:

```bash
aws configure --profile batbern-dev
# Enter your AWS Access Key ID
# Enter your AWS Secret Access Key
# Default region: eu-central-1
# Default output format: json
```

### Environment Sync

Before starting services, sync your `.env` file from AWS:

```bash
./scripts/config/sync-backend-config.sh development
```

This fetches:
- Database credentials (from AWS Secrets Manager)
- Cognito configuration (User Pool ID, Client ID)
- RDS endpoint information
- EventBridge configuration

## Quick Start

### Start All Services

```bash
make dev-native-up
```

This will:
1. Check prerequisites (Java, Node.js, etc.)
2. Create `.env.native` with localhost overrides
3. Start database tunnel to AWS RDS
4. Start all 6 backend services + frontend
5. Wait for health checks

**Services will be available at:**
- API Gateway: http://localhost:8080
- Company User Management: http://localhost:8081
- Event Management: http://localhost:8082
- Speaker Coordination: http://localhost:8083
- Partner Coordination: http://localhost:8084
- Attendee Experience: http://localhost:8085
- Web Frontend: http://localhost:3000

### Check Status

```bash
make dev-native-status
```

Shows:
- Running/stopped status for each service
- Process IDs (PIDs)
- Health check status
- Memory usage per service

### View Logs

```bash
# All services
make dev-native-logs

# Specific service
./scripts/dev/logs-native.sh api-gateway

# Last 100 lines
./scripts/dev/logs-native.sh api-gateway -n 100
```

### Restart Services

```bash
# Restart all
make dev-native-restart

# Restart specific service
./scripts/dev/restart-native.sh api-gateway
```

### Stop Services

```bash
# Stop all services (keeps DB tunnel running)
make dev-native-down

# Stop all including DB tunnel
./scripts/dev/stop-all-native.sh
```

## How It Works

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Your Laptop (Native)                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │  Frontend    │  │ API Gateway  │  │  Services    │    │
│  │  (npm dev)   │  │  (bootRun)   │  │  (bootRun)   │    │
│  │  Port: 3000  │  │  Port: 8080  │  │  Ports:      │    │
│  │              │  │              │  │  8081-8085   │    │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘    │
│         │                 │                 │              │
│         └─────────────────┴─────────────────┘              │
│                           │                                │
│                    localhost (HTTP)                        │
│                                                             │
└─────────────────────────┬───────────────────────────────────┘
                          │
                  ┌───────┴────────┐
                  │  DB Tunnel     │
                  │  (SSM Session) │
                  │  Port: 5432    │
                  └───────┬────────┘
                          │
                          ▼
                  ┌───────────────┐
                  │  AWS Cloud    │
                  │  RDS, Cognito │
                  │  EventBridge  │
                  └───────────────┘
```

### Environment Configuration

The startup script creates `.env.native` which:
1. Sources the main `.env` file (synced from AWS)
2. Overrides `DB_HOST` from `host.docker.internal` to `localhost`
3. Sets microservice URLs to `http://localhost:808X`

**Key Differences from Docker:**

| Configuration | Docker | Native |
|--------------|--------|--------|
| DB_HOST | `host.docker.internal` | `localhost` |
| Service URLs | `http://service-name:8080` | `http://localhost:808X` |
| Networking | Docker bridge network | Localhost loopback |
| Isolation | Container isolation | Process isolation |

### Process Management

Services run as background processes:
- **PIDs stored in:** `/tmp/batbern-dev-{service}.pid`
- **Logs stored in:** `/tmp/batbern-dev-{service}.log`
- **Graceful shutdown:** SIGTERM with 15-second timeout
- **Force kill:** SIGKILL if graceful shutdown fails

### Database Tunnel

The AWS SSM Session Manager tunnel:
- Connects via bastion host in AWS VPC
- Forwards `localhost:5432` → AWS RDS PostgreSQL
- Runs in background (PID tracked in `/tmp/batbern-dev-db-tunnel.pid`)
- Auto-starts bastion instance if stopped

## Common Workflows

### Daily Development

```bash
# Morning: Start services
make dev-native-up

# Work on code (hot reload happens automatically)
# ... make changes to services ...

# Check if everything is healthy
make dev-native-status

# View logs if something breaks
make dev-native-logs

# Evening: Stop services
make dev-native-down
```

### Working on Specific Service

```bash
# Start all services
make dev-native-up

# Make changes to api-gateway
# ... edit code ...

# Restart just that service
./scripts/dev/restart-native.sh api-gateway

# Watch its logs
./scripts/dev/logs-native.sh api-gateway
```

### Debugging a Service

```bash
# Start all services
make dev-native-up

# Stop the service you want to debug
./scripts/dev/stop-all-native.sh api-gateway

# Start it from your IDE with debugger attached
# (IntelliJ: Run → Debug 'api-gateway:bootRun')

# Other services continue running and talking to your debugged service
```

### Switching Between Docker and Native

```bash
# Use native for development
make dev-native-up
# ... do work ...
make dev-native-down

# Use Docker for pre-PR testing
make docker-up
# ... run integration tests ...
make docker-down

# Back to native for next feature
make dev-native-up
```

## Troubleshooting

### Services Won't Start

**Symptom:** Service fails health check or crashes immediately

**Solutions:**
1. Check if ports are already in use:
   ```bash
   lsof -i :8080  # Check specific port
   ```

2. View service logs:
   ```bash
   ./scripts/dev/logs-native.sh api-gateway -n 100
   ```

3. Verify `.env` is synced:
   ```bash
   ./scripts/config/sync-backend-config.sh development
   ```

4. Check Java version:
   ```bash
   java -version  # Should be 21+
   ```

### Database Connection Issues

**Symptom:** Services fail with database connection errors

**Solutions:**
1. Check if DB tunnel is running:
   ```bash
   pgrep -f "AWS-StartPortForwardingSessionToRemoteHost"
   ```

2. Restart DB tunnel:
   ```bash
   pkill -f "AWS-StartPortForwardingSessionToRemoteHost"
   make dev-native-up
   ```

3. Test database connectivity:
   ```bash
   psql -h localhost -p 5432 -U postgres -d batbern
   ```

4. Check AWS credentials:
   ```bash
   aws sts get-caller-identity --profile batbern-dev
   ```

### Port Already in Use

**Symptom:** Service fails to start with "Address already in use" error

**Solutions:**
1. Find process using the port:
   ```bash
   lsof -i :8080
   ```

2. Kill the process:
   ```bash
   kill -9 <PID>
   ```

3. Or use Docker if ports conflict with native services:
   ```bash
   make dev-native-down
   # Wait a few seconds
   make dev-native-up
   ```

### Out of Memory

**Symptom:** Services become slow or crash with OutOfMemoryError

**Solutions:**
1. Check memory usage:
   ```bash
   make dev-native-status  # Shows memory per service
   ```

2. Stop unused services:
   ```bash
   ./scripts/dev/stop-all-native.sh speaker-coordination
   ```

3. Increase Java heap size (if needed):
   ```bash
   # Edit .env.native, add:
   JAVA_OPTS="-Xmx2g"
   ```

### Gradle Daemon Conflicts

**Symptom:** Build errors or services won't start

**Solutions:**
1. Stop all Gradle daemons:
   ```bash
   ./gradlew --stop
   ```

2. Restart services:
   ```bash
   make dev-native-restart
   ```

## Performance Tuning

### Reduce Memory Usage

**Option 1: Run only needed services**
```bash
# Start all services
make dev-native-up

# Stop services you don't need
./scripts/dev/stop-all-native.sh partner-coordination
./scripts/dev/stop-all-native.sh attendee-experience
```

**Option 2: Adjust JVM heap sizes**
Edit `.env.native`:
```bash
# Reduce heap size for all services (default is auto)
JAVA_OPTS="-Xmx512m -Xms256m"
```

### Speed Up Startup

**Option 1: Keep DB tunnel running**
```bash
# Stop services but keep tunnel
./scripts/dev/stop-all-native.sh --keep-tunnel

# Next startup is faster (tunnel already connected)
make dev-native-up
```

**Option 2: Parallel service startup**
The startup script already starts backend services in parallel. If you need more control, edit `scripts/dev/start-all-native.sh`.

## Advanced Usage

### Custom Environment Variables

Add service-specific overrides to `.env.native`:

```bash
# Edit .env.native
LOG_LEVEL=TRACE  # More verbose logging
SPRING_PROFILES_ACTIVE=local,debug
```

### Running Services in Foreground

For debugging, run a service in foreground:

```bash
# Stop the background service
./scripts/dev/stop-all-native.sh api-gateway

# Run in foreground
source .env.native
./gradlew :api-gateway:bootRun
```

### Using Different Database

To use a local PostgreSQL instead of AWS RDS:

```bash
# Edit .env.native
DB_HOST=localhost
DB_PORT=5432
DB_NAME=batbern_local
DB_USER=localuser
DB_PASSWORD=localpass
DATABASE_URL=jdbc:postgresql://localhost:5432/batbern_local?user=localuser&password=localpass

# Don't start DB tunnel
# Comment out the DB tunnel check in scripts/dev/start-all-native.sh
```

## Comparison: Native vs Docker

| Feature | Native Execution | Docker Compose |
|---------|-----------------|----------------|
| **Startup Time** | 20-30 seconds | 60-90 seconds |
| **Memory Usage** | ~6-8GB | ~12-14GB |
| **CPU Overhead** | Minimal | Moderate (daemon) |
| **Hot Reload** | Instant | 2-5 seconds |
| **Debugging** | Direct attach | Port forwarding |
| **Isolation** | Process-level | Container-level |
| **Staging Parity** | Good (same code) | Excellent (same containers) |
| **Network Testing** | Localhost only | Bridge network |
| **Port Conflicts** | Easier to debug | Isolated |

## Best Practices

1. **Sync Environment Daily**
   ```bash
   # Before starting work
   ./scripts/config/sync-backend-config.sh development
   make dev-native-up
   ```

2. **Monitor Service Health**
   ```bash
   # Periodically check status
   make dev-native-status
   ```

3. **Check Logs When Issues Occur**
   ```bash
   # Don't guess, check the logs
   make dev-native-logs
   ```

4. **Clean Restart When in Doubt**
   ```bash
   make dev-native-down
   ./gradlew --stop  # Stop Gradle daemons
   make dev-native-up
   ```

5. **Use Docker for Final PR Testing**
   ```bash
   # Before creating PR
   make dev-native-down
   make docker-up
   # Run full test suite
   make test
   ```

## FAQ

**Q: Can I use native execution and Docker at the same time?**

A: No, they use the same ports. Stop one before starting the other.

**Q: Will native execution work on Windows?**

A: The scripts are written for macOS/Linux (bash). Windows users should use WSL2 or Docker.

**Q: How do I know if a service is using too much memory?**

A: Run `make dev-native-status` to see memory usage per service.

**Q: Can I run just one service natively and the rest in Docker?**

A: Not easily. It's better to run all natively or all in Docker.

**Q: What happens if my laptop goes to sleep?**

A: Services and DB tunnel may need to be restarted:
```bash
make dev-native-down
make dev-native-up
```

**Q: How do I know which service is causing errors?**

A: Check logs for each service:
```bash
make dev-native-logs
# or
./scripts/dev/logs-native.sh api-gateway
```

## Getting Help

If you encounter issues:

1. Check service status: `make dev-native-status`
2. View logs: `make dev-native-logs`
3. Verify prerequisites: Script will check automatically on startup
4. Restart services: `make dev-native-restart`
5. Ask on Slack: #dev-infrastructure channel

## See Also

- [Local Development Setup](./local-setup.md)
- [Docker Development Setup](./docker-setup.md)
- [AWS Configuration](../architecture/02-infrastructure-deployment.md)
- [Troubleshooting Guide](./troubleshooting.md)
