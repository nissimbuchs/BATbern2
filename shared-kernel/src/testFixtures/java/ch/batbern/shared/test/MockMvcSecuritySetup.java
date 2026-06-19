package ch.batbern.shared.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

/**
 * Re-applies Spring Security to the auto-configured {@code MockMvc}.
 *
 * <p>Spring Boot 4 removed the auto-configuration that used to apply
 * {@link SecurityMockMvcConfigurers#springSecurity()} to the {@code @AutoConfigureMockMvc}
 * builder (the old {@code MockMvcSecurityConfiguration} no longer ships in
 * {@code spring-boot-webmvc-test}). Without it, the {@code SecurityContext} established by
 * {@code @WithMockUser} never reaches the controller — the security filter chain replaces it
 * with an anonymous context, so every {@code @PreAuthorize} check denies and authenticated
 * requests return 403 regardless of role.
 *
 * <p>{@code MockMvcAutoConfiguration} still applies every {@link MockMvcBuilderCustomizer} bean
 * in the context, so registering one that calls {@code apply(springSecurity())} restores the
 * pre-SB4 behaviour. Imported once on {@link AbstractIntegrationTest} so all integration tests
 * inherit it (Spring processes {@code @Import} up the test class hierarchy).
 */
@TestConfiguration(proxyBeanMethods = false)
public class MockMvcSecuritySetup {

    @Bean
    MockMvcBuilderCustomizer springSecurityMockMvcBuilderCustomizer() {
        return builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity());
    }
}
