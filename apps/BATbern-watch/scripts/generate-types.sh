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
API_DIR="$PROJECT_ROOT/../../docs/api"
# The events API was split into per-domain specs (API consolidation Phase 6); the
# watch app's curated models are sourced from the union of all of them.
API_SPECS=(
    "events-core-api.openapi.yml"
    "event-sessions-api.openapi.yml"
    "event-speakers-api.openapi.yml"
    "event-registrations-api.openapi.yml"
    "event-newsletter-api.openapi.yml"
    "event-media-api.openapi.yml"
    "event-ai-api.openapi.yml"
    "event-analytics-api.openapi.yml"
    "event-watch-api.openapi.yml"
)
MODELS_DIR="$PROJECT_ROOT/BATbern-watch Watch App/Generated/Models"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

echo "🔄 Refreshing Swift API models from OpenAPI specs..."
echo "   Specs:  $API_DIR/event*-api.openapi.yml (${#API_SPECS[@]} per-domain specs)"
echo "   Models: $MODELS_DIR"

# Install openapi-generator if needed
if ! command -v openapi-generator &> /dev/null; then
    echo "📦 Installing openapi-generator..."
    brew install openapi-generator
fi

# Generate Swift 5 models for each per-domain spec into an isolated temp dir, then
# accumulate the union of all generated models into one directory. Generated/ is
# untouched until generation succeeds. Shared models (e.g. ErrorResponse) regenerate
# identically across specs — overwriting is harmless.
SRC_MODELS="$TEMP_DIR/all-models"
mkdir -p "$SRC_MODELS"
for spec in "${API_SPECS[@]}"; do
    spec_path="$API_DIR/$spec"
    if [ ! -f "$spec_path" ]; then
        echo "❌ OpenAPI spec not found: $spec_path" >&2
        exit 1
    fi
    out="$TEMP_DIR/$spec"
    openapi-generator generate \
        -i "$spec_path" \
        -g swift5 \
        -o "$out" \
        --additional-properties=\
library=urlsession,\
projectName=BATbernAPI,\
responseAs=Codable,\
useSPMFileStructure=false \
        --global-property=models >/dev/null
    if [ -d "$out/Sources/BATbernAPI/Models" ]; then
        cp -f "$out/Sources/BATbernAPI/Models"/*.swift "$SRC_MODELS"/ 2>/dev/null || true
    fi
done

if [ -z "$(ls -A "$SRC_MODELS" 2>/dev/null)" ]; then
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
