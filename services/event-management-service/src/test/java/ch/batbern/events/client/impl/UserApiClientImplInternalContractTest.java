package ch.batbern.events.client.impl;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.config.RestClientConfig;
import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.exception.UserServiceException;
import ch.batbern.events.notification.UserPreferences;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

/**
 * Contract test of every {@code /internal} call of {@link UserApiClientImpl} against
 * {@code docs/api/internal/cums-internal.openapi.yml} (Story 17.0b2): the real client on the real
 * {@code RestTemplate} bean, each request validated against the spec, answers from its examples.
 * Replaces the {@code @Mock RestTemplate} tests {@code UserApiClientImplInternalApiTest},
 * {@code UserApiClientImplGetOrCreateUserTest} and {@code UserApiClientImplPagedListsTest}.
 *
 * <p>The internal API is VPC-only and carries no JWT: a signed-in user triggered every lookup
 * here, and their token must never reach CUMS (the results are cached for every caller).
 */
@SpringBootTest(classes = {UserApiClientImpl.class, RestClientConfig.class, CacheConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "user-service.base-url=http://cums.test")
@ImportAutoConfiguration({
    RestTemplateAutoConfiguration.class,
    Jackson2AutoConfiguration.class,
    HttpMessageConvertersAutoConfiguration.class
})
@DisplayName("UserApiClientImpl /internal calls honour the CUMS internal contract")
class UserApiClientImplInternalContractTest {

    private static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";

    @Autowired
    private UserApiClient client;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CacheManager cacheManager;

    private ContractServer cums;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(n -> Objects.requireNonNull(cacheManager.getCache(n)).clear());
        Jwt jwt = Jwt.withTokenValue("attendee-token").header("alg", "none").claim("sub", "attendee").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        cums = ContractServer.bindTo(restTemplate, CUMS_INTERNAL);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cums.close();
    }

    private static void noJwt(ClientHttpRequest request) {
        assertThat(request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    // ------------------------------------------------------------------ GET /internal/users/{username}

    @Test
    void should_readUser_withoutJwt() {
        cums.expect(GET, "/internal/users/{username}").andExpect(UserApiClientImplInternalContractTest::noJwt)
                .respondWithExample("user");

        UserResponse user = client.getUserByUsername("anna.meier");

        assertThat(user.getEmail()).isEqualTo("anna.meier@example.com");
        assertThat(user.getFirstName()).isEqualTo("Anna");
        cums.verify();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
        "jean-luc.o'brien | /internal/users/jean-luc.o%27brien",
        "anna meier       | /internal/users/anna%20meier",
        "a/b?c#d%         | /internal/users/a%2Fb%3Fc%23d%25",
    })
    @DisplayName("a username is one strictly encoded path segment")
    void should_encodeUsername_asOnePathSegment(String username, String rawPath) {
        cums.expect(GET, "/internal/users/{username}")
                .andExpect(r -> assertThat(r.getURI().getRawPath()).isEqualTo(rawPath))
                .respondWithExample("user");

        client.getUserByUsername(username);

        cums.verify();
    }

    @Test
    void should_throwUserNotFound_when_cumsAnswers404() {
        cums.expect(GET, "/internal/users/{username}").respondWithExample(HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> client.getUserByUsername("ghost.user")).isInstanceOf(UserNotFoundException.class);
        cums.verify();
    }

    @Test
    void should_notCallCums_when_usernameEmpty() {
        assertThatThrownBy(() -> client.getUserByUsername("")).isInstanceOf(UserNotFoundException.class);
        cums.verify();
    }

    @Test
    void should_validateUserExists_trueAndFalse() {
        cums.expect(GET, "/internal/users/anna.meier").respondWithExample("user");
        cums.expect(GET, "/internal/users/ghost.user").respondWithExample(HttpStatus.NOT_FOUND);

        assertThat(client.validateUserExists("anna.meier")).isTrue();
        assertThat(client.validateUserExists("ghost.user")).isFalse();
        cums.verify();
    }

    @Test
    @DisplayName("#1146: preferences come from ?include=preferences, without JWT")
    void should_readPreferences_fromInclude() {
        cums.expect(GET, "/internal/users/{username}").withQueryParam("include", "preferences")
                .andExpect(UserApiClientImplInternalContractTest::noJwt)
                .respondWithExample("withPreferences");

        UserPreferences prefs = client.getPreferences("anna.meier");

        assertThat(prefs.isEmailNotificationsEnabled()).isTrue();
        assertThat(prefs.isPushNotificationsEnabled()).isFalse();
        assertThat(prefs.getNotificationFrequency()).isEqualTo("immediate");
        cums.verify();
    }

    @Test
    void should_fallBackToDefaultPreferences_when_userHasNone() {
        cums.expect(GET, "/internal/users/{username}").withQueryParam("include", "preferences")
                .respondWithExample("user");

        UserPreferences prefs = client.getPreferences("anna.meier");

        assertThat(prefs.isEmailNotificationsEnabled()).isTrue();
        assertThat(prefs.getQuietHoursStart()).isNull();
        cums.verify();
    }

    @Test
    void should_readPreferredLanguageAndQnaFrequency() {
        cums.expect(GET, "/internal/users/{username}").withQueryParam("include", "preferences")
                .times(ExpectedCount.twice())
                .respondWithExample("withPreferences");

        assertThat(client.getPreferredLanguage("anna.meier")).isEqualTo("en");
        assertThat(client.getQnaNotificationFrequency("anna.meier")).isEqualTo("daily");
        cums.verify();
    }

    @Test
    void should_returnNullLanguage_when_userUnknown() {
        cums.expect(GET, "/internal/users/{username}").respondWithExample(HttpStatus.NOT_FOUND);

        assertThat(client.getPreferredLanguage("ghost.user")).isNull();
        cums.verify();
    }

    // ------------------------------------------------------------------ GET /internal/users (paged)

    private static final String PAGE_ONE_OF_TWO = """
            {"data": [{"id": "anna.organizer", "email": "anna@example.com", "firstName": "Anna",
                       "lastName": "Organizer", "roles": ["ORGANIZER"], "createdAt": "2025-01-15T10:00:00Z",
                       "updatedAt": "2025-01-15T10:00:00Z"}],
             "pagination": {"page": 1, "limit": 100, "totalItems": 101, "totalPages": 2,
                            "hasNext": true, "hasPrev": false}}""";

    @Test
    @DisplayName("organizers: every page is read, within limit 100, without JWT")
    void should_readOrganizers_fromAllPages() {
        cums.expect(GET, "/internal/users").withQueryParam("role", "ORGANIZER").withQueryParam("page", "1")
                .withQueryParam("limit", "100").andExpect(UserApiClientImplInternalContractTest::noJwt)
                .respondWith(HttpStatus.OK, PAGE_ONE_OF_TWO);
        cums.expect(GET, "/internal/users").withQueryParam("role", "ORGANIZER").withQueryParam("page", "2")
                .respondWithExample();

        assertThat(client.getOrganizerUsernames()).containsExactly("anna.organizer", "anna.meier");
        cums.verify();
    }

    @Test
    void should_readPartners_fromAllPages() {
        cums.expect(GET, "/internal/users").withQueryParam("role", "PARTNER").withQueryParam("page", "1")
                .respondWith(HttpStatus.OK, PAGE_ONE_OF_TWO);
        cums.expect(GET, "/internal/users").withQueryParam("role", "PARTNER").withQueryParam("page", "2")
                .respondWithExample();

        assertThat(client.getPartnerUsernames()).containsExactly("anna.organizer", "anna.meier");
        cums.verify();
    }

    @Test
    @DisplayName("a company name with '+' and '&' stays one query value")
    void should_encodeCompanyStrictly() {
        cums.expect(GET, "/internal/users").withQueryParam("company", "Meier+Partner & Co")
                .withQueryParam("role", "PARTNER")
                .andExpect(r -> assertThat(r.getURI().getRawQuery()).contains("company=Meier%2BPartner%20%26%20Co"))
                .respondWithExample();

        List<UserResponse> users = client.getUsersByCompany("Meier+Partner & Co", "PARTNER");

        assertThat(users).extracting(UserResponse::getId).containsExactly("anna.meier");
        cums.verify();
    }

    // ------------------------------------------------------------------ GET /internal/email-owners

    @Test
    @DisplayName("#1183: a plus address arrives intact (%2B, not a space)")
    void should_readEmailOwners_withPlusAddressEncoded() {
        cums.expect(GET, "/internal/email-owners").withQueryParam("email", "anna+events@example.com")
                .andExpect(r -> assertThat(r.getURI().getRawQuery()).isEqualTo("email=anna%2Bevents%40example.com"))
                .respondWithExample("oneOwner");

        assertThat(client.getEmailOwners(" anna+events@example.com ")).containsExactly("anna.meier");
        cums.verify();
    }

    @Test
    void should_notCallCums_when_emailBlank() {
        assertThat(client.getEmailOwners(" ")).isEmpty();
        cums.verify();
    }

    // ------------------------------------------------------------------ POST /internal/users/get-or-create

    private static GetOrCreateUserRequest newAttendee() {
        return new GetOrCreateUserRequest().email("anna.meier@example.com").firstName("Anna").lastName("Meier")
                .companyId("Swiss IT Solutions AG").cognitoSync(false);
    }

    @Test
    void should_getOrCreateUser_withoutJwt() {
        cums.expect(POST, "/internal/users/get-or-create").andExpect(UserApiClientImplInternalContractTest::noJwt)
                .respondWithExample("created");

        GetOrCreateUserResponse response = client.getOrCreateUser(newAttendee());

        assertThat(response.getUsername()).isEqualTo("anna.meier");
        assertThat(response.getCreated()).isTrue();
        cums.verify();
    }

    @Test
    @DisplayName("409 EMAIL_LINKED_TO_ACCOUNT keeps status 409 and the code in the message")
    void should_keepEmailLinkedCode_when_cumsAnswers409() {
        cums.expect(POST, "/internal/users/get-or-create").respondWithExample(HttpStatus.CONFLICT);

        assertThatThrownBy(() -> client.getOrCreateUser(newAttendee()))
                .isInstanceOf(UserServiceException.class)
                .hasMessageStartingWith("EMAIL_LINKED_TO_ACCOUNT")
                .satisfies(e -> assertThat(((UserServiceException) e).getStatusCode()).isEqualTo(409));
        cums.verify();
    }

    @Test
    void should_keepGenericMessage_when_otherClientError() {
        cums.expect(POST, "/internal/users/get-or-create").respondWith(HttpStatus.BAD_REQUEST,
                "{\"status\": 400, \"error\": \"Bad Request\", \"message\": \"email: must be a well-formed address\"}");

        assertThatThrownBy(() -> client.getOrCreateUser(newAttendee()))
                .isInstanceOf(UserServiceException.class)
                .hasMessageStartingWith("Client error getting/creating user");
        cums.verify();
    }
}
