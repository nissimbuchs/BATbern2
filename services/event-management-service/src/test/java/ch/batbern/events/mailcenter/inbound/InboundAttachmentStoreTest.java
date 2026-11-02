package ch.batbern.events.mailcenter.inbound;

import ch.batbern.events.domain.MailMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Story 16.6 PR 2 prerequisite (owner decisions 2026-11-01 and 2026-11-02): inbound attachments up
 * to 25 MB per file are kept in the private inbound bucket under {@code mail-attachments/}; larger
 * ones keep only their name.
 */
@DisplayName("Story 16.6: inbound attachments kept up to the cap")
class InboundAttachmentStoreTest {

    private static final String BUCKET = "batbern-inbound-emails-test";
    private S3Client s3;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
    }

    private static MailMessage.Attachment meta(String name, long size) {
        return new MailMessage.Attachment(name, "application/pdf", size);
    }

    @Test
    @DisplayName("a file within the cap is uploaded and gets its key; a larger one keeps only its name")
    void should_storeWithinCap_and_skipLarger() {
        InboundAttachmentStore store = new InboundAttachmentStore(s3, BUCKET, 10);

        List<MailMessage.Attachment> result = store.store("in_abc",
                List.of(meta("slides.pdf", 8), meta("video.mp4", 11)), List.of(new byte[8], new byte[11]));

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3, times(1)).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo("mail-attachments/in_abc/0");
        assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(result.get(0).storageKey()).isEqualTo("mail-attachments/in_abc/0");
        assertThat(result.get(1).storageKey()).isNull();
        assertThat(result.get(1).name()).isEqualTo("video.mp4");
    }

    @Test
    @DisplayName("default cap is 25 MB")
    void defaultCap() {
        assertThat(InboundAttachmentStore.DEFAULT_MAX_BYTES).isEqualTo(25L * 1024 * 1024);
    }

    @Test
    @DisplayName("no bucket configured (local dev, tests): names only, no S3 call")
    void should_keepNamesOnly_when_noBucket() {
        InboundAttachmentStore store = new InboundAttachmentStore(s3, "", 10);
        List<MailMessage.Attachment> result = store.store("in_abc", List.of(meta("a.pdf", 1)),
                List.of(new byte[1]));
        assertThat(result.get(0).storageKey()).isNull();
        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("an S3 failure is thrown, so the caller's failure handling decides")
    void should_throw_when_uploadFails() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("denied").build());
        InboundAttachmentStore store = new InboundAttachmentStore(s3, BUCKET, 10);
        assertThatThrownBy(() -> store.store("in_abc", List.of(meta("a.pdf", 1)), List.of(new byte[1])))
                .isInstanceOf(S3Exception.class);
    }
}
