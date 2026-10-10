#!/bin/bash
set -euo pipefail

# Dependabot monthly batch (#1076)
#
# Collects every green Dependabot PR into ONE branch, so a month of dependency updates is
# built, tested and deployed once instead of once per PR. A pull request to `develop` IS a
# production deploy (CLAUDE.md), so N individual merges meant N releases.
#
# What it does:
#   1. Closes Dependabot PRs whose change is already on develop (included in a previous batch).
#   2. Creates deps/batch-YYYY-MM from develop and merges every open Dependabot PR whose
#      REQUIRED checks are green into it (one merge commit per PR, so a failing bump is
#      attributable). PRs that conflict with an earlier PR of the batch wait for next month.
#   3. Pushes that branch and opens an issue with the link to open the batch PR (and the
#      one-liner to recreate any stuck PRs).
#
# What it deliberately does NOT do:
#   - Rebase or push to Dependabot's branches. A commit pushed with GITHUB_TOKEN has
#     github-actions[bot] as committer, and every run on it lands at `action_required`, so
#     the required checks never run (#1076, #991). PRs stuck that way (or with cancelled
#     checks) are listed for a person to comment `@dependabot recreate`: Dependabot ignores
#     that command from this workflow's token, and then pushes as itself.
#   - Open the batch PR. A PR opened with GITHUB_TOKEN triggers no workflows at all, so it
#     could never build. A human opens it from the issue link. That is also the sign-off
#     before the production deploy.
#
# Usage:
#   DRY_RUN=true  ./scripts/ci/dependabot-batch-merge.sh   # preview, changes nothing remote
#   DRY_RUN=false ./scripts/ci/dependabot-batch-merge.sh
#
# Environment variables:
#   GH_TOKEN      - token with contents/pull-requests/issues write (required)
#   DRY_RUN       - 'true' to preview without pushing, commenting, closing or opening (default: false)
#   BASE_BRANCH   - default: develop
#   BATCH_BRANCH  - default: deps/batch-YYYY-MM (UTC)

DRY_RUN="${DRY_RUN:-false}"
BASE_BRANCH="${BASE_BRANCH:-develop}"
BATCH_BRANCH="${BATCH_BRANCH:-deps/batch-$(date -u +%Y-%m)}"
# Conventional type first: the Doc Drift Check skips chore PRs by title.
PR_TITLE="chore(deps): Dependabot batch $(date -u +%Y-%m)"
SUMMARY_FILE="/tmp/dependabot-batch-merge-summary.md"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Console output goes to STDERR: these helpers are called inside functions whose stdout is
# captured by command substitution, and logging to stdout put the log text into the value.
log_info() { echo -e "${BLUE}ℹ️  $1${NC}" >&2; }
log_success() { echo -e "${GREEN}✅ $1${NC}" >&2; }
log_warn() { echo -e "${YELLOW}⚠️  $1${NC}" >&2; }
log_error() { echo -e "${RED}❌ $1${NC}" >&2; }

run() {
    # Executes a remote-changing command, or prints it in dry-run mode.
    if [ "$DRY_RUN" = "true" ]; then
        log_warn "[DRY RUN] would run: $*"
        return 0
    fi
    "$@"
}

INCLUDED=()
EXCLUDED=()
CLOSED=()
NEEDS_RECREATE=()
DISARMED=()
VERSIONS_SYNCED=false

check_prerequisites() {
    if [ -z "${GH_TOKEN:-}" ]; then
        log_error "GH_TOKEN environment variable not set"
        exit 1
    fi
    for tool in gh git jq; do
        command -v "$tool" >/dev/null || { log_error "$tool not installed"; exit 1; }
    done
    git rev-parse --git-dir >/dev/null 2>&1 || { log_error "not inside a git repository"; exit 1; }
    REPO="${GITHUB_REPOSITORY:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
    OWNER="${REPO%%/*}"
}

# Open Dependabot PRs against BASE_BRANCH, oldest first:
# number<TAB>headRefName<TAB>isDraft<TAB>autoMergeArmed<TAB>title
list_dependabot_prs() {
    gh pr list --repo "$REPO" --author "app/dependabot" --base "$BASE_BRANCH" --state open \
        --limit 100 --json number,headRefName,isDraft,autoMergeRequest,title,createdAt \
        --jq 'sort_by(.createdAt) | .[] | [.number, .headRefName, .isDraft, (.autoMergeRequest != null), .title] | @tsv'
}

# True when merging the PR head into BASE_BRANCH would change nothing: its update is already
# on develop (typically via last month's batch, which is squash-merged).
already_on_base() {
    local head_sha=$1
    local merged_tree
    merged_tree=$(git merge-tree --write-tree "origin/$BASE_BRANCH" "$head_sha" 2>/dev/null) || return 1
    [ "$merged_tree" = "$(git rev-parse "origin/$BASE_BRANCH^{tree}")" ]
}

# Did the runs on the PR's current head wait for approval? (#1076: bot-pushed commits)
awaits_approval() {
    local branch=$1 head_sha=$2
    gh run list --repo "$REPO" --branch "$branch" --limit 30 --json headSha,conclusion \
        --jq "[.[] | select(.headSha == \"$head_sha\" and .conclusion == \"action_required\")] | length" \
        | grep -qv '^0$'
}

# green | pending | failed | cancelled | none, over the PR's REQUIRED checks.
required_status() {
    local number=$1 json
    json=$(gh pr checks "$number" --repo "$REPO" --required --json bucket 2>/dev/null || echo '[]')
    jq -r '
        if length == 0 then "none"
        elif any(.[]; .bucket == "fail") then "failed"
        elif any(.[]; .bucket == "cancel") then "cancelled"
        elif any(.[]; .bucket == "pending") then "pending"
        elif all(.[]; .bucket == "pass" or .bucket == "skipping") then "green"
        else "failed" end' <<< "$json"
}

process_pr() {
    local number=$1 branch=$2 is_draft=$3 auto_merge=$4 title=$5
    log_info "PR #$number: $title"

    # A Dependabot PR must never merge on its own: each merge to develop is a release, which
    # is what this batch exists to avoid. The pre-#1077 job armed auto-merge on every PR, and
    # an armed PR merges the moment its checks turn green (#1044 did, 2026-10-10).
    if [ "$auto_merge" = "true" ]; then
        log_warn "#$number has auto-merge armed, disarming"
        run gh pr merge "$number" --repo "$REPO" --disable-auto
        DISARMED+=("#$number")
    fi

    if [ "$is_draft" = "true" ]; then
        EXCLUDED+=("#$number: draft")
        return
    fi

    git fetch --quiet origin "pull/$number/head"
    local head_sha
    head_sha=$(git rev-parse FETCH_HEAD)

    if already_on_base "$head_sha"; then
        log_success "#$number is already on $BASE_BRANCH, closing"
        run gh pr close "$number" --repo "$REPO" \
            --comment "This update is already on \`$BASE_BRANCH\` (included in a monthly Dependabot batch, #1076). Closing."
        CLOSED+=("#$number")
        return
    fi

    # Judge the REQUIRED checks by their buckets, not by the exit code: `gh pr checks` exits 0
    # when checks are CANCELLED (measured on #1067, 2026-10-10), so the exit code is no gate.
    local status
    status=$(required_status "$number")
    case "$status" in
        green) ;;
        pending)
            EXCLUDED+=("#$number: required checks still running")
            return
            ;;
        none|cancelled)
            # These need `@dependabot recreate`, and only a person can send it: Dependabot answers
            # this workflow's token with "only users with push access can use that command"
            # (measured 2026-10-10). Do NOT approve the stuck runs instead: their actor is
            # github-actions[bot], which the deploy guard does not exclude, so an approved run
            # would deploy a Dependabot branch to production.
            if awaits_approval "$branch" "$head_sha"; then
                EXCLUDED+=("#$number: CI never ran (runs awaited approval); needs \`@dependabot recreate\`")
            else
                EXCLUDED+=("#$number: required checks $status; needs \`@dependabot recreate\`")
            fi
            NEEDS_RECREATE+=("$number")
            return
            ;;
        *)
            EXCLUDED+=("#$number: a required check failed")
            return
            ;;
    esac

    if git merge --no-ff --quiet -m "chore(deps): include #$number ($title)" "$head_sha" >/dev/null 2>&1; then
        log_success "#$number merged into $BATCH_BRANCH"
        INCLUDED+=("#$number $title")
    else
        git merge --abort
        log_warn "#$number conflicts with an earlier PR of this batch"
        EXCLUDED+=("#$number: conflicts with an earlier PR of this batch, next month")
    fi
}

write_summary() {
    {
        echo "# Dependabot monthly batch"
        echo ""
        echo "**Run**: $(date -u '+%Y-%m-%d %H:%M UTC') · **Mode**: $([ "$DRY_RUN" = "true" ] && echo "dry run" || echo "live") · **Branch**: \`$BATCH_BRANCH\`"
        echo ""
        echo "## Included (${#INCLUDED[@]})"
        if [ ${#INCLUDED[@]} -gt 0 ]; then printf -- '- %s\n' "${INCLUDED[@]}"; else echo "_none_"; fi
        echo ""
        echo "## Not included (${#EXCLUDED[@]})"
        if [ ${#EXCLUDED[@]} -gt 0 ]; then printf -- '- %s\n' "${EXCLUDED[@]}"; else echo "_none_"; fi
        echo ""
        if [ ${#NEEDS_RECREATE[@]} -gt 0 ]; then
            echo "**To get the stuck PRs into a batch**, a person with push access runs this, then re-runs the workflow once their CI is green (or waits for next month):"
            echo ""
            echo "\`for n in ${NEEDS_RECREATE[*]}; do gh pr comment \$n --body \"@dependabot recreate\"; done\`"
            echo ""
        fi
        if [ ${#DISARMED[@]} -gt 0 ]; then
            echo "## Auto-merge disarmed (${#DISARMED[@]})"
            printf -- '- %s\n' "${DISARMED[@]}"
            echo ""
        fi
        if [ "$VERSIONS_SYNCED" = "true" ]; then
            echo "**docs/versions.json** was regenerated in a separate commit on the batch branch."
            echo ""
        fi
        echo "## Closed, already on \`$BASE_BRANCH\` (${#CLOSED[@]})"
        if [ ${#CLOSED[@]} -gt 0 ]; then printf -- '- %s\n' "${CLOSED[@]}"; else echo "_none_"; fi
    } > "$SUMMARY_FILE"
}

# A version bump in the batch can drift docs/versions.json, which the required
# "Verify versions.json" check rejects (vitest 4 -> 5 failed #1089). Regenerate it on top.
sync_versions_json() {
    node scripts/update-versions.js >&2
    if git diff --quiet -- docs/versions.json; then
        log_info "docs/versions.json already in sync"
        return
    fi
    git commit --quiet -m "chore(deps): sync docs/versions.json" -- docs/versions.json
    VERSIONS_SYNCED=true
    log_info "docs/versions.json regenerated and committed"
}

# Close the "batch ready" issues that point at a batch branch being rebuilt.
supersede_ready_issues() {
    local issues
    issues=$(gh issue list --repo "$REPO" --state open --author "app/github-actions" \
        --search "\"$BATCH_BRANCH\" in:body" --json number,title \
        --jq '.[] | select(.title | startswith("Dependabot batch")) | .number')
    for issue in $issues; do
        log_info "Closing superseded batch issue #$issue"
        run gh issue close "$issue" --repo "$REPO" --comment "Superseded: \`$BATCH_BRANCH\` was rebuilt by a later run, see the newer batch issue."
    done
}

open_ready_issue() {
    # Prefill the title in the browser path too: without it GitHub uses the branch name, which
    # carries no commit type, and the Doc Drift Check fails the batch PR as type 'unknown'.
    local compare="https://github.com/$REPO/compare/$BASE_BRANCH...$BATCH_BRANCH?expand=1&title=$(jq -rn --arg t "$PR_TITLE" '$t|@uri')"
    local body
    body=$(cat <<EOF
The monthly Dependabot batch is ready on \`$BATCH_BRANCH\`: ${#INCLUDED[@]} update(s), each green on its own CI.

**Open the batch PR (this is the sign-off; opening it deploys to production once):**

- In the browser: $compare
- Or: \`gh pr create --base $BASE_BRANCH --head $BATCH_BRANCH --title "$PR_TITLE" --body "Batch of the updates listed in the tracking issue."\`

It must be opened by a person: a PR opened by the workflow token triggers no CI. After it merges, the next run closes the included Dependabot PRs.

$(sed '1,2d' "$SUMMARY_FILE")
EOF
)
    run gh issue create --repo "$REPO" --title "Dependabot batch $(date -u +%Y-%m) ready: ${#INCLUDED[@]} update(s)" \
        --assignee "$OWNER" --body "$body"
}

main() {
    log_info "Dependabot monthly batch, base=$BASE_BRANCH branch=$BATCH_BRANCH dry_run=$DRY_RUN"
    check_prerequisites
    git fetch --quiet origin "$BASE_BRANCH"

    if git ls-remote --exit-code --heads origin "$BATCH_BRANCH" >/dev/null 2>&1; then
        local open_pr
        open_pr=$(gh pr list --repo "$REPO" --head "$BATCH_BRANCH" --state open --json number -q '.[0].number')
        if [ -n "$open_pr" ]; then
            log_info "Batch PR #$open_pr for $BATCH_BRANCH is already open, nothing to do"
            exit 0
        fi
        # Nobody has opened a PR from it, so it is this job's own unopened batch from an earlier
        # run this month (re-runs are normal: the first run after a stall only asks Dependabot to
        # recreate PRs). Rebuild it from scratch so it carries every PR that is green NOW.
        log_warn "$BATCH_BRANCH exists without an open PR: rebuilding it"
        run git push --quiet origin --delete "$BATCH_BRANCH"
        supersede_ready_issues
    fi

    git -c advice.detachedHead=false checkout --quiet --detach "origin/$BASE_BRANCH"
    git config user.name "github-actions[bot]"
    git config user.email "41898282+github-actions[bot]@users.noreply.github.com"

    local prs
    prs=$(list_dependabot_prs)
    log_info "Found $(printf '%s' "$prs" | grep -c . || true) open Dependabot PR(s) against $BASE_BRANCH"
    while IFS=$'\t' read -r number branch is_draft auto_merge title; do
        [ -n "$number" ] || continue
        process_pr "$number" "$branch" "$is_draft" "$auto_merge" "$title"
    done <<< "$prs"

    if [ ${#INCLUDED[@]} -gt 0 ]; then sync_versions_json; fi

    write_summary
    cat "$SUMMARY_FILE" >&2

    if [ ${#INCLUDED[@]} -eq 0 ]; then
        log_info "Nothing to batch this month"
        if [ ${#NEEDS_RECREATE[@]} -gt 0 ]; then
            # Stay visible: stuck PRs never become green on their own.
            run gh issue create --repo "$REPO" --assignee "$OWNER" \
                --title "Dependabot: ${#NEEDS_RECREATE[@]} PR(s) need @dependabot recreate" \
                --body "$(sed '1,2d' "$SUMMARY_FILE")"
        fi
        exit 0
    fi

    run git push --quiet origin "HEAD:refs/heads/$BATCH_BRANCH"
    open_ready_issue
    log_success "Batch branch $BATCH_BRANCH prepared with ${#INCLUDED[@]} update(s)"
}

main "$@"
