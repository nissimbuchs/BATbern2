package ch.batbern.events.mailcenter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #1179: no mail to a whole audience goes out without an organizer pressing send.
 *
 * <p>On 2026-11-02 a dormant scheduler mailed all 267 BATbern60 registrations, cancelled and
 * waitlisted included. It had been in the code since January and woke up when #1159 fixed the
 * preference lookup that made every send on its path fail. Nobody had decided it should send.
 *
 * <p>Every method that runs without a person (scheduler, Spring event listener, SQS listener) must
 * therefore be listed here with the audience it can reach. A new one fails this test until someone
 * has looked at it. A mail to many people is not a valid reason: that goes through the Mail Center
 * composer, where an organizer sees it and sends it.
 */
@DisplayName("Every automatic trigger has a reviewed audience (#1179)")
class AutomaticTriggerAudienceGuardTest {

    private static final Path SOURCES = Path.of("src/main/java/ch/batbern/events");

    private static final Pattern TRIGGER =
            Pattern.compile("^\\s*@(Scheduled|EventListener|TransactionalEventListener|SqsListener)\\b");
    private static final Pattern METHOD =
            Pattern.compile("^\\s*(?:public|protected|private)?\\s*[\\w<>\\[\\], ?]+\\s+(\\w+)\\s*\\(");

    /** Class#method of every automatic trigger, with who it can mail. */
    private static final Map<String, String> REVIEWED = Map.ofEntries(
            Map.entry("AutoEnrollmentListener#onEventCreated", "no mail: registers organizers and partners"),
            Map.entry("BounceProcessingService#handleBounceNotification", "no mail: marks bounced addresses"),
            Map.entry("EmailForwardingListener#handleS3Notification",
                    "forwards one inbound mail to its distribution list"),
            Map.entry("EmailForwardingLogRetentionScheduler#purgeOldEntries", "no mail: deletes old log rows"),
            Map.entry("EventWorkflowScheduledService#processCompletedEvents", "no mail: workflow state"),
            Map.entry("EventWorkflowScheduledService#processEventsGoingLive", "no mail: workflow state"),
            Map.entry("EventWorkflowScheduledService#processEventsToArchive", "no mail: workflow state"),
            Map.entry("EventWorkflowTransitionListener#handleSpeakersPhasePublished", "no mail: activates tasks"),
            Map.entry("EventWorkflowTransitionListener#handleWorkflowTransition", "no mail: activates tasks"),
            Map.entry("InboundEmailListenerService#handleS3Notification",
                    "answers the one person who wrote, or tells organizers"),
            Map.entry("MailDeliveryStatusListener#onMessage", "no mail: delivery status"),
            Map.entry("MailCenterRequestFeedJob#poll",
                    "no mail: opens and closes Mail Center conversations of CUMS merge and logo requests"),
            Map.entry("MailMessageRetentionScheduler#purgeExpired", "no mail: deletes old mail records"),
            Map.entry("NewsletterEmailService#recoverOrphanedSends",
                    "finishes a newsletter an organizer already sent, after a restart"),
            Map.entry("OrganizerNotificationListener#onAnyDomainEvent", "no mail: in-app notices for organizers"),
            Map.entry("PublishingScheduledService#runDailyAutoPublish",
                    "no mail: website publishing (in-app notices for organizers)"),
            Map.entry("QnaNotificationService#flushPending", "one session's speakers and the moderators"),
            Map.entry("RegistrationCleanupService#cleanupUnconfirmedRegistrations", "no mail"),
            Map.entry("RegistrationResendService#resendUnconfirmedRegistrations",
                    "each person who has not confirmed, their own confirmation link"),
            Map.entry("SessionQnaScheduledService#freezeExpiredWindows", "no mail: closes Q&A windows"),
            Map.entry("SessionQnaWindowListener#handleSpeakersPhasePublished", "no mail: opens Q&A windows"),
            Map.entry("SessionQnaWindowListener#handleWorkflowTransition", "no mail: opens Q&A windows"),
            Map.entry("SessionTimingAssignedEventListener#handleSessionTimingAssigned", "no mail: workflow state"),
            Map.entry("SpeakerAcceptedEventListener#handleSpeakerAccepted", "no mail: workflow state"),
            Map.entry("SpeakerAddedToPoolEventListener#handleSpeakerAddedToPool", "no mail: workflow state"),
            Map.entry("SpeakerReminderScheduler#processReminders", "each speaker, about their own deadline"),
            Map.entry("TaskActivationScheduler#reconcileOpenTasks", "no mail: task state"),
            Map.entry("TaskDeadlineReminderScheduler#processReminders", "the organizer a task is assigned to"),
            Map.entry("WatchWebSocketController#handleDisconnect", "no mail: Watch session"),
            Map.entry("WatchWebSocketDisconnectListener#handleDisconnect", "no mail: Watch session"));

    /** Removed by #1179. They mailed every registration of an event without anyone deciding to. */
    private static final List<String> FORBIDDEN = List.of(
            "DeadlineReminderJob#sendDeadlineReminders",
            "NotificationService#onEventPublished");

    @Test
    @DisplayName("every automatic trigger is reviewed, and the two bulk senders are gone")
    void should_listEveryAutomaticTrigger_when_scanningSources() throws IOException {
        Map<String, String> found = scanTriggers();

        assertThat(found).as("the scan found the triggers at all").hasSizeGreaterThan(20);
        assertThat(found.keySet())
                .as("#1179: these mailed every registration without approval and must not come back")
                .doesNotContainAnyElementsOf(FORBIDDEN);
        assertThat(new TreeSet<>(found.keySet()))
                .as("A new automatic trigger: add it to REVIEWED with the audience it can mail. "
                        + "A mail to many people belongs in the Mail Center, sent by an organizer (#1179)")
                .isSubsetOf(REVIEWED.keySet());
        assertThat(new TreeSet<>(REVIEWED.keySet()))
                .as("REVIEWED lists a trigger that no longer exists; remove it")
                .isSubsetOf(found.keySet());
    }

    private static Map<String, String> scanTriggers() throws IOException {
        Map<String, String> found = new TreeMap<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String className = file.getFileName().toString().replace(".java", "");
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher trigger = TRIGGER.matcher(lines.get(i));
                    if (!trigger.find()) {
                        continue;
                    }
                    for (int j = i + 1; j < Math.min(i + 12, lines.size()); j++) {
                        String line = lines.get(j).trim();
                        if (line.startsWith("@") || line.startsWith("//") || line.startsWith("*")) {
                            continue;
                        }
                        Matcher method = METHOD.matcher(lines.get(j));
                        if (method.find()) {
                            found.put(className + "#" + method.group(1), trigger.group(1));
                            break;
                        }
                    }
                }
            }
        }
        return found;
    }
}
