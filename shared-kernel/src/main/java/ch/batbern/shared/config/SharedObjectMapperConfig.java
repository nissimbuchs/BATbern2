package ch.batbern.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Jackson 2 {@link ObjectMapper} of every service that scans {@code ch.batbern.shared}.
 *
 * <p>Story 17.2 part B moved this bean here unchanged. Until then it was declared as
 * {@code eventBridgeObjectMapper} in the EventBridge configuration (and again in each service's
 * local and test AWS configuration). Because it is a plain {@code ObjectMapper} bean, Spring Boot's
 * Jackson 2 auto-configuration backs off, so it has always been the mapper of the HTTP message
 * converters and of every injected {@code ObjectMapper}: API responses are indented, and
 * {@code spring.jackson2.*} properties have no effect. Keeping its exact settings keeps the API
 * payloads byte-identical; changing them is a separate decision.
 */
@Configuration
public class SharedObjectMapperConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }
}
