<!-- Powered by BMAD™ Core -->

# Create Next Story Task

## Purpose

To identify the next logical story based on project progress and epic definitions, and then to prepare a comprehensive, self-contained, and actionable story file using the enhanced `Story Template` with domain-aware context. This task ensures the story is enriched with all necessary domain context, technical specifications, requirements traceability, and architecture patterns, making it ready for efficient implementation by a Developer Agent with minimal need for additional research.

## SEQUENTIAL Task Execution (Do not proceed until current Task is complete)

### 0. Load Core Configuration and Domain Context

- Load `.bmad-core/core-config.yaml` from the project root
- If the file does not exist, HALT and inform the user: "core-config.yaml not found. This file is required for story creation. You can either: 1) Copy it from GITHUB bmad-core/core-config.yaml and configure it for your project OR 2) Run the BMad installer against your project to upgrade and add the file automatically. Please add and configure core-config.yaml before proceeding."
- Extract key configurations: `devStoryLocation`, `prd.*`, `architecture.*`, `workflow.*`
- Load `.bmad-core/data/domain-context-map.yaml` to understand domain mappings
- If domain context map exists, use it to guide context extraction

### 1. Identify Next Story for Preparation

#### 1.1 Locate Epic Files and Review Existing Stories

- Based on `prdSharded` from config, locate epic files (sharded location/pattern or monolithic PRD sections)
- If `devStoryLocation` has story files, load the highest `{epicNum}.{storyNum}.story.md` file
- **If highest story exists:**
  - Verify status is 'Done'. If not, alert user: "ALERT: Found incomplete story! File: {lastEpicNum}.{lastStoryNum}.story.md Status: [current status] You should fix this story first, but would you like to accept risk & override to create the next story in draft?"
  - If proceeding, select next sequential story in the current epic
  - If epic is complete, prompt user: "Epic {epicNum} Complete: All stories in Epic {epicNum} have been completed. Would you like to: 1) Begin Epic {epicNum + 1} with story 1 2) Select a specific story to work on 3) Cancel story creation"
  - **CRITICAL**: NEVER automatically skip to another epic. User MUST explicitly instruct which story to create.
- **If no story files exist:** The next story is ALWAYS 1.1 (first story of first epic)
- Announce the identified story to the user: "Identified next story for preparation: {epicNum}.{storyNum} - {Story Title}"

### 2. Determine Domain Context from Epic

Using the domain-context-map.yaml:
- Identify which domain the epic belongs to based on epic number
- Extract:
  - Primary domain name and description
  - Related services
  - Architecture document sections relevant to this domain
  - Wireframe files and components
  - Functional requirements
  - Workflow steps (if Event Management domain)
  - Cross-domain dependencies

If no domain map exists or epic not mapped, proceed with manual identification.

### 3. Gather Story Requirements and Previous Story Context

- Extract story requirements from the identified epic file, including:
  - User story statement (As a... I want... so that...)
  - Full acceptance criteria
  - Technical specifications mentioned in the epic story
  - Architecture integration notes
- If previous story exists, review Dev Agent Record sections for:
  - Completion Notes and Debug Log References
  - Implementation deviations and technical decisions
  - Challenges encountered and lessons learned
  - File paths and patterns established
- Extract relevant insights that inform the current story's preparation

### 4. Gather Architecture Context (Domain-Aware)

#### 4.1 Use Domain Context Map for Targeted Reading

If domain context map loaded successfully:
- Read ONLY the architecture sections specified for this domain
- Focus on the specific sections listed in the domain map
- Extract patterns and specifications relevant to the domain

#### 4.2 Read Architecture Documents Based on Story Type

**For ALL Stories:**
- tech-stack.md - Technology choices and versions
- coding-standards.md - Project conventions
- source-tree.md - File organization and naming

**Based on Domain from Context Map:**
- **Event Management**: Focus on workflow engine, state machines, publishing pipeline
- **Speaker Coordination**: Focus on invitation system, submission validation, material storage
- **Partner Analytics**: Focus on data aggregation, reporting, predictive models
- **Attendee Experience**: Focus on search, PWA, content delivery, mobile optimization
- **Shared Infrastructure**: Focus on authentication, authorization, cross-cutting concerns

**For Backend Stories, additionally:**
- Backend service patterns from 06-backend-architecture.md
- API specifications from 04-api-design.md
- Data models from 03-data-architecture.md

**For Frontend Stories, additionally:**
- React patterns from 05-frontend-architecture.md
- Component specifications
- State management patterns

#### 4.3 Extract Story-Specific Technical Details

Extract ONLY information directly relevant to implementing the current story:
- Specific API endpoints with OpenAPI specifications
- Database schemas and migrations needed
- React component interfaces and props
- Domain event schemas for EventBridge
- Infrastructure components (Lambda, S3, etc.)
- Caching strategies and keys
- Testing patterns specific to the domain

ALWAYS cite source documents: `[Source: architecture/{filename}.md#{section}]`

### 5. Gather Wireframe Context

If the story involves UI components:
- Identify relevant wireframe files from domain context map
- List specific components/sections from wireframes that relate to the story
- Note UI/UX requirements and interactions
- Identify Material-UI components to be used
- Document responsive design requirements

### 6. Determine Story Creation Mode

Check core-config.yaml for Linear integration settings:
- If `linear.enabled: true` AND `linear.linearIsSourceOfTruth: true` → **LINEAR-FIRST MODE** (Steps 6A, 7A, 8A)
- Otherwise → **LEGACY MODE** (Steps 6B, 7B, 8B)

---

## LINEAR-FIRST MODE (Linear as Primary Source)

### 6A. Create Linear Issue with Product View

**CRITICAL**: In Linear-first mode, Linear issue is created FIRST with product-facing content, then minimal local stub is created.

Create Linear issue using `mcp__linear-server__create_issue` with the following content:

#### Linear Issue Structure (Product View - WHAT to build)

```markdown
## User Story
**As a** {role}
**I want** {capability}
**so that** {benefit}

## Context
{Background and motivation from epic}

## Domain Context

### Primary Domain
{Domain name from domain context map}

### Involved Services
{List services from domain map}

### Cross-Domain Dependencies
{Integration points identified}

## Requirements Context

### Related Functional Requirements
{FRs from prd-enhanced.md mapped in domain context}

### Workflow Steps
{For Event Management stories, specify which of the 16 steps}

### Acceptance Criteria Source
{Reference exact epic section, e.g., "Epic 2, Story 2.3"}

## Architecture Context

### Architecture Patterns
{High-level patterns from domain's architecture sections - references only, not full code}

### Database Schema Reference
{Table names and references to architecture docs - e.g., "See 03-data-architecture.md, Section 3.2"}

### Infrastructure Components
{AWS services, Lambda functions, database tables - high level}

## Wireframe Context

### Wireframe References
{Files and specific components from domain map}

### UI Components
{React components to create/modify - names only, not full interfaces}

## Acceptance Criteria
1. [ ] AC1: {description}
2. [ ] AC2: {description}
3. [ ] AC3: {description}

## Test Specifications (TDD) - Product View

⚠️ CRITICAL: Tests must be written BEFORE implementation (Red-Green-Refactor)

### Test Scenarios by Acceptance Criteria

**AC1 Tests** (high-level - WHAT to test):
- Test 1.1: should_{expected_behavior}_when_{condition}
- Test 1.2: should_throwError_when_{invalid_condition}

**AC2 Tests**:
- Test 2.1: should_{expected_behavior}_when_{condition}
- Test 2.2: should_handleEdgeCase_when_{edge_condition}

**AC3 Tests**:
- Test 3.1: should_{expected_behavior}_when_{condition}

### Test Coverage Requirements
- Unit Tests: >90% for business logic
- Integration Tests: >80% for APIs
- E2E Tests: All critical user journeys

## Tasks / Subtasks (TDD Workflow)

- [ ] Task 0: Schema Validation & Migration (For JPA entities - FIRST)
  - [ ] Read architecture doc schema
  - [ ] Check if Flyway migration exists
  - [ ] Compare migration to architecture
  - [ ] If mismatch: Create/correct migration
  - [ ] Update Database Schema Reference
  - [ ] Commit migration BEFORE creating entities

- [ ] Task 1: Write E2E Tests (RED Phase)
  - [ ] Write failing E2E test for main user journey
  - [ ] Verify test fails with meaningful error

- [ ] Task 2a: Backend TDD Tests (RED Phase) (AC: {numbers})
  - [ ] Write failing integration tests for API endpoints
  - [ ] Write failing unit tests for service methods
  - [ ] Write failing unit tests for domain logic

- [ ] Task 2b: Backend TDD Implementation (GREEN Phase) (AC: {numbers})
  - [ ] Implement minimal code to make tests pass

- [ ] Task 3a: Frontend TDD Tests (RED Phase) (AC: {numbers})
  - [ ] Write failing component tests
  - [ ] Write failing hook tests
  - [ ] Write failing service tests

- [ ] Task 3b: Frontend TDD Implementation (GREEN Phase) (AC: {numbers})
  - [ ] Implement minimal component code to pass tests

- [ ] Task {X}: Infrastructure Setup
  - [ ] Verify AWS resources defined in CDK
  - [ ] Wire frontend to backend via API Gateway
  - [ ] Integrate components in navigation

- [ ] Task {Y}: Backend and Frontend Refactoring (REFACTOR)
  - [ ] Verify no mocks used except in tests
  - [ ] Verify all E2E tests pass
  - [ ] Refactor backend code while keeping tests green
  - [ ] Refactor frontend components

## Definition of Done

**Development Complete:**
- [ ] All tests written BEFORE implementation (TDD followed)
- [ ] All acceptance criteria have corresponding tests
- [ ] Unit tests >90% coverage
- [ ] Integration tests >80% coverage
- [ ] E2E tests pass
- [ ] Code follows conventions
- [ ] API documentation updated

**Schema Validation (For JPA stories):**
- [ ] Task 0 completed: Migration validated
- [ ] Migration matches architecture 100%
- [ ] Migration tested with PostgreSQL
- [ ] JPA entity matches architecture
- [ ] SchemaValidationTest passing

**Infrastructure Complete:**
- [ ] CDK changes implemented
- [ ] IAM permissions validated
- [ ] API Gateway routes deployed
- [ ] Database migrations applied
- [ ] EventBridge rules configured
- [ ] CloudWatch alarms updated

**Frontend Complete (if applicable):**
- [ ] Components match wireframes
- [ ] Responsive design tested
- [ ] Accessibility requirements met (WCAG 2.1 AA)
- [ ] Performance metrics achieved
- [ ] Error handling implemented

**Review Ready:**
- [ ] PR created with description
- [ ] Code review completed
- [ ] Security review passed
- [ ] Documentation updated

## Implementation Details
📄 **Dev Notes**: [`docs/stories/BAT-{N}.{slug}.md`](github-link)
(Contains dev implementation notes, template references, test file locations, Testcontainers config)

## Technical Stack
- Frontend: {stack}
- Backend: {stack}
- Database: {stack}

## Dependencies
- Depends on: {Linear issue links if any}
- Blocks: {Linear issue links if any}

## Notes
{Additional context for developers}
```

**Linear Issue Metadata**:
- **Title**: `{epicNum}.{storyNum}: {story_title}`
- **Labels**: `epic-{epicNum}`, `domain-{domain_name}`, `status-draft`
- **Priority**: {P0/P1/P2 based on epic}
- **Project**: {Epic project name}

**Capture**:
- Linear issue ID (e.g., BAT-42)
- Linear issue URL

### 7A. Create Minimal Local Stub File

Create local story stub file: `{devStoryLocation}/BAT-{N}.{story_title_short}.md`

**CRITICAL**: Local file contains ONLY implementation details (HOW to build), NOT product content (WHAT to build).

```markdown
# Story: {story_title}

**Linear Issue**: [BAT-{N}](linear-url) ← **PRIMARY SOURCE**

**Story File**: This file contains **ONLY** dev implementation notes

---

## ⚠️ IMPORTANT: Story Content Location

This file contains **ONLY** dev implementation notes. For story content, see Linear:

- **User Story**: [Linear description](linear-url)
- **Acceptance Criteria**: [Linear issue](linear-url) (see checkboxes)
- **Tasks/Subtasks**: [Linear subtasks](linear-url)
- **QA Results**: [Linear comments](linear-url)
- **Status**: [Linear workflow state](linear-url)

---

## Dev Agent Record

### Agent Model Used
{To be filled by dev agent}

### Template References

**Implementation Patterns to Use**:
{List applicable templates from docs/templates/README.md}
- Backend: `docs/templates/backend/{pattern-name}.md`
- Frontend: `docs/templates/frontend/{pattern-name}.md`

**Existing Code References**:
- Similar to: Story {reference} ({service/component})

### Test Implementation Details (HOW to test)

**CRITICAL**: All backend integration tests MUST use PostgreSQL via Testcontainers. NEVER use H2 - it creates false confidence and hides PostgreSQL-specific issues (JSONB types, functions, etc.).

#### Test File Locations (Exact Paths)
**Frontend Tests**:
- `{frontend_path}/src/components/{Component}/{Component}.test.tsx`
- `{frontend_path}/src/hooks/{hookName}/{hookName}.test.ts`
- `{frontend_path}/src/services/{service}/{service}.test.ts`

**Backend Tests**:
- Unit: `{service}/src/test/unit/{package}/{ClassName}Test.java`
- Integration: `{service}/src/test/integration/{package}/{ClassName}IntegrationTest.java`
- Repository: `{service}/src/test/integration/repository/{Repository}Test.java`

**E2E Tests** (if applicable):
- `e2e/workflows/{workflow-name}/{workflow-name}.spec.ts`

#### Test Data & Mocks Configuration

**Test Data Builders**:
- {EntityName}TestDataBuilder for creating test {entities}
- {User}TestDataBuilder for test users with different roles

**Mock Services**:
- Mock AWS Cognito responses for authentication tests
- Mock EventBridge for domain event tests

**Test Containers (MANDATORY)**:
- PostgreSQL 16 Alpine via Testcontainers for ALL integration tests
  - All integration tests MUST extend AbstractIntegrationTest
  - Use singleton pattern with withReuse(true) for performance
  - Enable Flyway migrations for production parity
- LocalStack for AWS service mocks (S3, EventBridge, etc.)

**Test Configuration**:
```properties
# application-test.properties
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
```

### Story-Specific Implementation

**Deviations from Templates** (max 100 lines):
```{language}
// ONLY code that differs from templates
// To be filled during implementation
```

### API Contracts (OpenAPI Excerpts)

```yaml
# Specific OpenAPI specs for this story
# Copy relevant sections from architecture docs
```

### Database Schema (SQL)

```sql
-- CREATE TABLE statements from architecture doc
-- Copy from 03-data-architecture.md, Section {X.X}
```

### Implementation Approach
{To be filled by dev agent during implementation}

### Debug Log
See: `.ai/debug-log.md#{story-id}` for detailed implementation debugging

### Completion Notes
{To be filled by dev agent}

### File List
**Created**:
- {files}

**Modified**:
- {files}

**Deleted**:
- {files}

### Change Log
- {date}: {change}

### Deployment Notes
{Special deployment considerations}

### Status
Draft
```

### 8A. Story Draft Completion (Linear-First Mode)

- Execute `.bmad-core/tasks/execute-checklist` with `.bmad-core/checklists/story-draft-checklist.md`
- Provide summary to user:
  ```
  ✅ Story Created Successfully (LINEAR-FIRST MODE)
  ━━━━━━━━━━━━━━━━━━━━━━━━━
  📋 Linear Issue: BAT-{N} ({linear_url})
  📄 Local Stub: BAT-{N}.{story_title_short}.md
  🎯 Domain: {domain_name}
  🔧 Services: {services_list}
  📋 Status: Draft (Linear workflow state)
  🏗️ Architecture Refs: {count} sections
  🎨 Wireframes: {count} files referenced
  🔗 Cross-Domain: {dependencies_list or "None"}
  📊 Functional Reqs: {FR_numbers}
  ✓ Checklist: {pass/fail status}

  Next Steps:
  1. Review Linear issue for product alignment
  2. Review local stub for implementation completeness
  3. Approve story for development
  ```

---

## LEGACY MODE (Local File as Primary Source)

### 6B. Populate Enhanced Story Template with Full Context

Create new story file: `{devStoryLocation}/{epicNum}.{storyNum}.{story_title_short}.md` using the enhanced Story Template v3.

#### 6B.1 Basic Information
- Title, Status (Draft), Story statement from epic

#### 6B.2 Domain Context Section
- **Primary Domain**: From domain context map
- **Involved Services**: List services from domain map
- **Cross-Domain Dependencies**: Any integration points identified

#### 6B.3 Requirements Context Section
- **Related Functional Requirements**: FRs from prd-enhanced.md mapped in domain context
- **Workflow Steps**: For Event Management stories, specify which of the 16 steps
- **Acceptance Criteria Source**: Reference exact epic section (e.g., "Epic 2, Story 2.3")

#### 6B.4 Architecture Context Section
- **Architecture Patterns**: Specific patterns from the domain's architecture sections
- **Infrastructure Components**: AWS services, Lambda functions, database tables, etc.

#### 6B.5 Wireframe Context Section
- **Wireframe References**: Files and specific components from domain map
- **UI Components**: React components to create/modify

#### 6B.6 Acceptance Criteria
- Copy exact acceptance criteria from the epic story

#### 6B.7 Tasks/Subtasks
Generate detailed tasks based on:
- Domain-specific implementation patterns
- Each involved service
- Cross-domain integrations identified
- Testing requirements from domain
Generate detailed, sequential list of additional tasks based on Story AC

#### 6B.8 Dev Notes - Implementation Guide (CRITICAL)
Consolidate ALL context into actionable implementation guide:
- **Service Setup**: Exact file paths based on domain and service
- **API Specifications**: Complete OpenAPI specs extracted from architecture
- **Database Schema**: Full CREATE TABLE statements if needed
- **React Components**: TypeScript interfaces with Material-UI usage
- **Integration Code**: EventBridge event schemas, service calls
- **Testing Patterns**: Domain-specific test requirements

**Template References**: When applicable patterns exist in template library:
- Reference existing templates instead of duplicating full code
- Format: `**Implementation Pattern**: See [template-name](path/to/template.md)`
- Include only story-specific deviations (<50 lines)
- Check `docs/templates/README.md` for available templates

Include actual code snippets that can be copy-pasted.
Every technical detail MUST include source reference.

### 7B. Apply Domain-Specific Enhancements (Legacy Mode)

Based on the domain, add specific considerations:

**Event Management Stories:**
- Workflow state machine configurations
- Publishing pipeline setup
- Timeline management details

**Speaker Coordination Stories:**
- Content validation rules (1000 char abstract, lessons learned)
- File upload configurations
- Communication templates

**Partner Analytics Stories:**
- Analytics query specifications
- Report generation templates
- ML model integration points

**Attendee Experience Stories:**
- Search index configurations
- PWA manifest settings
- Offline caching strategies

### 8B. Story Draft Completion (Legacy Mode)

- Review all sections for completeness and domain alignment
- Verify all source references are included
- Ensure cross-domain dependencies are documented
- Update status to "Draft" and save the story file
- Execute `.bmad-core/tasks/execute-checklist` with `.bmad-core/checklists/story-draft-checklist.md`
- If Linear integration is enabled (`linear.enabled: true` and `linear.autoSyncOnStoryCreate: true`):
  - Execute `.bmad-core/tasks/sync-story-to-linear` with story file path
  - This will create a Linear issue, add bidirectional links, and apply appropriate labels
  - Capture Linear issue ID and URL for output summary
- Provide summary to user:
  ```
  ✅ Story Created Successfully (LEGACY MODE)
  ━━━━━━━━━━━━━━━━━━━━━━━━━
  📄 File: {epicNum}.{storyNum}.{story_title_short}.md
  🎯 Domain: {domain_name}
  🔧 Services: {services_list}
  📋 Status: Draft
  🏗️ Architecture Refs: {count} sections
  🎨 Wireframes: {count} files referenced
  🔗 Cross-Domain: {dependencies_list or "None"}
  📊 Functional Reqs: {FR_numbers}
  📋 Linear Issue: BAT-{N} ({linear_url}) (if synced)
  ✓ Checklist: {pass/fail status}

  Next Steps:
  1. Review the story draft
  2. Validate domain context is complete
  3. Approve story for development
  ```

### 9. Domain Context Validation (Both Modes)

Perform final validation regardless of mode:
- Confirm all domain-specific requirements are addressed
- Verify integration points with other domains are documented
- Check that all relevant wireframes are referenced
- Ensure functional requirements are properly traced
- **Linear-First Mode**: Validate Linear issue has product view AND local stub has implementation details
- **Legacy Mode**: Validate local story file has complete implementation guide