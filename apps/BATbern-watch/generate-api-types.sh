#!/bin/bash
# Generate Swift types from OpenAPI specifications
# Uses OpenAPI Generator: https://openapi-generator.tech

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_SPECS_DIR="$SCRIPT_DIR/../../docs/api"
OUTPUT_DIR="$SCRIPT_DIR/BATbern-watch Watch App/Generated"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔄 Generating Swift API types from OpenAPI specs...${NC}"

# Install OpenAPI Generator if not present
if ! command -v openapi-generator &> /dev/null; then
    echo -e "${BLUE}📦 Installing OpenAPI Generator...${NC}"
    brew install openapi-generator
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Generate from events-api.openapi.yml
echo -e "${BLUE}📄 Generating from events-api.openapi.yml${NC}"
openapi-generator generate \
    -i "$API_SPECS_DIR/events-api.openapi.yml" \
    -g swift5 \
    -o "$OUTPUT_DIR/EventsAPI" \
    --additional-properties=projectName=EventsAPI,responseAs=Codable \
    --global-property=models,supportingFiles

# Copy only the model files to a cleaner location
mkdir -p "$OUTPUT_DIR/Models"
cp "$OUTPUT_DIR/EventsAPI/EventsAPI/Classes/OpenAPIs/Models"/*.swift "$OUTPUT_DIR/Models/" 2>/dev/null || true

echo -e "${GREEN}✅ Swift API types generated successfully!${NC}"
echo -e "   Output: $OUTPUT_DIR/Models/"
echo -e ""
echo -e "Next steps:"
echo -e "  1. Add files from Generated/Models/ to Xcode project"
echo -e "  2. Replace manual DTOs.swift with generated types"
echo -e "  3. Update ViewModel to use generated models"
