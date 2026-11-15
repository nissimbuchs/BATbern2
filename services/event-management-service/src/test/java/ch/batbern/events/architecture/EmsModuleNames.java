package ch.batbern.events.architecture;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

/**
 * The EMS module of a class by its simple name, as the architecture review's scripts assign it: a Java
 * port of {@code scripts/architecture/ems-module-deps.py} (ordered keyword rules and overrides on the
 * top-level class name, the package only for watch, usermerge, notification and the mail center) and
 * of the target regexes of {@code ems-module-target.py} (publishing, planning, ai, mail core, notify
 * core, ...). Keep it in step with the scripts: a change there changes what the rules here allow.
 */
final class EmsModuleNames {

    static final String EVENT_CORE = "event_core";
    static final String PLATFORM = "platform";

    private static final Map<String, BiPredicate<String, String>> RULES = new LinkedHashMap<>();

    static {
        RULES.put("watch", (p, n) -> p.startsWith("watch") || n.contains("LiveTiming") || n.contains("Watch"));
        RULES.put("usermerge", (p, n) -> p.startsWith("usermerge") || n.contains("UserMerge")
                || n.contains("MergeRequest"));
        RULES.put("notification", (p, n) -> p.startsWith("notification") || n.startsWith("Notification")
                || n.contains("InAppNotification"));
        RULES.put("tasks", (p, n) -> n.contains("Task"));
        RULES.put("analytics", (p, n) -> n.contains("Analytics") || n.contains("Attendance"));
        RULES.put("speakers", (p, n) -> containsAny(n, "Speaker", "Outreach", "Invitation", "ContentSubmission",
                "SelfNomination", "SessionProposal", "QualityReview"));
        RULES.put("registrations", (p, n) -> containsAny(n, "Registration", "Registrant", "Deregistration",
                "Participant", "Waitlist", "Badge", "Attendee", "Enroll", "Stakeholder", "DistributionList"));
        RULES.put("sessions", (p, n) -> containsAny(n, "Session", "Slot", "Timetable", "Agenda", "Qna", "QnA",
                "Timing", "Material"));
        RULES.put("topics_ai", (p, n) -> containsAny(n, "Topic", "Ai", "AI", "Prompt", "Similarity", "Cluster",
                "OpenAi"));
        RULES.put("media", (p, n) -> containsAny(n, "Photo", "Teaser", "Image"));
        RULES.put("mail", (p, n) -> p.startsWith("mailcenter") || p.startsWith("forwarding")
                || containsAny(n, "Newsletter", "Email", "Mail", "SlidesOnline", "Notice", "Venue", "Inbound",
                "Bounce", "Unsubscribe", "Subscriber", "Ses", "Conversation"));
        RULES.put(EVENT_CORE, (p, n) -> containsAny(n, "Event", "Workflow", "Publish", "Thanks", "Setting", "Admin",
                "Presentation", "Feature"));
    }

    private static final Map<String, String> OVERRIDES = Map.ofEntries(
            Map.entry("CalendarDeclineService", "mail"), Map.entry("CalendarInviteUid", "mail"),
            Map.entry("ConflictDetectionService", "sessions"), Map.entry("ConflictSeverity", "sessions"),
            Map.entry("ConflictType", "sessions"), Map.entry("SchedulingConflict", "sessions"),
            Map.entry("AssignmentStrategy", "sessions"), Map.entry("PreferenceMatchingAlgorithm", "sessions"),
            Map.entry("ContentStatusDeriver", "speakers"), Map.entry("InvalidPromotionStateException", "speakers"),
            Map.entry("ReadyRequiresPromoteException", "speakers"), Map.entry("AlreadyRespondedException", "speakers"),
            Map.entry("TransitionPayload", "speakers"), Map.entry("TransitionResult", "speakers"),
            Map.entry("StalenessScoreService", "speakers"), Map.entry("ReminderProperties", "speakers"),
            Map.entry("ConfirmationTokenService", "registrations"),
            Map.entry("OrganizerNotificationService", "notification"),
            Map.entry("DefaultOrganizerNotificationService", "notification"),
            Map.entry("CdnInvalidationService", EVENT_CORE), Map.entry("ArchivalBestEffortSteps", EVENT_CORE),
            Map.entry("GenericLogoService", "media"), Map.entry("Logo", "media"), Map.entry("LogoRepository", "media"),
            Map.entry("LogoStatus", "media"), Map.entry("TopicSessionDataService", "topics_ai"),
            Map.entry("TopicSessionDataController", "topics_ai"));

    /** The target script's reassignments, in its order: a later match wins. */
    private static final List<Map.Entry<String, List<String>>> TARGET = List.of(
            Map.entry("publishing", List.of("Publish.*", "Publishing.*", "PublicEvent.*", "PublicSession.*",
                    "CdnInvalidationService", "SpeakersPhasePublishedEvent")),
            Map.entry("planning", List.of("SlotAssignment.*", "ConflictDetection.*", "PreferenceMatching.*",
                    "SchedulingConflict", "Conflict(Severity|Type)", "AssignmentStrategy", "SessionTiming.*",
                    "SpeakerSlotPreference.*")),
            Map.entry("ai", List.of("Ai.*", "AI.*", "OpenAi.*", ".*Prompt.*")),
            Map.entry("topics_ai", List.of("StalenessScoreService")),
            Map.entry("sessions", List.of("SpeakerAssignmentNotFoundException")),
            Map.entry("registrations", List.of("AttendanceSummaryProjection")),
            Map.entry("mail_core", List.of("MailPipeline", "MailVariables?", "MailContent", "RenderedMail",
                    "Rendering", "DeliveredMail", "SkippedRecipient", "Recipients", "EmailService",
                    "EmailTemplate.*", "AlreadySentException", "UnknownPlaceholderException",
                    "ModeratorReplyAddress", "AudienceAdapter", "Compose(Context|Recipient|Request)",
                    "MailDeliveryStatusService")),
            Map.entry("notif_core", List.of("Notification", "NotificationService", "NotificationRequest",
                    "NotificationRepository", "NotificationController")));

    private EmsModuleNames() {
    }

    /**
     * The module of a top-level EMS class.
     *
     * @param firstPackage the first package segment below the EMS root ("" for a class in the root)
     * @param simpleName   the top-level class's simple name
     */
    static String moduleOf(String firstPackage, String simpleName) {
        String module = PLATFORM;
        for (Map.Entry<String, BiPredicate<String, String>> rule : RULES.entrySet()) {
            if (rule.getValue().test(firstPackage, simpleName)) {
                module = rule.getKey();
                break;
            }
        }
        module = OVERRIDES.getOrDefault(simpleName, module);
        for (Map.Entry<String, List<String>> target : TARGET) {
            if (target.getValue().stream().anyMatch(regex -> Pattern.matches(regex, simpleName))) {
                module = target.getKey();
            }
        }
        if ("mail".equals(module)) {
            return "mail_center";
        }
        return "notification".equals(module) ? "org_notify" : module;
    }

    private static boolean containsAny(String name, String... keywords) {
        for (String keyword : keywords) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
