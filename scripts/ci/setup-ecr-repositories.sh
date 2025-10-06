#!/bin/bash
# Setup script for AWS ECR repositories
# Creates all required container registries for BATbern platform services

set -e  # Exit on error

AWS_REGION="${AWS_REGION:-eu-central-1}"
PROJECT_NAME="BATbern"

# Color output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "=================================================="
echo "  BATbern ECR Repository Setup"
echo "=================================================="
echo ""

# Services requiring ECR repositories
SERVICES=(
  "shared-kernel"
  "api-gateway"
  "event-management-service"
  "speaker-coordination-service"
  "partner-coordination-service"
  "attendee-experience-service"
  "company-management-service"
)

# Check AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo -e "${RED}✗ AWS CLI not found${NC}"
    echo "Please install AWS CLI: https://aws.amazon.com/cli/"
    exit 1
fi

# Verify AWS credentials
echo "Verifying AWS credentials..."
if ! aws sts get-caller-identity &> /dev/null; then
    echo -e "${RED}✗ AWS credentials not configured${NC}"
    echo "Run: aws configure"
    exit 1
fi

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo -e "${GREEN}✓ AWS Account: $ACCOUNT_ID${NC}"
echo -e "${GREEN}✓ Region: $AWS_REGION${NC}"
echo ""

# Lifecycle policy to keep only recent images
LIFECYCLE_POLICY='{
  "rules": [
    {
      "rulePriority": 1,
      "description": "Keep only last 10 images",
      "selection": {
        "tagStatus": "any",
        "countType": "imageCountMoreThan",
        "countNumber": 10
      },
      "action": {
        "type": "expire"
      }
    },
    {
      "rulePriority": 2,
      "description": "Remove untagged images after 7 days",
      "selection": {
        "tagStatus": "untagged",
        "countType": "sinceImagePushed",
        "countUnit": "days",
        "countNumber": 7
      },
      "action": {
        "type": "expire"
      }
    }
  ]
}'

# Create repositories
echo "Creating ECR repositories..."
echo ""

SUCCESS_COUNT=0
SKIPPED_COUNT=0
FAILED_COUNT=0

for service in "${SERVICES[@]}"; do
  echo -n "Creating repository: $service... "

  # Check if repository exists
  if aws ecr describe-repositories --repository-names "$service" --region "$AWS_REGION" &> /dev/null; then
    echo -e "${YELLOW}SKIPPED (already exists)${NC}"
    ((SKIPPED_COUNT++))
    continue
  fi

  # Create repository
  if aws ecr create-repository \
    --repository-name "$service" \
    --region "$AWS_REGION" \
    --image-scanning-configuration scanOnPush=true \
    --encryption-configuration encryptionType=AES256 \
    --tags Key=Project,Value="$PROJECT_NAME" Key=ManagedBy,Value=CICD \
    --output json > /dev/null 2>&1; then

    # Set lifecycle policy
    aws ecr put-lifecycle-policy \
      --repository-name "$service" \
      --region "$AWS_REGION" \
      --lifecycle-policy-text "$LIFECYCLE_POLICY" \
      --output json > /dev/null 2>&1

    echo -e "${GREEN}✓ CREATED${NC}"
    ((SUCCESS_COUNT++))
  else
    echo -e "${RED}✗ FAILED${NC}"
    ((FAILED_COUNT++))
  fi
done

echo ""
echo "=================================================="
echo "  Summary"
echo "=================================================="
echo -e "${GREEN}Created:  $SUCCESS_COUNT${NC}"
echo -e "${YELLOW}Skipped:  $SKIPPED_COUNT${NC}"
if [ $FAILED_COUNT -gt 0 ]; then
  echo -e "${RED}Failed:   $FAILED_COUNT${NC}"
fi
echo ""

# List all repositories
echo "ECR Repositories in $AWS_REGION:"
echo ""
aws ecr describe-repositories \
  --region "$AWS_REGION" \
  --query 'repositories[?contains(repositoryName, `batbern`) || contains(repositoryName, `service`) || contains(repositoryName, `kernel`) || contains(repositoryName, `gateway`)].{Name:repositoryName,URI:repositoryUri}' \
  --output table 2>/dev/null || \
aws ecr describe-repositories \
  --region "$AWS_REGION" \
  --query 'repositories[].{Name:repositoryName,URI:repositoryUri}' \
  --output table

echo ""
echo -e "${GREEN}✓ ECR setup complete!${NC}"
echo ""
echo "Next steps:"
echo "  1. Configure GitHub Secrets with AWS credentials"
echo "  2. Add AWS_ACCOUNT_ID secret: $ACCOUNT_ID"
echo "  3. Test Docker image push:"
echo ""
echo "     aws ecr get-login-password --region $AWS_REGION | \\"
echo "       docker login --username AWS --password-stdin \\"
echo "       $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
echo ""
