package ch.batbern.migration.reader;

import ch.batbern.migration.model.legacy.LegacySession;
import ch.batbern.migration.model.legacy.LegacySpeaker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reads speakers from legacy sessions.json file
 * Extracts unique speakers from all sessions (deduplication by name)
 * Step-scoped to prevent state leakage between job executions
 */
@Slf4j
@Component
@StepScope
public class SpeakerJsonReader implements ItemReader<LegacySpeaker> {

    private Iterator<LegacySpeaker> speakerIterator;
    private boolean initialized = false;

    @Value("${migration.source-data-path}")
    private String sourceDataPath;

    @Override
    public LegacySpeaker read() throws Exception {
        if (!initialized) {
            initializeReader();
            initialized = true;
        }

        if (speakerIterator != null && speakerIterator.hasNext()) {
            LegacySpeaker speaker = speakerIterator.next();
            log.debug("Read speaker: {}", speaker.getName());
            return speaker;
        }

        return null; // Signal end of data
    }

    private void initializeReader() throws IOException {
        File sessionsFile = new File(sourceDataPath, "sessions.json");

        if (!sessionsFile.exists()) {
            throw new IOException("sessions.json not found at: " + sessionsFile.getAbsolutePath());
        }

        log.info("Reading speakers from: {}", sessionsFile.getAbsolutePath());

        ObjectMapper objectMapper = new ObjectMapper();
        List<LegacySession> sessions = objectMapper.readValue(
            sessionsFile,
            new TypeReference<List<LegacySession>>() {}
        );

        // Extract unique speakers from all sessions
        Map<String, LegacySpeaker> uniqueSpeakers = new LinkedHashMap<>();

        for (LegacySession session : sessions) {
            if (session.getReferenten() != null) {
                for (LegacySpeaker speaker : session.getReferenten()) {
                    // Deduplicate by name (case-insensitive)
                    String speakerKey = speaker.getName().toLowerCase().trim();

                    if (!uniqueSpeakers.containsKey(speakerKey)) {
                        uniqueSpeakers.put(speakerKey, speaker);
                    } else {
                        log.debug("Duplicate speaker found: {} (skipping)", speaker.getName());
                    }
                }
            }
        }

        log.info("Extracted {} unique speakers from {} sessions", uniqueSpeakers.size(), sessions.size());
        this.speakerIterator = uniqueSpeakers.values().iterator();
    }
}
