package ch.batbern.migration.job;

import ch.batbern.migration.model.legacy.LegacyEvent;
import ch.batbern.migration.model.target.EventDto;
import ch.batbern.migration.processor.EventMappingProcessor;
import ch.batbern.migration.reader.EventJsonReader;
import ch.batbern.migration.writer.EventApiWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClientException;

/**
 * Spring Batch job configuration for Event Migration
 * Implements AC5-8: Event migration with German date parsing
 */
@Slf4j
@Configuration
public class EventMigrationJobConfig {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EventJsonReader eventReader;

    @Autowired
    private EventMappingProcessor eventProcessor;

    @Autowired
    private EventApiWriter eventWriter;

    @Value("${migration.batch.chunk-size:100}")
    private int chunkSize;

    @Bean
    public Job eventMigrationJob(Step eventMigrationStep) {
        return new JobBuilder("eventMigrationJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(eventMigrationStep)
            .build();
    }

    @Bean
    public Step eventMigrationStep() {
        return new StepBuilder("eventMigrationStep", jobRepository)
            .<LegacyEvent, EventDto>chunk(chunkSize, transactionManager)
            .reader(eventReader)
            .processor(eventProcessor)
            .writer(eventWriter)
            .faultTolerant()
            .skipLimit(10)
            .skip(IllegalArgumentException.class) // Skip date parsing errors
            .retryLimit(3)
            .retry(RestClientException.class) // Retry transient API failures
            .build();
    }
}
