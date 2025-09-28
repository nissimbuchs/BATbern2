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

### 6. Populate Enhanced Story Template with Full Context

Create new story file: `{devStoryLocation}/{epicNum}.{storyNum}.{story_title_short}.md` using the enhanced Story Template v3.

#### 6.1 Basic Information
- Title, Status (Draft), Story statement from epic

#### 6.2 Domain Context Section
- **Primary Domain**: From domain context map
- **Involved Services**: List services from domain map
- **Cross-Domain Dependencies**: Any integration points identified

#### 6.3 Requirements Context Section
- **Related Functional Requirements**: FRs from prd-enhanced.md mapped in domain context
- **Workflow Steps**: For Event Management stories, specify which of the 16 steps
- **Acceptance Criteria Source**: Reference exact epic section (e.g., "Epic 2, Story 2.3")

#### 6.4 Architecture Context Section
- **Architecture Patterns**: Specific patterns from the domain's architecture sections
- **Infrastructure Components**: AWS services, Lambda functions, database tables, etc.

#### 6.5 Wireframe Context Section
- **Wireframe References**: Files and specific components from domain map
- **UI Components**: React components to create/modify

#### 6.6 Acceptance Criteria
- Copy exact acceptance criteria from the epic story

#### 6.7 Tasks/Subtasks
Generate detailed tasks based on:
- Domain-specific implementation patterns
- Each involved service
- Cross-domain integrations identified
- Testing requirements from domain
Generate detailed, sequential list of additional tasks based on Story AC

#### 6.8 Dev Notes - Implementation Guide (CRITICAL)
Consolidate ALL context into actionable implementation guide:
- **Service Setup**: Exact file paths based on domain and service
- **API Specifications**: Complete OpenAPI specs extracted from architecture
- **Database Schema**: Full CREATE TABLE statements if needed
- **React Components**: TypeScript interfaces with Material-UI usage
- **Integration Code**: EventBridge event schemas, service calls
- **Testing Patterns**: Domain-specific test requirements

Include actual code snippets that can be copy-pasted.
Every technical detail MUST include source reference.

### 7. Apply Domain-Specific Enhancements

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

### 8. Story Draft Completion and Review

- Review all sections for completeness and domain alignment
- Verify all source references are included
- Ensure cross-domain dependencies are documented
- Update status to "Draft" and save the story file
- Execute `.bmad-core/tasks/execute-checklist` with `.bmad-core/checklists/story-draft-checklist.md`
- Provide summary to user including:
  - Story created: `{devStoryLocation}/{epicNum}.{storyNum}.{story_title_short}.md`
  - Status: Draft
  - Domain: {primary domain name}
  - Services involved: {list of services}
  - Key architecture sections referenced
  - Wireframes referenced (if applicable)
  - Cross-domain dependencies noted
  - Checklist results
  - Next steps: Review the story draft and validate domain context is complete

### 9. Domain Context Validation

Perform final validation:
- Confirm all domain-specific requirements are addressed
- Verify integration points with other domains are documented
- Check that all relevant wireframes are referenced
- Ensure functional requirements are properly traced
- Validate that the implementation guide in Dev Notes is complete and actionable

### Output Summary Format

```
✅ Story Created Successfully
━━━━━━━━━━━━━━━━━━━━━━━━━
📄 File: {epicNum}.{storyNum}.{story_title_short}.md
🎯 Domain: {domain_name}
🔧 Services: {services_list}
📋 Status: Draft
🏗️ Architecture Refs: {count} sections
🎨 Wireframes: {count} files referenced
🔗 Cross-Domain: {dependencies_list or "None"}
📊 Functional Reqs: {FR_numbers}
✓ Checklist: {pass/fail status}

Next Steps:
1. Review domain context alignment
2. Validate technical specifications
3. Approve story for development
```