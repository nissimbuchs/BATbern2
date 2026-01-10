package ch.batbern.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClientBuilder;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * EventBridge configuration for AWS event publishing
 *
 * Note: This configuration is NOT active for 'local' or 'test' profiles.
 * Those profiles use mock EventBridge clients for development/testing.
 */
@Configuration
@org.springframework.context.annotation.Profile("!local & !test")
public class EventBridgeConfig {

    @Value("${aws.region:eu-central-1}")
    private String awsRegion;

    @Value("${aws.eventbridge.endpoint:#{null}}")
    private String eventBridgeEndpoint;

    @Bean
    @ConditionalOnMissingBean
    public EventBridgeAsyncClient eventBridgeAsyncClient() {
        // Configure bounded thread pool for EventBridge async operations
        // Prevents unbounded thread growth under high load
        ExecutorService executor = new ThreadPoolExecutor(
            10,  // corePoolSize - minimum threads always alive
            50,  // maximumPoolSize - max threads under high load
            60L, TimeUnit.SECONDS,  // keepAliveTime - idle thread timeout
            new LinkedBlockingQueue<>(100),  // Bounded queue - max 100 pending tasks
            new ThreadFactoryBuilder()
                .setNameFormat("eventbridge-async-%d")
                .setDaemon(true)
                .build(),
            new ThreadPoolExecutor.CallerRunsPolicy()  // Backpressure: caller executes when queue full
        );

        EventBridgeAsyncClientBuilder builder = EventBridgeAsyncClient.builder()
            .region(Region.of(awsRegion))
            .credentialsProvider(awsCredentialsProvider())
            .asyncConfiguration(b -> b.advancedOption(
                software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR,
                executor
            ));

        // For LocalStack testing
        if (eventBridgeEndpoint != null && !eventBridgeEndpoint.isEmpty()) {
            builder.endpointOverride(URI.create(eventBridgeEndpoint));
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper eventBridgeObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }
}