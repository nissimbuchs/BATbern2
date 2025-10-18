#!/bin/bash
# View logs from BATbern services running natively
#
# Usage:
#   ./scripts/dev/logs-native.sh [service-name] [options]
#
# Examples:
#   ./scripts/dev/logs-native.sh                    # Tail all service logs
#   ./scripts/dev/logs-native.sh api-gateway        # Tail specific service
#   ./scripts/dev/logs-native.sh api-gateway -n 50  # Show last 50 lines
#
# Available services:
#   api-gateway, company-user-management, event-management,
#   speaker-coordination, partner-coordination, attendee-experience,
#   web-frontend, db-tunnel

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Configuration
LOG_DIR="/tmp"

# Service name to color mapping
declare -A SERVICE_COLORS=(
    ["api-gateway"]="${BLUE}"
    ["company-user-management"]="${GREEN}"
    ["event-management"]="${CYAN}"
    ["speaker-coordination"]="${YELLOW}"
    ["partner-coordination"]="${MAGENTA}"
    ["attendee-experience"]="${RED}"
    ["web-frontend"]="${GREEN}"
    ["db-tunnel"]="${CYAN}"
)

# Show usage
show_usage() {
    echo "Usage: $0 [service-name] [options]"
    echo ""
    echo "Available services:"
    echo "  api-gateway                 API Gateway service"
    echo "  company-user-management     Company User Management service"
    echo "  event-management            Event Management service"
    echo "  speaker-coordination        Speaker Coordination service"
    echo "  partner-coordination        Partner Coordination service"
    echo "  attendee-experience         Attendee Experience service"
    echo "  web-frontend                Web Frontend service"
    echo "  db-tunnel                   Database tunnel"
    echo "  all                         All services (default)"
    echo ""
    echo "Options:"
    echo "  -n NUM    Show last NUM lines (default: follow mode)"
    echo "  -f        Follow mode (default)"
    echo ""
    echo "Examples:"
    echo "  $0                          # Tail all services"
    echo "  $0 api-gateway              # Tail API Gateway"
    echo "  $0 api-gateway -n 100       # Show last 100 lines"
    exit 1
}

# Tail a specific service log
tail_service_log() {
    local service_name=$1
    local lines=${2:-""}
    local log_file="${LOG_DIR}/batbern-dev-${service_name}.log"

    if [ ! -f "$log_file" ]; then
        echo -e "${RED}Error: Log file not found for ${service_name}${NC}"
        echo -e "${YELLOW}Expected: ${log_file}${NC}"
        return 1
    fi

    local color="${SERVICE_COLORS[$service_name]:-${NC}}"

    if [ -n "$lines" ]; then
        # Show last N lines
        echo -e "${color}==> ${service_name} (last ${lines} lines) <==${NC}"
        tail -n "$lines" "$log_file" | sed "s/^/${color}[$service_name]${NC} /"
    else
        # Follow mode
        echo -e "${color}==> Following ${service_name} logs <==${NC}"
        tail -f "$log_file" | sed "s/^/${color}[$service_name]${NC} /" &
    fi
}

# Tail all service logs
tail_all_logs() {
    local lines=${1:-""}

    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║         BATbern Platform - Service Logs (Native)          ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""

    if [ -n "$lines" ]; then
        # Show last N lines from all services
        for service in api-gateway company-user-management event-management speaker-coordination partner-coordination attendee-experience web-frontend db-tunnel; do
            tail_service_log "$service" "$lines"
            echo ""
        done
    else
        # Follow mode for all services
        echo -e "${CYAN}Following logs from all services...${NC}"
        echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
        echo ""

        # Start tail for each service in background
        for service in api-gateway company-user-management event-management speaker-coordination partner-coordination attendee-experience web-frontend db-tunnel; do
            tail_service_log "$service" &
        done

        # Wait for all background processes
        wait
    fi
}

# Main execution
main() {
    local service_name=""
    local lines=""
    local follow=true

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -n)
                lines="$2"
                follow=false
                shift 2
                ;;
            -f)
                follow=true
                shift
                ;;
            -h|--help)
                show_usage
                ;;
            all)
                service_name=""
                shift
                ;;
            api-gateway|company-user-management|event-management|speaker-coordination|partner-coordination|attendee-experience|web-frontend|db-tunnel)
                service_name="$1"
                shift
                ;;
            *)
                echo -e "${RED}Unknown option or service: $1${NC}"
                echo ""
                show_usage
                ;;
        esac
    done

    # If no service specified, show all logs
    if [ -z "$service_name" ]; then
        tail_all_logs "$lines"
    else
        tail_service_log "$service_name" "$lines"

        # If in follow mode for single service, wait
        if [ "$follow" = true ] && [ -z "$lines" ]; then
            wait
        fi
    fi
}

# Run main
main "$@"
