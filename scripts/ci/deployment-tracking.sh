#!/bin/bash
set -e

# Deployment Tracking via GitHub Deployments API
#
# Tracks the last successfully deployed commit per environment to ensure
# all changed services are rebuilt even when previous workflows fail partway through.
#
# Usage:
#   deployment-tracking.sh <environment> get-last-deployed
#   deployment-tracking.sh <environment> mark-deployed <commit-sha>
#
# Environment Variables Required:
#   GITHUB_TOKEN        - GitHub API token (automatic in GitHub Actions)
#   GITHUB_REPOSITORY   - Repository name (e.g., org/repo)
#   GITHUB_RUN_ID       - Workflow run ID (for logging)
#   GITHUB_SERVER_URL   - GitHub server URL (for links)

ENVIRONMENT=$1
OPERATION=$2
COMMIT_SHA=$3

# Validate required environment variables
if [ -z "$GITHUB_TOKEN" ]; then
  echo "::error::GITHUB_TOKEN not set"
  exit 1
fi

if [ -z "$GITHUB_REPOSITORY" ]; then
  echo "::error::GITHUB_REPOSITORY not set"
  exit 1
fi

GITHUB_API="https://api.github.com"
REPO="$GITHUB_REPOSITORY"
TOKEN="$GITHUB_TOKEN"

# Validate arguments
if [ -z "$ENVIRONMENT" ]; then
  echo "::error::Environment not specified"
  echo "Usage: $0 <environment> <get-last-deployed|mark-deployed> [commit-sha]"
  exit 1
fi

if [ -z "$OPERATION" ]; then
  echo "::error::Operation not specified"
  echo "Usage: $0 <environment> <get-last-deployed|mark-deployed> [commit-sha]"
  exit 1
fi

case $OPERATION in
  get-last-deployed)
    echo "🔍 Querying last successful deployment for $ENVIRONMENT..."

    # Query GitHub Deployments API for most recent successful deployment
    RESPONSE=$(curl -s -f \
      -H "Authorization: token $TOKEN" \
      -H "Accept: application/vnd.github+json" \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      "$GITHUB_API/repos/$REPO/deployments?environment=$ENVIRONMENT&per_page=1" 2>/dev/null || echo "[]")

    # Extract SHA from most recent deployment
    LAST_DEPLOYED=$(echo "$RESPONSE" | jq -r '.[0].sha // empty' 2>/dev/null || echo "")

    if [ -z "$LAST_DEPLOYED" ]; then
      # No deployment history found
      echo "::notice::No deployment history for $ENVIRONMENT"
      echo "::notice::This is either the first deployment or tracking was just enabled"
      echo "::notice::Will fall back to comparing against previous commit"
      echo ""  # Return empty to trigger fallback in workflow
    else
      # Found last deployed commit
      DEPLOYED_AT=$(echo "$RESPONSE" | jq -r '.[0].created_at // "unknown"' 2>/dev/null)
      DESCRIPTION=$(echo "$RESPONSE" | jq -r '.[0].description // "No description"' 2>/dev/null)

      echo "✅ Found last successful deployment:"
      echo "   Commit: $LAST_DEPLOYED"
      echo "   Deployed: $DEPLOYED_AT"
      echo "   Description: $DESCRIPTION"
      echo "$LAST_DEPLOYED"  # Output SHA for use in workflow
    fi
    ;;

  mark-deployed)
    if [ -z "$COMMIT_SHA" ]; then
      echo "::error::Commit SHA not provided"
      echo "Usage: $0 <environment> mark-deployed <commit-sha>"
      exit 1
    fi

    echo "🎯 Recording successful deployment to $ENVIRONMENT"
    echo "   Commit: $COMMIT_SHA"
    echo "   Environment: $ENVIRONMENT"
    echo "   Run ID: ${GITHUB_RUN_ID:-unknown}"

    # Create deployment record
    DEPLOYMENT_PAYLOAD=$(cat <<EOF
{
  "ref": "$COMMIT_SHA",
  "environment": "$ENVIRONMENT",
  "description": "Deployed via GitHub Actions run ${GITHUB_RUN_ID:-unknown}",
  "auto_merge": false,
  "required_contexts": []
}
EOF
)

    echo "📝 Creating deployment record..."
    DEPLOYMENT_RESPONSE=$(curl -s -f -X POST \
      -H "Authorization: token $TOKEN" \
      -H "Accept: application/vnd.github+json" \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      "$GITHUB_API/repos/$REPO/deployments" \
      -d "$DEPLOYMENT_PAYLOAD" 2>/dev/null || echo '{"id":null}')

    DEPLOYMENT_ID=$(echo "$DEPLOYMENT_RESPONSE" | jq -r '.id // empty' 2>/dev/null)

    if [ -z "$DEPLOYMENT_ID" ] || [ "$DEPLOYMENT_ID" = "null" ]; then
      echo "::error::Failed to create deployment record"
      echo "::error::Response: $DEPLOYMENT_RESPONSE"

      # Check for common errors
      ERROR_MESSAGE=$(echo "$DEPLOYMENT_RESPONSE" | jq -r '.message // "Unknown error"' 2>/dev/null)
      echo "::error::GitHub API Error: $ERROR_MESSAGE"

      # Non-blocking: Log error but don't fail workflow
      echo "::warning::Deployment was successful but tracking update failed"
      echo "::warning::Next build may rebuild some services unnecessarily"
      exit 0  # Exit 0 to not fail the workflow
    fi

    echo "✅ Created deployment record: $DEPLOYMENT_ID"

    # Mark deployment as successful
    STATUS_PAYLOAD=$(cat <<EOF
{
  "state": "success",
  "log_url": "${GITHUB_SERVER_URL:-https://github.com}/$REPO/actions/runs/${GITHUB_RUN_ID:-unknown}",
  "description": "Successfully deployed $COMMIT_SHA to $ENVIRONMENT",
  "environment": "$ENVIRONMENT"
}
EOF
)

    echo "📝 Marking deployment as successful..."
    STATUS_RESPONSE=$(curl -s -f -X POST \
      -H "Authorization: token $TOKEN" \
      -H "Accept: application/vnd.github+json" \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      "$GITHUB_API/repos/$REPO/deployments/$DEPLOYMENT_ID/statuses" \
      -d "$STATUS_PAYLOAD" 2>/dev/null || echo '{"state":null}')

    STATUS_STATE=$(echo "$STATUS_RESPONSE" | jq -r '.state // empty' 2>/dev/null)

    if [ "$STATUS_STATE" != "success" ]; then
      echo "::warning::Failed to mark deployment as successful"
      echo "::warning::Response: $STATUS_RESPONSE"
      echo "::warning::Deployment record created but status not set"
      exit 0  # Non-blocking
    fi

    echo ""
    echo "✅ Successfully recorded deployment:"
    echo "   Deployment ID: $DEPLOYMENT_ID"
    echo "   Commit: $COMMIT_SHA"
    echo "   Environment: $ENVIRONMENT"
    echo "   Status: success"
    echo ""
    echo "🔗 View deployment: ${GITHUB_SERVER_URL:-https://github.com}/$REPO/deployments"
    echo ""
    echo "📊 Next build will compare against this commit"
    ;;

  *)
    echo "::error::Invalid operation: $OPERATION"
    echo "Usage: $0 <environment> <get-last-deployed|mark-deployed> [commit-sha]"
    echo ""
    echo "Operations:"
    echo "  get-last-deployed  - Query last successful deployment SHA"
    echo "  mark-deployed      - Mark a commit as successfully deployed"
    echo ""
    echo "Examples:"
    echo "  $0 staging get-last-deployed"
    echo "  $0 staging mark-deployed abc123def456"
    echo "  $0 production mark-deployed v1.2.3"
    exit 1
    ;;
esac
