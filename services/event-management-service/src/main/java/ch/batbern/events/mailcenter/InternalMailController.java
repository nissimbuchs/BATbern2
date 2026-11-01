package ch.batbern.events.mailcenter;

import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.OutboundMail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Story 16.9 AC4: another service records a mail it sent in the event's "What went out".
 * partner-coordination posts its meeting invites and cancellations here; EMS records them through
 * the same {@link JpaOutboundMailRecorder} as its own mail, so the To: line is split per address.
 *
 * <p>{@code POST /internal/mails} carries no JWT: it is called from the {@code @Async} send thread,
 * which has no user. It is not routed by the API gateway, which also denies {@code /internal/**}
 * (reachable in the VPC only), and it is not part of a public OpenAPI contract, like
 * {@code InternalRsvpController} in partner-coordination. It accepts audience PARTNERS only, so it
 * cannot be used to plant mail of another audience, and it bounds every size.
 */
@RestController
@RequiredArgsConstructor
public class InternalMailController {

    static final int MAX_SUBJECT = 1000;
    static final int MAX_BODY = 2 * 1024 * 1024;
    static final int MAX_ADDRESSES = 500;

    private final JpaOutboundMailRecorder recorder;

    @PostMapping("/internal/mails")
    public ResponseEntity<Void> record(@Valid @RequestBody InternalMailRequest request) {
        if (!MailContext.Audience.PARTNERS.name().equals(request.audience())) {
            throw new IllegalArgumentException("Only audience PARTNERS may be recorded through /internal/mails");
        }
        List<String> cc = request.cc() == null ? List.of() : request.cc();
        if (JpaOutboundMailRecorder.splitAddresses(request.to()).size() + cc.size() > MAX_ADDRESSES) {
            throw new IllegalArgumentException("At most " + MAX_ADDRESSES + " addresses per mail");
        }
        MailContext.ContentSource source = request.contentSource() == null
                ? MailContext.ContentSource.TEMPLATE
                : MailContext.ContentSource.valueOf(request.contentSource());
        MailContext context = new MailContext(request.sendGroupId(), request.eventCode(),
                MailContext.Audience.PARTNERS, request.templateKey(), source, request.senderUsername(), null,
                Boolean.TRUE.equals(request.test()), List.of(), request.reference());
        recorder.record(context, new OutboundMail(request.to(), cc, request.subject(), request.htmlBody(),
                request.sesMessageId(), request.failureReason()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Flat mirror of {@link MailContext} + {@link OutboundMail}. Subject and body arrive already
     * redacted: {@code EmailService.record} redacts before any recorder sees the mail (16.1a).
     */
    public record InternalMailRequest(
            @NotBlank @Size(max = 256) String sendGroupId,
            @Size(max = 50) String eventCode,
            @NotBlank String audience,
            @Size(max = 100) String templateKey,
            String contentSource,
            @Size(max = 100) String senderUsername,
            Boolean test,
            @Size(max = 100) String reference,
            @NotNull @Size(max = 64_000) String to,
            @Size(max = MAX_ADDRESSES) List<@Size(max = 320) String> cc,
            @Size(max = MAX_SUBJECT) String subject,
            @Size(max = MAX_BODY) String htmlBody,
            @Size(max = 255) String sesMessageId,
            @Size(max = 500) String failureReason) {
    }
}
