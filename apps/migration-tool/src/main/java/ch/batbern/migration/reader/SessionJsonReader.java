package ch.batbern.migration.reader;

import ch.batbern.migration.model.legacy.LegacySession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Reader for legacy sessions.json
 * Reads session data from BAT legacy system
 *
 * IMPORTANT: NOT a @Component - instances created per job execution via factory method
 * to avoid state leakage between test runs.
 *
 * Story: 3.2.1 - AC6: Session Migration
 */
@Slf4j
public class SessionJsonReader implements ItemReader<LegacySession> {

    private final ObjectMapper objectMapper;
    private final String sourceDataPath;

    private Iterator<LegacySession> sessionIterator;
    private boolean initialized = false;

    public SessionJsonReader(ObjectMapper objectMapper, String sourceDataPath) {
        this.objectMapper = objectMapper;
        this.sourceDataPath = sourceDataPath;
    }

    @Override
    public LegacySession read() {
        if (!initialized) {
            initialize();
        }

        if (sessionIterator != null && sessionIterator.hasNext()) {
            return sessionIterator.next();
        }

        return null;  // End of data
    }

    private void initialize() {
        try {
            String sessionsPath = sourceDataPath + "/sessions.json";
            log.info("Reading sessions from: {}", sessionsPath);

            File file = new File(sessionsPath);

            if (!file.exists()) {
                log.error("Sessions file not found: {}", sessionsPath);
                throw new IllegalStateException("Sessions file not found: " + sessionsPath);
            }

            List<LegacySession> sessions = objectMapper.readValue(file, new TypeReference<>() {});
            sessionIterator = sessions.iterator();
            initialized = true;

            log.info("Loaded {} sessions from legacy data", sessions.size());

        } catch (IOException e) {
            log.error("Failed to read sessions.json", e);
            throw new RuntimeException("Failed to read sessions.json", e);
        }
    }
}
