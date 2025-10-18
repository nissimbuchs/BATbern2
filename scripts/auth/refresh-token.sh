#!/bin/bash
# Auto-refresh authentication token using refresh token
# Usage: ./scripts/auth/refresh-token.sh [environment]

set -e

ENVIRONMENT=${1:-"staging"}

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Load config
local_config=~/.batbern/${ENVIRONMENT}.json
if [ ! -f "$local_config" ]; then
    echo -e "${RED}ERROR: No token config found at $local_config${NC}"
    echo "Run: ./scripts/auth/get-token.sh $ENVIRONMENT your-email your-password"
    exit 1
fi

# Check if token is expired or expiring soon (within 5 minutes)
retrieved_at=$(jq -r '.retrievedAt' "$local_config" 2>/dev/null)
expires_in=$(jq -r '.expiresIn' "$local_config" 2>/dev/null)

# Calculate expiration time
if [ -z "$retrieved_at" ] || [ "$retrieved_at" = "null" ]; then
    echo -e "${YELLOW}WARNING: Cannot determine token age, refreshing anyway${NC}"
    needs_refresh=true
else
    # Parse ISO 8601 date to epoch
    if date --version >/dev/null 2>&1; then
        # GNU date
        retrieved_epoch=$(date -d "$retrieved_at" +%s 2>/dev/null || echo 0)
    else
        # BSD date (macOS)
        retrieved_epoch=$(date -j -f "%Y-%m-%dT%H:%M:%SZ" "$retrieved_at" +%s 2>/dev/null || echo 0)
    fi

    current_epoch=$(date +%s)
    age_seconds=$((current_epoch - retrieved_epoch))
    time_left=$((expires_in - age_seconds))

    if [ $time_left -lt 300 ]; then
        echo -e "${YELLOW}Token expiring soon (${time_left}s left), refreshing...${NC}"
        needs_refresh=true
    else
        echo -e "${GREEN}✓ Token still valid (${time_left}s / ~$((time_left / 60))min remaining)${NC}"
        needs_refresh=false
    fi
fi

if [ "$needs_refresh" != "true" ]; then
    exit 0
fi

# Get refresh token
refresh_token=$(jq -r '.refreshToken' "$local_config" 2>/dev/null)
if [ -z "$refresh_token" ] || [ "$refresh_token" = "null" ]; then
    echo -e "${RED}ERROR: No refresh token found${NC}"
    echo "Run: ./scripts/auth/get-token.sh $ENVIRONMENT your-email your-password"
    exit 1
fi

# Get configuration based on environment
if [ "$ENVIRONMENT" = "staging" ]; then
    REGION="eu-central-1"
    AWS_PROFILE="batbern-staging"
    USER_POOL_NAME="batbern-staging-user-pool"
elif [ "$ENVIRONMENT" = "development" ]; then
    REGION="eu-central-1"
    AWS_PROFILE="batbern-dev"
    USER_POOL_NAME="batbern-development-user-pool"
else
    echo -e "${RED}ERROR: Environment $ENVIRONMENT not supported yet${NC}"
    exit 1
fi

CLIENT_ID=$(AWS_PROFILE=$AWS_PROFILE aws cognito-idp list-user-pool-clients \
    --user-pool-id $(AWS_PROFILE=$AWS_PROFILE aws cognito-idp list-user-pools --max-results 10 --query "UserPools[?Name==\`$USER_POOL_NAME\`].Id" --output text) \
    --max-results 10 \
    --region $REGION \
    --query 'UserPoolClients[0].ClientId' \
    --output text)

echo -e "${BLUE}Refreshing token using refresh token...${NC}"

# Use refresh token to get new tokens
auth_response=$(AWS_PROFILE=$AWS_PROFILE aws cognito-idp initiate-auth \
    --region $REGION \
    --auth-flow REFRESH_TOKEN_AUTH \
    --client-id $CLIENT_ID \
    --auth-parameters REFRESH_TOKEN="$refresh_token" \
    2>&1)

if [ $? -ne 0 ]; then
    echo -e "${RED}ERROR: Token refresh failed${NC}"
    echo "$auth_response"
    echo ""
    echo "Your refresh token may have expired. Please re-authenticate:"
    echo "./scripts/auth/get-token.sh $ENVIRONMENT your-email your-password"
    exit 1
fi

# Extract new tokens
id_token=$(echo "$auth_response" | jq -r '.AuthenticationResult.IdToken // empty')
access_token=$(echo "$auth_response" | jq -r '.AuthenticationResult.AccessToken // empty')
new_expires_in=$(echo "$auth_response" | jq -r '.AuthenticationResult.ExpiresIn // 86400')

if [ -z "$id_token" ] || [ -z "$access_token" ]; then
    echo -e "${RED}ERROR: Failed to extract tokens from response${NC}"
    exit 1
fi

# Get existing user info
email=$(jq -r '.email' "$local_config")
user_id=$(jq -r '.userId' "$local_config")

# Update config file with new tokens (keep existing refresh token)
cat > "$local_config" <<EOF
{
  "environment": "$ENVIRONMENT",
  "userId": "$user_id",
  "email": "$email",
  "idToken": "$id_token",
  "accessToken": "$access_token",
  "refreshToken": "$refresh_token",
  "expiresIn": $new_expires_in,
  "retrievedAt": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
}
EOF

chmod 600 "$local_config"

echo -e "${GREEN}✓ Token refreshed successfully${NC}"
echo "New token expires in: $new_expires_in seconds (~$((new_expires_in / 3600)) hours)"
echo "Stored in: $local_config"
