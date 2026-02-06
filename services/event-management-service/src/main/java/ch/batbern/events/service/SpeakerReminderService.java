package ch.batbern.events.service;

import ch.batbern.events.config.ReminderProperties;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.OutreachHistory;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerReminderLog;
import ch.batbern.events.notification.NotificationRequest;
import ch.batbern.events.notification.NotificationService;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.OutreachHistoryRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerReminderLogRepository;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Service for processing speaker deadline reminders.
 * Story 6.5: Automated Deadline Reminders (AC2, AC3, AC5, AC6, AC7)
 *
 * Handles:
 * - Deadline detection for response and content deadlines
 * - Deduplication to prevent duplicate reminders
 * - Smart skipping (already responded, content submitted, no email, disabled)
 * - Outreach history logging
 * - Organizer escalation after Tier 3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerReminderService {

    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final SpeakerReminderLogRepository reminderLogRepository;
    private final OutreachHistoryRepository outreachHistoryRepository;
    private final SpeakerReminderEmailService reminderEmailService;
    private final MagicLinkService magicLinkService;
    private final NotificationService notificationService;
    private final ReminderProperties reminderProperties;

    private static final String REMINDER_TYPE_RESPONSE = "RESPONSE";
    private static final String REMINDER_TYPE_CONTENT = "CONTENT";
    private static final String TRIGGERED_BY_SYSTEM = "SYSTEM";

    /**
     * Process all speaker deadline reminders for today.
     * Called by the scheduler job.
     *
     * @return summary of reminders processed
     */
    @Transactional
    public ReminderProcessingResult processReminders() {
        if (!reminderProperties.isEnabled()) {
            log.info("Speaker reminders are disabled via configuration");
            return new ReminderProcessingResult(0, 0, 0);
        }

        log.info("Starting speaker deadline reminder processing");

        LocalDate today = LocalDate.now();
        int responseReminders = 0;
        int contentReminders = 0;
        int skipped = 0;

        // Get all events with future dates
        List<Event> activeEvents = eventRepository.findByDateAfter(Instant.now());

        for (Event event : activeEvents) {
            List<SpeakerPool> speakers = speakerPoolRepository.findByEventId(event.getId());

            for (SpeakerPool speaker : speakers) {
                try {
                    // Process response deadline reminders (INVITED speakers)
                    if (speaker.getStatus() == SpeakerWorkflowState.INVITED
                            && speaker.getResponseDeadline() != null) {
                        String tier = findMatchingTier(today, speaker.getResponseDeadline());
                        if (tier != null) {
                            if (shouldSendReminder(speaker, REMINDER_TYPE_RESPONSE, tier,
                                    speaker.getResponseDeadline())) {
                                sendAndLogReminder(speaker, event, REMINDER_TYPE_RESPONSE, tier,
                                        speaker.getResponseDeadline(), TRIGGERED_BY_SYSTEM);
                                responseReminders++;
                            } else {
                                skipped++;
                            }
                        }
                    }

                    // Process content deadline reminders (ACCEPTED speakers with PENDING content)
                    if (speaker.getStatus() == SpeakerWorkflowState.ACCEPTED
                            && "PENDING".equals(speaker.getContentStatus())
                            && speaker.getContentDeadline() != null) {
                        String tier = findMatchingTier(today, speaker.getContentDeadline());
                        if (tier != null) {
                            if (shouldSendReminder(speaker, REMINDER_TYPE_CONTENT, tier,
                                    speaker.getContentDeadline())) {
                                sendAndLogReminder(speaker, event, REMINDER_TYPE_CONTENT, tier,
                                        speaker.getContentDeadline(), TRIGGERED_BY_SYSTEM);
                                contentReminders++;
                            } else {
                                skipped++;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to process reminders for speaker {}: {}",
                            speaker.getId(), e.getMessage(), e);
                    skipped++;
                }
            }
        }

        log.info("Speaker reminder processing complete: {} response, {} content, {} skipped",
                responseReminders, contentReminders, skipped);

        return new ReminderProcessingResult(responseReminders, contentReminders, skipped);
    }

    /**
     * Manually send a reminder for a specific speaker (AC8).
     * Bypasses deduplication but respects reminders_disabled flag.
     *
     * @param speakerPoolId the speaker pool entry ID
     * @param reminderType RESPONSE or CONTENT
     * @param tier optional tier (auto-detected if null)
     * @param triggeredBy organizer username
     * @return result with tier used and email sent to
     */
    @Transactional
    public ManualReminderResult sendManualReminder(
            UUID speakerPoolId,
            String reminderType,
            String tier,
            String triggeredBy
    ) {
        SpeakerPool speaker = speakerPoolRepository.findById(speakerPoolId)
                .orElseThrow(() -> new IllegalArgumentException("Speaker pool entry not found: " + speakerPoolId));

        // Validate reminders not disabled
        if (Boolean.TRUE.equals(speaker.getRemindersDisabled())) {
            throw new RemindersDisabledException("Reminders are disabled for this speaker");
        }

        // Validate speaker state matches reminder type
        validateSpeakerState(speaker, reminderType);

        // Get the event
        Event event = eventRepository.findById(speaker.getEventId())
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + speaker.getEventId()));

        // Auto-detect tier if not specified
        LocalDate deadline = REMINDER_TYPE_RESPONSE.equals(reminderType)
                ? speaker.getResponseDeadline()
                : speaker.getContentDeadline();

        if (deadline == null) {
            // Fallback to event date for manual reminders without an explicit deadline
            deadline = event.getDate().atZone(ZoneId.of("Europe/Zurich")).toLocalDate();
            log.info("No {} deadline set for speaker {}, using event date {} as fallback",
                    reminderType.toLowerCase(), speakerPoolId, deadline);
        }

        String effectiveTier = tier != null ? tier : autoDetectTier(LocalDate.now(), deadline);

        // Send (bypass dedup for manual triggers)
        sendAndLogReminder(speaker, event, reminderType, effectiveTier, deadline, triggeredBy);

        return new ManualReminderResult(effectiveTier, speaker.getEmail());
    }

    /**
     * Find the matching tier for a deadline based on configured rules.
     * Returns the tier if today matches exactly the configured days-before-deadline, or null.
     */
    String findMatchingTier(LocalDate today, LocalDate deadline) {
        long daysUntilDeadline = ChronoUnit.DAYS.between(today, deadline);

        for (ReminderProperties.TierConfig tierConfig : reminderProperties.getTiers()) {
            if (daysUntilDeadline == tierConfig.getDaysBeforeDeadline()) {
                return tierConfig.getTier();
            }
        }
        return null;
    }

    /**
     * Auto-detect the appropriate tier based on days remaining.
     * Used for manual reminders when tier is not specified.
     */
    String autoDetectTier(LocalDate today, LocalDate deadline) {
        long daysUntilDeadline = ChronoUnit.DAYS.between(today, deadline);

        // Find the closest tier (round to nearest configured tier)
        String bestTier = "TIER_1"; // default
        int bestDiff = Integer.MAX_VALUE;

        for (ReminderProperties.TierConfig tierConfig : reminderProperties.getTiers()) {
            int diff = Math.abs((int) daysUntilDeadline - tierConfig.getDaysBeforeDeadline());
            if (diff < bestDiff) {
                bestDiff = diff;
                bestTier = tierConfig.getTier();
            }
        }
        return bestTier;
    }

    /**
     * Check if a reminder should be sent (deduplication + smart skipping).
     */
    boolean shouldSendReminder(SpeakerPool speaker, String reminderType, String tier, LocalDate deadline) {
        // Skip if no email
        if (speaker.getEmail() == null || speaker.getEmail().isBlank()) {
            log.debug("Skipping reminder for speaker {} - no email", speaker.getId());
            return false;
        }

        // Skip if reminders disabled
        if (Boolean.TRUE.equals(speaker.getRemindersDisabled())) {
            log.debug("Skipping reminder for speaker {} - reminders disabled", speaker.getId());
            return false;
        }

        // Dedup check: has this exact SYSTEM reminder already been sent?
        boolean alreadySent = reminderLogRepository
                .existsBySpeakerPoolIdAndReminderTypeAndTierAndDeadlineDateAndTriggeredBy(
                        speaker.getId(), reminderType, tier, deadline, TRIGGERED_BY_SYSTEM);
        if (alreadySent) {
            log.debug("Skipping reminder for speaker {} - already sent (type={}, tier={})",
                    speaker.getId(), reminderType, tier);
            return false;
        }

        return true;
    }

    /**
     * Send the reminder email and log it.
     */
    /**
     * Send the reminder email and log it.
     * DB records are persisted BEFORE sending email to ensure dedup safety:
     * if email succeeds but a later operation fails, the dedup record prevents
     * duplicate emails on the next scheduler run. If email fails, the DB record
     * remains and the organizer can retry manually via the send-reminder endpoint.
     */
    private void sendAndLogReminder(
            SpeakerPool speaker,
            Event event,
            String reminderType,
            String tier,
            LocalDate deadline,
            String triggeredBy
    ) {
        // Generate VIEW token for portal link
        String portalToken = magicLinkService.generateToken(speaker.getId(), TokenAction.VIEW);

        // Persist reminder log BEFORE sending email (dedup safety)
        SpeakerReminderLog reminderLog = SpeakerReminderLog.builder()
                .speakerPoolId(speaker.getId())
                .eventId(event.getId())
                .reminderType(reminderType)
                .tier(tier)
                .emailAddress(speaker.getEmail())
                .deadlineDate(deadline)
                .triggeredBy(triggeredBy)
                .build();
        reminderLogRepository.save(reminderLog);

        // Log in outreach history
        String contactMethod = TRIGGERED_BY_SYSTEM.equals(triggeredBy)
                ? "automated_email" : "manual_email";
        String notes = String.format("%s deadline reminder (%s) sent for event %s. Deadline: %s",
                reminderType, tier, event.getEventCode(), deadline);

        OutreachHistory outreach = new OutreachHistory();
        outreach.setSpeakerPoolId(speaker.getId());
        outreach.setContactDate(Instant.now());
        outreach.setContactMethod(contactMethod);
        outreach.setNotes(notes);
        outreach.setOrganizerUsername(
                TRIGGERED_BY_SYSTEM.equals(triggeredBy) ? "SYSTEM" : triggeredBy);
        outreachHistoryRepository.save(outreach);

        // Send email - catch failures to preserve DB records for dedup
        // TODO: Use speaker language preference when SpeakerPool gets a locale field
        try {
            reminderEmailService.sendReminderEmail(
                    speaker, event, reminderType, tier, deadline, portalToken, Locale.GERMAN);
        } catch (Exception e) {
            log.error("Failed to send reminder email for speaker {}: type={}, tier={}",
                    speaker.getId(), reminderType, tier, e);
        }

        // Escalation after Tier 3
        if ("TIER_3".equals(tier) && reminderProperties.isEscalateAfterTier3()) {
            sendOrganizerEscalation(speaker, event, reminderType);
        }
    }

    private void sendOrganizerEscalation(SpeakerPool speaker, Event event, String reminderType) {
        try {
            String typeLabel = REMINDER_TYPE_RESPONSE.equals(reminderType) ? "responded" : "submitted content";
            String message = String.format(
                    "Speaker %s has not %s despite 3 reminders for %s. Manual follow-up recommended.",
                    speaker.getSpeakerName(), typeLabel, event.getTitle());

            // Notify the assigned organizer, falling back to event creator
            String organizerUsername = speaker.getAssignedOrganizerId() != null
                    ? speaker.getAssignedOrganizerId()
                    : event.getOrganizerUsername();

            notificationService.createAndSendEmailNotification(
                    NotificationRequest.builder()
                            .recipientUsername(organizerUsername)
                            .eventCode(event.getEventCode())
                            .type("SPEAKER_ESCALATION")
                            .channel("EMAIL")
                            .priority("HIGH")
                            .subject("Speaker Follow-up Required: " + speaker.getSpeakerName())
                            .body(message)
                            .build()
            );

            log.info("Organizer escalation sent for speaker {} on event {}",
                    speaker.getSpeakerName(), event.getEventCode());

        } catch (Exception e) {
            log.warn("Failed to send organizer escalation for speaker {}: {}",
                    speaker.getId(), e.getMessage());
        }
    }

    private void validateSpeakerState(SpeakerPool speaker, String reminderType) {
        if (REMINDER_TYPE_RESPONSE.equals(reminderType)) {
            if (speaker.getStatus() != SpeakerWorkflowState.INVITED) {
                throw new InvalidSpeakerStateException(
                        "Speaker is not in INVITED state (current: " + speaker.getStatus() + ")");
            }
        } else if (REMINDER_TYPE_CONTENT.equals(reminderType)) {
            if (speaker.getStatus() != SpeakerWorkflowState.ACCEPTED) {
                throw new InvalidSpeakerStateException(
                        "Speaker is not in ACCEPTED state (current: " + speaker.getStatus() + ")");
            }
            if (!"PENDING".equals(speaker.getContentStatus())) {
                throw new InvalidSpeakerStateException(
                        "Content already submitted (status: " + speaker.getContentStatus() + ")");
            }
        } else {
            throw new IllegalArgumentException("Invalid reminder type: " + reminderType);
        }
    }

    // Result records
    public record ReminderProcessingResult(int responseReminders, int contentReminders, int skipped) {
    }

    public record ManualReminderResult(String tier, String emailAddress) {
    }

    // Exception classes
    public static class RemindersDisabledException extends RuntimeException {
        public RemindersDisabledException(String message) {
            super(message);
        }
    }

    public static class InvalidSpeakerStateException extends RuntimeException {
        public InvalidSpeakerStateException(String message) {
            super(message);
        }
    }
}
