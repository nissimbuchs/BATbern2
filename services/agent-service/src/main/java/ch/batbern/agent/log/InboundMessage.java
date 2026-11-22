package ch.batbern.agent.log;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;

/**
 * A message a run was given, logged in full (AD-9).
 *
 * @param channel    where it came from
 * @param externalId the source's idempotency key (a mail's message id), or {@code null}
 * @param receivedAt when the source received it
 * @param content    the whole message as JSON
 */
public record InboundMessage(InboundChannel channel, String externalId, Instant receivedAt, JsonNode content) {

    public InboundMessage {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(content, "content");
    }
}
