#!/bin/bash
# Generate the Swift API model types the watch decodes, from the canonical OpenAPI specs.
#
# The watch decodes exactly one API payload — EventDetail, from
# GET /api/v1/public/events/current — so Generated/Models/ holds EventDetail plus every
# model it references, transitively (the "closure" of ROOT_MODELS). The script computes
# that closure from the generator output, syncs it into Models/, and removes models
# that fell out of it. Add a root only when the watch starts decoding another payload.
#
# It NEVER touches files outside Generated/Models/, so hand-maintained files in
# Generated/ (OpenAPIUtilities.swift, EventDetailExtensions.swift) are preserved.
# Generation happens in an isolated temp dir and is only synced on success — a failed
# generation leaves the existing models untouched.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."
API_DIR="$PROJECT_ROOT/../../docs/api"
# The events API was split into per-domain specs (API consolidation Phase 6); models are
# generated from all of them so the closure can follow cross-spec references.
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
# The payloads the watch decodes. Everything they reference is pulled in automatically.
ROOT_MODELS=("EventDetail")
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

echo "🔄 Refreshing Swift API models from OpenAPI specs..."
echo "   Specs:  $API_DIR/event*-api.openapi.yml (${#API_SPECS[@]} per-domain specs)"
echo "   Roots:  ${ROOT_MODELS[*]}"
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

# Compute the closure of ROOT_MODELS: a model belongs if a member references its type name
# (comments excluded). Plain newline-separated lists — macOS /bin/bash is 3.2 (no `declare -A`).
closure=""
in_closure() { printf '%s\n' "$closure" | grep -qx "$1"; }
queue="$(printf '%s\n' "${ROOT_MODELS[@]}")"
while [ -n "$queue" ]; do
    name="$(printf '%s\n' "$queue" | head -n 1)"
    queue="$(printf '%s\n' "$queue" | tail -n +2)"
    in_closure "$name" && continue
    if [ ! -f "$SRC_MODELS/$name.swift" ]; then
        echo "❌ Root/referenced model not produced by the generator: $name" >&2
        exit 1
    fi
    closure="$(printf '%s\n%s' "$closure" "$name" | sed '/^$/d')"
    code="$(grep -vE '^[[:space:]]*(//|/\*\*|\*)' "$SRC_MODELS/$name.swift")"
    for candidate in "$SRC_MODELS"/*.swift; do
        ref="$(basename "$candidate" .swift)"
        in_closure "$ref" && continue
        if printf '%s\n' "$code" | grep -qE "(^|[^A-Za-z0-9_])$ref([^A-Za-z0-9_]|$)"; then
            queue="$(printf '%s\n%s' "$queue" "$ref" | sed '/^$/d')"
        fi
    done
done

mkdir -p "$MODELS_DIR"

# Sync: copy every closure member, remove every model outside the closure.
added=""; removed=""; updated=0; total=0
for name in $closure; do
    total=$((total + 1))
    target="$MODELS_DIR/$name.swift"
    if [ -f "$target" ]; then
        cmp -s "$SRC_MODELS/$name.swift" "$target" || updated=$((updated + 1))
    else
        added="$added $name"
    fi
    cp "$SRC_MODELS/$name.swift" "$target"
done
for existing in "$MODELS_DIR"/*.swift; do
    [ -e "$existing" ] || continue
    name="$(basename "$existing" .swift)"
    if ! in_closure "$name"; then
        rm "$existing"
        removed="$removed $name"
    fi
done

echo ""
echo "✅ $total model(s) in the closure of: ${ROOT_MODELS[*]} ($updated changed)"
for name in $added; do echo "   + $name"; done
for name in $removed; do echo "   - $name"; done

echo ""
echo "📝 Next: build and run the tests (Cmd+U) — Generated/EventDetailExtensions.swift maps"
echo "   these types to the watch's domain models and is where contract changes surface."
