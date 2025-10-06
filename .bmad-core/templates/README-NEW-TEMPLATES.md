# Layered Story Templates - Overview

## What Changed?

The BATbern project now uses **5 specialized story templates** instead of one monolithic template. This enables focused development, parallel work, and faster delivery.

## The Problem We Solved

**Before:** Stories were too large, mixing frontend, backend, API, and infrastructure concerns
- ❌ 8-10 day stories
- ❌ Context switching between layers
- ❌ No parallelization
- ❌ Late integration
- ❌ Massive PRs

**After:** Stories split by layer for focused, parallel development
- ✅ 2-4 day focused stories
- ✅ Developers work in their expertise zone
- ✅ Frontend and backend work in parallel
- ✅ Early contract definition
- ✅ Small, reviewable PRs

## The 5 Templates

### 1. API Contract Template
**File:** `story-api-contract-tmpl.yaml`
**Purpose:** Define API contracts first (Contract-First Development)
**Duration:** 1-2 days
**Usage:** Start every full-stack feature with this

### 2. Frontend Feature Template
**File:** `story-frontend-tmpl.yaml`
**Purpose:** Implement UI/UX with MSW mocked backend
**Duration:** 2-4 days
**Usage:** After API Contract is done, parallel with Backend

### 3. Backend Implementation Template
**File:** `story-backend-tmpl.yaml`
**Purpose:** Implement business logic and data layer
**Duration:** 3-5 days
**Usage:** After API Contract is done, parallel with Frontend

### 4. Integration Template
**File:** `story-integration-tmpl.yaml`
**Purpose:** Replace mocks with real backend, E2E testing
**Duration:** 1-2 days
**Usage:** After both Frontend and Backend are done

### 5. Infrastructure Template
**File:** `story-infrastructure-tmpl.yaml`
**Purpose:** Cross-cutting infrastructure concerns
**Duration:** 2-5 days
**Usage:** For AWS infrastructure, CI/CD, monitoring, security

## Quick Start

### For Scrum Masters

**When creating a new feature story:**

1. **Determine if it needs splitting:**
   - Complex feature (>5 days)? → Split into 4 stories (a, b, c, d)
   - Simple feature (<5 days)? → Consider 2-story approach
   - Frontend only? → Use Frontend template only
   - Backend only? → Use Backend template only
   - Infrastructure? → Use Infrastructure template only

2. **Use the Scrum Master agent:**
   ```bash
   /sm
   *help  # See available commands
   *template-guide  # Show decision matrix
   *splitting-examples  # See concrete examples
   ```

3. **Create stories using commands:**
   ```bash
   *draft-api-contract  # For API Contract story (2.2a)
   *draft-frontend      # For Frontend story (2.2b)
   *draft-backend       # For Backend story (2.2c)
   *draft-integration   # For Integration story (2.2d)
   *draft-infrastructure # For Infrastructure story
   ```

### For Developers

**Working on Frontend stories:**
1. Wait for API Contract story to be Done
2. Use generated TypeScript types
3. Configure MSW mocks
4. Develop UI in isolation
5. Write tests using mocks

**Working on Backend stories:**
1. Wait for API Contract story to be Done
2. Use generated Java DTOs
3. Implement against contract tests
4. Develop service in isolation
5. Validate against OpenAPI spec

**Working on Integration stories:**
1. Wait for Frontend AND Backend to be Done
2. Remove/disable MSW mocks
3. Configure real API endpoints
4. Run E2E tests
5. Deploy and monitor

## Example: Story 2.2 (Topic Selection System)

### Original (Monolithic)
- **1 Story:** 8-10 days, 1 developer, sequential

### New Approach (Layered)
- **Story 2.2a:** API Contract (1 day)
- **Story 2.2b:** Frontend (3 days, parallel with 2.2c)
- **Story 2.2c:** Backend (3 days, parallel with 2.2b)
- **Story 2.2d:** Integration (1 day)

**Result:** 5 days calendar time instead of 8-10 days (37% faster!)

See full example: [docs/examples/epic-2-story-2.2-split-example.md](../../docs/examples/epic-2-story-2.2-split-example.md)

## Documentation

### Must-Read Guides

1. **[Story Template Selection Guide](.bmad-core/docs/story-template-guide.md)**
   - Decision matrix for choosing templates
   - When to split vs when not to
   - Common patterns and examples

2. **[Story Splitting Examples](.bmad-core/docs/story-splitting-examples.md)**
   - Before/after comparisons
   - Real examples from Epic 2
   - Lessons learned

3. **[Example: Story 2.2 Split](../../docs/examples/epic-2-story-2.2-split-example.md)**
   - Complete split example
   - All 4 stories fully documented
   - Shows OpenAPI spec, React components, Spring Boot services

## Benefits Summary

### For Teams
✅ **Parallel Development** - Frontend and backend work simultaneously
✅ **Focused Work** - Developers stay in expertise zone
✅ **Reduced Context Switching** - No jumping between layers
✅ **Clear Ownership** - Each layer has clear responsibility
✅ **Better Testing** - Layer-specific testing strategies

### For Projects
✅ **Faster Delivery** - 30-40% faster through parallel work
✅ **Higher Quality** - Focused development improves quality
✅ **Easier Reviews** - Smaller, focused PRs
✅ **Better Documentation** - Layer-specific docs
✅ **Flexibility** - Can prioritize layers independently

### For Individuals
✅ **Skill Development** - Developers deepen expertise
✅ **Clearer Tasks** - Smaller, more manageable stories
✅ **Less Overwhelm** - Don't need full-stack for every story
✅ **Better Estimates** - Easier to estimate focused work
✅ **More Confidence** - Work in comfort zone

## Migration Guide

### Existing Stories

**Completed stories:** Leave as-is, no changes needed
**In-progress stories:** Consider splitting if >50% work remains
**New stories:** Always use new templates

### Gradual Adoption (Recommended)

**Week 1-2:**
- Use new templates for infrastructure stories
- Continue old template for feature stories
- Team training

**Week 3-4:**
- Start splitting complex features
- Use combined approach for simple features
- Retrospective

**Week 5+:**
- Full adoption
- Refine based on feedback
- Document lessons learned

## Common Questions

### "When should I NOT split a story?"
- Story is simple (<2 days total)
- Only one developer available
- Infrastructure-only work
- Tight coupling requires simultaneous changes
- Experimental/prototype work

### "What if we discover API needs changes during development?"
- Stop current work
- Update API Contract story
- Both frontend and backend update accordingly
- Resume development

### "How do we handle shared work?"
- Create multiple stories with clear boundaries
- Use Infrastructure template for shared components
- Coordinate through API contracts

## Getting Help

**Questions about templates?**
- Ask the Architect agent: `/architect`
- Read the template selection guide
- Review splitting examples
- Consult with Tech Lead

**Issues or improvements?**
- Discuss in retrospectives
- Update documentation
- Share with team

## Files Reference

### Templates
- `story-api-contract-tmpl.yaml` - API Contract stories
- `story-frontend-tmpl.yaml` - Frontend stories
- `story-backend-tmpl.yaml` - Backend stories
- `story-integration-tmpl.yaml` - Integration stories
- `story-infrastructure-tmpl.yaml` - Infrastructure stories
- `story-tmpl.yaml` - Legacy template (still available)

### Documentation
- `.bmad-core/docs/story-template-guide.md` - Selection guide
- `.bmad-core/docs/story-splitting-examples.md` - Examples
- `docs/examples/epic-2-story-2.2-split-example.md` - Complete example

### Agents
- `.bmad-core/agents/sm.md` - Updated Scrum Master with new commands

## Summary

The new layered story templates enable:
- **37% faster delivery** through parallel development
- **Higher quality** through focused work
- **Better team scalability** through specialization
- **Reduced risk** through early contract definition

Start using them today for your next feature story!

---

**Need help?** Run `/sm` and use `*template-guide` or `*splitting-examples`
