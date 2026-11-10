package ch.batbern.shared.test.contract;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;

/**
 * Self-test of the S2S contract harness (Story 17.0b1) against the real CUMS specs: the #1072
 * request ({@code limit=1000}) must fail, as must an unknown path, a body the schema rejects and
 * a call without the JWT the spec requires.
 */
@DisplayName("ContractServer")
class ContractServerTest {

    private static final String COMPANIES = "docs/api/companies-api.openapi.yml";
    private static final String USERS = "docs/api/users-api.openapi.yml";
    private static final String BASE = "http://cums";

    private final SimpleClientHttpRequestFactory realFactory = new SimpleClientHttpRequestFactory();
    private RestTemplate restTemplate;
    private ContractServer server;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate(realFactory);
        server = ContractServer.bindTo(restTemplate, COMPANIES);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private static HttpEntity<Void> withJwt() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-token");
        return new HttpEntity<>(headers);
    }

    private String getCompanies(String query) {
        return restTemplate.exchange(BASE + "/api/v1/companies?" + query, GET, withJwt(), String.class).getBody();
    }

    @Test
    void should_answerWithSpecExample_when_requestIsValid() {
        server.expect(GET, "/api/v1/companies").respondWithExample();

        String body = getCompanies("page=1&limit=100");

        assertThat(body).contains("\"data\"").contains("\"pagination\"");
        server.verify();
    }

    @Test
    @DisplayName("#1072: limit=1000 fails with the validator's message naming limit and its maximum")
    void should_fail_when_limitExceedsSpecMaximum() {
        server.expect(GET, "/api/v1/companies").respondWithExample();

        assertThatThrownBy(() -> getCompanies("page=1&limit=1000"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(COMPANIES)
                .satisfies(e -> {
                    // Only the validator's own lines, never the URI header that carries "limit=1000".
                    String verdict = e.getMessage().substring(e.getMessage().indexOf("\n"));
                    assertThat(verdict).contains("[validation.request.parameter.schema.maximum]")
                            .contains("(parameter 'limit')").contains("maximum value of 1E+2");
                });
    }

    @Test
    void should_fail_when_expectingPathTheSpecLacks() {
        assertThatThrownBy(() -> server.expect(GET, "/api/v1/companies/x/branches"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("GET /api/v1/companies/x/branches")
                .hasMessageContaining("no such path");
    }

    @Test
    void should_fail_when_methodNotInSpec() {
        assertThatThrownBy(() -> server.expect(PATCH, "/api/v1/companies"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PATCH /api/v1/companies")
                .hasMessageContaining("the method does not");
    }

    @Test
    void should_fail_withSpecVerdict_when_clientCallsUnknownPath() {
        server.expect(GET, "/api/v1/companies").respondWithExample();

        assertThatThrownBy(() -> restTemplate.exchange(BASE + "/api/v1/companies-typo", GET, withJwt(), String.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("GET")
                .hasMessageContaining("/api/v1/companies-typo")
                .hasMessageContaining("is not an operation of " + COMPANIES);
    }

    @Test
    void should_fail_when_customBodyViolatesResponseSchema() {
        assertThatThrownBy(() -> server.expect(GET, "/api/v1/companies/{name}")
                .respondWith(HttpStatus.OK, """
                        {"name": 42, "isVerified": true,
                         "createdAt": "2025-01-15T10:00:00Z", "updatedAt": "2025-01-15T10:00:00Z", "createdBy": "lead.organizer"}"""))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Test body for GET /api/v1/companies/{name} 200")
                .hasMessageContaining("[validation.response.body.schema.type]")
                .hasMessageContaining("at /name")
                .hasMessageNotContaining("required");
    }

    @Test
    void should_answerWithCustomBody_when_itMatchesResponseSchema() {
        server.expect(GET, "/api/v1/companies/{name}").respondWith(HttpStatus.OK, """
                {"name": "GoogleZH", "displayName": "Google Schweiz GmbH", "isVerified": true,
                 "createdAt": "2025-01-01T00:00:00Z", "updatedAt": "2025-01-01T00:00:00Z", "createdBy": "lead.organizer"}""");

        String body = restTemplate.exchange(BASE + "/api/v1/companies/GoogleZH", GET, withJwt(), String.class)
                .getBody();

        assertThat(body).contains("Google Schweiz GmbH");
        server.verify();
    }

    @Test
    void should_fail_when_jwtMissingButSpecRequiresIt() {
        server.expect(GET, "/api/v1/companies").respondWithExample();

        assertThatThrownBy(() -> restTemplate.getForObject(BASE + "/api/v1/companies?limit=100", String.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("security");
    }

    @Test
    void should_acceptMissingJwt_when_ignoringMissingAuthorization() {
        server.ignoringMissingAuthorization().expect(GET, "/api/v1/companies").respondWithExample();

        assertThat(restTemplate.getForObject(BASE + "/api/v1/companies?limit=100", String.class)).contains("data");
    }

    @Test
    void should_answerDeclaredErrorStatus_when_requested() {
        server.expect(GET, "/api/v1/companies/{name}").respondWithStatus(HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> restTemplate.exchange(BASE + "/api/v1/companies/Ghost", GET, withJwt(), String.class))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void should_refuseUndeclaredStatus() {
        assertThatThrownBy(() -> server.expect(GET, "/api/v1/companies/{name}")
                .respondWithStatus(HttpStatus.I_AM_A_TEAPOT))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("does not declare status 418");
    }

    @Test
    void should_matchOnQueryParam_when_pagesAnswerDifferently() {
        server.expect(GET, "/api/v1/companies").withQueryParam("page", "2").respondWith(HttpStatus.OK, """
                {"data": [], "pagination": {"page": 2, "limit": 100, "totalItems": 100, "totalPages": 2,
                 "hasNext": false, "hasPrev": true}}""");
        server.expect(GET, "/api/v1/companies").withQueryParam("page", "1").respondWithExample();

        assertThat(getCompanies("page=1&limit=100")).contains("\"data\"");
        assertThat(getCompanies("page=2&limit=100")).contains("\"page\": 2");
        server.verify();
    }

    @Test
    void should_matchDecodedQueryValue_when_valueHasSpace() {
        server.expect(GET, "/api/v1/companies").withQueryParam("sort", "-createdAt name").respondWithExample();

        assertThat(getCompanies("limit=100&sort=-createdAt name")).contains("\"data\"");
        server.verify();
    }

    @Test
    void should_restoreRealRequestFactory_when_closed() {
        server.close();

        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(restTemplate, "requestFactory"))
                .isSameAs(realFactory);
    }

    @Test
    void should_validateRequestBody_againstUsersSpec() {
        // The body schema is users-api's PatchUserProfileRequest, reached through the internal spec's
        // $ref (the operation moved to /internal in Epic 17 entry 6).
        server.close();
        server = ContractServer.bindTo(restTemplate, CUMS_INTERNAL);
        server.expect(PATCH, "/internal/users/{username}/profile").respondWith(HttpStatus.OK, """
                {"id": "jane.smith", "email": "jane.smith@example.com", "firstName": "Jane", "lastName": "Smith",
                 "bio": "x", "roles": ["SPEAKER"], "active": true, "hasCognitoAccount": true, "companyId": null,
                 "profilePictureUrl": null, "lastLoginAt": null, "termsAcceptedAt": null, "company": null,
                 "settings": null, "additionalEmails": [],
                 "createdAt": "2025-01-01T00:00:00Z", "updatedAt": "2025-01-01T00:00:00Z"}""");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("t");
        headers.set("Content-Type", "application/json");

        assertThatThrownBy(() -> restTemplate.exchange(BASE + "/internal/users/jane.smith/profile", PATCH,
                new HttpEntity<>(Map.of("bio", 7), headers), String.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("bio");
    }

    @Test
    void should_findExamplesValid_forCompaniesList() {
        ProviderContract.assertExamplesMatchSchema(COMPANIES, "GET", "/api/v1/companies");
    }

    // ---------------------------------------------------------------- internal specs (17.0b2)

    private static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";

    @Test
    @DisplayName("an internal spec (server /, full paths) routes /internal paths and answers its example")
    void should_routeInternalSpec_withFullPaths() {
        server.close();
        server = ContractServer.bindTo(restTemplate, CUMS_INTERNAL);
        server.expect(GET, "/internal/users/{username}").respondWithExample();

        String body = restTemplate.getForObject(BASE + "/internal/users/anna.meier", String.class);

        assertThat(body).contains("\"id\":\"anna.meier\"");
        assertThat(OpenApiContract.load(CUMS_INTERNAL).basePath()).isEmpty();
        assertThat(OpenApiContract.load(COMPANIES).basePath()).isEqualTo("/api/v1");
        server.verify();
    }

    @Test
    @DisplayName("an /internal path the spec lacks fails, naming method, path and spec")
    void should_fail_when_internalPathNotInSpec() {
        server.close();
        server = ContractServer.bindTo(restTemplate, CUMS_INTERNAL);
        server.expect(GET, "/internal/users/{username}").respondWithExample();

        assertThatThrownBy(() -> restTemplate.getForObject(BASE + "/internal/users/anna.meier/roles", String.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("GET /internal/users/anna.meier/roles is not an operation of " + CUMS_INTERNAL);
        assertThatThrownBy(() -> server.expect(GET, "/internal/users/{username}/roles"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("GET /internal/users/{username}/roles")
                .hasMessageContaining(CUMS_INTERNAL);
    }

    @Test
    @DisplayName("an unknown query parameter on an internal operation fails")
    void should_fail_when_internalQueryParamNotInSpec() {
        server.close();
        server = ContractServer.bindTo(restTemplate, CUMS_INTERNAL);
        server.expect(GET, "/internal/users").respondWithExample();

        String url = BASE + "/internal/users?role=PARTNER&size=500";
        assertThatThrownBy(() -> restTemplate.getForObject(url, String.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("(parameter 'size')");
    }

    @Test
    @DisplayName("one RestTemplate, two specs: each request is checked against the spec that has its path")
    void should_routeEachRequest_toTheSpecThatHasItsPath() {
        server.close();
        server = ContractServer.bindTo(restTemplate, COMPANIES, CUMS_INTERNAL);
        server.expect(GET, "/api/v1/companies").respondWithExample();
        server.expect(GET, "/internal/email-owners").respondWithExample();

        assertThat(getCompanies("page=1&limit=100")).contains("\"data\"");
        assertThat(restTemplate.getForObject(BASE + "/internal/email-owners?email=a@example.com", String.class))
                .contains("anna.meier");
        server.verify();
        assertThatThrownBy(() -> restTemplate.getForObject(BASE + "/internal/nothing", String.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(COMPANIES + ", " + CUMS_INTERNAL);
    }

    @Test
    @DisplayName("respondPerRequest builds each body from the decoded path variables and checks it")
    void should_answerPerRequest_fromPathVariables() {
        server.close();
        server = ContractServer.bindTo(restTemplate, CUMS_INTERNAL);
        server.expect(GET, "/internal/users/{username}").times(org.springframework.test.web.client.ExpectedCount
                .manyTimes()).respondPerRequest(HttpStatus.OK, call -> """
                        {"id": "%s", "email": "x@example.com", "firstName": "F", "lastName": "L", "roles": [],
                         "bio": null, "active": true, "hasCognitoAccount": true, "companyId": null,
                         "profilePictureUrl": null, "lastLoginAt": null, "termsAcceptedAt": null, "company": null,
                         "settings": null, "additionalEmails": [],
                         "createdAt": "2025-01-01T00:00:00Z", "updatedAt": "2025-01-01T00:00:00Z"}"""
                .formatted(call.pathVariables().get("username")));

        assertThat(restTemplate.getForObject(BASE + "/internal/users/jane.smith", String.class))
                .contains("\"jane.smith\"");
        // The id pattern of UserResponse rejects a body built for an invalid username.
        assertThatThrownBy(() -> restTemplate.getForObject(BASE + "/internal/users/Not Valid", String.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Test body for GET /internal/users/{username} 200")
                .hasMessageContaining("/id");
    }

    @Test
    @DisplayName("onlyFor: routed requests reach the harness, all others the real request factory")
    void should_routeOnlySelectedRequests_toTheHarness() {
        // A stub stands in for the real request factory: the non-routed request must reach it,
        // never the network.
        List<URI> reachedStub = new ArrayList<>();
        ClientHttpRequestFactory stub = (uri, method) -> {
            reachedStub.add(uri);
            MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
            request.setResponse(new MockClientHttpResponse("\"from the stub\"".getBytes(StandardCharsets.UTF_8),
                    HttpStatus.OK));
            return request;
        };
        server.close();
        restTemplate = new RestTemplate(stub);
        server = ContractServer.bindTo(restTemplate, CUMS_INTERNAL).onlyFor(uri -> uri.getHost().equals("cums"));
        server.expect(GET, "/internal/email-owners").respondPerRequest(call ->
                ContractServer.Reply.ok("[\"" + call.queryParam("email").split("@")[0] + "\"]"));

        assertThat(restTemplate.getForObject(BASE + "/internal/email-owners?email=anna.meier@example.com",
                String.class)).isEqualTo("[\"anna.meier\"]");
        assertThat(reachedStub).isEmpty();
        assertThat(restTemplate.getForObject("http://other.test/internal/email-owners", String.class))
                .isEqualTo("\"from the stub\"");
        assertThat(reachedStub).containsExactly(URI.create("http://other.test/internal/email-owners"));
        server.verify();
    }

    @Test
    @DisplayName("an encoded '/' in a path parameter stays inside its segment")
    void should_keepEncodedSlash_insideOneSegment() {
        server.close();
        server = ContractServer.bindTo(restTemplate, CUMS_INTERNAL);
        server.expect(GET, "/internal/users/{username}").respondWithExample();

        String body = restTemplate.getForObject(URI.create(BASE + "/internal/users/a%2Fb%3Fc"), String.class);

        assertThat(body).contains("anna.meier");
        server.verify();
    }

    @Test
    @DisplayName("a declared error example answers with its status and body")
    void should_answerDeclaredErrorExample() {
        server.close();
        server = ContractServer.bindTo(restTemplate, CUMS_INTERNAL);
        server.expect(POST, "/internal/users/get-or-create").respondWithExample(HttpStatus.CONFLICT);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        assertThatThrownBy(() -> restTemplate.postForObject(BASE + "/internal/users/get-or-create",
                new HttpEntity<>(Map.of("email", "a@example.com", "firstName", "A", "lastName", "B"), headers),
                String.class))
                .isInstanceOf(HttpClientErrorException.Conflict.class)
                .hasMessageContaining("EMAIL_LINKED_TO_ACCOUNT");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "docs/api/internal/cums-internal.openapi.yml",
        "docs/api/internal/ems-internal.openapi.yml",
        "docs/api/internal/pcs-internal.openapi.yml"
    })
    @DisplayName("every response example of every internal operation matches its schema")
    void should_haveValidExamples_inEveryInternalSpec(String spec) {
        OpenApiContract contract = OpenApiContract.load(spec);
        contract.operations().forEach((method, paths) -> paths.forEach(path ->
                ProviderContract.assertExamplesMatchSchema(spec, method, path)));
    }

    // ---------------------------------------------------------------- GeneratedCodeSemantics

    private static final String COMPANY_DATES = "\"createdBy\": \"lead.organizer\", \"createdAt\": \"2025-01-15T10:00:00Z\", "
            + "\"updatedAt\": \"2025-01-15T10:00:00Z\"";

    @Test
    @DisplayName("null is valid only where the spec says nullable, optional or not (owner decision 2026-11-09)")
    void should_rejectNull_when_propertyIsNotNullable() {
        OpenApiContract companies = OpenApiContract.load(COMPANIES);

        // displayName is optional and not nullable: absent is fine, null is not
        assertThat(companies.validateResponse("GET", "/api/v1/companies/x", 200,
                "{\"name\": \"Acme\", \"isVerified\": true, " + COMPANY_DATES + "}"))
                .isEmpty();
        assertThat(companies.validateResponse("GET", "/api/v1/companies/x", 200,
                "{\"name\": \"Acme\", \"isVerified\": true, \"displayName\": null, " + COMPANY_DATES + "}"))
                .singleElement().asString().contains("/displayName");
        assertThat(companies.validateResponse("GET", "/api/v1/companies/x", 200,
                "{\"name\": null, \"isVerified\": true, " + COMPANY_DATES + "}"))
                .singleElement().asString().contains("/name");
    }

    @Test
    @DisplayName("nullable: true in a 3.0 spec admits null, also on an enum")
    void should_honourNullableKeyword_in30Spec() {
        OpenApiContract registrations = OpenApiContract.load("docs/api/event-registrations-api.openapi.yml");
        String registration = """
                {"registrationCode": "BATbern1-reg-ABC123", "eventCode": "BATbern1", "status": "REGISTERED",
                 "attendeeUsername": "anna.muster", "eventTitle": null, "eventDate": null, "attendeeFirstName": null,
                 "attendeeLastName": null, "attendeeEmail": null, "attendeeCompany": null, "waitlistPosition": null,
                 "registrationDate": null, "createdAt": null, "updatedAt": null}""";

        assertThat(registrations.validateResponse("GET", "/api/v1/events/registrations", 200,
                "[" + registration + "]")).isEmpty();

        // MailMessageSummary.participantStatus: nullable enum without null in its list; the
        // spec example of the page carries it as null
        OpenApiContract mail = OpenApiContract.load("docs/api/event-mail-api.openapi.yml");
        String page = """
                {"data": [{"mailKey": "BATbern1-1", "sentAt": "2026-05-14T16:00:00Z", "audience": "REGISTRANTS",
                  "contentSource": "TEMPLATE", "recipientCount": 1, "deliveredCount": 1, "failedCount": 0,
                  "messageCount": 1, "test": false, "participantStatus": null, "senderUsername": null,
                  "templateKey": null, "subject": null, "newsletterSendId": null, "newsletterStatus": null,
                  "reference": null, "direction": "OUT", "fromAddress": null, "fromName": null, "eventCode": null,
                  "conversationKey": null, "spam": null, "attachments": null}],
                 "pagination": {"page": 1, "limit": 20, "totalItems": 1, "totalPages": 1,
                  "hasNext": false, "hasPrev": false}}""";
        assertThat(mail.validateResponse("GET", "/api/v1/events/BATbern1/mails", 200, page)).isEmpty();
    }

    @Test
    @DisplayName("nullable: true in a 3.1 spec admits null")
    void should_honourNullableKeyword_in31Spec() {
        // InvitationCredentialsResponse.temporaryPassword (users-api, nullable: true), through the
        // 3.1 internal spec's $ref since the operation moved there (Epic 17 entry 6)
        OpenApiContract users = OpenApiContract.load(CUMS_INTERNAL);

        assertThat(users.validateResponse("POST", "/internal/users/x/issue-invitation-credentials", 200,
                "{\"temporaryPassword\": null, \"action\": \"USE_EXISTING_PASSWORD\"}")).isEmpty();
    }

    @Test
    @DisplayName("a nullable $ref (allOf or oneOf with type null) admits null, 3.0 and 3.1")
    void should_admitNull_when_referencedSchemaIsNullable() {
        // 3.1, nullable: true next to allOf
        assertThat(OpenApiContract.load(USERS).validateResponse("POST",
                "/api/v1/users/merge-requests/0b9f8e2a-1c3d-4e5f-8a7b-9c0d1e2f3a4b/decline", 200, """
                {"requestId": "0b9f8e2a-1c3d-4e5f-8a7b-9c0d1e2f3a4b", "status": "DECLINED", "requesterUsername": "anna.muster",
                 "sourceUsername": "anna.muster.2", "createdAt": "2026-05-14T16:00:00Z", "note": null,
                 "decidedBy": null, "decidedAt": null, "reason": null, "preview": null}"""))
                .isEmpty();
        // 3.1, oneOf [$ref, type: 'null']
        assertThat(OpenApiContract.load(USERS).validateResponse("GET",
                "/api/v1/users/anna.muster/watch-pairing", 200,
                "{\"pairedWatches\": [], \"pendingCode\": null}"))
                .isEmpty();
        // 3.0, nullable: true next to allOf
        assertThat(OpenApiContract.load("docs/api/event-mail-api.openapi.yml").validateResponse("POST",
                "/api/v1/events/BATbern1/mails/preview", 200, """
                {"subject": "S", "html": "<p>H</p>", "recipientIndex": 0, "recipientCount": 1,
                 "sampleRecipients": [], "personalized": false, "locale": "de", "organizerCount": 0,
                 "skipped": [], "unknownPlaceholders": [], "alreadySent": null,
                 "recipient": {"displayName": "P", "address": "p@example.com", "cc": []}, "templateKey": null,
                 "replyTo": null, "highlightedSubject": null, "highlightedHtml": null, "offeredVariables": [],
                 "allowedVariables": []}"""))
                .isEmpty();
        // 3.0, nullable allOf of an enum, inherited through SessionResponse = allOf [Session, ...]
        OpenApiContract sessions = OpenApiContract.load("docs/api/event-sessions-api.openapi.yml");
        String session = sessions.example("GET", "/api/v1/events/BATbern1/sessions/s1", 200, null);
        assertThat(session).contains("\"materialsStatus\":\"NONE\"");
        assertThat(sessions.validateResponse("GET", "/api/v1/events/BATbern1/sessions/s1", 200,
                session.replace("\"materialsStatus\":\"NONE\"", "\"materialsStatus\":null"))).isEmpty();
    }

    @Test
    @DisplayName("format: time means HH:mm[:ss], as EMS sends it")
    void should_readFormatTimeAsLocalWallClock() {
        OpenApiContract events = OpenApiContract.load("docs/api/events-core-api.openapi.yml");
        String event = """
                {"eventCode": "BATbern1", "title": "T", "eventNumber": 1, "date": "2026-06-11T16:00:00Z",
                 "registrationDeadline": "2026-06-04T16:00:00Z", "venueName": "V", "venueAddress": "A",
                 "venueCapacity": 1, "currentAttendeeCount": 0, "eventType": "EVENING", "workflowState": "CREATED",
                 "qnaEnabled": true, "qnaOpenTrigger": "EVENT_COMPLETED", "qnaWindowDays": 14,
                 "createdAt": "2026-01-01T00:00:00Z", "updatedAt": "2026-01-01T00:00:00Z", "typicalStartTime": "%s"}""";

        assertThat(events.validateResponse("GET", "/api/v1/events/BATbern1", 200, event.formatted("18:00"))).isEmpty();
        assertThat(events.validateResponse("GET", "/api/v1/events/BATbern1", 200, event.formatted("6pm")))
                .singleElement().asString().contains("/typicalStartTime");
    }

    @Test
    @DisplayName("format: idn-email checks the mailbox shape and accepts the reserved .example TLD (4.9b)")
    void should_acceptIdnAddressUnderReservedTld_andRejectNonAddress() {
        OpenApiContract users = OpenApiContract.load(USERS);
        String additional = "{\"email\": \"%s\", \"label\": null, \"createdAt\": \"2026-05-22T10:00:00Z\", "
                + "\"verifiedAt\": null, \"receiveEventMail\": false}";

        // The 17.0d IDN person: Apache Commons' TLD list alone rejected it (no .example TLD)
        assertThat(users.validateResponse("POST", "/api/v1/users/me/additional-emails", 201,
                additional.formatted("juerg.brunner@bücherstube.example"))).isEmpty();
        assertThat(users.validateResponse("POST", "/api/v1/users/me/additional-emails", 201,
                additional.formatted("x@müller.ch"))).isEmpty();
        // positive control: the shape still rejects what is no address
        for (String bad : new String[] {"not an address", "no-at-sign.example", "x@nodot", "a@b@c.example"}) {
            assertThat(users.validateResponse("POST", "/api/v1/users/me/additional-emails", 201,
                    additional.formatted(bad))).as(bad).singleElement().asString().contains("/email");
        }
    }
}
