package ch.batbern.companyuser.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Story 12.12: infrastructure beans for the one-time federated avatar import.
 *
 * <p>Both beans use {@code @ConditionalOnMissingBean} so {@code TestAwsConfig} can supply
 * a synchronous executor + mock HTTP client for deterministic integration tests — the same
 * override pattern {@code AwsConfig} uses for its S3/Cognito clients.
 */
@Configuration
public class AvatarImportConfig {

    /**
     * Dedicated small executor so avatar fetches never run on (or block) request threads
     * (AC3 latency budget). Tiny pool: each user triggers at most ONE import, ever.
     */
    @Bean(name = "avatarImportExecutor")
    @ConditionalOnMissingBean(name = "avatarImportExecutor")
    public ThreadPoolTaskExecutor avatarImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("avatar-import-");
        return executor;
    }

    /**
     * HTTP client for fetching the Google avatar (timeouts mirror the admin
     * upload-from-url endpoint in {@code UserController}).
     */
    @Bean(name = "avatarFetchHttpClient")
    @ConditionalOnMissingBean(name = "avatarFetchHttpClient")
    public HttpClient avatarFetchHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
