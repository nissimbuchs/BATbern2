#!/bin/bash
# Show status of all BATbern services running natively
#
# Usage:
#   ./scripts/dev/status-native.sh

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

# Banner
echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║       BATbern Platform - Native Services Status           ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check service status
check_service_status() {
    local service_name=$1
    local port=$2
    local pid_file="${PID_DIR}/batbern-dev-${service_name}.pid"
    local log_file="${LOG_DIR}/batbern-dev-${service_name}.log"

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
        if curl -sf "http://localhost:${port}/actuator/health" > /dev/null 2>&1; then
            health_status="${GREEN}HEALTHY${NC}"
        else
            health_status="${YELLOW}UNHEALTHY${NC}"
        fi
    else
        # For frontend, just check if port is listening
        if lsof -i:3000 -sTCP:LISTEN > /dev/null 2>&1; then
            health_status="${GREEN}LISTENING${NC}"
        else
            health_status="${YELLOW}STARTING${NC}"
        fi
    fi

    echo -e " ${GREEN}✓ RUNNING${NC}   PID: ${CYAN}${pid}${NC}  Port: ${CYAN}${port:-3000}${NC}  Health: ${health_status}  Mem: ${CYAN}${mem_mb}MB${NC}"

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

    # Check all services
    check_service_status "api-gateway" 8080 || all_running=false
    check_service_status "company-user-management" 8081 || all_running=false
    check_service_status "event-management" 8082 || all_running=false
    check_service_status "speaker-coordination" 8083 || all_running=false
    check_service_status "partner-coordination" 8084 || all_running=false
    check_service_status "attendee-experience" 8085 || all_running=false
    check_service_status "web-frontend" 3000 || all_running=false

    echo ""

    # Check database tunnel
    echo -e "${CYAN}Database Tunnel:${NC}"
    if pgrep -f "AWS-StartPortForwardingSessionToRemoteHost" > /dev/null; then
        local tunnel_pid=$(pgrep -f "AWS-StartPortForwardingSessionToRemoteHost" | head -1)
        echo -e "  ${GREEN}✓ RUNNING${NC}   PID: ${CYAN}${tunnel_pid}${NC}  Port: ${CYAN}5432${NC} → AWS RDS"
    else
        echo -e "  ${RED}✗ STOPPED${NC}"
        all_running=false
    fi

    echo ""
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"

    if [ "$all_running" = true ]; then
        echo -e "${GREEN}All services are running ✅${NC}"
    else
        echo -e "${YELLOW}Some services are not running ⚠${NC}"
        echo -e "${CYAN}To start all services: make dev-native-up${NC}"
    fi

    echo ""
    echo -e "${CYAN}Useful commands:${NC}"
    echo -e "  View logs:         ${YELLOW}make dev-native-logs${NC}"
    echo -e "  Restart services:  ${YELLOW}make dev-native-restart${NC}"
    echo -e "  Stop services:     ${YELLOW}make dev-native-down${NC}"
    echo ""
}

# Run main
main
