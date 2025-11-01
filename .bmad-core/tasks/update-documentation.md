<!-- Powered by BMAD™ Core -->

# update-documentation

Systematically analyze recent changes and update documentation to keep it in sync with project implementation progress. This task ensures README, architecture docs, API docs, and guides remain accurate and up-to-date.

## Purpose

- Detect documentation drift by analyzing git changes, story files, and codebase structure
- Identify which documentation files need updates based on recent implementation work
- Update documentation systematically with accurate, current information
- Maintain documentation quality and consistency with actual codebase state

## Inputs

```yaml
optional:
  - scope: 'story-only' | 'full-audit' | 'targeted' (default: 'story-only')
    # story-only: Update docs based on current/recent story only
    # full-audit: Comprehensive audit of all documentation vs. codebase
    # targeted: User specifies specific areas (e.g., 'api', 'architecture', 'readme')

  - story_id: '{epic}.{story}' # e.g., "2.2" - if provided, analyze this story's changes

  - target_areas: ['readme', 'architecture', 'api', 'guides', 'deployment']
    # Only used when scope='targeted'

  - since_commit: '{commit-sha}' # Analyze changes since this commit (default: last 10 commits)

  - dry_run: true|false # Preview changes without writing (default: false)
```

## Documentation Locations (from core-config.yaml)

Based on project structure:
- **README**: `/README.md`
- **Architecture**: `/docs/architecture/`
  - `coding-standards.md`
  - `tech-stack.md`
  - `source-tree.md`
  - Numbered architecture docs (`01-system-overview.md`, etc.)
- **API Documentation**: `/docs/api/`
- **Guides**: `/docs/guides/`
- **Deployment**: `/docs/deployment/`
- **Stories**: `{devStoryLocation}` from core-config.yaml
- **PRD**: `{prdShardedLocation}` from core-config.yaml

## Prerequisites

- Git repository with commit history
- Access to core-config.yaml
- Story files (if scope includes story analysis)
- Write permissions to documentation directories

## Process (Do not skip steps)

### 0) Load Configuration & Determine Scope

- Read `.bmad-core/core-config.yaml` to get documentation paths
- Parse input parameters to determine update scope
- Set analysis boundaries (commits, files, stories to examine)

### 1) Analyze Recent Changes

**For scope='story-only':**
- If `story_id` provided: Read story file from `{devStoryLocation}/{story_id}.*.md`
- Extract from story:
  - File List (all modified/added files)
  - Change Log entries
  - Completion Notes
  - Story title and description
  - Technologies/components touched
- Run `git diff` on File List to understand nature of changes

**For scope='full-audit':**
- Run comprehensive codebase analysis:
  - `git log --since="30 days ago" --name-only --pretty=format:""` to get all changed files
  - Analyze directory structure vs. `source-tree.md`
  - Compare `package.json`/`build.gradle` dependencies vs. `tech-stack.md`
  - Check for new endpoints vs. API documentation
  - Verify README Quick Start commands still work

**For scope='targeted':**
- Focus analysis only on specified `target_areas`
- Use git changes + file reads to verify current state vs. docs

### 2) Identify Documentation Gaps

Create a prioritized update plan based on detected drift:

**Critical Updates (do first):**
1. **README.md Project Status** - If new epic/story completed
2. **README.md Quick Start** - If setup/build commands changed
3. **API Documentation** - If new endpoints added or modified
4. **Architecture Changes** - If new services, infrastructure, or patterns introduced

**Important Updates:**
5. **Tech Stack** - If dependencies upgraded or technologies added/removed
6. **Source Tree** - If new directories/services created
7. **Guides** - If new features need user documentation
8. **Deployment Docs** - If deployment process changed

**Nice-to-Have Updates:**
9. **Code Examples** - If patterns changed significantly
10. **Troubleshooting** - If new common issues discovered

### 3) Generate Documentation Updates

For each identified gap, prepare update content:

**README.md Updates:**
- Update Project Status table with completed stories
- Update component status (✅ Complete, ⏳ In Progress, 📋 Planned)
- Verify Quick Start commands are accurate
- Add new build commands if introduced
- Update deployment environment URLs if changed
- Verify testing commands still work

**Architecture Documentation Updates:**
- Update `source-tree.md` if new directories/services added
- Update `tech-stack.md` if versions bumped or technologies changed
- Update numbered architecture docs if system design evolved
- Update `coding-standards.md` if new patterns/rules established

**API Documentation Updates:**
- Generate/update OpenAPI specs for new endpoints
- Add request/response examples
- Document new authentication requirements
- Update error codes and scenarios

**Guide Updates:**
- Create new guides for significant new features
- Update existing guides if workflows changed
- Add troubleshooting entries for known issues
- Update screenshots if UI changed (note: requires manual action)

### 4) Validation Checks

Before writing, verify update quality:

**Accuracy:**
- Cross-reference changes against actual codebase files
- Verify commands work (e.g., `make test`, `docker-compose up`)
- Check links are valid (internal doc references)
- Ensure version numbers match `package.json`/`build.gradle`

**Consistency:**
- Use same terminology across all docs
- Follow existing documentation style and formatting
- Maintain consistent code example formatting
- Keep tone professional and concise

**Completeness:**
- All changed components documented
- All new public APIs documented
- All breaking changes highlighted
- Migration guides included if needed

### 5) Apply Updates (or Preview)

**If dry_run=true:**
- Output summary of proposed changes to console
- Show before/after diffs for each file
- List files that would be updated
- HALT for user approval

**If dry_run=false (default):**
- Apply updates to documentation files using Edit tool
- For each file updated:
  - Make targeted edits (prefer Edit over Write)
  - Preserve existing structure and formatting
  - Add changelog comment if file has one
- Log all updated files

### 6) Generate Update Summary

Create a summary report:

```markdown
## Documentation Update Summary

**Scope:** {scope}
**Story:** {story_id} (if applicable)
**Date:** {current_date}

### Files Updated
- README.md
  - Updated Project Status table (Story 2.2 complete)
  - Added new testing commands
- docs/architecture/source-tree.md
  - Added services/event-management-service structure
- docs/api/events-api.openapi.yml
  - Added new /api/events endpoints

### Validation Results
✅ All build commands verified
✅ All internal links valid
✅ Version numbers consistent
⚠️  Screenshots need manual update (web-frontend changes)

### Manual Actions Required
- [ ] Update screenshots in docs/guides/ if UI changed
- [ ] Review technical accuracy of auto-generated API docs
- [ ] Consider adding more code examples for complex features
```

### 7) Post-Update Actions

**Story-based updates:**
- Add documentation update note to story's Completion Notes
- Update story File List if documentation files modified
- Do NOT modify story Status

**Full-audit updates:**
- Create a documentation update commit:
  ```bash
  git add docs/ README.md
  git commit -m "docs: update documentation to reflect recent implementation changes"
  ```

## Blocking Conditions

- Missing `.bmad-core/core-config.yaml`
- Story file not found (if story_id provided and scope='story-only')
- Git repository not initialized
- No changes detected in analysis window
  - HALT and inform user documentation appears current

## Completion Checklist

- All identified documentation gaps addressed
- README.md Project Status current with latest story completions
- Architecture docs reflect current system design
- API documentation covers all public endpoints
- Build/deployment commands verified and accurate
- Internal documentation links valid
- Update summary generated
- Manual action items identified (if any)

## Usage Examples

### Example 1: Update docs after completing story
```yaml
# Called automatically or manually after story 2.2 completion
scope: story-only
story_id: "2.2"
dry_run: false
```

### Example 2: Preview full documentation audit
```yaml
# Preview what would change in comprehensive audit
scope: full-audit
dry_run: true
```

### Example 3: Update only README and API docs
```yaml
# Targeted update for specific areas
scope: targeted
target_areas: ['readme', 'api']
since_commit: "abc123"
dry_run: false
```

### Example 4: Integration with develop-story workflow
```bash
# In develop-story completion logic:
# After all tasks complete and before "Ready for Review"
# Automatically run: update-documentation with scope=story-only
```

## Integration Points

**Add to Story DOD Checklist:**
```markdown
- [ ] Documentation updated to reflect implementation changes
  - README.md Project Status current
  - Architecture docs reflect new components
  - API docs cover new endpoints
  - Guides updated for new features
```

**Add to develop-story completion:**
```
Before setting Status: "Ready for Review"
→ Run update-documentation task with scope=story-only
→ Review and apply suggested documentation updates
→ Then proceed with story completion
```

## Key Principles

- **Accuracy First**: Documentation must reflect actual codebase state
- **Automation-Friendly**: Prefer git analysis over manual discovery
- **Targeted Updates**: Focus on what changed, not rewriting everything
- **Quality Checks**: Verify commands work, links valid, versions match
- **User Guidance**: Provide clear summary of what changed and why
- **Manual Awareness**: Flag items that need human review (screenshots, complex diagrams)

## Technology-Specific Documentation Rules

**Java/Spring Boot Projects:**
- Update OpenAPI specs from `@RestController` annotations
- Document `application.yml` changes
- Update Gradle dependency versions in tech-stack.md

**React/TypeScript Projects:**
- Update component documentation for major UI changes
- Document new hooks in architecture docs
- Update `package.json` dependency versions

**Infrastructure/CDK:**
- Update deployment docs for new AWS resources
- Document environment variable changes
- Update infrastructure diagrams (note for manual update)

**Docker/Compose:**
- Update Quick Start if docker-compose.yml changed
- Document new service containers
- Update port mappings and volume mounts

## Output Format

The task should output a structured summary that can be logged and referenced:

```
📚 Documentation Update Complete

Scope: story-only (Story 2.2)
Files Updated: 4
Warnings: 1

✅ README.md - Project Status updated
✅ docs/architecture/source-tree.md - Added event-management-service
✅ docs/api/events-api.openapi.yml - New endpoints documented
✅ docs/guides/event-management-guide.md - Created new guide

⚠️  Manual Action Required:
- Update screenshots in docs/guides/event-management-guide.md

Run `git diff docs/ README.md` to review changes.
```
