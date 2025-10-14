#!/bin/bash

# Script to get a JWT token from the staging Cognito User Pool
# Usage: ./get-staging-token.sh <email> <password>

set -e

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <email> <password>"
    echo "Example: $0 user@example.com MyPassword123"
    exit 1
fi

EMAIL="$1"
PASSWORD="$2"
USER_POOL_ID="eu-central-1_FtgfxgQRF"
CLIENT_ID="1b53lci6qpqsmdn0u3e8s4knvv"
REGION="eu-central-1"

echo "🔐 Getting JWT token from staging Cognito..."
echo "User Pool: $USER_POOL_ID"
echo "Email: $EMAIL"
echo ""

# Authenticate and get tokens
RESPONSE=$(AWS_PROFILE=batbern-staging aws cognito-idp initiate-auth \
    --auth-flow USER_PASSWORD_AUTH \
    --client-id "$CLIENT_ID" \
    --auth-parameters "USERNAME=$EMAIL,PASSWORD=$PASSWORD" \
    --region "$REGION" \
    2>&1)

# Check if authentication was successful
if echo "$RESPONSE" | grep -q "NotAuthorizedException\|UserNotFoundException"; then
    echo "❌ Authentication failed!"
    echo "Error: $(echo "$RESPONSE" | grep -o '"message":"[^"]*"' | cut -d'"' -f4)"
    echo ""
    echo "Possible issues:"
    echo "  - User does not exist in staging Cognito pool"
    echo "  - Incorrect password"
    echo "  - User needs to be created first"
    exit 1
fi

# Extract ID token
ID_TOKEN=$(echo "$RESPONSE" | grep -o '"IdToken":"[^"]*"' | cut -d'"' -f4)

if [ -z "$ID_TOKEN" ]; then
    echo "❌ Failed to extract ID token"
    echo "Response: $RESPONSE"
    exit 1
fi

echo "✅ Token obtained successfully!"
echo ""
echo "==================== COPY THIS TOKEN ===================="
echo "$ID_TOKEN"
echo "========================================================="
echo ""
echo "📋 Next steps:"
echo "  1. Copy the token above"
echo "  2. Open: bruno-tests/event-management-service/environments/staging.bru"
echo "  3. Replace the authToken value with this new token"
echo "  4. Save and test in Bruno"
echo ""

# Decode and show expiry
PAYLOAD=$(echo "$ID_TOKEN" | cut -d. -f2)
case $((${#PAYLOAD} % 4)) in
  2) PAYLOAD="${PAYLOAD}==" ;;
  3) PAYLOAD="${PAYLOAD}=" ;;
esac
EXP=$(echo "$PAYLOAD" | base64 -d 2>/dev/null | grep -o '"exp":[0-9]*' | cut -d: -f2)
EXP_DATE=$(date -r "$EXP" 2>/dev/null || date -d "@$EXP" 2>/dev/null)
echo "⏰ Token expires: $EXP_DATE"
echo ""
