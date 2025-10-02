#!/bin/bash
# Sync feature flags from source to target environment
# Usage: ./sync-feature-flags.sh <source_env> <target_env>

set -e

SOURCE_ENV=$1
TARGET_ENV=$2

if [ -z "$SOURCE_ENV" ] || [ -z "$TARGET_ENV" ]; then
    echo "Usage: $0 <source_env> <target_env>"
    exit 1
fi

if [ -z "$LAUNCHDARKLY_API_TOKEN" ]; then
    echo "ERROR: LAUNCHDARKLY_API_TOKEN environment variable not set"
    exit 1
fi

echo "=========================================="
echo "Syncing Feature Flags"
echo "Source: $SOURCE_ENV"
echo "Target: $TARGET_ENV"
echo "=========================================="

LAUNCHDARKLY_PROJECT="batbern"
LAUNCHDARKLY_API="https://app.launchdarkly.com/api/v2"

# Get all flags from LaunchDarkly project
echo "Fetching feature flags from LaunchDarkly..."
FLAGS_RESPONSE=$(curl -s -H "Authorization: $LAUNCHDARKLY_API_TOKEN" \
    "$LAUNCHDARKLY_API/flags/$LAUNCHDARKLY_PROJECT")

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to fetch feature flags from LaunchDarkly"
    exit 1
fi

FLAG_KEYS=$(echo "$FLAGS_RESPONSE" | jq -r '.items[].key')
FLAG_COUNT=$(echo "$FLAG_KEYS" | wc -l)

echo "Found $FLAG_COUNT feature flags to sync"

# Sync each flag from source to target environment
for flag_key in $FLAG_KEYS; do
    echo ""
    echo "Processing flag: $flag_key"

    # Get source environment flag state
    SOURCE_FLAG=$(curl -s -H "Authorization: $LAUNCHDARKLY_API_TOKEN" \
        "$LAUNCHDARKLY_API/flags/$LAUNCHDARKLY_PROJECT/$flag_key")

    if [ $? -ne 0 ]; then
        echo "  ⚠️  Failed to fetch flag $flag_key, skipping..."
        continue
    fi

    # Extract source environment state
    SOURCE_STATE=$(echo "$SOURCE_FLAG" | jq -r ".environments[\"$SOURCE_ENV\"].on")
    SOURCE_VARIATION=$(echo "$SOURCE_FLAG" | jq -r ".environments[\"$SOURCE_ENV\"].fallthrough.variation")

    if [ "$SOURCE_STATE" == "null" ]; then
        echo "  ⚠️  Source environment not found for flag $flag_key, skipping..."
        continue
    fi

    echo "  Source state: on=$SOURCE_STATE, variation=$SOURCE_VARIATION"

    # Update target environment flag state
    UPDATE_PAYLOAD=$(cat <<EOF
{
  "comment": "Promoted from $SOURCE_ENV to $TARGET_ENV",
  "patch": [
    {
      "op": "replace",
      "path": "/environments/$TARGET_ENV/on",
      "value": $SOURCE_STATE
    }
  ]
}
EOF
)

    UPDATE_RESPONSE=$(curl -s -X PATCH \
        -H "Authorization: $LAUNCHDARKLY_API_TOKEN" \
        -H "Content-Type: application/json" \
        -d "$UPDATE_PAYLOAD" \
        "$LAUNCHDARKLY_API/flags/$LAUNCHDARKLY_PROJECT/$flag_key")

    if [ $? -eq 0 ]; then
        echo "  ✓ Successfully synced $flag_key to $TARGET_ENV"
    else
        echo "  ❌ Failed to sync $flag_key: $UPDATE_RESPONSE"
    fi
done

# Verify feature flag state after promotion
echo ""
echo "Verifying feature flag state in $TARGET_ENV..."

VERIFICATION_PASSED=true
for flag_key in $FLAG_KEYS; do
    TARGET_FLAG=$(curl -s -H "Authorization: $LAUNCHDARKLY_API_TOKEN" \
        "$LAUNCHDARKLY_API/flags/$LAUNCHDARKLY_PROJECT/$flag_key")

    TARGET_STATE=$(echo "$TARGET_FLAG" | jq -r ".environments[\"$TARGET_ENV\"].on")

    if [ "$TARGET_STATE" == "null" ]; then
        echo "  ⚠️  Could not verify flag $flag_key in $TARGET_ENV"
        VERIFICATION_PASSED=false
    fi
done

if [ "$VERIFICATION_PASSED" = true ]; then
    echo "✓ Feature flag verification complete"
else
    echo "⚠️  Some feature flags could not be verified"
fi

echo ""
echo "=========================================="
echo "✅ Feature flag sync complete!"
echo "Synced $FLAG_COUNT flags from $SOURCE_ENV to $TARGET_ENV"
echo "=========================================="
