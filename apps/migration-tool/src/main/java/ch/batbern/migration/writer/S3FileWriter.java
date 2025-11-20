package ch.batbern.migration.writer;

import ch.batbern.migration.model.target.FileUploadDto;
import ch.batbern.migration.service.S3UploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Writes files to S3 and generates CDN URLs
 * AC13-14: Upload presentations and photos to S3
 * AC16: Generate CloudFront CDN URLs
 */
@Component
public class S3FileWriter implements ItemWriter<FileUploadDto> {

    private static final Logger log = LoggerFactory.getLogger(S3FileWriter.class);

    private final S3UploadService s3UploadService;

    public S3FileWriter(S3UploadService s3UploadService) {
        this.s3UploadService = s3UploadService;
    }

    @Override
    public void write(Chunk<? extends FileUploadDto> chunk) throws Exception {
        for (FileUploadDto uploadDto : chunk) {
            try {
                log.info("Uploading file: {} to S3 key: {}",
                    uploadDto.getFile().getName(), uploadDto.getS3Key());

                // Check if file already exists (idempotency)
                if (s3UploadService.fileExists(uploadDto.getS3Key())) {
                    log.info("File already exists in S3, skipping: {}", uploadDto.getS3Key());
                    String cdnUrl = s3UploadService.generateCdnUrl(uploadDto.getS3Key());
                    uploadDto.setCdnUrl(cdnUrl);
                    continue;
                }

                // Upload file to S3
                String cdnUrl = s3UploadService.uploadFile(
                    uploadDto.getFile(),
                    uploadDto.getS3Key(),
                    uploadDto.getContentType()
                );

                // Set CDN URL in DTO
                uploadDto.setCdnUrl(cdnUrl);

                log.info("Successfully uploaded file {} with CDN URL: {}",
                    uploadDto.getFile().getName(), cdnUrl);

            } catch (Exception e) {
                log.error("Failed to upload file: {}", uploadDto.getFile().getName(), e);
                throw e; // Let Spring Batch retry/skip policy handle
            }
        }
    }
}
