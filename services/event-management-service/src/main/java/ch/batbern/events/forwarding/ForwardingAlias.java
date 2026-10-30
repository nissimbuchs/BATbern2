package ch.batbern.events.forwarding;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One {@code @batbern.ch} forwarding address, classified by its local part.
 *
 * <p>Ported from the forwarder Lambda ({@code address-resolver.ts}, {@code sender-auth.ts}).
 * Who may send to an alias is part of the alias: {@code info@}, {@code events@},
 * {@code support@} and {@code batbern{N}-moderator@} are contact addresses open to anyone;
 * everything else is organizer-only. The model is fail-closed: a new kind is organizer-only
 * unless it is added to {@link Kind#openToAnyone} on purpose. Never open a mass-mail alias.
 *
 * @param localPart lowercased local part, e.g. {@code batbern60-speaker}
 * @param kind      what the alias resolves to
 * @param eventCode {@code BATbern{N}} for the per-event kinds, otherwise null
 */
public record ForwardingAlias(String localPart, Kind kind, String eventCode) {

    private static final Pattern EVENT_ALIAS =
            Pattern.compile("^batbern(\\d+)(?:-(speaker|moderator|participants))?$");
    private static final Pattern EVENT_CODE = Pattern.compile("^batbern(\\d+)$", Pattern.CASE_INSENSITIVE);

    public enum Kind {
        ORGANIZERS,
        PARTNERS,
        SUPPORT,
        EVENT_SPEAKERS,
        EVENT_MODERATOR,
        EVENT_PARTICIPANTS,
        /** Bare {@code batbern{N}@}: deprecated spelling of {@code batbern{N}-participants@}. */
        EVENT_PARTICIPANTS_DEPRECATED,
        /** {@code noreply@}, {@code calendar@}: auto-replies to our own mail land here. Dropped. */
        NO_REPLY,
        UNKNOWN
    }

    /**
     * Classify an address. Empty when it is not in the forwarding domain.
     */
    public static Optional<ForwardingAlias> parse(String address, String forwardingDomain) {
        if (address == null || forwardingDomain == null) {
            return Optional.empty();
        }
        String lower = address.trim().toLowerCase(Locale.ROOT);
        String suffix = "@" + forwardingDomain.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(suffix) || lower.length() == suffix.length()) {
            return Optional.empty();
        }
        String localPart = lower.substring(0, lower.length() - suffix.length());
        return Optional.of(classify(localPart));
    }

    private static ForwardingAlias classify(String localPart) {
        switch (localPart) {
            case "ok", "info", "events":
                return new ForwardingAlias(localPart, Kind.ORGANIZERS, null);
            case "partner":
                return new ForwardingAlias(localPart, Kind.PARTNERS, null);
            case "support":
                return new ForwardingAlias(localPart, Kind.SUPPORT, null);
            case "noreply", "no-reply", "calendar":
                return new ForwardingAlias(localPart, Kind.NO_REPLY, null);
            default:
                break;
        }
        Matcher m = EVENT_ALIAS.matcher(localPart);
        if (!m.matches()) {
            return new ForwardingAlias(localPart, Kind.UNKNOWN, null);
        }
        String eventCode = "BATbern" + m.group(1);
        String suffix = m.group(2);
        Kind kind;
        if (suffix == null) {
            kind = Kind.EVENT_PARTICIPANTS_DEPRECATED;
        } else if (suffix.equals("speaker")) {
            kind = Kind.EVENT_SPEAKERS;
        } else if (suffix.equals("moderator")) {
            kind = Kind.EVENT_MODERATOR;
        } else {
            kind = Kind.EVENT_PARTICIPANTS;
        }
        return new ForwardingAlias(localPart, kind, eventCode);
    }

    /**
     * {@code batbern{N}-moderator@{domain}} for an event: the speakers' contact point (Story 16.0,
     * owner decision 2026-10-29). Null when the code is not {@code BATbern{N}}, so callers fall
     * back to the default Reply-To.
     */
    public static String moderatorAddress(String eventCode, String forwardingDomain) {
        if (eventCode == null || forwardingDomain == null) {
            return null;
        }
        Matcher m = EVENT_CODE.matcher(eventCode.trim());
        if (!m.matches()) {
            return null;
        }
        return "batbern" + m.group(1) + "-moderator@" + forwardingDomain.toLowerCase(Locale.ROOT);
    }

    /** Contact addresses anyone may write to. Everything else is organizer-only. */
    public boolean openToAnyone() {
        return switch (kind) {
            case SUPPORT, EVENT_MODERATOR -> true;
            case ORGANIZERS -> !localPart.equals("ok");
            default -> false;
        };
    }

    /** Whether mail to this alias is forwarded at all. */
    public boolean forwardable() {
        return kind != Kind.NO_REPLY && kind != Kind.UNKNOWN;
    }
}
