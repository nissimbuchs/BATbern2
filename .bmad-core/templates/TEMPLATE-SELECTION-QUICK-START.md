# Story Template Quick Start Guide

## TL;DR - Choose Your Approach

### 🚀 **4-Story Approach** (Complex Features)
**Use when:** Complex features, large team (3+ devs), maximum parallelization

```bash
/sm
*draft-api-contract      # Story X.Xa (1 day)
*draft-frontend          # Story X.Xb (3 days, parallel with c)
*draft-backend           # Story X.Xc (3 days, parallel with b)
*draft-integration       # Story X.Xd (1 day)
```

**Result:** 8 days dev → **5 days calendar** (3 days saved!)

---

### ⚡ **2-Story Approach** (Simple Features)
**Use when:** Simple CRUD, small team (1-2 devs), less complexity

```bash
/sm
*draft-frontend-first        # Story X.X-1 (3 days)
*draft-backend-integration   # Story X.X-2 (2 days)
```

**Result:** 5 days dev → **3-5 days calendar**

---

### 🎯 **Single-Layer Approach**
**Use when:** Only one layer needs changes

```bash
/sm
*draft-frontend         # Frontend-only (2-4 days)
*draft-backend          # Backend-only (3-5 days)
*draft-infrastructure   # Infrastructure (2-5 days)
```

---

## Decision Tree (30 seconds)

```
START: New Feature Story

1. Infrastructure/cross-cutting? (CI/CD, AWS, monitoring)
   └─ YES → *draft-infrastructure
   └─ NO → Continue

2. How complex is the feature?
   ├─ COMPLEX (8+ days, intricate logic, multiple domains)
   │  └─ Large team (3+ devs)?
   │     └─ YES → 4-Story Approach (a,b,c,d)
   │     └─ NO → 2-Story Approach (-1,-2)
   │
   └─ SIMPLE (4-6 days, straightforward CRUD)
      └─ 2-Story Approach (-1,-2)

3. Single layer only?
   ├─ Frontend only? → *draft-frontend
   ├─ Backend only? → *draft-backend
   └─ Both? → See #2 above
```

---

## Quick Comparison

| Approach | Stories | Dev Time | Calendar | Team Size | Complexity | Use When |
|----------|---------|----------|----------|-----------|------------|----------|
| **4-Story** | 4 (a,b,c,d) | 8 days | 5 days | 2-3+ | High | Complex features, max parallelization |
| **2-Story** | 2 (-1,-2) | 5 days | 3-5 days | 1-2 | Low-Med | Simple CRUD, small teams |
| **Single** | 1 | 2-5 days | 2-5 days | 1 | Varies | Frontend-only, Backend-only |

---

## Examples

### Example 1: Topic Selection System (Complex)
**Complexity:** High (ML features, heat maps, similarity detection)
**Team:** 2-3 developers
**Choice:** **4-Story Approach**

```
2.2a: Topic API Contract (1 day)
2.2b: Topic Selector UI (3 days, parallel)
2.2c: Topic Service Backend (3 days, parallel)
2.2d: Topic Integration (1 day)

Calendar: 5 days (37% faster than 8-day sequential)
```

See: [docs/examples/epic-2-story-2.2-split-example.md](/docs/examples/epic-2-story-2.2-split-example.md)

---

### Example 2: Company Management (Simple CRUD)
**Complexity:** Low (basic CRUD operations)
**Team:** 1-2 developers
**Choice:** **2-Story Approach**

```
3.X-1: Company Management Frontend-First (3 days)
  - API contract inline
  - Complete UI with MSW mocks

3.X-2: Company Management Backend-Integration (2 days)
  - Backend implementation
  - Replace mocks

Calendar: 3-5 days depending on team
```

See: [docs/examples/company-management-2-story-example.md](/docs/examples/company-management-2-story-example.md)

---

### Example 3: Security Headers (Infrastructure)
**Complexity:** Medium (cross-cutting)
**Team:** 1 platform engineer
**Choice:** **Infrastructure Template**

```
1.11: Security Compliance Essentials (3 days)
  - Security headers
  - Input validation
  - GDPR endpoints
  - Audit logging

Calendar: 3 days
```

---

## Common Patterns

### Pattern 1: Full-Stack Feature (Medium Complexity)
→ **2-Story Approach**
```
Story X.X-1: Frontend-First (API inline + UI)
Story X.X-2: Backend-Integration (Backend + connect)
```

### Pattern 2: Full-Stack Feature (High Complexity)
→ **4-Story Approach**
```
Story X.Xa: API Contract
Story X.Xb: Frontend (parallel with c)
Story X.Xc: Backend (parallel with b)
Story X.Xd: Integration
```

### Pattern 3: UI Improvements Only
→ **Single Frontend Template**
```
Story X.Xb: Frontend improvements (use existing APIs)
```

### Pattern 4: Background Service
→ **Single Backend Template**
```
Story X.Xc: Background job implementation (no UI)
```

### Pattern 5: AWS Infrastructure
→ **Infrastructure Template**
```
Story X.X: Infrastructure setup (CDK, monitoring, etc)
```

---

## Scrum Master Commands

### Setup
```bash
/sm  # Activate Scrum Master agent
*help  # Show all available commands
```

### 4-Story Approach Commands
```bash
*draft-api-contract      # Step 1: API Contract
*draft-frontend          # Step 2: Frontend with mocks
*draft-backend           # Step 3: Backend implementation
*draft-integration       # Step 4: Integration
```

### 2-Story Approach Commands
```bash
*draft-frontend-first        # Step 1: API inline + Frontend
*draft-backend-integration   # Step 2: Backend + Integration
```

### Single-Layer Commands
```bash
*draft-frontend         # Frontend only
*draft-backend          # Backend only
*draft-infrastructure   # Infrastructure/cross-cutting
```

### Helper Commands
```bash
*template-guide         # Full selection guide
*splitting-examples     # Before/after examples
```

---

## When in Doubt

**Question:** Should I split this story?
**Answer:** If it's >5 days or involves both frontend AND backend → Yes, split it!

**Question:** 4-story or 2-story?
**Answer:**
- Complex feature + large team → 4-story
- Simple CRUD + small team → 2-story

**Question:** Can I start backend before API contract is done?
**Answer:** No! API contract (or inline definition) must be done first.

**Question:** Can frontend and backend work in parallel?
**Answer:**
- 4-Story: YES! After API Contract (story a) is done, stories b and c run parallel
- 2-Story: Limited - frontend can start first with mocks, backend follows

---

## Success Metrics

### 4-Story Approach Results
- ⏱️ **37% faster delivery** (5 days vs 8 days)
- 👥 **2-3 developers work in parallel**
- 📦 **Smaller PRs** (easier reviews)
- 🎯 **Focused work** (stay in expertise zone)
- ⚠️ **Early risk detection** (contract first)

### 2-Story Approach Results
- ⏱️ **Simpler coordination** (2 stories vs 4)
- 👥 **Flexible team size** (works with 1-2 devs)
- 🚀 **Frontend-first** (demo before backend ready)
- 📦 **Medium PRs** (manageable size)
- ⚡ **Faster setup** (API inline, not separate)

---

## Getting Started Today

1. **Pick an upcoming story** from your backlog
2. **Assess complexity** (Simple CRUD vs Complex Feature)
3. **Choose approach** (2-story vs 4-story)
4. **Run Scrum Master:** `/sm`
5. **Create stories:** Use `*draft-` commands
6. **Start work!**

---

## More Resources

📖 **Comprehensive Guide:** [.bmad-core/docs/story-template-guide.md](/.bmad-core/docs/story-template-guide.md)
📝 **Before/After Examples:** [.bmad-core/docs/story-splitting-examples.md](/.bmad-core/docs/story-splitting-examples.md)
🎯 **Complete Examples:**
- [Epic 2 Story 2.2 (4-Story)](/docs/examples/epic-2-story-2.2-split-example.md)
- [Company Management (2-Story)](/docs/examples/company-management-2-story-example.md)

📋 **Template Overview:** [.bmad-core/templates/README-NEW-TEMPLATES.md](/.bmad-core/templates/README-NEW-TEMPLATES.md)

---

**Ready to go? Run `/sm` and start with `*template-guide` or `*splitting-examples`!** 🚀
