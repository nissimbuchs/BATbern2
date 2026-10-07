package ch.batbern.events.client.impl;

import ch.batbern.events.dto.CompanyBasicDto;
import ch.batbern.events.dto.generated.users.PaginatedUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.shared.api.PaginationMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Role and company lists are read page by page within the users/companies API limit (max 100).
 *
 * <p>Regression: since PR #825 (2026-07-01) CUMS enforces {@code limit <= 100} from its OpenAPI
 * contract. getPartnerUsernames sent {@code limit=1000} and got a 400, so new events were created
 * without their partners enrolled (logged only as a warning). getAllCompanies sent
 * {@code limit=1000} the same way. getOrganizerUsernames sent no limit and silently received only
 * the first 20.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserApiClientImpl paged role and company lists")
class UserApiClientImplPagedListsTest {

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

    private static ResponseEntity<PaginatedUserResponse> userPage(int page, int totalPages, String... usernames) {
        PaginatedUserResponse body = new PaginatedUserResponse();
        body.setData(java.util.Arrays.stream(usernames).map(u -> new UserResponse().id(u)).toList());
        PaginationMetadata meta = new PaginationMetadata();
        meta.setPage(page);
        meta.setLimit(100);
        meta.setTotalPages(totalPages);
        meta.setHasNext(page < totalPages);
        body.setPagination(meta);
        return ResponseEntity.ok(body);
    }

    private static String param(URI uri, String name) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(name);
    }

    @Test
    @DisplayName("should_collectPartnersFromAllPages_when_moreThanOnePage")
    void should_collectPartnersFromAllPages_when_moreThanOnePage() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(PaginatedUserResponse.class)))
                .thenReturn(userPage(1, 2, "partner.a", "partner.b"))
                .thenReturn(userPage(2, 2, "partner.c"));

        List<String> usernames = client.getPartnerUsernames();

        assertThat(usernames).containsExactly("partner.a", "partner.b", "partner.c");
        ArgumentCaptor<URI> uris = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate, times(2)).exchange(uris.capture(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(PaginatedUserResponse.class));
        assertThat(uris.getAllValues()).allSatisfy(u -> {
            assertThat(Integer.parseInt(param(u, "limit"))).isLessThanOrEqualTo(100);
            assertThat(u.toString()).contains("PARTNER");
        });
        assertThat(uris.getAllValues()).extracting(u -> param(u, "page")).containsExactly("1", "2");
    }

    @Test
    @DisplayName("should_collectMoreThanTwentyOrganizers_when_listSpansPages")
    void should_collectMoreThanTwentyOrganizers_when_listSpansPages() {
        String[] first = IntStream.range(0, 100).mapToObj(i -> "org." + i).toArray(String[]::new);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(PaginatedUserResponse.class)))
                .thenReturn(userPage(1, 2, first))
                .thenReturn(userPage(2, 2, "org.100"));

        List<String> usernames = client.getOrganizerUsernames();

        assertThat(usernames).hasSize(101);
    }

    @Test
    @DisplayName("should_collectCompaniesFromAllPages_when_moreThanOnePage")
    void should_collectCompaniesFromAllPages_when_moreThanOnePage() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {"data":[{"name":"AcmeZH"},{"name":"GoogleZH"}],
                         "pagination":{"page":1,"limit":100,"totalPages":2,"hasNext":true}}"""))
                .thenReturn(ResponseEntity.ok("""
                        {"data":[{"name":"ElcaBE"}],
                         "pagination":{"page":2,"limit":100,"totalPages":2,"hasNext":false}}"""));

        List<CompanyBasicDto> companies = client.getAllCompanies();

        assertThat(companies).hasSize(3);
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(2)).exchange(urls.capture(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(String.class));
        assertThat(urls.getAllValues()).allSatisfy(u -> assertThat(u).contains("limit=100"));
        assertThat(urls.getAllValues().get(1)).contains("page=2");
    }
}
