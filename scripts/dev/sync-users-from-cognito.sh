#!/bin/bash
# Sync users from Cognito to Local Database
# Story 1.2.5: Manual user reconciliation for dev testing
#
# This script syncs users from STAGING Cognito to local PostgreSQL.
# The local database mirrors staging Cognito users for development.

set -e

# Always use staging (local dev mirrors staging Cognito)
ENVIRONMENT="staging"
JWT_TOKEN=${1:-""}

export AWS_PROFILE=batbern-staging
export AWS_REGION=eu-central-1

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}Sync Users from Staging Cognito${NC}"
echo -e "${BLUE}================================${NC}"
echo ""
echo "Source: Staging Cognito (batbern-staging)"
echo "Target: Local PostgreSQL (localhost:5432)"
echo ""

# Try to load token from local config if not provided
if [ -z "$JWT_TOKEN" ]; then
    echo -e "${BLUE}🔑 Loading authentication token...${NC}"

    # Auto-refresh token if expired
    if [ -f "./scripts/auth/refresh-token.sh" ]; then
        ./scripts/auth/refresh-token.sh staging || true
    fi

    local_config=~/.batbern/staging.json
    if [ -f "$local_config" ]; then
        echo -e "${BLUE}Loading auth token from: $local_config${NC}"
        # Use ID token (contains 'aud' claim required by JWT validator)
        JWT_TOKEN=$(jq -r '.idToken' "$local_config" 2>/dev/null)

        if [ "$JWT_TOKEN" = "null" ] || [ -z "$JWT_TOKEN" ]; then
            echo -e "${RED}❌ Error: Failed to load token from local config${NC}"
            echo ""
            echo "Get a staging token first:"
            echo "  ./scripts/auth/get-token.sh staging your-email your-password"
            echo ""
            echo "Or provide JWT_TOKEN manually:"
            echo "  JWT_TOKEN='your-token' $0"
            exit 1
        else
            # Check if token is expired
            retrieved_at=$(jq -r '.retrievedAt' "$local_config" 2>/dev/null)
            expires_in=$(jq -r '.expiresIn' "$local_config" 2>/dev/null)
            echo -e "${GREEN}✓ Token loaded successfully${NC}"
            echo "Retrieved at: $retrieved_at"
            echo "Expires in: ~$(($expires_in / 60)) minutes from retrieval"
        fi
    else
        echo -e "${RED}❌ Error: No auth token found${NC}"
        echo ""
        echo "Get a staging token first:"
        echo "  ./scripts/auth/get-token.sh staging your-email your-password"
        echo ""
        echo "Or provide JWT_TOKEN manually:"
        echo "  JWT_TOKEN='your-token' $0"
        exit 1
    fi
fi
echo ""

# Get Staging Cognito User Pool ID
USER_POOL_ID=$(aws cloudformation describe-stacks \
  --stack-name BATbern-staging-Cognito \
  --query 'Stacks[0].Outputs[?OutputKey==`UserPoolId`].OutputValue' \
  --output text \
  --region $AWS_REGION 2>/dev/null)

if [ -z "$USER_POOL_ID" ] || [ "$USER_POOL_ID" == "None" ]; then
  echo -e "${RED}❌ Error: Staging Cognito User Pool not found${NC}"
  echo ""
  echo "Ensure staging Cognito stack is deployed:"
  echo "  cd infrastructure"
  echo "  npm run deploy:staging"
  exit 1
fi

echo -e "${BLUE}📋 Configuration:${NC}"
echo "   User Pool: $USER_POOL_ID (staging)"
echo "   Local API Gateway: http://localhost:8000"
echo "   AWS Profile: $AWS_PROFILE"
echo "   AWS Region: $AWS_REGION"
echo ""

# Check if API Gateway is running
echo -e "${BLUE}🔍 Checking if API Gateway is running...${NC}"
if ! curl -s http://localhost:8000/actuator/health > /dev/null 2>&1; then
  echo -e "${RED}❌ Error: API Gateway is not running on port 8000${NC}"
  echo ""
  echo "Start services first:"
  echo "  make dev-native-up"
  echo "  OR"
  echo "  docker compose up -d"
  exit 1
fi

echo -e "${GREEN}✅ API Gateway is running${NC}"
echo ""

# Check sync status first
echo -e "${BLUE}📊 Checking current sync status...${NC}"
echo ""

SYNC_STATUS=$(curl -s -X GET http://localhost:8000/api/v1/users/admin/sync-status \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json")

if [ $? -ne 0 ]; then
  echo -e "${RED}❌ Error: Failed to check sync status${NC}"
  exit 1
fi

# Parse sync status
COGNITO_COUNT=$(echo $SYNC_STATUS | jq -r '.cognitoUserCount // 0')
DATABASE_COUNT=$(echo $SYNC_STATUS | jq -r '.databaseUserCount // 0')
MISSING_COUNT=$(echo $SYNC_STATUS | jq -r '.missingInDatabase // 0')
ORPHANED_COUNT=$(echo $SYNC_STATUS | jq -r '.orphanedInDatabase // 0')
IN_SYNC=$(echo $SYNC_STATUS | jq -r '.inSync // false')
MESSAGE=$(echo $SYNC_STATUS | jq -r '.message // "Unknown"')

echo "   Cognito users: $COGNITO_COUNT"
echo "   Database users: $DATABASE_COUNT"
echo "   Missing in DB: $MISSING_COUNT"
echo "   Orphaned in DB: $ORPHANED_COUNT"
echo "   Status: $MESSAGE"
echo ""

if [ "$IN_SYNC" == "true" ]; then
  echo -e "${GREEN}✅ All users are already in sync!${NC}"
  echo ""
  exit 0
fi

# Trigger reconciliation
echo -e "${BLUE}🔄 Triggering reconciliation...${NC}"
echo ""

RECONCILIATION_RESULT=$(curl -s -X POST http://localhost:8000/api/v1/users/admin/reconcile \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json")

if [ $? -ne 0 ]; then
  echo -e "${RED}❌ Error: Failed to trigger reconciliation${NC}"
  exit 1
fi

# Parse reconciliation result
CREATED=$(echo $RECONCILIATION_RESULT | jq -r '.missingUsersCreated // 0')
DEACTIVATED=$(echo $RECONCILIATION_RESULT | jq -r '.orphanedUsersDeactivated // 0')
DURATION=$(echo $RECONCILIATION_RESULT | jq -r '.durationMs // 0')
SUCCESS=$(echo $RECONCILIATION_RESULT | jq -r '.success // false')
RESULT_MESSAGE=$(echo $RECONCILIATION_RESULT | jq -r '.message // "Unknown"')
ERRORS=$(echo $RECONCILIATION_RESULT | jq -r '.errors // []')

echo -e "${GREEN}✅ Reconciliation complete!${NC}"
echo ""
echo "📊 Results:"
echo "   Users created: $CREATED"
echo "   Users deactivated: $DEACTIVATED"
echo "   Duration: ${DURATION}ms"
echo "   Status: $RESULT_MESSAGE"
echo ""

if [ "$SUCCESS" != "true" ]; then
  echo -e "${YELLOW}⚠️  Reconciliation had errors:${NC}"
  echo "$ERRORS" | jq -r '.[]'
  echo ""
fi

echo -e "${GREEN}✅ Sync complete! You can now test password reset flows.${NC}"
echo ""
