package ch.batbern.migration.job;

import ch.batbern.migration.config.BatchConfiguration;
import ch.batbern.migration.listener.MigrationSkipListener;
import ch.batbern.migration.model.legacy.LegacyCompany;
import ch.batbern.migration.model.target.CompanyDto;
import ch.batbern.migration.processor.CompanyMappingProcessor;
import ch.batbern.migration.reader.CompanyJsonReader;
import ch.batbern.migration.writer.CompanyApiWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClientException;

/**
 * Company Migration Job Configuration
 *
 * Spring Batch job for migrating company data from legacy JSON to target API.
 *
 * Job Flow:
 * 1. Read: CompanyJsonReader reads from docs/migration/companies.json
 * 2. Process: CompanyMappingProcessor applies transformations (AC 17-20)
 * 3. Write: CompanyApiWriter POSTs to Company Management API
 *
 * Chunk-oriented processing: 100 records per chunk (configurable)
 * Fault tolerance: Skip validation errors, retry transient API failures
 *
 * Story: 3.2.1 - Migration Tool Implementation, AC 1-4, 17-20
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class CompanyMigrationJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CompanyMappingProcessor companyProcessor;
    private final CompanyApiWriter companyWriter;
    private final BatchConfiguration batchConfiguration;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final MigrationSkipListener<LegacyCompany, CompanyDto> skipListener;

    @Value("${migration.batch.skip-limit:10}")
    private int skipLimit;

    @Value("${migration.batch.retry-limit:3}")
    private int retryLimit;

    @Value("${migration.source-data-path}")
    private String sourceDataPath;

    /**
     * Company Migration Job Bean
     * AC 2: Create job for Company entity type
     */
    @Bean
    public Job companyMigrationJob() {
        return new JobBuilder("companyMigrationJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(companyMigrationStep())
            .build();
    }

    /**
     * Company Reader Bean - Step Scoped
     * Creates a new reader instance for each step execution to avoid state leakage
     * between job runs (critical for test isolation)
     */
    @Bean
    @org.springframework.context.annotation.Scope(value = "step", proxyMode = org.springframework.context.annotation.ScopedProxyMode.TARGET_CLASS)
    public CompanyJsonReader companyReader() {
        return new CompanyJsonReader(objectMapper, sourceDataPath);
    }

    /**
     * Company Migration Step
     * AC 3: Chunk-oriented processing (100 records per chunk)
     * AC 4: Can run in parallel with Event migration (no dependencies)
     */
    @Bean
    public Step companyMigrationStep() {
        return new StepBuilder("companyMigrationStep", jobRepository)
            .<LegacyCompany, CompanyDto>chunk(batchConfiguration.getChunkSize(), transactionManager)
            .reader(companyReader())
            .processor(companyProcessor)
            .writer(companyWriter)
            .faultTolerant()
            .skipLimit(skipLimit)
            .skip(IllegalArgumentException.class) // Skip validation errors
            .retryLimit(retryLimit)
            .retry(RestClientException.class) // Retry transient API failures
            .listener(skipListener) // Log skipped items to migration_errors
            .build();
    }
}
