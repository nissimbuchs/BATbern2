#!/bin/bash
# Automated GitHub configuration using gh CLI
# Sets up: secrets, environments, and environment protection rules

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "=================================================="
echo "  GitHub Repository CI/CD Configuration"
echo "=================================================="
echo ""

# Check gh CLI
if ! command -v gh &> /dev/null; then
    echo -e "${RED}✗ GitHub CLI (gh) not found${NC}"
    echo "Install: https://cli.github.com/"
    exit 1
fi

# Check gh auth
if ! gh auth status &> /dev/null; then
    echo -e "${RED}✗ Not authenticated with GitHub${NC}"
    echo "Run: gh auth login"
    exit 1
fi

echo -e "${GREEN}✓ GitHub CLI authenticated${NC}"
echo ""

# Get repository info
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
echo -e "${BLUE}Repository: $REPO${NC}"
echo ""

# ═══════════════════════════════════════════════════════════
# COLLECT AWS CREDENTIALS
# ═══════════════════════════════════════════════════════════

echo "=================================================="
echo "  Step 1: AWS Credentials"
echo "=================================================="
echo ""

# Try to get from AWS CLI
if command -v aws &> /dev/null && aws sts get-caller-identity &> /dev/null; then
    AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
    echo -e "${GREEN}✓ Detected AWS Account ID: $AWS_ACCOUNT_ID${NC}"

    read -p "Use credentials from current AWS CLI profile? (y/n): " use_current
    if [[ "$use_current" == "y" ]]; then
        # Get access key from current profile (if available)
        AWS_PROFILE=${AWS_PROFILE:-default}
        echo "Using AWS profile: $AWS_PROFILE"

        read -p "Enter AWS_ACCESS_KEY_ID: " AWS_ACCESS_KEY_ID
        read -sp "Enter AWS_SECRET_ACCESS_KEY: " AWS_SECRET_ACCESS_KEY
        echo ""
    fi
else
    echo "Enter AWS credentials for GitHub Actions:"
    read -p "AWS Account ID: " AWS_ACCOUNT_ID
    read -p "AWS_ACCESS_KEY_ID: " AWS_ACCESS_KEY_ID
    read -sp "AWS_SECRET_ACCESS_KEY: " AWS_SECRET_ACCESS_KEY
    echo ""
fi

# ═══════════════════════════════════════════════════════════
# DATABASE CREDENTIALS - MANAGED BY CDK
# ═══════════════════════════════════════════════════════════

echo ""
echo "=================================================="
echo "  Step 2: Database Credentials"
echo "=================================================="
echo ""
echo -e "${GREEN}✓ Databases managed by CDK - no manual setup needed${NC}"
echo ""
echo "Database credentials are automatically:"
echo "  • Created by CDK when deploying Database stack"
echo "  • Stored in AWS Secrets Manager"
echo "  • Retrieved by GitHub Actions workflows at runtime"
echo ""
echo "No GitHub secrets required for databases!"
echo ""

# ═══════════════════════════════════════════════════════════
# COLLECT EXTERNAL SERVICE TOKENS
# ═══════════════════════════════════════════════════════════

echo ""
echo "=================================================="
echo "  Step 3: External Services (Optional)"
echo "=================================================="
echo ""
echo "These are optional but recommended for quality gates"
echo "(Press Enter to skip)"
echo ""

read -p "Snyk Token (from app.snyk.io): " SNYK_TOKEN
read -p "SonarQube Host URL: " SONAR_HOST_URL
read -p "SonarQube Token: " SONAR_TOKEN
read -p "Codecov Token (from codecov.io): " CODECOV_TOKEN
read -p "Slack Webhook URL: " SLACK_WEBHOOK_URL

# ═══════════════════════════════════════════════════════════
# SET REPOSITORY SECRETS
# ═══════════════════════════════════════════════════════════

echo ""
echo "=================================================="
echo "  Step 4: Setting Repository Secrets"
echo "=================================================="
echo ""

set_secret() {
    local name=$1
    local value=$2

    if [ -n "$value" ]; then
        echo -n "Setting $name... "
        if gh secret set "$name" --body "$value" --repo "$REPO" 2>/dev/null; then
            echo -e "${GREEN}✓${NC}"
        else
            echo -e "${RED}✗${NC}"
        fi
    fi
}

# AWS Credentials
set_secret "AWS_ACCOUNT_ID" "$AWS_ACCOUNT_ID"
set_secret "AWS_ACCESS_KEY_ID" "$AWS_ACCESS_KEY_ID"
set_secret "AWS_SECRET_ACCESS_KEY" "$AWS_SECRET_ACCESS_KEY"

# Database credentials - NOT NEEDED (managed by CDK)
echo "Skipping database secrets (managed by CDK/Secrets Manager)"

# External Services
set_secret "SNYK_TOKEN" "$SNYK_TOKEN"
set_secret "SONAR_HOST_URL" "$SONAR_HOST_URL"
set_secret "SONAR_TOKEN" "$SONAR_TOKEN"
set_secret "CODECOV_TOKEN" "$CODECOV_TOKEN"
set_secret "SLACK_WEBHOOK_URL" "$SLACK_WEBHOOK_URL"

# ═══════════════════════════════════════════════════════════
# CREATE ENVIRONMENTS
# ═══════════════════════════════════════════════════════════

echo ""
echo "=================================================="
echo "  Step 5: Creating GitHub Environments"
echo "=================================================="
echo ""

# Note: gh CLI doesn't directly support environment creation
# We need to use GitHub API

create_environment() {
    local env_name=$1

    echo -n "Creating environment: $env_name... "

    # Create environment via API
    response=$(gh api \
        --method PUT \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        "/repos/$REPO/environments/$env_name" \
        2>&1)

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓${NC}"
        return 0
    else
        # Environment might already exist
        if echo "$response" | grep -q "already exists"; then
            echo -e "${YELLOW}EXISTS${NC}"
            return 0
        else
            echo -e "${RED}✗${NC}"
            return 1
        fi
    fi
}

create_environment "staging"
create_environment "production"

# ═══════════════════════════════════════════════════════════
# CONFIGURE ENVIRONMENT PROTECTION
# ═══════════════════════════════════════════════════════════

echo ""
echo "=================================================="
echo "  Step 6: Environment Protection Rules"
echo "=================================================="
echo ""
echo "⚠️  Manual step required:"
echo ""
echo "1. Go to: https://github.com/$REPO/settings/environments"
echo "2. For 'staging':"
echo "   - Add 1+ required reviewers (team leads)"
echo "   - Deployment branches: develop, main"
echo "3. For 'production':"
echo "   - Add 2+ required reviewers"
echo "   - Wait timer: 10 minutes (optional)"
echo "   - Deployment branches: main only"
echo ""
read -p "Press Enter to open in browser..."
gh browse --settings

# ═══════════════════════════════════════════════════════════
# SUMMARY
# ═══════════════════════════════════════════════════════════

echo ""
echo "=================================================="
echo "  Setup Complete!"
echo "=================================================="
echo ""
echo -e "${GREEN}✓ Repository secrets configured${NC}"
echo -e "${GREEN}✓ Environments created${NC}"
echo -e "${YELLOW}⚠ Manual: Configure environment protection rules${NC}"
echo ""
echo "Next steps:"
echo "1. Deploy CDK CICD stack:"
echo "   cd infrastructure"
echo "   npm run deploy:dev"
echo ""
echo "2. Test the pipeline:"
echo "   git push origin develop"
echo ""
echo "3. View workflow runs:"
echo "   gh run list"
echo ""
