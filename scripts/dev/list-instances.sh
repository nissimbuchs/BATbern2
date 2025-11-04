#!/bin/bash
# List all running native development instances
#
# Usage:
#   ./scripts/dev/list-instances.sh

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

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║        BATbern Native Development - Active Instances      ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Find all instance-specific PID files
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

echo -e "${CYAN}Active Instances:${NC}"
echo ""

for instance in "${found_instances[@]}"; do
    # Calculate ports based on instance
    if [ "$instance" = "1" ]; then
        base_port=8000
    elif [ "$instance" = "2" ]; then
        base_port=9000
    else
        base_port=$instance
    fi

    port_offset=$((base_port / 1000))
    api_port=$((port_offset * 1000 + 80))
    frontend_port=$((3000 + (instance - 1) * 1000))
    db_port=$((5432 + (instance - 1) * 1000))
    minio_api=$((9000 + (instance - 1) * 100))
    minio_console=$((9001 + (instance - 1) * 100))

    echo -e "${GREEN}Instance ${instance}${NC} (BASE_PORT=${base_port})"
    echo -e "  Services:"
    echo -e "    API Gateway:       http://localhost:${api_port}"
    echo -e "    Frontend:          http://localhost:${frontend_port}"
    echo -e "  Infrastructure:"
    echo -e "    DB Tunnel:         localhost:${db_port}"
    echo -e "    MinIO API:         http://localhost:${minio_api}"
    echo -e "    MinIO Console:     http://localhost:${minio_console}"
    echo -e "  Management:"
    if [ "$instance" = "1" ]; then
        echo -e "    Status:            make dev-native-status"
        echo -e "    Logs:              make dev-native-logs"
        echo -e "    Stop:              make dev-native-down"
    else
        echo -e "    Status:            make dev-native-status-instance BASE_PORT=${base_port}"
        echo -e "    Logs:              make dev-native-logs-instance BASE_PORT=${base_port}"
        echo -e "    Stop:              make dev-native-down-instance BASE_PORT=${base_port}"
    fi
    echo ""
done

echo -e "${CYAN}Total Active Instances: ${GREEN}${#found_instances[@]}${NC}"
echo ""
