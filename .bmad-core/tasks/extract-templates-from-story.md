<!-- Powered by BMAD™ Core -->

# Extract Templates from Story Task

## Purpose

To analyze a story's Dev Notes section, identify reusable code patterns, extract them into template files, and replace the original code with concise template references. This task reduces story token count by 65%+ while preserving implementation guidance and creating reusable patterns for future stories.

## Prerequisites

- Story file exists and contains Dev Notes section with code blocks
- Template library structure exists at `docs/templates/`
- `.bmad-core/core-config.yaml` contains template configuration

## Parameters

- `story_file_path` (required): Path to the story markdown file (e.g., `docs/stories/2.5.3.event-management-frontend.md`)
- `extraction_min_lines` (optional): Minimum code block size to extract (default: `50` lines, from config)
- `dry_run` (optional): Preview extraction without modifying files (default: `false`)
- `auto_approve` (optional): Skip manual approval for each extraction (default: `false`)

## SEQUENTIAL Task Execution

### 0. Load Configuration

- Load `.bmad-core/core-config.yaml` from the project root
- Extract template configuration:
  - `templates.location`: Template directory (e.g., `docs/templates`)
  - `templates.indexFile`: Template index file (e.g., `docs/templates/README.md`)
  - `templates.categories`: Template categories array (backend, frontend, infrastructure)
  - `templates.extractionMinLines`: Minimum lines for extraction (default: `50`)
- Verify template directory structure exists:
  - `{templates.location}/backend/`
  - `{templates.location}/frontend/`
  - `{templates.location}/infrastructure/`
  - `{templates.location}/README.md`
- If structure missing, ask user: "Template library structure not found. Would you like to create it now? (y/n)"

### 1. Parse Story File

Read the story file and extract:
- **Story ID**: From filename pattern
- **Title**: From header
- **Dev Notes section**: Extract entire section content
- **Tech Stack**: Determine from story (Backend/Frontend/Infrastructure/Fullstack)

### 2. Analyze Dev Notes for Code Patterns

Scan Dev Notes section for code blocks:
- Identify all fenced code blocks (```language ... ```)
- For each code block:
  - Count lines (excluding blank lines)
  - Determine language (java, typescript, yaml, bash, etc.)
  - Extract surrounding context (headers, explanatory text)
  - Classify pattern type (see Pattern Classification below)
  - Check if block exceeds `extractionMinLines` threshold

#### Pattern Classification Rules

Classify code blocks by analyzing content:

**Backend Patterns** (Java/Spring Boot):
- `spring-boot-service-foundation` - Contains @SpringBootApplication, main class, application.yml
- `jwt-propagation-pattern` - Contains RestTemplate, WebClient, JWT headers, security config
- `flyway-migration-pattern` - Contains Flyway SQL migrations, version naming
- `integration-test-pattern` - Contains @SpringBootTest, AbstractIntegrationTest, Testcontainers
- `jpa-entity-pattern` - Contains @Entity, @Table, JPA relationships
- `rest-controller-pattern` - Contains @RestController, @RequestMapping, OpenAPI annotations

**Frontend Patterns** (React/TypeScript):
- `react-query-caching-pattern` - Contains useQuery, useMutation, queryClient
- `zustand-store-pattern` - Contains create(), zustand state management
- `react-component-pattern` - Contains React.FC, Material-UI components, hooks
- `form-validation-pattern` - Contains form handling, validation schemas
- `api-service-pattern` - Contains axios, API client configuration

**Infrastructure Patterns** (AWS CDK/Terraform):
- `aws-cdk-service-stack` - Contains CDK constructs, stack definitions
- `github-actions-ci-pattern` - Contains workflow YAML, CI/CD steps

### 3. Build Extraction Plan

For each code block exceeding threshold:
- **Block ID**: Sequential number (1, 2, 3...)
- **Pattern Type**: Classified pattern name
- **Lines**: Code block line count
- **Language**: Programming language
- **Target Template**: Determine template filename
  - If template exists: Use existing file
  - If new pattern: Generate filename from pattern type
- **Context**: Surrounding explanatory text
- **Story-Specific Code**: Identify deviations from standard pattern

**Deduplication Check**:
- For each identified pattern, check if template already exists
- If template exists:
  - Compare code block with template content
  - If >80% similar: Reference existing template
  - If <80% similar: Extract as "Story-Specific Adaptation" section

### 4. Present Extraction Plan to User

If `dry_run=true` or `auto_approve=false`, show plan:

```
📋 Template Extraction Plan
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Story: {story_id}
Dev Notes: {total_lines} lines
Extractable Blocks: {count}

Extractions:
1️⃣ {pattern_type} ({lines} lines)
   → {target_template_file}
   Status: {NEW|UPDATE|REFERENCE}

2️⃣ {pattern_type} ({lines} lines)
   → {target_template_file}
   Status: {NEW|UPDATE|REFERENCE}

Estimated Token Reduction: {current_tokens} → {estimated_tokens} ({percent}% reduction)

Proceed? (y/n)
```

If user confirms or `auto_approve=true`, proceed to step 5.

### 5. Extract Code to Templates

For each extraction in the plan:

#### 5.1 Determine Template Category

Map pattern type to category:
- Backend patterns → `backend/`
- Frontend patterns → `frontend/`
- Infrastructure patterns → `infrastructure/`

#### 5.2 Create or Update Template File

**Template File Path**: `{templates.location}/{category}/{pattern_type}.md`

**Template Content Format**:
```markdown
# {Pattern Name}

**Category**: {Backend|Frontend|Infrastructure}
**Used in Stories**: {story_id_list}
**Last Updated**: {ISO-8601 date}

## Overview

{When to use this pattern - extracted from surrounding context}

## Prerequisites

- {Dependencies identified from code}

## Implementation Steps

### Step 1: {First step}

{Explanatory text from story}

```{language}
{extracted_code_block}
```

### Step 2: {Next step}

...

## Testing

{Test approach from story if available}

## Common Pitfalls

- **Issue**: {Identified from story notes}
  **Solution**: {Solution from story}

## Story-Specific Adaptations

### Story {story_id}

{Story-specific deviations or customizations}

```{language}
// Custom code for this story
{story_specific_code}
```
```

**If template exists**:
- Add story ID to "Used in Stories" list
- Add new "Story-Specific Adaptations" section if code differs
- Update "Last Updated" date

**If template is new**:
- Create file with full structure
- Initialize "Used in Stories" with current story ID

#### 5.3 Replace Code in Story with Template Reference

In the story's Dev Notes section, replace the extracted code block with:

```markdown
### {Section Title}

**Implementation Pattern**: See [`{template_file_path}`](../{relative_path_to_template})

**Story-Specific Notes**:
- {Customization 1}
- {Customization 2}

{Keep only story-specific code <50 lines if needed}
```

**Example Replacement**:

Before:
```markdown
### React Query Caching Setup

```typescript
// 280 lines of React Query setup code
...
```
```

After:
```markdown
### React Query Caching Setup

**Implementation Pattern**: See [`docs/templates/frontend/react-query-caching-pattern.md`](../../templates/frontend/react-query-caching-pattern.md)

**Story-Specific Notes**:
- Custom cache key includes `partnerId` for multi-tenant filtering
- Added `staleTime: 5 * 60 * 1000` for partner analytics

```typescript
// Only unique adaptations
export const usePartnerEngagement = (partnerId: string) => {
  return useQuery({
    queryKey: ['partner-engagement', partnerId],
    staleTime: 5 * 60 * 1000, // 5 minutes
    // ... rest uses standard pattern
  });
};
```
```

### 6. Update Template Index

Read `{templates.indexFile}` (e.g., `docs/templates/README.md`) and update:

**For new templates**:
- Add entry to appropriate category section
- Format: `- ✅ [{pattern-name}]({category}/{filename}) - {brief_description} (Stories: {id_list})`

**For updated templates**:
- Update story ID list in existing entry
- Update status to ✅ if was 📋 (planned)

**Example Index Entry**:
```markdown
### Frontend Templates

- ✅ [React Query Caching Pattern](frontend/react-query-caching-pattern.md) - Server state management and caching (Stories: 2.5.3, 2.8.1)
- ✅ [Zustand Store Pattern](frontend/zustand-store-pattern.md) - Client state management (Stories: 2.5.3, 2.8.1)
```

### 7. Calculate Token Savings

Measure optimization results:
- **Original Dev Notes**: Count words/tokens
- **Optimized Dev Notes**: Count words/tokens after replacement
- **Reduction**: Calculate percentage
- **Templates Created/Updated**: Count files modified

### 8. Validation

Verify extraction quality:
- [ ] All code blocks >extractionMinLines extracted or referenced
- [ ] Template files follow standard format
- [ ] Template index updated
- [ ] Story Dev Notes references are valid (paths exist)
- [ ] Story-specific code sections <50 lines
- [ ] Essential context preserved in story
- [ ] Templates are self-contained and actionable

### 9. Output Summary

Provide user feedback:

```
✅ Template Extraction Complete
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📄 Story: {story_id}
📦 Templates: {templates_created} created, {templates_updated} updated
📝 Extractions: {total_extractions}

Token Reduction:
Before: {original_tokens} tokens
After:  {optimized_tokens} tokens
Savings: {reduction_percent}% ({tokens_saved} tokens)

Files Modified:
✏️  {story_file_path}
{template_files_list}
📋 {templates.indexFile}

Next Steps:
1. Review extracted templates for accuracy
2. Validate story references work correctly
3. Consider extracting patterns from other stories
```

## Error Handling

- **Story file not found**: HALT with clear error message
- **No extractable code blocks**: Inform user, no changes made
- **Template directory missing**: Offer to create structure
- **Invalid pattern classification**: Log warning, skip block
- **File write errors**: Roll back changes, report error

## Common Use Cases

### Use Case 1: Optimize Large Story
```
/extract-templates-from-story story_file_path=docs/stories/2.5.3.event-management-frontend.md
```
- Analyzes 14,256-word story
- Extracts 5 reusable patterns
- Reduces to ~2,000 words (86% reduction)

### Use Case 2: Preview Extraction
```
/extract-templates-from-story story_file_path=docs/stories/1.18.codebase-structure-consolidation.md dry_run=true
```
- Shows extraction plan without modifying files
- User can review before committing

### Use Case 3: Batch Process Stories
```
for story in docs/stories/*.md; do
  /extract-templates-from-story story_file_path=$story auto_approve=true
done
```
- Processes all stories
- Auto-approves extractions
- Builds comprehensive template library

## Integration with Other Tasks

- **create-next-story.md**: New stories reference existing templates
- **sync-story-to-linear.md**: Smaller stories sync faster to Linear
- **qa-gate.md**: Reviewers focus on story-specific code, not boilerplate

## Key Principles

- Templates are reusable patterns, not duplicated code
- Story files retain essential context and deviations
- Token reduction improves AI agent efficiency
- Templates create consistency across implementations
- Story-specific adaptations documented clearly
- Template library grows organically from real implementations
