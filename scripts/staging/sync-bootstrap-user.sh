#!/bin/bash
# Wrapper script for sync-bootstrap-user.ts
# Simplifies execution of the TypeScript sync script

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/../.." && pwd )"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}🚀 Bootstrap User Sync Wrapper${NC}"
echo "================================"
echo ""

# Check if tunnel is running
if ! lsof -i :5433 > /dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  DB tunnel does not appear to be running on port 5433${NC}"
    echo ""
    echo "Start the tunnel first in another terminal:"
    echo "  ./scripts/staging/start-db-tunnel.sh"
    echo ""
    read -p "Continue anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Check for required dependencies
echo "Checking dependencies in infrastructure/node_modules..."
cd "$PROJECT_ROOT/infrastructure"

if [ ! -d "node_modules/pg" ] || [ ! -d "node_modules/@aws-sdk/client-cognito-identity-provider" ]; then
    echo -e "${YELLOW}⚠️  Required dependencies are not installed${NC}"
    echo ""
    echo "Installing dependencies..."
    npm install @aws-sdk/client-cognito-identity-provider @aws-sdk/client-secrets-manager @types/pg @types/node
    echo ""
fi

# Run the TypeScript script from infrastructure directory (where node_modules are)
echo "Running sync script from infrastructure directory..."
echo "Script: $SCRIPT_DIR/sync-bootstrap-user.ts"
echo "Arguments: $@"
echo ""

# Change to infrastructure directory and run the script with relative path
# Use --transpile-only to skip type checking (modules will resolve from infrastructure/node_modules)
# Override tsconfig settings with -O flags for compatibility
# Set NODE_PATH so Node can find modules in infrastructure/node_modules
# Export AWS_PROFILE for AWS SDK to use
export AWS_PROFILE=batbern-staging
export AWS_REGION=eu-central-1

cd "$PROJECT_ROOT/infrastructure"
NODE_PATH="$PROJECT_ROOT/infrastructure/node_modules" npx ts-node \
  --transpile-only \
  --skip-project \
  -O '{"module":"commonjs","moduleResolution":"node"}' \
  "$SCRIPT_DIR/sync-bootstrap-user.ts" "$@"
