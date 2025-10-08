#!/bin/bash
# Backend Configuration Sync Script
# Generates environment-specific .env files from CDK outputs
#
# Usage:
#   ./scripts/config/sync-backend-config.sh [environment]
#
# Environment: development (default), staging, production

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
ENVIRONMENT="${1:-development}"
TEMPLATE_FILE="config/templates/backend.env.template"
OUTPUT_FILE=".env"

# Validate environment
case "${ENVIRONMENT}" in
    development|staging|production)
        ;;
    *)
        echo -e "${RED}Error: Invalid environment '${ENVIRONMENT}'${NC}"
        echo "Usage: $0 [development|staging|production]"
        exit 1
        ;;
esac

# Set AWS profile and region based on environment
case "${ENVIRONMENT}" in
    development)
        AWS_PROFILE="${AWS_PROFILE:-batbern-dev}"
        ;;
    staging)
        AWS_PROFILE="${AWS_PROFILE:-batbern-staging}"
        ;;
    production)
        AWS_PROFILE="${AWS_PROFILE:-batbern-prod}"
        ;;
esac

AWS_REGION="${AWS_REGION:-eu-central-1}"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Backend Configuration Sync${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "Environment:  ${GREEN}${ENVIRONMENT}${NC}"
echo -e "AWS Profile:  ${GREEN}${AWS_PROFILE}${NC}"
echo -e "AWS Region:   ${GREEN}${AWS_REGION}${NC}"
echo ""

# Check prerequisites
echo "Checking prerequisites..."

if ! command -v aws &> /dev/null; then
    echo -e "${RED}Error: AWS CLI is not installed${NC}"
    echo "Install: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html"
    exit 1
fi

if ! command -v jq &> /dev/null; then
    echo -e "${RED}Error: jq is not installed${NC}"
    echo "Install: brew install jq (macOS) or apt-get install jq (Linux)"
    exit 1
fi

if [ ! -f "${TEMPLATE_FILE}" ]; then
    echo -e "${RED}Error: Template file not found: ${TEMPLATE_FILE}${NC}"
    exit 1
fi

echo -e "${GREEN}✓${NC} Prerequisites check passed"
echo ""

# Verify AWS credentials
echo "Verifying AWS credentials (profile: ${AWS_PROFILE})..."
if ! aws sts get-caller-identity --profile ${AWS_PROFILE} --region ${AWS_REGION} &> /dev/null; then
    echo -e "${RED}Error: AWS credentials not configured or invalid${NC}"
    echo "Configure: aws configure --profile ${AWS_PROFILE}"
    exit 1
fi
echo -e "${GREEN}✓${NC} AWS credentials verified"
echo ""

# Function to get stack output
get_stack_output() {
    local stack_name=$1
    local output_key=$2
    local value=$(aws cloudformation describe-stacks \
        --stack-name "${stack_name}" \
        --profile ${AWS_PROFILE} \
        --region ${AWS_REGION} \
        2>/dev/null | jq -r ".Stacks[0].Outputs[] | select(.OutputKey==\"${output_key}\") | .OutputValue")

    if [ -z "$value" ] || [ "$value" == "null" ]; then
        return 1
    fi
    echo "$value"
}

# Fetch Database Stack outputs
echo "Fetching Database configuration from AWS..."
DB_STACK="BATbern-${ENVIRONMENT}-Database"

DB_ENDPOINT=$(get_stack_output "${DB_STACK}" "DatabaseEndpoint")
if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Could not fetch database endpoint from stack ${DB_STACK}${NC}"
    echo "Make sure the database stack is deployed: cd infrastructure && npx cdk deploy ${DB_STACK}"
    exit 1
fi

DB_PORT=$(get_stack_output "${DB_STACK}" "DatabasePort")
DB_NAME=$(get_stack_output "${DB_STACK}" "DatabaseName")
DB_SECRET_NAME=$(get_stack_output "${DB_STACK}" "DatabaseSecretName")

echo -e "${GREEN}✓${NC} Database endpoint: ${DB_ENDPOINT}"
echo -e "${GREEN}✓${NC} Database port: ${DB_PORT}"
echo -e "${GREEN}✓${NC} Database name: ${DB_NAME}"
echo ""

# Fetch Database credentials from Secrets Manager
echo "Fetching database credentials from AWS Secrets Manager..."
DB_SECRET=$(aws secretsmanager get-secret-value \
    --secret-id "${DB_SECRET_NAME}" \
    --profile ${AWS_PROFILE} \
    --region ${AWS_REGION} \
    2>/dev/null | jq -r '.SecretString')

if [ -z "$DB_SECRET" ] || [ "$DB_SECRET" == "null" ]; then
    echo -e "${RED}Error: Could not fetch database credentials from Secrets Manager${NC}"
    exit 1
fi

DB_USER=$(echo "$DB_SECRET" | jq -r '.username')
DB_PASSWORD=$(echo "$DB_SECRET" | jq -r '.password')

echo -e "${GREEN}✓${NC} Database credentials retrieved"
echo ""

# Fetch Cognito Stack outputs
echo "Fetching Cognito configuration from AWS..."
COGNITO_STACK="BATbern-${ENVIRONMENT}-Cognito"

COGNITO_USER_POOL_ID=$(get_stack_output "${COGNITO_STACK}" "UserPoolId")
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}Warning: Could not fetch Cognito User Pool ID from stack ${COGNITO_STACK}${NC}"
    echo "Deploy the Cognito stack: cd infrastructure && npm run deploy:${ENVIRONMENT}"
    COGNITO_USER_POOL_ID="NOT_DEPLOYED"
    COGNITO_CLIENT_ID="NOT_DEPLOYED"
    COGNITO_DOMAIN_URL="NOT_DEPLOYED"
else
    COGNITO_CLIENT_ID=$(get_stack_output "${COGNITO_STACK}" "UserPoolClientId")
    COGNITO_DOMAIN_URL=$(get_stack_output "${COGNITO_STACK}" "UserPoolDomainUrl")
    echo -e "${GREEN}✓${NC} Cognito User Pool ID: ${COGNITO_USER_POOL_ID}"
    echo -e "${GREEN}✓${NC} Cognito Client ID: ${COGNITO_CLIENT_ID}"
fi
echo ""

# Environment-specific configuration
if [ "$ENVIRONMENT" == "development" ]; then
    # Development uses local Redis in Docker
    REDIS_HOST="redis"
    REDIS_PORT="6379"
    REDIS_PASSWORD=""
    SPRING_PROFILE="local"
    LOG_LEVEL="DEBUG"
else
    # Staging/Production use ElastiCache
    CACHE_ENDPOINT=$(get_stack_output "${DB_STACK}" "CacheEndpoint") || REDIS_HOST="NOT_DEPLOYED"
    REDIS_HOST="${CACHE_ENDPOINT:-NOT_DEPLOYED}"
    REDIS_PORT="6379"
    REDIS_PASSWORD=""
    SPRING_PROFILE="${ENVIRONMENT}"
    LOG_LEVEL="INFO"
fi

# Get AWS Account ID
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --profile ${AWS_PROFILE} --query Account --output text)

# Generate .env file from template
echo "Generating ${OUTPUT_FILE} from template..."

cat "${TEMPLATE_FILE}" | \
    sed "s|{{ENVIRONMENT}}|${ENVIRONMENT}|g" | \
    sed "s|{{GENERATION_TIMESTAMP}}|$(date)|g" | \
    sed "s|{{SPRING_PROFILE}}|${SPRING_PROFILE}|g" | \
    sed "s|{{LOG_LEVEL}}|${LOG_LEVEL}|g" | \
    sed "s|{{AWS_REGION}}|${AWS_REGION}|g" | \
    sed "s|{{AWS_ACCOUNT_ID}}|${AWS_ACCOUNT_ID}|g" | \
    sed "s|{{DB_ENDPOINT}}|${DB_ENDPOINT}|g" | \
    sed "s|{{DB_PORT}}|${DB_PORT}|g" | \
    sed "s|{{DB_NAME}}|${DB_NAME}|g" | \
    sed "s|{{DB_USER}}|${DB_USER}|g" | \
    sed "s|{{DB_PASSWORD}}|${DB_PASSWORD}|g" | \
    sed "s|{{REDIS_HOST}}|${REDIS_HOST}|g" | \
    sed "s|{{REDIS_PORT}}|${REDIS_PORT}|g" | \
    sed "s|{{REDIS_PASSWORD}}|${REDIS_PASSWORD}|g" | \
    sed "s|{{COGNITO_USER_POOL_ID}}|${COGNITO_USER_POOL_ID}|g" | \
    sed "s|{{COGNITO_CLIENT_ID}}|${COGNITO_CLIENT_ID}|g" | \
    sed "s|{{COGNITO_DOMAIN_URL}}|${COGNITO_DOMAIN_URL}|g" | \
    sed "s|{{ENABLE_COGNITO_AUTH}}|true|g" \
    > "${OUTPUT_FILE}"

echo -e "${GREEN}✓${NC} ${OUTPUT_FILE} file generated successfully"
echo ""

# Verify .env file was created
if [ ! -f "${OUTPUT_FILE}" ]; then
    echo -e "${RED}Error: Failed to create ${OUTPUT_FILE} file${NC}"
    exit 1
fi

# Print summary
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Sync Complete!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "Configuration file:  ${OUTPUT_FILE}"
echo "Environment:         ${ENVIRONMENT}"
echo ""
echo "Database:            ${DB_ENDPOINT}"
echo "Cognito Pool:        ${COGNITO_USER_POOL_ID}"
echo "Redis:               ${REDIS_HOST}"
echo ""
echo "Next steps:"
echo "  1. Review the generated ${OUTPUT_FILE} file"
echo "  2. Start Docker services: docker-compose up -d"
echo "  3. View logs: docker-compose logs -f"
echo ""
echo -e "${YELLOW}Note: The ${OUTPUT_FILE} file contains sensitive credentials.${NC}"
echo -e "${YELLOW}Make sure it's listed in .gitignore${NC}"
echo ""
