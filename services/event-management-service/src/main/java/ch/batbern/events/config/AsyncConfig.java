package ch.batbern.events.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration for non-blocking operations.
 *
 * Used for:
 * - Email sending
 * - Event publishing
 * - Background tasks
 * - Workflow event listeners (auto-transitions)
 *
 * Story 2.2a Task B12: Async email confirmation
 * Story 5.7 (BAT-11): Automatic workflow transitions via event listeners
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("Initialized async executor for email sending with core pool size: {}",
                executor.getCorePoolSize());

        return executor;
    }
}
