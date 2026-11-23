package ch.batbern.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Names the state of the agent switch once at startup, so the log shows whether the service runs dark. */
@Component
class AgentSwitchLog {

    private static final Logger LOG = LoggerFactory.getLogger(AgentSwitchLog.class);

    private final AgentProperties properties;

    AgentSwitchLog(AgentProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    void logSwitch() {
        LOG.info("Agent switch agent.enabled={}{}", properties.enabled(),
                properties.enabled() ? "" : ": the service runs dark");
    }
}
