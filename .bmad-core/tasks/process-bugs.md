<!-- Powered by BMAD™ Core -->

# process-bugs

Automatically fetch and process all open bugs from GitHub Issues with `acceptance-testing` label. This task is designed for the Dev agent to systematically work through bugs reported during acceptance testing, prioritize by severity, and fix each one while maintaining full traceability.

## Purpose

- Fetch all open bugs from GitHub Issues
- Filter by story ID (optional) or process all acceptance testing bugs
- Prioritize bugs by severity (Critical → High → Medium → Low)
- Process each bug systematically using the `fix-bug` task
- Update story files with fixes
- Maintain GitHub issue traceability

## Prerequisites

- `gh` CLI installed and authenticated (`gh auth status`)
- Repository must have GitHub Issues enabled
- Story file exists for bugs being processed
- All tests currently passing (clean baseline)

## Inputs

```yaml
optional:
  - story_id: '{epic}.{story}' # e.g., "1.14" - filter bugs for specific story
  - severity_filter: 'Critical|High|Medium|Low' # Only process bugs at or above this severity
  - max_bugs: number # Maximum number of bugs to process in this run (default: all)
  - auto_close: boolean # Auto-close issues when tests pass (default: true)
```

## Process (Do not skip steps)

### 0) Validate Prerequisites

- Check `gh` CLI is available: `gh --version`
- Verify authentication: `gh auth status`
- Check if connected to correct repository
- HALT if any prerequisite fails

### 1) Fetch Open Bugs from GitHub

Use `gh` CLI to fetch all open bugs with acceptance-testing label:

```bash
# Fetch all acceptance testing bugs
gh issue list --label "acceptance-testing" --state open --json number,title,labels,body --limit 100

# If story_id provided, filter by story label
gh issue list --label "acceptance-testing" --label "story:${story_id}" --state open --json number,title,labels,body --limit 100
```

**Parse response:**
- Extract issue numbers, titles, severity labels, story references
- HALT if no bugs found (report: "No open acceptance testing bugs found")

### 2) Prioritize Bugs

Sort bugs by severity using labels:
1. **Critical** (`severity:critical` label) - Process first
2. **High** (`severity:high` label)
3. **Medium** (`severity:medium` label)
4. **Low** (`severity:low` label)
5. **Unlabeled** - Default to Medium priority

Apply `severity_filter` if provided:
- If `severity_filter: High`, only process Critical and High bugs
- If `severity_filter: Critical`, only process Critical bugs

Apply `max_bugs` limit if provided:
- Process top N bugs from prioritized list

### 3) Process Each Bug

For each bug in prioritized order:

a) **Notify Start:**
   ```bash
   gh issue comment ${issue_number} --body "🤖 Dev Agent Processing

   Starting automated fix for this bug.

   Agent: dev
   Model: ${model_name}
   Status: In Progress"
   ```

b) **Execute fix-bug task:**
   - Call `fix-bug.md` task with issue_number parameter
   - Task will read issue, implement fix, run tests, update story
   - Capture success/failure status

c) **Handle Results:**

   **If fix-bug succeeds:**
   - If `auto_close: true` and all tests pass:
     ```bash
     gh issue comment ${issue_number} --body "✅ Bug Fixed

     Fix implemented and all tests passing.

     ${fix_summary_from_task}

     Closing issue."

     gh issue close ${issue_number}
     ```
   - If `auto_close: false`:
     ```bash
     gh issue comment ${issue_number} --body "✅ Fix Implemented

     Fix completed and tests passing.

     ${fix_summary_from_task}

     Please verify and close manually."

     # Add label 'ready-for-verification'
     gh issue edit ${issue_number} --add-label "ready-for-verification"
     ```

   **If fix-bug fails:**
   - Do not close issue
   - Comment with failure reason:
     ```bash
     gh issue comment ${issue_number} --body "⚠️ Fix Attempt Failed

     Unable to fully resolve this bug automatically.

     ${failure_reason}

     Recommended Action:
     ${suggested_next_steps}

     Issue remains open for manual investigation."

     # Add label 'needs-manual-fix'
     gh issue edit ${issue_number} --add-label "needs-manual-fix"
     ```

### 4) Generate Summary Report

After processing all bugs, create summary:

```markdown
## Bug Processing Summary

**Run Date:** ${date}
**Story Filter:** ${story_id or "All stories"}
**Severity Filter:** ${severity_filter or "All severities"}

### Bugs Processed: ${total_processed}

**Successfully Fixed:** ${fixed_count}
${list_of_fixed_issues}

**Failed to Fix:** ${failed_count}
${list_of_failed_issues}

**Skipped (below severity filter):** ${skipped_count}

### Next Steps
${recommendations}
```

Output summary to user and optionally write to `.ai/debug-log.md`

### 5) Update Story Files

For stories with fixed bugs:
- Update story status if needed
- Ensure File List is current
- Add entry to Change Log documenting bug fixes
- Update Dev Agent Record with bug fix session details

## Blocking Conditions

- `gh` CLI not installed or not authenticated
- No bugs found with `acceptance-testing` label
- Story file not found for bug with story label
- Base test suite failing (cannot establish clean baseline)

## Success Criteria

- All prioritized bugs processed (fixed or marked needs-manual-fix)
- GitHub issues updated with comments and status
- Story files updated with changes
- Test suite remains passing
- Summary report generated

## Configuration

Reads from `bmad-core/core-config.yaml`:
```yaml
bugs:
  system: github-issues
  labels:
    bug: bug
    acceptanceTesting: acceptance-testing
    severityCritical: severity:critical
    severityHigh: severity:high
    severityMedium: severity:medium
    severityLow: severity:low
  autoProcessOnStoryComplete: true
  autoCloseIssues: true
```

## Example Usage

**Process all bugs for story 1.14:**
```
User: *process-bugs story_id=1.14
Agent: Fetching bugs for story 1.14...
Agent: Found 3 bugs: 2 High, 1 Medium
Agent: Processing bug #123 (High): "Search cache not working"...
Agent: ✅ Fixed bug #123, all tests passing
Agent: Processing bug #124 (High): "Logo upload timeout"...
Agent: ✅ Fixed bug #124, all tests passing
Agent: Processing bug #125 (Medium): "Minor UI alignment"...
Agent: ✅ Fixed bug #125, all tests passing
Agent: Summary: 3 bugs fixed, 0 failed
```

**Process only critical bugs across all stories:**
```
User: *process-bugs severity_filter=Critical
Agent: Fetching critical bugs across all stories...
Agent: Found 1 critical bug in story 2.5
Agent: Processing bug #130 (Critical): "Authentication bypass"...
Agent: ✅ Fixed bug #130, all tests passing
Agent: Summary: 1 bug fixed
```

## Integration with develop-story

When `autoProcessOnStoryComplete: true` in config, the `develop-story` command will automatically run this task after all story tasks are completed and before marking status "Ready for Review".

This ensures all acceptance testing bugs are addressed before submitting for review.

## Key Principles

- **Automation First**: Attempt to fix all bugs automatically
- **Traceability**: All changes tracked in GitHub issues
- **Prioritization**: Critical bugs always processed first
- **Safety**: Never auto-close if tests fail
- **Transparency**: Detailed comments on every issue
- **Fallback**: Mark for manual fix if automation fails
