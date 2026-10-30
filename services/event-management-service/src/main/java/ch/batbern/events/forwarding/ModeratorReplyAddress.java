package ch.batbern.events.forwarding;

import ch.batbern.events.domain.Event;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reply-To for speaker-facing mail: {@code batbern{N}-moderator@}, the speakers' contact point
 * (Story 16.0 AC1, owner decision 2026-10-29). The alias resolves to the event's lead organizer
 * and is open to any sender, so a speaker's answer reaches a person instead of {@code replies@}.
 */
@Component
@RequiredArgsConstructor
public class ModeratorReplyAddress {

    private final EmailForwardingProperties properties;

    /** @return the moderator alias, or null (→ the default Reply-To) when the event has no number */
    public String forEvent(Event event) {
        return event == null ? null : ForwardingAlias.moderatorAddress(event.getEventCode(), properties.domain());
    }
}
