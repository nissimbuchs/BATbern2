#!/bin/bash
# Start all BATbern services natively (without Docker)
# This script provides a lightweight alternative to Docker Compose for local development
#
# Usage:
#   ./scripts/dev/start-all-native.sh              # Default instance (BASE_PORT=8000)
#   BASE_PORT=9000 ./scripts/dev/start-all-native.sh  # Second instance (BASE_PORT=9000)
#
# Parallel Instance Support:
#   - Instance 1: BASE_PORT=8000 (default) → services 8080-8085, frontend 3000, DB tunnel 5432, MinIO 9000-9001
#   - Instance 2: BASE_PORT=9000 → services 9080-9085, frontend 4000, DB tunnel 6432, MinIO 9100-9101
#   - Both instances share the same database but have isolated processes
#
# Services started BY DEFAULT (with default ports):
#   - API Gateway (port 8080)
#   - Company User Management Service (port 8081)
#   - Event Management Service (port 8082)
#   - Partner Coordination Service (port 8084)
#   - Web Frontend (port 3000)
#
# All six services start by default (see DEV_SERVICES in lib/native-services.sh). The
# 4-service default that ran 2026-08-08..2026-08-14 existed only for the 6 GB NAS dev
# host, which is being decommissioned. To run a subset on a small machine, name it:
#   DEV_SERVICES="api-gateway company-user-management event-management" make dev-native-up
#
# Memory tuning (added 2026-08-08 for the 6 GB NAS dev host; kept — it also helps laptops
# and is what keeps six JVMs affordable):
#   - Each service runs as `java -Xmx… -jar <fat jar>` instead of `./gradlew bootRun`.
#     bootRun cost a Gradle launcher JVM *per service* on top of the app JVM.
#     Set DEV_RUN_MODE=bootrun to get the old behaviour back (needed for DevTools hot reload).
#   - Per-service heap caps, biggest where the data is: see service_heap() below.
#   - JARs are built in ONE Gradle invocation before any service starts, instead of N
#     concurrent `--no-daemon` builds, which was the real memory peak.

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PID_DIR="/tmp"
LOG_DIR="/tmp"
ENV_FILE="${PROJECT_ROOT}/.env"

# Service selection, per-service heaps, module paths — shared with status-native.sh.
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/native-services.sh"

# jar     = java -jar the fat JAR (default; one JVM per service)
# bootrun = ./gradlew bootRun (Spring DevTools hot reload; adds a Gradle launcher JVM per service)
DEV_RUN_MODE="${DEV_RUN_MODE:-jar}"

# Instance-specific configuration
BASE_PORT="${BASE_PORT:-8000}"  # Default to 8000 (instance 1)

# Calculate instance identifier based on BASE_PORT
# Use friendly names for common ports, otherwise use the port number itself
if [ "$BASE_PORT" -eq 8000 ]; then
    INSTANCE="1"
elif [ "$BASE_PORT" -eq 9000 ]; then
    INSTANCE="2"
else
    INSTANCE="$BASE_PORT"
fi

ENV_NATIVE_FILE="${PROJECT_ROOT}/.env.native.${INSTANCE}"

# Calculate ports - consistent for all instances
# Pattern: BASE_PORT for API Gateway, BASE_PORT+1..5 for services, BASE_PORT+100 for frontend
API_GATEWAY_PORT=$BASE_PORT
COMPANY_USER_MGMT_PORT=$((BASE_PORT + 1))
EVENT_MGMT_PORT=$((BASE_PORT + 2))
SPEAKER_COORD_PORT=$((BASE_PORT + 3))
PARTNER_COORD_PORT=$((BASE_PORT + 4))
ATTENDEE_EXP_PORT=$((BASE_PORT + 5))
FRONTEND_PORT=$((BASE_PORT + 100))

# Shared infrastructure ports (all instances use the same DB tunnel and MinIO)
# DB tunnel port - 5432 by default, and shared across all instances by design.
# Overridable because some hosts already have something on 5432 (e.g. a Synology NAS
# runs DSM's own PostgreSQL on 127.0.0.1:5432). Must match the host port that
# docker-compose-dev.yml publishes, i.e. POSTGRES_PORT.
DB_TUNNEL_PORT="${DB_TUNNEL_PORT:-5432}"

# MinIO ports - always 8450/8451 (shared across all instances)
# Using 8450/8451 to avoid conflicts with common application ports like 9000
MINIO_API_PORT=8450
MINIO_CONSOLE_PORT=8451

# Banner
echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     BATbern Platform - Native Development Startup         ║${NC}"
echo -e "${BLUE}║              Instance ${INSTANCE} (BASE_PORT=${BASE_PORT})                  ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check prerequisites
check_prerequisites() {
    echo -e "${CYAN}→ Checking prerequisites...${NC}"

    local errors=0

    # Check Java
    if ! command -v java &> /dev/null; then
        echo -e "${RED}  ✗ Java not found${NC}"
        echo "    Install: https://adoptium.net/ (Java 21 required)"
        errors=$((errors + 1))
    else
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | cut -d'.' -f1)
        if [ "$JAVA_VERSION" -lt 21 ]; then
            echo -e "${RED}  ✗ Java version $JAVA_VERSION found, but Java 21+ required${NC}"
            errors=$((errors + 1))
        else
            echo -e "${GREEN}  ✓ Java $JAVA_VERSION${NC}"
        fi
    fi

    # Check Node.js
    if ! command -v node &> /dev/null; then
        echo -e "${RED}  ✗ Node.js not found${NC}"
        echo "    Install: https://nodejs.org/ (Node.js 20+ required)"
        errors=$((errors + 1))
    else
        NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
        if [ "$NODE_VERSION" -lt 20 ]; then
            echo -e "${RED}  ✗ Node.js version $NODE_VERSION found, but Node.js 20+ required${NC}"
            errors=$((errors + 1))
        else
            echo -e "${GREEN}  ✓ Node.js $(node -v)${NC}"
        fi
    fi

    # Check Gradle
    if ! command -v gradle &> /dev/null; then
        echo -e "${YELLOW}  ⚠ Gradle not found (will use ./gradlew)${NC}"
    else
        echo -e "${GREEN}  ✓ Gradle $(gradle -v | grep Gradle | awk '{print $2}')${NC}"
    fi

    # Check npm
    if ! command -v npm &> /dev/null; then
        echo -e "${RED}  ✗ npm not found${NC}"
        errors=$((errors + 1))
    else
        echo -e "${GREEN}  ✓ npm $(npm -v)${NC}"
    fi

    # Check .env file
    if [ ! -f "${ENV_FILE}" ]; then
        echo -e "${RED}  ✗ .env file not found${NC}"
        echo "    Run: ./scripts/config/sync-backend-config.sh development"
        errors=$((errors + 1))
    else
        echo -e "${GREEN}  ✓ .env file exists${NC}"
    fi

    echo ""

    if [ $errors -gt 0 ]; then
        echo -e "${RED}Prerequisites check failed. Please fix the errors above.${NC}"
        exit 1
    fi
}

# Create .env.native with localhost overrides
create_env_native() {
    echo -e "${CYAN}→ Creating instance-specific environment file...${NC}"

    # Source the original .env to get all values
    set -a
    source "${ENV_FILE}"
    set +a

    # Compute AWS profile for native services.
    # Local dev always targets staging Cognito for admin SDK calls.
    # Reads AWS_PROFILE from .env if explicitly set; ignores the parent shell's
    # AWS_PROFILE (which may point to a different account, e.g. batbern-dev).
    NATIVE_AWS_PROFILE=$(grep "^AWS_PROFILE=" "${ENV_FILE}" 2>/dev/null | head -1 | cut -d= -f2 | tr -d '"' | tr -d "'" | xargs)
    NATIVE_AWS_PROFILE="${NATIVE_AWS_PROFILE:-batbern-staging}"

    # Override ports with instance-specific calculated values
    # (These override any values from the base .env file)
    API_GATEWAY_PORT=$BASE_PORT
    COMPANY_USER_MGMT_PORT=$((BASE_PORT + 1))
    EVENT_MGMT_PORT=$((BASE_PORT + 2))
    SPEAKER_COORD_PORT=$((BASE_PORT + 3))
    PARTNER_COORD_PORT=$((BASE_PORT + 4))
    ATTENDEE_EXP_PORT=$((BASE_PORT + 5))
    FRONTEND_PORT=$((BASE_PORT + 100))

    # Create .env.native.{INSTANCE} with localhost overrides and instance-specific ports
    cat > "${ENV_NATIVE_FILE}" << EOF
# ==============================================
# BATbern Native Development Environment
# Instance ${INSTANCE} (BASE_PORT=${BASE_PORT})
# ==============================================
# Auto-generated by scripts/dev/start-all-native.sh
# Generated: $(date)
#
# This file overrides .env for native (non-Docker) execution
# ==============================================

# Source the main .env file (auto-synced from AWS)
# Then override specific values for native execution

# ==============================================
# Instance Configuration
# ==============================================
INSTANCE=${INSTANCE}
BASE_PORT=${BASE_PORT}

# ==============================================
# Database Configuration (Native Execution)
# ==============================================
# Override: Use local PostgreSQL (docker-compose-dev.yml credentials)
# NOTE: These match the credentials in docker-compose-dev.yml, NOT AWS RDS
DB_HOST=localhost
DB_PORT=${DB_TUNNEL_PORT}
DB_NAME=batbern_development
DB_USER=postgres
DB_PASSWORD=devpass123
DATABASE_URL="jdbc:postgresql://localhost:${DB_TUNNEL_PORT}/batbern_development"
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=devpass123

# ==============================================
# Application Configuration
# ==============================================
# Force local development settings (ignore values from .env)
APP_ENVIRONMENT=development
SPRING_PROFILES_ACTIVE=local
LOG_LEVEL=DEBUG
# Override API base URL for local development
API_BASE_URL=http://localhost:${API_GATEWAY_PORT}

# ==============================================
# AWS Configuration
# ==============================================
AWS_REGION=${AWS_REGION:-eu-central-1}
AWS_ACCOUNT_ID=${AWS_ACCOUNT_ID}
AWS_PROFILE=${NATIVE_AWS_PROFILE}

# ==============================================
# AWS Cognito Configuration
# ==============================================
COGNITO_USER_POOL_ID=${COGNITO_USER_POOL_ID}
COGNITO_CLIENT_ID=${COGNITO_CLIENT_ID}
COGNITO_DOMAIN_URL=${COGNITO_DOMAIN_URL}

# ==============================================
# Feature Flags
# ==============================================
ENABLE_COGNITO_AUTH=${ENABLE_COGNITO_AUTH:-true}

# ==============================================
# AI / OpenAI Configuration (Story 10.16)
# ==============================================
AI_ENABLED=${AI_ENABLED:-false}
OPENAI_API_KEY=${OPENAI_API_KEY:-}
# Local dev: CloudFront is MinIO. Domain is the MinIO host only — CloudFrontUrlBuilder
# appends the bucket for localhost (path-style access). Baking the bucket into the
# domain would double-include it for every consumer that uses the builder.
CLOUDFRONT_DOMAIN=http://localhost:${MINIO_API_PORT}

# ==============================================
# Service Ports (Instance ${INSTANCE})
# ==============================================
API_GATEWAY_PORT=${API_GATEWAY_PORT}

# ==============================================
# Microservice URLs (Instance ${INSTANCE})
# ==============================================
# Services communicate via localhost with instance-specific ports
COMPANY_USER_MANAGEMENT_SERVICE_URL="http://localhost:${COMPANY_USER_MGMT_PORT}"
EVENT_MANAGEMENT_SERVICE_URL="http://localhost:${EVENT_MGMT_PORT}"
SPEAKER_COORDINATION_SERVICE_URL="http://localhost:${SPEAKER_COORD_PORT}"
PARTNER_COORDINATION_SERVICE_URL="http://localhost:${PARTNER_COORD_PORT}"
ATTENDEE_EXPERIENCE_SERVICE_URL="http://localhost:${ATTENDEE_EXP_PORT}"

# ==============================================
# EventBridge Configuration
# ==============================================
EVENT_BUS_NAME=${EVENT_BUS_NAME:-batbern-development}

# ==============================================
# MinIO Configuration (Instance ${INSTANCE})
# ==============================================
# MinIO provides S3-compatible API for local development
MINIO_ENDPOINT=http://localhost:${MINIO_API_PORT}
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=batbern-development-company-logos
# Override S3 configuration for local development
AWS_S3_ENDPOINT=http://localhost:${MINIO_API_PORT}
AWS_S3_PATH_STYLE_ACCESS=true
AWS_S3_BUCKET_NAME=batbern-development-company-logos

# ==============================================
# Application Base URL (for email links, etc.)
# ==============================================
APP_BASE_URL=http://localhost:${FRONTEND_PORT}

# ==============================================
# JWT Secret (for confirmation tokens)
# ==============================================
JWT_SECRET=${JWT_SECRET:-dev-secret-change-in-production-use-openssl-rand-base64-32}

# ==============================================
# Frontend Configuration (Instance ${INSTANCE})
# ==============================================
VITE_API_BASE_URL=http://localhost:${API_GATEWAY_PORT}
VITE_API_PORT=${API_GATEWAY_PORT}
VITE_PORT=${FRONTEND_PORT}
EOF

    echo -e "${GREEN}  ✓ Created ${ENV_NATIVE_FILE}${NC}"
    echo ""
}

# Check if local PostgreSQL is running
check_local_postgres() {
    echo -e "${CYAN}→ Checking local PostgreSQL (port 5432)...${NC}"

    # Check if Docker is running
    if ! docker info > /dev/null 2>&1; then
        echo -e "${RED}  ✗ Docker is not running${NC}"
        echo "    Start Docker Desktop and try again"
        exit 1
    fi

    # Check if PostgreSQL container exists and is running
    local container_status=$(docker ps -a --filter "name=batbern-dev-postgres" --format "{{.Status}}" 2>/dev/null)

    if [[ $container_status == Up* ]]; then
        # Container is running, check if it's healthy
        local health_status=$(docker inspect --format='{{.State.Health.Status}}' batbern-dev-postgres 2>/dev/null || echo "none")

        if [ "$health_status" == "healthy" ]; then
            echo -e "${GREEN}  ✓ Local PostgreSQL is running and healthy${NC}"
            echo ""
            return 0
        elif [ "$health_status" == "starting" ]; then
            echo -e "${YELLOW}  ⏳ PostgreSQL is starting, waiting for health check...${NC}"
            sleep 5
            health_status=$(docker inspect --format='{{.State.Health.Status}}' batbern-dev-postgres 2>/dev/null || echo "none")
            if [ "$health_status" == "healthy" ]; then
                echo -e "${GREEN}  ✓ Local PostgreSQL is running and healthy${NC}"
                echo ""
                return 0
            fi
        else
            echo -e "${GREEN}  ✓ Local PostgreSQL is running (no health check)${NC}"
            echo ""
            return 0
        fi
    fi

    # Container not running, start it
    echo -e "${YELLOW}  ⚠ Local PostgreSQL not running${NC}"

    # Check if container exists but is stopped
    if [ -n "$container_status" ]; then
        # Container exists but is stopped - just start it
        echo -e "${CYAN}  → Starting existing PostgreSQL container...${NC}"
        docker start batbern-dev-postgres > /dev/null 2>&1
        if [ $? -ne 0 ]; then
            echo -e "${RED}  ✗ Failed to start PostgreSQL container${NC}"
            exit 1
        fi
    else
        # Container doesn't exist - create and start with docker compose
        echo -e "${CYAN}  → Creating PostgreSQL container with Docker Compose...${NC}"
        cd "${PROJECT_ROOT}"
        COMPOSE=$(compose_cmd)
        if [ -z "$COMPOSE" ]; then
            echo -e "${RED}  ✗ No Docker Compose found (looked for 'docker compose' and 'docker-compose')${NC}"
            exit 1
        fi
        $COMPOSE -f docker-compose-dev.yml up -d 2>&1 | sed 's/^/    /'
        if [ $? -ne 0 ]; then
            echo -e "${RED}  ✗ Failed to start PostgreSQL container${NC}"
            exit 1
        fi
    fi

    echo -e "${CYAN}  → Waiting for PostgreSQL to be ready (10 seconds)...${NC}"
    sleep 10

    # Verify it's healthy
    if docker exec batbern-dev-postgres pg_isready -U postgres > /dev/null 2>&1; then
        echo -e "${GREEN}  ✓ Local PostgreSQL started successfully${NC}"
    else
        echo -e "${RED}  ✗ PostgreSQL is running but not accepting connections${NC}"
        echo "    Check logs: docker logs batbern-dev-postgres"
        exit 1
    fi
    echo ""
}

# Check if MinIO is running
check_minio() {
    # Shared MinIO - use "shared" prefix instead of instance-specific
    local pid_file="${PID_DIR}/batbern-shared-minio.pid"

    echo -e "${CYAN}→ Checking MinIO (local S3, ports ${MINIO_API_PORT}/${MINIO_CONSOLE_PORT})...${NC}"

    # Check if shared MinIO is already running
    if [ -f "$pid_file" ]; then
        local old_pid=$(cat "$pid_file")
        if ps -p $old_pid > /dev/null 2>&1; then
            echo -e "${GREEN}  ✓ MinIO is running (PID: $old_pid, Ports: ${MINIO_API_PORT}/${MINIO_CONSOLE_PORT})${NC}"
            echo ""
            return 0
        else
            rm -f "$pid_file"
        fi
    fi

    echo -e "${YELLOW}  ⚠ MinIO not running (shared across all instances)${NC}"
    echo -e "${CYAN}  → Starting MinIO on ports ${MINIO_API_PORT}/${MINIO_CONSOLE_PORT}...${NC}"

    MINIO_API_PORT=${MINIO_API_PORT} MINIO_CONSOLE_PORT=${MINIO_CONSOLE_PORT} INSTANCE=${INSTANCE} "${PROJECT_ROOT}/scripts/dev/start-minio.sh"

    echo ""
}

# Wait for service health check
wait_for_health() {
    local service_name=$1
    local port=$2
    local max_attempts=${3:-30}
    local attempt=1

    echo -e "${CYAN}    Waiting for ${service_name} health check...${NC}"

    while [ $attempt -le $max_attempts ]; do
        if curl -sf "http://localhost:${port}/actuator/health" > /dev/null 2>&1; then
            echo -e "${GREEN}    ✓ ${service_name} is healthy${NC}"
            return 0
        fi

        echo -ne "${YELLOW}    ⏳ Attempt ${attempt}/${max_attempts}...\r${NC}"
        sleep 2
        attempt=$((attempt + 1))
    done

    echo -e "${RED}    ✗ ${service_name} health check timeout${NC}"
    return 1
}

# Start a Spring Boot service
start_spring_service() {
    local service_name=$1
    local gradle_task="$(service_module "$service_name"):bootRun"
    local port="$(service_port "$service_name")"
    local heap="$(service_heap "$service_name")"
    local pid_file="${PID_DIR}/batbern-${INSTANCE}-${service_name}.pid"
    local log_file="${LOG_DIR}/batbern-${INSTANCE}-${service_name}.log"

    # Check if already running
    if [ -f "$pid_file" ]; then
        local old_pid=$(cat "$pid_file")
        if ps -p $old_pid > /dev/null 2>&1; then
            echo -e "${YELLOW}  ⚠ ${service_name} already running (PID: $old_pid)${NC}"
            return 0
        else
            rm -f "$pid_file"
        fi
    fi

    # NOTE: the JAR is built up-front by build_jars() in a single Gradle invocation.
    # It used to be built here, which meant N concurrent `--no-daemon` Gradle builds —
    # the peak memory moment of the whole script.

    # Find the JAR file (exclude -plain.jar, get the executable fat JAR)
    local jar_path=""
    if [[ "$gradle_task" == ":api-gateway:"* ]]; then
        jar_path=$(find "${PROJECT_ROOT}/api-gateway/build/libs" -name "*.jar" -type f ! -name "*-plain.jar" | head -1)
    else
        # Extract service directory from gradle task (e.g., :services:company-user-management-service:bootRun)
        local service_dir=$(echo "$gradle_task" | sed 's/:bootRun$//' | sed 's/^://' | sed 's/:/\//g')
        jar_path=$(find "${PROJECT_ROOT}/${service_dir}/build/libs" -name "*.jar" -type f ! -name "*-plain.jar" | head -1)
    fi

    if [ ! -f "$jar_path" ]; then
        echo -e "${RED}    ✗ JAR file not found for ${service_name}${NC}"
        echo -e "${YELLOW}    Expected in: ${service_dir}/build/libs/${NC}"
        return 1
    fi

    local heap_note="heap ${heap}"
    if [ "$DEV_RUN_MODE" = "bootrun" ]; then heap_note="heap uncapped (bootrun)"; fi
    echo -e "${CYAN}  → Starting ${service_name} (${heap_note}, mode ${DEV_RUN_MODE})...${NC}"
    echo -e "${CYAN}    JAR: $(basename $jar_path)${NC}"

    # Export environment: base .env first, then instance overrides
    set -a
    [ -f "${ENV_FILE}" ] && source "${ENV_FILE}"
    source "${ENV_NATIVE_FILE}"
    set +a

    cd "${PROJECT_ROOT}"
    local pid
    if [ "$DEV_RUN_MODE" = "bootrun" ]; then
        # Gradle bootRun: keeps Spring DevTools hot reload, at the cost of an extra
        # Gradle launcher JVM per service.
        # NOTE: the per-service heap cap does NOT apply in this mode — no build.gradle
        # reads a jvmArgs property, and JAVA_TOOL_OPTIONS would also cap Gradle's own
        # JVMs. Use this mode on a machine with RAM to spare.
        local gradle_module=$(echo "$gradle_task" | sed 's/:bootRun$//')
        nohup ./gradlew ${gradle_module}:bootRun \
            --args="--server.port=$port" > "$log_file" 2>&1 &
        pid=$!
    else
        # Default: run the fat JAR directly. One JVM per service, explicit heap, no Gradle.
        nohup java -Xmx"${heap}" -XX:MaxMetaspaceSize=192m \
            -jar "$jar_path" --server.port=$port > "$log_file" 2>&1 &
        pid=$!
    fi
    echo $pid > "$pid_file"
    disown  # Remove from job control so parent script can exit

    echo -e "${GREEN}    ✓ Started ${service_name} (PID: $pid, Port: $port)${NC}"
    echo -e "${CYAN}    📝 Logs: ${log_file}${NC}"

    # Wait for health check
    if ! wait_for_health "$service_name" "$port" 60; then
        echo -e "${RED}    ✗ ${service_name} failed to become healthy${NC}"
        echo -e "${YELLOW}    Last 20 lines of log:${NC}"
        tail -20 "$log_file"
        return 1
    fi

    echo ""
}

# Start frontend service
start_frontend() {
    local service_name="web-frontend"
    local port=${FRONTEND_PORT}
    local pid_file="${PID_DIR}/batbern-${INSTANCE}-${service_name}.pid"
    local log_file="${LOG_DIR}/batbern-${INSTANCE}-${service_name}.log"

    # Check if already running
    if [ -f "$pid_file" ]; then
        local old_pid=$(cat "$pid_file")
        if ps -p $old_pid > /dev/null 2>&1; then
            echo -e "${YELLOW}  ⚠ ${service_name} already running (PID: $old_pid)${NC}"
            return 0
        else
            rm -f "$pid_file"
        fi
    fi

    echo -e "${CYAN}  → Starting ${service_name}...${NC}"

    # Export environment from .env.native (for VITE_ variables)
    set -a
    source "${ENV_NATIVE_FILE}"
    set +a

    # Start the frontend with PORT environment variable
    cd "${PROJECT_ROOT}/web-frontend"
    PORT=${port} nohup npm run dev > "$log_file" 2>&1 &
    local pid=$!
    echo $pid > "$pid_file"
    disown  # Remove from job control so parent script can exit

    echo -e "${GREEN}    ✓ Started ${service_name} (PID: $pid, Port: $port)${NC}"
    echo -e "${CYAN}    📝 Logs: ${log_file}${NC}"
    echo ""
}

# Build every selected service's fat JAR in ONE Gradle invocation.
# Previously each service built its own with --no-daemon, in parallel — N Gradle JVMs
# alive simultaneously, which dwarfed the memory the running services actually need.
build_jars() {
    local tasks=""
    local svc
    for svc in ${DEV_SERVICES}; do
        tasks="${tasks} $(service_module "$svc"):bootJar"
    done

    echo -e "${CYAN}→ Building JARs:${tasks}${NC}"
    cd "${PROJECT_ROOT}"
    if ! ./gradlew ${tasks} -q; then
        echo -e "${RED}  ✗ JAR build failed — see the Gradle output above${NC}"
        exit 1
    fi
    echo -e "${GREEN}  ✓ JARs built${NC}"
    echo ""
}

# Main execution
main() {
    check_prerequisites
    create_env_native
    check_local_postgres
    check_minio

    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║              Starting BATbern Services                     ║${NC}"
    echo -e "${BLUE}║              Instance ${INSTANCE} (BASE_PORT=${BASE_PORT})                  ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""

    echo -e "${CYAN}→ Services selected: ${DEV_SERVICES}${NC}"
    echo ""

    # One Gradle run for every JAR, before any service JVM exists.
    build_jars

    # Start API Gateway first (other services may depend on it)
    if service_enabled "api-gateway"; then
        start_spring_service "api-gateway"
    fi

    # Start backend services in parallel (they don't depend on each other)
    echo -e "${CYAN}→ Starting backend microservices...${NC}"
    local svc
    for svc in ${DEV_SERVICES}; do
        if [ "$svc" != "api-gateway" ]; then
            start_spring_service "$svc" &
        fi
    done

    # Wait for all background service starts to complete
    wait

    # Start frontend last
    start_frontend

    # Success banner
    echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║          All Services Started Successfully! 🚀             ║${NC}"
    echo -e "${GREEN}║              Instance ${INSTANCE} (BASE_PORT=${BASE_PORT})                  ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${CYAN}Services running at:${NC}"
    # `|| true` on every line: under `set -e` a false `&&` list would abort the script.
    service_enabled "api-gateway"             && echo -e "  ${GREEN}API Gateway:${NC}                http://localhost:${API_GATEWAY_PORT}  (heap $(service_heap api-gateway))" || true
    service_enabled "company-user-management" && echo -e "  ${GREEN}Company User Management:${NC}    http://localhost:${COMPANY_USER_MGMT_PORT}  (heap $(service_heap company-user-management))" || true
    service_enabled "event-management"        && echo -e "  ${GREEN}Event Management:${NC}           http://localhost:${EVENT_MGMT_PORT}  (heap $(service_heap event-management))" || true
    service_enabled "speaker-coordination"    && echo -e "  ${GREEN}Speaker Coordination:${NC}       http://localhost:${SPEAKER_COORD_PORT}  (heap $(service_heap speaker-coordination))" || true
    service_enabled "partner-coordination"    && echo -e "  ${GREEN}Partner Coordination:${NC}       http://localhost:${PARTNER_COORD_PORT}  (heap $(service_heap partner-coordination))" || true
    service_enabled "attendee-experience"     && echo -e "  ${GREEN}Attendee Experience:${NC}        http://localhost:${ATTENDEE_EXP_PORT}  (heap $(service_heap attendee-experience))" || true
    echo -e "  ${GREEN}Web Frontend:${NC}               http://localhost:${FRONTEND_PORT}"
    echo ""

    # Be explicit about what is NOT running — a 502 from the gateway is otherwise baffling.
    local skipped=""
    for svc in ${ALL_NATIVE_SERVICES}; do
        service_enabled "$svc" || skipped="${skipped} ${svc}"
    done
    if [ -n "$skipped" ]; then
        echo -e "${YELLOW}Not started:${skipped}${NC}"
        echo -e "${YELLOW}  Gateway routes to these will fail. Start them with:${NC}"
        echo -e "${YELLOW}  DEV_SERVICES=\"${DEV_SERVICES}${skipped}\" make dev-native-up${NC}"
        echo ""
    fi
    echo -e "${CYAN}Infrastructure:${NC}"
    echo -e "  ${GREEN}Database Tunnel:${NC}            localhost:${DB_TUNNEL_PORT}"
    echo -e "  ${GREEN}MinIO API:${NC}                  http://localhost:${MINIO_API_PORT}"
    echo -e "  ${GREEN}MinIO Console:${NC}              http://localhost:${MINIO_CONSOLE_PORT}"
    echo ""
    echo -e "${CYAN}Useful commands:${NC}"
    if [ "$INSTANCE" = "1" ]; then
        echo -e "  ${YELLOW}Check status:${NC}     make dev-native-status"
        echo -e "  ${YELLOW}View logs:${NC}        make dev-native-logs"
        echo -e "  ${YELLOW}Stop services:${NC}    make dev-native-down"
    else
        echo -e "  ${YELLOW}Check status:${NC}     make dev-native-status-instance BASE_PORT=${BASE_PORT}"
        echo -e "  ${YELLOW}View logs:${NC}        make dev-native-logs-instance BASE_PORT=${BASE_PORT}"
        echo -e "  ${YELLOW}Stop services:${NC}    make dev-native-down-instance BASE_PORT=${BASE_PORT}"
    fi
    echo ""
    echo -e "${YELLOW}💡 Tip: Logs are in ${LOG_DIR}/batbern-${INSTANCE}-*.log${NC}"
    echo ""
}

# Run main
main
