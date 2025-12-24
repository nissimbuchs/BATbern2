<!-- Powered by BMAD™ Core -->

# qa-gate

Create or update a quality gate decision file for a story based on review findings.

## Purpose

Generate a standalone quality gate file that provides a clear pass/fail decision with actionable feedback. This gate serves as an advisory checkpoint for teams to understand quality status.

## Prerequisites

- Story has been reviewed (manually or via review-story task)
- Review findings are available
- Understanding of story requirements and implementation

## Gate File Location

**ALWAYS** check the `bmad-core/core-config.yaml` for the `qa.qaLocation/gates`

Slug rules:

- Convert to lowercase
- Replace spaces with hyphens
- Strip punctuation
- Example: "User Auth - Login!" becomes "user-auth-login"

## Minimal Required Schema

```yaml
schema: 1
story: '{epic}.{story}'
gate: PASS|CONCERNS|FAIL|WAIVED
status_reason: '1-2 sentence explanation of gate decision'
reviewer: 'Quinn'
updated: '{ISO-8601 timestamp}'
top_issues: [] # Empty array if no issues
waiver: { active: false } # Only set active: true if WAIVED
```

## Schema with Issues

```yaml
schema: 1
story: '1.3'
gate: CONCERNS
status_reason: 'Missing rate limiting on auth endpoints poses security risk.'
reviewer: 'Quinn'
updated: '2025-01-12T10:15:00Z'
top_issues:
  - id: 'SEC-001'
    severity: high # ONLY: low|medium|high
    finding: 'No rate limiting on login endpoint'
    suggested_action: 'Add rate limiting middleware before production'
  - id: 'TEST-001'
    severity: medium
    finding: 'No integration tests for auth flow'
    suggested_action: 'Add integration test coverage'
waiver: { active: false }
```

## Schema when Waived

```yaml
schema: 1
story: '1.3'
gate: WAIVED
status_reason: 'Known issues accepted for MVP release.'
reviewer: 'Quinn'
updated: '2025-01-12T10:15:00Z'
top_issues:
  - id: 'PERF-001'
    severity: low
    finding: 'Dashboard loads slowly with 1000+ items'
    suggested_action: 'Implement pagination in next sprint'
waiver:
  active: true
  reason: 'MVP release - performance optimization deferred'
  approved_by: 'Product Owner'
```

## Gate Decision Criteria

### PASS

- All acceptance criteria met
- No high-severity issues
- Test coverage meets project standards

### CONCERNS

- Non-blocking issues present
- Should be tracked and scheduled
- Can proceed with awareness

### FAIL

- Acceptance criteria not met
- High-severity issues present
- Recommend return to InProgress

### WAIVED

- Issues explicitly accepted
- Requires approval and reason
- Proceed despite known issues

## Severity Scale

**FIXED VALUES - NO VARIATIONS:**

- `low`: Minor issues, cosmetic problems
- `medium`: Should fix soon, not blocking
- `high`: Critical issues, should block release

## Issue ID Prefixes

- `SEC-`: Security issues
- `PERF-`: Performance issues
- `REL-`: Reliability issues
- `TEST-`: Testing gaps
- `MNT-`: Maintainability concerns
- `ARCH-`: Architecture issues
- `DOC-`: Documentation gaps
- `REQ-`: Requirements issues

## Output Requirements

1. **ALWAYS** create gate file at: `qa.qaLocation/gates` from `bmad-core/core-config.yaml`
2. **ALWAYS** append this exact format to story's QA Results section:

   ```text
   Gate: {STATUS} → qa.qaLocation/gates/{epic}.{story}-{slug}.yml
   ```

3. Keep status_reason to 1-2 sentences maximum
4. Use severity values exactly: `low`, `medium`, or `high`
5. **NEW - Linear Integration**: If Linear is enabled (`linear.enabled: true` and `linear.postQaToLinear: true` in core-config.yaml):
   - Post QA results to Linear issue as comment
   - Update Linear issue status based on gate decision
   - Add QA label to Linear issue (`qa-pass`, `qa-concerns`, or `qa-fail`)
   - Replace story QA Results section with Linear reference

## Linear Integration Workflow (Optional)

If Linear is enabled in core-config.yaml, perform these additional steps after creating the gate file:

### Step 1: Load Linear Configuration

- Check `linear.enabled: true`
- Check `linear.postQaToLinear: true`
- If both true, proceed with Linear integration
- Extract story's Linear issue ID from story header `**Linear Issue**: [BAT-{N}](url)`

### Step 2: Format QA Results for Linear Comment

Create Linear-formatted markdown comment using this template:

```markdown
## QA Results - {Date}

**Gate Decision**: {PASS|CONCERNS|FAIL}
**Quality Score**: {calculated_score}/100
**Reviewer**: {reviewer_name}

### Executive Summary
{Brief 1-2 sentence summary of gate decision}

### Code Quality Assessment

**Strengths**:
- ✅ {Strength 1}
- ✅ {Strength 2}

**Areas for Improvement**:
{If CONCERNS or FAIL, list issues from top_issues array}
- ⚠️ {Issue 1} (Severity: {severity})
- ⚠️ {Issue 2} (Severity: {severity})

### Test Coverage
- Unit Tests: {count} passing
- Integration Tests: {count} configured
- Coverage: {percent}%

### Requirements Traceability
| AC | Status | Test Count |
|----|--------|------------|
{For each AC from Linear issue description}
| AC{n} | ✅/⚠️/❌ | {count} |

### NFR Validation
- Security: {PASS|FAIL}
- Performance: {PASS|FAIL}

### Recommended Actions
{If issues exist, list suggested_action from top_issues}
1. {Action 1}
2. {Action 2}

---
📊 Gate File: `{qa.qaLocation}/gates/{epic}.{story}-{slug}.yml`
*QA Agent | Powered by BMAD™ Core*
```

### Step 3: Post QA Comment to Linear

- Call `mcp__linear-server__create_comment`:
  - `issueId`: Linear issue ID from story header
  - `body`: Formatted QA results from Step 2
- Capture comment ID for reference

### Step 4: Update Linear Issue Labels

Determine QA label based on gate decision:
- `PASS` → Apply `qa-pass` label
- `CONCERNS` → Apply `qa-concerns` label
- `FAIL` → Apply `qa-fail` label
- `WAIVED` → Apply `qa-concerns` label + custom field

Remove any existing QA labels before applying new one:
- Call `mcp__linear-server__update_issue`:
  - `id`: Linear issue ID
  - `labels`: Updated label array (remove old qa-* labels, add new one)

### Step 5: Update Linear Issue Status

Map gate decision to Linear status update:
- `PASS` → Update status to "Done" (if not already)
- `FAIL` → Update status to "In Progress"
- `CONCERNS` → Keep current status (advisory only)
- `WAIVED` → Update status to "Done"

Call `mcp__linear-server__update_issue`:
- `id`: Linear issue ID
- `state`: Mapped status

### Step 6: Update Story QA Results Section

If Linear integration successful, replace verbose QA Results section with:

```markdown
## QA Results

**Gate**: {PASS|CONCERNS|FAIL|WAIVED}

See Linear Issue: [BAT-{N}]({linear_url}) for detailed QA results and traceability.

Gate File: `{qa.qaLocation}/gates/{epic}.{story}-{slug}.yml`
```

This keeps the story file lean while maintaining full QA traceability in Linear.

## Example Story Update

After creating gate file, append to story's QA Results section:

```markdown
## QA Results

### Review Date: 2025-01-12

### Reviewed By: Quinn (Test Architect)

[... existing review content ...]

### Gate Status

Gate: CONCERNS → qa.qaLocation/gates/{epic}.{story}-{slug}.yml
```

## Key Principles

- Keep it minimal and predictable
- Fixed severity scale (low/medium/high)
- Always write to standard path
- Always update story with gate reference
- Clear, actionable findings
