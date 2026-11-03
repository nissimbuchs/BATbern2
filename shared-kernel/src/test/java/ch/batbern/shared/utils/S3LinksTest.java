package ch.batbern.shared.utils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** #1187: one way to build presigned links. Signed offline with dummy credentials; nothing calls AWS. */
@DisplayName("S3Links (#1187)")
class S3LinksTest {

    private static S3Presigner presigner;

    @BeforeAll
    static void presigner() {
        presigner = S3Presigner.builder()
                .region(Region.EU_CENTRAL_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIDTEST", "secret")))
                .build();
    }

    @AfterAll
    static void close() {
        presigner.close();
    }

    @Test
    @DisplayName("upload: bucket, key, validity, and content type and length signed into the link")
    void upload() {
        String url = S3Links.uploadUrl(presigner, "bucket-a", "materials/temp/u1/file-u1.pdf", "application/pdf",
                1234L, Duration.ofMinutes(15));

        assertThat(url).startsWith("https://bucket-a.s3.eu-central-1.amazonaws.com/materials/temp/u1/file-u1.pdf?")
                .contains("X-Amz-Expires=900");
        String signed = URLDecoder.decode(url, StandardCharsets.UTF_8);
        assertThat(signed).contains("X-Amz-SignedHeaders=").contains("content-length").contains("content-type");
    }

    @Test
    @DisplayName("upload without a length: content type signed, length not")
    void uploadWithoutLength() {
        String signed = URLDecoder.decode(S3Links.uploadUrl(presigner, "b", "k.jpg", "image/jpeg", null,
                Duration.ofSeconds(900)), StandardCharsets.UTF_8);

        assertThat(signed).contains("X-Amz-Expires=900").contains("content-type").doesNotContain("content-length");
    }

    @Test
    @DisplayName("download: opens inline by default; as attachment with an RFC 6266 file name when asked")
    void download() {
        String inline = S3Links.downloadUrl(presigner, "b", "materials/x.pdf", Duration.ofHours(1));
        assertThat(inline).contains("X-Amz-Expires=3600").doesNotContain("response-content-disposition");

        String attachment = URLDecoder.decode(S3Links.downloadUrl(presigner, "b", "mail/a1", Duration.ofMinutes(5),
                "Präsentation final.pdf"), StandardCharsets.UTF_8);
        assertThat(attachment).contains("X-Amz-Expires=300")
                .contains("response-content-disposition=attachment; filename*=UTF-8''Pr%C3%A4sentation%20final.pdf");
    }
}
