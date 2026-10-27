package ch.batbern.events.forwarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailForwardingListener")
class EmailForwardingListenerTest {

    private static final String BUCKET = "batbern-inbound-emails-staging";

    @Mock
    private S3Client s3Client;
    @Mock
    private EmailForwardingService forwardingService;

    private EmailForwardingListener listener;

    @BeforeEach
    void setUp() {
        listener = new EmailForwardingListener(s3Client, forwardingService, new ObjectMapper(), BUCKET);
    }

    private static String event(String bucket, String key) {
        return """
                {"Records":[{"eventSource":"aws:s3","s3":{"bucket":{"name":"%s"},"object":{"key":"%s"}}}]}
                """.formatted(bucket, key);
    }

    @Test
    @DisplayName("should_fetchObjectAndForward_when_forwardingNotification")
    void should_fetchObjectAndForward_when_forwardingNotification() throws Exception {
        byte[] raw = "From: a@b.ch\r\n\r\nx".getBytes(StandardCharsets.UTF_8);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), raw));

        listener.handleS3Notification(event(BUCKET, "forwarding/abc+def%3D"));

        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo("forwarding/abc def=");
        verify(forwardingService).forward("forwarding/abc def=", raw);
    }

    @Test
    @DisplayName("should_ignore_when_testEventWithoutRecords")
    void should_ignore_when_testEventWithoutRecords() throws Exception {
        listener.handleS3Notification("{\"Service\":\"Amazon S3\",\"Event\":\"s3:TestEvent\"}");

        verifyNoInteractions(s3Client, forwardingService);
    }

    @Test
    @DisplayName("should_ignore_when_objectOutsideForwardingPrefixOrBucket")
    void should_ignore_when_objectOutsideForwardingPrefixOrBucket() throws Exception {
        listener.handleS3Notification(event(BUCKET, "emails/reply-1"));
        listener.handleS3Notification(event("someone-elses-bucket", "forwarding/x"));

        verifyNoInteractions(s3Client);
        verify(forwardingService, never()).forward(anyString(), any());
    }

    @Test
    @DisplayName("should_propagate_when_forwardingFails_soSqsRedelivers")
    void should_propagate_when_forwardingFails_soSqsRedelivers() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {1}));
        when(forwardingService.forward(eq("forwarding/x"), any())).thenThrow(new IllegalStateException("CUMS down"));

        assertThatThrownBy(() -> listener.handleS3Notification(event(BUCKET, "forwarding/x")))
                .isInstanceOf(IllegalStateException.class);
    }
}
