package ch.batbern.events.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InboundEmailListenerService (Story 10.17 — AC1, AC3, AC4, AC5).
 *
 * Tests MIME parsing, S3 fetch, and routing delegation.
 * TDD: RED phase — tests define behaviour before implementation.
 */
@ExtendWith(MockitoExtension.class)
class InboundEmailListenerServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private InboundEmailRouter router;

    private InboundEmailListenerService listenerService;

    private static final String BUCKET_NAME = "batbern-inbound-emails-staging";

    @BeforeEach
    void setUp() {
        listenerService = new InboundEmailListenerService(s3Client, router, BUCKET_NAME);
    }

    /**
     * A minimal raw MIME email for testing.
     */
    private static final String RAW_EMAIL = "From: sender@example.com\r\n"
            + "To: replies@batbern.ch\r\n"
            + "Subject: Re: BATbern42 Registration Confirmation\r\n"
            + "MIME-Version: 1.0\r\n"
            + "Content-Type: text/plain; charset=UTF-8\r\n"
            + "\r\n"
            + "cancel\r\n";

    private String s3NotificationJson(String key) {
        return "{\"Records\":[{\"s3\":{\"bucket\":{\"name\":\"" + BUCKET_NAME
               + "\"},\"object\":{\"key\":\"" + key + "\"}}}]}";
    }

    @Test
    void handleS3Notification_withValidEmail_parsesAndRoutes() throws IOException {
        String key = "emails/test-message-id";
        byte[] rawBytes = RAW_EMAIL.getBytes(StandardCharsets.UTF_8);

        ResponseInputStream<GetObjectResponse> responseStream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                new ByteArrayInputStream(rawBytes)
        );
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        listenerService.handleS3Notification(s3NotificationJson(key));

        ArgumentCaptor<InboundEmailRouter.ParsedEmail> captor =
                ArgumentCaptor.forClass(InboundEmailRouter.ParsedEmail.class);
        verify(router).route(captor.capture());

        InboundEmailRouter.ParsedEmail parsed = captor.getValue();
        assertThat(parsed.senderEmail()).isEqualTo("sender@example.com");
        assertThat(parsed.subject()).contains("BATbern42");
        assertThat(parsed.bodyFirstLine()).isEqualToIgnoringCase("cancel");
    }

    @Test
    void handleS3Notification_whenS3FetchFails_logsAndDiscardsGracefully() {
        String key = "emails/failing-key";
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("Not found").build());

        // Should NOT throw — exception must be caught
        listenerService.handleS3Notification(s3NotificationJson(key));

        verifyNoInteractions(router);
    }

    @Test
    void handleS3Notification_withEmptyKey_returnsEarlyWithoutRouting() {
        String notificationWithEmptyKey = s3NotificationJson("");

        listenerService.handleS3Notification(notificationWithEmptyKey);

        verifyNoInteractions(router);
        verifyNoInteractions(s3Client);
    }

    @Test
    void handleS3Notification_withInvalidJson_logsAndDiscardsGracefully() {
        // Should NOT throw for malformed JSON
        listenerService.handleS3Notification("not-json");

        verifyNoInteractions(router);
        verifyNoInteractions(s3Client);
    }
}
