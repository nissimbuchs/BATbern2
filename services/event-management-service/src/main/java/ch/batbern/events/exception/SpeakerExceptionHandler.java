package ch.batbern.events.exception;

import ch.batbern.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * The speakers module's error responses that carry more than {@code details.code} (Story 17.14).
 * Every other domain exception is mapped once by {@code GlobalExceptionHandler}.
 *
 * <p>{@link Ordered#HIGHEST_PRECEDENCE}: {@code GlobalExceptionHandler} maps every
 * {@link DomainException} and has a catch-all, so this advice must run first.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class SpeakerExceptionHandler {

    /** Story 6.2a AC7: the speaker already answered the invitation (409, previous answer and time). */
    @ExceptionHandler(AlreadyRespondedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyResponded(
            AlreadyRespondedException ex,
            HttpServletRequest request) {
        log.warn("Already responded: {}", ex.getMessage());
        Map<String, Object> details = new HashMap<>();
        details.put("previousResponse", ex.getPreviousResponse().toString());
        if (ex.getRespondedAt() != null) {
            details.put("respondedAt", ex.getRespondedAt().toString());
        }
        return DomainErrorResponse.of(ex, request, ex.getMessage(), details);
    }

    /** Story 11.D.1: promote called in a state other than CONTACTED or READY (409, current state). */
    @ExceptionHandler(InvalidPromotionStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPromotionState(
            InvalidPromotionStateException ex,
            HttpServletRequest request) {
        log.warn("Invalid promotion state: {}", ex.getMessage());
        Map<String, Object> details = DomainErrorResponse.codeDetails(ex);
        details.put("currentState", ex.getCurrentState().name());
        return DomainErrorResponse.of(ex, request, ex.getMessage(), details);
    }

    /** Story 11.B.3 AC5: READY is reachable only through the promote endpoint (400, the alternative). */
    @ExceptionHandler(ReadyRequiresPromoteException.class)
    public ResponseEntity<ErrorResponse> handleReadyRequiresPromote(
            ReadyRequiresPromoteException ex,
            HttpServletRequest request) {
        log.warn("READY requires promote endpoint: {}", ex.getMessage());
        Map<String, Object> details = DomainErrorResponse.codeDetails(ex);
        details.put("rejectedValue", "READY");
        details.put("alternativeEndpoint", ReadyRequiresPromoteException.ALTERNATIVE_ENDPOINT);
        return DomainErrorResponse.of(ex, request, ex.getMessage(), details);
    }

    /** Story 11.B.2: READY to INVITED blocked by the slot-capacity gate (409, the counts). */
    @ExceptionHandler(SlotCapacityReachedException.class)
    public ResponseEntity<ErrorResponse> handleSlotCapacityReached(
            SlotCapacityReachedException ex,
            HttpServletRequest request) {
        log.warn("Slot capacity reached: {}", ex.getMessage());
        Map<String, Object> details = DomainErrorResponse.codeDetails(ex);
        details.put("eventId", ex.getEventId().toString());
        details.put("acceptedCount", ex.getAcceptedCount());
        details.put("invitedCount", ex.getInvitedCount());
        details.put("maxSlots", ex.getMaxSlots());
        return DomainErrorResponse.of(ex, request, ex.getMessage(), details);
    }
}
