package ch.batbern.events.mailcenter.render;

import ch.batbern.events.domain.Event;

/**
 * Reply-To for speaker-facing mail: {@code batbern{N}-moderator@}, the speakers' contact point
 * (Story 16.0 AC1, owner decision 2026-10-29). The alias resolves to the event's lead organizer
 * and is open to any sender, so a speaker's answer reaches a person instead of {@code replies@}.
 *
 * <p>Mail core declares it; the mail center's forwarding module, which owns the alias scheme,
 * implements it, so senders depend on mail core only.
 */
public interface ModeratorReplyAddress {

    /** @return the moderator alias, or null (→ the default Reply-To) when the event has no number */
    String forEvent(Event event);
}
