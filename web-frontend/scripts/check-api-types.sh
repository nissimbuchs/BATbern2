#!/bin/bash
set -e

# Script to check if generated API types are up-to-date with OpenAPI spec
# Used in CI to ensure developers regenerated types after OpenAPI changes

echo "🔍 Checking if API types are up-to-date..."

# Generate types to a temporary location
TEMP_DIR=$(mktemp -d)
TEMP_OUTPUT="$TEMP_DIR/company-api.types.ts"

# Generate types
npx openapi-typescript ../docs/api/companies-api.openapi.yml -o "$TEMP_OUTPUT"

# Compare with committed types
if ! diff -q "$TEMP_OUTPUT" "src/types/generated/company-api.types.ts" > /dev/null 2>&1; then
  echo ""
  echo "❌ ERROR: Generated API types are out of date!"
  echo ""
  echo "The OpenAPI spec (docs/api/companies-api.openapi.yml) was changed"
  echo "but the generated TypeScript types were not updated."
  echo ""
  echo "To fix this, run:"
  echo "  cd web-frontend"
  echo "  npm run generate:api-types"
  echo "  git add src/types/generated/"
  echo "  git commit --amend --no-edit"
  echo ""
  echo "Differences:"
  diff "$TEMP_OUTPUT" "src/types/generated/company-api.types.ts" || true
  echo ""

  # Cleanup
  rm -rf "$TEMP_DIR"
  exit 1
fi

# Cleanup
rm -rf "$TEMP_DIR"

echo "✅ API types are up-to-date"
exit 0
