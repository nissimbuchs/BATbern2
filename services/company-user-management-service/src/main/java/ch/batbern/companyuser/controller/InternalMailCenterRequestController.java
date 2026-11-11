package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.service.MailCenterRequestFeedService;
import ch.batbern.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Epic 17 entry 7: the change feed of merge and logo requests, pulled by event-management's Mail
 * Center every minute ({@link MailCenterRequestFeedService}). Same reachability as
 * {@link InternalUserController}: VPC only, the gateway denies {@code /internal/**}; a forwarded JWT
 * is optional and never changes the answer. A read, so it records nobody.
 */
@RestController
@RequiredArgsConstructor
public class InternalMailCenterRequestController {

    private final MailCenterRequestFeedService feed;

    @GetMapping("/internal/mail-center-requests")
    public ResponseEntity<MailCenterRequestFeedService.Page> mailCenterRequests(
            @RequestParam(required = false) String since,
            @RequestParam(required = false, defaultValue = "" + MailCenterRequestFeedService.DEFAULT_LIMIT) int limit) {
        return ResponseEntity.ok(feed.page(since, limit));
    }

    @ExceptionHandler({MailCenterRequestFeedService.InvalidCursorException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> badRequest(RuntimeException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(status.value())
                .error(status.getReasonPhrase())
                .errorCode("VALIDATION_ERROR")
                .message(e.getMessage())
                .build());
    }
}
