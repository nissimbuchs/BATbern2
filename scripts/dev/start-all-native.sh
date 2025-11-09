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
# Services started (with default ports):
#   - API Gateway (port 8080)
#   - Company User Management Service (port 8081)
#   - Event Management Service (port 8082)
#   - Speaker Coordination Service (port 8083)
#   - Partner Coordination Service (port 8084)
#   - Attendee Experience Service (port 8085)
#   - Web Frontend (port 3000)

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
# DB tunnel port - always 5432 (shared across all instances)
DB_TUNNEL_PORT=5432

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
# Override: Use localhost with instance-specific tunnel port
DB_HOST=localhost
DB_PORT=${DB_TUNNEL_PORT}
DB_NAME=${DB_NAME}
DB_USER=${DB_USER}
DB_PASSWORD=${DB_PASSWORD}
DATABASE_URL="jdbc:postgresql://localhost:${DB_TUNNEL_PORT}/${DB_NAME}?user=${DB_USER}&password=${DB_PASSWORD}"

# ==============================================
# Application Configuration
# ==============================================
APP_ENVIRONMENT=${APP_ENVIRONMENT:-development}
SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-local}
LOG_LEVEL=${LOG_LEVEL:-DEBUG}

# ==============================================
# AWS Configuration
# ==============================================
AWS_REGION=${AWS_REGION:-eu-central-1}
AWS_ACCOUNT_ID=${AWS_ACCOUNT_ID}
AWS_PROFILE=${AWS_PROFILE:-batbern-dev}

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
# Frontend Configuration (Instance ${INSTANCE})
# ==============================================
VITE_API_BASE_URL=http://localhost:${API_GATEWAY_PORT}
VITE_API_PORT=${API_GATEWAY_PORT}
VITE_PORT=${FRONTEND_PORT}
EOF

    echo -e "${GREEN}  ✓ Created ${ENV_NATIVE_FILE}${NC}"
    echo ""
}

# Check if DB tunnel is running
check_db_tunnel() {
    # Shared DB tunnel - use "shared" prefix instead of instance-specific
    local pid_file="${PID_DIR}/batbern-shared-db-tunnel.pid"
    local log_file="${LOG_DIR}/batbern-shared-db-tunnel.log"

    echo -e "${CYAN}→ Checking database tunnel (port ${DB_TUNNEL_PORT})...${NC}"

    # Check if shared tunnel is already running
    if [ -f "$pid_file" ]; then
        local old_pid=$(cat "$pid_file")
        if ps -p $old_pid > /dev/null 2>&1; then
            echo -e "${GREEN}  ✓ Database tunnel is running (PID: $old_pid, Port: ${DB_TUNNEL_PORT})${NC}"
            echo ""
            return 0
        else
            rm -f "$pid_file"
        fi
    fi

    echo -e "${YELLOW}  ⚠ Database tunnel not running (shared across all instances)${NC}"
    echo -e "${CYAN}  → Starting database tunnel on port ${DB_TUNNEL_PORT}...${NC}"

    DB_TUNNEL_PORT=${DB_TUNNEL_PORT} "${PROJECT_ROOT}/scripts/dev/start-db-tunnel.sh" > "$log_file" 2>&1 &
    local tunnel_pid=$!
    echo $tunnel_pid > "$pid_file"
    disown  # Remove from job control so parent script can exit

    echo -e "${CYAN}  → Waiting for tunnel to be ready (15 seconds)...${NC}"
    sleep 15

    if ps -p $tunnel_pid > /dev/null 2>&1; then
        echo -e "${GREEN}  ✓ Database tunnel started (PID: $tunnel_pid, Port: ${DB_TUNNEL_PORT})${NC}"
    else
        echo -e "${RED}  ✗ Database tunnel failed to start${NC}"
        echo "    Check logs: cat $log_file"
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
    local gradle_task=$2
    local port=$3
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

    echo -e "${CYAN}  → Building ${service_name} JAR...${NC}"

    # Build the JAR
    cd "${PROJECT_ROOT}"
    local jar_task="${gradle_task//:bootRun/:bootJar}"
    ./gradlew ${jar_task} --no-daemon -q > /dev/null 2>&1

    if [ $? -ne 0 ]; then
        echo -e "${RED}    ✗ Failed to build ${service_name} JAR${NC}"
        return 1
    fi

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

    echo -e "${CYAN}  → Starting ${service_name}...${NC}"
    echo -e "${CYAN}    JAR: $(basename $jar_path)${NC}"

    # Export environment from .env.native
    set -a
    source "${ENV_NATIVE_FILE}"
    set +a

    # Start with gradlew bootRun for better hot-reload with DevTools
    # Use absolute path from PROJECT_ROOT
    local gradle_module=$(echo "$gradle_task" | sed 's/:bootRun$//')
    cd "${PROJECT_ROOT}"
    nohup ./gradlew ${gradle_module}:bootRun --args="--server.port=$port" > "$log_file" 2>&1 &
    local pid=$!
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

# Main execution
main() {
    check_prerequisites
    create_env_native
    check_db_tunnel
    check_minio

    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║              Starting BATbern Services                     ║${NC}"
    echo -e "${BLUE}║              Instance ${INSTANCE} (BASE_PORT=${BASE_PORT})                  ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""

    # Start API Gateway first (other services may depend on it)
    start_spring_service "api-gateway" ":api-gateway:bootRun" ${API_GATEWAY_PORT}

    # Start backend services in parallel (they don't depend on each other)
    echo -e "${CYAN}→ Starting backend microservices...${NC}"
    start_spring_service "company-user-management" ":services:company-user-management-service:bootRun" ${COMPANY_USER_MGMT_PORT} &
    start_spring_service "event-management" ":services:event-management-service:bootRun" ${EVENT_MGMT_PORT} &
    start_spring_service "speaker-coordination" ":services:speaker-coordination-service:bootRun" ${SPEAKER_COORD_PORT} &
    start_spring_service "partner-coordination" ":services:partner-coordination-service:bootRun" ${PARTNER_COORD_PORT} &
    start_spring_service "attendee-experience" ":services:attendee-experience-service:bootRun" ${ATTENDEE_EXP_PORT} &

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
    echo -e "  ${GREEN}API Gateway:${NC}                http://localhost:${API_GATEWAY_PORT}"
    echo -e "  ${GREEN}Company User Management:${NC}    http://localhost:${COMPANY_USER_MGMT_PORT}"
    echo -e "  ${GREEN}Event Management:${NC}           http://localhost:${EVENT_MGMT_PORT}"
    echo -e "  ${GREEN}Speaker Coordination:${NC}       http://localhost:${SPEAKER_COORD_PORT}"
    echo -e "  ${GREEN}Partner Coordination:${NC}       http://localhost:${PARTNER_COORD_PORT}"
    echo -e "  ${GREEN}Attendee Experience:${NC}        http://localhost:${ATTENDEE_EXP_PORT}"
    echo -e "  ${GREEN}Web Frontend:${NC}               http://localhost:${FRONTEND_PORT}"
    echo ""
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
