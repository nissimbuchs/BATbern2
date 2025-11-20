package ch.batbern.migration.reader;

import ch.batbern.migration.model.legacy.LegacyEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Reads events from legacy topics.json file
 * Step-scoped to prevent state leakage between job executions
 */
@Slf4j
@Component
@StepScope
public class EventJsonReader implements ItemReader<LegacyEvent> {

    private Iterator<LegacyEvent> eventIterator;
    private boolean initialized = false;

    @Value("${migration.source-data-path}")
    private String sourceDataPath;

    @Override
    public LegacyEvent read() throws Exception {
        if (!initialized) {
            initializeReader();
            initialized = true;
        }

        if (eventIterator != null && eventIterator.hasNext()) {
            LegacyEvent event = eventIterator.next();
            log.debug("Read event: BAT {}", event.getBat());
            return event;
        }

        return null; // Signal end of data
    }

    private void initializeReader() throws IOException {
        File topicsFile = new File(sourceDataPath, "topics.json");

        if (!topicsFile.exists()) {
            throw new IOException("topics.json not found at: " + topicsFile.getAbsolutePath());
        }

        log.info("Reading events from: {}", topicsFile.getAbsolutePath());

        ObjectMapper objectMapper = new ObjectMapper();
        List<LegacyEvent> events = objectMapper.readValue(
            topicsFile,
            new TypeReference<List<LegacyEvent>>() {}
        );

        log.info("Loaded {} events from topics.json", events.size());
        this.eventIterator = events.iterator();
    }
}
