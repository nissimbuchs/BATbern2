#!/bin/bash
# Stop all BATbern services running natively
#
# Usage:
#   ./scripts/dev/stop-all-native.sh [--keep-tunnel]
#
# Options:
#   --keep-tunnel    Don't stop the database tunnel

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
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Stop a service gracefully
stop_service() {
    local service_name=$1
    local pid_file="${PID_DIR}/batbern-dev-${service_name}.pid"

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

    # Stop DB tunnel (unless --keep-tunnel is specified)
    if [ "$KEEP_TUNNEL" = false ]; then
        echo ""
        stop_service "db-tunnel"

        # Also try to kill any AWS SSM session processes
        if pgrep -f "AWS-StartPortForwardingSessionToRemoteHost" > /dev/null; then
            echo -e "${CYAN}  → Stopping AWS SSM tunnel sessions...${NC}"
            pkill -f "AWS-StartPortForwardingSessionToRemoteHost" || true
            echo -e "${GREEN}    ✓ SSM tunnel sessions stopped${NC}"
        fi
    else
        echo ""
        echo -e "${YELLOW}  ⚠ Database tunnel kept running (use --keep-tunnel to stop)${NC}"
    fi

    echo ""
    echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║          All Services Stopped Successfully! ✅             ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""

    if [ "$KEEP_TUNNEL" = true ]; then
        echo -e "${CYAN}💡 Database tunnel is still running${NC}"
        echo -e "${CYAN}   To stop it: ./scripts/dev/stop-all-native.sh${NC}"
        echo ""
    fi
}

# Run main
main
