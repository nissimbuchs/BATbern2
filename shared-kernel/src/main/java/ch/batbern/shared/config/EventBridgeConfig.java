package ch.batbern.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

@Configuration
public class EventBridgeConfig {

    @Value("${aws.region:eu-central-1}")
    private String awsRegion;

    @Value("${aws.eventbridge.endpoint:#{null}}")
    private String eventBridgeEndpoint;

    @Bean
    @ConditionalOnMissingBean
    public EventBridgeAsyncClient eventBridgeAsyncClient() {
        EventBridgeAsyncClientBuilder builder = EventBridgeAsyncClient.builder()
            .region(Region.of(awsRegion))
            .credentialsProvider(awsCredentialsProvider());

        // For LocalStack testing
        if (eventBridgeEndpoint != null && !eventBridgeEndpoint.isEmpty()) {
            builder.endpointOverride(URI.create(eventBridgeEndpoint));
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.create();
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