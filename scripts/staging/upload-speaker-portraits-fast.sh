#!/bin/bash

###############################################################################
# Fast Upload Speaker Portraits to Staging S3
#
# This script only uploads portraits that are actually referenced in speakers.json
# Much faster than scanning all archiv directories.
###############################################################################

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
AWS_PROFILE="batbern-staging"
S3_BUCKET="batbern-content-staging"
S3_PREFIX="import-data/speaker-portraits"
CDN_BASE="https://cdn.staging.batbern.ch"

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}BATbern - Fast Upload Speaker Portraits to Staging S3${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

# Check AWS CLI
if ! command -v aws &> /dev/null; then
    echo -e "${RED}Error: AWS CLI is not installed${NC}"
    exit 1
fi

# Test AWS credentials
echo -e "${YELLOW}Checking AWS credentials...${NC}"
if ! AWS_PROFILE=$AWS_PROFILE aws s3 ls "s3://$S3_BUCKET/" &> /dev/null; then
    echo -e "${RED}Error: Cannot access S3 bucket${NC}"
    exit 1
fi
echo -e "${GREEN}✓ AWS credentials valid${NC}"
echo ""

# Generate the staging JSON first to get list of needed files
echo -e "${YELLOW}Generating staging JSON to identify needed portraits...${NC}"
cd "$PROJECT_ROOT"
node scripts/staging/generate-staging-speakers-json.js > /dev/null 2>&1

# Extract unique filenames from the generated JSON
PORTRAIT_FILES=$(grep -o 'import-data/speaker-portraits/[^"]*' "$PROJECT_ROOT/apps/BATspa-old/src/api/speakers-with-staging-urls.json" | sed 's|import-data/speaker-portraits/||' | sort -u)

PORTRAIT_COUNT=$(echo "$PORTRAIT_FILES" | wc -l | tr -d ' ')
echo -e "${GREEN}✓ Found $PORTRAIT_COUNT unique portrait files needed${NC}"
echo ""

# Function to find a portrait file in archiv directories
find_portrait() {
    local filename="$1"
    local archiv_base="$PROJECT_ROOT/apps/BATspa-old/src/archiv"
    local portraits_dir="$PROJECT_ROOT/apps/BATspa-old/public/portraits"

    # Check portraits dir first
    if [ -f "$portraits_dir/$filename" ]; then
        echo "$portraits_dir/$filename"
        return 0
    fi

    # Check archiv directories (1-57)
    for i in {1..57}; do
        if [ -f "$archiv_base/$i/$filename" ]; then
            echo "$archiv_base/$i/$filename"
            return 0
        fi
    done

    return 1
}

# Function to get Content-Type
get_content_type() {
    case "${1##*.}" in
        jpg|jpeg|JPG|JPEG) echo "image/jpeg" ;;
        png|PNG) echo "image/png" ;;
        svg|SVG) echo "image/svg+xml" ;;
        gif|GIF) echo "image/gif" ;;
        webp|WEBP) echo "image/webp" ;;
        bmp|BMP) echo "image/bmp" ;;
        *) echo "application/octet-stream" ;;
    esac
}

# Upload portraits
echo -e "${YELLOW}Uploading portraits to S3...${NC}"
echo ""

UPLOAD_COUNT=0
SKIP_COUNT=0
FAIL_COUNT=0

while IFS= read -r filename; do
    [ -z "$filename" ] && continue

    s3_path="s3://$S3_BUCKET/$S3_PREFIX/$filename"

    # Check if already exists
    if AWS_PROFILE=$AWS_PROFILE aws s3 ls "$s3_path" &> /dev/null; then
        echo -e "  ${YELLOW}⊘${NC} $filename (already exists)"
        SKIP_COUNT=$((SKIP_COUNT + 1))
        continue
    fi

    # Find the file
    if local_path=$(find_portrait "$filename"); then
        content_type=$(get_content_type "$filename")

        # Upload (without ACL - bucket policy handles access)
        if AWS_PROFILE=$AWS_PROFILE aws s3 cp "$local_path" "$s3_path" \
            --content-type "$content_type" \
            --cache-control "max-age=31536000, public" \
            &> /dev/null; then
            echo -e "  ${GREEN}✓${NC} $filename"
            UPLOAD_COUNT=$((UPLOAD_COUNT + 1))
        else
            echo -e "  ${RED}✗${NC} $filename (upload failed)"
            FAIL_COUNT=$((FAIL_COUNT + 1))
        fi
    else
        echo -e "  ${RED}✗${NC} $filename (not found locally)"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
done <<< "$PORTRAIT_FILES"

# Summary
echo ""
echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}Summary${NC}"
echo -e "${BLUE}============================================================${NC}"
echo -e "Total portraits needed:    $PORTRAIT_COUNT"
echo -e "Successfully uploaded:     ${GREEN}$UPLOAD_COUNT${NC}"
echo -e "Already existed (skipped): ${YELLOW}$SKIP_COUNT${NC}"
echo -e "Failed:                    ${RED}$FAIL_COUNT${NC}"
echo -e "${BLUE}============================================================${NC}"

if [ $UPLOAD_COUNT -gt 0 ] || [ $SKIP_COUNT -gt 0 ]; then
    echo ""
    echo -e "${GREEN}Portraits accessible at: ${BLUE}$CDN_BASE/$S3_PREFIX/<filename>${NC}"
fi

echo ""
echo -e "${GREEN}Done!${NC}"

exit 0
