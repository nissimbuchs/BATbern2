#!/bin/bash
# Show detailed status of all running native development instances
#
# Usage:
#   ./scripts/dev/status-all-instances.sh

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
PID_DIR="/tmp"

# Service names
SERVICES=("api-gateway" "company-user-management" "event-management" "speaker-coordination" "partner-coordination" "attendee-experience" "web-frontend" "db-tunnel" "minio")

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   BATbern Native Development - Instances Status Report    ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Find all instances
found_instances=()

for pid_file in ${PID_DIR}/batbern-*-api-gateway.pid; do
    if [ -f "$pid_file" ]; then
        # Extract instance number from filename
        instance=$(basename "$pid_file" | sed 's/batbern-//' | sed 's/-api-gateway.pid//')

        # Check if the process is actually running
        pid=$(cat "$pid_file")
        if ps -p $pid > /dev/null 2>&1; then
            found_instances+=("$instance")
        fi
    fi
done

if [ ${#found_instances[@]} -eq 0 ]; then
    echo -e "${YELLOW}No active instances found${NC}"
    echo ""
    echo -e "${CYAN}To start an instance:${NC}"
    echo -e "  ${GREEN}Instance 1:${NC} make dev-native-up"
    echo -e "  ${GREEN}Instance 2:${NC} make dev-native-up-instance BASE_PORT=9000"
    echo ""
    exit 0
fi

# Show status for each instance
for instance in "${found_instances[@]}"; do
    # Calculate ports based on instance
    if [ "$instance" = "1" ]; then
        base_port=8000
    elif [ "$instance" = "2" ]; then
        base_port=9000
    else
        base_port=$instance
    fi

    echo -e "${GREEN}╭─ Instance ${instance} (BASE_PORT=${base_port}) ───────────────────────────────────╮${NC}"
    echo ""

    running_count=0
    stopped_count=0

    for service in "${SERVICES[@]}"; do
        pid_file="${PID_DIR}/batbern-${instance}-${service}.pid"

        if [ ! -f "$pid_file" ]; then
            echo -e "  ${YELLOW}○${NC} ${service}: Not started"
            stopped_count=$((stopped_count + 1))
            continue
        fi

        pid=$(cat "$pid_file")

        if ps -p $pid > /dev/null 2>&1; then
            # Get process uptime
            start_time=$(ps -o lstart= -p $pid 2>/dev/null | xargs)
            cpu_usage=$(ps -o %cpu= -p $pid 2>/dev/null | xargs)
            mem_usage=$(ps -o %mem= -p $pid 2>/dev/null | xargs)

            echo -e "  ${GREEN}●${NC} ${service}: Running (PID: ${pid})"
            echo -e "     CPU: ${cpu_usage}% | MEM: ${mem_usage}% | Started: ${start_time}"

            running_count=$((running_count + 1))
        else
            echo -e "  ${RED}✗${NC} ${service}: Stopped (stale PID: ${pid})"
            stopped_count=$((stopped_count + 1))
        fi
    done

    echo ""
    echo -e "  ${CYAN}Summary:${NC} ${GREEN}${running_count} running${NC}, ${YELLOW}${stopped_count} stopped${NC}"

    # Calculate ports
    port_offset=$((base_port / 1000))
    api_port=$((port_offset * 1000 + 80))
    frontend_port=$((3000 + (instance - 1) * 1000))
    db_port=$((5432 + (instance - 1) * 1000))
    minio_api=$((9000 + (instance - 1) * 100))
    minio_console=$((9001 + (instance - 1) * 100))

    echo -e "  ${CYAN}Endpoints:${NC}"
    echo -e "    API Gateway:    http://localhost:${api_port}"
    echo -e "    Frontend:       http://localhost:${frontend_port}"
    echo -e "    DB Tunnel:      localhost:${db_port}"
    echo -e "    MinIO Console:  http://localhost:${minio_console}"

    echo ""
    echo -e "${GREEN}╰────────────────────────────────────────────────────────────╯${NC}"
    echo ""
done

echo -e "${CYAN}Total Active Instances: ${GREEN}${#found_instances[@]}${NC}"
echo ""
echo -e "${CYAN}Useful commands:${NC}"
echo -e "  ${YELLOW}List instances:${NC}   make dev-native-list"
echo -e "  ${YELLOW}View logs:${NC}        make dev-native-logs-instance BASE_PORT=<port>"
echo ""
