package ch.batbern.migration.processor;

import ch.batbern.migration.model.legacy.LegacyFile;
import ch.batbern.migration.model.target.FileUploadDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Processes legacy files and generates S3 upload metadata
 * Generates S3 keys according to Story 3.1.2 patterns:
 * - Presentations: presentations/{eventNumber}/{filename}
 * - Photos: photos/events/{eventNumber}/{filename}
 * - Logos: logos/{companyName}/{filename}
 */
@Component
public class FileUploadProcessor implements ItemProcessor<LegacyFile, FileUploadDto> {

    private static final Logger log = LoggerFactory.getLogger(FileUploadProcessor.class);

    @Override
    public FileUploadDto process(LegacyFile legacyFile) throws Exception {
        log.debug("Processing file: {} (type: {})", legacyFile.getFile().getName(), legacyFile.getFileType());

        // Generate S3 key based on file type
        String s3Key = generateS3Key(legacyFile);

        // Determine content type
        String contentType = determineContentType(legacyFile);

        // Create upload DTO
        FileUploadDto uploadDto = new FileUploadDto(legacyFile.getFile(), s3Key, contentType);

        // Note: Thumbnail generation removed per user request
        // uploadDto.setGenerateThumbnail(shouldGenerateThumbnail(legacyFile));

        log.info("Mapped file {} to S3 key: {}", legacyFile.getFile().getName(), s3Key);

        return uploadDto;
    }

    /**
     * Generate S3 key based on file type and Story 3.1.2 patterns
     */
    private String generateS3Key(LegacyFile legacyFile) {
        String filename = legacyFile.getFile().getName();
        String eventNumber = legacyFile.getEventNumber();

        switch (legacyFile.getFileType()) {
            case PRESENTATION:
                // Pattern: presentations/{eventNumber}/{filename}
                return String.format("presentations/%s/%s", eventNumber, filename);

            case PHOTO:
                // Pattern: photos/events/{eventNumber}/{filename}
                return String.format("photos/events/%s/%s", eventNumber, filename);

            case LOGO:
                // Pattern: logos/{companyName}/{filename}
                // Note: Company name would need to be passed in context
                // For now, use a generic pattern
                return String.format("logos/companies/%s", filename);

            case CV:
                // Pattern: cvs/{speakerId}/{filename}
                return String.format("cvs/speakers/%s", filename);

            default:
                throw new IllegalArgumentException("Unknown file type: " + legacyFile.getFileType());
        }
    }

    /**
     * Determine MIME content type from file extension
     */
    private String determineContentType(LegacyFile legacyFile) {
        String extension = legacyFile.getExtension().toLowerCase();

        switch (extension) {
            case "pdf":
                return "application/pdf";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            default:
                return "application/octet-stream";
        }
    }
}
