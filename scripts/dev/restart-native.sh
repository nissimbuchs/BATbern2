#!/bin/bash
# Restart BATbern services running natively
#
# Usage:
#   ./scripts/dev/restart-native.sh [service-name]
#
# Examples:
#   ./scripts/dev/restart-native.sh                  # Restart all services
#   ./scripts/dev/restart-native.sh api-gateway      # Restart specific service
#
# Available services:
#   api-gateway, company-user-management, event-management,
#   speaker-coordination, partner-coordination, attendee-experience,
#   web-frontend

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
ENV_NATIVE_FILE="${PROJECT_ROOT}/.env.native"

# Service configuration
declare -A SERVICE_GRADLE_TASKS=(
    ["api-gateway"]=":api-gateway:bootRun"
    ["company-user-management"]=":services:company-user-management-service:bootRun"
    ["event-management"]=":services:event-management-service:bootRun"
    ["speaker-coordination"]=":services:speaker-coordination-service:bootRun"
    ["partner-coordination"]=":services:partner-coordination-service:bootRun"
    ["attendee-experience"]=":services:attendee-experience-service:bootRun"
)

declare -A SERVICE_PORTS=(
    ["api-gateway"]=8080
    ["company-user-management"]=8081
    ["event-management"]=8082
    ["speaker-coordination"]=8083
    ["partner-coordination"]=8084
    ["attendee-experience"]=8085
    ["web-frontend"]=3000
)

# Show usage
show_usage() {
    echo "Usage: $0 [service-name]"
    echo ""
    echo "Available services:"
    echo "  api-gateway                 API Gateway service"
    echo "  company-user-management     Company User Management service"
    echo "  event-management            Event Management service"
    echo "  speaker-coordination        Speaker Coordination service"
    echo "  partner-coordination        Partner Coordination service"
    echo "  attendee-experience         Attendee Experience service"
    echo "  web-frontend                Web Frontend service"
    echo "  all                         All services (default)"
    echo ""
    echo "Examples:"
    echo "  $0                          # Restart all services"
    echo "  $0 api-gateway              # Restart API Gateway only"
    exit 1
}

# Stop a service
stop_service() {
    local service_name=$1
    local pid_file="${PID_DIR}/batbern-dev-${service_name}.pid"

    if [ ! -f "$pid_file" ]; then
        return 0
    fi

    local pid=$(cat "$pid_file")

    if ! ps -p $pid > /dev/null 2>&1; then
        rm -f "$pid_file"
        return 0
    fi

    echo -e "${CYAN}  → Stopping ${service_name}...${NC}"
    kill -TERM $pid 2>/dev/null || true

    # Wait for up to 10 seconds
    local attempts=0
    while [ $attempts -lt 10 ]; do
        if ! ps -p $pid > /dev/null 2>&1; then
            rm -f "$pid_file"
            return 0
        fi
        sleep 1
        attempts=$((attempts + 1))
    done

    # Force kill if still running
    kill -KILL $pid 2>/dev/null || true
    rm -f "$pid_file"
}

# Wait for service health check
wait_for_health() {
    local service_name=$1
    local port=$2
    local max_attempts=30
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if curl -sf "http://localhost:${port}/actuator/health" > /dev/null 2>&1; then
            return 0
        fi
        sleep 2
        attempt=$((attempt + 1))
    done

    return 1
}

# Start a Spring Boot service
start_spring_service() {
    local service_name=$1
    local gradle_task="${SERVICE_GRADLE_TASKS[$service_name]}"
    local port="${SERVICE_PORTS[$service_name]}"
    local pid_file="${PID_DIR}/batbern-dev-${service_name}.pid"
    local log_file="${LOG_DIR}/batbern-dev-${service_name}.log"

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
        local service_dir=$(echo "$gradle_task" | sed 's/:bootRun$//' | sed 's/^://' | sed 's/:/\//g')
        jar_path=$(find "${PROJECT_ROOT}/${service_dir}/build/libs" -name "*.jar" -type f ! -name "*-plain.jar" | head -1)
    fi

    if [ ! -f "$jar_path" ]; then
        echo -e "${RED}    ✗ JAR file not found for ${service_name}${NC}"
        return 1
    fi

    echo -e "${CYAN}  → Starting ${service_name}...${NC}"
    echo -e "${CYAN}    JAR: $(basename $jar_path)${NC}"

    # Export environment
    set -a
    source "${ENV_NATIVE_FILE}"
    set +a

    # Start the JAR with the correct port
    cd "${PROJECT_ROOT}"
    nohup java -jar "$jar_path" --server.port=$port > "$log_file" 2>&1 &
    local pid=$!
    echo $pid > "$pid_file"
    disown  # Remove from job control

    echo -e "${GREEN}    ✓ Started ${service_name} (PID: $pid)${NC}"

    # Wait for health check
    if wait_for_health "$service_name" "$port"; then
        echo -e "${GREEN}    ✓ ${service_name} is healthy${NC}"
    else
        echo -e "${YELLOW}    ⚠ ${service_name} health check timeout (check logs)${NC}"
    fi
}

# Start frontend
start_frontend() {
    local service_name="web-frontend"
    local port=3000
    local pid_file="${PID_DIR}/batbern-dev-${service_name}.pid"
    local log_file="${LOG_DIR}/batbern-dev-${service_name}.log"

    echo -e "${CYAN}  → Starting ${service_name}...${NC}"

    cd "${PROJECT_ROOT}/web-frontend"
    nohup npm run dev > "$log_file" 2>&1 &
    local pid=$!
    echo $pid > "$pid_file"
    disown  # Remove from job control

    echo -e "${GREEN}    ✓ Started ${service_name} (PID: $pid)${NC}"
}

# Restart a specific service
restart_service() {
    local service_name=$1

    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║     Restarting: ${service_name}${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""

    # Stop the service
    stop_service "$service_name"

    # Start the service
    if [ "$service_name" = "web-frontend" ]; then
        start_frontend
    else
        start_spring_service "$service_name"
    fi

    echo ""
    echo -e "${GREEN}✓ ${service_name} restarted successfully${NC}"
    echo ""
}

# Restart all services
restart_all() {
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║         Restarting All BATbern Services (Native)          ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""

    # Stop all services
    echo -e "${CYAN}→ Stopping all services...${NC}"
    for service in api-gateway company-user-management event-management speaker-coordination partner-coordination attendee-experience web-frontend; do
        stop_service "$service"
    done
    echo ""

    # Start all services
    echo -e "${CYAN}→ Starting all services...${NC}"
    echo ""

    start_spring_service "api-gateway"
    echo ""

    # Start backend services
    start_spring_service "company-user-management"
    echo ""
    start_spring_service "event-management"
    echo ""
    start_spring_service "speaker-coordination"
    echo ""
    start_spring_service "partner-coordination"
    echo ""
    start_spring_service "attendee-experience"
    echo ""

    # Start frontend
    start_frontend
    echo ""

    echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║       All Services Restarted Successfully! ✅              ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

# Main execution
main() {
    local service_name=""

    # Parse arguments
    if [ $# -eq 0 ]; then
        service_name="all"
    elif [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
        show_usage
    else
        service_name="$1"
    fi

    # Check if .env.native exists
    if [ ! -f "${ENV_NATIVE_FILE}" ]; then
        echo -e "${RED}Error: ${ENV_NATIVE_FILE} not found${NC}"
        echo -e "${YELLOW}Run: make dev-native-up${NC}"
        exit 1
    fi

    # Restart service(s)
    if [ "$service_name" = "all" ]; then
        restart_all
    else
        # Validate service name
        if [ -z "${SERVICE_PORTS[$service_name]}" ]; then
            echo -e "${RED}Error: Unknown service '${service_name}'${NC}"
            echo ""
            show_usage
        fi

        restart_service "$service_name"
    fi
}

# Run main
main "$@"
