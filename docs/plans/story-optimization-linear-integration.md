# Story Optimization & Linear Integration Plan

## Overview

**Problems Solved**:
1. **Token Bloat**: Story files exceed 25k tokens due to full source code in Dev Notes (39-45%) and verbose QA sections (20-35%)
2. **Brittle IDs**: Meaningful IDs like `1.15a.10` break when stories move between epics or get reordered
3. **Poor Stakeholder Visibility**: No product roadmap view, QA buried in markdown

**Solutions**:
- **Template Library**: Move code to reusable templates (docs/templates/)
- **Linear for Product**: Use Linear for AC + roadmap with immutable sequential IDs (BAT-1, BAT-2...)
- **QA in Linear**: Move QA to Linear comments for stakeholder visibility
- **Lean Stories**: Keep story files at 1,500-2,000 tokens with references

**Expected Results**:
- 65% token reduction per large story (6,100 → 2,000 tokens)
- Zero broken references when moving stories between epics
- Product roadmap visibility for stakeholders

---

## Why Linear's Sequential IDs Solve Your Problem

**Current System Pain Points**:
```
Story: 1.15a.10.notifications-api-consolidation.md
       ^   ^  ^
       |   |  └─ Substory number (breaks when inserting stories)
       |   └──── Story variant (a, b, c - gets confusing)
       └──────── Epic number (breaks when moving to different epic)

Problems:
- Move story from Epic 1 to Epic 2 → Must rename → Breaks all references
- Insert new story between 1.15a.10 and 1.15a.11 → Renumber everything after
- Split story into substories → Add a, b, c suffixes → Hard to track
```

**Linear System Advantages**:
```
Story: BAT-42.notifications-api-consolidation.md
       ^^^^^
       └────────── Immutable ID (never changes)

Metadata (via labels, easy to change):
- Epic: epic-2 (can move stories freely)
- Domain: domain-infrastructure
- Priority: P1 (independent of epic)

Benefits:
✅ Move story between epics → Just change label, ID stays BAT-42
✅ Reorder stories → No renumbering needed
✅ References never break → BAT-42 is permanent
✅ Backlog stories → Same ID system, just different status
```

**Migration Strategy**:
- **Existing stories**: Keep old filenames, add Linear link in header
- **New stories**: Use Linear ID in filename (BAT-N.slug.md)
- **Cross-references**: Gradually migrate to Linear IDs as you touch files

---

## Implementation Phases

### Phase 1: Template Library Foundation (Days 1-2)

Create reusable code pattern library to eliminate duplication across stories.

**Directory Structure**:
```
docs/templates/
├── README.md                              # Template index
├── backend/
│   ├── spring-boot-service-foundation.md  # Extract from docs/guides/service-foundation-pattern.md
│   ├── jwt-propagation-pattern.md         # Extract from docs/guides/microservices-http-clients.md
│   ├── flyway-migration-pattern.md        # Extract from docs/guides/flyway-migration-guide.md
│   └── integration-test-pattern.md        # Extract from story 2.8.1
├── frontend/
│   ├── react-query-caching-pattern.md     # Extract from stories 2.5.3, 2.8.1
│   ├── zustand-store-pattern.md           # Extract from stories 2.5.3, 2.8.1
│   ├── react-component-pattern.md         # Extract from story 2.5.3
│   └── form-validation-pattern.md         # Extract from stories with forms
└── infrastructure/
    ├── aws-cdk-service-stack.md
    └── github-actions-ci-pattern.md
```

**Template Standard Format**:
```markdown
# {Pattern Name}

**Category**: Backend/Frontend/Infrastructure
**Used in Stories**: [List of story IDs]
**Last Updated**: {Date}

## Overview
{When to use this pattern}

## Prerequisites
- {Dependencies}

## Implementation Steps
{Step-by-step with full code examples}

## Testing
{Test approach}

## Common Pitfalls
- {Pitfall}: {Solution}

## Story-Specific Adaptations
{How to customize}
```

**Priority Templates** (extract first):
1. `spring-boot-service-foundation.md` (400 lines from existing guide)
2. `jwt-propagation-pattern.md` (200 lines from existing guide)
3. `react-query-caching-pattern.md` (250 lines from stories 2.5.3, 2.8.1)
4. `zustand-store-pattern.md` (200 lines from story 2.5.3)
5. `integration-test-pattern.md` (300 lines from story 2.8.1)

**Validation**:
- Each template referenced by 2+ stories
- README.md with complete index
- Cross-references between related templates

---

### Phase 2: Linear Setup (Day 2)

Configure Linear for product management with proper structure and labels.

**Linear Labels to Create**:
```javascript
// Epic labels
epic-1, epic-2, epic-4 (blue/purple/orange)

// Domain labels
domain-event-management
domain-speaker-coordination
domain-partner-coordination
domain-company-user-management
domain-infrastructure

// Status labels (in addition to workflow states)
status-draft, status-blocked, status-in-progress, status-review, status-done

// QA labels (posted by qa-gate task)
qa-pass, qa-concerns, qa-fail
```

**Linear Projects**:
- Epic 1: Microservices Foundation
- Epic 2: Core Event Management
- Epic 4: Partner Management

**Linear Numbering Strategy**:
- **Linear ID**: Sequential auto-increment (BAT-1, BAT-2, BAT-3...)
  - Immutable - never changes even when story moves between epics
  - No semantic meaning - just a unique identifier
  - Team-scoped across all BATbern issues
- **Story File Name**: Use Linear ID instead of epic.story pattern
  - OLD: `1.15a.10.notifications-api-consolidation.md` (breaks when moved)
  - NEW: `BAT-42.notifications-api-consolidation.md` (permanent reference)
- **Metadata in Labels**: Epic/domain captured in Linear labels, not ID
  - `epic-1`, `epic-2`, `domain-event-management`, etc.
  - Easy to change labels when story moves between epics
- **Legacy Migration**: Keep old story files as-is, new stories use Linear IDs

**Linear Issue Template**:
```
Title: {Story Title}

ID: BAT-{N} (auto-assigned by Linear)

Description:
## User Story
**As a** {role}
**I want** {capability}
**so that** {benefit}

## Acceptance Criteria
- [ ] AC1: {Description}
- [ ] AC2: {Description}
...

## Implementation Details
📄 Story File: [`docs/stories/BAT-{N}.{slug}.md`](github-link)

## Metadata
- Epic: Epic 1 / Epic 2 / Epic 4 (via label, can change)
- Domain: {Domain name} (via label, can change)
- Priority: P0 / P1 / P2 (Linear priority field)
- Stack: {Tech stack}
```

**Create Issues for Active Stories** (6 total):

For **existing stories** (backward compatibility):
1. Create Linear issue (gets auto-assigned: BAT-5, BAT-6, etc.)
2. Add label matching old epic (e.g., `epic-1`, `epic-2`)
3. Title format: Use story title (NOT including old ID)
4. Link to existing story file: `docs/stories/1.15a.10.notifications-api-consolidation.md`
5. Add custom field: `Legacy ID: 1.15a.10` (for reference)

Example:
```
Linear Issue BAT-15 (auto-assigned)
Title: "Notifications API Consolidation"
Labels: epic-1, domain-infrastructure, status-draft
Custom Field: Legacy ID = 1.15a.10
Description: Links to existing story file docs/stories/1.15a.10.notifications-api-consolidation.md
```

For **new stories** (going forward):
1. Create Linear issue first (gets BAT-N)
2. Create story file: `docs/stories/BAT-{N}.{slug}.md`
3. No more epic.story numbering
4. Epic indicated by label only

**Bidirectional Links**:
- Story file header: `**Linear Issue**: [BAT-{N}](linear-url)`
- Linear description: `📄 Story File: [docs/stories/BAT-{N}.{slug}.md](github-url)` (new) or `[docs/stories/{legacy-id}.{slug}.md](github-url)` (existing)

---

### Phase 3: BMAD Task Updates (Days 3-4)

Modify BMAD tasks to integrate Linear and use templates.

**New Tasks to Create**:

1. **`.bmad-core/tasks/sync-story-to-linear.md`**
   - Parse story file (epic, story, title, AC, domain, status)
   - Create or update Linear issue
   - Map status: Draft→Backlog, InProgress→In Progress, Review→In Review, Done→Done
   - Add labels: epic-{N}, domain-{name}, status-{status}
   - Update story file header with Linear link

2. **`.bmad-core/tasks/extract-templates-from-story.md`**
   - Parse Dev Notes section
   - Identify code blocks >50 lines
   - Determine pattern type (Spring Boot, React Query, etc.)
   - Create or update template file
   - Replace code in story with template reference

**Tasks to Modify**:

3. **`.bmad-core/tasks/create-next-story.md`**
   - Add step 8: Call sync-story-to-linear after story creation
   - Add Linear issue link to story header
   - Update Dev Notes section to reference templates

4. **`.bmad-core/tasks/qa-gate.md`**
   - Add step 5: Post QA results to Linear comment
   - Format QA report using Linear comment template
   - Add QA label (qa-pass|qa-concerns|qa-fail)
   - Update Linear status (Done if PASS, In Progress if FAIL)
   - Replace story QA section with: `See Linear Issue: [BAT-X.Y](url)`

5. **`.bmad-core/tasks/apply-qa-fixes.md`**
   - Modify step 1: Read QA findings from Linear comments
   - Call mcp__linear-server__list_comments(issueId)
   - Parse latest QA comment
   - Add step 5: Post fix summary to Linear
   - Update status to "In Review"

**QA Comment Template** (posted to Linear):
```markdown
## QA Results - {Date}

**Gate Decision**: PASS/CONCERNS/FAIL
**Quality Score**: {score}/100

### Executive Summary
{Brief summary}

### Code Quality Assessment
**Strengths**:
- ✅ {Strength 1}
- ✅ {Strength 2}

**Areas for Improvement**:
- ⚠️ {Issue 1}

### Test Coverage
- Unit: {count} passing
- Integration: {count} configured
- Coverage: {percent}%

### Requirements Traceability
| AC | Status | Tests |
|----|--------|-------|
| AC1 | ✅ | 15 |

### NFR Validation
- Security: PASS/FAIL
- Performance: PASS/FAIL

### Recommended Actions
1. {Action}

---
*QA Agent | Powered by BMAD™ Core*
```

---

### Phase 4: Story Refactoring (Days 5-10)

Refactor 6 active stories to use templates and Linear for QA.

**Refactoring Process** (per story):

1. **Extract Templates** (automated):
   - Run `/extract-templates-from-story story_file_path={path}`
   - Moves code blocks >50 lines to templates
   - Replaces with references

2. **Move QA Results** (manual):
   - Copy QA Results section from story
   - Post as Linear comment: mcp__linear-server__create_comment(issueId, body)
   - Add qa-{status} label
   - Replace story section with: `See Linear Issue: [BAT-X.Y](url)`

3. **Optimize Dev Notes** (manual):
   - Keep only story-specific deviations (max 50 lines code)
   - Replace full implementations with template references:
     ```markdown
     ### Implementation References
     **Templates Used**:
     - `docs/templates/frontend/react-query-caching-pattern.md`
     - `docs/templates/frontend/zustand-store-pattern.md`

     **Story-Specific Code** (max 50 lines):
     ```typescript
     // ONLY deviations from template
     export const usePartnerEngagement = (partnerId: string) => {
       // Custom logic here
     }
     ```
     ```

4. **Validate** (automated):
   - Check word count <2,500 words
   - Verify template references exist
   - Verify Linear link works
   - Ensure essential context remains

**Priority Order**:
1. `2.5.3.event-management-frontend.md` (14,256 words → target 2,000)
2. `1.18.codebase-structure-consolidation.md`
3. `1.15a.10.notifications-api-consolidation.md`
4. `1.15a.11.remaining-resources-consolidation.md`
5. `1.15a.8.organizers-api-consolidation.md`
6. `1.15a.9.attendees-api-consolidation.md`

**Expected Savings**:
- Story 2.5.3: 14,256 → 2,000 words (86% reduction)
- Average across 6 stories: 76% reduction

---

### Phase 5: Core Config Updates (Day 10)

Update BMAD configuration to enable Linear integration.

**File**: `.bmad-core/core-config.yaml`

**Add sections**:
```yaml
# Linear Integration
linear:
  enabled: true
  team: BATbern
  teamId: 978bcf9f-669c-464b-a0a4-a162072e575c
  autoSyncOnStoryCreate: true
  postQaToLinear: true
  issuePrefix: BAT
  statusMapping:
    Draft: Backlog
    InProgress: In Progress
    Review: In Review
    Done: Done
    Accepted: Done

# Template Library
templates:
  location: docs/templates
  indexFile: docs/templates/README.md
  categories:
    - backend
    - frontend
    - infrastructure
  extractionMinLines: 50

# Update devLoadAlwaysFiles
devLoadAlwaysFiles:
  - docs/architecture/coding-standards.md
  - docs/architecture/tech-stack.md
  - docs/architecture/source-tree.md
  - docs/templates/README.md  # NEW
```

**Validation**:
```bash
# Verify YAML syntax
python3 -c "import yaml; yaml.safe_load(open('.bmad-core/core-config.yaml'))"

# Check Linear section
cat .bmad-core/core-config.yaml | grep -A10 "linear:"
```

---

### Phase 6: Final Validation (Day 11)

**Validation Checklist**:
- [ ] All 6 active stories refactored to <2,500 words
- [ ] All 6 active stories have Linear issues with AC checkboxes
- [ ] 8+ templates created, each referenced by 2+ stories
- [ ] QA results for active stories moved to Linear comments
- [ ] BMAD tasks updated and tested
- [ ] Core config updated and validated
- [ ] Dev agent can implement using new template-based stories

**Final Tests**:
```bash
# Test new story creation
/create-next-story
# Expected: Creates story with template refs + Linear issue

# Test QA workflow
/qa-gate story_id=2.5.3
# Expected: Posts to Linear comment + adds label + updates status

# Test fix workflow
/apply-qa-fixes story_id=2.5.3
# Expected: Reads from Linear + implements fixes + posts summary
```

---

## Critical Files

### Phase 1: Templates (Create)
1. `docs/templates/README.md`
2. `docs/templates/backend/spring-boot-service-foundation.md`
3. `docs/templates/backend/jwt-propagation-pattern.md`
4. `docs/templates/frontend/react-query-caching-pattern.md`
5. `docs/templates/frontend/zustand-store-pattern.md`

### Phase 3: BMAD Tasks (Modify)
6. `.bmad-core/tasks/sync-story-to-linear.md` (NEW)
7. `.bmad-core/tasks/extract-templates-from-story.md` (NEW)
8. `.bmad-core/tasks/create-next-story.md` (UPDATE)
9. `.bmad-core/tasks/qa-gate.md` (UPDATE)
10. `.bmad-core/tasks/apply-qa-fixes.md` (UPDATE)

### Phase 4: Stories (Refactor)
11. `docs/stories/2.5.3.event-management-frontend.md` (REFACTOR - priority 1)
12. `docs/stories/1.18.codebase-structure-consolidation.md` (REFACTOR - priority 2)

### Phase 5: Config (Update)
13. `.bmad-core/core-config.yaml` (UPDATE)

### Reference (Source Material)
14. `docs/guides/service-foundation-pattern.md` (READ-ONLY)
15. `docs/guides/microservices-http-clients.md` (READ-ONLY)
16. `docs/stories/2.8.1.partner-directory.md` (READ-ONLY)

---

## Migration Strategy

**Scope**: Hybrid approach
- ✅ **Refactor**: 6 active/incomplete stories (Draft or In Progress)
- ⏸️ **Leave as-is**: 25 completed stories (Accepted)
- ✨ **New format**: All future stories use optimized template

**Rollback Plan**:
- Revert Git commits to pre-migration state
- Templates can stay (useful regardless)
- Linear issues can stay (no harm)
- BMAD tasks revert to original versions

---

## Expected Outcomes

### Token Savings
- **Before**: Story 2.5.3 = 14,256 words (~19,000 tokens)
- **After**: Story 2.5.3 = ~2,000 words (~2,700 tokens)
- **Reduction**: 86% per large story

### Developer Experience
- **Before**: Scroll 2,500 lines for context, copy/paste 1000+ line code blocks
- **After**: Focus on 500-line story with essentials, reference proven templates

### Stakeholder Visibility
- **Before**: No product view, QA buried in markdown
- **After**: Linear dashboard with roadmap, QA as issue comments, visual kanban

---

## Key Principles

1. **Stories remain source of truth** for implementation details
2. **Linear is product-facing** (what to build, acceptance criteria)
3. **Templates are reusable** (how to build, proven patterns)
4. **QA in Linear** (stakeholder-friendly, conversation-based)
5. **BMAD compatibility** (minimal workflow disruption)
6. **GitHub Issues stay** for bug tracking (existing automation works)

---

## Success Criteria

✅ 65% average token reduction across active stories
✅ Linear provides product roadmap visibility
✅ Templates eliminate code duplication
✅ QA workflow integrated with Linear
✅ Dev agents can implement from optimized stories
✅ No regression in completed stories
