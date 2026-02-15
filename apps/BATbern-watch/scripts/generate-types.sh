#!/bin/bash
# Generate Swift types from OpenAPI spec using openapi-generator CLI

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."
API_SPEC="$PROJECT_ROOT/../../docs/api/events-api.openapi.yml"
OUTPUT_DIR="$PROJECT_ROOT/BATbern-watch Watch App/Generated"

echo "🔄 Generating Swift types from OpenAPI spec..."

# Install openapi-generator if needed
if ! command -v openapi-generator &> /dev/null; then
    echo "📦 Installing openapi-generator..."
    brew install openapi-generator
fi

# Clean previous output
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# Generate Swift 5 models
openapi-generator generate \
    -i "$API_SPEC" \
    -g swift5 \
    -o "$OUTPUT_DIR/temp" \
    --additional-properties=\
library=urlsession,\
projectName=BATbernAPI,\
responseAs=Codable,\
useSPMFileStructure=false \
    --global-property=models

# Copy only model files
mkdir -p "$OUTPUT_DIR/Models"
if [ -d "$OUTPUT_DIR/temp/Sources/BATbernAPI/Models" ]; then
    cp "$OUTPUT_DIR/temp/Sources/BATbernAPI/Models"/*.swift "$OUTPUT_DIR/Models/" 2>/dev/null || true
fi

# Cleanup
rm -rf "$OUTPUT_DIR/temp"

echo "✅ Generated Swift types in: $OUTPUT_DIR/Models/"
echo ""
echo "📝 Next steps:"
echo "  1. Open Xcode"
echo "  2. Add files from Generated/Models/ to project"
echo "  3. Replace manual DTOs with generated types"
