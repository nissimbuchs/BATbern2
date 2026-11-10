package ch.batbern.events.client.impl;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.config.RestClientConfig;
import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.dto.generated.users.InvitationCredentialsResponse;
import ch.batbern.events.dto.generated.users.PatchUserProfileRequest;
import ch.batbern.events.dto.generated.users.ProvisionUserRequest;
import ch.batbern.events.dto.generated.users.ProvisionUserResponse;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;

/**
 * Contract test of every call of {@link UserApiClientImpl} against
 * {@code docs/api/internal/cums-internal.openapi.yml} (Story 17.0b2; Epic 17 entry 6 moved the last
 * public calls, provision, profile patch, invitation credentials and the company read, here and
 * deleted the public-API contract test): the real client on the real {@code RestTemplate} bean,
 * each request validated against the spec, answers from its examples. Only the internal spec is
 * bound, so a call to any other path fails.
 *
 * <p>ADR-016 decision 3: every call forwards the signed-in user's JWT and works without one. Each
 * operation runs both ways (a user signed in, or nobody) and asserts the header is there or
 * absent. CUMS's internal answers do not depend on the caller, so the reads stay cacheable.
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

    private static final String TOKEN = "user-token";

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(n -> Objects.requireNonNull(cacheManager.getCache(n)).clear());
        SecurityContextHolder.clearContext();
        cums = ContractServer.bindTo(restTemplate, CUMS_INTERNAL);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cums.close();
    }

    /** A user signed in with a JWT (as on a request thread), or nobody (scheduler, SQS, anonymous). */
    private static void signedIn(boolean user) {
        if (user) {
            Jwt jwt = Jwt.withTokenValue(TOKEN).header("alg", "none").claim("sub", "organizer")
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        } else {
            SecurityContextHolder.clearContext();
        }
    }

    /** The JWT is forwarded when a user is signed in, and no {@code Authorization} is sent otherwise. */
    private static Consumer<ClientHttpRequest> jwtForwardedIff(boolean user) {
        return request -> {
            if (user) {
                assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + TOKEN);
            } else {
                assertThat(request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
            }
        };
    }

    // ------------------------------------------------------------------ GET /internal/users/{username}

    @ParameterizedTest(name = "signed in: {0}")
    @CsvSource({"true", "false"})
    void should_readUser_withOrWithoutJwt(boolean user) {
        signedIn(user);
        cums.expect(GET, "/internal/users/{username}").andExpect(jwtForwardedIff(user))
                .respondWithExample("user");

        UserResponse found = client.getUserByUsername("anna.meier");

        assertThat(found.getEmail()).isEqualTo("anna.meier@example.com");
        assertThat(found.getFirstName()).isEqualTo("Anna");
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

    @ParameterizedTest(name = "signed in: {0}")
    @CsvSource({"true", "false"})
    @DisplayName("#1146, Story 4.9: notification settings come from ?include=settings")
    void should_readPreferences_fromInclude(boolean user) {
        signedIn(user);
        cums.expect(GET, "/internal/users/{username}").withQueryParam("include", "settings")
                .andExpect(jwtForwardedIff(user))
                .respondWithExample("withSettings");

        UserPreferences prefs = client.getPreferences("anna.meier");

        assertThat(prefs.isEmailNotificationsEnabled()).isTrue();
        assertThat(prefs.isPushNotificationsEnabled()).isFalse();
        assertThat(prefs.getNotificationFrequency()).isEqualTo("immediate");
        cums.verify();
    }

    @Test
    void should_fallBackToDefaultPreferences_when_userHasNone() {
        cums.expect(GET, "/internal/users/{username}").withQueryParam("include", "settings")
                .respondWithExample("user");

        UserPreferences prefs = client.getPreferences("anna.meier");

        assertThat(prefs.isEmailNotificationsEnabled()).isTrue();
        assertThat(prefs.getQuietHoursStart()).isNull();
        cums.verify();
    }

    @Test
    void should_readPreferredLanguageAndQnaFrequency() {
        cums.expect(GET, "/internal/users/{username}").withQueryParam("include", "settings")
                .times(ExpectedCount.twice())
                .respondWithExample("withSettings");

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
                       "updatedAt": "2025-01-15T10:00:00Z", "hasCognitoAccount": true, "bio": null,
                       "companyId": null, "profilePictureUrl": null, "active": true, "lastLoginAt": null,
                       "termsAcceptedAt": null, "company": null, "settings": null, "additionalEmails": []}],
             "pagination": {"page": 1, "limit": 100, "totalItems": 101, "totalPages": 2,
                            "hasNext": true, "hasPrev": false}}""";

    @ParameterizedTest(name = "signed in: {0}")
    @CsvSource({"true", "false"})
    @DisplayName("organizers: every page is read, within limit 100")
    void should_readOrganizers_fromAllPages(boolean user) {
        signedIn(user);
        cums.expect(GET, "/internal/users").withQueryParam("role", "ORGANIZER").withQueryParam("page", "1")
                .withQueryParam("limit", "100").andExpect(jwtForwardedIff(user))
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

    @ParameterizedTest(name = "signed in: {0}")
    @CsvSource({"true", "false"})
    @DisplayName("#1183: a plus address arrives intact (%2B, not a space)")
    void should_readEmailOwners_withPlusAddressEncoded(boolean user) {
        signedIn(user);
        cums.expect(GET, "/internal/email-owners").withQueryParam("email", "anna+events@example.com")
                .andExpect(jwtForwardedIff(user))
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

    @ParameterizedTest(name = "signed in: {0}")
    @CsvSource({"true", "false"})
    void should_getOrCreateUser_withOrWithoutJwt(boolean user) {
        signedIn(user);
        cums.expect(POST, "/internal/users/get-or-create").andExpect(jwtForwardedIff(user))
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

    // ------------------------------------------------------------------ POST /internal/users/provision

    @ParameterizedTest(name = "signed in: {0}")
    @CsvSource({"true", "false"})
    void should_provisionSpeaker_withOrWithoutJwt(boolean user) {
        signedIn(user);
        cums.expect(POST, "/internal/users/provision").andExpect(jwtForwardedIff(user))
                .respondWithExample("created");

        ProvisionUserResponse response = client.provisionUserWithRole(new ProvisionUserRequest()
                .email("anna.meier@example.com").firstName("Anna").lastName("Meier")
                .role(ProvisionUserRequest.RoleEnum.SPEAKER));

        assertThat(response.getUsername()).isEqualTo("anna.meier");
        assertThat(response.getCreated()).isTrue();
        cums.verify();
    }

    @Test
    void should_provisionWithEmailOnly_whenNamesUnknown() {
        cums.expect(POST, "/internal/users/provision").respondWithExample("alreadyExisted");

        ProvisionUserResponse response = client.provisionUserWithRole(new ProvisionUserRequest()
                .email("anna.meier@example.com").role(ProvisionUserRequest.RoleEnum.SPEAKER));

        assertThat(response.getCreated()).isFalse();
        cums.verify();
    }

    @Test
    @DisplayName("a role CUMS refuses (SPEAKER only) surfaces as UserServiceException 400")
    void should_throw_when_cumsRefusesRole() {
        cums.expect(POST, "/internal/users/provision").respondWithExample(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> client.provisionUserWithRole(new ProvisionUserRequest()
                .email("anna.meier@example.com").role(ProvisionUserRequest.RoleEnum.ORGANIZER)))
                .isInstanceOf(UserServiceException.class)
                .satisfies(e -> assertThat(((UserServiceException) e).getStatusCode()).isEqualTo(400));
        cums.verify();
    }

    // ------------------------------------------------------------------ PATCH /internal/users/{username}/profile

    @ParameterizedTest(name = "signed in: {0}")
    @CsvSource({"true", "false"})
    void should_patchProfile_withOrWithoutJwt(boolean user) {
        signedIn(user);
        cums.expect(PATCH, "/internal/users/{username}/profile").andExpect(jwtForwardedIff(user))
                .respondWithExample();

        UserResponse patched = client.patchUserProfile("anna.meier", new PatchUserProfileRequest()
                .bio("Software architect"));

        assertThat(patched.getId()).isEqualTo("anna.meier");
        assertThat(patched.getBio()).isEqualTo("Software architect");
        cums.verify();
    }

    @Test
    void should_throwUserNotFound_when_profileUserUnknown() {
        cums.expect(PATCH, "/internal/users/{username}/profile").respondWithExample(HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> client.patchUserProfile("ghost.user", new PatchUserProfileRequest().bio("x")))
                .isInstanceOf(UserNotFoundException.class);
        cums.verify();
    }

    // ------------------------------------------------------------------ issue-invitation-credentials

    @ParameterizedTest(name = "signed in: {0}")
    @CsvSource({"true", "false"})
    void should_returnFreshTempPassword_withOrWithoutJwt(boolean user) {
        signedIn(user);
        cums.expect(POST, "/internal/users/{username}/issue-invitation-credentials")
                .andExpect(jwtForwardedIff(user))
                .respondWithExample("freshTempPassword");

        InvitationCredentialsResponse result = client.issueInvitationCredentials("anna.meier");

        assertThat(result.getAction()).isEqualTo(InvitationCredentialsResponse.ActionEnum.FRESH_TEMP_PASSWORD);
        assertThat(result.getTemporaryPassword()).isEqualTo("Tk7!aB2@nx9pQ4#z");
        cums.verify();
    }

    @Test
    void should_keepExistingPassword_when_userConfirmed() {
        cums.expect(POST, "/internal/users/{username}/issue-invitation-credentials")
                .respondWithExample("useExistingPassword");

        InvitationCredentialsResponse result = client.issueInvitationCredentials("anna.meier");

        assertThat(result.getAction()).isEqualTo(InvitationCredentialsResponse.ActionEnum.USE_EXISTING_PASSWORD);
        assertThat(result.getTemporaryPassword()).isNull();
        cums.verify();
    }

    @Test
    void should_failFast_when_freshPasswordActionComesWithNullPassword() {
        cums.expect(POST, "/internal/users/{username}/issue-invitation-credentials")
                .respondWith(HttpStatus.OK, "{\"temporaryPassword\": null, \"action\": \"FRESH_TEMP_PASSWORD\"}");

        assertThatThrownBy(() -> client.issueInvitationCredentials("anna.meier"))
                .isInstanceOf(UserServiceException.class)
                .hasStackTraceContaining("no temporaryPassword");
    }

    @Test
    void should_throwUserNotFound_when_credentialsUserUnknown() {
        cums.expect(POST, "/internal/users/{username}/issue-invitation-credentials")
                .respondWithExample(HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> client.issueInvitationCredentials("ghost.user"))
                .isInstanceOf(UserNotFoundException.class);
        cums.verify();
    }

    // ------------------------------------------------------------------ GET /internal/companies/{name}

    @ParameterizedTest(name = "signed in: {0}")
    @CsvSource({"true", "false"})
    void should_returnDisplayName_withOrWithoutJwt(boolean user) {
        signedIn(user);
        cums.expect(GET, "/internal/companies/{name}").andExpect(jwtForwardedIff(user)).respondWithExample();

        assertThat(client.getCompanyDisplayName("swissitsolut")).isEqualTo("Swiss IT Solutions AG");
        cums.verify();
    }

    @Test
    void should_fallBackToName_when_displayNameMissing() {
        cums.expect(GET, "/internal/companies/{name}").respondWith(HttpStatus.OK, """
                {"name": "adesso", "isVerified": false, "createdAt": "2025-01-15T10:00:00Z",
                 "updatedAt": "2025-01-15T10:00:00Z", "createdBy": "lead.organizer"}""");

        assertThat(client.getCompanyDisplayName("adesso")).isEqualTo("adesso");
        cums.verify();
    }

    @Test
    void should_returnNull_when_companyUnknown() {
        cums.expect(GET, "/internal/companies/{name}").respondWithExample(HttpStatus.NOT_FOUND);

        assertThat(client.getCompanyDisplayName("ghost")).isNull();
        cums.verify();
    }

    @Test
    @DisplayName("a company name is one strictly encoded path segment")
    void should_encodeCompanyName_asOnePathSegment() {
        cums.expect(GET, "/internal/companies/{name}")
                .andExpect(r -> assertThat(r.getURI().getRawPath()).isEqualTo("/internal/companies/a%2Fb%20c"))
                .respondWithExample();

        client.getCompanyDisplayName("a/b c");

        cums.verify();
    }

    @Test
    void should_returnNull_when_cumsUnreachable() {
        cums.expect(GET, "/internal/companies/{name}").respondWith(request -> {
            throw new ResourceAccessException("connection refused");
        });

        assertThat(client.getCompanyDisplayName("mobiliar")).isNull();
        cums.verify();
    }

    @Test
    void should_notCallCums_when_slugBlank() {
        assertThat(client.getCompanyDisplayName(null)).isNull();
        assertThat(client.getCompanyDisplayName("")).isNull();
        assertThat(client.getCompanyDisplayName("   ")).isNull();
        cums.verify();
    }
}
