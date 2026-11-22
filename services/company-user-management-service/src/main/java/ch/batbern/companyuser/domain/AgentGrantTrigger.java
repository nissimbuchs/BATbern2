package ch.batbern.companyuser.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Optional;

/**
 * Why an agent run grant is requested (agent identity, story 5): the trigger names the caller that
 * may request it and decides the principal. Stored lowercase in {@code agent_grants.trigger_kind}
 * (V42).
 */
public enum AgentGrantTrigger {
    /** EMS hands an organizer's mail to the agent: EMS caller, principal the requester. */
    EMS_HANDOFF(AgentPrincipalKind.REQUESTER),
    /** An organizer signed in to the agent service: agent-service caller, principal that organizer. */
    ORGANIZER_LOGIN(AgentPrincipalKind.ORGANIZER),
    /** A run that follows an active requester run: agent-service caller, principal the delegate. */
    LINKED_RUN(AgentPrincipalKind.ORGANIZER_DELEGATE),
    /** A listed scheduler job: agent-service caller, principal the delegate. */
    SCHEDULER_JOB(AgentPrincipalKind.ORGANIZER_DELEGATE);

    private final AgentPrincipalKind principalKind;

    AgentGrantTrigger(AgentPrincipalKind principalKind) {
        this.principalKind = principalKind;
    }

    /** The principal kind this trigger grants. */
    public AgentPrincipalKind principalKind() {
        return principalKind;
    }

    /** The trigger named {@code value} exactly (API spelling), empty for anything else. */
    public static Optional<AgentGrantTrigger> fromApi(String value) {
        for (AgentGrantTrigger trigger : values()) {
            if (trigger.name().equals(value)) {
                return Optional.of(trigger);
            }
        }
        return Optional.empty();
    }

    /** Java {@code UPPER_CASE} to the lowercase database value and back. */
    @Converter
    public static class DbConverter implements AttributeConverter<AgentGrantTrigger, String> {

        @Override
        public String convertToDatabaseColumn(AgentGrantTrigger attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public AgentGrantTrigger convertToEntityAttribute(String dbData) {
            return dbData == null ? null : AgentGrantTrigger.valueOf(dbData.toUpperCase());
        }
    }
}
