package ch.batbern.partners.config;

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
 * Async configuration for partner-coordination-service.
 *
 * Story 8.3: Enables @Async for PartnerInviteEmailService (AC8 — 202 response).
 * Propagates SecurityContext to async threads so JWT token is available
 * in async email send calls.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-partner-");
        executor.setTaskDecorator(new ContextCopyingDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Propagates SecurityContext and RequestAttributes to async threads.
     */
    static class ContextCopyingDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            SecurityContext securityContext = SecurityContextHolder.getContext();
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

            return () -> {
                try {
                    SecurityContextHolder.setContext(securityContext);
                    if (requestAttributes != null) {
                        RequestContextHolder.setRequestAttributes(requestAttributes);
                    }
                    runnable.run();
                } finally {
                    SecurityContextHolder.clearContext();
                    RequestContextHolder.resetRequestAttributes();
                }
            };
        }
    }
}
