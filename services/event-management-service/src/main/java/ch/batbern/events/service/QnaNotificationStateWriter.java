package ch.batbern.events.service;

import ch.batbern.events.domain.SessionQnaNotification;
import ch.batbern.events.domain.SessionQnaNotificationId;
import ch.batbern.events.repository.SessionQnaNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists a Q&A digest throttle/water-mark row in its <b>own</b> committed transaction
 * (Story 15.7, code-review fix).
 *
 * <p>This MUST be a separate bean (not a method on {@link QnaNotificationService}) so the
 * {@code REQUIRES_NEW} boundary is honoured by the Spring proxy. The digest flush deliberately
 * runs <b>without</b> a surrounding transaction and records each recipient's state here, right
 * after a successful send — so a failure (or Fargate Spot kill) while processing a later recipient
 * can never roll back an already-sent recipient's water mark and cause a duplicate digest on the
 * next flush. Blast radius is one recipient.
 */
@Component
@RequiredArgsConstructor
public class QnaNotificationStateWriter {

    private final SessionQnaNotificationRepository notificationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSent(UUID windowId, String recipientUsername,
                           Instant lastNotifiedAt, Instant notifiedThrough) {
        SessionQnaNotification state = notificationRepository
                .findByIdWindowIdAndIdRecipientUsername(windowId, recipientUsername)
                .orElseGet(() -> SessionQnaNotification.builder()
                        .id(new SessionQnaNotificationId(windowId, recipientUsername))
                        .build());
        state.setLastNotifiedAt(lastNotifiedAt);
        state.setNotifiedThrough(notifiedThrough);
        notificationRepository.save(state);
    }
}
