#!/bin/bash
# Generate / refresh Swift API model types from the canonical OpenAPI spec.
#
# The watch app consumes a *subset* of the events API, so Generated/Models/
# holds a curated set of model files (not every schema the spec defines). This
# script refreshes the models that are already present, and reports any new
# models the spec now offers so you can opt them in deliberately when a feature
# needs them.
#
# It NEVER deletes the Generated/ root, so hand-maintained files there
# (e.g. OpenAPIUtilities.swift) are preserved. Generation happens in an
# isolated temp dir and is only synced into Models/ on success — a failed
# generation leaves the existing models untouched.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."
API_SPEC="$PROJECT_ROOT/../../docs/api/events-api.openapi.yml"
MODELS_DIR="$PROJECT_ROOT/BATbern-watch Watch App/Generated/Models"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

echo "🔄 Refreshing Swift API models from OpenAPI spec..."
echo "   Spec:   $API_SPEC"
echo "   Models: $MODELS_DIR"

if [ ! -f "$API_SPEC" ]; then
    echo "❌ OpenAPI spec not found: $API_SPEC" >&2
    exit 1
fi

# Install openapi-generator if needed
if ! command -v openapi-generator &> /dev/null; then
    echo "📦 Installing openapi-generator..."
    brew install openapi-generator
fi

# Generate Swift 5 models into an isolated temp dir (Generated/ is untouched
# until generation succeeds).
openapi-generator generate \
    -i "$API_SPEC" \
    -g swift5 \
    -o "$TEMP_DIR" \
    --additional-properties=\
library=urlsession,\
projectName=BATbernAPI,\
responseAs=Codable,\
useSPMFileStructure=false \
    --global-property=models

SRC_MODELS="$TEMP_DIR/Sources/BATbernAPI/Models"
if [ ! -d "$SRC_MODELS" ]; then
    echo "❌ Generation produced no models at: $SRC_MODELS" >&2
    exit 1
fi

mkdir -p "$MODELS_DIR"

# Refresh the curated set: overwrite each model already present with its freshly
# generated version. Curated files that no longer exist in the spec are left in
# place and reported (manual review).
refreshed=0
missing_from_spec=()
for existing in "$MODELS_DIR"/*.swift; do
    [ -e "$existing" ] || continue   # no curated models yet
    name="$(basename "$existing")"
    if [ -f "$SRC_MODELS/$name" ]; then
        cp "$SRC_MODELS/$name" "$existing"
        refreshed=$((refreshed + 1))
    else
        missing_from_spec+=("$name")
    fi
done

# Report new models the spec now offers that aren't in the curated set.
new_available=()
for src in "$SRC_MODELS"/*.swift; do
    [ -e "$src" ] || continue
    name="$(basename "$src")"
    [ -f "$MODELS_DIR/$name" ] || new_available+=("$name")
done

echo ""
echo "✅ Refreshed $refreshed curated model(s) in: $MODELS_DIR/"
echo "   (Generated/OpenAPIUtilities.swift and other hand-maintained files preserved.)"

if [ ${#new_available[@]} -gt 0 ]; then
    echo ""
    echo "ℹ️  ${#new_available[@]} new model(s) available in the spec but NOT in the curated set."
    echo "    Copy one from the generator output into Models/ only if a watch feature needs it"
    echo "    (e.g. when a refreshed model references a new type the build then flags as missing):"
    printf '      - %s\n' "${new_available[@]}" | sort
fi

if [ ${#missing_from_spec[@]} -gt 0 ]; then
    echo ""
    echo "⚠️  ${#missing_from_spec[@]} curated model(s) no longer exist in the spec (left untouched — review):"
    printf '      - %s\n' "${missing_from_spec[@]}"
fi

echo ""
echo "📝 Next steps:"
echo "  1. Build in Xcode (Cmd+B) — new .swift files are picked up automatically."
echo "  2. If the build flags a missing referenced type, copy that model from the"
echo "     generator output (printed above) into Models/ and rebuild."
echo "  3. Run tests (Cmd+U) to verify decoding against the refreshed contract."
