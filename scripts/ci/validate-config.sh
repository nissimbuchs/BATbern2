#!/bin/bash
# Validate configuration compatibility between environments
# Usage: ./validate-config.sh <source_env> <target_env>

set -e

SOURCE_ENV=$1
TARGET_ENV=$2

if [ -z "$SOURCE_ENV" ] || [ -z "$TARGET_ENV" ]; then
    echo "Usage: $0 <source_env> <target_env>"
    exit 1
fi

echo "=========================================="
echo "Validating Configuration Compatibility"
echo "Source: $SOURCE_ENV"
echo "Target: $TARGET_ENV"
echo "=========================================="

# Required parameters that must exist
REQUIRED_PARAMS=(
    "database/connection-string"
    "database/pool-size"
    "redis/cluster-endpoint"
    "api-gateway/cors-origins"
)

# Get source environment parameters
SOURCE_PARAMS=$(aws ssm get-parameters-by-path \
    --path "/batbern/$SOURCE_ENV/" \
    --recursive \
    --query 'Parameters[].Name' \
    --output json)

echo "Checking required parameters..."
for param in "${REQUIRED_PARAMS[@]}"; do
    if ! echo "$SOURCE_PARAMS" | jq -e ".[] | select(contains(\"$param\"))" > /dev/null; then
        echo "❌ ERROR: Required parameter $param not found in $SOURCE_ENV"
        exit 1
    fi
    echo "✓ Found: $param"
done

echo ""
echo "=========================================="
echo "✅ Configuration validation passed!"
echo "=========================================="
