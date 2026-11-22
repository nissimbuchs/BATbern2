package ch.batbern.agent.config;

import ch.batbern.agent.tools.OperationClassTable;
import ch.batbern.agent.tools.StandardTools;
import ch.batbern.agent.tools.ToolRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** The tool registry the agent loop offers the model (story 5): the shipped tools against the shipped table. */
@Configuration
public class ToolConfig {

    @Bean
    public ToolRegistry toolRegistry(Clock clock) {
        return new ToolRegistry(StandardTools.create(clock), OperationClassTable.standard());
    }
}
