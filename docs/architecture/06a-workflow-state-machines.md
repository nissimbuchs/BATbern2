# Workflow State Machines

This document details the workflow state management systems for the BATbern Event Management Platform, including event lifecycle, speaker coordination, task management, slot assignment, quality review, and overflow management.

## Overview

The BATbern platform implements sophisticated state machines to manage event workflows. **Key architectural insight:** The original "16-step linear workflow" was a misconception. The actual implementation uses:

1. **Event Workflow**: 8-state state machine for high-level event lifecycle
2. **Speaker Workflow**: Per-speaker state machine with parallel progression (quality review and slot assignment can happen in any order)
3. **Task System**: Configurable tasks (newsletters, catering, etc.) separate from workflow states

These state machines ensure proper transition validation, business rule enforcement, and event-driven notifications.

## Workflow Architecture Redesign (2025-12-19)

**Discovery:** During Stories 5.1-5.4 implementation, we discovered that:
- Event progresses through high-level states while speakers progress individually in parallel
- Quality review and slot assignment are independent (can happen in any order per speaker)
- Tasks like newsletters, catering, partner meetings are assignable work items, not workflow states

This led to a complete redesign from 16 stories to 8 stories, with clearer separation of concerns.

## Event Workflow State Machine (8 States)

### State Diagram

```
CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT →
AGENDA_PUBLISHED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED
```

> **Skip transition:** `CREATED` may transition directly to `SPEAKER_IDENTIFICATION` (skipping `TOPIC_SELECTION`) if a speaker is added to the pool before a topic is formally selected. This is an explicitly allowed path in the transition validator.

> **Historical note:** An `AGENDA_FINALIZED` state was removed during implementation (see inline test comment: "Direct scheduler transition (AGENDA_FINALIZED removed)"). The transition now goes directly from `AGENDA_PUBLISHED` to `EVENT_LIVE`. The 14-day-before-event guard that was attached to `AGENDA_FINALIZED` is no longer part of the workflow.

### State Definitions

| State | Description | Entry Condition | Exit Condition |
|-------|-------------|-----------------|----------------|
| **CREATED** | Event created, no topic selected | Event created | Topic selected |
| **TOPIC_SELECTION** | Topic selected, brainstorming speakers | Topic selected | Minimum speakers in pool |
| **SPEAKER_IDENTIFICATION** | Building speaker pool, outreach in progress | Min speakers in pool | All slots filled (after overflow if needed) |
| **SLOT_ASSIGNMENT** | Speakers assigned to time slots | All slots filled | Agenda published |
| **AGENDA_PUBLISHED** | Agenda public, accepting registrations | Agenda published | Event day (direct scheduler transition) |
| **EVENT_LIVE** | Event currently happening | Event day | Manual trigger after event |
| **EVENT_COMPLETED** | Event finished, post-processing | After event | Auto: daily scheduler 02:00 Bern time, 14 days after event date |
| **ARCHIVED** | Event archived | Auto-archived when event date is **more than** 14 days in the past (exclusive boundary: exactly 14 days does not qualify); or manual trigger | Terminal state |

### Post-Event Window (14-Day Rule)

After an event transitions to **EVENT_COMPLETED**, it enters a 14-day public-visibility window before being auto-archived:

- **During the window (days 0–14 after event date):**
  - `getCurrentEvent()` query: Phase 1 checks for upcoming/live events; Phase 2 falls back to the most recent EVENT_COMPLETED event within the 14-day window.
  - The homepage renders the event with an **archive-style UI**: timetable and speakers visible; no registration form, no logistics/venue block.
  - A 404 is NOT returned — the event is still surfaced to the public.

- **After 14 days:**
  - `processEventsToArchive()` runs at **02:00 Bern time** via a daily ShedLock-guarded scheduler.
  - It transitions all qualifying EVENT_COMPLETED events to ARCHIVED.
  - From this point, `getCurrentEvent()` returns 404 if no future event is active.

**Implementation note:** The scheduler lives in `event-management-service` and is guarded by ShedLock to prevent duplicate execution across ECS tasks. The archive-style homepage UI is determined in the frontend by checking `event.workflowState === 'EVENT_COMPLETED'`.

### Implementation

```java
@Component
@Slf4j
public class EventWorkflowStateMachine {

    private final EventRepository eventRepository;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final WorkflowTransitionValidator transitionValidator;
    private final DomainEventPublisher eventPublisher;
    private final EventTaskService eventTaskService;

    public Event transitionToState(String eventCode, EventWorkflowState targetState, String organizerUsername) {
        Event event = eventRepository.findByEventCode(eventCode)
            .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventCode));

        EventWorkflowState currentState = event.getWorkflowState();

        // Validate transition is allowed
        transitionValidator.validateTransition(currentState, targetState, event);

        // Apply state-specific business logic
        switch (targetState) {
            case TOPIC_SELECTION:
                // No validation needed - just topic selected
                break;

            case SPEAKER_IDENTIFICATION:
                validateMinimumSpeakersInPool(event);
                break;

            case SLOT_ASSIGNMENT:
                validateAllSlotsHaveSpeakers(event);
                break;

            case AGENDA_PUBLISHED:
                validateAllSpeakersConfirmed(event);
                break;

            case EVENT_LIVE:
                // Auto-triggered on event day
                break;

            case EVENT_COMPLETED:
                validateEventDateInPast(event);
                break;

            case ARCHIVED:
                // Manual archival
                break;
        }

        // Update state
        event.setWorkflowState(targetState);
        event.setUpdatedBy(organizerUsername);
        event.setUpdatedAt(Instant.now());

        Event savedEvent = eventRepository.save(event);

        // Publish state transition event (triggers task auto-creation)
        EventWorkflowTransitionEvent transitionEvent = new EventWorkflowTransitionEvent(
            eventCode, currentState, targetState, organizerUsername, Instant.now(), event
        );
        eventPublisher.publish(transitionEvent);

        log.info("Event {} transitioned from {} to {} by organizer {}",
                 eventCode, currentState, targetState, organizerUsername);

        return savedEvent;
    }

    /**
     * Validates minimum speakers are in speaker_pool (not necessarily accepted yet)
     */
    private void validateMinimumSpeakersInPool(Event event) {
        int requiredSpeakers = event.getSlotConfiguration().getMinSlots();
        long speakersInPool = speakerPoolRepository.countByEventId(event.getId());

        if (speakersInPool < requiredSpeakers) {
            throw new WorkflowValidationException(
                "Insufficient speakers in pool",
                Map.of("required", requiredSpeakers, "inPool", speakersInPool)
            );
        }
    }

    /**
     * Validates all slots have confirmed speakers assigned
     */
    private void validateAllSlotsHaveSpeakers(Event event) {
        int maxSlots = event.getSlotConfiguration().getMaxSlots();
        long confirmedSpeakers = speakerPoolRepository
            .countByEventIdAndStatus(event.getId(), "confirmed");

        if (confirmedSpeakers < maxSlots) {
            throw new WorkflowValidationException(
                "Minimum threshold not met",
                Map.of("maxSlots", maxSlots, "confirmed", confirmedSpeakers)
            );
        }
    }

    /**
     * Validates all speakers are confirmed (quality_reviewed AND session.startTime exists)
     */
    private void validateAllSpeakersConfirmed(Event event) {
        long acceptedSpeakers = speakerPoolRepository
            .countByEventIdAndStatus(event.getId(), "accepted");

        long confirmedSpeakers = speakerPoolRepository
            .countByEventIdAndStatus(event.getId(), "confirmed");

        if (acceptedSpeakers > confirmedSpeakers) {
            throw new WorkflowValidationException(
                "Not all accepted speakers are confirmed",
                Map.of("accepted", acceptedSpeakers, "confirmed", confirmedSpeakers)
            );
        }
    }
}
```

## Speaker Workflow Management (Per Speaker - Parallel)

### State Diagram

```
identified → contacted → ready → accepted/declined
                                    ↓ (if accepted)
                                content_submitted
                                    ↓
                                quality_reviewed
                                    ↓
                                confirmed
                    (auto-confirmed when quality_reviewed AND session.startTime exists)

overflow (backup speaker)
withdrew (speaker drops out after accepting)
```

**Note:** Slot assignment is NOT a speaker state. It's tracked by whether the session has timing assigned (`session.startTime != null`). The speaker reaches CONFIRMED when they are quality_reviewed AND their session has timing.

**Note on `SLOT_ASSIGNED` enum value:** The enum value `SLOT_ASSIGNED` exists but is rejected by the workflow service — it was an early design artefact. Attempting to transition to `SLOT_ASSIGNED` throws `IllegalStateException("Invalid state transition")`.

### State Definitions

| State | Description | Stored In | Notes |
|-------|-------------|-----------|-------|
| **identified** | Added to speaker pool | speaker_pool.status | Initial state when speaker brainstormed |
| **contacted** | Organizer recorded outreach | speaker_pool.status | Outreach attempt made |
| **ready** | Speaker ready to accept/decline | speaker_pool.status | Speaker has received invitation |
| **accepted** | Speaker accepted invitation | speaker_pool.status | Speaker committed to presenting |
| **declined** | Speaker declined invitation | speaker_pool.status | Speaker not available |
| **content_submitted** | Title/abstract submitted | speaker_pool.status | Presentation details received |
| **quality_reviewed** | Content approved by moderator | speaker_pool.status | Abstract meets quality standards |
| **confirmed** | Quality reviewed AND session timing assigned | speaker_pool.status | **Terminal state.** Auto-confirmed when quality_reviewed AND session.startTime exists. Speaker fully confirmed, ready for publishing. Any further transition throws `IllegalStateException`. |
| **overflow** | Backup speaker (no slot available) | speaker_pool.status | Accepted but no slots left |
| **withdrew** | Speaker dropped out after accepting | speaker_pool.status | Speaker cancelled commitment |

### Key Characteristics

**Parallel Workflow:**
- Quality review and slot timing assignment are **independent** and can happen in any order
- Quality review updates `speaker_pool.status = 'quality_reviewed'`
- Slot timing assignment sets `session.startTime` and `session.endTime` (NOT a speaker state)
- `confirmed` state reached when BOTH complete (order doesn't matter):
  - Speaker is `quality_reviewed` AND
  - Session has timing (`session.startTime != null`)
- Auto-confirmation is bidirectional:
  - Quality review completion → checks if session has timing → auto-confirms
  - Session timing assignment → checks if speaker is quality_reviewed → auto-confirms

**Data Model:**
- **speaker_pool**: Tracks workflow state (10 possible values: identified, contacted, ready, accepted, declined, content_submitted, quality_reviewed, confirmed, overflow, withdrew)
  - **contentStatus** field tracks content review progress. Possible values: `null` (no content submitted), `"SUBMITTED"`, `"REVISION_NEEDED"` (set when content is rejected), `"APPROVED"`.
- **sessions**: Stores presentation details AND timing (startTime, endTime, room)
- **session_users**: Junction table linking speaker (username) to session; `is_confirmed` is set to `true` on auto-confirmation
- **speaker_pool.session_id**: FK to sessions table (links speaker to their session/slot)

### Implementation

```java
@Service
@Slf4j
public class SpeakerWorkflowService {

    private final SpeakerPoolRepository speakerPoolRepository;
    private final SessionRepository sessionRepository;
    private final SessionUserRepository sessionUserRepository;
    private final WorkflowNotificationService notificationService;
    private final DomainEventPublisher eventPublisher;

    public void updateSpeakerWorkflowState(String poolId, String newState, String updatedBy) {
        SpeakerPool speaker = speakerPoolRepository.findById(poolId)
            .orElseThrow(() -> new EntityNotFoundException("Speaker not found in pool: " + poolId));

        String previousState = speaker.getStatus();

        // Validate state transition
        validateStateTransition(previousState, newState);

        // Apply state-specific logic
        switch (newState) {
            case "contacted":
                // Organizer recorded outreach
                notificationService.recordOutreach(speaker);
                break;

            case "accepted":
                // Check for overflow
                checkForOverflow(speaker.getEventId());
                break;

            case "declined":
                // Handle decline
                handleSpeakerDecline(speaker);
                break;

            case "content_submitted":
                // Content submitted, can now be reviewed
                notificationService.notifyModeratorsOfPendingReview(speaker);
                break;

            case "quality_reviewed":
                // Content approved, check if session has timing → auto-confirm
                checkAndUpdateToConfirmed(speaker);
                break;

            case "confirmed":
                // Auto-confirmed when quality_reviewed AND session.startTime exists
                updateSessionUserConfirmation(speaker, true);
                break;

            case "withdrew":
                // Speaker dropped out, promote from overflow if available
                handleSpeakerWithdrawal(speaker);
                break;

            case "overflow":
                // Speaker is backup (accepted but no slots)
                notificationService.notifySpeakerOfOverflowStatus(speaker);
                break;
        }

        // Update state
        speaker.setStatus(newState);
        speakerPoolRepository.save(speaker);

        // Publish workflow state change event
        eventPublisher.publish(new SpeakerWorkflowStateChangeEvent(
            poolId, speaker.getEventId().toString(), previousState, newState, updatedBy
        ));

        log.info("Speaker {} (pool ID: {}) moved from {} to {} by {}",
                 speaker.getSpeakerName(), poolId, previousState, newState, updatedBy);
    }

    /**
     * Checks if speaker has both quality_reviewed AND session timing assigned,
     * and auto-updates to confirmed if so.
     *
     * Parallel workflow: Either quality review or slot timing can happen first.
     * When the second one completes, speaker is auto-confirmed.
     */
    private void checkAndUpdateToConfirmed(SpeakerPool speaker) {
        boolean isQualityReviewed = "quality_reviewed".equals(speaker.getStatus());
        boolean hasSessionTiming = speaker.getSessionId() != null &&
            sessionRepository.findById(speaker.getSessionId())
                .map(session -> session.getStartTime() != null)
                .orElse(false);

        if (isQualityReviewed && hasSessionTiming) {
            // Auto-update to confirmed
            speaker.setStatus("confirmed");
            speakerPoolRepository.save(speaker);

            // Update session_users.is_confirmed
            updateSessionUserConfirmation(speaker, true);

            eventPublisher.publishEvent(new SpeakerConfirmedEvent(
                speaker.getId().toString(),
                speaker.getEventId().toString(),
                speaker.getSpeakerName()
            ));

            log.info("Speaker {} auto-updated to confirmed (quality reviewed AND session timing assigned)",
                     speaker.getSpeakerName());
        }
    }

    private boolean isContentQualityReviewed(SpeakerPool speaker) {
        // Check if content was previously quality_reviewed
        // (would be confirmed if it had been, but need to check session data)
        return speaker.getSessionId() != null; // Simplified check
    }

    private void updateSessionUserConfirmation(SpeakerPool speaker, boolean confirmed) {
        if (speaker.getSessionId() != null) {
            sessionUserRepository.updateIsConfirmed(speaker.getSessionId(), confirmed);
        }
    }

    private void checkForOverflow(UUID eventId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EntityNotFoundException("Event not found"));

        int maxSlots = event.getSlotConfiguration().getMaxSlots();
        long acceptedSpeakers = speakerPoolRepository
            .countByEventIdAndStatus(eventId, "accepted");

        if (acceptedSpeakers > maxSlots) {
            eventPublisher.publishEvent(new SpeakerOverflowDetectedEvent(
                eventId.toString(), acceptedSpeakers, maxSlots
            ));
        }
    }
}
```

## Task Management System (Story 5.5+)

### Concept

**Tasks are NOT workflow states.** They are assignable work items with due dates that organizers need to complete as part of event planning. Tasks are triggered by workflow state transitions but exist independently.

### Task Types

**Default Task Templates (7):**
1. **Venue Booking**: Triggered at TOPIC_SELECTION, due 90 days before event
2. **Partner Meeting Coordination**: Triggered at TOPIC_SELECTION, due same day as event
3. **Moderator Assignment**: Triggered at TOPIC_SELECTION, due 14 days before event
4. **Newsletter: Topic Announcement**: Triggered at TOPIC_SELECTION, due immediately
5. **Newsletter: Speaker Lineup**: Triggered at AGENDA_PUBLISHED, due 30 days before event
6. **Newsletter: Final Agenda**: Triggered at AGENDA_PUBLISHED, due 14 days before event
7. **Catering**: Triggered at AGENDA_PUBLISHED, due 30 days before event

> **Note:** Template names match exactly what is seeded by the V22 Flyway migration. Default templates are immutable — update and delete operations on them throw an `IllegalStateException` (HTTP 400).

> **Default template immutability:** Attempting to update or delete any default template (those seeded by migration) throws `IllegalStateException("Cannot modify/delete default template")`.

**Custom Tasks:**
Organizers can create custom tasks with:
- Task name (free text)
- Trigger state (any EventWorkflowState)
- Due date (immediate, relative to event, absolute)
- Assigned organizer

### Data Model

**task_templates** table:
- id, name, trigger_state, due_date_type, due_date_offset_days, is_default, created_by_username

**event_tasks** table:
- id, event_id, template_id, task_name, trigger_state, due_date, assigned_organizer_username, status (`pending`/`todo`/`in_progress`/`completed`), notes, completed_date, completed_by_username

**Two-phase task lifecycle:**
- Tasks are created with `status="pending"` at event creation time (covering all future trigger states).
- When an `EventWorkflowTransitionEvent` fires, tasks whose `trigger_state` matches the new state are activated: their status moves from `"pending"` → `"todo"`.
- This ensures tasks are always pre-created (idempotent) and only become actionable when the event reaches the relevant state.

### Auto-Creation on Workflow Transitions

```java
@Service
@Slf4j
public class EventTaskService implements ApplicationListener<EventWorkflowTransitionEvent> {

    private final TaskTemplateRepository templateRepository;
    private final EventTaskRepository eventTaskRepository;

    @Override
    @Transactional
    public void onApplicationEvent(EventWorkflowTransitionEvent event) {
        String triggeredState = event.getNewState().name().toLowerCase();

        // Find all templates that should be triggered by this state transition
        List<TaskTemplate> templates = templateRepository.findByTriggerState(triggeredState);

        for (TaskTemplate template : templates) {
            createTaskFromTemplate(event.getEventId(), template, event.getEvent().getEventDate());
        }

        log.info("Created {} tasks for event {} on transition to {}",
                 templates.size(), event.getEventId(), triggeredState);
    }

    private void createTaskFromTemplate(String eventId, TaskTemplate template, LocalDateTime eventDate) {
        LocalDateTime dueDate = calculateDueDate(template, eventDate);

        EventTask task = EventTask.builder()
            .eventId(UUID.fromString(eventId))
            .templateId(template.getId())
            .taskName(template.getName())
            .triggerState(template.getTriggerState())
            .dueDate(dueDate)
            .assignedOrganizerUsername(template.getDefaultAssignee())
            .status("pending") // Tasks start as "pending"; activated to "todo" when event reaches trigger state
            .build();

        eventTaskRepository.save(task);

        log.info("Created task '{}' for event {} with due date {}",
                 template.getName(), eventId, dueDate);
    }

    private LocalDateTime calculateDueDate(TaskTemplate template, LocalDateTime eventDate) {
        return switch (template.getDueDateType()) {
            case "immediate" -> LocalDateTime.now();
            case "relative_to_event" -> eventDate.plusDays(template.getDueDateOffsetDays());
            case "absolute" -> template.getAbsoluteDueDate();
            default -> throw new IllegalArgumentException("Unknown due date type: " + template.getDueDateType());
        };
    }

    /**
     * Mark task as complete
     */
    @Transactional
    public EventTask completeTask(String taskId, String notes, String completedBy) {
        EventTask task = eventTaskRepository.findById(UUID.fromString(taskId))
            .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));

        task.setStatus("completed");
        task.setNotes(notes);
        task.setCompletedDate(LocalDateTime.now());
        task.setCompletedByUsername(completedBy);

        return eventTaskRepository.save(task);
    }

    /**
     * Get tasks for organizer
     */
    public List<EventTask> getTasksForOrganizer(String username) {
        return eventTaskRepository.findByAssignedOrganizerUsername(username);
    }
}
```

### Task Dashboard

Organizers see tasks grouped by status:
- **TODO**: Not started, sorted by due date (overdue highlighted in red)
- **IN_PROGRESS**: Currently working on
- **COMPLETED**: Finished tasks with completion notes

**Critical tasks filter:** `getCriticalTasksForOrganizer()` returns only tasks that are overdue or due within the next 3 days. This is separate from the status-based grouping above.

**Task reassignment:** `reassignTask(taskId, newOrganizerUsername)` allows changing the assigned organizer on any open task.

**Task creation idempotency:** Calling `createTasksForEvent` twice for the same template/event pair does not create duplicate tasks. The creation guard prevents duplicates even if a workflow transition event is replayed.

## Slot Assignment Algorithm Service

```java
@Service
@Slf4j
public class SlotAssignmentService {

    private final EventSlotRepository slotRepository;
    private final SpeakerPreferencesRepository preferencesRepository;
    private final SlotAssignmentAlgorithm assignmentAlgorithm;

    @Transactional
    public List<SlotAssignment> assignSpeakersToSlots(String eventId, boolean useAutomaticAssignment) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EntityNotFoundException("Event not found"));

        List<EventSlot> availableSlots = slotRepository.findByEventIdAndAssignedSpeakerIdIsNull(eventId);
        List<SessionSpeaker> unassignedSpeakers = getUnassignedAcceptedSpeakers(event);
        List<SpeakerSlotPreferences> preferences = preferencesRepository.findByEventId(eventId);

        SlotAssignmentContext context = SlotAssignmentContext.builder()
            .event(event)
            .availableSlots(availableSlots)
            .unassignedSpeakers(unassignedSpeakers)
            .speakerPreferences(preferences)
            .useAutomaticAssignment(useAutomaticAssignment)
            .build();

        List<SlotAssignment> assignments = assignmentAlgorithm.calculateOptimalAssignments(context);

        // Apply assignments
        for (SlotAssignment assignment : assignments) {
            EventSlot slot = slotRepository.findById(assignment.getSlotId())
                .orElseThrow(() -> new EntityNotFoundException("Slot not found"));

            slot.setAssignedSpeakerId(assignment.getSpeakerId());
            slot.setAssignedAt(Instant.now());
            slotRepository.save(slot);

            // Note: No speaker state update needed
            // Slot assignment is tracked via session.startTime, not speaker state
            // Speaker auto-confirmed to CONFIRMED when quality_reviewed AND session.startTime exists
        }

        log.info("Assigned {} speakers to slots for event {}", assignments.size(), eventId);
        return assignments;
    }

    private List<SessionSpeaker> getUnassignedAcceptedSpeakers(Event event) {
        return event.getSessions().stream()
            .flatMap(session -> session.getSpeakers().stream())
            .filter(speaker -> speaker.getWorkflowState() == SpeakerWorkflowState.ACCEPTED)
            .filter(speaker -> speaker.getSlotAssignment() == null)
            .collect(Collectors.toList());
    }
}
```

## Quality Review Workflow Engine

```java
@Service
@Slf4j
public class QualityReviewService {

    private final ContentQualityReviewRepository reviewRepository;
    private final ContentValidationService contentValidator;
    private final NotificationService notificationService;

    @Transactional
    public ContentQualityReview submitContentForReview(String sessionId, String speakerId,
                                                     SubmitContentRequest request) {
        // Validate content meets basic requirements
        ContentValidationResult validation = contentValidator.validateContent(request);

        if (!validation.isValid()) {
            throw new ContentValidationException("Content validation failed", validation.getErrors());
        }

        ContentQualityReview review = ContentQualityReview.builder()
            .sessionId(sessionId)
            .speakerId(speakerId)
            .abstractReview(AbstractReview.builder()
                .content(request.getAbstract())
                .characterCount(request.getAbstract().length())
                .hasLessonsLearned(contentValidator.hasLessonsLearned(request.getAbstract()))
                .hasProductPromotion(contentValidator.hasProductPromotion(request.getAbstract()))
                .meetsStandards(validation.meetsAbstractStandards())
                .build())
            .materialReview(buildMaterialReview(request))
            .status(QualityReviewStatus.PENDING)
            .submittedAt(Instant.now())
            .build();

        ContentQualityReview savedReview = reviewRepository.save(review);

        // Notify moderator of pending review
        notificationService.notifyModeratorOfPendingReview(savedReview);

        // Update speaker workflow state to CONTENT_SUBMITTED (not yet reviewed)
        speakerWorkflowService.updateSpeakerWorkflowState(
            sessionId, speakerId, SpeakerWorkflowState.CONTENT_SUBMITTED, speakerId
        );

        return savedReview;
    }

    @Transactional
    public ContentQualityReview updateReviewStatus(String reviewId, UpdateReviewRequest request,
                                                  String moderatorId) {
        ContentQualityReview review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new EntityNotFoundException("Review not found"));

        review.setStatus(request.getStatus());
        review.setReviewedAt(Instant.now());
        review.setReviewerId(moderatorId);
        review.setFeedback(request.getFeedback());

        if (request.getStatus() == QualityReviewStatus.REQUIRES_CHANGES) {
            review.setRevisionRequested(true);
            review.setRevisionDeadline(Instant.now().plus(Duration.ofDays(7)));

            // Notify speaker of required changes
            notificationService.notifySpeakerOfRequiredChanges(review);
        } else if (request.getStatus() == QualityReviewStatus.APPROVED) {
            // Moderator approval transitions speaker to QUALITY_REVIEWED;
            // auto-confirmation to CONFIRMED happens when session timing is also assigned.
            speakerWorkflowService.updateSpeakerWorkflowState(
                review.getSessionId(),
                review.getSpeakerId(),
                SpeakerWorkflowState.QUALITY_REVIEWED,
                moderatorId
            );
        }

        return reviewRepository.save(review);
    }
}
```

### Quality Review — Constraints

**Rejection requires non-empty feedback:** Calling `rejectContent(..., null, ...)` or with a blank feedback string throws `IllegalArgumentException("Feedback is required when rejecting content")` (HTTP 400).

**`content_submissions` table:** Stores the review record. Key fields populated on rejection/approval:
- `reviewer_feedback` — the moderator's written feedback
- `reviewed_by` — moderator username
- `reviewed_at` — review timestamp
- `submission_version` — version counter for resubmissions

## Overflow Management & Voting System

```java
@Service
@Slf4j
public class OverflowManagementService {

    private final OverflowManagementRepository overflowRepository;
    private final SpeakerSelectionVoteRepository voteRepository;
    private final EventRepository eventRepository;

    @Transactional
    public SpeakerSelectionVote submitSpeakerVote(String eventId, SpeakerVoteRequest request,
                                                 String organizerId) {
        OverflowManagement overflow = overflowRepository.findByEventId(eventId)
            .orElseThrow(() -> new EntityNotFoundException("No overflow situation for event"));

        // Check if organizer already voted for this speaker
        Optional<SpeakerSelectionVote> existingVote = voteRepository
            .findByOrganizerIdAndSpeakerId(organizerId, request.getSpeakerId());

        SpeakerSelectionVote vote;
        if (existingVote.isPresent()) {
            vote = existingVote.get();
            vote.setVote(request.getVote());
            vote.setReason(request.getReason());
            vote.setVotedAt(Instant.now());
        } else {
            vote = SpeakerSelectionVote.builder()
                .organizerId(organizerId)
                .speakerId(request.getSpeakerId())
                .vote(request.getVote())
                .reason(request.getReason())
                .votedAt(Instant.now())
                .build();
        }

        SpeakerSelectionVote savedVote = voteRepository.save(vote);

        // Check if voting is complete
        checkVotingCompletion(overflow);

        return savedVote;
    }

    private void checkVotingCompletion(OverflowManagement overflow) {
        List<String> allOrganizers = getAllEventOrganizers(overflow.getEventId());
        List<String> speakersInOverflow = overflow.getOverflowSpeakers().stream()
            .map(OverflowSpeaker::getSpeakerId)
            .collect(Collectors.toList());

        boolean allVotesReceived = speakersInOverflow.stream()
            .allMatch(speakerId ->
                voteRepository.countBySpeakerId(speakerId) >= allOrganizers.size());

        if (allVotesReceived && !overflow.isVotingComplete()) {
            overflow.setVotingComplete(true);
            overflowRepository.save(overflow);

            // Calculate final selection
            selectFinalSpeakers(overflow);

            eventPublisher.publishEvent(new OverflowVotingCompleteEvent(
                overflow.getEventId(), overflow.getOverflowSpeakers()
            ));
        }
    }

    private void selectFinalSpeakers(OverflowManagement overflow) {
        Event event = eventRepository.findById(overflow.getEventId())
            .orElseThrow(() -> new EntityNotFoundException("Event not found"));

        int availableSlots = event.getSlotConfiguration().getMaxSlots();

        // Calculate vote scores and select top speakers
        List<OverflowSpeaker> selectedSpeakers = overflow.getOverflowSpeakers().stream()
            .peek(speaker -> {
                int approveVotes = voteRepository.countBySpeakerIdAndVote(
                    speaker.getSpeakerId(), VoteType.APPROVE);
                speaker.setVotes(approveVotes);
            })
            .sorted(Comparator.comparingInt(OverflowSpeaker::getVotes).reversed())
            .limit(availableSlots)
            .collect(Collectors.toList());

        // Mark selected speakers
        selectedSpeakers.forEach(speaker -> {
            speaker.setSelected(true);
            speakerWorkflowService.updateSpeakerWorkflowState(
                speaker.getSessionId(),
                speaker.getSpeakerId(),
                SpeakerWorkflowState.ACCEPTED,
                "SYSTEM"
            );
        });

        // Mark unselected speakers as overflow (remain in READY state)
        overflow.getOverflowSpeakers().stream()
            .filter(speaker -> !speaker.isSelected())
            .forEach(speaker -> {
                overflowManagementService.addToOverflow(
                    overflow.getEventId(),
                    speaker.getSpeakerId(),
                    votingResults.get(speaker.getSpeakerId())
                );
            });

        overflowRepository.save(overflow);
    }
}
```

## Archival Cleanup

When an event transitions to `ARCHIVED`, `EventArchivalCleanupService.cleanup(eventId, eventCode)` runs automatically. It performs three steps:

1. **Open task cancellation** — all open tasks for the event are bulk-cancelled. This step is mandatory; failure aborts the cleanup.
2. **Waitlist registration cancellation** — best-effort; failures are logged but do not propagate.
3. **Notification dismissal** — best-effort; failures are logged but do not propagate.

**Idempotency:** The cleanup is idempotent and safe to call multiple times. A second call for an already-archived event produces no side-effects and no exceptions.

## Related Documentation

- [Backend Architecture Overview](./06-backend-architecture.md)
- [User Lifecycle Sync Patterns](./06b-user-lifecycle-sync.md)
- [Notification System](./06d-notification-system.md)
