package ch.batbern.events.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * Puts the {@link CumsContractHarness} on the shared {@code RestTemplate} of every EMS integration
 * test context (Story 17.0b2). Like {@code TestAwsConfig}, it is picked up by the application's own
 * {@code @ComponentScan("ch.batbern.events")}, so every context gets it; the real
 * {@code UserApiClientImpl} then runs against the CUMS internal spec instead of a Mockito mock
 * (the former {@code TestUserApiClientConfig}). Tests autowire {@link CumsContractHarness} to
 * register answers.
 */
@TestConfiguration
public class CumsContractHarnessConfig {

    @Bean(destroyMethod = "close")
    public CumsContractHarness cumsContractHarness(RestTemplate restTemplate, CacheManager cacheManager,
            @Value("${user-service.base-url}") String cumsBaseUrl) {
        return new CumsContractHarness(restTemplate, cumsBaseUrl, cacheManager);
    }
}
