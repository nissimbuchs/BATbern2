package ch.batbern.migration.model.target;

import java.io.File;

/**
 * DTO for file upload to S3 with metadata
 */
public class FileUploadDto {
    private File file;
    private String s3Key;           // Full S3 key path (e.g., "presentations/45/speaker1.pdf")
    private String cdnUrl;          // CloudFront URL (e.g., "https://cdn.batbern.ch/presentations/45/speaker1.pdf")
    private String contentType;     // MIME type (e.g., "application/pdf", "image/jpeg")
    private boolean generateThumbnail;  // Whether to generate thumbnail
    private File thumbnail;         // Thumbnail file (if generated)
    private String thumbnailS3Key;  // S3 key for thumbnail
    private String thumbnailCdnUrl; // CDN URL for thumbnail

    public FileUploadDto() {
    }

    public FileUploadDto(File file, String s3Key, String contentType) {
        this.file = file;
        this.s3Key = s3Key;
        this.contentType = contentType;
    }

    // Getters and setters
    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public String getCdnUrl() {
        return cdnUrl;
    }

    public void setCdnUrl(String cdnUrl) {
        this.cdnUrl = cdnUrl;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public boolean isGenerateThumbnail() {
        return generateThumbnail;
    }

    public void setGenerateThumbnail(boolean generateThumbnail) {
        this.generateThumbnail = generateThumbnail;
    }

    public File getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(File thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getThumbnailS3Key() {
        return thumbnailS3Key;
    }

    public void setThumbnailS3Key(String thumbnailS3Key) {
        this.thumbnailS3Key = thumbnailS3Key;
    }

    public String getThumbnailCdnUrl() {
        return thumbnailCdnUrl;
    }

    public void setThumbnailCdnUrl(String thumbnailCdnUrl) {
        this.thumbnailCdnUrl = thumbnailCdnUrl;
    }

    /**
     * Generate CDN URL from S3 key
     */
    public void generateCdnUrl() {
        this.cdnUrl = "https://cdn.batbern.ch/" + s3Key;
    }

    /**
     * Generate thumbnail CDN URL from thumbnail S3 key
     */
    public void generateThumbnailCdnUrl() {
        if (thumbnailS3Key != null) {
            this.thumbnailCdnUrl = "https://cdn.batbern.ch/" + thumbnailS3Key;
        }
    }

    @Override
    public String toString() {
        return "FileUploadDto{" +
                "file=" + (file != null ? file.getName() : null) +
                ", s3Key='" + s3Key + '\'' +
                ", cdnUrl='" + cdnUrl + '\'' +
                ", generateThumbnail=" + generateThumbnail +
                '}';
    }
}
