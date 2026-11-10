package ch.batbern.events.service;

import java.util.UUID;

/**
 * A queued newsletter or registrant-notice send (Story 16.8c): its {@code newsletter_sends} row and
 * how many recipients it was counted for. The job runs on an {@code @Async} thread; the composer
 * records the mail under {@code sendId}.
 *
 * @param sendId         the {@code newsletter_sends} row
 * @param recipientCount the recipients counted when the send was queued
 */
public record QueuedSend(UUID sendId, int recipientCount) {
}
