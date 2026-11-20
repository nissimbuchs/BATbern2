package ch.batbern.events.controller;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.service.ConfirmationTokenService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer.Call;
import ch.batbern.shared.test.contract.ContractServer.Reply;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link RegistrationController}: anonymous registration (Story 2.2a,
 * ADR-005), the e-mail confirmation (Story 4.1.5c), the organizer's list, status change and delete,
 * and the spec validation the generated {@code RegistrationsApi} brings (Story 17.13).
 *
 * <p>Production chain, CUMS as the {@link CumsContractHarness} and SES as the {@link SesRecorder}
 * (no own-code mocks). Not {@code @Transactional}: the requests commit, so the test removes its two
 * events and their registrations in {@code @BeforeEach} and {@code @AfterEach}. Attendee usernames
 * are ASCII ({@code john.doe}) because CUMS usernames are ({@code ^[a-z]+\.[a-z]+}); the search tests
 * carry the non-ASCII names in the denormalized attendee fields.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class RegistrationControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern1713";
    private static final String OTHER_EVENT_CODE = "BATbern1714";
    /** An AWS mailbox-simulator address: mail to it may be sent. */
    private static final String JOHN_MAIL = "success+john.doe@simulator.amazonses.com";
    private static final String JANE_MAIL = "success+jane.smith@simulator.amazonses.com";
    private static final String CONFIRM_LINK = "confirm-registration?token=";
    private static final Pattern CONFIRM_TOKEN =
            Pattern.compile("confirm-registration\\?token=([A-Za-z0-9._-]+)");
    private static final String NEW_REGISTRATION = """
            {
                "firstName": "John",
                "lastName": "Doe",
                "email": "john.doe@example.com",
                "termsAccepted": true
            }
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private ConfirmationTokenService confirmationTokenService;
    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;

    private Event testEvent;

    @BeforeEach
    void setUp() {
        cleanUp();
        cums.reset();
        sesRecorder.reset();

        testEvent = eventRepository.save(event(EVENT_CODE, 1713));
        // CUMS get-or-create: the username is first.last, the user what was sent.
        cums.answer(POST, "/internal/users/get-or-create", call -> Reply.ok(getOrCreateReply(call, false)));
        cums.user("john.doe", "John", "Doe", JOHN_MAIL);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        cums.reset();
    }

    /** Deletes this class's events and their registrations (the requests commit). */
    private void cleanUp() {
        jdbc.update("DELETE FROM newsletter_subscribers WHERE lower(email) IN (?, ?)", JOHN_MAIL, JANE_MAIL);
        jdbc.update("DELETE FROM registrations WHERE event_id IN (SELECT id FROM events WHERE event_code IN (?, ?))",
                EVENT_CODE, OTHER_EVENT_CODE);
        jdbc.update("DELETE FROM events WHERE event_code IN (?, ?)", EVENT_CODE, OTHER_EVENT_CODE);
    }

    // ============================================================================
    // Create anonymous registration (ADR-005)
    // ============================================================================

    @Test
    @DisplayName("should_createRegistration_when_validDataProvided")
    void should_createRegistration_when_validDataProvided() throws Exception {
        // Story 4.1.5c: the response is {message, email}, the registration details go by mail
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_REGISTRATION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Registration submitted successfully. Check your email to confirm."))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.registrationCode").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());

        Registration saved = registrationRepository.findByEventIdAndAttendeeUsername(testEvent.getId(), "john.doe")
                .orElseThrow();
        assertThat(saved.getRegistrationCode()).startsWith(EVENT_CODE + "-reg-");
        assertThat(saved.getStatus()).isEqualTo("registered"); // confirmed by the mailed link

        // The confirmation link goes to the attendee CUMS knows, with the deregistration link.
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(JOHN_MAIL)).singleElement()
                .satisfies(mail -> assertThat(mail.html())
                        .contains("/deregister?token=" + saved.getDeregistrationToken()));
    }

    @Test
    @DisplayName("should_returnCompanyUpdatedTrue_when_existingUserCompanyRefreshed")
    void should_returnCompanyUpdatedTrue_when_existingUserCompanyRefreshed() throws Exception {
        // BATbern59 badge fix: CUMS reports it refreshed the returning attendee's company.
        cums.answer(POST, "/internal/users/get-or-create", call -> Reply.ok(getOrCreateReply(call, true)));

        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "John", "lastName": "Doe", "email": "john.doe@example.com",
                                 "company": "New Employer AG", "termsAccepted": true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyUpdated").value(true));
    }

    @Test
    @DisplayName("should_returnCompanyUpdatedFalse_when_companyUnchanged")
    void should_returnCompanyUpdatedFalse_when_companyUnchanged() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_REGISTRATION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyUpdated").value(false));
    }

    @Test
    @DisplayName("should_return400_when_invalidDataProvided")
    void should_return400_when_invalidDataProvided() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "", "lastName": "Doe", "email": "invalid-email", "termsAccepted": true}
                                """))
                .andExpect(status().isBadRequest());
        assertThat(registrationRepository.findByEventIdAndAttendeeUsername(testEvent.getId(), "john.doe")).isEmpty();
    }

    @Test
    @DisplayName("should_return400_when_creatingRegistrationWithMalformedJson")
    void should_return400_when_creatingRegistrationWithMalformedJson() throws Exception {
        // Moved from EventControllerIntegrationTest (Story 17.13): a trailing comma is no JSON.
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"invalid-email\",}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_return404_when_eventNotFound")
    void should_return404_when_eventNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", "BATbern99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_REGISTRATION))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONEXISTENT", "BATbern10k", "batbern1713"})
    @DisplayName("should_return400_when_eventCodeViolatesSpecPattern")
    void should_return400_when_eventCodeViolatesSpecPattern(String eventCode) throws Exception {
        // Story 17.13: RegistrationsApi brings the spec pattern ^BATbern[0-9]+$ (was 404).
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", eventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_REGISTRATION))
                .andExpect(status().isBadRequest());
        assertThat(cums.calls(POST, "/internal/users/get-or-create")).isEmpty();
    }

    @Test
    @DisplayName("should_generateUniqueRegistrationCode_when_multipleRegistrationsCreated")
    void should_generateUniqueRegistrationCode_when_multipleRegistrationsCreated() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_REGISTRATION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"));
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "Jane", "lastName": "Smith", "email": "jane.smith@example.com",
                                 "termsAccepted": true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("jane.smith@example.com"));

        String code1 = registrationRepository.findByEventIdAndAttendeeUsername(testEvent.getId(), "john.doe")
                .orElseThrow().getRegistrationCode();
        String code2 = registrationRepository.findByEventIdAndAttendeeUsername(testEvent.getId(), "jane.smith")
                .orElseThrow().getRegistrationCode();
        assertThat(code1).isNotEqualTo(code2);
        assertThat(code1).matches(EVENT_CODE + "-reg-[A-Z0-9]{6}");
        assertThat(code2).matches(EVENT_CODE + "-reg-[A-Z0-9]{6}");
    }

    @Test
    @DisplayName("#1315: a repeated pending registration answers 200 and mails a new confirmation link once")
    void should_return200AndResendConfirmation_when_pendingRegistrationRepeated() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_REGISTRATION))
                .andExpect(status().isCreated());
        sesRecorder.awaitAsyncSends();
        sesRecorder.reset();

        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_REGISTRATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "You are already registered for this event. A new confirmation email has been sent."))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"));

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(JOHN_MAIL)).singleElement()
                .satisfies(mail -> assertThat(mail.html()).contains(CONFIRM_LINK));
        assertThat(registrationRepository.findAll().stream()
                .filter(r -> testEvent.getId().equals(r.getEventId()))).hasSize(1);
    }

    @Test
    @DisplayName("QA Fix (VALID-001): a repeated confirmed registration answers 409 and mails nothing")
    void should_return409AndMailNothing_when_confirmedRegistrationRepeated() throws Exception {
        createTestRegistration("john.doe", "confirmed");

        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_REGISTRATION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already registered")));

        assertThat(registrationRepository.findAll().stream()
                .filter(r -> testEvent.getId().equals(r.getEventId()))).hasSize(1);
        // Positive control: a new registration in the same event is mailed.
        cums.user("jane.smith", "Jane", "Smith", JANE_MAIL);
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Jane\", \"lastName\": \"Smith\", \"email\": \""
                                + JANE_MAIL + "\", \"termsAccepted\": true}"))
                .andExpect(status().isCreated());
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(JANE_MAIL)).hasSize(1);
        assertThat(sesRecorder.mailsTo(JOHN_MAIL)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"en,Please confirm your registration", "de,Bitte bestätigen", "'',Bitte bestätigen"})
    @DisplayName("#1315: the confirmation mail of a registration is in the attendee's language, German by default")
    void should_mailConfirmationInPreferredLanguage_when_registering(String language, String subject)
            throws Exception {
        String address = givenLanguage("john.doe", language);

        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_REGISTRATION))
                .andExpect(status().isCreated());

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(address)).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).startsWith(subject));
    }

    @ParameterizedTest
    @CsvSource({"en,Please confirm your registration", "de,Bitte bestätigen", "'',Bitte bestätigen"})
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("#1315: the organizer's resend is in the attendee's language, German by default")
    void should_resendConfirmationInPreferredLanguage(String language, String subject) throws Exception {
        String address = givenLanguage("john.doe", language);
        Registration pending = createTestRegistration("john.doe", "registered");

        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/{registrationCode}/resend-confirmation",
                        EVENT_CODE, pending.getRegistrationCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Confirmation email resent to " + address));

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(address)).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).startsWith(subject));
    }

    /**
     * CUMS answers this user with this language ({@code <username>@test.com}), or, for an empty
     * language, with no settings (John at his simulator address).
     */
    private String givenLanguage(String username, String language) {
        if (language.isEmpty()) {
            return JOHN_MAIL;
        }
        cums.userWithLanguage(username, language);
        return username + "@test.com";
    }

    @Test
    @DisplayName("should_sendNoConfirmationLink_when_anonymousRegistrationLandsOnWaitlist")
    void should_sendNoConfirmationLink_when_anonymousRegistrationLandsOnWaitlist() throws Exception {
        Event capped = cappedOtherEvent();
        cums.user("jane.smith", "Jane", "Smith", JANE_MAIL);

        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", OTHER_EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_REGISTRATION))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", OTHER_EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Jane\", \"lastName\": \"Smith\", \"email\": \""
                                + JANE_MAIL + "\", \"termsAccepted\": true}"))
                .andExpect(status().isCreated());

        assertThat(registrationRepository.findByEventIdAndAttendeeUsername(capped.getId(), "jane.smith")
                .orElseThrow().getStatus()).isEqualTo("waitlist");
        sesRecorder.awaitAsyncSends();
        // Positive control: the first attendee got the confirmation link.
        assertThat(sesRecorder.mailsTo(JOHN_MAIL)).anyMatch(mail -> mail.html().contains(CONFIRM_LINK));
        assertThat(sesRecorder.mailsTo(JANE_MAIL)).noneMatch(mail -> mail.html().contains(CONFIRM_LINK));
    }

    // ============================================================================
    // Registration of the signed-in user (POST /my-registration)
    // ============================================================================

    @Test
    @DisplayName("should_confirmOrWaitlist_when_signedInUserRegisters")
    void should_confirmOrWaitlist_when_signedInUserRegisters() throws Exception {
        cappedOtherEvent();
        cums.user("jane.smith", "Jane", "Smith", JANE_MAIL);

        mockMvc.perform(post("/api/v1/events/{eventCode}/my-registration", OTHER_EVENT_CODE)
                        .with(signedIn("john.doe", JOHN_MAIL)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Registration confirmed."))
                .andExpect(jsonPath("$.email").value(JOHN_MAIL));

        // The event holds one: the next one waits.
        mockMvc.perform(post("/api/v1/events/{eventCode}/my-registration", OTHER_EVENT_CODE)
                        .with(signedIn("jane.smith", JANE_MAIL)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("You have been added to the waitlist for this event."))
                .andExpect(jsonPath("$.email").value(JANE_MAIL));
    }

    // ============================================================================
    // List registrations of an event (Story 3.3)
    // ============================================================================

    @Test
    @DisplayName("should_listRegistrations_when_eventHasRegistrations_withPagination")
    @WithMockUser(roles = "ORGANIZER")
    void should_listRegistrations_when_eventHasRegistrations() throws Exception {
        createTestRegistration("john.doe", "confirmed");
        createTestRegistration("jane.smith", "registered");

        mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].attendeeUsername").value("john.doe")) // sorted by last name
                .andExpect(jsonPath("$.data[0].eventCode").value(EVENT_CODE))
                .andExpect(jsonPath("$.data[0].attendeeFirstName").value("John")) // from CUMS
                .andExpect(jsonPath("$.data[1].attendeeUsername").value("jane.smith"))
                .andExpect(jsonPath("$.pagination.totalItems").value(2))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.limit").value(25));
    }

    @Test
    @DisplayName("should_returnEmptyList_when_eventHasNoRegistrations")
    @WithMockUser(roles = "ORGANIZER")
    void should_returnEmptyList_when_eventHasNoRegistrations() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.pagination.totalItems").value(0))
                .andExpect(jsonPath("$.pagination.totalPages").value(0));
    }

    @Test
    @DisplayName("should_return404_when_listingRegistrationsForNonExistentEvent")
    @WithMockUser(roles = "ORGANIZER")
    void should_return404_when_listingRegistrationsForNonExistentEvent() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", "BATbern99999"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"non-existent-id,1,25", EVENT_CODE + ",0,25", EVENT_CODE + ",1,0", EVENT_CODE + ",1,500"})
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("should_return400_when_listParametersViolateSpec")
    void should_return400_when_listParametersViolateSpec(String eventCode, String page, String limit)
            throws Exception {
        // Story 17.13: spec pattern of eventCode, page >= 1, 1 <= limit <= 100 (were 404 or clamped)
        mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", eventCode)
                        .param("page", page).param("limit", limit))
                .andExpect(status().isBadRequest());
        // Positive control: the same request within the limits answers.
        mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .param("page", "1").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.limit").value(100));
    }

    @Test
    @DisplayName("should_denyAccess_when_anonymousListsRegistrations")
    void should_denyAccess_when_anonymousListsRegistrations() throws Exception {
        // The production chain answers 401 before the controller (17.0b3).
        createTestRegistration("john.doe", "confirmed");

        mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", EVENT_CODE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "joe.user", roles = "ATTENDEE")
    @DisplayName("should_return403_when_nonOrganizerListsRegistrations")
    void should_return403_when_nonOrganizerListsRegistrations() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", EVENT_CODE))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should_returnPlaceholderData_when_userNotFound")
    @WithMockUser(roles = "ORGANIZER")
    void should_returnPlaceholderData_when_userNotFound() throws Exception {
        createTestRegistration("john.doe", "confirmed");
        createTestRegistration("deleted.user", "registered");
        cums.unknownUser("deleted.user");

        // The list still answers; the missing user is shown as such (a data-quality signal).
        mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].attendeeUsername").value("john.doe"))
                .andExpect(jsonPath("$.data[0].attendeeFirstName").value("John"))
                .andExpect(jsonPath("$.data[0].attendeeEmail").value(JOHN_MAIL))
                .andExpect(jsonPath("$.data[1].attendeeUsername").value("deleted.user"))
                .andExpect(jsonPath("$.data[1].attendeeFirstName").value("[User Not Found]"))
                .andExpect(jsonPath("$.data[1].attendeeLastName").value("[deleted.user]"))
                .andExpect(jsonPath("$.data[1].attendeeEmail").value("missing@unknown.invalid"));
    }

    @Test
    @DisplayName("should_filterByStatus_when_statusFilterProvided")
    @WithMockUser(roles = "ORGANIZER")
    void should_filterByStatus_when_statusFilterProvided() throws Exception {
        createTestRegistration("john.doe", "confirmed");
        createTestRegistration("jane.smith", "registered");
        createTestRegistration("bob.jones", "confirmed");

        mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", EVENT_CODE).param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination.totalItems").value(2))
                .andExpect(jsonPath("$.data[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data[1].status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("should_filterByMultipleStatuses_when_multipleStatusFiltersProvided")
    @WithMockUser(roles = "ORGANIZER")
    void should_filterByMultipleStatuses_when_multipleStatusFiltersProvided() throws Exception {
        createTestRegistration("john.doe", "confirmed");
        createTestRegistration("jane.smith", "registered");
        createTestRegistration("bob.jones", "cancelled");
        createTestRegistration("alice.williams", "attended");

        mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .param("status", "CONFIRMED")
                        .param("status", "ATTENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination.totalItems").value(2));
    }

    @Test
    @DisplayName("should_filterByCompanyId_when_companyIdProvided")
    @WithMockUser(roles = "ORGANIZER")
    void should_filterByCompanyId_when_companyIdProvided() throws Exception {
        saveParticipant("john.doe", "John", "Doe", "companyone");
        saveParticipant("jane.smith", "Jane", "Smith", "companytwo");

        mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", EVENT_CODE).param("companyId", "companyone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].attendeeUsername").value("john.doe"));
    }

    @Test
    @DisplayName("should_applyCombinedFilters_when_multipleFiltersProvided")
    @WithMockUser(roles = "ORGANIZER")
    void should_applyCombinedFilters_when_multipleFiltersProvided() throws Exception {
        createTestRegistration("john.doe", "confirmed");
        createTestRegistration("jane.doe", "confirmed");
        createTestRegistration("bob.jones", "registered");

        // status=CONFIRMED AND search="doe"
        mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .param("status", "CONFIRMED")
                        .param("search", "doe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination.totalItems").value(2))
                .andExpect(jsonPath("$.data[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data[1].status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("A waitlisted registration carries its queue position in the list, others null")
    void should_returnWaitlistPosition_when_listingRegistrations() throws Exception {
        // The organizer's waitlist view numbers and orders rows by this position (Epic 14 FR28).
        Registration waiting = createTestRegistration("anna.wartend", "waitlist");
        waiting.setWaitlistPosition(3);
        registrationRepository.save(waiting);
        createTestRegistration("ben.dabei", "confirmed");

        mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", EVENT_CODE).param("status", "WAITLIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].waitlistPosition").value(3));
        mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", EVENT_CODE).param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].waitlistPosition").value(nullValue()));
    }

    // ============================================================================
    // E-mail confirmation (Story 4.1.5c)
    // ============================================================================

    @Test
    @DisplayName("should_confirmRegistration_when_validTokenProvided")
    void should_confirmRegistration_when_validTokenProvided() throws Exception {
        Registration registration = createTestRegistration("john.doe", "registered");
        String token = confirmationTokenService.generateConfirmationToken(registration.getId(), EVENT_CODE);

        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/confirm", EVENT_CODE).param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Registration confirmed successfully!"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getStatus())
                .isEqualTo("confirmed");
        // The "registration confirmed" mail (with the calendar invite) goes out once.
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(JOHN_MAIL)).hasSize(1);
    }

    @Test
    @DisplayName("should_returnAlreadyConfirmed_when_registrationAlreadyConfirmed")
    void should_returnAlreadyConfirmed_when_registrationAlreadyConfirmed() throws Exception {
        Registration registration = createTestRegistration("john.doe", "registered");
        String token = confirmationTokenService.generateConfirmationToken(registration.getId(), EVENT_CODE);
        // Positive control: the first click mails the confirmation.
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/confirm", EVENT_CODE).param("token", token))
                .andExpect(status().isOk());
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(JOHN_MAIL)).hasSize(1);
        sesRecorder.reset();

        // A second click is idempotent and does not mail the calendar invite again.
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/confirm", EVENT_CODE).param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Registration already confirmed"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).isEmpty();
        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getStatus())
                .isEqualTo("confirmed");
    }

    @Test
    @DisplayName("should_return400_when_invalidTokenProvided")
    void should_return400_when_invalidTokenProvided() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/confirm", EVENT_CODE)
                        .param("token", "invalid.jwt.token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_return404_when_registrationNotFoundForToken")
    void should_return404_when_registrationNotFoundForToken() throws Exception {
        String token = confirmationTokenService.generateConfirmationToken(UUID.randomUUID(), EVENT_CODE);

        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/confirm", EVENT_CODE).param("token", token))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancelled", "waitlist", "attended", "no_show"})
    @DisplayName("#1315: an old confirmation link does not revive a processed registration")
    void should_return409_when_confirmingProcessedRegistration(String processed) throws Exception {
        Registration registration = createTestRegistration("john.doe", processed);
        String token = confirmationTokenService.generateConfirmationToken(registration.getId(), EVENT_CODE);

        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/confirm", EVENT_CODE).param("token", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code").value("REGISTRATION_ALREADY_PROCESSED"));

        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getStatus())
                .isEqualTo(processed);
        // Positive control: a pending registration's link confirms and mails.
        cums.user("jane.smith", "Jane", "Smith", JANE_MAIL);
        Registration pending = createTestRegistration("jane.smith", "registered");
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/confirm", EVENT_CODE)
                        .param("token", confirmationTokenService.generateConfirmationToken(pending.getId(), EVENT_CODE)))
                .andExpect(status().isOk());
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(JANE_MAIL)).hasSize(1);
        assertThat(sesRecorder.mailsTo(JOHN_MAIL)).isEmpty();
    }

    // ============================================================================
    // Newsletter opt-in of a registration: double opt-in (#1288)
    // ============================================================================

    private static final String NEW_REGISTRATION_WITH_NEWSLETTER = """
            {
                "firstName": "John",
                "lastName": "Doe",
                "email": "success+john.doe@simulator.amazonses.com",
                "termsAccepted": true,
                "communicationPreferences": {"newsletterSubscribed": true}
            }
            """;

    private Map<String, Object> newsletterRow(String email) {
        return jdbc.queryForMap("SELECT source, confirmed_at, unsubscribed_at, confirmation_token "
                + "FROM newsletter_subscribers WHERE lower(email) = ?", email);
    }

    @Test
    @DisplayName("#1288: the newsletter box of a registration stays pending without an extra mail, "
            + "and the registration's confirmation link activates it")
    void should_activateNewsletterOptIn_when_registrationIsConfirmed() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_REGISTRATION_WITH_NEWSLETTER))
                .andExpect(status().isCreated());

        assertThat(newsletterRow(JOHN_MAIL)).satisfies(row -> {
            assertThat(row.get("source")).isEqualTo("registration");
            assertThat(row.get("confirmed_at")).as("pending").isNull();
        });
        // Only the registration's confirmation mail, no newsletter confirmation.
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).singleElement().satisfies(mail -> {
            assertThat(mail.to()).contains(JOHN_MAIL);
            assertThat(mail.html()).contains(CONFIRM_LINK).doesNotContain("/newsletter/confirm");
        });
        Matcher token = CONFIRM_TOKEN.matcher(sesRecorder.mails().get(0).html());
        assertThat(token.find()).isTrue();

        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/confirm", EVENT_CODE)
                        .param("token", token.group(1)))
                .andExpect(status().isOk());

        assertThat(newsletterRow(JOHN_MAIL)).satisfies(row -> {
            assertThat(row.get("confirmed_at")).as("active").isNotNull();
            assertThat(row.get("unsubscribed_at")).isNull();
            assertThat(row.get("confirmation_token")).isNull();
        });
    }

    @Test
    @DisplayName("#1288: an ACCEPT reply that confirms the registration activates its newsletter opt-in")
    void should_activateNewsletterOptIn_when_registrationIsConfirmedByReply(
            @Autowired ch.batbern.events.service.RegistrationService registrationService) throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_REGISTRATION_WITH_NEWSLETTER))
                .andExpect(status().isCreated());
        assertThat(newsletterRow(JOHN_MAIL).get("confirmed_at")).isNull();

        registrationService.confirmByEmail(JOHN_MAIL, EVENT_CODE);

        assertThat(newsletterRow(JOHN_MAIL).get("confirmed_at")).isNotNull();
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("#1288: an organizer's confirmation does not activate the opt-in; its owner gets the confirmation mail")
    void should_mailNewsletterConfirmation_when_organizerConfirmsRegistration() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_REGISTRATION_WITH_NEWSLETTER))
                .andExpect(status().isCreated());
        sesRecorder.awaitAsyncSends();
        // Positive control for "no newsletter mail before": only the registration's link went out.
        assertThat(sesRecorder.mails()).singleElement()
                .satisfies(mail -> assertThat(mail.html()).doesNotContain("/newsletter/confirm"));
        sesRecorder.reset();
        Registration registration = registrationRepository
                .findByEventIdAndAttendeeUsername(testEvent.getId(), "john.doe").orElseThrow();

        mockMvc.perform(patch("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        EVENT_CODE, registration.getRegistrationCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"confirmed\"}"))
                .andExpect(status().isOk());

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(JOHN_MAIL)).singleElement()
                .satisfies(mail -> assertThat(mail.html()).contains("/newsletter/confirm?token="));
        assertThat(newsletterRow(JOHN_MAIL)).satisfies(row -> {
            assertThat(row.get("confirmed_at")).as("still pending").isNull();
            assertThat(row.get("confirmation_token")).isNotNull();
        });
    }

    @Test
    @DisplayName("#1288: confirming a registration without the newsletter box leaves an anonymous request pending")
    void should_leaveAnonymousRequestPending_when_registrationWithoutBoxIsConfirmed() throws Exception {
        // Someone else asked for the newsletter at this address: pending, waiting for its own link.
        mockMvc.perform(post("/api/v1/newsletter/subscribers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + JOHN_MAIL + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "John", "lastName": "Doe",
                                 "email": "success+john.doe@simulator.amazonses.com", "termsAccepted": true}
                                """))
                .andExpect(status().isCreated());
        Registration registration = registrationRepository
                .findByEventIdAndAttendeeUsername(testEvent.getId(), "john.doe").orElseThrow();

        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/confirm", EVENT_CODE)
                        .param("token", confirmationTokenService.generateConfirmationToken(
                                registration.getId(), EVENT_CODE)))
                .andExpect(status().isOk());

        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getStatus())
                .isEqualTo("confirmed");
        assertThat(newsletterRow(JOHN_MAIL)).satisfies(row -> {
            assertThat(row.get("source")).isEqualTo("explicit");
            assertThat(row.get("confirmed_at")).as("still pending").isNull();
            assertThat(row.get("confirmation_token")).isNotNull();
        });
    }

    // ============================================================================
    // Organizer PATCH and DELETE
    // ============================================================================

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("should_updateStatus_when_patchWithTypedStatusBody")
    void should_updateStatus_when_patchWithTypedStatusBody() throws Exception {
        Registration registration = createTestRegistration("john.doe", "registered");

        mockMvc.perform(patch("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        EVENT_CODE, registration.getRegistrationCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"confirmed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationCode").value(registration.getRegistrationCode()))
                .andExpect(jsonPath("$.status").value("CONFIRMED")); // response status is upper case

        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getStatus())
                .isEqualTo("confirmed"); // DB constraint is lower case
    }

    @Test
    @WithMockUser(username = "joe.user", roles = "ATTENDEE")
    @DisplayName("should_return403_when_nonOrganizerPatchesRegistration")
    void should_return403_when_nonOrganizerPatchesRegistration() throws Exception {
        Registration registration = createTestRegistration("john.doe", "confirmed");

        mockMvc.perform(patch("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        EVENT_CODE, registration.getRegistrationCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"cancelled\"}"))
                .andExpect(status().isForbidden());

        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getStatus())
                .isEqualTo("confirmed");
    }

    @Test
    @WithMockUser(username = "joe.user", roles = "ATTENDEE")
    @DisplayName("should_return403_when_nonOrganizerDeletesRegistration")
    void should_return403_when_nonOrganizerDeletesRegistration() throws Exception {
        Registration registration = createTestRegistration("john.doe", "confirmed");

        mockMvc.perform(delete("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        EVENT_CODE, registration.getRegistrationCode()))
                .andExpect(status().isForbidden());

        assertThat(registrationRepository.findById(registration.getId())).isPresent();
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("should_deleteRegistration_when_organizerDeletes")
    void should_deleteRegistration_when_organizerDeletes() throws Exception {
        Registration registration = createTestRegistration("john.doe", "confirmed");

        mockMvc.perform(delete("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        EVENT_CODE, registration.getRegistrationCode()))
                .andExpect(status().isNoContent());

        assertThat(registrationRepository.findById(registration.getId())).isEmpty();
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("should_return404_when_registrationBelongsToAnotherEvent")
    void should_return404_when_registrationBelongsToAnotherEvent() throws Exception {
        // Story 17.13: PATCH and DELETE answer a code of event B under event A like an unknown code,
        // as promote and resend do; the message no longer names the other event.
        Event other = eventRepository.save(event(OTHER_EVENT_CODE, 1714));
        Registration elsewhere = registrationRepository.save(registration(other, "john.doe", "confirmed"));
        // Positive control: under its own event the registration is found.
        mockMvc.perform(patch("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        OTHER_EVENT_CODE, elsewhere.getRegistrationCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        EVENT_CODE, elsewhere.getRegistrationCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"cancelled\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", not(containsString("does not belong"))));
        mockMvc.perform(delete("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        EVENT_CODE, elsewhere.getRegistrationCode()))
                .andExpect(status().isNotFound());

        Registration unchanged = registrationRepository.findById(elsewhere.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo("confirmed");

        // Promote: a waitlisted registration of event B is not found under event A and keeps waiting.
        cums.user("ben.waiting", "Ben", "Waiting", "success+ben.waiting@simulator.amazonses.com");
        Registration waiting = registration(other, "ben.waiting", "waitlist");
        waiting.setWaitlistPosition(1);
        waiting = registrationRepository.save(waiting);
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/{registrationCode}/promote",
                        EVENT_CODE, waiting.getRegistrationCode()))
                .andExpect(status().isNotFound());
        assertThat(registrationRepository.findById(waiting.getId()).orElseThrow().getStatus()).isEqualTo("waitlist");
        // Positive control: under its own event it is promoted.
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/{registrationCode}/promote",
                        OTHER_EVENT_CODE, waiting.getRegistrationCode()))
                .andExpect(status().isNoContent());
        assertThat(registrationRepository.findById(waiting.getId()).orElseThrow().getStatus())
                .isEqualTo("registered");
    }

    /** A signed-in caller with this username and e-mail claim (no role needed). */
    private static RequestPostProcessor signedIn(String username, String email) {
        return jwt().jwt(token -> token.subject("sub-" + username)
                .claim("custom:username", username)
                .claim("email", email));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("should_return404_when_registrationOrEventUnknown")
    void should_return404_when_registrationOrEventUnknown() throws Exception {
        mockMvc.perform(delete("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        EVENT_CODE, EVENT_CODE + "-reg-ABC123"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        "BATbern99999", "BATbern99999-reg-ABC123"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("should_return400_when_registrationCodeViolatesSpecPattern")
    void should_return400_when_registrationCodeViolatesSpecPattern() throws Exception {
        Registration registration = createTestRegistration("john.doe", "confirmed");

        mockMvc.perform(delete("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        EVENT_CODE, "not-a-registration-code"))
                .andExpect(status().isBadRequest());
        assertThat(registrationRepository.findById(registration.getId())).isPresent();
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("should_resendWorkingConfirmation_when_registrationPending_and_refuseOtherwise")
    void should_resendWorkingConfirmation_when_registrationPending_and_refuseOtherwise() throws Exception {
        Registration pending = createTestRegistration("john.doe", "registered");

        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/{registrationCode}/resend-confirmation",
                        EVENT_CODE, pending.getRegistrationCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Confirmation email resent to " + JOHN_MAIL));
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(JOHN_MAIL)).hasSize(1);
        Matcher token = CONFIRM_TOKEN.matcher(sesRecorder.mailsTo(JOHN_MAIL).get(0).html());
        assertThat(token.find()).as("confirmation link in the mail").isTrue();
        // The resent token confirms the registration.
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/confirm", EVENT_CODE)
                        .param("token", token.group(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Registration confirmed successfully!"));
        sesRecorder.awaitAsyncSends();
        sesRecorder.reset();

        // Now confirmed: 409, nothing sent.
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/{registrationCode}/resend-confirmation",
                        EVENT_CODE, pending.getRegistrationCode()))
                .andExpect(status().isConflict());
        // A pending registration of another event under this one: 404, nothing sent.
        Event other = eventRepository.save(event(OTHER_EVENT_CODE, 1714));
        Registration elsewhere = registrationRepository.save(registration(other, "jane.smith", "registered"));
        cums.user("jane.smith", "Jane", "Smith", JANE_MAIL);
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/{registrationCode}/resend-confirmation",
                        EVENT_CODE, elsewhere.getRegistrationCode()))
                .andExpect(status().isNotFound());
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).isEmpty();
    }

    // ============================================================================
    // Organizer status change: the transition table (#1315)
    // ============================================================================

    private static final List<String> STATUSES =
            List.of("registered", "waitlist", "confirmed", "cancelled", "attended", "no_show");

    /** The 36 pairs; the table of docs/architecture/06-backend-architecture.md, "Organizer status changes (#1315)". */
    static Stream<Arguments> everyTransition() {
        Map<String, Set<String>> allowed = Map.of(
                "registered", Set.of("confirmed", "cancelled", "attended", "no_show"),
                "waitlist", Set.of("confirmed", "cancelled"),
                "confirmed", Set.of("cancelled", "attended", "no_show"),
                "attended", Set.of("confirmed", "cancelled", "no_show"),
                "no_show", Set.of("confirmed", "cancelled", "attended"),
                "cancelled", Set.of());
        return STATUSES.stream().flatMap(from -> STATUSES.stream().map(to -> {
            boolean ok = from.equals(to) || allowed.get(from).contains(to);
            // waitlist -> confirmed is the promote endpoint: the registration ends as registered.
            String end = !ok ? from : "waitlist".equals(from) && "confirmed".equals(to) ? "registered" : to;
            return Arguments.of(from, to, ok ? 200 : 409, end);
        }));
    }

    @ParameterizedTest(name = "{0} -> {1}: {2}, ends {3}")
    @MethodSource("everyTransition")
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("#1315: every status pair answers 200 or 409 as the table says, never 500")
    void should_followTransitionTable_when_organizerPatchesStatus(String from, String to, int expected, String end)
            throws Exception {
        Registration registration = registration(testEvent, "john.doe", from);
        if ("waitlist".equals(from)) {
            registration.setWaitlistPosition(1);
        }
        registration = registrationRepository.save(registration);

        var result = patchStatus(EVENT_CODE, registration.getRegistrationCode(), "{\"status\":\"" + to + "\"}")
                .andExpect(status().is(expected));
        if (expected == 409) {
            result.andExpect(jsonPath("$.details.code").value("REGISTRATION_TRANSITION_NOT_ALLOWED"));
        } else {
            result.andExpect(jsonPath("$.status").value(end.toUpperCase(Locale.ROOT)));
        }
        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getStatus()).isEqualTo(end);
    }

    @ParameterizedTest
    @CsvSource({"cancelled,confirmed", "registered,waitlist", "confirmed,waitlist", "confirmed,registered"})
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("#1315: a transition outside the table answers 409, changes nothing and mails nobody")
    void should_return409AndMailNobody_when_transitionNotInTable(String from, String to) throws Exception {
        Registration registration = createTestRegistration("john.doe", from);
        Instant updatedAt = registrationRepository.findById(registration.getId()).orElseThrow().getUpdatedAt();

        patchStatus(EVENT_CODE, registration.getRegistrationCode(), "{\"status\":\"" + to + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code").value("REGISTRATION_TRANSITION_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value(containsString(from + " to " + to)));

        Registration unchanged = registrationRepository.findById(registration.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(from);
        assertThat(unchanged.getUpdatedAt()).isEqualTo(updatedAt);
        // Positive control: the one PATCH that mails (waitlist -> confirmed) reaches Jane.
        cums.user("jane.smith", "Jane", "Smith", JANE_MAIL);
        Registration waiting = registration(testEvent, "jane.smith", "waitlist");
        waiting.setWaitlistPosition(1);
        waiting = registrationRepository.save(waiting);
        patchStatus(EVENT_CODE, waiting.getRegistrationCode(), "{\"status\":\"confirmed\"}").andExpect(status().isOk());
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(JANE_MAIL)).hasSize(1);
        assertThat(sesRecorder.mailsTo(JOHN_MAIL)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMED", "foo", ""})
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("#1315: a status outside the lower-case enum answers 400 (was 500 or a stored bad value)")
    void should_return400_when_statusNotInEnum(String value) throws Exception {
        Registration registration = createTestRegistration("john.doe", "registered");

        patchStatus(EVENT_CODE, registration.getRegistrationCode(), "{\"status\":\"" + value + "\"}")
                .andExpect(status().isBadRequest());

        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getStatus())
                .isEqualTo("registered");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":\"confirmed\"}"})
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("#1315: no status or the same status writes nothing and mails nobody")
    void should_writeNothing_when_statusMissingOrUnchanged(String body) throws Exception {
        Registration registration = createTestRegistration("john.doe", "confirmed");
        Instant updatedAt = registrationRepository.findById(registration.getId()).orElseThrow().getUpdatedAt();

        patchStatus(EVENT_CODE, registration.getRegistrationCode(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getUpdatedAt())
                .isEqualTo(updatedAt);
        // Positive control: a real change writes.
        patchStatus(EVENT_CODE, registration.getRegistrationCode(), "{\"status\":\"attended\"}")
                .andExpect(status().isOk());
        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getUpdatedAt())
                .isAfter(updatedAt);
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).isEmpty();
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("#1315: registered -> confirmed is refused while the event holds more than its capacity")
    void should_checkCapacity_when_organizerConfirmsPendingRegistration(CapturedOutput output) throws Exception {
        Event capped = cappedOtherEvent(); // capacity 1
        Registration john = registrationRepository.save(registration(capped, "john.doe", "registered"));
        Registration jane = registrationRepository.save(registration(capped, "jane.smith", "registered"));

        // Two hold a place, one fits: confirming John would keep the event over capacity.
        patchStatus(OTHER_EVENT_CODE, john.getRegistrationCode(), "{\"status\":\"confirmed\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code").value("capacity_exceeded"))
                .andExpect(jsonPath("$.details.activeCount").value(2))
                .andExpect(jsonPath("$.details.capacity").value(1))
                .andExpect(jsonPath("$.message").value(not(containsString("force"))));
        assertThat(registrationRepository.findById(john.getId()).orElseThrow().getStatus()).isEqualTo("registered");
        // The log names the refused operation, not the add-participant dialog.
        assertThat(output.getOut()).contains("PATCH /api/v1/events/" + OTHER_EVENT_CODE + "/registrations/"
                + john.getRegistrationCode() + " refused, event at capacity");
        assertThat(output.getOut()).doesNotContain("Add-participant refused");

        // At capacity (John's own place counted): confirmed.
        patchStatus(OTHER_EVENT_CODE, jane.getRegistrationCode(), "{\"status\":\"cancelled\"}")
                .andExpect(status().isOk());
        patchStatus(OTHER_EVENT_CODE, john.getRegistrationCode(), "{\"status\":\"confirmed\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("#1315: waitlist -> confirmed is the promote endpoint: registered, position cleared, mail")
    void should_promote_when_organizerConfirmsWaitlistedRegistration() throws Exception {
        Event capped = cappedOtherEvent(); // capacity 1, already full: the promotion ignores it
        registrationRepository.save(registration(capped, "jane.smith", "confirmed"));
        Registration waiting = registration(capped, "john.doe", "waitlist");
        waiting.setWaitlistPosition(1);
        waiting = registrationRepository.save(waiting);

        patchStatus(OTHER_EVENT_CODE, waiting.getRegistrationCode(), "{\"status\":\"confirmed\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.waitlistPosition").value(nullValue()));

        Registration promoted = registrationRepository.findById(waiting.getId()).orElseThrow();
        assertThat(promoted.getStatus()).isEqualTo("registered");
        assertThat(promoted.getWaitlistPosition()).isNull();
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(JOHN_MAIL)).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).contains("Warteliste"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("#1315: waitlist -> cancelled leaves the queue, renumbers it and promotes nobody")
    void should_renumberWaitlist_when_organizerCancelsWaitlistedRegistration() throws Exception {
        Event capped = event(OTHER_EVENT_CODE, 1714);
        capped.setRegistrationCapacity(2);
        capped = eventRepository.save(capped);
        registrationRepository.save(registration(capped, "jane.smith", "confirmed")); // one place free
        Registration first = waiting(capped, "anna.erste", 1);
        Registration second = waiting(capped, "ben.zweite", 2);
        Registration third = waiting(capped, "carl.dritte", 3);

        patchStatus(OTHER_EVENT_CODE, second.getRegistrationCode(), "{\"status\":\"cancelled\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Registration left = registrationRepository.findById(second.getId()).orElseThrow();
        assertThat(left.getStatus()).isEqualTo("cancelled");
        assertThat(left.getWaitlistPosition()).isNull();
        // Nobody moves up: a waitlisted registration held no place.
        assertThat(registrationRepository.findById(first.getId()).orElseThrow())
                .satisfies(r -> assertThat(r.getStatus()).isEqualTo("waitlist"))
                .satisfies(r -> assertThat(r.getWaitlistPosition()).isEqualTo(1));
        assertThat(registrationRepository.findById(third.getId()).orElseThrow())
                .satisfies(r -> assertThat(r.getStatus()).isEqualTo("waitlist"))
                .satisfies(r -> assertThat(r.getWaitlistPosition()).isEqualTo(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"registered", "confirmed"})
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("#1315: cancelling a place holder promotes the first waiting in the same request")
    void should_promoteFromWaitlist_when_organizerCancelsPlaceHolder(String holder) throws Exception {
        Event capped = cappedOtherEvent(); // capacity 1
        Registration leaving = registrationRepository.save(registration(capped, "jane.smith", holder));
        Registration waiting = waiting(capped, "john.doe", 1);

        patchStatus(OTHER_EVENT_CODE, leaving.getRegistrationCode(), "{\"status\":\"cancelled\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(registrationRepository.findById(leaving.getId()).orElseThrow().getStatus()).isEqualTo("cancelled");
        assertThat(registrationRepository.findById(waiting.getId()).orElseThrow().getStatus()).isEqualTo("registered");
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(JOHN_MAIL)).hasSize(1); // the promotion mail
    }

    @ParameterizedTest
    @ValueSource(strings = {"registered", "confirmed"})
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("#1315: deleting a place holder gives the place to the first waiting")
    void should_promoteFromWaitlist_when_organizerDeletesPlaceHolder(String holder) throws Exception {
        Event capped = cappedOtherEvent(); // capacity 1
        Registration leaving = registrationRepository.save(registration(capped, "jane.smith", holder));
        Registration first = waiting(capped, "john.doe", 1);
        Registration second = waiting(capped, "ben.zweite", 2);

        mockMvc.perform(delete("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        OTHER_EVENT_CODE, leaving.getRegistrationCode()))
                .andExpect(status().isNoContent());

        assertThat(registrationRepository.findById(leaving.getId())).isEmpty();
        assertThat(registrationRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo("registered");
        assertThat(registrationRepository.findById(second.getId()).orElseThrow().getStatus()).isEqualTo("waitlist");
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(JOHN_MAIL)).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).contains("Warteliste"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("#1315: deleting a waitlisted registration renumbers the queue and promotes nobody")
    void should_renumberWaitlist_when_organizerDeletesWaitlistedRegistration() throws Exception {
        Event capped = event(OTHER_EVENT_CODE, 1714);
        capped.setRegistrationCapacity(2);
        capped = eventRepository.save(capped);
        registrationRepository.save(registration(capped, "jane.smith", "confirmed")); // one place free
        Registration first = waiting(capped, "anna.erste", 1);
        Registration second = waiting(capped, "john.doe", 2);

        mockMvc.perform(delete("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        OTHER_EVENT_CODE, first.getRegistrationCode()))
                .andExpect(status().isNoContent());

        assertThat(registrationRepository.findById(first.getId())).isEmpty();
        assertThat(registrationRepository.findById(second.getId()).orElseThrow())
                .satisfies(r -> assertThat(r.getStatus()).isEqualTo("waitlist"))
                .satisfies(r -> assertThat(r.getWaitlistPosition()).isEqualTo(1));
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(JOHN_MAIL)).isEmpty();
        // Positive control: deleting the place holder now promotes John, with the mail.
        Registration holder = registrationRepository.findByEventIdAndAttendeeUsername(capped.getId(), "jane.smith")
                .orElseThrow();
        mockMvc.perform(delete("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        OTHER_EVENT_CODE, holder.getRegistrationCode()))
                .andExpect(status().isNoContent());
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(JOHN_MAIL)).hasSize(1);
    }

    private ResultActions patchStatus(String eventCode, String registrationCode,
            String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        eventCode, registrationCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** A waitlisted registration at this position. */
    private Registration waiting(Event event, String username, int position) {
        Registration registration = registration(event, username, "waitlist");
        registration.setWaitlistPosition(position);
        return registrationRepository.save(registration);
    }

    // ============================================================================
    // Participant search. Every word in username, first/last name, e-mail or
    // company id (denormalized columns), folded, any order, in SQL before paging.
    // ============================================================================

    private void saveParticipants() {
        saveParticipant("anna.mueller", "Anna", "Müller", "mobiliar");
        saveParticipant("ben.mueller", "Ben", "Mueller", "sbb");
        saveParticipant("carl.muller", "Carl", "Muller", "post");
        saveParticipant("hp.muster", "Hans Peter", "Muster", "swisscom");
    }

    @ParameterizedTest
    @CsvSource({
        "Müller,3", "Mueller,3", "Muller,3", "muster hans,1", "peter,1", "hans muster,1",
        "mobiliar,1", "ben.mueller@example,1", "anna mobiliar,1", "anna sbb,0"
    })
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("Participant search folds, matches any word order and every field")
    void should_findParticipants_bySearch(String input, int expected) throws Exception {
        saveParticipants();

        mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", EVENT_CODE).param("search", input))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(expected));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("A match beyond page 1 is returned and totalItems counts all")
    void should_returnParticipantBeyondFirstPage() throws Exception {
        saveParticipants();

        mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .param("search", "mueller").param("page", "2").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.pagination.totalItems").value(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"(test", "a  b", "100%", "'", "C++", "", "   "})
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("Hostile or blank participant search answers 2xx; blank is no search")
    void should_answer2xx_when_participantSearchIsHostileOrBlank(String input) throws Exception {
        saveParticipants();

        var result = mockMvc.perform(get("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .param("search", input))
                .andExpect(status().isOk());
        if (input.isBlank()) {
            result.andExpect(jsonPath("$.pagination.totalItems").value(4));
        }
        if ("100%".equals(input)) {
            result.andExpect(jsonPath("$.pagination.totalItems").value(0));
        }
    }

    // ============================================================================
    // Helpers
    // ============================================================================

    private static Event event(String eventCode, int eventNumber) {
        Instant date = Instant.now().plus(60, ChronoUnit.DAYS);
        return Event.builder()
                .eventCode(eventCode)
                .title("Registration Test " + eventNumber)
                .eventNumber(eventNumber)
                .date(date)
                .registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address 123, Bern")
                .venueCapacity(100)
                .organizerUsername("test.organizer")
                .currentAttendeeCount(0)
                .description("Test event for registration")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.CREATED)
                .build();
    }

    /** The other event, with room for one registration. */
    private Event cappedOtherEvent() {
        Event capped = event(OTHER_EVENT_CODE, 1714);
        capped.setRegistrationCapacity(1);
        return eventRepository.save(capped);
    }

    /** A registration with the denormalized attendee fields derived from the username. */
    private static Registration registration(Event event, String username, String status) {
        String[] names = username.split("\\.");
        return Registration.builder()
                .registrationCode(event.getEventCode() + "-reg-" + randomCode())
                .eventId(event.getId())
                .attendeeUsername(username)
                .attendeeFirstName(capitalize(names[0]))
                .attendeeLastName(capitalize(names[1]))
                .attendeeEmail(username + "@example.com")
                .status(status)
                .deregistrationToken(UUID.randomUUID())
                .registrationDate(Instant.now())
                .build();
    }

    private Registration createTestRegistration(String username, String status) {
        return registrationRepository.save(registration(testEvent, username, status));
    }

    private void saveParticipant(String username, String first, String last, String companyId) {
        registrationRepository.save(Registration.builder()
                .registrationCode(EVENT_CODE + "-reg-" + randomCode())
                .eventId(testEvent.getId())
                .attendeeUsername(username)
                .attendeeFirstName(first)
                .attendeeLastName(last)
                .attendeeEmail(username + "@example.com")
                .attendeeCompanyId(companyId)
                .status("registered")
                .registrationDate(Instant.now())
                .build());
    }

    /** Unique per call: the registration code is unique across all tests. */
    private static String randomCode() {
        return "TEST" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    /** A {@code GetOrCreateUserResponse} for the requested person, username {@code first.last}. */
    private String getOrCreateReply(Call call, boolean companyUpdated) {
        JsonNode request;
        try {
            request = objectMapper.readTree(call.body());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        String email = request.get("email").asText();
        String username = (request.get("firstName").asText() + "." + request.get("lastName").asText())
                .toLowerCase(Locale.ROOT);
        String user = CumsContractHarness.userJson(username, request.get("firstName").asText(),
                request.get("lastName").asText(), email,
                request.hasNonNull("companyId") ? request.get("companyId").asText() : null);
        return "{\"username\": \"" + username + "\", \"created\": true, \"companyUpdated\": " + companyUpdated
                + ", \"cognitoUserId\": null, \"user\": " + user + "}";
    }
}
