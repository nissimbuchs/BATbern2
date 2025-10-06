#!/bin/bash
# One-time setup: Create GitHub OIDC provider in AWS
# This allows GitHub Actions to authenticate with AWS without long-lived credentials

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo "=================================================="
echo "  GitHub Actions OIDC Provider Setup"
echo "=================================================="
echo ""

# Check AWS CLI
if ! command -v aws &> /dev/null; then
    echo -e "${RED}✗ AWS CLI not found${NC}"
    exit 1
fi

# Verify credentials
if ! aws sts get-caller-identity &> /dev/null; then
    echo -e "${RED}✗ AWS credentials not configured${NC}"
    exit 1
fi

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo -e "${GREEN}✓ AWS Account: $ACCOUNT_ID${NC}"
echo ""

# Check if provider already exists
echo "Checking for existing OIDC provider..."
if aws iam get-open-id-connect-provider \
  --open-id-connect-provider-arn "arn:aws:iam::${ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com" \
  &> /dev/null; then
    echo -e "${YELLOW}⚠ OIDC provider already exists${NC}"
    echo ""
    echo "Provider details:"
    aws iam get-open-id-connect-provider \
      --open-id-connect-provider-arn "arn:aws:iam::${ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com" \
      --query '{ClientIDList:ClientIDList,ThumbprintList:ThumbprintList,CreateDate:CreateDate}' \
      --output table
    echo ""
    echo -e "${GREEN}✓ Setup complete - OIDC provider is ready${NC}"
    exit 0
fi

# Create OIDC provider
echo "Creating GitHub OIDC provider..."
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1 \
  --tags Key=Project,Value=BATbern Key=Purpose,Value=GitHub-Actions-OIDC

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ OIDC provider created successfully${NC}"
else
    echo -e "${RED}✗ Failed to create OIDC provider${NC}"
    exit 1
fi

echo ""
echo "=================================================="
echo "  Next Steps"
echo "=================================================="
echo ""
echo "1. Deploy the CICD stack with CDK:"
echo "   cd infrastructure"
echo "   npm run deploy:dev -- --context githubRepository=nissimbuchs/BATbern2"
echo ""
echo "2. The stack will create IAM role: batbern-development-github-actions-role"
echo ""
echo "3. Update GitHub workflows to use OIDC:"
echo "   Replace aws-actions/configure-aws-credentials with:"
echo ""
echo "   - name: Configure AWS credentials"
echo "     uses: aws-actions/configure-aws-credentials@v4"
echo "     with:"
echo "       role-to-assume: arn:aws:iam::${ACCOUNT_ID}:role/batbern-ENV-github-actions-role"
echo "       aws-region: eu-central-1"
echo ""
