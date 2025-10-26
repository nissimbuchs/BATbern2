#!/bin/bash

###############################################################################
# Push release-notes.txt to Staging Environment
#
# This script uploads the release-notes.txt file to the staging S3 bucket
# and invalidates the CloudFront cache so the changes are immediately visible.
#
# Usage:
#   ./scripts/staging/push-release-notes.sh
#
# Requirements:
#   - AWS CLI installed
#   - AWS_PROFILE=batbern-staging configured
###############################################################################

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Get the project root directory (2 levels up from this script)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Configuration
AWS_PROFILE="batbern-staging"
S3_BUCKET="batbern-frontend-staging"
RELEASE_NOTES_FILE="$PROJECT_ROOT/web-frontend/public/release-notes.txt"
S3_PATH="release-notes.txt"

echo -e "${YELLOW}=== Pushing Release Notes to Staging ===${NC}"
echo ""

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo -e "${RED}Error: AWS CLI is not installed${NC}"
    echo "Install it from: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html"
    exit 1
fi

# Check if the release notes file exists
if [ ! -f "$RELEASE_NOTES_FILE" ]; then
    echo -e "${RED}Error: $RELEASE_NOTES_FILE not found${NC}"
    echo "Expected path: $RELEASE_NOTES_FILE"
    exit 1
fi

# Show the file content
echo -e "${YELLOW}File content to upload:${NC}"
echo "---"
cat "$RELEASE_NOTES_FILE"
echo "---"
echo ""

# Upload to S3
echo -e "${YELLOW}Uploading to S3...${NC}"
AWS_PROFILE=$AWS_PROFILE aws s3 cp \
    "$RELEASE_NOTES_FILE" \
    "s3://$S3_BUCKET/$S3_PATH" \
    --content-type "text/plain; charset=utf-8" \
    --cache-control "max-age=60" \
    --metadata-directive REPLACE

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ File uploaded successfully to s3://$S3_BUCKET/$S3_PATH${NC}"
else
    echo -e "${RED}✗ Upload failed${NC}"
    exit 1
fi

# Get CloudFront distribution ID
echo ""
echo -e "${YELLOW}Finding CloudFront distribution...${NC}"
DISTRIBUTION_ID=$(AWS_PROFILE=$AWS_PROFILE aws cloudfront list-distributions \
    --query "DistributionList.Items[?Origins.Items[?DomainName=='$S3_BUCKET.s3.amazonaws.com']].Id | [0]" \
    --output text)

if [ -z "$DISTRIBUTION_ID" ] || [ "$DISTRIBUTION_ID" == "None" ]; then
    echo -e "${YELLOW}Warning: Could not find CloudFront distribution for $S3_BUCKET${NC}"
    echo "The file is uploaded but cache invalidation was skipped"
    echo "Changes may take up to 24 hours to appear due to caching"
else
    echo -e "Found distribution: ${GREEN}$DISTRIBUTION_ID${NC}"

    # Create CloudFront invalidation
    echo ""
    echo -e "${YELLOW}Creating CloudFront cache invalidation...${NC}"
    INVALIDATION_ID=$(AWS_PROFILE=$AWS_PROFILE aws cloudfront create-invalidation \
        --distribution-id "$DISTRIBUTION_ID" \
        --paths "/$S3_PATH" \
        --query 'Invalidation.Id' \
        --output text)

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Cache invalidation created: $INVALIDATION_ID${NC}"
        echo "Changes should be visible in staging within 1-2 minutes"
    else
        echo -e "${YELLOW}Warning: Cache invalidation failed${NC}"
        echo "The file is uploaded but may be cached for up to 1 minute"
    fi
fi

echo ""
echo -e "${GREEN}=== Done! ===${NC}"
echo "The release notes should now be visible at: https://staging.batbern.ch"
