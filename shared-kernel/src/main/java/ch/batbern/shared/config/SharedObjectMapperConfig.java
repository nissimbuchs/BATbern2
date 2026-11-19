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
 * <p>Because it is a plain {@code ObjectMapper} bean, Spring Boot's Jackson 2 auto-configuration
 * backs off, so it is the mapper of the HTTP message converters and of every injected
 * {@code ObjectMapper}: API responses are indented, and {@code spring.jackson2.*} properties have
 * no effect. The owner kept it as is (#1254); its settings are pinned by a test, so the API payloads
 * stay byte-identical.
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
