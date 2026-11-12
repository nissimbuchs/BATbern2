package ch.batbern.companyuser.config;

import ch.batbern.companyuser.domain.CompanyUserUpload;
import ch.batbern.companyuser.repository.CompanyUserUploadRepository;
import ch.batbern.shared.upload.UploadPurposeHandler;
import ch.batbern.shared.upload.UploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Clock;
import java.util.List;

/** The shared upload mechanics on this service's table and purposes (Story 17.11). */
@Configuration
public class UploadConfig {

    @Bean
    public UploadService<CompanyUserUpload> uploadService(CompanyUserUploadRepository repository,
            List<UploadPurposeHandler> handlers, S3Client s3Client, S3Presigner s3Presigner,
            @Value("${aws.s3.bucket-name:batbern-development-company-logos}") String bucket,
            @Value("${aws.cloudfront.domain:https://cdn.batbern.ch}") String cdnDomain) {
        return new UploadService<>(repository, CompanyUserUpload::new, handlers, s3Client, s3Presigner,
                new UploadService.UploadStorage(bucket, cdnDomain), Clock.systemUTC());
    }
}
