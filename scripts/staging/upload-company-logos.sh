#!/bin/bash

###############################################################################
# Upload Company Logos to Staging S3
#
# This script uploads company logo files from local archiv directories to the
# staging S3 bucket for use with batch company import on staging environment.
#
# Usage:
#   ./scripts/staging/upload-company-logos.sh [--dry-run]
#
# Requirements:
#   - AWS CLI installed
#   - AWS_PROFILE=batbern-staging configured
#   - Logo files in apps/BATspa-old/src/archiv/ directories
###############################################################################

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get the project root directory (2 levels up from this script)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Configuration
AWS_PROFILE="batbern-staging"
S3_BUCKET="batbern-content-staging"
S3_PREFIX="import-data/company-logos"
ARCHIV_BASE="$PROJECT_ROOT/apps/BATspa-old/src/archiv"
PARTNERS_DIR="$PROJECT_ROOT/apps/BATspa-old/src/assets/partners"
CDN_BASE="https://cdn.staging.batbern.ch"

# Parse command line arguments
DRY_RUN=false
if [ "$1" == "--dry-run" ]; then
    DRY_RUN=true
fi

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}BATbern - Upload Company Logos to Staging S3${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

if [ "$DRY_RUN" = true ]; then
    echo -e "${YELLOW}Running in DRY-RUN mode (no uploads will be performed)${NC}"
    echo ""
fi

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo -e "${RED}Error: AWS CLI is not installed${NC}"
    echo "Install it from: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html"
    exit 1
fi

# Check if archiv base directory exists
if [ ! -d "$ARCHIV_BASE" ]; then
    echo -e "${RED}Error: Archiv directory not found: $ARCHIV_BASE${NC}"
    exit 1
fi

# Test AWS credentials and bucket access
echo -e "${YELLOW}Checking AWS credentials and bucket access...${NC}"
if ! AWS_PROFILE=$AWS_PROFILE aws s3 ls "s3://$S3_BUCKET/" &> /dev/null; then
    echo -e "${RED}Error: Cannot access S3 bucket: $S3_BUCKET${NC}"
    echo "Please ensure AWS_PROFILE=$AWS_PROFILE is configured correctly"
    exit 1
fi
echo -e "${GREEN}✓ AWS credentials valid and bucket accessible${NC}"
echo ""

# Function to determine Content-Type from file extension
get_content_type() {
    local filename="$1"
    local extension="${filename##*.}"
    extension=$(echo "$extension" | tr '[:upper:]' '[:lower:]')

    case "$extension" in
        jpg|jpeg) echo "image/jpeg" ;;
        png) echo "image/png" ;;
        svg) echo "image/svg+xml" ;;
        gif) echo "image/gif" ;;
        webp) echo "image/webp" ;;
        bmp) echo "image/bmp" ;;
        *) echo "application/octet-stream" ;;
    esac
}

# Find all logo files
echo -e "${YELLOW}Searching for logo files...${NC}"
declare -a LOGO_FILES
declare -a SEEN_FILENAMES

# Function to check if filename already seen
is_seen() {
    local filename="$1"
    for seen in "${SEEN_FILENAMES[@]}"; do
        if [ "$seen" = "$filename" ]; then
            return 0
        fi
    done
    return 1
}

# Search partners directory first (higher quality)
if [ -d "$PARTNERS_DIR" ]; then
    shopt -s nullglob
    for ext in jpg jpeg JPG JPEG png PNG svg SVG gif GIF webp WEBP bmp BMP; do
        for file in "$PARTNERS_DIR"/*."$ext"; do
            if [ -f "$file" ]; then
                filename=$(basename "$file")
                if ! is_seen "$filename"; then
                    LOGO_FILES+=("$file")
                    SEEN_FILENAMES+=("$filename")
                fi
            fi
        done
    done
    shopt -u nullglob
fi

# Search archiv directories (1-57)
for i in {1..57}; do
    ARCHIV_DIR="$ARCHIV_BASE/$i"
    if [ -d "$ARCHIV_DIR" ]; then
        shopt -s nullglob
        for ext in jpg jpeg JPG JPEG png PNG svg SVG gif GIF webp WEBP bmp BMP; do
            for file in "$ARCHIV_DIR"/*."$ext"; do
                if [ -f "$file" ]; then
                    filename=$(basename "$file")
                    # Skip if already seen (from partners dir)
                    if ! is_seen "$filename"; then
                        LOGO_FILES+=("$file")
                        SEEN_FILENAMES+=("$filename")
                    fi
                fi
            done
        done
        shopt -u nullglob
    fi
done

# Check if any files were found
if [ ${#LOGO_FILES[@]} -eq 0 ]; then
    echo -e "${RED}Error: No logo files found in archiv directories${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Found ${#LOGO_FILES[@]} logo files${NC}"
echo ""

# Upload files
echo -e "${YELLOW}Uploading logo files to S3...${NC}"
echo ""

UPLOAD_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0
declare -a FAILED_FILES
declare -a CDN_URLS

for file in "${LOGO_FILES[@]}"; do
    filename=$(basename "$file")
    content_type=$(get_content_type "$filename")
    s3_path="s3://$S3_BUCKET/$S3_PREFIX/$filename"
    cdn_url="$CDN_BASE/$S3_PREFIX/$filename"

    # Check if file already exists in S3 (skip if it does)
    if AWS_PROFILE=$AWS_PROFILE aws s3 ls "$s3_path" &> /dev/null; then
        echo -e "  ${YELLOW}⊘${NC} $filename (already exists)"
        SKIP_COUNT=$((SKIP_COUNT + 1))
        CDN_URLS+=("$cdn_url")
        continue
    fi

    if [ "$DRY_RUN" = true ]; then
        echo -e "  ${BLUE}[DRY-RUN]${NC} $filename → $s3_path"
        echo -e "            Content-Type: $content_type"
        UPLOAD_COUNT=$((UPLOAD_COUNT + 1))
        CDN_URLS+=("$cdn_url")
    else
        # Upload to S3
        if AWS_PROFILE=$AWS_PROFILE aws s3 cp "$file" "$s3_path" \
            --content-type "$content_type" \
            --cache-control "max-age=31536000, public" \
            --acl public-read \
            --metadata-directive REPLACE \
            &> /dev/null; then
            echo -e "  ${GREEN}✓${NC} $filename → $s3_path"
            UPLOAD_COUNT=$((UPLOAD_COUNT + 1))
            CDN_URLS+=("$cdn_url")
        else
            echo -e "  ${RED}✗${NC} $filename (upload failed)"
            FAILED_FILES+=("$filename")
            FAIL_COUNT=$((FAIL_COUNT + 1))
        fi
    fi
done

# Summary
echo ""
echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}Summary${NC}"
echo -e "${BLUE}============================================================${NC}"
echo -e "Total logo files found:    ${#LOGO_FILES[@]}"
echo -e "Successfully uploaded:     ${GREEN}$UPLOAD_COUNT${NC}"
echo -e "Already existed (skipped): ${YELLOW}$SKIP_COUNT${NC}"
echo -e "Failed:                    ${RED}$FAIL_COUNT${NC}"
echo -e "${BLUE}============================================================${NC}"

# Show failed files if any
if [ $FAIL_COUNT -gt 0 ]; then
    echo ""
    echo -e "${RED}Failed uploads:${NC}"
    for failed_file in "${FAILED_FILES[@]}"; do
        echo -e "  - $failed_file"
    done
fi

# Show CDN URLs for verification
if [ ${#CDN_URLS[@]} -gt 0 ]; then
    echo ""
    echo -e "${GREEN}Logos are now accessible via CDN:${NC}"
    echo -e "  Base URL: ${BLUE}$CDN_BASE/$S3_PREFIX/${NC}"
    echo ""
    echo -e "Example URLs:"
    # Show first 5 URLs
    count=0
    for url in "${CDN_URLS[@]}"; do
        if [ $count -lt 5 ]; then
            echo -e "  ${BLUE}$url${NC}"
            count=$((count + 1))
        fi
    done
    if [ ${#CDN_URLS[@]} -gt 5 ]; then
        echo -e "  ... and $((${#CDN_URLS[@]} - 5)) more"
    fi
fi

# Next steps
if [ "$DRY_RUN" = false ]; then
    echo ""
    echo -e "${YELLOW}Next steps:${NC}"
    echo -e "  1. Verify CDN access: ${BLUE}curl -I ${CDN_URLS[0]}${NC}"
    echo -e "  2. Generate staging JSON: ${BLUE}node scripts/staging/generate-staging-companies-json.js${NC}"
    echo -e "  3. Use batch import with the generated JSON file"
fi

echo ""
echo -e "${GREEN}Done!${NC}"
