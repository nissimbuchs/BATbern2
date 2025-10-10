#!/bin/bash
# Backend Configuration Validation Script
# Validates that all required environment variables are present in .env file
#
# Usage:
#   ./scripts/config/validate-backend-config.sh

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ENV_FILE=".env"

# Required variables for backend services
REQUIRED_VARS=(
    "APP_ENVIRONMENT"
    "SPRING_PROFILES_ACTIVE"
    "AWS_REGION"
    "DB_HOST"
    "DB_PORT"
    "DB_NAME"
    "DB_USER"
    "DB_PASSWORD"
    "DATABASE_URL"
    "REDIS_HOST"
    "REDIS_PORT"
    "COGNITO_USER_POOL_ID"
    "COGNITO_CLIENT_ID"
    "API_GATEWAY_PORT"
)

echo "========================================="
echo "Backend Configuration Validation"
echo "========================================="
echo ""

# Check if .env file exists
if [ ! -f "${ENV_FILE}" ]; then
    echo -e "${RED}❌ ${ENV_FILE} file not found${NC}"
    echo ""
    echo "Run the sync script to generate configuration:"
    echo "  ./scripts/config/sync-backend-config.sh development"
    echo ""
    exit 1
fi

echo "Checking ${ENV_FILE} for required variables..."
echo ""

# Check for missing variables
MISSING=()
for var in "${REQUIRED_VARS[@]}"; do
    if ! grep -q "^${var}=" "${ENV_FILE}"; then
        MISSING+=("$var")
    fi
done

# Check for empty values
EMPTY=()
for var in "${REQUIRED_VARS[@]}"; do
    value=$(grep "^${var}=" "${ENV_FILE}" 2>/dev/null | cut -d'=' -f2-)
    if [ -z "$value" ] || [ "$value" == "NOT_DEPLOYED" ]; then
        EMPTY+=("$var")
    fi
done

# Report results
if [ ${#MISSING[@]} -ne 0 ]; then
    echo -e "${RED}❌ Missing required variables:${NC}"
    printf '  - %s\n' "${MISSING[@]}"
    echo ""
fi

if [ ${#EMPTY[@]} -ne 0 ]; then
    echo -e "${YELLOW}⚠️  Variables with empty or placeholder values:${NC}"
    printf '  - %s\n' "${EMPTY[@]}"
    echo ""
    echo "These may indicate that resources haven't been deployed yet."
    echo ""
fi

if [ ${#MISSING[@]} -eq 0 ] && [ ${#EMPTY[@]} -eq 0 ]; then
    echo -e "${GREEN}✅ All required variables present and have values${NC}"
    echo ""

    # Show environment summary
    APP_ENV=$(grep "^APP_ENVIRONMENT=" "${ENV_FILE}" | cut -d'=' -f2-)
    DB_HOST=$(grep "^DB_HOST=" "${ENV_FILE}" | cut -d'=' -f2-)
    COGNITO_POOL=$(grep "^COGNITO_USER_POOL_ID=" "${ENV_FILE}" | cut -d'=' -f2-)

    echo "Configuration Summary:"
    echo "  Environment:    ${APP_ENV}"
    echo "  Database:       ${DB_HOST}"
    echo "  Cognito Pool:   ${COGNITO_POOL}"
    echo ""
    echo "Configuration is valid and ready for use."
    exit 0
fi

# If there are issues, provide guidance
if [ ${#MISSING[@]} -gt 0 ] || [ ${#EMPTY[@]} -gt 0 ]; then
    echo "To fix these issues:"
    echo "  1. Ensure CDK stacks are deployed: npx cdk deploy --all"
    echo "  2. Re-run the sync script: ./scripts/config/sync-backend-config.sh"
    echo ""
    exit 1
fi
