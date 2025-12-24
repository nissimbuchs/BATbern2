<!-- Powered by BMAD™ Core -->

# Sync Story to Linear Task

## Purpose

To create or update a Linear issue for a story file, establishing bidirectional linking between the story file and Linear issue. This task ensures product visibility, roadmap tracking, and stakeholder-friendly acceptance criteria tracking in Linear while maintaining the story file as the source of truth for implementation details.

## Prerequisites

- Story file exists at `{devStoryLocation}/{story_id}.{slug}.md` or `{devStoryLocation}/BAT-{N}.{slug}.md`
- Linear MCP server is configured and authenticated
- `.bmad-core/core-config.yaml` contains Linear configuration

## Parameters

- `story_file_path` (required): Path to the story markdown file (e.g., `docs/stories/2.5.3.event-management-frontend.md` or `docs/stories/BAT-5.event-management-frontend.md`)
- `create_new` (optional): Set to `true` to force creating a new Linear issue even if one exists (default: `false`)

## SEQUENTIAL Task Execution

### 0. Load Configuration

- Load `.bmad-core/core-config.yaml` from the project root
- Verify `linear.enabled: true`
- Extract Linear configuration:
  - `linear.team`: Team name
  - `linear.teamId`: Team UUID
  - `linear.issuePrefix`: Issue prefix (e.g., "BAT")
  - `linear.statusMapping`: Status mapping dictionary
  - `linear.autoSyncOnStoryCreate`: Auto-sync on story creation flag
  - `linear.postQaToLinear`: Post QA results to Linear flag
- If Linear is not enabled, inform user: "Linear integration is disabled in core-config.yaml. Enable it with `linear.enabled: true` to use this task."

### 1. Parse Story File

Read the story file and extract:
- **Story ID**: From filename pattern (e.g., `2.5.3`, `1.15a.10`, or `BAT-5`)
- **Title**: From `# Story {ID}: {Title}` header
- **Epic Number**: From story ID or Linear label reference
- **Domain**: From Domain Context section or infer from services
- **Status**: From `**Status**: {status}` field
- **User Story**: From User Story section (As a... I want... so that...)
- **Acceptance Criteria**: All AC items from Acceptance Criteria section
- **Linear Issue Link**: Check for existing `**Linear Issue**: [BAT-{N}](url)` in header
- **Legacy ID**: For old stories, extract original ID for custom field

### 2. Determine Operation Mode

**If Linear Issue Link exists in story header:**
- Extract Linear issue ID from URL (e.g., `BAT-42`)
- If `create_new=true`, warn user and create new issue
- Otherwise, proceed to UPDATE mode

**If no Linear Issue Link exists:**
- Proceed to CREATE mode

### 3. CREATE Mode - Create New Linear Issue

#### 3.1 Map Story Metadata to Linear

- **Title**: Use story title (without story ID prefix)
- **Team**: Use `linear.teamId` from config
- **Project**: Map epic to project:
  - Epic 1 → "Epic 1: Microservices Foundation"
  - Epic 2 → "Epic 2: Core Event Management"
  - Epic 4 → "Epic 4: Partner Management"
  - (Query Linear for project IDs via `mcp__linear-server__list_projects`)
- **Labels**: Build label array:
  - `epic-{N}` (e.g., `epic-1`, `epic-2`, `epic-4`)
  - `domain-{domain}` (e.g., `domain-event-management`, `domain-infrastructure`)
  - `status-{mapped_status}` (map story status to Linear status label)
- **Status**: Map story status to Linear workflow state using `statusMapping`:
  - `Draft` → `Backlog`
  - `InProgress` → `In Progress`
  - `Review` → `In Review`
  - `Done` → `Done`
  - `Accepted` → `Done`
- **Priority**: Default to `3` (Normal) unless specified in story
- **Description**: Format as markdown:

```markdown
## User Story
{user_story_text}

## Acceptance Criteria
- [ ] {AC1}
- [ ] {AC2}
...

## Implementation Details
📄 Story File: [{story_filename}](github-link-to-file)

## Metadata
- **Epic**: Epic {N}
- **Domain**: {domain_name}
- **Stack**: {tech_stack_from_story}
- **Legacy ID**: {original_id} (if applicable)
```

#### 3.2 Create Linear Issue

- Call `mcp__linear-server__create_issue`:
  - `title`: Story title
  - `team`: Team ID or name from config
  - `project`: Project name or ID
  - `description`: Formatted description from 3.1
  - `labels`: Array of label names (Linear will create if they don't exist)
  - `state`: Mapped workflow state
  - `priority`: Priority value (0-4)
- Capture response: `issueId`, `identifier` (e.g., `BAT-42`), `url`

#### 3.3 Update Story File with Linear Link

- Update story header section to add/replace:
  ```markdown
  **Linear Issue**: [BAT-{N}]({linear_url})
  ```
- Insert this line after the `**Status**:` line
- Preserve all other header content

### 4. UPDATE Mode - Update Existing Linear Issue

#### 4.1 Verify Linear Issue Exists

- Extract Linear issue ID from story header link
- Call `mcp__linear-server__get_issue(id: "{issue_id}")`
- If issue not found, warn user and ask whether to create new issue or abort

#### 4.2 Compare and Update

Compare story file with Linear issue:
- **Title**: Update if different
- **Status**: Update if story status changed
- **Labels**: Add/remove labels based on epic, domain, status changes
- **Description - Acceptance Criteria**: Update AC checkboxes if criteria changed
- **Description - Story File Link**: Update if filename changed

Call `mcp__linear-server__update_issue`:
- `id`: Linear issue ID
- `title`: Updated title (if changed)
- `state`: Updated workflow state (if changed)
- `labels`: Updated label array (if changed)
- `description`: Updated description (if changed)

### 5. Sync Labels to Linear

For each label needed (epic, domain, status):
- Query `mcp__linear-server__list_issue_labels(team: "{team_name}")`
- If label doesn't exist, create it:
  - `mcp__linear-server__create_issue_label`:
    - `name`: Label name
    - `color`: Predefined color mapping:
      - `epic-1`: `#3b82f6` (blue)
      - `epic-2`: `#8b5cf6` (purple)
      - `epic-4`: `#f97316` (orange)
      - `domain-*`: `#10b981` (green)
      - `status-draft`: `#6b7280` (gray)
      - `status-in-progress`: `#eab308` (yellow)
      - `status-review`: `#06b6d4` (cyan)
      - `status-done`: `#22c55e` (green)
      - `qa-pass`: `#22c55e` (green)
      - `qa-concerns`: `#f59e0b` (amber)
      - `qa-fail`: `#ef4444` (red)

### 6. Validation

Verify synchronization:
- Linear issue ID is in story header
- Story file path is in Linear description
- Labels match story metadata
- Status is synchronized
- Acceptance criteria are in Linear description

### 7. Output Summary

Provide user feedback:

**CREATE mode:**
```
✅ Linear Issue Created
━━━━━━━━━━━━━━━━━━━━━━━━━
🆔 Issue: BAT-{N}
🔗 URL: {linear_url}
📄 Story: {story_filename}
🎯 Epic: {epic_number}
🔧 Domain: {domain_name}
📋 Status: {linear_status}
🏷️ Labels: {label_count} applied

Story file updated with Linear link.
```

**UPDATE mode:**
```
✅ Linear Issue Updated
━━━━━━━━━━━━━━━━━━━━━━━━━
🆔 Issue: BAT-{N}
🔗 URL: {linear_url}
📄 Story: {story_filename}
🔄 Changes: {list_of_changes}

Synchronization complete.
```

## Error Handling

- **Story file not found**: HALT with clear error message
- **Linear not enabled**: Inform user to enable in config
- **Linear API error**: Display error message and suggest retry
- **Missing required fields**: List missing fields and halt
- **Invalid status mapping**: Show available statuses and halt

## Common Use Cases

### Use Case 1: First-Time Sync of Existing Story
```
/sync-story-to-linear story_file_path=docs/stories/2.5.3.event-management-frontend.md
```
- Creates new Linear issue
- Adds Linear link to story header
- Applies epic-2, domain-event-management labels

### Use Case 2: Update After Story Changes
```
/sync-story-to-linear story_file_path=docs/stories/BAT-5.event-management-frontend.md
```
- Finds existing Linear issue BAT-5
- Updates status, labels, AC if changed
- Synchronizes metadata

### Use Case 3: Bulk Sync All Stories
```
for story in docs/stories/*.md; do
  /sync-story-to-linear story_file_path=$story
done
```
- Processes each story file
- Creates or updates Linear issues
- Establishes bidirectional links

## Integration with Other Tasks

- **create-next-story.md**: Calls this task in step 8 after story creation
- **qa-gate.md**: References Linear issue for posting QA results
- **apply-qa-fixes.md**: Reads QA findings from Linear comments

## Key Principles

- Story file remains source of truth for implementation details
- Linear provides product visibility and stakeholder engagement
- Bidirectional links maintained automatically
- Immutable Linear IDs (BAT-N) never change
- Metadata in labels (epic, domain) can be changed freely
- Acceptance criteria synchronized for tracking
