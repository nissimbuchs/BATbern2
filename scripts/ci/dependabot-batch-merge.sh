#!/bin/bash
set -euo pipefail

# Dependabot Batch Merge Script
# Automatically merges open dependabot PRs in batches to avoid conflicts
#
# Usage:
#   # A PR armed for auto-merge this long without merging means the required checks
# are not completing. Override with STALLED_AFTER_DAYS=n.
STALLED_AFTER_DAYS="${STALLED_AFTER_DAYS:-3}"

DRY_RUN=false ./scripts/ci/dependabot-batch-merge.sh
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
        --json number,title,headRefName,updatedAt,mergeable,autoMergeRequest \
        --jq 'sort_by(.updatedAt) | .[]' \
        > /tmp/dependabot-prs.json

    local pr_count=$(jq -s 'length' /tmp/dependabot-prs.json)
    log_info "Found $pr_count open dependabot PRs"

    echo "$pr_count"
}

# Attempt to update PR branch (rebase onto develop)
update_pr() {
    local pr_number=$1
    local pr_title=$2

    log_info "Updating branch for PR #$pr_number: $pr_title"

    if [ "$DRY_RUN" = "true" ]; then
        log_warn "[DRY RUN] Would rebase PR #$pr_number onto develop"
        return 0
    fi

    # Actually rebase the PR branch onto the base branch
    if gh pr update-branch "$pr_number" --rebase 2>/dev/null; then
        log_success "PR #$pr_number branch rebased onto develop"
        # Allow a moment for GitHub to register the update
        sleep 5
        local mergeable=$(gh pr view "$pr_number" --json mergeable --jq '.mergeable')
        if [ "$mergeable" = "MERGEABLE" ]; then
            return 0
        else
            log_warn "PR #$pr_number still has conflicts after rebase"
            return 1
        fi
    else
        log_warn "Failed to rebase PR #$pr_number — checking for merge conflicts"
        local mergeable=$(gh pr view "$pr_number" --json mergeable --jq '.mergeable')
        if [ "$mergeable" = "CONFLICTING" ]; then
            return 1
        fi
        # UNKNOWN state means CI hasn't run yet — treat as updatable
        return 0
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
    local armed_at=$(echo "$pr_data" | jq -r '.autoMergeRequest.enabledAt // empty')

    echo "" >> "$SUMMARY_FILE"
    log_info "Processing PR #$pr_number: $pr_title"

    # A PR that has been armed for auto-merge for days and is STILL open means the
    # required checks are not completing — arming is not merging. Surface it loudly:
    # reporting "merged" for these is what hid a 22-PR jam for six weeks (issue #877).
    if [ -n "$armed_at" ]; then
        local armed_epoch=$(date -d "$armed_at" +%s 2>/dev/null || echo 0)
        local now_epoch=$(date +%s)
        local age_days=$(( (now_epoch - armed_epoch) / 86400 ))
        if [ "$armed_epoch" -gt 0 ] && [ "$age_days" -ge "$STALLED_AFTER_DAYS" ]; then
            log_error "PR #$pr_number has had auto-merge armed since $armed_at ($age_days days) and is still open"
            echo "- ⚠️ **#$pr_number stalled**: auto-merge armed $age_days days ago, still not merged" >> "$SUMMARY_FILE"
            return 2
        fi
    fi

    if [ "$mergeable" = "CONFLICTING" ]; then
        close_pr "$pr_number" "Merge conflicts detected. Will be recreated in next dependabot run."
        return 1
    fi

    # Always rebase onto develop first so CI re-runs with up-to-date base
    # (branch protection has strict:true — PRs behind develop won't auto-merge)
    if ! update_pr "$pr_number" "$pr_title"; then
        close_pr "$pr_number" "Merge conflicts after rebase. Will be recreated in next dependabot run."
        return 1
    fi

    # Propagate the result. This used to be `enable_auto_merge ...; return 0`, so a
    # FAILED arming was still counted as a success — e.g. `gh pr merge --auto` refuses
    # draft PRs, and every one of them would have been reported as fine.
    if enable_auto_merge "$pr_number"; then
        return 0
    fi
    return 3
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
    local armed=0
    local closed=0
    local stalled=0
    local failed=0

    while IFS= read -r pr_data; do
        process_pr "$pr_data"
        case $? in
            0) ((armed++))   || true ;;
            2) ((stalled++)) || true ;;
            3) ((failed++))  || true ;;
            *) ((closed++))  || true ;;
        esac

        # Brief delay between PRs to avoid rate limiting
        sleep 3
    done < /tmp/dependabot-prs.json

    # Final summary
    echo "" >> "$SUMMARY_FILE"
    echo "---" >> "$SUMMARY_FILE"
    echo "" >> "$SUMMARY_FILE"
    echo "## 📊 Final Statistics" >> "$SUMMARY_FILE"
    echo "" >> "$SUMMARY_FILE"
    echo "- **Total PRs processed**: $pr_count" >> "$SUMMARY_FILE"
    echo "- **Auto-merge ARMED (not merged — merges when required checks pass)**: $armed" >> "$SUMMARY_FILE"
    echo "- **STALLED (armed >= ${STALLED_AFTER_DAYS}d, still open)**: $stalled" >> "$SUMMARY_FILE"
    echo "- **Failed to arm**: $failed" >> "$SUMMARY_FILE"
    echo "- **PRs closed (conflicts)**: $closed" >> "$SUMMARY_FILE"
    echo "" >> "$SUMMARY_FILE"

    log_info "Processed: $pr_count | Armed: $armed | Stalled: $stalled | Failed: $failed | Closed: $closed"

    if [ "$stalled" -gt 0 ] || [ "$failed" -gt 0 ]; then
        echo "> **$stalled PR(s) have had auto-merge armed for >= ${STALLED_AFTER_DAYS} days and have still not merged.**" >> "$SUMMARY_FILE"
        echo "> Arming is not merging: GitHub merges only once every REQUIRED check reports." >> "$SUMMARY_FILE"
        echo "> Check that Dependabot's workflow runs are not sitting at \`action_required\` — see issue #877." >> "$SUMMARY_FILE"
        log_error "$stalled PR(s) stalled — failing so this is visible instead of reported as success"
        cat "$SUMMARY_FILE"
        exit 1
    fi

    log_success "Batch merge completed!"

    cat "$SUMMARY_FILE"
}

# Run main
main "$@"
