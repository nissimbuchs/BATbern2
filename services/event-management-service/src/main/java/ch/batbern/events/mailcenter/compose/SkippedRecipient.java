package ch.batbern.events.mailcenter.compose;

/** Someone in the audience who does not get the mail, and why. */
public record SkippedRecipient(String displayName, String reason) {

    public static final String OPTED_OUT = "OPTED_OUT";
    public static final String REMINDERS_DISABLED = "REMINDERS_DISABLED";
    public static final String NO_EMAIL = "NO_EMAIL";
    public static final String DUPLICATE = "DUPLICATE";
}
