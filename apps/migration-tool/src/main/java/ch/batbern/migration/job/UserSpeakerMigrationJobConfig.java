package ch.batbern.migration.job;

import ch.batbern.migration.model.legacy.LegacySpeaker;
import ch.batbern.migration.processor.UserSpeakerMappingProcessor;
import ch.batbern.migration.reader.SpeakerJsonReader;
import ch.batbern.migration.writer.UserSpeakerApiWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClientException;

/**
 * Spring Batch job configuration for User/Speaker Migration
 * Implements AC9-12: User + Speaker creation with ADR-004 compliance
 */
@Slf4j
@Configuration
public class UserSpeakerMigrationJobConfig {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private SpeakerJsonReader speakerReader;

    @Autowired
    private UserSpeakerMappingProcessor userSpeakerProcessor;

    @Autowired
    private UserSpeakerApiWriter userSpeakerWriter;

    @Value("${migration.batch.chunk-size:100}")
    private int chunkSize;

    @Bean
    public Job userSpeakerMigrationJob(Step userSpeakerMigrationStep) {
        return new JobBuilder("userSpeakerMigrationJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(userSpeakerMigrationStep)
            .build();
    }

    @Bean
    public Step userSpeakerMigrationStep() {
        return new StepBuilder("userSpeakerMigrationStep", jobRepository)
            .<LegacySpeaker, UserSpeakerMappingProcessor.UserSpeakerPair>chunk(chunkSize, transactionManager)
            .reader(speakerReader)
            .processor(userSpeakerProcessor)
            .writer(userSpeakerWriter)
            .faultTolerant()
            .skipLimit(10)
            .skip(IllegalArgumentException.class) // Skip name parsing errors
            .retryLimit(3)
            .retry(RestClientException.class) // Retry transient API failures
            .build();
    }
}
