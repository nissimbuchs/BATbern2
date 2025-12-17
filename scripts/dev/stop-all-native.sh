#!/bin/bash
# Stop all BATbern services running natively
#
# Usage:
#   ./scripts/dev/stop-all-native.sh [--keep-tunnel]              # Stop default instance
#   BASE_PORT=9000 ./scripts/dev/stop-all-native.sh              # Stop instance 2
#
# Options:
#   --keep-tunnel    Don't stop the database tunnel and MinIO

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
KEEP_TUNNEL=false

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

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --keep-tunnel)
            KEEP_TUNNEL=true
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Usage: $0 [--keep-tunnel]"
            exit 1
            ;;
    esac
done

# Banner
echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║      BATbern Platform - Stopping Native Services          ║${NC}"
echo -e "${BLUE}║              Instance ${INSTANCE} (BASE_PORT=${BASE_PORT})                  ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Stop a service gracefully
stop_service() {
    local service_name=$1

    # Shared services (minio, db-tunnel) use "shared" prefix instead of instance number
    if [ "$service_name" = "minio" ] || [ "$service_name" = "db-tunnel" ]; then
        local pid_file="${PID_DIR}/batbern-shared-${service_name}.pid"
    else
        local pid_file="${PID_DIR}/batbern-${INSTANCE}-${service_name}.pid"
    fi

    if [ ! -f "$pid_file" ]; then
        echo -e "${YELLOW}  ⚠ ${service_name}: No PID file found${NC}"
        return 0
    fi

    local pid=$(cat "$pid_file")

    if ! ps -p $pid > /dev/null 2>&1; then
        echo -e "${YELLOW}  ⚠ ${service_name}: Process not running (PID: $pid)${NC}"
        rm -f "$pid_file"
        return 0
    fi

    echo -e "${CYAN}  → Stopping ${service_name} (PID: $pid)...${NC}"

    # Send SIGTERM for graceful shutdown
    kill -TERM $pid 2>/dev/null || true

    # Wait for up to 15 seconds for graceful shutdown
    local attempts=0
    local max_attempts=15

    while [ $attempts -lt $max_attempts ]; do
        if ! ps -p $pid > /dev/null 2>&1; then
            echo -e "${GREEN}    ✓ ${service_name} stopped gracefully${NC}"
            rm -f "$pid_file"
            return 0
        fi

        sleep 1
        attempts=$((attempts + 1))
    done

    # Force kill if still running
    echo -e "${YELLOW}    ⚠ ${service_name} did not stop gracefully, force killing...${NC}"
    kill -KILL $pid 2>/dev/null || true
    sleep 1

    if ! ps -p $pid > /dev/null 2>&1; then
        echo -e "${GREEN}    ✓ ${service_name} stopped (forced)${NC}"
        rm -f "$pid_file"
        return 0
    else
        echo -e "${RED}    ✗ Failed to stop ${service_name}${NC}"
        return 1
    fi
}

# Main execution
main() {
    echo -e "${CYAN}→ Stopping services...${NC}"
    echo ""

    # Stop frontend first
    stop_service "web-frontend"

    # Stop backend services
    stop_service "api-gateway"
    stop_service "company-user-management"
    stop_service "event-management"
    stop_service "speaker-coordination"
    stop_service "partner-coordination"
    stop_service "attendee-experience"

    # Stop infrastructure services (unless --keep-tunnel is specified)
    if [ "$KEEP_TUNNEL" = false ]; then
        echo ""

        # Stop MinIO
        stop_service "minio"

        # Note: PostgreSQL is managed by Docker Compose (docker-compose-dev.yml)
        # It is NOT stopped by this script to preserve data between sessions
        # To stop PostgreSQL: docker compose -f docker-compose-dev.yml down
        echo -e "${CYAN}  ℹ  Local PostgreSQL is managed by Docker Compose (kept running)${NC}"
    else
        echo ""
        echo -e "${YELLOW}  ⚠ Infrastructure services (PostgreSQL, MinIO) kept running${NC}"
    fi

    echo ""
    echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║          All Services Stopped Successfully! ✅             ║${NC}"
    echo -e "${GREEN}║              Instance ${INSTANCE} (BASE_PORT=${BASE_PORT})                  ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""

    echo -e "${CYAN}💡 Infrastructure services:${NC}"
    echo -e "${CYAN}   - PostgreSQL: Managed by Docker Compose (kept running)${NC}"
    echo -e "${CYAN}     Stop: docker compose -f docker-compose-dev.yml down${NC}"
    if [ "$KEEP_TUNNEL" = true ]; then
        echo -e "${CYAN}   - MinIO: Still running${NC}"
    fi
    echo ""
}

# Run main
main
