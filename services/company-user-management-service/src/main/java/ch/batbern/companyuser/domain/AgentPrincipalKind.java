package ch.batbern.companyuser.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Who an agent run acts as (agent identity, story 5), decided by its {@link AgentGrantTrigger}, never
 * by the request body. Stored lowercase in {@code agent_grants.principal_kind} (V42).
 */
public enum AgentPrincipalKind {
    /** The organizer who wrote to the agent; EMS hands the mail over. */
    REQUESTER,
    /** The organizer signed in to the agent service. */
    ORGANIZER,
    /** The organizer delegate of the agent settings, for a linked run or a scheduler job. */
    ORGANIZER_DELEGATE;

    /** Java {@code UPPER_CASE} to the lowercase database value and back. */
    @Converter
    public static class DbConverter implements AttributeConverter<AgentPrincipalKind, String> {

        @Override
        public String convertToDatabaseColumn(AgentPrincipalKind attribute) {
            return attribute == null ? null : attribute.name().toLowerCase();
        }

        @Override
        public AgentPrincipalKind convertToEntityAttribute(String dbData) {
            return dbData == null ? null : AgentPrincipalKind.valueOf(dbData.toUpperCase());
        }
    }
}
