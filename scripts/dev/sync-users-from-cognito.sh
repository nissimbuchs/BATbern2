#!/bin/bash
# Sync users from Cognito to Database
# Story 1.2.5: Manual user reconciliation for dev testing
#
# This script triggers the reconciliation API to sync Cognito users to the database.
# Useful for testing password reset flows when Lambda triggers aren't deployed.

set -e

export AWS_PROFILE=batbern-dev
export AWS_REGION=eu-central-1

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "🔄 Syncing users from Cognito to Database..."
echo ""

# Get Cognito User Pool ID
USER_POOL_ID=$(aws cloudformation describe-stacks \
  --stack-name BATbern-development-Cognito \
  --query 'Stacks[0].Outputs[?OutputKey==`UserPoolId`].OutputValue' \
  --output text \
  --region $AWS_REGION 2>/dev/null)

if [ -z "$USER_POOL_ID" ] || [ "$USER_POOL_ID" == "None" ]; then
  echo -e "${RED}❌ Error: Cognito User Pool not found${NC}"
  echo ""
  echo "Deploy Cognito stack first:"
  echo "  cd infrastructure"
  echo "  npm run deploy:dev"
  exit 1
fi

echo -e "${BLUE}📋 Configuration:${NC}"
echo "   User Pool: $USER_POOL_ID"
echo "   API URL: http://localhost:8081"
echo "   Profile: $AWS_PROFILE"
echo "   Region: $AWS_REGION"
echo ""

# Check if service is running
echo -e "${BLUE}🔍 Checking if company-user-management-service is running...${NC}"
if ! curl -s http://localhost:8081/actuator/health > /dev/null 2>&1; then
  echo -e "${RED}❌ Error: company-user-management-service is not running${NC}"
  echo ""
  echo "Start Docker services first:"
  echo "  docker compose up -d"
  exit 1
fi

echo -e "${GREEN}✅ Service is running${NC}"
echo ""

# Get credentials for organizer user (nissim@buchs.be)
# First check if we can get the user
ORGANIZER_EMAIL="nissim@buchs.be"
echo -e "${BLUE}🔑 Getting authentication token for organizer user...${NC}"

# We need to use admin API to get user info
ADMIN_USER=$(aws cognito-idp admin-get-user \
  --user-pool-id $USER_POOL_ID \
  --username $ORGANIZER_EMAIL \
  --region $AWS_REGION 2>/dev/null || echo "")

if [ -z "$ADMIN_USER" ]; then
  echo -e "${YELLOW}⚠️  Organizer user not found. Creating bootstrap user...${NC}"
  echo ""
  echo "Run deployment to create bootstrap organizer:"
  echo "  cd infrastructure && npm run deploy:dev"
  exit 1
fi

echo -e "${YELLOW}⚠️  Note: You need a valid JWT token with ORGANIZER role${NC}"
echo ""
echo "To get a token:"
echo "  1. Login to web app: http://localhost:3000"
echo "  2. Use organizer credentials (nissim@buchs.be)"
echo "  3. Copy JWT from browser DevTools > Application > Local Storage"
echo "  4. Set JWT_TOKEN environment variable"
echo ""
echo "OR run this script with JWT_TOKEN:"
echo "  JWT_TOKEN='your-token-here' $0"
echo ""

# Check if JWT_TOKEN is provided
if [ -z "$JWT_TOKEN" ]; then
  echo -e "${RED}❌ Error: JWT_TOKEN environment variable not set${NC}"
  echo ""
  exit 1
fi

echo -e "${GREEN}✅ JWT token provided${NC}"
echo ""

# Check sync status first
echo -e "${BLUE}📊 Checking current sync status...${NC}"
echo ""

SYNC_STATUS=$(curl -s -X GET http://localhost:8081/api/v1/users/admin/sync-status \
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

RECONCILIATION_RESULT=$(curl -s -X POST http://localhost:8081/api/v1/users/admin/reconcile \
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
