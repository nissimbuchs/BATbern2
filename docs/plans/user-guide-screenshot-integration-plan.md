# User Guide Screenshot Integration Plan

**Status**: In Progress 🔄
**Created**: 2026-01-09
**Last Updated**: 2026-01-09

---

## Overview

Integrate E2E test screenshots into user guide workflow documentation. Focus on **value-added screenshots** that help organizers understand the actual implemented workflow.

**Principle**: Only add screenshots that answer "How do I do this?" - skip redundant or low-value screenshots.

---

## Actual Workflow (from E2E Test Analysis)

### Phase A: Event Setup
**What it does:**
1. Navigate to Event Dashboard (authenticated)
2. Create New Event (fill form with event details, venue info)
3. Assign Tasks to Organizers (3 tasks assigned)
4. Navigate to Task List (view My Tasks / All Tasks)
5. Select Topic via Heat Map
6. Add Speaker Candidates (brainstorming - 5 candidates)

**Screenshots to add (6 key screenshots):**
- `a-01-event-dashboard.png` - How to access event creation
- `a-02-event-creation-modal.png` - Event creation form
- `a-07-edit-modal-tasks-tab-initial.png` - Task assignment interface
- `a-13-task-list-my-tasks-filter.png` - Task list view
- `a-16-topic-heatmap.png` - Topic selection heat map
- `a-19-speaker-brainstorming-form.png` - Speaker brainstorming interface

### Phase B: Speaker Outreach
**What it does:**
1. View Speaker Outreach screen (Kanban board)
2. Mark 5 speakers as "Contacted" (one by one)
3. Drag all 5 speakers to "Accepted" column

**Screenshots to add (4 key screenshots):**
- `b-01-outreach-view-ready.png` - Kanban board overview
- `b-02-before-contact-speaker-1.png` - Speaker card before contact
- `b-03-after-contact-speaker-1.png` - Speaker card after marking contacted
- `b-15-all-speakers-accepted.png` - All speakers moved to Accepted

### Phase B.5: Content Submission
**What it does:**
1. Publish Topic (from Publishing tab)
2. Submit content for 3 speakers (title + summary)

**Screenshots to add (3 key screenshots):**
- `b5-01-publish-tab-before-topic.png` - Publishing controls
- `b5-04-content-submission-drawer-1-opened.png` - Content submission form
- `b5-06-content-submitted-1.png` - Submitted content confirmation

### Phase C: Quality Review
**What it does:**
1. Publish Speakers (from Publishing tab)
2. Review & Approve 3 presentations (quality review drawer)

**Screenshots to add (3 key screenshots):**
- `c-01-publish-tab-before-speakers.png` - Publishing controls
- `c-04-quality-review-1-opened.png` - Quality review drawer
- `c-05-content-approved-1.png` - Approval confirmation

### Phase D: Slot Assignment & Publish Agenda
**What it does:**
1. Navigate to Speakers tab → Sessions view
2. Click "Manage Slot Assignments"
3. Use Auto-Assign feature to assign all sessions
4. Navigate to Publishing tab
5. Publish Agenda

**Screenshots to add (4 key screenshots):**
- `d-02-sessions-view-loaded.png` - Sessions view
- `d-03-slot-assignment-page-loaded.png` - Slot assignment interface
- `d-05-auto-assign-modal-opened.png` - Auto-assign modal
- `d-10-agenda-published.png` - Agenda published confirmation

### Phase E: Archival (currently in phase-e-publishing.md)
**What it does:**
1. Navigate to Overview tab
2. Edit event → Change status to ARCHIVED
3. Override workflow validation (expected error first)
4. Save successfully

**Screenshots to add (4 key screenshots):**
- `e-02-edit-modal-opened.png` - Event edit modal
- `e-04-status-changed-to-archived.png` - Status dropdown showing ARCHIVED
- `e-06-override-checkbox-checked.png` - Override validation checkbox
- `e-08-archived-badge-visible.png` - ARCHIVED badge on event

---

## Integration Strategy

### For Each Phase Document:

1. **Read existing document** to understand current structure
2. **Identify insertion points** for screenshots (after "Purpose", before/after "Acceptance Criteria", within steps)
3. **Add screenshots with context**:
   ```markdown
   ### Step 1: Access the Event Dashboard

   Navigate to the Events section from the main navigation.

   ![Event Dashboard](../assets/screenshots/workflow/phase-a-setup/a-01-event-dashboard.png)

   Click **Create Event** to begin.
   ```
4. **Update any outdated descriptions** to match actual implementation
5. **Keep it concise** - screenshot should speak for itself with minimal text

### Screenshot Placement Guidelines:

- **DO**: Place screenshots immediately after describing what to do
- **DO**: Use screenshots to show non-obvious UI elements (heat maps, modals, drawers)
- **DO**: Show before/after states for status changes
- **DON'T**: Add screenshots for obvious buttons or standard forms
- **DON'T**: Add every single screenshot - curate to ~3-5 per phase
- **DON'T**: Add screenshots without context text

---

## Files to Update

1. ✅ `docs/user-guide/appendix/screenshot-index.md` - COMPLETE
2. ⏳ `docs/user-guide/workflow/phase-a-setup.md` - Pending
3. ⏳ `docs/user-guide/workflow/phase-b-outreach.md` - Pending
4. ⏳ `docs/user-guide/workflow/phase-c-quality.md` - Pending
5. ⏳ `docs/user-guide/workflow/phase-d-assignment.md` - Pending
6. ⏳ `docs/user-guide/workflow/phase-e-publishing.md` - Pending (rename to archival section)

---

## Acceptance Criteria

For each updated document:
- ✅ Screenshots match actual implementation
- ✅ Only high-value screenshots included (~3-5 per phase)
- ✅ Screenshots have descriptive alt text
- ✅ Screenshot paths are correct and use phase prefix naming
- ✅ Text updated to match actual workflow (if needed)
- ✅ Markdown properly formatted
- ✅ Document flows logically from step to step

---

## Execution Order

1. Phase A: Event Setup (~6 screenshots)
2. Phase B: Speaker Outreach (~4 screenshots)
3. Phase B.5: Content Submission (~3 screenshots)
4. Phase C: Quality Review (~3 screenshots)
5. Phase D: Slot Assignment (~4 screenshots)
6. Phase E: Archival (~4 screenshots)

**Total**: ~24 high-value screenshots integrated across 5 documents

---

## Timeline

**Estimated Effort**: 2-3 hours

- Phase A: 30 minutes (most complex)
- Phase B: 20 minutes
- Phase B.5: 15 minutes
- Phase C: 15 minutes
- Phase D: 20 minutes
- Phase E: 20 minutes
- Review & testing: 30 minutes

---

## Success Metrics

- User guide clearly shows HOW to perform each workflow step
- Screenshots reduce organizer confusion and support tickets
- Documentation matches actual implementation (no outdated info)
- Easy to maintain (clear screenshot naming, documented regeneration process)

---

## Related Documentation

- [Screenshot Index](../user-guide/appendix/screenshot-index.md)
- [E2E Test Spec](../../web-frontend/e2e/workflows/documentation/complete-event-workflow.spec.ts)
- [E2E Workflow Master Plan](./E2E-WORKFLOW-MASTER-PLAN.md)
- [Original Implementation Plan](./e2e-screenshot-workflow-plan.md)
