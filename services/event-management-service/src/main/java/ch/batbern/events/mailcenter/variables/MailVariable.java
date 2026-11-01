package ch.batbern.events.mailcenter.variables;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one catalog of mail variables (Story 16.11 AC1). Every {@code {{name}}} a BATbern mail may
 * contain is defined here, once: its canonical name, the scope that provides its value, whether it
 * carries a credential, and the old names it also answers to (AC3: templates keep working).
 *
 * <p>A mail gets every {@link Scope#SYSTEM} variable, every {@link Scope#EVENT} variable when it has
 * an event, plus the scopes its context provides ({@link MailVariables}).
 */
public enum MailVariable {

    // ── SYSTEM: every mail ──────────────────────────────────────────────────
    CURRENT_YEAR("currentYear", Scope.SYSTEM),
    LOGO_URL("logoUrl", Scope.SYSTEM),
    BASE_URL("baseUrl", Scope.SYSTEM),
    SUPPORT_URL("supportUrl", Scope.SYSTEM),
    ORGANIZER_NAME("organizerName", Scope.SYSTEM),
    ORGANIZER_EMAIL("organizerEmail", Scope.SYSTEM),

    // ── EVENT: every mail about an event ────────────────────────────────────
    EVENT_CODE("eventCode", Scope.EVENT),
    EVENT_NUMBER("eventNumber", Scope.EVENT),
    EVENT_TITLE("eventTitle", Scope.EVENT),
    EVENT_TYPE("eventType", Scope.EVENT),
    EVENT_DATE("eventDate", Scope.EVENT),
    EVENT_TIME("eventTime", Scope.EVENT),
    VENUE_NAME("venueName", Scope.EVENT, "venue"),
    VENUE_ADDRESS("venueAddress", Scope.EVENT),
    VENUE_DIRECTIONS_URL("venueDirectionsUrl", Scope.EVENT),
    EVENT_URL("eventUrl", Scope.EVENT, "eventDetailLink"),
    REGISTRATION_LINK("registrationLink", Scope.EVENT),
    CONFERENCE_LANGUAGE("conferenceLanguage", Scope.EVENT),
    TOPIC_TITLE("topicTitle", Scope.EVENT),
    AGENDA("agenda", Scope.EVENT),
    SPEAKERS_SECTION("speakersSection", Scope.EVENT),
    UPCOMING_EVENTS_SECTION("upcomingEventsSection", Scope.EVENT),

    // ── RECIPIENT: one person ───────────────────────────────────────────────
    RECIPIENT_NAME("recipientName", Scope.RECIPIENT),
    SALUTATION("salutation", Scope.RECIPIENT),
    DASHBOARD_LINK("dashboardLink", Scope.RECIPIENT),

    // ── REGISTRATION ────────────────────────────────────────────────────────
    ATTENDEE_FIRST_NAME("attendeeFirstName", Scope.REGISTRATION),
    ATTENDEE_LAST_NAME("attendeeLastName", Scope.REGISTRATION),
    ATTENDEE_NAME("attendeeName", Scope.REGISTRATION),
    REGISTRATION_CODE("registrationCode", Scope.REGISTRATION, true),
    CONFIRMATION_URL("confirmationUrl", Scope.REGISTRATION, true),
    DEREGISTRATION_URL("deregistrationUrl", Scope.REGISTRATION, true, "deregistrationLink"),
    WAITLIST_POSITION("waitlistPosition", Scope.REGISTRATION),
    CREATE_ACCOUNT_URL("createAccountUrl", Scope.REGISTRATION),

    // ── SPEAKER ─────────────────────────────────────────────────────────────
    SPEAKER_NAME("speakerName", Scope.SPEAKER),
    SESSION_TITLE("sessionTitle", Scope.SPEAKER),
    SESSION_DESCRIPTION("sessionDescription", Scope.SPEAKER),
    DEADLINE("deadline", Scope.SPEAKER),
    DAYS_REMAINING("daysRemaining", Scope.SPEAKER),
    RESPONSE_DEADLINE("responseDeadline", Scope.SPEAKER),
    CONTENT_DEADLINE("contentDeadline", Scope.SPEAKER),
    PORTAL_LINK("portalLink", Scope.SPEAKER),
    PROFILE_URL("profileUrl", Scope.SPEAKER),
    CONTENT_URL("contentUrl", Scope.SPEAKER),
    LOGIN_URL("loginUrl", Scope.SPEAKER),
    USERNAME_FOR_LOGIN("usernameForLogin", Scope.SPEAKER),
    TEMPORARY_PASSWORD("temporaryPassword", Scope.SPEAKER, true),
    TEMP_PASSWORD_VALIDITY_DAYS("tempPasswordValidityDays", Scope.SPEAKER),
    USE_EXISTING_PASSWORD("useExistingPassword", Scope.SPEAKER),

    // ── SUBSCRIBER (newsletter) ─────────────────────────────────────────────
    UNSUBSCRIBE_LINK("unsubscribeLink", Scope.SUBSCRIBER, true),
    PREFERENCES_LINK("preferencesLink", Scope.SUBSCRIBER),
    REMINDER_PREFIX("reminderPrefix", Scope.SUBSCRIBER),

    // ── VENUE & CATERING ────────────────────────────────────────────────────
    NOTES("notes", Scope.VENUE),
    ORGANIZER_SIGNATURE("organizerSignature", Scope.VENUE),

    // ── TASK (organizer task reminder) ──────────────────────────────────────
    TASK_NAME("taskName", Scope.TASK),
    DUE_DATE("dueDate", Scope.TASK),
    TASK_NOTES("taskNotes", Scope.TASK),
    TASK_BOARD_LINK("taskBoardLink", Scope.TASK),

    // ── QNA (session Q&A digest) ────────────────────────────────────────────
    COUNT("count", Scope.QNA),
    IS_SINGLE("isSingle", Scope.QNA),
    IS_MULTIPLE("isMultiple", Scope.QNA),
    QNA_LINK("qnaLink", Scope.QNA),

    // ── LAYOUT: filled by the layout merge, never by a caller ───────────────
    CONTENT("content", Scope.LAYOUT);

    /** Who provides a variable's value. */
    public enum Scope { SYSTEM, EVENT, RECIPIENT, REGISTRATION, SPEAKER, SUBSCRIBER, VENUE, TASK, QNA, LAYOUT }

    private static final Map<String, MailVariable> BY_NAME = Arrays.stream(values())
            .flatMap(v -> v.names().stream().map(n -> Map.entry(n, v)))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    private final String name;
    private final Scope scope;
    private final boolean credential;
    private final List<String> aliases;

    MailVariable(String name, Scope scope, String... aliases) {
        this(name, scope, false, aliases);
    }

    MailVariable(String name, Scope scope, boolean credential, String... aliases) {
        this.name = name;
        this.scope = scope;
        this.credential = credential;
        this.aliases = List.of(aliases);
    }

    /** Canonical placeholder name, e.g. {@code eventUrl}. */
    public String placeholder() {
        return name;
    }

    public Scope scope() {
        return scope;
    }

    /** Carries a credential: masked in records and previews, dummy in test sends. */
    public boolean credential() {
        return credential;
    }

    public List<String> aliases() {
        return aliases;
    }

    /** Canonical name plus aliases. */
    public List<String> names() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(name), aliases.stream()).toList();
    }

    /** The variable a placeholder name (canonical or alias) stands for. */
    public static Optional<MailVariable> byName(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }

    /** Every placeholder name (canonical and alias) of the given scopes. */
    public static Set<String> namesOf(Set<Scope> scopes) {
        return Arrays.stream(values()).filter(v -> scopes.contains(v.scope))
                .flatMap(v -> v.names().stream())
                .collect(Collectors.toUnmodifiableSet());
    }
}
