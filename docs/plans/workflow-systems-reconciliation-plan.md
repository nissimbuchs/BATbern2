# Workflow Systems Reconciliation Plan

**Created:** 2025-12-30
**Story Reference:** Story 5.7 (BAT-11) - Slot Assignment & Progressive Publishing
**Status:** Analysis Complete, Implementation Required

---

## Executive Summary

BATbern currently has **THREE separate workflow systems** that are loosely coupled and partially inconsistent:

1. **Event Workflow** - 16 states in code, but architecture docs specify 9 states
2. **Speaker Workflow** - 11 states, partially implemented with auto-confirmation
3. **Publishing Phase** - 4 phases stored separately, only "agenda" connects to Event Workflow

This document defines the target architecture (9-step event workflow) and provides implementation tasks to reconcile these systems.

---

## 1. Event Workflow (9 States)

### Target State Machine

The architecture decision is to use a **9-step event workflow** (not 16). The 16 states in `EventWorkflowState.java` contain states that should be tasks, not workflow states.

```
┌─────────┐      ┌─────────────────┐      ┌───────────────────────┐
│ CREATED │ ───► │ TOPIC_SELECTION │ ───► │ SPEAKER_IDENTIFICATION│
└─────────┘      └─────────────────┘      └───────────────────────┘
                                                      │
                                                      ▼
┌──────────┐      ┌──────────────────┐      ┌─────────────────┐
│ ARCHIVED │ ◄─── │ EVENT_COMPLETED  │ ◄─── │ EVENT_LIVE      │
└──────────┘      └──────────────────┘      └─────────────────┘
                          ▲                         ▲
                          │                         │
┌────────────────┐      ┌──────────────────┐      ┌─────────────────┐
│AGENDA_FINALIZED│ ◄─── │ AGENDA_PUBLISHED │ ◄─── │ SLOT_ASSIGNMENT │
└────────────────┘      └──────────────────┘      └─────────────────┘
```

### State Definitions

| State | Description | Entry Trigger | Exit Trigger |
|-------|-------------|---------------|--------------|
| **CREATED** | Event created, no topic | Event creation | Topic selected |
| **TOPIC_SELECTION** | Topic selected, brainstorming speakers | Topic selection via UI | Min speakers identified |
| **SPEAKER_IDENTIFICATION** | Building speaker pool, outreach in progress | Min speakers in pool | All slots assigned with timing |
| **SLOT_ASSIGNMENT** | Assigning speakers to time slots | All speakers have timing | Agenda published |
| **AGENDA_PUBLISHED** | Agenda public, accepting registrations | Publish "agenda" phase | Manual finalization |
| **AGENDA_FINALIZED** | Agenda locked (14 days before event) | Manual finalization | Event day (automatic) |
| **EVENT_LIVE** | Event currently happening | Event date reached (cron) | Event ends (cron) |
| **EVENT_COMPLETED** | Post-event processing | Event date passed (cron) | Manual archival |
| **ARCHIVED** | Terminal state | Manual trigger | N/A |

### State Transition Trigger Table

| From State | To State | Trigger Type | Trigger Source | Implementation Status |
|------------|----------|--------------|----------------|----------------------|
| CREATED | TOPIC_SELECTION | Manual | `TopicService.selectTopicForEvent()` | ✅ Implemented |
| TOPIC_SELECTION | SPEAKER_IDENTIFICATION | Manual | `EventWorkflowController.transitionEventWorkflowState()` | ⚠️ Validation placeholder |
| SPEAKER_IDENTIFICATION | SLOT_ASSIGNMENT | Manual | `EventWorkflowController.transitionEventWorkflowState()` | ✅ Implemented |
| SLOT_ASSIGNMENT | AGENDA_PUBLISHED | Manual | `PublishingService.publishPhase("agenda")` | ✅ Implemented |
| AGENDA_PUBLISHED | AGENDA_FINALIZED | Manual | `EventWorkflowController.transitionEventWorkflowState()` | ✅ Implemented |
| AGENDA_FINALIZED | EVENT_LIVE | Automatic | **MISSING: Cron job** | ❌ Not Implemented |
| EVENT_LIVE | EVENT_COMPLETED | Automatic | **MISSING: Cron job** | ❌ Not Implemented |
| EVENT_COMPLETED | ARCHIVED | Manual | `EventWorkflowController.transitionEventWorkflowState()` | ✅ Implemented |

### Implementation Files

| Component | File Path | Lines |
|-----------|-----------|-------|
| State Enum | `shared-kernel/src/main/java/ch/batbern/shared/types/EventWorkflowState.java` | 1-125 |
| State Machine | `services/event-management-service/src/main/java/ch/batbern/events/service/EventWorkflowStateMachine.java` | 1-335 |
| Controller | `services/event-management-service/src/main/java/ch/batbern/events/controller/EventWorkflowController.java` | 1-242 |
| Transition Validator | `services/event-management-service/src/main/java/ch/batbern/events/service/WorkflowTransitionValidator.java` | Full file |
| Task Listener | `services/event-management-service/src/main/java/ch/batbern/events/listener/EventWorkflowTransitionListener.java` | Full file |

---

## 2. Speaker Workflow (11 States)

### State Machine

```
                    ┌────────────┐
                    │ IDENTIFIED │
                    └─────┬──────┘
                          │ (organizer records outreach)
                          ▼
                    ┌────────────┐
                    │ CONTACTED  │
                    └─────┬──────┘
                          │ (speaker responds)
                          ▼
                    ┌────────────┐
              ┌─────┤   READY    ├─────┐
              │     └────────────┘     │
              ▼                        ▼
        ┌──────────┐            ┌──────────┐
        │ ACCEPTED │            │ DECLINED │ (terminal)
        └────┬─────┘            └──────────┘
             │ (content submitted)
             ▼
    ┌──────────────────┐              ┌──────────┐
    │ CONTENT_SUBMITTED│              │ WITHDREW │ (can happen anytime after ACCEPTED)
    └────────┬─────────┘              └──────────┘
             │ (moderator approves)
             ▼
    ┌──────────────────┐
    │ QUALITY_REVIEWED │────────────────────────────┐
    └────────┬─────────┘                            │
             │                                      │
             │ (when BOTH conditions met:           │
             │  quality_reviewed AND                │
             │  session.startTime != null)          │
             ▼                                      │
    ┌────────────────┐        ┌──────────┐         │
    │   CONFIRMED    │        │ OVERFLOW │◄────────┘
    └────────────────┘        └──────────┘
    (when max slots exceeded, speaker goes to OVERFLOW instead of CONFIRMED)
```

### State Definitions

| State | Description | Stored In | Trigger |
|-------|-------------|-----------|---------|
| **IDENTIFIED** | Added to speaker pool | `speaker_pool.status` | Initial state |
| **CONTACTED** | Organizer recorded outreach | `speaker_pool.status` | Manual via UI |
| **READY** | Speaker responded, ready to decide | `speaker_pool.status` | Manual via UI |
| **ACCEPTED** | Speaker accepted invitation | `speaker_pool.status` | Manual via UI |
| **DECLINED** | Speaker declined (terminal) | `speaker_pool.status` | Manual via UI |
| **CONTENT_SUBMITTED** | Title/abstract submitted | `speaker_pool.status` | `SpeakerContentSubmissionService.submitContent()` |
| **QUALITY_REVIEWED** | Content approved by moderator | `speaker_pool.status` | `QualityReviewService.approveContent()` |
| **SLOT_ASSIGNED** | (Deprecated - use session.startTime) | N/A | N/A |
| **CONFIRMED** | Quality reviewed AND slot timing set | `speaker_pool.status` | Auto: see below |
| **WITHDREW** | Speaker backed out after accepting | `speaker_pool.status` | Manual via UI |
| **OVERFLOW** | Accepted but no slots available | `speaker_pool.status` | Automatic overflow detection |

### Auto-Confirmation Logic (AC17)

Speaker is auto-confirmed when BOTH conditions are met:
1. Status is `QUALITY_REVIEWED`
2. Session has `startTime` set (slot assigned)

**Implementation Status: ✅ IMPLEMENTED**

Two entry points call `checkAndUpdateToConfirmed()`:

| Entry Point | File | Method | Lines |
|-------------|------|--------|-------|
| Quality Review Approval | `QualityReviewService.java` | `approveContent()` → `checkAndUpdateToConfirmed()` | 77-99, 148-196 |
| Slot Assignment | `SessionTimingService.java` | `assignTiming()` → `checkAndAutoConfirmSpeaker()` | 41-80, 183-205 |

### State Transition Trigger Table

| From State | To State | Trigger Type | Trigger Source | Status |
|------------|----------|--------------|----------------|--------|
| IDENTIFIED | CONTACTED | Manual | `SpeakerStatusService.updateStatus()` | ✅ |
| CONTACTED | READY | Manual | `SpeakerStatusService.updateStatus()` | ✅ |
| READY | ACCEPTED | Manual | `SpeakerStatusService.updateStatus()` | ✅ |
| READY | DECLINED | Manual | `SpeakerStatusService.updateStatus()` | ✅ |
| ACCEPTED | CONTENT_SUBMITTED | Auto | `SpeakerContentSubmissionService.submitContent()` | ✅ |
| CONTENT_SUBMITTED | QUALITY_REVIEWED | Manual | `QualityReviewService.approveContent()` | ✅ |
| QUALITY_REVIEWED | CONFIRMED | Auto | `checkAndUpdateToConfirmed()` when slot assigned | ✅ |
| CONTENT_SUBMITTED | CONFIRMED | Auto | `checkAndAutoConfirmSpeaker()` when quality reviewed | ✅ |
| ACCEPTED+ | WITHDREW | Manual | `SpeakerStatusService.updateStatus()` | ✅ |
| ACCEPTED | OVERFLOW | Auto | When `acceptedCount > maxSlots` | ⚠️ Detection only, no auto-transition |

### Implementation Files

| Component | File Path |
|-----------|-----------|
| State Enum | `shared-kernel/src/main/java/ch/batbern/shared/types/SpeakerWorkflowState.java` |
| Status Service | `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerStatusService.java` |
| Transition Validator | `services/event-management-service/src/main/java/ch/batbern/events/validator/StatusTransitionValidator.java` |
| Quality Review | `services/event-management-service/src/main/java/ch/batbern/events/service/QualityReviewService.java` |
| Slot Assignment | `services/event-management-service/src/main/java/ch/batbern/events/service/slotassignment/SessionTimingService.java` |
| Controller | `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerStatusController.java` |

---

## 3. Publishing Phase Workflow (4 Phases)

### State Machine

```
┌────────┐      ┌─────────┐      ┌───────────┐      ┌────────┐
│  none  │ ───► │  topic  │ ───► │  speakers │ ───► │ agenda │
└────────┘      └─────────┘      └───────────┘      └────────┘
                                                         │
                                                         │ (triggers)
                                                         ▼
                                              EventWorkflowState.AGENDA_PUBLISHED
```

### Phase Definitions

| Phase | What's Published | Event Workflow Impact | Storage |
|-------|-----------------|----------------------|---------|
| **none** | Nothing public | None | `event.currentPublishedPhase = null` |
| **topic** | Event topic only | None | `event.currentPublishedPhase = "topic"` |
| **speakers** | Topic + speaker names | None | `event.currentPublishedPhase = "speakers"` |
| **agenda** | Topic + speakers + schedule | **Triggers AGENDA_PUBLISHED** | `event.currentPublishedPhase = "agenda"` |

### Publishing Trigger Table

| Phase | Trigger | Controller | Service Method | Event Workflow Change |
|-------|---------|------------|----------------|----------------------|
| topic | Manual | `PublishingEngineController` | `publishPhase("topic")` | ❌ None |
| speakers | Manual | `PublishingEngineController` | `publishPhase("speakers")` | ❌ None |
| agenda | Manual | `PublishingEngineController` | `publishPhase("agenda")` | ✅ → AGENDA_PUBLISHED |

### Implementation Files

| Component | File Path | Lines |
|-----------|-----------|-------|
| Entity Field | `services/event-management-service/src/main/java/ch/batbern/events/domain/Event.java` | 132-133 |
| Service | `services/event-management-service/src/main/java/ch/batbern/events/service/publishing/PublishingService.java` | 70-106 |
| Controller | `services/event-management-service/src/main/java/ch/batbern/events/controller/PublishingEngineController.java` | Full file |
| Frontend Tab | `web-frontend/src/components/organizer/EventPage/EventPublishingTab.tsx` | Full file |
| Frontend Types | `web-frontend/src/types/event.types.ts` | 444 |

---

## 4. Implementation Gaps and Required Tasks

### GAP-1: Event Workflow Enum Has 16 States, Should Have 9

**Problem:** `EventWorkflowState.java` defines 16 states, but the architecture specifies 9 states. The extra 7 states should be tasks, not workflow states.

**States to Remove from Enum:**
- `SPEAKER_BRAINSTORMING` → Part of SPEAKER_IDENTIFICATION
- `SPEAKER_OUTREACH` → Part of SPEAKER_IDENTIFICATION
- `SPEAKER_CONFIRMATION` → Part of SPEAKER_IDENTIFICATION
- `CONTENT_COLLECTION` → Part of SPEAKER_IDENTIFICATION
- `QUALITY_REVIEW` → Part of SPEAKER_IDENTIFICATION
- `THRESHOLD_CHECK` → Part of SPEAKER_IDENTIFICATION
- `OVERFLOW_MANAGEMENT` → Part of SPEAKER_IDENTIFICATION
- `NEWSLETTER_SENT` → Should be a task
- `EVENT_READY` → Should be a task
- `PARTNER_MEETING_COMPLETE` → Should be a task

**Tasks:**
- [ ] **TASK-1.1:** Update `EventWorkflowState.java` to have only 9 states
- [ ] **TASK-1.2:** Add Flyway migration to convert old states to new states in database
- [ ] **TASK-1.3:** Update `EventWorkflowController.getNextAvailableStates()` for 9-step workflow
- [ ] **TASK-1.4:** Update `WorkflowTransitionValidator` transition matrix
- [ ] **TASK-1.5:** Update frontend workflow state display
- [ ] **TASK-1.6:** Create default task templates for removed states (newsletter, partner meeting)

---

### GAP-2: Missing Cron Jobs for Automatic Transitions

**Problem:** `EVENT_LIVE` and `EVENT_COMPLETED` should be triggered automatically based on event date.

**Required Automatic Transitions:**
| Transition | Trigger Condition | Cron Schedule |
|------------|-------------------|---------------|
| AGENDA_FINALIZED → EVENT_LIVE | `event.date == today` | Daily at 00:01 |
| EVENT_LIVE → EVENT_COMPLETED | `event.date < today` | Daily at 23:59 |

**Tasks:**
- [ ] **TASK-2.1:** Create `EventWorkflowScheduledService` with `@Scheduled` methods
- [ ] **TASK-2.2:** Implement `processEventsGoingLive()` for EVENT_LIVE transition
- [ ] **TASK-2.3:** Implement `processCompletedEvents()` for EVENT_COMPLETED transition
- [ ] **TASK-2.4:** Add integration tests for scheduled transitions
- [ ] **TASK-2.5:** Configure cron expressions in `application.yml`

---

### GAP-3: AGENDA_FINALIZED Never Triggered

**Problem:** There is no button or service call that triggers `AGENDA_FINALIZED` state.

**Required:**
- Add "Finalize Agenda" button to Publishing tab
- This should be available after agenda is published and at least 14 days before event

**Tasks:**
- [ ] **TASK-3.1:** Add "Finalize Agenda" button to `EventPublishingTab.tsx`
- [ ] **TASK-3.2:** Create `finalizeAgenda()` method in `PublishingService`
- [ ] **TASK-3.3:** Add validation: must be ≥14 days before event
- [ ] **TASK-3.4:** After finalization, prevent further publishing changes

---

### GAP-4: Publishing Tab Not Connected to Backend State

**Problem:** `EventPublishingTab.tsx` uses hardcoded mock data (line 27, 53) instead of reading from `event.currentPublishedPhase`.

**Tasks:**
- [ ] **TASK-4.1:** Read `currentPublishedPhase` from event API response
- [ ] **TASK-4.2:** Replace mock data with real validation from backend
- [ ] **TASK-4.3:** Wire up publishing buttons to `PublishingEngineController`

---

### GAP-5: Placeholder Validation Methods

**Problem:** Some validation methods in `EventWorkflowStateMachine.java` are placeholders that always throw.

**Placeholder Methods (lines 187-214):**
- `validateMinimumSpeakersIdentified()` - Always throws
- `validateAllContentSubmitted()` - Always throws

**Tasks:**
- [ ] **TASK-5.1:** Implement `validateMinimumSpeakersIdentified()` to check speaker pool count
- [ ] **TASK-5.2:** Implement `validateAllContentSubmitted()` to check content submission status

---

### GAP-6: Overflow Auto-Transition Not Implemented

**Problem:** When `acceptedCount > maxSlots`, overflow is detected but speakers are not automatically moved to OVERFLOW state.

**Current:** `StatusSummaryResponse.overflowDetected` is calculated but not acted upon.

**Tasks:**
- [ ] **TASK-6.1:** Create `OverflowDetectionService` that monitors acceptance counts
- [ ] **TASK-6.2:** Auto-transition latest accepted speakers to OVERFLOW when threshold exceeded
- [ ] **TASK-6.3:** Send notification to organizers when overflow detected

---

## 5. Recommended Implementation Order

### Phase 1: Clean Up Event Workflow Enum (High Priority)
1. TASK-1.1, TASK-1.2, TASK-1.3, TASK-1.4

### Phase 2: Add Automatic Transitions (High Priority)
2. TASK-2.1, TASK-2.2, TASK-2.3, TASK-2.4, TASK-2.5

### Phase 3: Fix Publishing Integration (Medium Priority)
3. TASK-3.1, TASK-3.2, TASK-3.3, TASK-3.4
4. TASK-4.1, TASK-4.2, TASK-4.3

### Phase 4: Implement Missing Validations (Medium Priority)
5. TASK-5.1, TASK-5.2

### Phase 5: Overflow Automation (Low Priority)
6. TASK-6.1, TASK-6.2, TASK-6.3

### Phase 6: Frontend Updates (Low Priority)
7. TASK-1.5, TASK-1.6

---

## 6. Architecture Document Enhancement Required

Update `docs/architecture/06a-workflow-state-machines.md` with:
1. The three state diagrams in this document
2. The trigger tables for each workflow
3. Cross-references between systems
4. Remove references to 16-step workflow

---

## Appendix A: Current Code References

### Event Workflow State Enum (Current - 16 States)

```java
// shared-kernel/src/main/java/ch/batbern/shared/types/EventWorkflowState.java
public enum EventWorkflowState {
    CREATED,
    TOPIC_SELECTION,
    SPEAKER_BRAINSTORMING,      // ❌ Remove - part of SPEAKER_IDENTIFICATION
    SPEAKER_OUTREACH,           // ❌ Remove - part of SPEAKER_IDENTIFICATION
    SPEAKER_CONFIRMATION,       // ❌ Remove - part of SPEAKER_IDENTIFICATION
    CONTENT_COLLECTION,         // ❌ Remove - part of SPEAKER_IDENTIFICATION
    QUALITY_REVIEW,             // ❌ Remove - part of SPEAKER_IDENTIFICATION
    THRESHOLD_CHECK,            // ❌ Remove - part of SPEAKER_IDENTIFICATION
    OVERFLOW_MANAGEMENT,        // ❌ Remove - part of SPEAKER_IDENTIFICATION
    SLOT_ASSIGNMENT,
    AGENDA_PUBLISHED,
    AGENDA_FINALIZED,
    NEWSLETTER_SENT,            // ❌ Remove - should be a task
    EVENT_READY,                // ❌ Remove - should be a task
    PARTNER_MEETING_COMPLETE,   // ❌ Remove - should be a task
    ARCHIVED
}
```

### Speaker Workflow State Enum (Current - 11 States)

```java
// shared-kernel/src/main/java/ch/batbern/shared/types/SpeakerWorkflowState.java
public enum SpeakerWorkflowState {
    IDENTIFIED,
    CONTACTED,
    READY,
    ACCEPTED,
    DECLINED,
    CONTENT_SUBMITTED,
    QUALITY_REVIEWED,
    SLOT_ASSIGNED,              // ⚠️ Deprecated - use session.startTime instead
    CONFIRMED,
    WITHDREW,
    OVERFLOW
}
```

### Publishing Phase (Current - String)

```java
// services/event-management-service/src/main/java/ch/batbern/events/domain/Event.java:132-133
@Column(name = "current_published_phase", length = 50)
private String currentPublishedPhase; // none, topic, speakers, agenda
```

```typescript
// web-frontend/src/types/event.types.ts:444
export type PublishingPhase = 'topic' | 'speakers' | 'agenda';
```
