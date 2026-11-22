package ch.batbern.companyuser.security;

import ch.batbern.companyuser.security.AgentCallerSecrets.Caller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent identity, story 5: the caller secrets that {@code /internal/agent-grants} and
 * {@code /internal/agent-eligibility} check. Pure logic, no context.
 */
@DisplayName("CUMS: agent caller secrets")
class AgentCallerSecretsTest {

    private static final String EMS = "test-only-ems-secret";
    private static final String AGENT_SERVICE = "test-only-agent-service-secret";

    private final AgentCallerSecrets secrets = new AgentCallerSecrets(EMS, AGENT_SERVICE);

    @Test
    @DisplayName("the right secret matches its caller")
    void should_match_when_rightSecret() {
        assertThat(secrets.matches(Caller.EMS, EMS)).isTrue();
        assertThat(secrets.matches(Caller.AGENT_SERVICE, AGENT_SERVICE)).isTrue();
    }

    @Test
    @DisplayName("a wrong, empty or missing secret matches no caller")
    void should_notMatch_when_wrongOrMissing() {
        assertThat(secrets.matches(Caller.EMS, EMS + "x")).isFalse();
        assertThat(secrets.matches(Caller.EMS, EMS.substring(1))).isFalse();
        assertThat(secrets.matches(Caller.EMS, "")).isFalse();
        assertThat(secrets.matches(Caller.EMS, null)).isFalse();
        assertThat(secrets.matches(Caller.AGENT_SERVICE, null)).isFalse();
    }

    @Test
    @DisplayName("one caller's secret does not authenticate the other")
    void should_notMatch_when_otherCallersSecret() {
        assertThat(secrets.matches(Caller.EMS, AGENT_SERVICE)).isFalse();
        assertThat(secrets.matches(Caller.AGENT_SERVICE, EMS)).isFalse();
    }

    @Test
    @DisplayName("a blank configured secret refuses everything, even a blank one presented")
    void should_refuseEverything_when_configuredSecretBlank() {
        AgentCallerSecrets unset = new AgentCallerSecrets("", "   ");

        assertThat(unset.matches(Caller.EMS, "")).isFalse();
        assertThat(unset.matches(Caller.EMS, null)).isFalse();
        assertThat(unset.matches(Caller.AGENT_SERVICE, "   ")).isFalse();
        assertThat(unset.matches(Caller.AGENT_SERVICE, AGENT_SERVICE)).isFalse();

        AgentCallerSecrets nulls = new AgentCallerSecrets(null, null);
        assertThat(nulls.matches(Caller.EMS, null)).isFalse();
        // positive control: the same lookup with a configured secret matches
        assertThat(secrets.matches(Caller.EMS, EMS)).isTrue();
    }
}
