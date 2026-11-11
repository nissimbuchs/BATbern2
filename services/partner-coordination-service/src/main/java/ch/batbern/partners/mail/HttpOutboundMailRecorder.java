package ch.batbern.partners.mail;

import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.OutboundMail;
import ch.batbern.shared.mail.OutboundMailRecorder;
import ch.batbern.shared.security.S2sHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Story 16.9: records what partner-coordination sends (meeting invites and cancellations) in the
 * event's "What went out", which lives in event-management. {@code EmailService} hands every send
 * with a {@link MailContext} to this bean (16.1a); it posts the already redacted mail to EMS
 * {@code POST /internal/mails}.
 *
 * <p>Best effort (AC2): the mail has already left. EMS down, slow or answering an error costs the
 * record, never the send: one WARN line with the reference and the HTTP status or exception class,
 * no addresses and no body, and no retry.
 *
 * <p>Headers from {@link S2sHeaders#forCurrentUser()} like every service-to-service call (ADR-016
 * decision 3): the user's JWT when the sending thread has one that stays valid, none otherwise. It
 * usually runs on the {@code @Async} send thread, which has no {@code SecurityContext}, so the record
 * mostly goes out without one; {@code POST /internal/mails} needs none. {@code /internal/**} is not
 * routed by the API gateway and is reachable in the VPC only. Own short timeouts (2 s connect, 5 s
 * read) so a hanging EMS never holds the async pool.
 */
@Component
@Slf4j
public class HttpOutboundMailRecorder implements OutboundMailRecorder {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestTemplate restTemplate;
    private final String eventManagementBaseUrl;

    @Autowired
    public HttpOutboundMailRecorder(RestTemplateBuilder builder,
            @Value("${event-management-service.base-url}") String eventManagementBaseUrl) {
        this(builder.connectTimeout(CONNECT_TIMEOUT).readTimeout(READ_TIMEOUT).build(), eventManagementBaseUrl);
    }

    HttpOutboundMailRecorder(RestTemplate restTemplate, String eventManagementBaseUrl) {
        this.restTemplate = restTemplate;
        this.eventManagementBaseUrl = eventManagementBaseUrl;
    }

    @Override
    public void record(MailContext context, OutboundMail mail) {
        try {
            restTemplate.postForEntity(eventManagementBaseUrl + "/internal/mails",
                    new HttpEntity<>(payload(context, mail), S2sHeaders.forCurrentUser()), Void.class);
        } catch (HttpStatusCodeException e) {
            log.warn("Mail record not delivered to event-management: reference={}, group={}, status={}",
                    context.reference(), context.sendGroupId(), e.getStatusCode().value());
        } catch (RuntimeException e) {
            log.warn("Mail record not delivered to event-management: reference={}, group={}, cause={}",
                    context.reference(), context.sendGroupId(), e.getClass().getSimpleName());
        }
    }

    /** Flat mirror of {@link MailContext} + {@link OutboundMail}; secrets never leave the service. */
    private static Map<String, Object> payload(MailContext context, OutboundMail mail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sendGroupId", context.sendGroupId());
        body.put("eventCode", context.eventCode());
        body.put("audience", context.audience().name());
        body.put("templateKey", context.templateKey());
        body.put("contentSource", context.contentSource().name());
        body.put("senderUsername", context.senderUsername());
        body.put("test", context.test());
        body.put("reference", context.reference());
        body.put("to", mail.to());
        body.put("cc", mail.cc());
        body.put("subject", mail.subject());
        body.put("htmlBody", mail.htmlBody());
        body.put("sesMessageId", mail.sesMessageId());
        body.put("failureReason", mail.failureReason());
        return body;
    }
}
