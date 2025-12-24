# Workflow State Machines

This document details the workflow state management systems for the BATbern Event Management Platform, including event lifecycle, speaker coordination, task management, slot assignment, quality review, and overflow management.

## Overview

The BATbern platform implements sophisticated state machines to manage event workflows. **Key architectural insight:** The original "16-step linear workflow" was a misconception. The actual implementation uses:

1. **Event Workflow**: 9-state state machine for high-level event lifecycle
2. **Speaker Workflow**: Per-speaker state machine with parallel progression (quality review and slot assignment can happen in any order)
3. **Task System**: Configurable tasks (newsletters, catering, etc.) separate from workflow states

These state machines ensure proper transition validation, business rule enforcement, and event-driven notifications.

## Workflow Architecture Redesign (2025-12-19)

**Discovery:** During Stories 5.1-5.4 implementation, we discovered that:
- Event progresses through high-level states while speakers progress individually in parallel
- Quality review and slot assignment are independent (can happen in any order per speaker)
- Tasks like newsletters, catering, partner meetings are assignable work items, not workflow states

This led to a complete redesign from 16 stories to 8 stories, with clearer separation of concerns.

## Event Workflow State Machine (9 States)

### State Diagram

```
CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT →
AGENDA_PUBLISHED → AGENDA_FINALIZED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED
```

### State Definitions

| State | Description | Entry Condition | Exit Condition |
|-------|-------------|-----------------|----------------|
| **CREATED** | Event created, no topic selected | Event created | Topic selected |
| **TOPIC_SELECTION** | Topic selected, brainstorming speakers | Topic selected | Minimum speakers in pool |
| **SPEAKER_IDENTIFICATION** | Building speaker pool, outreach in progress | Min speakers in pool | All slots filled (after overflow if needed) |
| **SLOT_ASSIGNMENT** | Speakers assigned to time slots | All slots filled | Agenda published |
| **AGENDA_PUBLISHED** | Agenda public, accepting registrations | Agenda published | Manually finalized (2 weeks before) |
| **AGENDA_FINALIZED** | Agenda locked for printing | Manually finalized | Event day |
| **EVENT_LIVE** | Event currently happening | Event day | Manual trigger after event |
| **EVENT_COMPLETED** | Event finished, post-processing | After event | Manual trigger |
| **ARCHIVED** | Event archived | Archival trigger | Terminal state |

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

    public Event transitionToState(String eventId, EventWorkflowState targetState, String organizerId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));

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

            case AGENDA_FINALIZED:
                validateAgendaPublished(event);
                validateEventDateInFuture(event, 14); // Must be at least 14 days before event
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
        event.setLastUpdatedBy(organizerId);
        event.setUpdatedAt(Instant.now());

        Event savedEvent = eventRepository.save(event);

        // Publish state transition event (triggers task auto-creation)
        EventWorkflowTransitionEvent transitionEvent = new EventWorkflowTransitionEvent(
            eventId, currentState, targetState, organizerId, Instant.now(), event
        );
        eventPublisher.publishEvent(transitionEvent);

        log.info("Event {} transitioned from {} to {} by organizer {}",
                 eventId, currentState, targetState, organizerId);

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
                "Not all slots have confirmed speakers",
                Map.of("maxSlots", maxSlots, "confirmed", confirmedSpeakers)
            );
        }
    }

    /**
     * Validates all speakers are confirmed (quality_reviewed AND slot_assigned)
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

## Speaker Workflow Management (Per Speaker - Linear)

### State Diagram

```
identified → contacted → ready → accepted/declined
                                    ↓ (if accepted)
                            content_submitted
                                    ↓
                            quality_reviewed
                                    ↓
                               confirmed

Alternative states:
- overflow (backup speaker)
- withdrew (speaker drops out after accepting)

Slot assignment (orthogonal action):
- Sets sessions.start_time (NOT a workflow state)
- Can happen at any point after ACCEPTED
- Auto-confirmation triggers when: quality_reviewed + slot assigned
```

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
| **confirmed** | Quality reviewed AND slot assigned | speaker_pool.status | Speaker fully confirmed, ready for publishing |
| **overflow** | Backup speaker (no slot available) | speaker_pool.status | Accepted but no slots left |
| **withdrew** | Speaker dropped out after accepting | speaker_pool.status | Speaker cancelled commitment |

**Note:** `slot_assigned` is NOT a state - it's an orthogonal action that sets `sessions.start_time`.

### Key Characteristics

**Linear Workflow with Orthogonal Slot Assignment:**
- **Linear states**: IDENTIFIED → CONTACTED → READY → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED → CONFIRMED
- **Slot assignment is orthogonal**: Sets `sessions.start_time`, doesn't change workflow state
- **Three allowed flows**:
  1. Quality first: ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED → *[assign slot]* → CONFIRMED
  2. Slot first: ACCEPTED → *[assign slot]* → CONTENT_SUBMITTED → QUALITY_REVIEWED → CONFIRMED
  3. Slot during: ACCEPTED → CONTENT_SUBMITTED → *[assign slot]* → QUALITY_REVIEWED → CONFIRMED
- **Auto-confirmation**: When reaching QUALITY_REVIEWED state, if `sessions.start_time != null` → auto-confirm

**Data Model:**
- **speaker_pool**: Tracks workflow state (10 possible states - no SLOT_ASSIGNED state)
- **sessions**: Stores presentation title/abstract and start_time (slot assignment)
- **session_users**: Junction table linking speaker (username) to session

### Implementation

```java
@Service
@Slf4j
public class SpeakerWorkflowService {

    private final SpeakerPoolRepository speakerPoolRepository;
    private final SessionRepository sessionRepository;

    public void updateSpeakerWorkflowState(UUID speakerId, SpeakerWorkflowState newState, String organizerUsername) {
        SpeakerPool speaker = speakerPoolRepository.findById(speakerId)
            .orElseThrow(() -> new IllegalArgumentException("Speaker not found: " + speakerId));

        SpeakerWorkflowState currentState = speaker.getStatus();

        // Validate linear state transition
        if (!isValidTransition(currentState, newState)) {
            throw new IllegalStateException(
                String.format("Invalid state transition: %s → %s", currentState, newState)
            );
        }

        LOG.info("Transitioning speaker {} from {} to {} by organizer {}",
                speakerId, currentState, newState, organizerUsername);

        // Update state
        speaker.setStatus(newState);
        speakerPoolRepository.save(speaker);

        // Check for auto-confirmation when quality review completes
        if (newState == SpeakerWorkflowState.QUALITY_REVIEWED) {
            checkAndUpdateToConfirmed(speaker, organizerUsername);
        }

        // TODO: Publish SpeakerWorkflowStateChangeEvent to EventBridge
    }

    /**
     * Check if speaker has slot assigned and auto-update to CONFIRMED if so.
     *
     * Simple linear workflow:
     * - When speaker reaches QUALITY_REVIEWED state
     * - AND they have a time slot assigned (session.startTime != null)
     * - THEN auto-confirm them
     */
    private void checkAndUpdateToConfirmed(SpeakerPool speaker, String organizerUsername) {
        boolean hasSlotAssigned = hasTimeSlotAssigned(speaker);

        if (hasSlotAssigned) {
            LOG.info("Auto-confirming speaker {} - quality review complete and slot assigned",
                    speaker.getId());

            speaker.setStatus(SpeakerWorkflowState.CONFIRMED);
            speakerPoolRepository.save(speaker);

            LOG.info("Speaker {} auto-confirmed by system (triggered by organizer {})",
                    speaker.getId(), organizerUsername);

            // TODO: Publish SpeakerConfirmedEvent
        } else {
            LOG.debug("Speaker {} quality reviewed but no slot assigned yet - staying at QUALITY_REVIEWED",
                    speaker.getId());
        }
    }

    /**
     * Check if speaker has been assigned a time slot.
     * A slot is assigned if the speaker's session has a start_time set.
     */
    private boolean hasTimeSlotAssigned(SpeakerPool speaker) {
        if (speaker.getSessionId() == null) {
            return false;
        }

        return sessionRepository.findById(speaker.getSessionId())
                .map(session -> session.getStartTime() != null)
                .orElse(false);
    }

    /**
     * Validate linear state transitions.
     *
     * Allowed transitions:
     * - IDENTIFIED → CONTACTED, DECLINED
     * - CONTACTED → READY, DECLINED
     * - READY → ACCEPTED, DECLINED
     * - ACCEPTED → CONTENT_SUBMITTED, DECLINED, WITHDREW, OVERFLOW
     * - CONTENT_SUBMITTED → QUALITY_REVIEWED, DECLINED, WITHDREW
     * - QUALITY_REVIEWED → CONFIRMED, DECLINED, WITHDREW
     * - WITHDREW → ACCEPTED (re-acceptance)
     * - OVERFLOW → ACCEPTED (slot opened)
     * - DECLINED, CONFIRMED are terminal states
     */
    private boolean isValidTransition(SpeakerWorkflowState current, SpeakerWorkflowState next) {
        if (current == next) return true; // Idempotent

        return switch (current) {
            case IDENTIFIED -> next == SpeakerWorkflowState.CONTACTED || next == SpeakerWorkflowState.DECLINED;
            case CONTACTED -> next == SpeakerWorkflowState.READY || next == SpeakerWorkflowState.DECLINED;
            case READY -> next == SpeakerWorkflowState.ACCEPTED || next == SpeakerWorkflowState.DECLINED;
            case ACCEPTED -> next == SpeakerWorkflowState.CONTENT_SUBMITTED
                || next == SpeakerWorkflowState.DECLINED
                || next == SpeakerWorkflowState.WITHDREW
                || next == SpeakerWorkflowState.OVERFLOW;
            case CONTENT_SUBMITTED -> next == SpeakerWorkflowState.QUALITY_REVIEWED
                || next == SpeakerWorkflowState.DECLINED
                || next == SpeakerWorkflowState.WITHDREW;
            case QUALITY_REVIEWED -> next == SpeakerWorkflowState.CONFIRMED
                || next == SpeakerWorkflowState.DECLINED
                || next == SpeakerWorkflowState.WITHDREW;
            case WITHDREW -> next == SpeakerWorkflowState.ACCEPTED; // Re-acceptance
            case OVERFLOW -> next == SpeakerWorkflowState.ACCEPTED; // Slot opened
            case SLOT_ASSIGNED -> false; // Not used in linear model
            case DECLINED, CONFIRMED -> false; // Terminal states
            default -> false;
        };
    }
}
```

### Slot Assignment Service (Orthogonal Action)

Slot assignment is handled separately and does NOT change speaker workflow state:

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class SlotAssignmentService {

    private final SessionRepository sessionRepository;
    private final SpeakerPoolRepository speakerPoolRepository;

    /**
     * Assign a speaker to a specific time slot.
     *
     * This is an orthogonal action that:
     * - Links speaker to session (sets speaker.sessionId)
     * - Does NOT change speaker workflow state
     * - Auto-confirmation triggers when speaker reaches QUALITY_REVIEWED
     */
    public void assignSpeakerToSlot(String eventCode, UUID speakerId, UUID sessionId, String organizer) {
        // Validate session has time slot
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new NotFoundException("Session not found"));

        if (session.getStartTime() == null) {
            throw new IllegalStateException("Session has no time slot (start_time is null)");
        }

        // Assign speaker to session (does NOT change workflow state)
        SpeakerPool speaker = speakerPoolRepository.findById(speakerId)
            .orElseThrow(() -> new NotFoundException("Speaker not found"));

        speaker.setSessionId(sessionId);
        speakerPoolRepository.save(speaker);

        LOG.info("Assigned speaker {} to session {} (slot: {}) - speaker remains in state {}",
                speakerId, sessionId, session.getStartTime(), speaker.getStatus());

        // Note: If speaker is already QUALITY_REVIEWED, organizer should call
        // speakerWorkflowService.updateSpeakerWorkflowState() to trigger auto-confirmation
    }
}
```

## Task Management System (Story 5.5+)

### Concept

**Tasks are NOT workflow states.** They are assignable work items with due dates that organizers need to complete as part of event planning. Tasks are triggered by workflow state transitions but exist independently.

### Task Types

**Default Task Templates (7):**
1. **Venue Booking**: Triggered at TOPIC_SELECTION, due 90 days before event
2. **Partner Meeting**: Triggered at TOPIC_SELECTION, due same day as event
3. **Moderator Assignment**: Triggered at TOPIC_SELECTION, due 14 days before event
4. **Newsletter: Topic**: Triggered at TOPIC_SELECTION, due immediately
5. **Newsletter: Speakers**: Triggered at AGENDA_PUBLISHED, due 30 days before event
6. **Newsletter: Final**: Triggered at AGENDA_FINALIZED, due 14 days before event
7. **Catering**: Triggered at AGENDA_FINALIZED, due 30 days before event

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
- id, event_id, template_id, task_name, trigger_state, due_date, assigned_organizer_username, status (todo/in_progress/completed), notes, completed_date, completed_by_username

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
            .status("todo")
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

            // Update speaker workflow state
            speakerWorkflowService.updateSpeakerWorkflowState(
                assignment.getSessionId(),
                assignment.getSpeakerId(),
                SpeakerWorkflowState.SLOT_ASSIGNED,
                assignment.getAssignedBy()
            );
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

        // Update speaker workflow state
        speakerWorkflowService.updateSpeakerWorkflowState(
            sessionId, speakerId, SpeakerWorkflowState.QUALITY_REVIEWED, speakerId
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
            // Move speaker to final agenda state
            speakerWorkflowService.updateSpeakerWorkflowState(
                review.getSessionId(),
                review.getSpeakerId(),
                SpeakerWorkflowState.FINAL_AGENDA,
                moderatorId
            );
        }

        return reviewRepository.save(review);
    }
}
```

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

## Related Documentation

- [Backend Architecture Overview](./06-backend-architecture.md)
- [User Lifecycle Sync Patterns](./06b-user-lifecycle-sync.md)
- [Notification System](./06d-notification-system.md)
