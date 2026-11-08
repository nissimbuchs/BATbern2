package ch.batbern.events.forwarding;

import ch.batbern.events.domain.Event;
import ch.batbern.events.mailcenter.render.ModeratorReplyAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link ModeratorReplyAddress} from the alias scheme and the configured forwarding domain. The bean
 * keeps the name it had before the interface split.
 */
@Component("moderatorReplyAddress")
@RequiredArgsConstructor
public class ForwardingModeratorReplyAddress implements ModeratorReplyAddress {

    private final EmailForwardingProperties properties;

    @Override
    public String forEvent(Event event) {
        return event == null ? null : ForwardingAlias.moderatorAddress(event.getEventCode(), properties.domain());
    }
}
