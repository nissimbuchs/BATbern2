<!-- Powered by BMAD™ Core -->

# Create Next Wireframe Task

## Purpose

To identify the next logical wireframe based on PRD requirements and front-end specifications, and create comprehensive, detailed wireframe documentation using the wireframe template. This task ensures wireframes are complete, actionable, and ready for implementation with full API specifications, navigation maps, and technical requirements.

## SEQUENTIAL Task Execution (Do not proceed until current Task is complete)

### 0. Load Core Configuration and Project Context

- Load `.bmad-core/core-config.yaml` from the project root
- If the file does not exist, HALT and inform the user: "core-config.yaml not found. This file is required for wireframe creation. Please add and configure core-config.yaml before proceeding."
- Extract key configurations: `prd.*`, `front-end-spec.*`, `architecture.*`, `docs/wireframes/sitemap.md`
- Load `.bmad-core/data/domain-context-map.yaml` to understand user roles and domains

### 1. Identify Next Wireframe for Creation

#### 1.1 Review Existing Wireframes and Identify Gaps

- Locate existing wireframe files in `docs/wireframes/`
- If navigation gaps analysis exists (`docs/wireframes/navigation-gaps-analysis.md`), load it
- Load the highest numbered `story-{epic}.{story}-*.md` wireframe file
- **If wireframes exist:**
  - Identify gaps based on navigation gaps analysis
  - Check PRD and front-end spec for unimplemented screens
  - Prompt user: "Found {count} existing wireframes. Missing screens identified: {list}. Which wireframe would you like to create next? 1) {screen_name} 2) {screen_name} 3) Custom screen"
- **If no wireframes exist:** Start with the most critical screen from PRD (typically Dashboard or Landing Page)
- Announce the identified wireframe to the user: "Identified next wireframe for creation: {Screen Name} for {User Role}"

### 2. Gather Requirements and Context

#### 2.1 Load Product Requirements

- Read `docs/prd-enhanced.md` or configured PRD location
- Extract:
  - Related functional requirements (FRs) for this screen
  - User roles and their goals
  - Core workflows involving this screen
  - Success criteria and KPIs
  - Non-functional requirements (performance, accessibility)

#### 2.2 Load Front-End Specification

- Read `docs/front-end-spec.md` or configured frontend spec location
- Extract:
  - UI/UX design goals and principles
  - Visual design language (colors, typography, spacing)
  - Component library and framework choices
  - Responsive design requirements
  - Accessibility standards (WCAG level)
  - Interaction paradigms
  - State management approach

#### 2.3 Load Architecture Context

- Read relevant architecture documents:
  - `docs/architecture/05-frontend-architecture.md` - React patterns, component structure
  - `docs/architecture/04-api-design.md` - API endpoints and contracts
  - `docs/architecture/03-data-architecture.md` - Data models and entities
  - `docs/architecture/01-system-overview.md` - Overall system context
- Extract patterns and specifications relevant to this screen

#### 2.4 Review Related Stories and Epics

- If this wireframe corresponds to a specific story, read the epic file
- Extract:
  - User story statement
  - Acceptance criteria
  - Technical specifications
  - Related workflows

### 3. Determine Screen Context and Relationships

#### 3.1 Identify User Role and Primary Use Case

- Determine primary user role
- Identify the core user goal for this screen
- List secondary user roles that may access this screen

#### 3.2 Map Related Screens and Navigation Flow

- Identify screens that navigate TO this screen (entry points)
- Identify screens that navigate FROM this screen (exit points)
- Document the user journey context
- Note any conditional navigation based on user state or permissions

#### 3.3 Identify Data Requirements

- List all data entities displayed on this screen
- Identify data relationships and aggregations
- Note real-time vs. static data requirements
- Determine caching and performance needs

### 4. Create Wireframe Using Template

Create new wireframe file: `docs/wireframes/story-{epic}.{story}-{screen-name}.md` using the wireframe template (`.bmad-core/templates/wireframe-tmpl.yaml`).

Before completing the wireframe:

#### 8.1 Completeness Check
- [ ] All interactive elements documented
- [ ] All APIs specified with complete details
- [ ] All navigation paths mapped
- [ ] All user interactions described
- [ ] All error states covered
- [ ] All validation rules defined
- [ ] All responsive behaviors noted
- [ ] All accessibility requirements included

#### 8.2 Consistency Check
- [ ] Terminology consistent with PRD and other wireframes
- [ ] Navigation targets reference valid screens
- [ ] API endpoints follow API design standards
- [ ] UI patterns match frontend architecture
- [ ] Data structures match data architecture

#### 8.3 Implementation Readiness Check
- [ ] Developers have all info needed to implement
- [ ] No ambiguous requirements
- [ ] All dependencies and integrations identified
- [ ] Testing scenarios implied by acceptance criteria
- [ ] Performance and accessibility requirements clear

### 9. Wireframe Completion and Review

- Save wireframe file with proper naming: `story-{epic}.{story}-{screen-name}.md`
- Update navigation gaps analysis if it exists
- Generate summary for user

### 10. Output Summary Format

```
✅ Wireframe Created Successfully
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📄 File: story-{epic}.{story}-{screen-name}.md
🎭 Screen: {Screen Name}
👤 User Role: {User Role}
📋 Related FRs: {FR1, FR2, FR3}
🔌 APIs Defined: {count} load + {count} action APIs
🧭 Navigation: {count} paths documented
📱 Responsive: {Yes/No}
♿ Accessibility: {WCAG level or N/A}
📊 Forms: {count} forms with validation rules
⚡ Performance: {requirements or N/A}

Related Screens:
- Navigates FROM: {list}
- Navigates TO: {list}

Next Steps:
1. Review wireframe for completeness
2. Validate API specifications with backend team
3. Confirm navigation flow with product owner
4. Approve for implementation
```

## Best Practices

### Do's
- ✅ Start with mobile layout first, then adapt for larger screens
- ✅ Include realistic example data in wireframes
- ✅ Document every API call with complete specifications
- ✅ Map every navigation path, including error states
- ✅ Show loading states, empty states, and error states
- ✅ Reference other wireframes for consistent navigation
- ✅ Use consistent terminology from PRD and architecture
- ✅ Include accessibility requirements from the start
- ✅ Document validation rules clearly
- ✅ Consider edge cases and error scenarios

### Don'ts
- ❌ Don't create generic wireframes without specific requirements
- ❌ Don't omit API specifications (they block development)
- ❌ Don't forget error states and edge cases
- ❌ Don't ignore accessibility requirements
- ❌ Don't create disconnected screens (map navigation)
- ❌ Don't use vague descriptions (be specific)
- ❌ Don't skip responsive design considerations
- ❌ Don't forget real-time update mechanisms
- ❌ Don't overlook performance requirements
- ❌ Don't create wireframes that conflict with architecture

## Integration with Development Workflow

This wireframe becomes the source of truth for:
- Frontend developers implementing React components
- Backend developers implementing API endpoints
- QA engineers writing test scenarios
- Product owners validating functionality
- Designers ensuring visual consistency

All API endpoints, navigation paths, and validation rules defined here should be implemented exactly as specified unless changes are explicitly agreed upon and documented.

## Notes

- Wireframes should be created iteratively, starting with core screens
- Navigation gaps analysis helps identify missing screens
- Regular reviews with stakeholders ensure alignment
- Update wireframes when requirements change
- Keep wireframes synchronized with PRD and architecture
- Use wireframe coverage report to track completion
