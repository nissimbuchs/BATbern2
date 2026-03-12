package ch.batbern.events.client.impl;

import ch.batbern.events.client.PartnerMeetingRsvpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * RestTemplate-based implementation of PartnerMeetingRsvpClient — Story 10.27 (AC4).
 *
 * <p><strong>No JWT propagation</strong> — this client is called from an @SqsListener
 * (async background thread with no SecurityContextHolder). The /internal/** endpoint
 * is permitAll(), secured by VPC/Service Connect private DNS only.
 *
 * <p>Errors are logged and discarded — inbound email processing must never fail.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PartnerMeetingRsvpClientImpl implements PartnerMeetingRsvpClient {

    private final RestTemplate restTemplate;

    @Value("${partner-coordination-service.base-url:http://partner-coordination:8080}")
    private String partnerCoordinationBaseUrl;

    @Override
    public void recordRsvp(UUID meetingId, String attendeeEmail, String partStat) {
        try {
            // NO JWT header — /internal/** is permitAll(), SQS context has no SecurityContext
            Map<String, Object> body = Map.of(
                    "meetingId", meetingId,
                    "attendeeEmail", attendeeEmail,
                    "partStat", partStat
            );
            restTemplate.postForEntity(
                    partnerCoordinationBaseUrl + "/internal/partner-meetings/rsvps",
                    body,
                    Void.class
            );
            log.info("Recorded RSVP via PCS: meetingId={}, emailPrefix={}, status={}",
                    meetingId, emailPrefix(attendeeEmail), partStat);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("Partner meeting {} not found in PCS — discarding RSVP", meetingId);
            } else {
                log.error("HTTP error recording RSVP for meeting {} — discarding: {} {}",
                        meetingId, e.getStatusCode(), e.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to record RSVP for meeting {} — discarding: {}", meetingId, e.getMessage());
            // Never rethrow — inbound processing must not fail
        }
    }

    private String emailPrefix(String email) {
        return email != null ? email.substring(0, Math.min(5, email.length())) : "?";
    }
}
