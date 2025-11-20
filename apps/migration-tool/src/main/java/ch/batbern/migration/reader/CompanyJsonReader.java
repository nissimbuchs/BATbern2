package ch.batbern.migration.reader;

import ch.batbern.migration.model.legacy.LegacyCompaniesData;
import ch.batbern.migration.model.legacy.LegacyCompany;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Company JSON Reader
 *
 * Reads company data from docs/migration/companies.json.
 * Implements Spring Batch ItemReader for chunk processing.
 *
 * IMPORTANT: NOT a @Component - instances created per job execution via factory method
 * to avoid state leakage between test runs.
 *
 * Story: 3.2.1 - Migration Tool Implementation, AC 17-20
 */
@Slf4j
public class CompanyJsonReader implements ItemReader<LegacyCompany> {

    private final ObjectMapper objectMapper;
    private final String sourceDataPath;
    private Iterator<LegacyCompany> companyIterator;
    private boolean initialized = false;

    public CompanyJsonReader(ObjectMapper objectMapper, String sourceDataPath) {
        this.objectMapper = objectMapper;
        this.sourceDataPath = sourceDataPath;
    }

    /**
     * Read next company from JSON file
     * Called by Spring Batch for each item in chunk
     */
    @Override
    public LegacyCompany read() throws Exception {
        if (!initialized) {
            initialize();
        }

        if (companyIterator != null && companyIterator.hasNext()) {
            return companyIterator.next();
        }

        return null; // Signal end of data
    }

    /**
     * Initialize reader by loading companies.json
     * Lazy initialization on first read() call
     */
    private void initialize() throws IOException {
        File companiesFile = new File(sourceDataPath, "companies.json");

        if (!companiesFile.exists()) {
            log.warn("Companies file not found: {}. Using empty list.", companiesFile.getAbsolutePath());
            companyIterator = List.<LegacyCompany>of().iterator();
            initialized = true;
            return;
        }

        log.info("Reading companies from: {}", companiesFile.getAbsolutePath());

        LegacyCompaniesData data = objectMapper.readValue(companiesFile, LegacyCompaniesData.class);

        log.info("Loaded {} companies from JSON (metadata: total={})",
            data.getCompanies().size(),
            data.getMetadata().getTotalCompanies());

        companyIterator = data.getCompanies().iterator();
        initialized = true;
    }
}
