#!/bin/bash
# Update .env file with new Cognito credentials after deployment
# Usage: ./scripts/dev/update-cognito-env.sh [environment]

set -e

# Default to development if no environment specified
ENV=${1:-development}

echo "🔍 Fetching Cognito configuration for environment: $ENV"

# Get AWS region
REGION="eu-central-1"

# Get User Pool ID by name
echo "📋 Looking for user pool: batbern-${ENV}-user-pool"
USER_POOL_ID=$(aws cognito-idp list-user-pools \
  --max-results 50 \
  --region $REGION \
  --query "UserPools[?Name=='batbern-${ENV}-user-pool'].Id | [0]" \
  --output text)

if [ "$USER_POOL_ID" == "None" ] || [ -z "$USER_POOL_ID" ]; then
  echo "❌ Error: User pool 'batbern-${ENV}-user-pool' not found"
  echo "Please deploy the Cognito stack first:"
  echo "  cd infrastructure && npm run deploy:${ENV}"
  exit 1
fi

echo "✅ Found User Pool ID: $USER_POOL_ID"

# Get User Pool Client ID
echo "📋 Fetching user pool client..."
CLIENT_ID=$(aws cognito-idp list-user-pool-clients \
  --user-pool-id $USER_POOL_ID \
  --region $REGION \
  --query "UserPoolClients[?ClientName=='batbern-${ENV}-web-client'].ClientId | [0]" \
  --output text)

if [ "$CLIENT_ID" == "None" ] || [ -z "$CLIENT_ID" ]; then
  echo "❌ Error: User pool client 'batbern-${ENV}-web-client' not found"
  exit 1
fi

echo "✅ Found Client ID: $CLIENT_ID"

# Get Cognito Domain
COGNITO_DOMAIN="batbern-${ENV}-auth.auth.${REGION}.amazoncognito.com"

# Update .env file
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ENV_FILE="$SCRIPT_DIR/../../.env"

echo ""
echo "📝 Updating $ENV_FILE..."

# Update backend Cognito config
sed -i.bak "s|COGNITO_USER_POOL_ID=.*|COGNITO_USER_POOL_ID=$USER_POOL_ID|g" $ENV_FILE
sed -i.bak "s|COGNITO_CLIENT_ID=.*|COGNITO_CLIENT_ID=$CLIENT_ID|g" $ENV_FILE
sed -i.bak "s|COGNITO_DOMAIN_URL=.*|COGNITO_DOMAIN_URL=https://$COGNITO_DOMAIN|g" $ENV_FILE

# Update frontend Cognito config
sed -i.bak "s|VITE_COGNITO_USER_POOL_ID=.*|VITE_COGNITO_USER_POOL_ID=$USER_POOL_ID|g" $ENV_FILE
sed -i.bak "s|VITE_COGNITO_WEB_CLIENT_ID=.*|VITE_COGNITO_WEB_CLIENT_ID=$CLIENT_ID|g" $ENV_FILE
sed -i.bak "s|VITE_COGNITO_DOMAIN=.*|VITE_COGNITO_DOMAIN=$COGNITO_DOMAIN|g" $ENV_FILE

# Remove backup file
rm -f ${ENV_FILE}.bak

echo "✅ Updated $ENV_FILE with new Cognito credentials"
echo ""
echo "📋 Configuration Summary:"
echo "  Environment:     $ENV"
echo "  User Pool ID:    $USER_POOL_ID"
echo "  Client ID:       $CLIENT_ID"
echo "  Cognito Domain:  $COGNITO_DOMAIN"
echo ""
echo "🎉 Done! Restart your services to use the new configuration."
