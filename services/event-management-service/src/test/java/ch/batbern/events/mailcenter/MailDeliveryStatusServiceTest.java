package ch.batbern.events.mailcenter;

import ch.batbern.events.repository.MailRecipientRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Delivery status metrics (Story 16.1b AC3)")
class MailDeliveryStatusServiceTest {

    private final MailRecipientRepository recipients = mock(MailRecipientRepository.class);
    private final MailCenterMetrics metrics = mock(MailCenterMetrics.class);
    private final MailDeliveryStatusService service =
            new MailDeliveryStatusService(recipients, new ObjectMapper(), metrics);

    @Test
    @DisplayName("an unknown Message-ID is counted, not thrown")
    void unknownMessageId_counted() {
        when(recipients.existsBySesMessageId(anyString())).thenReturn(false);

        MailDeliveryStatusService.Outcome outcome = service.process(delivery("unknown-id"));

        assertThat(outcome.known()).isFalse();
        verify(metrics).increment(MailCenterMetrics.DELIVERY_EVENTS_UNMATCHED);
    }

    @Test
    @DisplayName("a known Message-ID is not counted as unmatched")
    void knownMessageId_notCounted() {
        when(recipients.existsBySesMessageId("known-id")).thenReturn(true);

        service.process(delivery("known-id"));

        verify(metrics, never()).increment(MailCenterMetrics.DELIVERY_EVENTS_UNMATCHED);
    }

    @Test
    @DisplayName("16.2a: the Message-ID header SES sent is stored on the recipients, without brackets")
    void commonHeaderMessageId_stored() {
        when(recipients.existsBySesMessageId("known-id")).thenReturn(true);

        service.process(deliveryWithHeader("known-id", "<custom-42@mailer.example.com>"));

        verify(recipients).recordMessageIdHeader("known-id", "custom-42@mailer.example.com");
    }

    @Test
    @DisplayName("16.2a: no header in the event, or an unknown Message-ID: nothing stored")
    void noHeaderOrUnknown_nothingStored() {
        when(recipients.existsBySesMessageId("known-id")).thenReturn(true);
        service.process(delivery("known-id"));
        service.process(deliveryWithHeader("unknown-id", "<x@y>"));

        verify(recipients, never()).recordMessageIdHeader(anyString(), anyString());
    }

    private static String deliveryWithHeader(String messageId, String header) {
        String event = "{\"eventType\":\"Delivery\",\"mail\":{\"messageId\":\"" + messageId + "\","
                + "\"commonHeaders\":{\"messageId\":\"" + header + "\"}},"
                + "\"delivery\":{\"recipients\":[\"success@simulator.amazonses.com\"]}}";
        return "{\"Type\":\"Notification\",\"Message\":" + new ObjectMapper().valueToTree(event) + "}";
    }

    private static String delivery(String messageId) {
        String event = "{\"eventType\":\"Delivery\",\"mail\":{\"messageId\":\"" + messageId + "\"},"
                + "\"delivery\":{\"recipients\":[\"success@simulator.amazonses.com\"]}}";
        return "{\"Type\":\"Notification\",\"Message\":" + new ObjectMapper().valueToTree(event) + "}";
    }
}
