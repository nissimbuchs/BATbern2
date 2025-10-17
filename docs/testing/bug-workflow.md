# Bug Reporting & Resolution Workflow

This document describes how to report bugs during acceptance testing and how the dev agent automatically processes and fixes them using GitHub Issues.

## Overview

The BATbern project uses **GitHub Issues** as the bug tracking system, integrated with the BMAD dev agent for automated bug fixing. When you report a bug during acceptance testing, the dev agent can automatically fetch, prioritize, and fix bugs with full traceability.

## For Testers: Reporting Bugs

### Quick Bug Report (During Acceptance Testing)

1. Go to GitHub repository: https://github.com/your-org/BATbern-develop/issues/new/choose
2. Select **"Acceptance Testing Bug"** template
3. Fill in the form:
   - **Story ID**: Enter the story number (e.g., `1.14`, `2.5.1`)
   - **Severity**: Choose Critical, High, Medium, or Low
   - **What's broken?**: Brief description of the issue
   - **How to reproduce**: Quick steps to reproduce the bug
   - **AC Violated**: Which acceptance criteria is violated (e.g., `AC9`)
   - **Test File Reference** (optional): E2E test file where this should be caught
   - **Additional Notes** (optional): Screenshots, logs, etc.
4. Click **"Submit new issue"**

The issue will be automatically labeled with `bug` and `acceptance-testing`.

### Detailed Bug Report

For complex bugs requiring detailed information:

1. Go to GitHub repository issues page
2. Select **"Bug Report"** template
3. Fill in comprehensive details:
   - Related story
   - Severity and bug type
   - Full description
   - Detailed reproduction steps
   - Expected vs. actual behavior
   - Environment details
   - Error logs/screenshots
4. Submit the issue

## Bug Labels

The system uses these labels to organize and prioritize bugs:

### Required Labels (Auto-applied)
- `bug` - Marks this as a bug issue
- `acceptance-testing` - Indicates bug found during acceptance testing

### Severity Labels (You select)
- `severity:critical` - System unusable, data loss, security issue
- `severity:high` - Major feature broken, no workaround
- `severity:medium` - Feature impaired, workaround exists
- `severity:low` - Minor issue, cosmetic

### Story Labels (Auto-derived or manual)
- `story:1.14` - Links bug to specific story
- `story:2.5` - etc.

### Status Labels (Agent-applied)
- `ready-for-verification` - Fix complete, awaiting manual verification
- `needs-manual-fix` - Agent couldn't fix automatically

## For Developers: Agent Commands

The dev agent provides two commands for bug fixing:

### 1. Process All Bugs (`*process-bugs`)

Automatically fetch and fix all open acceptance testing bugs.

**Usage:**
```
*process-bugs
```

**With filters:**
```
*process-bugs story_id=1.14
*process-bugs severity_filter=High
*process-bugs story_id=1.14 severity_filter=Critical max_bugs=5
```

**What it does:**
1. Fetches all open bugs with `acceptance-testing` label
2. Filters by story and/or severity if specified
3. Prioritizes: Critical → High → Medium → Low
4. For each bug:
   - Reads bug description from GitHub
   - Reproduces the issue
   - Writes failing test
   - Implements fix
   - Validates all tests pass
   - Updates GitHub issue with progress
   - Closes issue (if `autoCloseIssues: true` in config)
5. Generates summary report

**Example output:**
```
Fetching bugs for story 1.14...
Found 3 bugs: 2 High, 1 Medium

Processing bug #123 (High): "Search cache not working"...
✅ Fixed bug #123, all tests passing

Processing bug #124 (High): "Logo upload timeout"...
✅ Fixed bug #124, all tests passing

Processing bug #125 (Medium): "Minor UI alignment"...
✅ Fixed bug #125, all tests passing

Summary: 3 bugs fixed, 0 failed
```

### 2. Fix Single Bug (`*fix-bug`)

Fix a specific bug by issue number.

**Usage:**
```
*fix-bug issue_number=123
```

**With options:**
```
*fix-bug issue_number=123 story_id=1.14
*fix-bug issue_number=124 dry_run=true
```

**What it does:**
1. Fetches bug details from GitHub issue #123
2. Analyzes the bug and creates fix plan
3. If `dry_run=true`: Shows plan without implementing
4. Otherwise: Implements fix following TDD approach
5. Updates GitHub issue with fix details
6. Updates story file Dev Agent Record

**Example output:**
```
Fetching bug details from GitHub issue #123...
Bug: "Company search cache not working"
Story: 1.14, AC Violated: AC9

Reproducing bug...
Writing failing test in CompanySearchServiceTest.java...
Test fails as expected ✓

Implementing fix in CompanySearchService.java...
Running tests... All pass ✓

Updating story file...
✅ Bug #123 fixed successfully
```

## Automatic Bug Processing

The dev agent is configured to automatically process bugs when a story is completed.

**Configuration** (in `.bmad-core/core-config.yaml`):
```yaml
bugs:
  autoProcessOnStoryComplete: true  # Run *process-bugs after story tasks done
  autoCloseIssues: true              # Close GitHub issues when tests pass
```

**Workflow:**
1. You implement story tasks using `*develop-story`
2. All story tasks complete, all tests pass
3. Agent automatically runs `*process-bugs` for current story
4. Agent fixes all open acceptance testing bugs
5. Agent marks story "Ready for Review"

You can disable this by setting `autoProcessOnStoryComplete: false`.

## Bug Lifecycle

### 1. Bug Reported
- Tester creates GitHub issue using template
- Issue labeled: `bug`, `acceptance-testing`, `severity:*`, `story:*`
- Issue status: **Open**

### 2. Bug Processing Started
- Dev agent comments on issue: "🤖 Dev Agent Processing"
- Agent analyzes and reproduces bug

### 3. Bug Fixed
- Agent implements fix following TDD
- All tests pass
- Agent comments on issue: "✅ Bug Fixed" with fix summary
- If `autoCloseIssues: true`:
  - Issue status: **Closed**
- If `autoCloseIssues: false`:
  - Label added: `ready-for-verification`
  - Issue remains open for manual verification

### 4. Bug Cannot Be Fixed Automatically
- Agent comments on issue: "⚠️ Fix Attempt Failed"
- Label added: `needs-manual-fix`
- Issue remains open for manual investigation

### 5. Manual Verification (Optional)
- Tester verifies fix in environment
- If verified: Close issue manually
- If not fixed: Comment on issue, agent will retry

## Best Practices

### For Testers

1. **Use the right template**
   - Quick testing sessions: "Acceptance Testing Bug"
   - Complex issues: "Bug Report"

2. **Provide clear reproduction steps**
   - Numbered steps
   - Include expected vs. actual behavior
   - Reference acceptance criteria violated

3. **Set accurate severity**
   - Critical: Blocks testing or causes data loss
   - High: Major feature broken
   - Medium: Workaround available
   - Low: Minor annoyance

4. **Link to story**
   - Always include story ID
   - Helps agent filter and prioritize

5. **Attach evidence**
   - Screenshots for UI bugs
   - Error logs for backend bugs
   - Network traces for API bugs

### For Developers

1. **Let agent handle bugs automatically**
   - Run `*develop-story` as normal
   - Agent processes bugs at end of story

2. **Process bugs incrementally**
   - Don't wait until all stories done
   - Process bugs after each story: `*process-bugs story_id=X.Y`

3. **Use dry_run for complex bugs**
   - `*fix-bug issue_number=123 dry_run=true`
   - Review fix plan before implementing

4. **Check agent comments on GitHub**
   - Agent updates issues with progress
   - Review fix summaries for quality

5. **Handle manual-fix bugs promptly**
   - Filter issues by `needs-manual-fix` label
   - Investigate root cause
   - Implement fix manually
   - Close issue with explanation

## Troubleshooting

### Agent can't find bugs
- **Check labels**: Issue must have `acceptance-testing` label
- **Check filters**: Story ID or severity filter may be too restrictive
- Run: `gh issue list --label "acceptance-testing" --state open` to see all bugs

### Agent can't reproduce bug
- **Check reproduction steps**: Must be clear and complete
- **Check environment**: Bug may be environment-specific
- **Check test data**: May need specific data setup
- Agent will add `needs-manual-fix` label

### Agent fix causes test failures
- **Check for regressions**: Agent runs full test suite
- **Check fix scope**: May need broader changes
- Agent will not close issue if tests fail

### Issue not auto-closing
- **Check config**: `autoCloseIssues` may be `false`
- **Check test status**: Issue only closes if all tests pass
- **Check GitHub permissions**: Agent needs write access

## GitHub CLI Setup

The agent requires `gh` CLI to be installed and authenticated.

### Installation
```bash
# macOS
brew install gh

# Linux
sudo apt install gh

# Windows
winget install GitHub.cli
```

### Authentication
```bash
gh auth login
```

Follow prompts to authenticate with your GitHub account.

### Verify
```bash
gh auth status
```

Should show: "Logged in to github.com as [username]"

## Configuration Reference

**Location**: `.bmad-core/core-config.yaml`

```yaml
bugs:
  system: github-issues                    # Bug tracking system (only github-issues supported)
  labels:
    bug: bug                               # Label for all bugs
    acceptanceTesting: acceptance-testing  # Label for acceptance testing bugs
    severityCritical: severity:critical    # Critical severity label
    severityHigh: severity:high            # High severity label
    severityMedium: severity:medium        # Medium severity label
    severityLow: severity:low              # Low severity label
  autoProcessOnStoryComplete: true         # Auto-run *process-bugs after story done
  autoCloseIssues: true                    # Close issues when tests pass
  maxAutoFixAttempts: 3                    # Max attempts before marking needs-manual-fix
```

## Examples

### Example 1: Critical Bug During Testing

**Scenario**: You discover company search returns wrong results.

**Steps:**
1. Create issue using "Acceptance Testing Bug" template:
   - Story ID: `1.14`
   - Severity: `Critical`
   - What's broken?: "Company search returns competitors instead of searched company"
   - Reproduce: "1. Search 'Acme Corp', 2. Results show 'Beta Inc' instead"
   - AC Violated: `AC5`

2. Issue #145 created with labels: `bug`, `acceptance-testing`, `severity:critical`, `story:1.14`

3. Dev agent (when running `*process-bugs story_id=1.14`):
   - Fetches issue #145
   - Identifies root cause: Wrong SQL query in CompanyRepository
   - Writes failing test: `shouldReturnExactMatch_when_companyNameProvided`
   - Fixes query in `CompanyRepository.java`
   - All tests pass
   - Comments on #145: "✅ Fixed: Updated search query to use exact match..."
   - Closes #145

### Example 2: Medium Bug Found in E2E Test

**Scenario**: E2E test catches logo upload showing wrong size limit.

**Steps:**
1. Create issue using "Bug Report" template:
   - Related Story: `1.14`
   - Severity: `Medium`
   - Bug Type: `UI/UX`
   - Description: "Upload form shows 2MB limit but backend accepts 5MB"
   - Steps: "1. Go to company logo upload, 2. See error message"
   - Expected: "Error should say 'Max 5MB' per AC8"
   - Actual: "Error says 'Max 2MB'"
   - Test File: `company-creation.spec.ts:67`

2. Issue #146 created

3. Dev agent processes bug:
   - Updates constant in `CompanyLogoUpload.tsx`
   - Updates error message translation in `en.json`
   - Updates E2E test expectation
   - All tests pass
   - Closes #146

## Support

For questions or issues with the bug workflow:
- Check this documentation
- Review `.bmad-core/tasks/process-bugs.md` and `fix-bug.md`
- Ask in team chat or create discussion issue
