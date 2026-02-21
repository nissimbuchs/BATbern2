package ch.batbern.shared.unit.utils;

import ch.batbern.shared.utils.CloudFrontUrlBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CloudFrontUrlBuilder")
class CloudFrontUrlBuilderTest {

    @Nested
    @DisplayName("Production (CloudFront)")
    class ProductionCloudFront {

        @Test
        void should_buildUrl_withoutBucketName() {
            String url = CloudFrontUrlBuilder.buildUrl(
                    "https://cdn.batbern.ch", "my-bucket", "materials/2026/file.pdf");

            assertThat(url).isEqualTo("https://cdn.batbern.ch/materials/2026/file.pdf");
        }

        @Test
        void should_buildUrl_when_domainIsNull() {
            String url = CloudFrontUrlBuilder.buildUrl(
                    null, "my-bucket", "materials/2026/file.pdf");

            assertThat(url).isEqualTo("/materials/2026/file.pdf");
        }
    }

    @Nested
    @DisplayName("Local Development (MinIO)")
    class LocalMinIO {

        @Test
        void should_includeBucketName_when_domainIsLocalhost() {
            String url = CloudFrontUrlBuilder.buildUrl(
                    "http://localhost:8450", "materials", "materials/2026/file.pdf");

            assertThat(url).isEqualTo("http://localhost:8450/materials/materials/2026/file.pdf");
        }

        @Test
        void should_includeBucketName_when_domainContainsLocalhost() {
            String url = CloudFrontUrlBuilder.buildUrl(
                    "http://localhost:9000", "batbern-dev-logos", "logos/company.png");

            assertThat(url).isEqualTo("http://localhost:9000/batbern-dev-logos/logos/company.png");
        }
    }
}
