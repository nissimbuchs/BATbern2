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

    /**
     * Model for every chat call (description, abstract review, trending topics). Default: OpenAI's
     * alias for its latest chat model, so a retired model never breaks a call (owner, 2026-11-07).
     * The alias accepts only the default temperature, so requests send none.
     */
    @Value("${openai.chat-model:chat-latest}")
    private String chatModel;

    /** Model for theme images. Default: OpenAI's alias for its latest image model. */
    @Value("${openai.image-model:chatgpt-image-latest}")
    private String imageModel;
}
