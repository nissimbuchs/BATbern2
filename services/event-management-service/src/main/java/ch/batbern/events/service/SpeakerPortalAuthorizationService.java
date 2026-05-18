package ch.batbern.events.service;

import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.exception.SpeakerPortalAccessDeniedException;
import ch.batbern.events.repository.SpeakerPoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Story 11.E.3: resolves the {@link SpeakerPool} row that a Cognito-authenticated speaker
 * is operating on, gating access by pool ownership.
 *
 * <p>Used by every speaker-portal controller method that targets a specific event
 * ({@code respond}, {@code content/*}, {@code materials/*}). The dashboard endpoint is the
 * single exception — it lists across all of the speaker's events and uses
 * {@link SpeakerPoolRepository#findByUsername(String)} directly.
 *
 * <p>Centralising the lookup gives a single audit-logging point for foreign-event access
 * attempts and a single place to evolve the rule (e.g. should a future "speaker delegate"
 * concept allow speaker A to act on behalf of speaker B for event X, the change lands here).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerPortalAuthorizationService {

    private final SpeakerPoolRepository speakerPoolRepository;

    /**
     * Looks up the speaker_pool row for {@code (username, eventCode)} and returns it.
     * Throws {@link SpeakerPortalAccessDeniedException} (→ HTTP 403) if no row matches —
     * either the event doesn't exist or this speaker has no invitation for it. The two
     * cases are deliberately collapsed: revealing "event exists but you don't have access"
     * separately from "event doesn't exist" would leak event-existence information.
     *
     * @param username  the Cognito principal's username (from {@code Authentication#getName()})
     * @param eventCode the meaningful event identifier from the URL path
     * @return the matching {@link SpeakerPool} row
     * @throws SpeakerPortalAccessDeniedException if no row matches
     */
    @Transactional(readOnly = true)
    public SpeakerPool resolveSpeakerPool(String username, String eventCode) {
        return speakerPoolRepository
                .findByEventCodeAndUsername(eventCode, username)
                .orElseThrow(() -> {
                    log.warn(
                            "Speaker portal access denied: username={} eventCode={}",
                            username, eventCode);
                    return new SpeakerPortalAccessDeniedException(
                            "No invitation found for the requested event");
                });
    }
}
