package ch.batbern.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The optional S3 endpoint override (pre-deploy gate, P5 spike: MinIO). Inactive when the endpoint
 * is blank, which is the production case: the builders are then left exactly as the caller made them.
 */
class S3EndpointOverrideTest {

    private static final S3EndpointOverride MINIO =
        new S3EndpointOverride("http://172.17.0.1:19000", "gate", "gate-minio");

    @Test
    @DisplayName("should_pointClientAtMinio_when_endpointSet")
    void should_pointClientAtMinio_when_endpointSet() {
        S3ClientBuilder builder = S3Client.builder().region(Region.EU_CENTRAL_1);

        try (S3Client client = MINIO.applyTo(builder).build()) {
            assertThat(MINIO.active()).isTrue();
            assertThat(client.serviceClientConfiguration().endpointOverride())
                .contains(URI.create("http://172.17.0.1:19000"));
            assertThat(client.serviceClientConfiguration().credentialsProvider())
                .isInstanceOf(StaticCredentialsProvider.class);
        }
    }

    @Test
    @DisplayName("should_presignPathStyleUrlOnMinio_when_endpointSet")
    void should_presignPathStyleUrlOnMinio_when_endpointSet() {
        try (S3Presigner presigner = MINIO.applyTo(S3Presigner.builder().region(Region.EU_CENTRAL_1)).build()) {
            URL url = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(PutObjectRequest.builder().bucket("bucket").key("a/b.png").build())
                .build()).url();

            assertThat(url.getHost()).isEqualTo("172.17.0.1");
            assertThat(url.getPort()).isEqualTo(19000);
            assertThat(url.getPath()).isEqualTo("/bucket/a/b.png");
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("should_leaveBuildersUntouched_when_endpointBlank")
    void should_leaveBuildersUntouched_when_endpointBlank(String endpoint) {
        S3EndpointOverride none = new S3EndpointOverride(endpoint, "gate", "gate-minio");
        S3ClientBuilder builder = S3Client.builder().region(Region.EU_CENTRAL_1)
            .credentialsProvider(AnonymousCredentialsProvider.create());

        try (S3Client client = none.applyTo(builder).build()) {
            assertThat(none.active()).isFalse();
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
            assertThat(client.serviceClientConfiguration().credentialsProvider())
                .isInstanceOf(AnonymousCredentialsProvider.class);
        }
    }
}
