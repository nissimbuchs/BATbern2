# Story Template Selection Guide

## Overview

The BATbern project uses **layered story templates** to enable focused, parallel development across frontend, backend, and infrastructure teams. This guide helps Scrum Masters select the appropriate template(s) for each user story.

## Available Templates

### 1. **API Contract Template** (`story-api-contract-tmpl.yaml`)
**When to use:** Contract-first API definition
- Defines OpenAPI specifications
- Establishes request/response schemas
- Configures API Gateway routes
- Creates contract tests
- **Duration:** 1-2 days
- **Owner:** API designers, architects, or full-stack lead
- **Output:** OpenAPI spec, contract tests, mock data

### 2. **Frontend Feature Template** (`story-frontend-tmpl.yaml`)
**When to use:** UI/UX implementation with mocked backend
- Implements React components
- Uses MSW (Mock Service Worker) for API mocking
- Focuses on user experience
- Tests with mocked data
- **Duration:** 2-4 days
- **Owner:** Frontend developers
- **Output:** Complete UI with MSW integration
- **Dependency:** Requires API Contract story completed

### 3. **Backend Implementation Template** (`story-backend-tmpl.yaml`)
**When to use:** Business logic and data layer implementation
- Implements Spring Boot services
- Creates domain models (DDD)
- Designs database schemas
- Validates against API contract
- **Duration:** 3-5 days
- **Owner:** Backend developers
- **Output:** Backend service with contract validation
- **Dependency:** Requires API Contract story completed

### 4. **Integration Template** (`story-integration-tmpl.yaml`)
**When to use:** Connect frontend and backend, replace mocks
- Removes MSW mocks
- Connects real APIs
- End-to-end testing
- Performance validation
- **Duration:** 1-2 days
- **Owner:** Full-stack developer or integration specialist
- **Output:** Fully integrated feature
- **Dependency:** Requires Frontend AND Backend stories completed

### 5. **Infrastructure Template** (`story-infrastructure-tmpl.yaml`)
**When to use:** Cross-cutting infrastructure concerns
- AWS infrastructure (CDK)
- CI/CD pipelines
- Monitoring & observability
- Security & compliance
- Authentication systems
- **Duration:** 2-5 days (varies widely)
- **Owner:** DevOps, platform engineers, or senior developers
- **Output:** Deployed infrastructure

### 6. **Frontend-First Template (2-Story Approach)** (`story-frontend-first-tmpl.yaml`)
**When to use:** Simpler features with small teams
- Defines API contract inline (not separate story)
- Implements complete UI with MSW mocks
- Combines API Contract + Frontend work
- **Duration:** 3-4 days
- **Owner:** Frontend developer or full-stack developer
- **Output:** API contract + Complete UI with mocks
- **Next Step:** Story X.X-2 (Backend-Integration)

### 7. **Backend-Integration Template (2-Story Approach)** (`story-backend-integration-tmpl.yaml`)
**When to use:** Backend for Frontend-First story
- Implements backend against inline contract
- Replaces MSW mocks with real APIs
- Combines Backend + Integration work
- **Duration:** 2-3 days
- **Owner:** Backend developer or full-stack developer
- **Output:** Complete feature with real backend
- **Dependency:** Requires Story X.X-1 (Frontend-First) completed

---

## Two Approaches: 4-Story vs 2-Story

### 4-Story Approach (Recommended for Complex Features)
**Use when:** Complex features, large team, high parallelization needed

```
Story X.Xa: API Contract (1 day)
  ├─> Story X.Xb: Frontend (3 days, parallel)
  └─> Story X.Xc: Backend (3 days, parallel)
        └─> Story X.Xd: Integration (1 day)

Total: 8 days dev, 5 days calendar
```

**Benefits:**
- Maximum parallelization
- Separate API contract story
- Clear layer separation
- Best for teams >3 developers

### 2-Story Approach (Recommended for Simple Features)
**Use when:** Simple CRUD, small team (1-2 developers), tight deadline

```
Story X.X-1: Frontend-First (3 days)
  - Define API contract inline
  - Implement complete UI with MSW mocks

Story X.X-2: Backend-Integration (2 days)
  - Implement backend against inline contract
  - Replace mocks with real APIs

Total: 5 days dev, 5 days calendar (or 3 days if sequential)
```

**Benefits:**
- Simpler than 4-story split
- Frontend-first approach
- Less coordination overhead
- Good for small teams

---

## Decision Matrix

### Quick Decision Tree

```
START
│
├─ Is this about infrastructure, CI/CD, or cross-cutting concerns?
│   └─ YES → Use Infrastructure Template
│   └─ NO → Continue
│
├─ Does this story involve API changes?
│   └─ YES → Start with API Contract Template
│   │         ├─ Has UI changes? → Add Frontend Template
│   │         └─ Has backend logic? → Add Backend Template
│   │         └─ Both? → Add Integration Template at the end
│   │
│   └─ NO → Continue
│
├─ Is this UI-only (no API changes)?
│   └─ YES → Use Frontend Template only (with existing APIs)
│   └─ NO → Continue
│
├─ Is this backend-only (no UI changes)?
│   └─ YES → Use Backend Template only
│   └─ NO → Continue
│
└─ Is this a simple CRUD feature?
    └─ Can combine into 2 stories:
        ├─ Frontend-first (with API Contract inline)
        └─ Backend implementation
```

### Detailed Decision Criteria

#### Use **API Contract Template** when:
- [ ] Defining new API endpoints
- [ ] Modifying existing API contracts
- [ ] Changing request/response schemas
- [ ] Frontend and backend will work in parallel
- [ ] Multiple teams need clear API specifications

**Examples:**
- "Define Event Creation API"
- "Add Topic Selection Endpoint"
- "Modify Speaker Invitation Response Schema"

#### Use **Frontend Template** when:
- [ ] Implementing UI components
- [ ] Adding user interactions
- [ ] Creating forms or displays
- [ ] Working on UX improvements
- [ ] Backend API already defined or available

**Examples:**
- "Implement Event Type Selector Component"
- "Create Speaker Dashboard UI"
- "Build Topic Selection Interface"

#### Use **Backend Template** when:
- [ ] Implementing business logic
- [ ] Creating database schemas
- [ ] Adding domain models
- [ ] Implementing background jobs
- [ ] Processing data

**Examples:**
- "Implement Event Creation Service"
- "Add Topic Usage Tracking Logic"
- "Create Speaker Matching Algorithm"

#### Use **Integration Template** when:
- [ ] Connecting frontend to backend
- [ ] Replacing mock APIs with real ones
- [ ] Validating end-to-end flows
- [ ] Performance testing
- [ ] Deployment validation

**Examples:**
- "Integrate Event Creation Feature"
- "Connect Topic Selector to Backend"
- "Replace Speaker Dashboard Mocks"

#### Use **Infrastructure Template** when:
- [ ] Setting up AWS resources
- [ ] Configuring CI/CD
- [ ] Adding monitoring/alerting
- [ ] Implementing security controls
- [ ] Setting up databases
- [ ] Configuring authentication

**Examples:**
- "Setup Event Management Service Infrastructure"
- "Configure CI/CD Pipeline"
- "Implement Security Headers"
- "Setup CloudWatch Monitoring"

---

## Common Story Patterns

### Pattern 1: Full Feature (Frontend + Backend + API)

**Example:** Story 2.2 - Topic Selection System

Split into:
1. **Story 2.2a: API Contract** (1 day)
   - Define GET/POST /api/v1/topics
   - Request/response schemas
   - OpenAPI specification

2. **Story 2.2b: Frontend Implementation** (3 days)
   - TopicSelector React component
   - MSW mocks for /api/v1/topics
   - Component testing with mocks

3. **Story 2.2c: Backend Implementation** (3 days)
   - TopicService business logic
   - Database schema for topics table
   - Contract tests validation

4. **Story 2.2d: Integration** (1 day)
   - Remove MSW mocks
   - Connect UI to real backend
   - E2E testing
   - **Total: 8 days (but 2.2b and 2.2c run in parallel!)**

**Parallel Development:**
- Day 1: API Contract (2.2a)
- Days 2-4: Frontend (2.2b) AND Backend (2.2c) work simultaneously
- Day 5: Integration (2.2d)
- **Actual Calendar Time: 5 days instead of 8!**

### Pattern 2: Frontend-Only Story

**Example:** Update existing UI without API changes

Use:
- **Frontend Template** only
- Reference existing API contracts
- No backend or integration story needed

**Example Stories:**
- "Improve Event List Filtering UI"
- "Add Loading Skeletons to Dashboard"
- "Enhance Form Validation Feedback"

### Pattern 3: Backend-Only Story

**Example:** Background job or internal service logic

Use:
- **Backend Template** only
- No frontend changes needed
- May include API endpoints for admin or other services

**Example Stories:**
- "Implement Daily Speaker Reminder Job"
- "Add Event Archive Cleanup Service"
- "Create Analytics Aggregation Process"

### Pattern 4: Infrastructure Story

**Example:** Setup new service or shared infrastructure

Use:
- **Infrastructure Template** only
- Affects multiple services or teams
- Foundational work

**Example Stories:**
- "Setup Speaker Coordination Service Infrastructure"
- "Implement Multi-Environment CDK Pipeline"
- "Configure EventBridge for Domain Events"

### Pattern 5: Simple CRUD Feature

**Example:** Basic create/read/update/delete

**Option A: Split into 3 stories**
1. API Contract (1 day)
2. Frontend + Backend in parallel (3 days each)
3. Integration (1 day)

**Option B: Combine into 2 stories**
1. Frontend-first with inline API contract (3 days)
   - Define API inline
   - Implement UI with mocks
2. Backend implementation (3 days)
   - Implement against defined contract
   - Auto-integrate when done

**Choose Option B when:**
- Team is small (1-2 developers)
- Feature is straightforward
- Tight deadline
- Low complexity

---

## Template Selection Examples

### Example 1: Event Type Definition (Story 2.1)

**Analysis:**
- Needs API for CRUD operations
- Needs UI for selection
- Needs backend for persistence
- Moderate complexity

**Decision:**
Split into 4 stories:
1. **2.1a: API Contract** - Define event type endpoints
2. **2.1b: Frontend** - EventTypeSelector component
3. **2.1c: Backend** - EventType service and database
4. **2.1d: Integration** - Connect and test

**Rationale:**
- Parallel development saves time
- Clear separation of concerns
- Multiple developers can work simultaneously

### Example 2: Security Headers (Story 1.11)

**Analysis:**
- Infrastructure/cross-cutting concern
- Affects API Gateway
- No specific frontend or backend changes
- Security requirement

**Decision:**
Single story:
- **1.11: Infrastructure** - Implement security headers in API Gateway

**Rationale:**
- Infrastructure template covers this use case
- No need to split
- Cross-cutting concern

### Example 3: Speaker Dashboard Improvements

**Analysis:**
- Only UI changes
- Uses existing APIs
- No backend changes needed
- Frontend-focused

**Decision:**
Single story:
- **X.Xb: Frontend** - Enhance speaker dashboard UI

**Rationale:**
- Frontend-only change
- Existing APIs sufficient
- No integration needed

### Example 4: Event Archive Service

**Analysis:**
- Background processing
- No UI changes
- Backend logic and database
- May expose admin API

**Decision:**
Two stories:
1. **X.Xa: API Contract** - Admin API for archive management (optional)
2. **X.Xc: Backend** - Archive service and cleanup logic

**Rationale:**
- No frontend needed
- API contract only if admin UI exists
- Backend template sufficient

---

## Workflow Guidelines

### For Scrum Masters

**During Sprint Planning:**
1. Review each user story
2. Identify layers involved (Frontend, Backend, API, Infrastructure)
3. Select appropriate template(s)
4. Split story into sub-stories if needed
5. Assign sub-stories to appropriate developers
6. Set up dependency tracking (a → b,c → d)

**Story Numbering Convention:**
- Original story: 2.2
- API Contract: 2.2a
- Frontend: 2.2b
- Backend: 2.2c
- Integration: 2.2d

**Dependency Management:**
- Mark Frontend (b) and Backend (c) as "Blocked" until API Contract (a) is "Done"
- Mark Integration (d) as "Blocked" until both Frontend (b) and Backend (c) are "Done"

### For Developers

**When working on Frontend stories:**
1. Wait for API Contract story to be Done
2. Use generated TypeScript types
3. Configure MSW mocks based on contract
4. Develop UI in isolation
5. Write tests using mocks
6. Mark ready for integration when Done

**When working on Backend stories:**
1. Wait for API Contract story to be Done
2. Use generated Java DTOs
3. Implement against contract tests
4. Develop service in isolation
5. Write tests validating contract
6. Mark ready for integration when Done

**When working on Integration stories:**
1. Wait for both Frontend and Backend to be Done
2. Remove/disable MSW mocks
3. Configure environment for real APIs
4. Run E2E tests
5. Validate performance
6. Deploy and monitor

---

## Benefits of This Approach

### For Teams
✅ **Parallel Development** - Frontend and backend work simultaneously
✅ **Focused Work** - Developers stay in their expertise zone
✅ **Reduced Context Switching** - No jumping between layers
✅ **Clear Ownership** - Each layer has clear responsibility
✅ **Better Testing** - Layer-specific testing strategies

### For Projects
✅ **Faster Delivery** - Parallel work reduces calendar time
✅ **Higher Quality** - Focused development improves quality
✅ **Easier Reviews** - Smaller, focused PRs easier to review
✅ **Better Documentation** - Layer-specific documentation
✅ **Flexibility** - Can prioritize layers independently

### For Individuals
✅ **Skill Development** - Developers deepen expertise
✅ **Clearer Tasks** - Smaller, more manageable stories
✅ **Less Overwhelm** - Don't need full-stack knowledge for every story
✅ **Better Estimates** - Easier to estimate focused work
✅ **More Confidence** - Work in comfort zone

---

## Migration from Old Template

### Existing Stories

For stories already created with the old template:
1. **Don't rewrite completed stories** - Leave them as-is
2. **For in-progress stories** - Consider splitting if >50% remaining
3. **For new stories** - Always use new templates

### Gradual Adoption

**Week 1-2:**
- Use new templates for infrastructure stories
- Continue old template for feature stories
- Team training on new approach

**Week 3-4:**
- Start using split templates for complex features
- Use combined approach for simple features
- Retrospective on benefits/challenges

**Week 5+:**
- Full adoption of new templates
- Refine based on team feedback
- Document lessons learned

---

## Troubleshooting

### "When should I NOT split a story?"

DON'T split when:
- Story is very simple (< 2 days total)
- Only one developer available
- Infrastructure-only work
- Tight coupling requires simultaneous changes
- Experimental/prototype work

### "What if Frontend needs Backend changes during development?"

1. Frontend discovers API needs changes:
   - Stop Frontend story
   - Update API Contract story
   - Backend implements changes
   - Frontend resumes

2. For minor changes:
   - Frontend and Backend coordinate directly
   - Update API Contract documentation after
   - Document in story notes

### "What if Integration reveals issues?"

1. **Frontend issues:**
   - Reopen Frontend story
   - Fix issues
   - Re-mark as Done

2. **Backend issues:**
   - Reopen Backend story
   - Fix issues
   - Re-mark as Done

3. **Contract issues:**
   - Update API Contract
   - May require changes to Frontend and Backend
   - Update contract version

### "How to handle shared work?"

For work spanning multiple domains:
- Create multiple stories with clear boundaries
- Use Infrastructure template for shared components
- Coordinate through API contracts
- Document dependencies clearly

---

## Getting Help

**Questions about template selection?**
- Ask the Architect agent (`/architect`)
- Consult with Tech Lead
- Review this guide
- Refer to examples in `.bmad-core/docs/story-splitting-examples.md`

**Issues with templates?**
- Report in project retrospectives
- Propose improvements
- Update this guide
- Share with team

---

## Summary Quick Reference

| Story Type | Template(s) | Duration | Dependencies | Use When |
|------------|-------------|----------|--------------|----------|
| **4-Story Approach (Complex)** | a + b + c + d | 8 days (5 cal) | a → b,c → d | Large team, complex feature |
| **2-Story Approach (Simple)** | -1 + -2 | 5 days (3-5 cal) | -1 → -2 | Small team, simple CRUD |
| Frontend Only | Frontend | 2-4 days | Existing APIs | UI changes only |
| Backend Only | Backend | 3-5 days | None or existing | No UI needed |
| Infrastructure | Infrastructure | 2-5 days | Varies | AWS/cross-cutting |

**Template Details:**
- **4-Story:** a=API Contract, b=Frontend, c=Backend, d=Integration
- **2-Story:** -1=Frontend-First (inline API), -2=Backend-Integration

**Remember:** When in doubt, split it out! Smaller stories are easier to manage, review, and deliver.
