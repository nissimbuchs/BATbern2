#!/bin/bash
set -euo pipefail

# Dependabot Batch Merge Script
# Automatically merges open dependabot PRs in batches to avoid conflicts
#
# Usage:
#   DRY_RUN=false ./scripts/ci/dependabot-batch-merge.sh
#   DRY_RUN=true ./scripts/ci/dependabot-batch-merge.sh  # Test mode
#
# Environment variables:
#   GH_TOKEN - GitHub token with PR write permissions (required)
#   DRY_RUN  - Set to 'true' to preview actions without executing (default: false)

# Configuration
DRY_RUN="${DRY_RUN:-false}"
SUMMARY_FILE="/tmp/dependabot-batch-merge-summary.md"
MAX_CONFLICTS=3  # Close PRs with more than this many conflicts

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Initialize summary
init_summary() {
    cat > "$SUMMARY_FILE" <<EOF
# 🤖 Dependabot Batch Merge Summary

**Run Date**: $(date '+%Y-%m-%d %H:%M:%S')
**Mode**: $([ "$DRY_RUN" = "true" ] && echo "DRY RUN 🧪" || echo "LIVE 🔴")

---

EOF
}

# Log to both console and summary
log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
    echo "$1" >> "$SUMMARY_FILE"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
    echo "✅ $1" >> "$SUMMARY_FILE"
}

log_warn() {
    echo -e "${YELLOW}⚠️  $1${NC}"
    echo "⚠️ $1" >> "$SUMMARY_FILE"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
    echo "❌ $1" >> "$SUMMARY_FILE"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."

    if ! command -v gh &> /dev/null; then
        log_error "GitHub CLI (gh) not found. Please install it."
        exit 1
    fi

    if [ -z "${GH_TOKEN:-}" ]; then
        log_error "GH_TOKEN environment variable not set"
        exit 1
    fi

    log_success "Prerequisites check passed"
}

# Get all open dependabot PRs
get_dependabot_prs() {
    log_info "Fetching open dependabot PRs..."

    gh pr list \
        --label dependencies \
        --state open \
        --json number,title,headRefName,updatedAt,mergeable \
        --jq 'sort_by(.updatedAt) | .[]' \
        > /tmp/dependabot-prs.json

    local pr_count=$(jq -s 'length' /tmp/dependabot-prs.json)
    log_info "Found $pr_count open dependabot PRs"

    echo "$pr_count"
}

# Attempt to update PR (rebase)
update_pr() {
    local pr_number=$1
    local pr_title=$2

    log_info "Attempting to update PR #$pr_number: $pr_title"

    if [ "$DRY_RUN" = "true" ]; then
        log_warn "[DRY RUN] Would update PR #$pr_number"
        return 0
    fi

    # Try to update the PR
    if gh pr comment "$pr_number" --body "🤖 Attempting automatic rebase and merge..."; then
        # Check if PR is now mergeable
        local mergeable=$(gh pr view "$pr_number" --json mergeable --jq '.mergeable')

        if [ "$mergeable" = "MERGEABLE" ]; then
            log_success "PR #$pr_number is mergeable"
            return 0
        else
            log_warn "PR #$pr_number has conflicts"
            return 1
        fi
    else
        log_error "Failed to update PR #$pr_number"
        return 1
    fi
}

# Enable auto-merge for PR
enable_auto_merge() {
    local pr_number=$1

    if [ "$DRY_RUN" = "true" ]; then
        log_warn "[DRY RUN] Would enable auto-merge for PR #$pr_number"
        return 0
    fi

    log_info "Enabling auto-merge for PR #$pr_number"

    if gh pr merge "$pr_number" --auto --squash; then
        log_success "Auto-merge enabled for PR #$pr_number"
        return 0
    else
        log_error "Failed to enable auto-merge for PR #$pr_number"
        return 1
    fi
}

# Close PR with comment
close_pr() {
    local pr_number=$1
    local reason=$2

    if [ "$DRY_RUN" = "true" ]; then
        log_warn "[DRY RUN] Would close PR #$pr_number: $reason"
        return 0
    fi

    log_info "Closing PR #$pr_number: $reason"

    if gh pr close "$pr_number" --comment "🤖 Closing PR: $reason"; then
        log_success "Closed PR #$pr_number"
        return 0
    else
        log_error "Failed to close PR #$pr_number"
        return 1
    fi
}

# Process a single PR
process_pr() {
    local pr_data=$1
    local pr_number=$(echo "$pr_data" | jq -r '.number')
    local pr_title=$(echo "$pr_data" | jq -r '.title')
    local mergeable=$(echo "$pr_data" | jq -r '.mergeable')

    echo "" >> "$SUMMARY_FILE"
    log_info "Processing PR #$pr_number: $pr_title"

    # Check if already mergeable
    if [ "$mergeable" = "MERGEABLE" ]; then
        log_success "PR #$pr_number is already mergeable"
        enable_auto_merge "$pr_number"
        return 0
    fi

    # Try to update PR
    if update_pr "$pr_number" "$pr_title"; then
        enable_auto_merge "$pr_number"
        return 0
    else
        # PR has conflicts, close it
        close_pr "$pr_number" "Merge conflicts detected. Will be recreated in next dependabot run."
        return 1
    fi
}

# Main execution
main() {
    log_info "Starting Dependabot Batch Merge"
    echo "========================================"

    init_summary
    check_prerequisites

    local pr_count=$(get_dependabot_prs)

    if [ "$pr_count" -eq 0 ]; then
        log_success "No dependabot PRs to process"
        exit 0
    fi

    # Process each PR
    local merged=0
    local closed=0
    local failed=0

    while IFS= read -r pr_data; do
        if process_pr "$pr_data"; then
            ((merged++)) || true
        else
            ((closed++)) || true
        fi

        # Add delay between PRs to avoid rate limiting
        sleep 2
    done < /tmp/dependabot-prs.json

    # Final summary
    echo "" >> "$SUMMARY_FILE"
    echo "---" >> "$SUMMARY_FILE"
    echo "" >> "$SUMMARY_FILE"
    echo "## 📊 Final Statistics" >> "$SUMMARY_FILE"
    echo "" >> "$SUMMARY_FILE"
    echo "- **Total PRs processed**: $pr_count" >> "$SUMMARY_FILE"
    echo "- **PRs queued for merge**: $merged" >> "$SUMMARY_FILE"
    echo "- **PRs closed (conflicts)**: $closed" >> "$SUMMARY_FILE"
    echo "" >> "$SUMMARY_FILE"

    log_success "Batch merge completed!"
    log_info "Processed: $pr_count | Merged: $merged | Closed: $closed"

    cat "$SUMMARY_FILE"
}

# Run main
main "$@"
