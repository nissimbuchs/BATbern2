package ch.batbern.events.security;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.EndpointAuthorizationGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every write endpoint declares who may call it ({@code @PreAuthorize}). Neither gateway checks
 * roles (ADR-008), so a missing annotation means "any signed-in account". See
 * {@link EndpointAuthorizationGuard}.
 */
@SpringBootTest
@Import({TestSecurityConfig.class, TestAwsConfig.class})
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
