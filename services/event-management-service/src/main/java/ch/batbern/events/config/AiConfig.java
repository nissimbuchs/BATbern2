package ch.batbern.events.config;

import lombok.Getter;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@ToString
public class AiConfig {

    @Value("${batbern.ai.enabled:false}")
    private boolean aiEnabled;

    @ToString.Exclude
    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;
}
