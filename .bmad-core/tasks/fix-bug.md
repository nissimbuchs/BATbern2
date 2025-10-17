<!-- Powered by BMAD™ Core -->

# fix-bug

Fix a single bug reported in GitHub Issues. This task is designed for the Dev agent to read a bug report from GitHub, reproduce the issue, implement a fix following TDD principles, validate the fix with tests, and update relevant documentation while maintaining full traceability.

## Purpose

- Read bug details from a specific GitHub issue
- Reproduce the bug to understand the failure
- Implement fix following TDD red-green-refactor cycle
- Validate fix with existing and new tests
- Update story file Dev Agent Record
- Return success/failure status for automation

## Prerequisites

- `gh` CLI installed and authenticated
- Issue exists and is accessible
- Story file exists for the related story
- Test suite currently passing (clean baseline)

## Inputs

```yaml
required:
  - issue_number: number # GitHub issue number to fix

optional:
  - story_id: '{epic}.{story}' # Override story ID if not in issue labels
  - skip_tests: boolean # Skip test creation (only for trivial fixes, default: false)
  - dry_run: boolean # Analyze and plan but don't implement (default: false)
```

## Process (Do not skip steps)

### 0) Fetch Bug Details from GitHub

```bash
gh issue view ${issue_number} --json number,title,body,labels,author --jq '.'
```

**Parse issue data:**
- Extract story ID from labels (e.g., `story:1.14`) or use provided `story_id`
- Extract severity from labels (e.g., `severity:high`)
- Parse issue body for:
  - Bug description
  - Steps to reproduce
  - Expected behavior
  - Actual behavior
  - Acceptance criteria violated
  - Environment details
  - Error logs

**Validation:**
- HALT if issue not found
- HALT if story ID cannot be determined
- WARN if acceptance criteria not specified (but continue)

### 1) Locate Related Story File

- Read `bmad-core/core-config.yaml` to get `devStoryLocation`
- Find story file: `${devStoryLocation}/${story_id}*.md`
- HALT if story file not found

### 2) Analyze Bug Context

Read story file to understand:
- Which acceptance criteria is violated (from bug report)
- Existing test specifications
- Architecture context
- Implementation guidance

**Reproduce the Bug:**
- Follow "Steps to Reproduce" from issue
- Identify which test should catch this (if any)
- Determine if this is:
  - Missing test coverage (no test exists)
  - Failing existing test (test exists but code is broken)
  - Edge case not covered (test incomplete)

### 3) Create Fix Plan (TDD Approach)

Determine fix approach:

**If bug reveals missing test coverage:**
1. Write failing test that reproduces the bug (RED phase)
2. Implement fix to make test pass (GREEN phase)
3. Refactor if needed (REFACTOR phase)

**If existing test should catch but doesn't:**
1. Enhance existing test to catch the bug (RED phase)
2. Implement fix to make test pass (GREEN phase)
3. Refactor if needed (REFACTOR phase)

**If test exists and is failing:**
1. Fix the code to make test pass (GREEN phase)
2. Refactor if needed (REFACTOR phase)

Create deterministic plan:
```markdown
## Fix Plan for Issue #${issue_number}

**Bug:** ${bug_title}
**Story:** ${story_id}
**AC Violated:** ${ac_number}

### Root Cause
${analysis_of_root_cause}

### Fix Approach
1. ${step_1}
2. ${step_2}
3. ${step_3}

### Tests to Add/Modify
- ${test_file_1}: ${what_to_add}
- ${test_file_2}: ${what_to_modify}

### Files to Modify
- ${source_file_1}: ${what_to_change}
- ${source_file_2}: ${what_to_change}

### Expected Outcome
${description_of_fix}
```

**If `dry_run: true`:**
- Output fix plan to user
- HALT (do not implement)

### 4) Implement Fix (TDD RED Phase)

**Write/Update Test First:**
- Create or modify test file identified in fix plan
- Ensure test reproduces the bug (test should fail)
- Follow project testing conventions (see `docs/architecture/coding-standards.md`)
- Run test to verify it fails with expected error

Example:
```bash
# For Java/Gradle project
./gradlew test --tests CompanySearchServiceTest.shouldCacheSearchResults

# For Node/Deno project
deno test ${test_file_path}

# For Playwright E2E
npx playwright test ${test_file_path}
```

**Verify test failure:**
- HALT if test passes unexpectedly (can't reproduce bug)
- Continue if test fails as expected

### 5) Implement Fix (TDD GREEN Phase)

**Apply Code Changes:**
- Implement minimum code needed to make test pass
- Follow architecture patterns from story file
- Adhere to coding standards (see `devLoadAlwaysFiles` in core-config)
- Keep changes focused and minimal

**Validate Fix:**
```bash
# Run the specific test that was failing
${test_command} --tests ${failing_test}

# Run full test suite to ensure no regressions
${test_command} --all
```

**Iteration:**
- If test still fails: refine implementation and retry
- If test passes but others fail: fix regressions
- Maximum 3 iteration attempts before marking as "needs-manual-fix"

### 6) Refactor (TDD REFACTOR Phase)

**Code Quality:**
- Check for code duplication
- Verify naming conventions
- Ensure proper error handling
- Follow SOLID principles

**Run Full Validation:**
```bash
# Linting
${lint_command}

# Full test suite
${test_command} --all

# Build (if applicable)
${build_command}
```

**Quality Gates:**
- All tests passing (100%)
- No linting errors
- No build errors
- Code follows project standards

HALT if any quality gate fails after 3 attempts → mark as "needs-manual-fix"

### 7) Update Story File (Dev Agent Record ONLY)

**CRITICAL:** Only update these story file sections:
- **Tasks / Subtasks Checkboxes:** Add checkbox for bug fix if not exists
- **Dev Agent Record → Debug Log References:** Add test run results
- **Dev Agent Record → Completion Notes:** Document bug fix details
- **Dev Agent Record → File List:** Add all modified/created files
- **Change Log:** Add entry for bug fix

**Do NOT modify:** Story, Acceptance Criteria, QA Results, Testing sections

**Example Change Log Entry:**
```markdown
| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-17 | 2.3 | **Bug Fix**: Resolved issue #${issue_number} - ${bug_title}. Fixed ${what_was_fixed}. Added test coverage in ${test_file}. | Claude (Dev Agent) |
```

**Example Completion Notes:**
```markdown
**Bug Fix #${issue_number} - ${bug_title}:**
- Root cause: ${root_cause}
- Fix applied: ${fix_description}
- Tests added: ${test_files}
- All tests passing: ✅
```

### 8) Return Status

Return structured status for automation:

**Success:**
```json
{
  "status": "success",
  "issue_number": ${issue_number},
  "story_id": "${story_id}",
  "files_changed": ["file1.java", "file2.java", "Test1.java"],
  "tests_added": 2,
  "tests_modified": 1,
  "fix_summary": "Fixed cache invalidation in CompanySearchService. Added test coverage for cache behavior."
}
```

**Failure:**
```json
{
  "status": "failed",
  "issue_number": ${issue_number},
  "story_id": "${story_id}",
  "failure_reason": "Unable to reproduce bug in test environment",
  "suggested_action": "Requires manual investigation - bug may be environment-specific",
  "attempts": 3
}
```

## Blocking Conditions

- GitHub issue not found or inaccessible
- Story ID cannot be determined
- Story file not found
- Unable to reproduce bug after 3 attempts
- Tests still failing after 3 fix attempts
- Regression in unrelated tests cannot be resolved

## Success Criteria

- Bug reproduced in test (test fails before fix)
- Test passes after fix implementation
- Full test suite passing (no regressions)
- Linting passes
- Build successful (if applicable)
- Story file updated (allowed sections only)
- Clear traceability from issue to fix

## Configuration

Uses project-specific commands from story file and core-config:
- Test command: Determined from project type (Gradle, npm, Deno, etc.)
- Lint command: Determined from project setup
- Build command: Determined from project setup

## Example Usage

**Fix a specific bug:**
```
User: *fix-bug issue_number=123
Agent: Fetching bug details from GitHub issue #123...
Agent: Bug: "Company search cache not working"
Agent: Story: 1.14, AC Violated: AC9
Agent: Reproducing bug...
Agent: Writing failing test in CompanySearchServiceTest.java...
Agent: Test fails as expected ✓
Agent: Implementing fix in CompanySearchService.java...
Agent: Running tests... All pass ✓
Agent: Updating story file...
Agent: ✅ Bug #123 fixed successfully
```

**Analyze bug without fixing (dry run):**
```
User: *fix-bug issue_number=124 dry_run=true
Agent: Fetching bug details from GitHub issue #124...
Agent: Analyzing bug...
Agent: Fix Plan:
  - Root cause: Missing cache invalidation on company update
  - Fix: Add searchService.invalidateCache() call in CompanyService.updateCompany()
  - Tests: Add test shouldInvalidateSearchCache_when_companyUpdated
  - Files: CompanyService.java, CompanySearchServiceTest.java
Agent: Dry run complete - no changes made
```

## Integration Points

- Called by `process-bugs.md` task for batch bug fixing
- Can be invoked directly by user for single bug fix
- Returns status for automation and logging
- Updates story files maintaining traceability chain

## Key Principles

- **TDD First**: Always write/update test before fixing code
- **Minimal Changes**: Fix only what's needed for this bug
- **Quality Gates**: All tests pass, no regressions
- **Traceability**: Clear link from issue → fix → test → story
- **Automation-Friendly**: Returns structured status for tooling
- **Safe Failure**: Mark for manual fix rather than force broken solution
