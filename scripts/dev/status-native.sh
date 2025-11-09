#!/bin/bash
# Show status of all BATbern services running natively
#
# Usage:
#   ./scripts/dev/status-native.sh
#   BASE_PORT=9500 ./scripts/dev/status-native.sh

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
PID_DIR="/tmp"
LOG_DIR="/tmp"

# Instance-specific configuration
BASE_PORT="${BASE_PORT:-8000}"  # Default instance port

# Calculate instance identifier based on BASE_PORT
# Always use BASE_PORT as the instance identifier for consistency
INSTANCE="$BASE_PORT"

# Calculate ports - consistent for all instances
# Pattern: BASE_PORT for API Gateway, BASE_PORT+1..5 for services, BASE_PORT+100 for frontend
API_GATEWAY_PORT=$BASE_PORT
COMPANY_USER_MGMT_PORT=$((BASE_PORT + 1))
EVENT_MGMT_PORT=$((BASE_PORT + 2))
SPEAKER_COORD_PORT=$((BASE_PORT + 3))
PARTNER_COORD_PORT=$((BASE_PORT + 4))
ATTENDEE_EXP_PORT=$((BASE_PORT + 5))
FRONTEND_PORT=$((BASE_PORT + 100))

# Shared infrastructure ports
DB_TUNNEL_PORT=5432
MINIO_API_PORT=8450
MINIO_CONSOLE_PORT=8451

# Banner
echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║       BATbern Platform - Native Services Status           ║${NC}"
echo -e "${BLUE}║              Instance ${INSTANCE} (BASE_PORT=${BASE_PORT})                  ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check service status
check_service_status() {
    local service_name=$1
    local port=$2

    # Shared services (minio, db-tunnel) use "shared" prefix
    if [ "$service_name" = "minio" ] || [ "$service_name" = "db-tunnel" ]; then
        local pid_file="${PID_DIR}/batbern-shared-${service_name}.pid"
        local log_file="${LOG_DIR}/batbern-shared-${service_name}.log"
    else
        local pid_file="${PID_DIR}/batbern-${INSTANCE}-${service_name}.pid"
        local log_file="${LOG_DIR}/batbern-${INSTANCE}-${service_name}.log"
    fi

    # Service name column (left-aligned, 30 chars)
    printf "%-30s" "$service_name"

    # Check if PID file exists
    if [ ! -f "$pid_file" ]; then
        echo -e " ${RED}✗ STOPPED${NC}    ${YELLOW}(no PID file)${NC}"
        return 1
    fi

    local pid=$(cat "$pid_file")

    # Check if process is running
    if ! ps -p $pid > /dev/null 2>&1; then
        echo -e " ${RED}✗ STOPPED${NC}    ${YELLOW}(PID: $pid, process not found)${NC}"
        return 1
    fi

    # Get memory usage
    local mem_mb=0
    if command -v ps &> /dev/null; then
        mem_mb=$(ps -o rss= -p $pid 2>/dev/null | awk '{print int($1/1024)}')
    fi

    # Check health endpoint for Spring Boot services
    local health_status="N/A"
    if [ -n "$port" ]; then
        # Check service type and use appropriate health check
        if [[ "$service_name" == "minio" ]]; then
            # MinIO health check
            if curl -sf "http://localhost:${port}/minio/health/live" > /dev/null 2>&1; then
                health_status="${GREEN}HEALTHY${NC}"
            else
                health_status="${YELLOW}UNHEALTHY${NC}"
            fi
        elif [[ "$service_name" == "db-tunnel" ]]; then
            # DB tunnel - check if port is accessible
            if nc -z localhost ${port} 2>/dev/null; then
                health_status="${GREEN}ACTIVE${NC}"
            else
                health_status="${YELLOW}INACTIVE${NC}"
            fi
        elif [[ "$service_name" == "web-frontend" ]]; then
            # For frontend, check if port is listening
            if lsof -i:${port} -sTCP:LISTEN > /dev/null 2>&1; then
                health_status="${GREEN}LISTENING${NC}"
            else
                health_status="${YELLOW}STARTING${NC}"
            fi
        else
            # Spring Boot services
            if curl -sf "http://localhost:${port}/actuator/health" > /dev/null 2>&1; then
                health_status="${GREEN}HEALTHY${NC}"
            else
                health_status="${YELLOW}UNHEALTHY${NC}"
            fi
        fi
    fi

    echo -e " ${GREEN}✓ RUNNING${NC}   PID: ${CYAN}${pid}${NC}  Port: ${CYAN}${port}${NC}  Health: ${health_status}  Mem: ${CYAN}${mem_mb}MB${NC}"

    # Show last error line if service is running but unhealthy
    if [ "$health_status" == "${YELLOW}UNHEALTHY${NC}" ] && [ -f "$log_file" ]; then
        local last_error=$(grep -i "error\|exception\|failed" "$log_file" | tail -1 | cut -c1-100)
        if [ -n "$last_error" ]; then
            echo -e "   ${RED}⚠ Last error: ${last_error}...${NC}"
        fi
    fi

    return 0
}

# Main execution
main() {
    local all_running=true

    # Check all services using calculated ports
    check_service_status "api-gateway" ${API_GATEWAY_PORT} || all_running=false
    check_service_status "company-user-management" ${COMPANY_USER_MGMT_PORT} || all_running=false
    check_service_status "event-management" ${EVENT_MGMT_PORT} || all_running=false
    check_service_status "speaker-coordination" ${SPEAKER_COORD_PORT} || all_running=false
    check_service_status "partner-coordination" ${PARTNER_COORD_PORT} || all_running=false
    check_service_status "attendee-experience" ${ATTENDEE_EXP_PORT} || all_running=false
    check_service_status "web-frontend" ${FRONTEND_PORT} || all_running=false

    echo ""

    # Check shared infrastructure services
    echo -e "${CYAN}Shared Infrastructure (all instances):${NC}"
    check_service_status "db-tunnel" ${DB_TUNNEL_PORT} || all_running=false
    check_service_status "minio" ${MINIO_API_PORT} || all_running=false

    echo ""
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"

    if [ "$all_running" = true ]; then
        echo -e "${GREEN}All services are running ✅${NC}"
    else
        echo -e "${YELLOW}Some services are not running ⚠${NC}"
        echo -e "${CYAN}To start services: make dev-native-up-instance BASE_PORT=${BASE_PORT}${NC}"
    fi

    echo ""
    echo -e "${CYAN}Useful commands (for this instance):${NC}"
    echo -e "  View logs:         ${YELLOW}make dev-native-logs-instance BASE_PORT=${BASE_PORT}${NC}"
    echo -e "  Stop services:     ${YELLOW}make dev-native-down-instance BASE_PORT=${BASE_PORT}${NC}"
    echo -e "  Status:            ${YELLOW}make dev-native-status-instance BASE_PORT=${BASE_PORT}${NC}"
    echo ""
}

# Run main
main
