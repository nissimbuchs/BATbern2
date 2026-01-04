#!/bin/bash
# Linear Stop Hook - Adds dev notes as Linear comment when work stops
# This hook is triggered when Claude finishes responding or when the dev agent completes work

set -e

# Read hook input from stdin
INPUT=$(cat)

# Extract current working directory
CURRENT_DIR=$(echo "$INPUT" | jq -r '.cwd // empty' 2>/dev/null || pwd)

# Look for Linear issue ID (same logic as start hook)
ISSUE_ID=""

# Try to extract from git branch
if command -v git &> /dev/null; then
  BRANCH=$(git branch --show-current 2>/dev/null || echo "")
  if [[ "$BRANCH" =~ (BAT-[0-9]+) ]]; then
    ISSUE_ID="${BASH_REMATCH[1]}"
  fi
fi

# If not found in branch, look for story files
if [ -z "$ISSUE_ID" ]; then
  # Find the most recently modified story file
  STORY_FILE=$(find docs/stories -name "*.md" -type f -exec stat -f "%m %N" {} \; 2>/dev/null | sort -rn | head -1 | cut -d' ' -f2- || echo "")

  if [ -n "$STORY_FILE" ] && [ -f "$STORY_FILE" ]; then
    # Extract Linear issue ID from **Linear Issue**: [BAT-N](url) pattern
    ISSUE_ID=$(grep -oE '\*\*Linear Issue\*\*: \[(BAT-[0-9]+)\]' "$STORY_FILE" | grep -oE 'BAT-[0-9]+' | head -1 || echo "")
  fi
fi

# If no issue ID found, exit silently
if [ -z "$ISSUE_ID" ]; then
  echo "No Linear issue ID found, skipping dev notes posting" >&2
  exit 0
fi

# If LINEAR_API_KEY not set, exit silently
if [ -z "$LINEAR_API_KEY" ]; then
  echo "LINEAR_API_KEY not set, skipping Linear comment" >&2
  exit 0
fi

# Extract dev notes from story file
if [ -z "$STORY_FILE" ]; then
  # Try to find story file by Linear issue ID
  STORY_FILE=$(find docs/stories -name "*${ISSUE_ID}*.md" -type f 2>/dev/null | head -1 || echo "")
fi

if [ -z "$STORY_FILE" ] || [ ! -f "$STORY_FILE" ]; then
  echo "No story file found for issue $ISSUE_ID" >&2
  exit 0
fi

# Extract Dev Agent Record section from story file
# This section contains: Debug Log, Completion Notes, Change Log
extract_dev_notes() {
  local file="$1"

  # Extract everything between "## Dev Agent Record" and the next "##" header
  awk '/^## Dev Agent Record/,/^## [^D]/ {
    if (/^## [^D]/) exit;
    print
  }' "$file" | sed '/^$/d' | head -100  # Limit to 100 lines to avoid huge comments
}

DEV_NOTES=$(extract_dev_notes "$STORY_FILE")

# If dev notes are empty or too short, skip
if [ -z "$DEV_NOTES" ] || [ ${#DEV_NOTES} -lt 50 ]; then
  echo "No substantial dev notes found, skipping Linear comment" >&2
  exit 0
fi

# Format comment for Linear (escape special characters)
COMMENT="## Development Session Summary

\`\`\`markdown
$DEV_NOTES
\`\`\`

---
*Auto-posted by dev agent hook*"

# Escape quotes and newlines for JSON
ESCAPED_COMMENT=$(echo "$COMMENT" | jq -Rs .)

# Create Linear comment via GraphQL API
MUTATION='{
  "query": "mutation { commentCreate(input: { issueId: \"'"$ISSUE_ID"'\", body: '"$ESCAPED_COMMENT"' }) { success comment { id } } }"
}'

RESPONSE=$(curl -s -X POST https://api.linear.app/graphql \
  -H "Authorization: $LINEAR_API_KEY" \
  -H "Content-Type: application/json" \
  -d "$MUTATION")

SUCCESS=$(echo "$RESPONSE" | jq -r '.data.commentCreate.success' 2>/dev/null || echo "false")

if [ "$SUCCESS" = "true" ]; then
  COMMENT_ID=$(echo "$RESPONSE" | jq -r '.data.commentCreate.comment.id' 2>/dev/null || echo "")
  echo "✅ Posted dev notes to Linear issue $ISSUE_ID (comment: $COMMENT_ID)" >&2
else
  ERROR=$(echo "$RESPONSE" | jq -r '.errors[0].message' 2>/dev/null || echo "Unknown error")
  echo "⚠️  Failed to post dev notes to Linear: $ERROR" >&2
fi

exit 0
