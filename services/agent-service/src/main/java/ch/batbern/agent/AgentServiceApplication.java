package ch.batbern.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * agent-service (agentic interaction, epic agent runtime). Scans only {@code ch.batbern.agent}: it
 * needs none of the shared-kernel beans (no {@code EmailService}: the agent never sends mail itself,
 * AD-5) and uses shared-kernel classes directly ({@code BatbernJwtDecoder}, {@code ErrorResponse}).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AgentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentServiceApplication.class, args);
    }
}
