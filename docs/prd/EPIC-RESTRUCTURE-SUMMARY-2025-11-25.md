# Epic Restructure Summary - 2025-11-25

## Overview

This document summarizes the strategic repositioning of Epics 5, 6, and 8 to consolidate the complete 16-step organizer workflow into Epic 5 as a self-contained, organizer-driven implementation.

## Strategic Decision

**Before Restructure:**
- Epic 5: Partial workflow (3 stories, steps 1-2, 11)
- Epic 6: Speaker coordination (6 stories, steps 3-7)
- Epic 8: Partner coordination (2 stories, steps 9, 16)
- Missing: Steps 8, 10, 12-15

**After Restructure:**
- **Epic 5**: Complete workflow (15 stories, ALL 16 steps) - Organizer-driven
- **Epic 6**: Optional speaker self-service enhancement (5 stories)
- **Epic 8**: Optional partner analytics/voting enhancement (3 stories)

## Epic 5: Complete 16-Step Organizer Workflow

### File Modified
`docs/prd/epic-5-enhanced-organizer-workflows.md`

### Changes Made

**Duration:**
- Before: 8 weeks (Weeks 13-20)
- After: 12-15 weeks (Weeks 27-41)

**Story Count:**
- Before: 3 stories
- After: 15 stories

**Workflow Coverage:**
- Before: Steps 1-2, 11 (partial) = 2.5 of 16 steps
- After: ALL 16 steps complete

### Story Breakdown

#### Phase A: Event Setup (3 weeks)
- **Story 5.1**: Event Type Definition (Step 1)
- **Story 5.2**: Topic Selection & Speaker Brainstorming (Steps 1-3)

#### Phase B: Speaker Outreach & Coordination (4 weeks)
- **Story 5.3**: Speaker Outreach Tracking (Step 4)
- **Story 5.4**: Speaker Status Management (Step 5)
- **Story 5.5**: Speaker Content Collection (Step 6)

#### Phase C: Quality & Threshold (2.5 weeks)
- **Story 5.6**: Content Quality Review (Step 7)
- **Story 5.7**: Minimum Threshold Check (Step 8) ⚠️ *NEW*

#### Phase D: Selection & Assignment (4.5 weeks)
- **Story 5.8**: Speaker Selection & Overflow Management (Step 9) ⚠️ *NEW*
- **Story 5.9**: Speaker-to-Slot Assignment (Step 10) ⚠️ *NEW*

#### Phase E: Publishing & Finalization (3.5 weeks)
- **Story 5.10**: Progressive Publishing Engine (Step 11)
- **Story 5.11**: Agenda Finalization (Step 12) ⚠️ *NEW*

#### Phase F: Communication & Logistics (4.5 weeks)
- **Story 5.12**: Newsletter Distribution (Step 13) ⚠️ *NEW*
- **Story 5.13**: Moderation Assignment (Step 14) ⚠️ *NEW*
- **Story 5.14**: Catering Coordination (Step 15) ⚠️ *NEW*
- **Story 5.15**: Partner Meeting Coordination (Step 16) ⚠️ *NEW*

### Key Design Decisions

**Organizer-Driven Approach:**
- Organizers manually contact speakers (email/phone/in-person)
- Organizers manually update speaker status via UI
- Organizers collect and upload materials on behalf of speakers
- No dependency on speaker self-service portal

**MVP Scope for New Stories (5.12-5.15):**

**Story 5.12 - Newsletter Distribution:**
- ✅ AWS SES integration
- ✅ 3 pre-defined templates (topic, speaker lineup, final agenda)
- ✅ Pull recipients from registrations + subscribers
- ✅ Track "sent" status only
- ❌ No template editor (Phase 2)
- ❌ No open/click tracking (Phase 2)

**Story 5.13 - Moderation Assignment:**
- ✅ Simple dropdown (select from organizers)
- ✅ Email notification to moderator
- ✅ Moderator sees event dashboard
- ❌ No special briefing materials (Phase 2)
- ❌ No day-of tools (Phase 2)

**Story 5.14 - Catering Coordination:**
- ✅ Task reminder: "Contact caterer 1 month before"
- ✅ Free-text menu notes field
- ✅ Show registration count for headcount
- ✅ Mark task complete when done
- ❌ No dietary preferences collection (not needed per user)
- ❌ No menu selection UI (Phase 2)
- ❌ No caterer email integration (Phase 2)

**Story 5.15 - Partner Meeting Coordination:**
- ✅ Date/time field (lunchtime same day as event)
- ✅ Agenda template (Budget, Statistics, Topic Brainstorming)
- ✅ Meeting notes field (free text)
- ✅ Add topics to backlog from meeting
- ✅ Use Story 2.7 Partner Service
- ❌ No calendar integration (Phase 2)
- ❌ No structured budget tracking (Epic 8)

### Dependencies

**Prerequisites:**
- Epic 2 complete (CRUD APIs operational)
- Story 2.7 complete (Partner Coordination Service)

**No Longer Depends On:**
- ❌ Epic 6 (Speaker Portal) - now optional
- ❌ Epic 8 (Partner Portal) - now optional

---

## Epic 6: Speaker Self-Service Portal (Enhancement Layer)

### File Modified
`docs/prd/epic-6-speaker-portal-support.md`

### Changes Made

**Status:**
- Before: Required for Epic 5 operation
- After: Optional enhancement layer on top of Epic 5

**Duration:**
- Before: 12 weeks (Weeks 21-32)
- After: 8 weeks (Weeks 42-49, after Epic 5)

**Dependencies:**
- Before: Epic 5 depends on Epic 6
- After: Epic 6 depends on Epic 5 (reversed)

### New Positioning

**What Epic 6 Adds (Optional):**
- Automated speaker invitation emails with unique response links
- Speaker self-service response portal (Accept/Decline/Tentative)
- Speaker material self-submission portal
- Speaker dashboard (view-only, past/upcoming events)
- Automated deadline reminders

**Fallback to Epic 5:**
- If speaker doesn't use portal, organizer uses Epic 5 manual workflow
- Hybrid mode: Some speakers self-service, others organizer-driven
- Backward compatible: Epic 5 workflows continue to work

**Value Proposition:**
- ~40% reduction in organizer workload
- 2x faster speaker response time (3 days vs 7 days)
- Materials submitted 1 week faster on average

### Story Breakdown (5 stories)

1. **Story 6.1**: Automated Speaker Invitation System (2 weeks)
2. **Story 6.2**: Speaker Self-Service Response Portal (1.5 weeks)
3. **Story 6.3**: Speaker Material Self-Submission Portal (2.5 weeks)
4. **Story 6.4**: Speaker Dashboard View-Only (1.5 weeks)
5. **Story 6.5**: Automated Deadline Reminders (1.5 weeks)

### Decision Point

**Build Epic 6 If:**
- ✅ Organizers report high workload coordinating speakers
- ✅ Event frequency increasing
- ✅ Speaker feedback requests self-service option
- ✅ Development resources available

**Defer Epic 6 If:**
- ❌ Epic 5 organizer-driven workflow sufficient
- ❌ Low event frequency
- ❌ Other features higher priority

**Recommendation:** Gather data from Epic 5 operation (3-6 months) before committing to Epic 6.

---

## Epic 8: Advanced Partner Analytics & Voting (Enhancement Layer)

### File Modified
`docs/prd/epic-8-partner-coordination.md`

### Changes Made

**Status:**
- Before: Required for Epic 5 partner coordination
- After: Optional enhancement layer on top of Epic 5 Story 5.15

**Duration:**
- Before: 4 weeks (Weeks 47-50)
- After: 6 weeks (Weeks 50-55, after Epic 5)

**Dependencies:**
- Before: Epic 5 depends on Epic 8 for partner coordination
- After: Epic 8 depends on Epic 5 Story 5.15 (reversed)

### New Positioning

**What Epic 8 Adds (Optional):**
- Partner analytics dashboard (attendance, engagement, ROI)
- Sophisticated topic voting with tier-based weighting
- Automated meeting scheduling with calendar integration
- Analytics-driven meeting materials generation

**Fallback to Epic 5 Story 5.15:**
- If Epic 8 not available, Epic 5 manual meeting coordination works
- Topics can be manually added from meeting notes
- Meeting agenda uses template from Story 5.15

**Value Proposition:**
- Partners can demonstrate sponsorship ROI internally
- Strategic influence via weighted topic voting
- 50% reduction in meeting coordination time
- Automated meeting materials preparation

### Story Breakdown (3 stories)

1. **Story 8.1**: Partner Analytics Dashboard (2.5 weeks)
2. **Story 8.2**: Sophisticated Topic Voting System (2 weeks)
3. **Story 8.3**: Automated Meeting Coordination (1.5 weeks)

### Decision Point

**Build Epic 8 If:**
- ✅ Partners request ROI analytics for internal reporting
- ✅ Multiple partners want strategic influence via voting
- ✅ Meeting coordination burden high
- ✅ Development resources available

**Defer Epic 8 If:**
- ❌ Epic 5 Story 5.15 basic coordination sufficient
- ❌ Low partner engagement
- ❌ Other features higher priority

**Recommendation:** Gather data from Epic 5 Story 5.15 operation (6 months) and partner feedback before committing to Epic 8.

---

## Comparison: Before vs After

### Epic Dependencies

**Before:**
```
Epic 5 (Steps 1-2, 11) ──depends on──> Epic 6 (Steps 3-7)
Epic 5 (Steps 1-2, 11) ──depends on──> Epic 8 (Steps 9, 16)
Missing: Steps 8, 10, 12-15
```

**After:**
```
Epic 5 (ALL 16 steps) ──standalone──> Complete workflow
Epic 6 (Optional) ──enhances──> Epic 5 (Stories 5.3-5.5)
Epic 8 (Optional) ──enhances──> Epic 5 (Stories 5.2, 5.15)
```

### Timeline Impact

**Before:**
- Epic 5: Weeks 13-20 (8 weeks) - **BLOCKED** waiting for Epic 6 & 8
- Epic 6: Weeks 21-32 (12 weeks)
- Epic 8: Weeks 47-50 (4 weeks)
- **Total**: 24 weeks, sequential dependencies

**After:**
- Epic 5: Weeks 27-41 (15 weeks) - **INDEPENDENT**
- Epic 6: Weeks 42-49 (8 weeks) - **OPTIONAL**
- Epic 8: Weeks 50-55 (6 weeks) - **OPTIONAL**
- **Total**: 15 weeks required, 14 weeks optional

### Workflow Coverage

**Before:**
| Epic | Steps Covered | Status |
|------|---------------|--------|
| Epic 5 | 1-2, 11 (partial) | 2.5/16 steps |
| Epic 6 | 3-7 | 5/16 steps |
| Epic 8 | 9, 16 | 2/16 steps |
| **Missing** | **8, 10, 12-15** | **6.5/16 steps** |

**After:**
| Epic | Steps Covered | Status |
|------|---------------|--------|
| Epic 5 | 1-16 (ALL) | 16/16 steps ✅ |
| Epic 6 | Enhancement only | Optional |
| Epic 8 | Enhancement only | Optional |
| **Missing** | **None** | **0/16 steps** ✅ |

### Organizer Workflow

**Before (Distributed):**
- Step 1-2: Epic 5 (Topic selection)
- Step 3-7: Epic 6 (Speaker coordination) - **BLOCKED until Epic 6**
- Step 8: **MISSING**
- Step 9: Epic 8 (Overflow voting) - **BLOCKED until Epic 8**
- Step 10: **MISSING**
- Step 11: Epic 5 (Publishing - partial)
- Step 12-15: **MISSING**
- Step 16: Epic 8 (Partner meetings) - **BLOCKED until Epic 8**

**After (Consolidated):**
- Step 1-16: Epic 5 (ALL) - **COMPLETE, INDEPENDENT**
- Epic 6: Optional self-service automation for Steps 4-6
- Epic 8: Optional analytics/voting for Steps 2, 16

---

## Migration Impact

### For Existing Documentation

**Files Modified:**
1. `docs/prd/epic-5-enhanced-organizer-workflows.md` - Complete rewrite
2. `docs/prd/epic-6-speaker-portal-support.md` - Repositioned as enhancement
3. `docs/prd/epic-8-partner-coordination.md` - Repositioned as enhancement

**Files to Update Next:**
1. Project timeline (Epic 5 now 12-15 weeks, Epics 6 & 8 optional)
2. Epic dependency diagrams
3. Stakeholder communication materials
4. Sprint planning for Epic 5 (15 stories instead of 3)

### For Development Teams

**Immediate Changes:**
- Epic 5 is now self-contained (no dependencies on Epic 6 or 8)
- Epic 5 duration increased to 12-15 weeks
- 7 new stories added to Epic 5 (Stories 5.7-5.15, minus renumbered 5.10)

**Phasing:**
1. **Phase 1**: Implement Epic 5 completely (Weeks 27-41)
2. **Phase 2a**: Evaluate Epic 6 ROI, implement if justified (Weeks 42-49)
3. **Phase 2b**: Evaluate Epic 8 ROI, implement if justified (Weeks 50-55)

### For Product Management

**Decision Points:**
1. **After Epic 5 Complete**: Gather organizer feedback on workload
2. **3-6 months Epic 5 operation**: Evaluate Epic 6 ROI (speaker self-service)
3. **6 months Epic 5 operation**: Evaluate Epic 8 ROI (partner analytics/voting)

**Success Criteria for Epic 5:**
- ✅ All 16 workflow steps operational
- ✅ Organizers can complete end-to-end event creation
- ✅ Manual speaker coordination functional
- ✅ Partner meetings coordinated successfully

**Triggers for Epic 6:**
- Organizer feedback: "Too much manual work coordinating speakers"
- Event frequency increasing (more events = more coordination time)
- Speaker requests: "Can I submit materials myself?"

**Triggers for Epic 8:**
- Partner requests: "How do we demonstrate ROI?"
- Partners want strategic influence via topic voting
- Meeting coordination burden reported as high

---

## Rationale for Restructure

### Problem Statement

**Original Epic Structure Issues:**
1. Epic 5 blocked by Epic 6 and Epic 8 dependencies
2. 6.5 workflow steps completely missing (no stories defined)
3. Organizers couldn't complete full workflow without multiple epics
4. Speaker self-service portal not needed for MVP
5. Complex dependency chain delayed delivery

### Solution Benefits

**After Restructure:**
1. ✅ Epic 5 self-contained, no blockers
2. ✅ ALL 16 workflow steps covered
3. ✅ Organizers can complete full workflow immediately
4. ✅ Speaker/partner portals optional enhancements
5. ✅ Simpler dependency model, faster delivery

### Strategic Advantages

**Organizer-Driven Approach:**
- Proven workflow (organizers already coordinate this way manually)
- Lower technical risk (no dependency on speaker portal adoption)
- Faster time-to-value (Epic 5 delivers complete workflow)
- Flexible enhancement path (add Epic 6 & 8 if ROI justifies)

**Enhancement Layer Model:**
- Epic 6 & 8 become efficiency multipliers, not requirements
- Can evaluate ROI before investing in development
- Backward compatibility ensures Epic 5 always works
- Incremental value delivery (40% workload reduction from Epic 6, analytics from Epic 8)

---

## Next Steps

### Immediate Actions

1. **Update Project Timeline**
   - Epic 5: Weeks 27-41 (15 weeks)
   - Epic 6: Weeks 42-49 (8 weeks, optional)
   - Epic 8: Weeks 50-55 (6 weeks, optional)

2. **Create Wireframes for New Stories**
   - Story 5.12: Newsletter Distribution
   - Story 5.13: Moderation Assignment
   - Story 5.14: Catering Coordination
   - (Story 5.15 wireframe already exists: `story-6.2-partner-meetings.md`)

3. **Update Sprint Planning**
   - Break Epic 5 into 6 phases (A-F)
   - Allocate stories to sprints
   - Re-estimate effort for expanded scope

4. **Communicate Changes**
   - Stakeholder update: Epic consolidation rationale
   - Development team: New Epic 5 scope and timeline
   - Product owners: Decision points for Epic 6 & 8

### Future Decisions

**After Epic 5 Week 41 Complete:**
- [ ] Gather organizer feedback on manual workflow burden
- [ ] Measure time spent on speaker coordination per event
- [ ] Survey speakers: "Would you use self-service portal?"
- [ ] Decision: Build Epic 6? (Yes if high workload + speaker interest)

**After Epic 5 + 3-6 Months Operation:**
- [ ] Gather partner feedback on ROI demonstration needs
- [ ] Measure meeting coordination time for organizers
- [ ] Survey partners: "Do you need analytics dashboard?"
- [ ] Decision: Build Epic 8? (Yes if partner ROI needs + coordination burden)

---

## Summary

The epic restructure consolidates the complete 16-step organizer workflow into Epic 5 as a self-contained, organizer-driven implementation. Epic 6 (Speaker Portal) and Epic 8 (Partner Analytics) are repositioned as optional enhancement layers that can be evaluated for ROI after Epic 5 proves the core workflow.

**Key Outcomes:**
- ✅ Epic 5 delivers complete workflow (16/16 steps)
- ✅ No dependencies blocking Epic 5 implementation
- ✅ Epics 6 & 8 become optional efficiency enhancements
- ✅ Simpler architecture, faster delivery, lower risk

**Timeline:**
- Epic 5: 15 weeks (Weeks 27-41) - **REQUIRED**
- Epic 6: 8 weeks (Weeks 42-49) - **OPTIONAL** (evaluate after Epic 5)
- Epic 8: 6 weeks (Weeks 50-55) - **OPTIONAL** (evaluate 6 months post-Epic 5)

**Files Modified:**
- `docs/prd/epic-5-enhanced-organizer-workflows.md` (complete rewrite, 15 stories)
- `docs/prd/epic-6-speaker-portal-support.md` (repositioned as enhancement, 5 stories)
- `docs/prd/epic-8-partner-coordination.md` (repositioned as enhancement, 3 stories)

---

**Document Created:** 2025-11-25
**Author:** Strategic Planning Session
**Purpose:** Record epic restructure decisions and rationale
**Next Review:** After Epic 5 completion (Week 41)
