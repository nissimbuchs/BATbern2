package ch.batbern.events.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Hibernate Configuration for PostgreSQL Full-Text Search
 * Story 4.2: Historical Event Archive Browsing
 *
 * Registers custom PostgreSQL functions for full-text search:
 * - ts_match: Wrapper for the @@ operator (title_vector @@ to_tsquery(...))
 */
public class HibernateConfig implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        // Register custom functions for PostgreSQL full-text search on specific columns
        // Usage: ts_match_title(to_tsquery('german', ?))
        // Generates SQL: title_vector @@ ?1 (for events table context)
        functionContributions.getFunctionRegistry().registerPattern(
            "ts_match_title",
            "(title_vector @@ ?1)",
            functionContributions.getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.BOOLEAN)
        );

        // Usage: ts_match_description(to_tsquery('german', ?))
        // Generates SQL: description_vector @@ ?1 (for events table context)
        functionContributions.getFunctionRegistry().registerPattern(
            "ts_match_description",
            "(description_vector @@ ?1)",
            functionContributions.getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.BOOLEAN)
        );

        // Usage: ts_match_session_title(to_tsquery('german', ?))
        // Generates SQL: title_vector @@ ?1 (for sessions table context)
        functionContributions.getFunctionRegistry().registerPattern(
            "ts_match_session_title",
            "(title_vector @@ ?1)",
            functionContributions.getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.BOOLEAN)
        );

        // Usage: ts_match_session_description(to_tsquery('german', ?))
        // Generates SQL: description_vector @@ ?1 (for sessions table context)
        functionContributions.getFunctionRegistry().registerPattern(
            "ts_match_session_description",
            "(description_vector @@ ?1)",
            functionContributions.getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.BOOLEAN)
        );
    }
}
