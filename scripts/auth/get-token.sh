#!/bin/bash
# Authenticate with Cognito and store token locally
# Usage: ./scripts/auth/get-token.sh <environment> <email> <password> [role]
# role: organizer (default), speaker, partner
# When role is organizer (or empty), also writes the legacy {env}.json for backward compatibility.

set -e

ENVIRONMENT=${1:-"staging"}
EMAIL=${2}
PASSWORD=${3}
ROLE=${4:-""}

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

if [ -z "$EMAIL" ] || [ -z "$PASSWORD" ]; then
    echo -e "${RED}ERROR: Email and password required${NC}"
    echo "Usage: $0 <environment> <email> <password> [role]"
    echo "Example: $0 staging test@batbern.ch mypassword"
    echo "Example: $0 staging partner@batbern.ch mypassword partner"
    exit 1
fi

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}BATbern Token Retrieval${NC}"
echo -e "${BLUE}================================${NC}"
echo ""
echo "Environment: $ENVIRONMENT"
echo "Email: $EMAIL"
[ -n "$ROLE" ] && echo "Role: $ROLE"
echo ""

# Get Cognito configuration based on environment
# NOTE: Development environment deleted - using staging Cognito for both staging and development
if [ "$ENVIRONMENT" = "staging" ]; then
    AWS_PROFILE="batbern-staging"
    REGION="eu-central-1"
    USER_POOL_ID="eu-central-1_FtgfxgQRF"
    CLIENT_ID="1b53lci6qpqsmdn0u3e8s4knvv"
elif [ "$ENVIRONMENT" = "development" ]; then
    AWS_PROFILE="batbern-staging"
    REGION="eu-central-1"
    USER_POOL_ID="eu-central-1_FtgfxgQRF"
    CLIENT_ID="1b53lci6qpqsmdn0u3e8s4knvv"
elif [ "$ENVIRONMENT" = "production" ]; then
    AWS_PROFILE="batbern-prod"
    REGION="eu-central-1"
    # TODO: Add production Cognito details
    echo -e "${RED}ERROR: Production configuration not yet implemented${NC}"
    exit 1
else
    echo -e "${RED}ERROR: Unknown environment: $ENVIRONMENT${NC}"
    echo "Supported environments: development, staging, production"
    exit 1
fi

echo -e "${YELLOW}Authenticating with Cognito...${NC}"

# Authenticate with Cognito
auth_response=$(AWS_PROFILE=$AWS_PROFILE aws cognito-idp initiate-auth \
    --region $REGION \
    --auth-flow USER_PASSWORD_AUTH \
    --client-id $CLIENT_ID \
    --auth-parameters USERNAME="$EMAIL",PASSWORD="$PASSWORD" \
    --output json 2>&1)

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Authentication failed${NC}"
    echo "$auth_response"
    exit 1
fi

# Extract tokens
id_token=$(echo "$auth_response" | jq -r '.AuthenticationResult.IdToken')
access_token=$(echo "$auth_response" | jq -r '.AuthenticationResult.AccessToken')
refresh_token=$(echo "$auth_response" | jq -r '.AuthenticationResult.RefreshToken')
expires_in=$(echo "$auth_response" | jq -r '.AuthenticationResult.ExpiresIn')

if [ "$id_token" = "null" ] || [ -z "$id_token" ]; then
    echo -e "${RED}✗ Failed to extract ID token${NC}"
    echo "$auth_response"
    exit 1
fi

# Get user ID from token
user_id=$(echo "$id_token" | cut -d'.' -f2 | base64 -d 2>/dev/null | jq -r '.sub' 2>/dev/null || echo "unknown")

echo -e "${GREEN}✓ Authentication successful${NC}"
echo "User ID: $user_id"
echo "Token expires in: $expires_in seconds (~$(($expires_in / 60)) minutes)"
echo ""

# Create config directory if it doesn't exist
mkdir -p ~/.batbern

# Helper: write token JSON to a given file path
write_token_file() {
    local file_path="$1"
    local role_label="$2"
    cat > "$file_path" <<EOF
{
  "environment": "$ENVIRONMENT",
  "role": "$role_label",
  "userId": "$user_id",
  "email": "$EMAIL",
  "idToken": "$id_token",
  "accessToken": "$access_token",
  "refreshToken": "$refresh_token",
  "expiresIn": $expires_in,
  "retrievedAt": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
}
EOF
    chmod 600 "$file_path"
    echo -e "${GREEN}✓ Token stored in: $file_path${NC}"
}

# Determine storage paths based on role
if [ -z "$ROLE" ] || [ "$ROLE" = "organizer" ]; then
    # Write legacy file (backward compat) and role-specific file
    write_token_file ~/.batbern/${ENVIRONMENT}.json "organizer"
    write_token_file ~/.batbern/${ENVIRONMENT}-organizer.json "organizer"
else
    # Write only the role-specific file
    write_token_file ~/.batbern/${ENVIRONMENT}-${ROLE}.json "$ROLE"
fi

echo -e "${YELLOW}Token will expire at: $(date -v+${expires_in}S +"%Y-%m-%d %H:%M:%S")${NC}"
echo ""
echo -e "${BLUE}Token details:${NC}"
echo "ID Token (first 50 chars): ${id_token:0:50}..."
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "1. Bruno/Playwright tests will automatically use this token"
echo "2. Token will expire after ~$(($expires_in / 60)) minutes"
echo "3. Re-run this script when token expires"
