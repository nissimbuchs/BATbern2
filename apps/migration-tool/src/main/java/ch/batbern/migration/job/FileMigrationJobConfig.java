package ch.batbern.migration.job;

import ch.batbern.migration.model.legacy.LegacyFile;
import ch.batbern.migration.model.target.FileUploadDto;
import ch.batbern.migration.processor.FileUploadProcessor;
import ch.batbern.migration.reader.FileDirectoryReader;
import ch.batbern.migration.writer.S3FileWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch job configuration for file migration
 * AC13-16: Upload presentations, photos to S3 with CDN URLs
 */
@Configuration
public class FileMigrationJobConfig {

    @Value("${migration.source-data-path}")
    private String sourceDataPath;

    @Bean
    public Job fileMigrationJob(JobRepository jobRepository, Step fileUploadStep) {
        return new JobBuilder("fileMigrationJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(fileUploadStep)
            .build();
    }

    @Bean
    public Step fileUploadStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager,
                                ItemProcessor<LegacyFile, FileUploadDto> fileUploadProcessor,
                                ItemWriter<FileUploadDto> s3FileWriter) {
        return new StepBuilder("fileUploadStep", jobRepository)
            .<LegacyFile, FileUploadDto>chunk(10, transactionManager) // Smaller chunks for file uploads
            .reader(fileDirectoryReader())
            .processor(fileUploadProcessor)
            .writer(s3FileWriter)
            .faultTolerant()
            .skipLimit(5)
            .skip(Exception.class) // Skip individual file upload failures
            .build();
    }

    /**
     * Factory method for FileDirectoryReader
     * Creates new instance per job execution to avoid state leakage
     */
    @Bean
    public ItemReader<LegacyFile> fileDirectoryReader() {
        return new FileDirectoryReader(sourceDataPath);
    }
}
