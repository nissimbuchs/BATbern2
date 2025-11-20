package ch.batbern.migration.model.legacy;

import java.io.File;

/**
 * Represents a file to be migrated (presentation, photo, logo, CV)
 * Source: File system scan of apps/BATspa-old/src/assets/
 */
public class LegacyFile {
    private File file;
    private String eventNumber;  // Extracted from directory name (e.g., "event-45" → "45")
    private FileType fileType;   // Presentation, Photo, Logo, CV

    public enum FileType {
        PRESENTATION,  // PDFs in event directories
        PHOTO,         // JPG/PNG in event directories
        LOGO,          // Company logos
        CV             // Speaker CVs
    }

    public LegacyFile() {
    }

    public LegacyFile(File file, String eventNumber, FileType fileType) {
        this.file = file;
        this.eventNumber = eventNumber;
        this.fileType = fileType;
    }

    // Getters and setters
    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getEventNumber() {
        return eventNumber;
    }

    public void setEventNumber(String eventNumber) {
        this.eventNumber = eventNumber;
    }

    public FileType getFileType() {
        return fileType;
    }

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    /**
     * Get file extension (e.g., "pdf", "jpg", "png")
     */
    public String getExtension() {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot + 1).toLowerCase() : "";
    }

    /**
     * Check if file is a PDF
     */
    public boolean isPdf() {
        return "pdf".equalsIgnoreCase(getExtension());
    }

    /**
     * Check if file is an image (JPG, PNG, JPEG)
     */
    public boolean isImage() {
        String ext = getExtension();
        return "jpg".equalsIgnoreCase(ext) ||
               "jpeg".equalsIgnoreCase(ext) ||
               "png".equalsIgnoreCase(ext);
    }

    @Override
    public String toString() {
        return "LegacyFile{" +
                "file=" + file.getName() +
                ", eventNumber='" + eventNumber + '\'' +
                ", fileType=" + fileType +
                '}';
    }
}
