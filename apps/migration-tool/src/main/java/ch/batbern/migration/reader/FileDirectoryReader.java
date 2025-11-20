package ch.batbern.migration.reader;

import ch.batbern.migration.model.legacy.LegacyFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads files from legacy directory structure for migration
 * Scans event directories for presentations and photos
 *
 * IMPORTANT: NOT a @Component - instances created per job execution via factory method
 * to avoid state leakage between test runs.
 */
public class FileDirectoryReader implements ItemReader<LegacyFile> {

    private static final Logger log = LoggerFactory.getLogger(FileDirectoryReader.class);

    private final String sourceDataPath;
    private List<LegacyFile> files;
    private int currentIndex = 0;

    public FileDirectoryReader(String sourceDataPath) {
        this.sourceDataPath = sourceDataPath;
    }

    /**
     * Initialize file list by scanning directory structure
     */
    private void initialize() {
        if (files != null) {
            return; // Already initialized
        }

        files = new ArrayList<>();
        Path sourcePath = Paths.get(sourceDataPath);

        log.info("Scanning for files in: {}", sourcePath);

        try {
            // Scan for event directories (e.g., event-45, event-46)
            if (Files.exists(sourcePath)) {
                try (Stream<Path> eventDirs = Files.walk(sourcePath, 1)) {
                    eventDirs
                        .filter(Files::isDirectory)
                        .filter(dir -> dir.getFileName().toString().startsWith("event-"))
                        .forEach(this::scanEventDirectory);
                }
            }

            log.info("Found {} files to migrate", files.size());

        } catch (Exception e) {
            log.error("Error scanning source directory", e);
            throw new RuntimeException("Failed to scan source directory: " + sourceDataPath, e);
        }
    }

    /**
     * Scan a single event directory for files
     */
    private void scanEventDirectory(Path eventDir) {
        String eventNumber = extractEventNumber(eventDir);

        try (Stream<Path> fileStream = Files.walk(eventDir, 1)) {
            fileStream
                .filter(Files::isRegularFile)
                .forEach(filePath -> {
                    File file = filePath.toFile();
                    LegacyFile.FileType fileType = determineFileType(file);

                    if (fileType != null) {
                        LegacyFile legacyFile = new LegacyFile(file, eventNumber, fileType);
                        files.add(legacyFile);
                        log.debug("Found file: {} (type: {}, event: {})",
                            file.getName(), fileType, eventNumber);
                    }
                });

        } catch (Exception e) {
            log.error("Error scanning event directory: {}", eventDir, e);
        }
    }

    /**
     * Extract event number from directory name
     * Example: "event-45" -> "45"
     */
    private String extractEventNumber(Path eventDir) {
        String dirName = eventDir.getFileName().toString();
        return dirName.replaceFirst("event-", "");
    }

    /**
     * Determine file type based on extension
     */
    private LegacyFile.FileType determineFileType(File file) {
        String name = file.getName().toLowerCase();

        if (name.endsWith(".pdf")) {
            return LegacyFile.FileType.PRESENTATION;
        } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
            return LegacyFile.FileType.PHOTO;
        } else if (name.contains("logo")) {
            return LegacyFile.FileType.LOGO;
        }

        // Ignore other file types
        return null;
    }

    @Override
    public LegacyFile read() {
        if (files == null) {
            initialize();
        }

        if (currentIndex < files.size()) {
            return files.get(currentIndex++);
        }

        return null; // End of data
    }
}
