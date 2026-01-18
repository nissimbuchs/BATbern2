#!/bin/bash
# ==============================================
# Claude Code Launcher with Environment
# ==============================================
# This script sources the .env file before starting Claude Code
# to ensure MCP servers have access to required credentials.
#
# Usage: ./scripts/dev/claude-code.sh
# ==============================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Source .env file if it exists
if [ -f "${PROJECT_ROOT}/.env" ]; then
    echo "Loading environment from .env..."
    set -a
    source "${PROJECT_ROOT}/.env"
    set +a
fi

# Verify LINEAR_API_KEY is set
if [ -z "$LINEAR_API_KEY" ] || [ "$LINEAR_API_KEY" = "lin_api_YOUR_API_KEY_HERE" ]; then
    echo "WARNING: LINEAR_API_KEY not configured!"
    echo "Please edit .env and set your Linear API key."
    echo "Get your key from: https://linear.app/settings/api"
    echo ""
fi

# Start Claude Code
exec claude "$@"
