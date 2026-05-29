package ch.batbern.events.client.impl;

import ch.batbern.events.dto.CompanyBasicDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link UserApiClientImpl#getCompanyDisplayName(String)} — per-slug lookup
 * against {@code GET /api/v1/companies/{slug}}. Returns nullable {@code String} (not
 * {@code Optional}) so Spring's {@code @Cacheable unless = "#result == null"} composes
 * cleanly; an Optional return value gets unwrapped by Spring before the SpEL fires,
 * which would NPE the unless expression on an empty Optional.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserApiClientImpl.getCompanyDisplayName(slug)")
class UserApiClientImplGetCompanyDisplayNameTest {

    private static final String BASE_URL = "http://cums";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CacheManager cacheManager;

    private UserApiClientImpl client;

    @BeforeEach
    void setUp() {
        client = new UserApiClientImpl(restTemplate, new ObjectMapper(), cacheManager);
        ReflectionTestUtils.setField(client, "userServiceBaseUrl", BASE_URL);
    }

    @Test
    @DisplayName("returns the company's displayName when CUMS supplies it")
    void should_returnDisplayName_when_present() {
        CompanyBasicDto company = new CompanyBasicDto();
        company.setName("mobiliar");
        company.setDisplayName("Die Mobiliar");
        when(restTemplate.exchange(
                eq(BASE_URL + "/api/v1/companies/mobiliar"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(CompanyBasicDto.class)))
                .thenReturn(ResponseEntity.ok(company));

        String result = client.getCompanyDisplayName("mobiliar");

        assertThat(result).isEqualTo("Die Mobiliar");
    }

    @Test
    @DisplayName("falls back to the company name when displayName is null or blank")
    void should_fallBackToName_when_displayNameMissing() {
        CompanyBasicDto company = new CompanyBasicDto();
        company.setName("adesso");
        company.setDisplayName(null);
        when(restTemplate.exchange(
                eq(BASE_URL + "/api/v1/companies/adesso"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(CompanyBasicDto.class)))
                .thenReturn(ResponseEntity.ok(company));

        String result = client.getCompanyDisplayName("adesso");

        assertThat(result).isEqualTo("adesso");
    }

    @Test
    @DisplayName("returns null when CUMS responds 404 (unknown slug)")
    void should_returnNull_when_notFound() {
        when(restTemplate.exchange(
                eq(BASE_URL + "/api/v1/companies/ghost"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(CompanyBasicDto.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND,
                        "Not Found", null, null, null));

        String result = client.getCompanyDisplayName("ghost");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("returns null when CUMS is degraded (network/server error)")
    void should_returnNull_when_cumsDegraded() {
        when(restTemplate.exchange(
                eq(BASE_URL + "/api/v1/companies/mobiliar"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(CompanyBasicDto.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        String result = client.getCompanyDisplayName("mobiliar");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("returns null without hitting CUMS for null or blank input")
    void should_returnNull_when_slugBlank() {
        assertThat(client.getCompanyDisplayName(null)).isNull();
        assertThat(client.getCompanyDisplayName("")).isNull();
        assertThat(client.getCompanyDisplayName("   ")).isNull();
        verify(restTemplate, never()).exchange(
                any(String.class), any(HttpMethod.class), any(HttpEntity.class),
                eq(CompanyBasicDto.class));
    }
}
