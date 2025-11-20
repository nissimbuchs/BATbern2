package ch.batbern.migration.job;

import ch.batbern.migration.model.legacy.LegacySession;
import ch.batbern.migration.model.target.SessionDto;
import ch.batbern.migration.processor.SessionMappingProcessor;
import ch.batbern.migration.reader.SessionJsonReader;
import ch.batbern.migration.writer.SessionApiWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Spring Batch Job Configuration for Session Migration
 *
 * Story: 3.2.1 - AC6, AC12: Session Migration + SessionUser Creation
 *
 * Dependencies:
 * - Event migration must be completed (for eventId lookup)
 * - User/Speaker migration must be completed (for userId lookup)
 */
@Configuration
public class SessionMigrationJobConfig {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private SessionMappingProcessor sessionProcessor;

    @Autowired
    private SessionApiWriter sessionWriter;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${migration.source-data-path}")
    private String sourceDataPath;

    @Bean
    public Job sessionMigrationJob(Step sessionStep) {
        return new JobBuilder("sessionMigrationJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(sessionStep)
                .build();
    }

    @Bean
    public Step sessionStep() {
        return new StepBuilder("sessionStep", jobRepository)
                .<LegacySession, SessionDto>chunk(100, transactionManager)  // AC3: Chunk size = 100
                .reader(sessionJsonReader())
                .processor(sessionProcessor)
                .writer(sessionWriter)
                .build();
    }

    /**
     * Factory method for SessionJsonReader
     * Creates new instance per job execution to avoid state leakage
     * IMPORTANT: Prototype scope to avoid reader state reuse between tests
     */
    @Bean
    @org.springframework.context.annotation.Scope("prototype")
    public SessionJsonReader sessionJsonReader() {
        return new SessionJsonReader(objectMapper, sourceDataPath);
    }
}
