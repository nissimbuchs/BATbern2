package ch.batbern.migration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Spring Batch Configuration
 *
 * Configures thread pool for parallel job execution and chunk processing.
 * Story: 3.2.1 - Migration Tool Implementation, AC 1-4
 */
@Configuration
public class BatchConfiguration {

    @Value("${migration.batch.thread-pool-size:10}")
    private int threadPoolSize;

    @Value("${migration.batch.chunk-size:100}")
    private int chunkSize;

    /**
     * Task executor for parallel step execution
     * AC4: Parallel execution for independent entities (Company, Event)
     */
    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadPoolSize);
        executor.setMaxPoolSize(threadPoolSize * 2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("migration-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Get configured chunk size
     * AC3: Chunk-oriented processing (100 records per chunk by default)
     */
    public int getChunkSize() {
        return chunkSize;
    }
}
