package ch.batbern.events.service.workflow;

import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;

/**
 * Result of {@link ch.batbern.events.service.SpeakerWorkflowService#transition} — the persisted
 * {@link SpeakerPool} and the new {@link SpeakerStatusHistory} row so callers can build their
 * response DTOs without a second DB roundtrip.
 *
 * <p>{@code history} is {@code null} when the caller requested
 * {@code TransitionPayload.suppressHistoryRow == true} (e.g. outreach-driven
 * IDENTIFIED→CONTACTED, where {@code outreach_history} captures the audit).
 */
public record TransitionResult(SpeakerPool speakerPool, SpeakerStatusHistory history) {
}
