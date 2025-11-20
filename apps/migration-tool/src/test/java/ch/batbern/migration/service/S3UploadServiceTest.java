package ch.batbern.migration.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for S3UploadService
 * Tests CDN URL generation without actual S3 calls
 * AC16: Verify CDN URL format https://cdn.batbern.ch/{s3-key}
 */
class S3UploadServiceTest {

    @Test
    void should_generateCdnUrl_when_s3KeyProvided() {
        // Given
        S3Client mockS3Client = mock(S3Client.class);
        S3UploadService service = new S3UploadService(mockS3Client);
        ReflectionTestUtils.setField(service, "cdnBaseUrl", "https://cdn.batbern.ch");

        // When
        String cdnUrl = service.generateCdnUrl("presentations/45/speaker1.pdf");

        // Then - AC16: CDN URL format
        assertThat(cdnUrl).isEqualTo("https://cdn.batbern.ch/presentations/45/speaker1.pdf");
    }

    @Test
    void should_generateCdnUrl_for_photos_when_s3KeyProvided() {
        // Given
        S3Client mockS3Client = mock(S3Client.class);
        S3UploadService service = new S3UploadService(mockS3Client);
        ReflectionTestUtils.setField(service, "cdnBaseUrl", "https://cdn.batbern.ch");

        // When
        String cdnUrl = service.generateCdnUrl("photos/events/46/gallery.jpg");

        // Then - AC16: CDN URL format for photos
        assertThat(cdnUrl).isEqualTo("https://cdn.batbern.ch/photos/events/46/gallery.jpg");
    }
}
