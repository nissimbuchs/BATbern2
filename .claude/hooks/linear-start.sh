#!/bin/bash
# Linear Start Hook - Updates Linear issue status to "In Progress" when starting work
# This hook is triggered when user submits develop-story command

set -e

# Read hook input from stdin
INPUT=$(cat)

# Extract user prompt text
USER_PROMPT=$(echo "$INPUT" | jq -r '.user_prompt // empty' 2>/dev/null || echo "")

# Only proceed if the prompt contains develop-story command
if [[ ! "$USER_PROMPT" =~ develop-story ]]; then
  exit 0  # Silently exit if not a develop-story command
fi

# Extract story name from command (e.g., develop-story 2.5.3 or develop-story BAT-123)
STORY_REF=$(echo "$USER_PROMPT" | grep -oE 'develop-story\s+[^\s]+' | awk '{print $2}' || echo "")

# Extract current working directory
CURRENT_DIR=$(echo "$INPUT" | jq -r '.cwd // empty' 2>/dev/null || pwd)

# Look for Linear issue ID in multiple places:
# 1. Current git branch name (e.g., feature/BAT-123-some-feature)
# 2. Story files in docs/stories/ directory (extract from **Linear Issue**: [BAT-N](url))

ISSUE_ID=""

# Try to extract from git branch
if command -v git &> /dev/null; then
  BRANCH=$(git branch --show-current 2>/dev/null || echo "")
  if [[ "$BRANCH" =~ (BAT-[0-9]+) ]]; then
    ISSUE_ID="${BASH_REMATCH[1]}"
  fi
fi

# If not found in branch, look for story files using the story reference from command
if [ -z "$ISSUE_ID" ] && [ -n "$STORY_REF" ]; then
  # Try to find story file by reference (e.g., 2.5.3, BAT-123)
  if [[ "$STORY_REF" =~ ^BAT- ]]; then
    # Direct Linear issue reference
    STORY_FILE=$(find docs/stories -name "*${STORY_REF}*.md" -type f 2>/dev/null | head -1 || echo "")
  else
    # Story ID reference (e.g., 2.5.3)
    STORY_FILE=$(find docs/stories -name "${STORY_REF}.*.md" -type f 2>/dev/null | head -1 || echo "")
  fi

  if [ -n "$STORY_FILE" ] && [ -f "$STORY_FILE" ]; then
    # Extract Linear issue ID from **Linear Issue**: [BAT-N](url) pattern
    ISSUE_ID=$(grep -oE '\*\*Linear Issue\*\*: \[(BAT-[0-9]+)\]' "$STORY_FILE" | grep -oE 'BAT-[0-9]+' | head -1 || echo "")
  fi
fi

# Fallback: If still not found, check most recently modified story file
if [ -z "$ISSUE_ID" ]; then
  STORY_FILE=$(find docs/stories -name "*.md" -type f -exec stat -f "%m %N" {} \; 2>/dev/null | sort -rn | head -1 | cut -d' ' -f2- || echo "")

  if [ -n "$STORY_FILE" ] && [ -f "$STORY_FILE" ]; then
    ISSUE_ID=$(grep -oE '\*\*Linear Issue\*\*: \[(BAT-[0-9]+)\]' "$STORY_FILE" | grep -oE 'BAT-[0-9]+' | head -1 || echo "")
  fi
fi

# If still no issue ID found, exit silently
if [ -z "$ISSUE_ID" ]; then
  echo "No Linear issue ID found in branch name or story files" >&2
  exit 0
fi

# Update Linear issue status via Linear CLI or API
# Note: This requires LINEAR_API_KEY environment variable to be set

if [ -z "$LINEAR_API_KEY" ]; then
  echo "LINEAR_API_KEY not set, skipping Linear update" >&2
  exit 0
fi

# Call Linear GraphQL API to update issue status to "In Progress"
QUERY='mutation {
  issueUpdate(
    id: "'"$ISSUE_ID"'"
    input: { stateId: "'"$(get_in_progress_state_id)"'" }
  ) {
    success
    issue {
      id
      state {
        name
      }
    }
  }
}'

# Helper function to get "In Progress" state ID for the team
get_in_progress_state_id() {
  # Query for team states and find "In Progress" state ID
  TEAM_ID="978bcf9f-669c-464b-a0a4-a162072e575c"  # BATbern team ID from core-config.yaml

  STATE_QUERY='{
    team(id: "'"$TEAM_ID"'") {
      states {
        nodes {
          id
          name
        }
      }
    }
  }'

  STATE_ID=$(curl -s -X POST https://api.linear.app/graphql \
    -H "Authorization: $LINEAR_API_KEY" \
    -H "Content-Type: application/json" \
    -d '{"query":"'"$(echo "$STATE_QUERY" | tr '\n' ' ')"'"}' | \
    jq -r '.data.team.states.nodes[] | select(.name == "In Progress") | .id' 2>/dev/null || echo "")

  echo "$STATE_ID"
}

# Get the In Progress state ID
STATE_ID=$(get_in_progress_state_id)

if [ -z "$STATE_ID" ]; then
  echo "Could not find 'In Progress' state for Linear team" >&2
  exit 0
fi

# Update the issue
RESPONSE=$(curl -s -X POST https://api.linear.app/graphql \
  -H "Authorization: $LINEAR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"query":"mutation { issueUpdate(id: \"'"$ISSUE_ID"'\", input: { stateId: \"'"$STATE_ID"'\" }) { success issue { id state { name } } } }"}')

SUCCESS=$(echo "$RESPONSE" | jq -r '.data.issueUpdate.success' 2>/dev/null || echo "false")

if [ "$SUCCESS" = "true" ]; then
  echo "✅ Updated Linear issue $ISSUE_ID to 'In Progress'" >&2
else
  echo "⚠️  Failed to update Linear issue $ISSUE_ID" >&2
fi

exit 0
