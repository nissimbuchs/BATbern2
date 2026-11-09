package ch.batbern.shared.test;

import ch.batbern.shared.test.contract.ResponseConformance;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Checks every response of the auto-configured {@code MockMvc} against the service's specs
 * ({@link ResponseConformance}, the response conformance gate). Imported once on
 * {@link AbstractIntegrationTest}, so every IT is covered; the gate is off unless the Gradle
 * script {@code gradle/response-conformance.gradle} configures it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ResponseConformanceSetup {

    @Bean
    MockMvcBuilderCustomizer responseConformanceMockMvcBuilderCustomizer() {
        return builder -> builder.alwaysDo(ResponseConformance::check);
    }
}
