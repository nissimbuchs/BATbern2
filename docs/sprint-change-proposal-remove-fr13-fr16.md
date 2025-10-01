# Sprint Change Proposal: Remove FR13 and FR16 from MVP Scope

**Date:** 2025-10-01
**Change Type:** Scope Reduction / Strategic Refocus
**Status:** ✅ APPROVED
**Impact Level:** Moderate (MVP timeline reduction, complexity reduction)

---

## Executive Summary

This proposal recommends removing **FR13 (Intelligent Content Discovery)** and **FR16 (Community Features)** from the BATbern platform MVP scope as part of a strategic refocus. This change simplifies the technical architecture, reduces development complexity, and accelerates time-to-market by approximately **4 weeks** while maintaining all core event management functionality.

**Key Outcomes:**
- ✅ Core event management workflows fully preserved
- ✅ Eliminates AI/ML infrastructure requirements (AWS OpenSearch, SageMaker)
- ✅ Reduces Epic 5 duration from 8 → 6 weeks
- ✅ Reduces Epic 7 duration from 6 → 4 weeks
- ✅ MVP remains viable and delivers primary business value
- ✅ Features can be reconsidered post-MVP for future enhancement

---

## 1. Issue Summary

**Trigger:** Proactive strategic refocus decision to simplify MVP scope

**Requirements Being Removed:**
- **FR13**: Attendees shall access intelligent content discovery across historical BATbern events (20+ years) with AI-powered search, personalized recommendations, and advanced filtering as secondary functionality
- **FR16**: Attendees shall access community features including content ratings, social sharing, and curated learning pathways connecting related presentations across events

**Rationale:**
- Strategic refocus on core event management capabilities
- Reduce technical complexity and infrastructure requirements
- Accelerate MVP delivery timeline
- Both requirements are "nice-to-have" attendee-facing enhancements, not critical to core platform functionality

---

## 2. Epic Impact Analysis

### **Epic 5: Attendee Experience** (Weeks 39-46)

**Impact:** ⚠️ **MODERATE** - Requires Story 5.1 simplification

**Changes Required:**
- **Story 5.1: Historical Content Discovery** → **Story 5.1: Historical Content Search**
  - **Remove:** AI-powered recommendations, ML-based "related content" suggestions
  - **Keep:** Full-text search, filtering, content preview, downloads, auto-complete
  - **Technical Change:** PostgreSQL full-text search instead of AWS OpenSearch + ML

- **Story 5.2: Personal Engagement Management** → ✅ **NO CHANGE**
- **Story 5.3: Mobile Progressive Web App** → ✅ **NO CHANGE**

**Timeline Impact:** 8 weeks → **6 weeks** (Story 5.1 simplified, ~2 weeks saved)

---

### **Epic 7: Enhanced Features** (Weeks 57-62)

**Impact:** ⚠️ **SIGNIFICANT** - Remove 2 complete stories

**Changes Required:**
- **Story 7.1: Speaker Dashboard** → ✅ **NO CHANGE**
- **Story 7.2: Advanced Material Management** → ✅ **NO CHANGE**
- **Story 7.3: Communication Hub** → ✅ **NO CHANGE**
- **Story 7.4: Community Features** → ❌ **REMOVE ENTIRELY** (implements FR16)
  - Content rating system
  - Social sharing
  - Discussion forums
  - Curated learning pathways
  - Community feed
- **Story 7.5: Personalized Intelligence** → ❌ **REMOVE ENTIRELY** (implements FR13)
  - AI-powered recommendations
  - Interest profiling
  - AWS SageMaker integration
  - Recommendation engine
- **Story 7.6: Community Feedback System** → ✅ **KEEP** (renumber to 7.4)

**Timeline Impact:** 6 weeks → **4 weeks** (2 stories removed, ~2 weeks saved)

---

### **Other Epics**

- **Epics 1-4, 6:** ✅ **NO IMPACT**

---

## 3. Architecture & Technical Impact

### **Infrastructure Components Removed:**

| Component | Current Plan | Updated Plan | Impact |
|-----------|-------------|--------------|---------|
| **Search Engine** | AWS OpenSearch | PostgreSQL Full-Text Search | Reduced complexity & cost |
| **ML Platform** | AWS SageMaker | Not needed | Significant cost savings |
| **Recommendation Service** | Lambda + ML algorithms | Not needed | Simplified architecture |
| **Vector Database** | FAISS for similarity | Not needed | Reduced infrastructure |

### **Architecture Documents Affected:**

1. **09-aiml-architecture.md** - Mark entire document as DEFERRED
2. **01-system-overview.md** - Update Attendee Experience Service description
3. **04-api-design.md** - Update search sequence diagrams
4. **index.md** - Mark AI/ML architecture as out of scope

**Benefit:** Eliminates entire AI/ML infrastructure layer, significantly reducing operational complexity and costs.

---

## 4. Artifact Update Summary

### **PRD Documents (3 files)**

**docs/prd-enhanced.md:**
- Remove FR13 definition (lines 69-70)
- Remove FR16 definition (lines 75-76)
- Update Goals section: "intelligent content management" → "searchable content archive"
- Simplify NFR2: "advanced text search" → "full-text search"
- Update Epic 5 success metrics

**docs/prd/epic-5-attendee-experience-stories.md:**
- Update Epic Overview: Remove OpenSearch references
- Simplify Story 5.1: Remove AI/ML acceptance criteria and DoD items
- Update Success Metrics: Adjust business value expectations

**docs/prd/epic-7-enhanced-features-stories.md:**
- Update Epic Overview: Remove AI/ML and community features mentions
- Remove Story 7.4 (Community Features) - entire section
- Remove Story 7.5 (Personalized Intelligence) - entire section
- Renumber Story 7.6 → Story 7.4
- Update Success Metrics and duration (6 weeks → 4 weeks)

---

### **Architecture Documents (4 files)**

**docs/architecture/09-aiml-architecture.md:**
- Add prominent deferred notice at top of document
- Keep document for future reference

**docs/architecture/01-system-overview.md:**
- Update Attendee Experience Service technology stack
- Remove OpenSearch and Lambda recommendations references

**docs/architecture/04-api-design.md:**
- Update search sequence diagram: OpenSearch → PostgreSQL Search

**docs/architecture/index.md:**
- Mark AI/ML architecture document as DEFERRED/OUT OF SCOPE

---

### **Wireframe Documents (4 files)**

**docs/wireframes/story-5.1-content-discovery.md:**
- Add SUPERSEDED notice (AI features removed)
- Keep for reference, new simplified version needed

**docs/wireframes/story-5.2-personal-dashboard.md:**
- Add notice about removing community features
- Keep personal preferences sections, remove community/social sections

**docs/wireframes/README.md:**
- Add FR13/FR16 removal notice
- Update file status indicators

**docs/prd-wireframe-alignment-analysis.md:**
- Add notice that FR13/FR16 sections are outdated

---

### **Other Documents (1 file)**

**docs/todo.md:**
- Mark FR13 and FR16 removal items as completed

---

## 5. Recommended Path Forward

**Selected Approach:** ✅ **Option 1: Direct Adjustment / Integration**

### **Justification:**

| Criteria | Assessment |
|----------|------------|
| **Feasibility** | ✅ HIGH - Clear scope reduction in planning phase |
| **Effort** | ⚠️ MODERATE - ~12 document updates, ~2 hours work |
| **Risk** | ✅ LOW - No completed work, no technical debt |
| **MVP Viability** | ✅ MAINTAINED - Core functionality intact |
| **Timeline Impact** | ✅ POSITIVE - Saves ~4 weeks total |
| **Cost Impact** | ✅ POSITIVE - Eliminates AI/ML infrastructure costs |

### **MVP Core Functionality - Preserved:**

✅ **Epic 1:** Foundation & Core Infrastructure
✅ **Epic 2:** Basic Event Creation & Publishing
✅ **Epic 3:** Core Speaker Management
✅ **Epic 4:** Event Finalization & Quality
✅ **Epic 5:** Attendee Experience (simplified search)
✅ **Epic 6:** Partner Coordination
✅ **Epic 7:** Enhanced Features (4 stories instead of 6)

### **Features Removed from MVP:**

❌ AI-powered content recommendations
❌ ML-based personalization
❌ Community ratings & social sharing
❌ Curated learning pathways
❌ Discussion forums
❌ Advanced semantic search

**These features can be reconsidered as post-MVP enhancements if business value is validated.**

---

## 6. Detailed Changes by Artifact

### **Priority 1: PRD Updates** (Critical Path)

<details>
<summary><strong>docs/prd-enhanced.md</strong> - 5 changes</summary>

**Change 1.1:** Remove FR13 (lines 69-70)
**Change 1.2:** Remove FR16 (lines 75-76)
**Change 1.3:** Update Goals (line 18): "intelligent content discovery" → "searchable content archive"
**Change 1.4:** Simplify NFR2 (lines 91-92): "advanced text search" → "full-text search"
**Change 1.5:** Update Epic 5 success metrics (line 200)

</details>

<details>
<summary><strong>docs/prd/epic-5-attendee-experience-stories.md</strong> - 3 changes</summary>

**Change 2.1:** Update Epic Overview (lines 6, 11-12): Remove OpenSearch, add PostgreSQL
**Change 2.2:** Simplify Story 5.1 (lines 19-45):
- Title: "Historical Content Discovery" → "Historical Content Search"
- Remove AC #6: "Related Content"
- Remove DoD: "ML recommendations >80% relevant"
- Update architecture to PostgreSQL full-text search

**Change 2.3:** Update Success Metrics (lines 107-125): Adjust business value expectations

</details>

<details>
<summary><strong>docs/prd/epic-7-enhanced-features-stories.md</strong> - 5 changes</summary>

**Change 3.1:** Update Epic Overview (lines 6-15): Remove AI/ML, adjust duration to 4 weeks
**Change 3.2:** Remove Story 7.4 (lines 106-132) - ENTIRE SECTION
**Change 3.3:** Remove Story 7.5 (lines 135-161) - ENTIRE SECTION
**Change 3.4:** Renumber Story 7.6 → Story 7.4 (line 164)
**Change 3.5:** Update Success Metrics (lines 193-212): Remove community/AI metrics

</details>

---

### **Priority 2: Architecture Updates** (Technical Alignment)

<details>
<summary><strong>docs/architecture/09-aiml-architecture.md</strong> - Add deferred notice</summary>

Add at top of file:
```markdown
> **⚠️ STATUS: DEFERRED - OUT OF SCOPE FOR MVP**
>
> This document describes AI/ML capabilities originally planned for FR13 and FR16,
> which have been removed from MVP scope per strategic refocus decision.
>
> **Effective Date:** 2025-10-01
```

</details>

<details>
<summary><strong>docs/architecture/01-system-overview.md</strong> - Update service descriptions</summary>

**Change 5.1:** Line 389: "OpenSearch for full-text search" → "PostgreSQL full-text search"
**Change 5.2:** Line 393: Remove "AWS OpenSearch (content search), Lambda (recommendation algorithms)"

</details>

<details>
<summary><strong>docs/architecture/04-api-design.md</strong> - Update sequence diagram</summary>

**Change 6.1:** Line 913: "OpenSearch Engine" → "PostgreSQL Search"

</details>

<details>
<summary><strong>docs/architecture/index.md</strong> - Mark AI/ML as deferred</summary>

**Change 7.1:** Lines 63-64: Add "⚠️ DEFERRED" indicator and "(OUT OF SCOPE FOR MVP)" note

</details>

---

### **Priority 3: Wireframe Updates** (Documentation Cleanup)

<details>
<summary><strong>Wireframe Documents</strong> - 4 files</summary>

**story-5.1-content-discovery.md:** Add SUPERSEDED notice
**story-5.2-personal-dashboard.md:** Add community feature removal notice
**README.md:** Update status indicators for affected wireframes
**prd-wireframe-alignment-analysis.md:** Add outdated sections notice

</details>

<details>
<summary><strong>docs/todo.md</strong> - Mark items complete</summary>

Lines 34-35: Mark FR13 and FR16 removal as completed ✅

</details>

---

## 7. Timeline Impact

### **Original MVP Timeline:**

| Epic | Duration | Weeks |
|------|----------|-------|
| Epic 1 | Foundation | 12 |
| Epic 2 | Event Creation | 8 |
| Epic 3 | Speaker Management | 10 |
| Epic 4 | Event Finalization | 8 |
| Epic 5 | Attendee Experience | **8** |
| Epic 6 | Partner Coordination | 4 |
| Epic 7 | Enhanced Features | **6** |
| **Total** | | **56 weeks** |

### **Updated MVP Timeline:**

| Epic | Duration | Weeks | Change |
|------|----------|-------|--------|
| Epic 1 | Foundation | 12 | - |
| Epic 2 | Event Creation | 8 | - |
| Epic 3 | Speaker Management | 10 | - |
| Epic 4 | Event Finalization | 8 | - |
| Epic 5 | Attendee Experience | **6** | ✅ **-2 weeks** |
| Epic 6 | Partner Coordination | 4 | - |
| Epic 7 | Enhanced Features | **4** | ✅ **-2 weeks** |
| **Total** | | **52 weeks** | ✅ **-4 weeks** |

**Timeline Benefit:** MVP delivery accelerated by approximately **1 month**.

---

## 8. Cost & Resource Impact

### **Infrastructure Cost Savings (Annual):**

| Component | Estimated Annual Cost | Status |
|-----------|----------------------|---------|
| AWS OpenSearch | $3,000 - $5,000 | ❌ Eliminated |
| AWS SageMaker | $5,000 - $10,000 | ❌ Eliminated |
| Additional Lambda | $500 - $1,000 | ❌ Eliminated |
| **Total Savings** | **$8,500 - $16,000/year** | ✅ Benefit |

### **Development Resource Savings:**

- **Epic 5:** ~2 weeks developer time saved
- **Epic 7:** ~2 weeks developer time saved
- **Infrastructure setup:** ~1 week DevOps time saved
- **ML model development:** ~2-3 weeks data science time saved

**Total:** ~7-8 weeks of development effort eliminated

---

## 9. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|---------|------------|
| **User disappointment with basic search** | Low | Low | Basic search meets core needs; can enhance later |
| **Missing community features affects engagement** | Low | Low | Core event management drives primary value |
| **Documentation inconsistencies** | Medium | Low | Comprehensive update plan addresses all artifacts |
| **Scope creep to re-add features** | Medium | Medium | Clear decision documentation and approval |
| **Future re-implementation complexity** | Low | Medium | AI/ML architecture doc preserved for reference |

**Overall Risk:** ✅ **LOW** - Well-defined scope reduction with clear benefits

---

## 10. Success Criteria

This change will be considered successful when:

✅ All 12 documents updated with proposed changes
✅ No references to FR13/FR16 remain in active MVP scope
✅ Architecture simplified to use PostgreSQL search only
✅ Epic 5 and Epic 7 timelines adjusted in project plan
✅ Team alignment achieved on simplified scope
✅ Stakeholders understand removed features can be reconsidered post-MVP

---

## 11. Next Steps & Handoff Plan

### **Immediate Actions (This Sprint):**

1. ✅ **Obtain user approval** for this Sprint Change Proposal - COMPLETED
2. ⏭️ **Execute document updates** - Apply all 12 proposed changes
3. ⏭️ **Update project tracking** - Adjust Epic 5 and Epic 7 timelines
4. ⏭️ **Team notification** - Communicate scope change to development team

### **Agent Handoff:**

**No additional agent handoff required** - This is a clean scope reduction that can be executed directly. If fundamental replanning were needed (it isn't), we would engage:
- PM Agent (for significant PRD rework)
- Architect Agent (for major architectural changes)

### **Implementation Responsibility:**

- **PO/SM Agent:** Update backlog and sprint planning to reflect new Epic 5/7 scope
- **Development Team:** Proceed with simplified Story 5.1 implementation
- **Architect:** Confirm PostgreSQL full-text search approach

---

## 12. Approval & Sign-off

**Prepared By:** John (Product Manager Agent)
**Date:** 2025-10-01
**Review Status:** ✅ **APPROVED**

**Approval Received From:**
- [x] Project Stakeholder (User) - 2025-10-01
- [ ] Technical Lead (for architecture changes) - Pending
- [ ] Development Team (for timeline adjustments) - Pending

---

## Appendix A: Complete Change Checklist

### Section 1: Change Context ✅ COMPLETED
- [x] Identified triggering decision: Strategic refocus
- [x] Defined issue: Scope reduction for MVP simplification
- [x] Assessed initial impact: Epic 5 and Epic 7 affected
- [x] Gathered evidence: Strategic decision, no technical blockers

### Section 2: Epic Impact Assessment ✅ COMPLETED
- [x] Analyzed Current Epic 5: Story 5.1 requires simplification
- [x] Analyzed Current Epic 7: Stories 7.4 and 7.5 removed
- [x] Analyzed Future Epics: No impact on Epics 1-4, 6
- [x] Summarized Epic Impact: 2 epics affected, 4 weeks saved

### Section 3: Artifact Conflict Analysis ✅ COMPLETED
- [x] Reviewed PRD: 3 files require updates
- [x] Reviewed Architecture Documents: 4 files require updates
- [x] Reviewed Wireframes: 4 files require updates
- [x] Reviewed Other Artifacts: 1 file (todo.md) requires update
- [x] Summarized Artifact Impact: 12 total files identified

### Section 4: Path Forward Evaluation ✅ COMPLETED
- [x] Option 1 (Direct Adjustment): ✅ SELECTED - High feasibility, low risk
- [x] Option 2 (Rollback): N/A - Nothing to rollback
- [x] Option 3 (MVP Re-scoping): Not needed - MVP remains viable
- [x] Selected Recommended Path: Option 1 (Direct Adjustment)

### Section 5: Sprint Change Proposal ✅ COMPLETED
- [x] Issue Summary documented
- [x] Epic Impact Summary documented
- [x] Artifact Adjustment Needs listed
- [x] Recommended Path Forward selected with rationale
- [x] PRD MVP Impact assessed (MVP remains viable)
- [x] High-Level Action Plan defined
- [x] Agent Handoff Plan specified

### Section 6: Final Review & Handoff ✅ COMPLETED
- [x] Review Checklist completed
- [x] Review Sprint Change Proposal
- [x] User Approval obtained
- [x] Confirm Next Steps and handoff

---

## Appendix B: Files Requiring Updates

**Total: 12 files**

### PRD Documents (3)
1. `docs/prd-enhanced.md`
2. `docs/prd/epic-5-attendee-experience-stories.md`
3. `docs/prd/epic-7-enhanced-features-stories.md`

### Architecture Documents (4)
4. `docs/architecture/09-aiml-architecture.md`
5. `docs/architecture/01-system-overview.md`
6. `docs/architecture/04-api-design.md`
7. `docs/architecture/index.md`

### Wireframe Documents (4)
8. `docs/wireframes/story-5.1-content-discovery.md`
9. `docs/wireframes/story-5.2-personal-dashboard.md`
10. `docs/wireframes/README.md`
11. `docs/prd-wireframe-alignment-analysis.md`

### Other Documents (1)
12. `docs/todo.md`

---

**End of Sprint Change Proposal**
