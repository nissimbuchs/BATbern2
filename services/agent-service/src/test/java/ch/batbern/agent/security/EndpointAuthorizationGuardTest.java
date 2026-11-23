package ch.batbern.agent.security;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.EndpointAuthorizationGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every write endpoint declares who may call it ({@code @PreAuthorize}); neither gateway checks
 * roles (ADR-008). See {@link EndpointAuthorizationGuard}.
 */
@DisplayName("Write endpoints declare their authorization")
class EndpointAuthorizationGuardTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapping;

    @Test
    @DisplayName("should_enforcePreAuthorize_when_serviceStarts")
    void should_enforcePreAuthorize_when_serviceStarts() {
        assertThat(EndpointAuthorizationGuard.methodSecurityEnabled(context))
                .as("@EnableMethodSecurity missing: @PreAuthorize would be ignored")
                .isTrue();
    }

    @Test
    @DisplayName("should_declarePreAuthorize_when_endpointWrites")
    void should_declarePreAuthorize_when_endpointWrites() {
        assertThat(EndpointAuthorizationGuard.unguardedWriteEndpoints(mapping))
                .as("write endpoints without @PreAuthorize (add a role rule, isAuthenticated() or permitAll())")
                .isEmpty();
    }
}
