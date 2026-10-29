package ch.batbern.events.client.impl;

import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.dto.generated.users.PaginatedUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.shared.api.PaginationMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EMS reads users through the CUMS internal API ({@code /internal/users}) and never forwards the
 * end user's JWT there. The public {@code /api/v1/users} directory is organizer-only, and the
 * results are cached per username for every caller, so they must not depend on who triggered the
 * lookup.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserApiClientImpl reads users via the CUMS internal API")
class UserApiClientImplInternalApiTest {

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
        // A signed-in attendee triggered the lookup: their token must not reach CUMS.
        Jwt jwt = Jwt.withTokenValue("attendee-token").header("alg", "none")
                .claim("sub", "attendee").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private HttpHeaders sentHeaders(String url, Class<?> type) {
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(url), eq(HttpMethod.GET), entity.capture(), eq((Class) type));
        return entity.getValue().getHeaders();
    }

    @Test
    @DisplayName("getUserByUsername calls /internal/users/{username} without Authorization")
    void should_callInternalApiWithoutJwt_when_userLookedUp() {
        String url = BASE_URL + "/internal/users/john.doe";
        when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserResponse.class)))
                .thenReturn(ResponseEntity.ok(new UserResponse().id("john.doe").email("john@example.com")));

        UserResponse user = client.getUserByUsername("john.doe");

        assertThat(user.getEmail()).isEqualTo("john@example.com");
        assertThat(sentHeaders(url, UserResponse.class).containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    @DisplayName("getPreferredLanguage calls /internal/users/{username}?include=preferences without Authorization")
    void should_callInternalApiWithoutJwt_when_preferredLanguageLookedUp() {
        String url = BASE_URL + "/internal/users/john.doe?include=preferences";
        when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserResponse.class)))
                .thenReturn(ResponseEntity.ok(new UserResponse().id("john.doe")));

        client.getPreferredLanguage("john.doe");

        assertThat(sentHeaders(url, UserResponse.class).containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    @DisplayName("getQnaNotificationFrequency calls /internal/users/{username}?include=preferences without Authorization")
    void should_callInternalApiWithoutJwt_when_qnaFrequencyLookedUp() {
        String url = BASE_URL + "/internal/users/john.doe?include=preferences";
        when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserResponse.class)))
                .thenReturn(ResponseEntity.ok(new UserResponse().id("john.doe")));

        client.getQnaNotificationFrequency("john.doe");

        assertThat(sentHeaders(url, UserResponse.class).containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    @DisplayName("getOrganizerUsernames pages /internal/users?role=ORGANIZER without Authorization")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void should_callInternalApiWithoutJwt_when_organizersListed() {
        PaginationMetadata pagination = new PaginationMetadata();
        pagination.setHasNext(false);
        PaginatedUserResponse page = new PaginatedUserResponse();
        page.setData(List.of(new UserResponse().id("anna.organizer")));
        page.setPagination(pagination);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(PaginatedUserResponse.class))).thenReturn(ResponseEntity.ok(page));

        List<String> usernames = client.getOrganizerUsernames();

        assertThat(usernames).containsExactly("anna.organizer");
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(uri.capture(), eq(HttpMethod.GET), entity.capture(),
                eq(PaginatedUserResponse.class));
        assertThat(uri.getValue().toString())
                .isEqualTo(BASE_URL + "/internal/users?role=ORGANIZER&page=1&limit=100");
        assertThat(entity.getValue().getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    @DisplayName("getOrCreateUser posts to /internal/users/get-or-create without Authorization")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void should_callInternalApiWithoutJwt_when_userGetOrCreated() {
        String url = BASE_URL + "/internal/users/get-or-create";
        when(restTemplate.exchange(eq(url), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GetOrCreateUserResponse.class))).thenReturn(ResponseEntity.ok(new GetOrCreateUserResponse()));

        client.getOrCreateUser(new GetOrCreateUserRequest().email("new@example.com").firstName("N").lastName("P"));

        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(url), eq(HttpMethod.POST), entity.capture(),
                eq(GetOrCreateUserResponse.class));
        assertThat(entity.getValue().getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    /**
     * #1146: the old path {@code /api/v1/users/{username}/preferences} never existed in CUMS, so
     * every email notification threw before sending. The internal read carries the preferences.
     */
    @Test
    @DisplayName("getPreferences reads /internal/users/{username}?include=preferences without Authorization")
    void should_readPreferencesFromInternalApi_when_preferencesRequested() {
        String url = BASE_URL + "/internal/users/john.doe?include=preferences";
        ch.batbern.events.dto.generated.users.UserPreferences prefs =
                new ch.batbern.events.dto.generated.users.UserPreferences()
                        .emailNotifications(false)
                        .inAppNotifications(true)
                        .pushNotifications(false)
                        .notificationFrequency(ch.batbern.events.dto.generated.users.UserPreferences
                                .NotificationFrequencyEnum.DAILY_DIGEST)
                        .quietHoursStart("22:00")
                        .quietHoursEnd("07:00");
        when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserResponse.class)))
                .thenReturn(ResponseEntity.ok(new UserResponse().id("john.doe").preferences(prefs)));

        ch.batbern.events.notification.UserPreferences result = client.getPreferences("john.doe");

        assertThat(result.isEmailNotificationsEnabled()).isFalse();
        assertThat(result.isInAppNotificationsEnabled()).isTrue();
        assertThat(result.getNotificationFrequency()).isEqualTo("daily_digest");
        assertThat(result.getQuietHoursStart()).isEqualTo("22:00");
        assertThat(result.getQuietHoursEnd()).isEqualTo("07:00");
        assertThat(sentHeaders(url, UserResponse.class).containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    @DisplayName("getPreferences falls back to defaults (email on) when the user has no preferences row")
    void should_returnDefaults_when_userHasNoPreferences() {
        String url = BASE_URL + "/internal/users/jane.doe?include=preferences";
        when(restTemplate.exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserResponse.class)))
                .thenReturn(ResponseEntity.ok(new UserResponse().id("jane.doe")));

        ch.batbern.events.notification.UserPreferences result = client.getPreferences("jane.doe");

        assertThat(result.isEmailNotificationsEnabled()).isTrue();
        assertThat(result.getQuietHoursStart()).isNull();
    }
}
