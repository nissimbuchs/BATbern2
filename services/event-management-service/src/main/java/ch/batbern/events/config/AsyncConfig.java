package ch.batbern.events.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.concurrent.Executor;

/**
 * Async Configuration - SecurityContext Propagation
 * Story BAT-7: Notifications API Consolidation
 *
 * Configures Spring's async executor to propagate SecurityContext to async threads.
 * This enables JWT token propagation for service-to-service communication in async event listeners.
 *
 * Without this configuration:
 * - @Async methods run in separate threads without SecurityContext
 * - JWT token extraction fails (SecurityContextHolder.getContext() returns empty)
 * - Service-to-service HTTP calls get 401 Unauthorized
 *
 * With this configuration:
 * - SecurityContext is copied from parent thread to async thread
 * - JWT token is available in async methods
 * - Service-to-service authentication works correctly
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Configure async executor with SecurityContext propagation
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setTaskDecorator(new ContextCopyingDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Task Decorator that propagates SecurityContext and RequestAttributes to async threads
     */
    static class ContextCopyingDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            // Capture context from parent thread
            SecurityContext securityContext = SecurityContextHolder.getContext();
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

            return () -> {
                try {
                    // Set context in async thread
                    SecurityContextHolder.setContext(securityContext);
                    if (requestAttributes != null) {
                        RequestContextHolder.setRequestAttributes(requestAttributes);
                    }

                    // Execute async task
                    runnable.run();

                } finally {
                    // Clean up thread-local variables
                    SecurityContextHolder.clearContext();
                    RequestContextHolder.resetRequestAttributes();
                }
            };
        }
    }
}
