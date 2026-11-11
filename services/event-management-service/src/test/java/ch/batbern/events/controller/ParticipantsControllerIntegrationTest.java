package ch.batbern.events.controller;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer.Call;
import ch.batbern.shared.test.contract.ContractServer.Reply;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link ParticipantsController}: the one participants create (Story 4.10c:
 * single add, historical import, stakeholders), the badge exports and the per-event distribution
 * lists.
 *
 * <p>Production chain, CUMS as the {@link CumsContractHarness} and SES as the {@link SesRecorder}
 * (no own-code mocks). Not {@code @Transactional}: the import saves each person on its own, so
 * the test cleans up its events in {@code @AfterEach}.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class ParticipantsControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionUserRepository sessionUserRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;

    private static final String EVENT_CODE = "BATbern1999";
    private static final String FULL_EVENT_CODE = "BATbern1998";
    private static final String ORGANIZER_USERNAME = "organizer.alice";
    /** The positive control's attendee: an AWS mailbox-simulator address, so it may receive mail. */
    private static final String CONTROL = "pia.control";
    private static final String CONTROL_MAIL = "success+pia.control@simulator.amazonses.com";

    private Event event;

    @BeforeEach
    void setUp() {
        cleanUp();
        cums.reset();
        sesRecorder.reset();

        event = Event.builder()
                .eventCode(EVENT_CODE)
                .eventNumber(1999)
                .title("XLSX Integration Event")
                .eventType(EventType.EVENING)
                .date(Instant.now().plus(30, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(20, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Bern, CH")
                .venueCapacity(200)
                .organizerUsername(ORGANIZER_USERNAME)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .build();
        event = eventRepository.save(event);

        cums.usersWithRole("ORGANIZER", ORGANIZER_USERNAME);
        cums.user(CONTROL, "Pia", "Control", CONTROL_MAIL);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        cums.reset();
    }

    /** Deletes this class's events and everything under them (the test commits). */
    private void cleanUp() {
        String events = "SELECT id FROM events WHERE event_code IN ('" + EVENT_CODE + "', '" + FULL_EVENT_CODE + "')";
        jdbc.update("DELETE FROM registrations WHERE event_id IN (" + events + ")");
        jdbc.update("DELETE FROM session_users WHERE session_id IN (SELECT id FROM sessions WHERE event_id IN ("
                + events + "))");
        jdbc.update("DELETE FROM sessions WHERE event_id IN (" + events + ")");
        jdbc.update("DELETE FROM events WHERE event_code IN (?, ?)", EVENT_CODE, FULL_EVENT_CODE);
    }

    // ---- F3: XLSX export ----

    @Test
    @DisplayName("Organizer GET /participants/export.xlsx → 200 + valid XLSX with expected attachment header")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_returnXlsx_when_organizerExportsParticipants() throws Exception {
        seedSessionPrimarySpeaker("speaker.bob");
        seedRegistration("attendee.carol", "confirmed");

        MvcResult result = mockMvc.perform(get("/api/v1/events/{eventCode}/participants/export.xlsx", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(EVENT_CODE + "-namensschilder.xlsx")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotEmpty();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            Sheet sheet = wb.getSheetAt(0);
            // Header row + 3 rows (alice organizer, bob speaker, carol attendee).
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Vorname");
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("Non-organizer GET /participants/export.xlsx → 403")
    @WithMockUser(username = "joe.user", roles = {"ATTENDEE"})
    void should_return403_when_nonOrganizerExports() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/participants/export.xlsx", EVENT_CODE))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unknown event → 404")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_return404_when_unknownEvent() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/participants/export.xlsx", "BATbern99999"))
                .andExpect(status().isNotFound());
    }

    // ---- F3 (extension): DOCX export ----

    @Test
    @DisplayName("Organizer GET /participants/export.docx → 200 + valid OOXML DOCX with attachment header")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_returnDocx_when_organizerExportsParticipantsDocx() throws Exception {
        seedSessionPrimarySpeaker("speaker.bob");
        seedRegistration("attendee.carol", "confirmed");

        MvcResult result = mockMvc.perform(
                        get("/api/v1/events/{eventCode}/participants/export.docx", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(EVENT_CODE + "-namensschilder.docx")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotEmpty();
        // DOCX is a ZIP — verify the OOXML magic bytes (PK\x03\x04).
        assertThat(body[0]).isEqualTo((byte) 0x50);
        assertThat(body[1]).isEqualTo((byte) 0x4B);
        assertThat(body[2]).isEqualTo((byte) 0x03);
        assertThat(body[3]).isEqualTo((byte) 0x04);

        // Re-parse with POI to confirm three rendered badges in page 1.
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc =
                     new org.apache.poi.xwpf.usermodel.XWPFDocument(new ByteArrayInputStream(body))) {
            assertThat(doc.getTables()).hasSize(1);
            // Header sanity: the first table should hold the canonical 9 rows × 5 cells.
            assertThat(doc.getTables().get(0).getRows()).hasSize(9);
        }
    }

    @Test
    @DisplayName("Non-organizer GET /participants/export.docx → 403")
    @WithMockUser(username = "joe.user", roles = {"ATTENDEE"})
    void should_return403_when_nonOrganizerExportsDocx() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/participants/export.docx", EVENT_CODE))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unknown event DOCX → 404")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_return404_when_unknownEventDocx() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/participants/export.docx", "BATbern99999"))
                .andExpect(status().isNotFound());
    }

    // ---- F2: distribution list ----

    @Test
    @DisplayName("Organizer GET /distribution-list/speakers → 200 with scheduled primary speakers")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_return200_when_organizerGetsSpeakersList() throws Exception {
        seedScheduledSession("speaker.bob");

        // #1244: a copy only to a confirmed address with the copy switched on
        cums.user("speaker.bob", """
                {"firstName": "Speaker", "lastName": "Bob", "email": "bob@example.com",
                 "additionalEmails": [{"email": "bob.work@example.com", "label": null,
                   "createdAt": "2026-01-10T10:00:00Z", "verifiedAt": "2026-01-10T10:05:00Z",
                   "receiveEventMail": true}]}""");

        mockMvc.perform(get("/api/v1/events/{eventCode}/distribution-list/{kind}",
                        EVENT_CODE, "speakers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(EVENT_CODE))
                .andExpect(jsonPath("$.kind").value("speakers"))
                .andExpect(jsonPath("$.emails", org.hamcrest.Matchers.hasItem("bob@example.com")))
                .andExpect(jsonPath("$.emails", org.hamcrest.Matchers.hasItem("bob.work@example.com")));
    }

    @Test
    @DisplayName("Organizer GET /distribution-list/moderator → 200 with organizer email")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_return200_when_organizerGetsModeratorList() throws Exception {
        cums.user(ORGANIZER_USERNAME, "Alice", "Organizer", "alice@batbern.ch");

        mockMvc.perform(get("/api/v1/events/{eventCode}/distribution-list/{kind}",
                        EVENT_CODE, "moderator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("moderator"))
                .andExpect(jsonPath("$.emails[0]").value("alice@batbern.ch"));
    }

    @Test
    @DisplayName("Organizer GET /distribution-list/participants → 200 with active registrants, excludes cancelled/waitlist")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_return200_when_organizerGetsParticipantsList() throws Exception {
        seedRegistrationWithEmail("anna.active", "anna@example.com", "registered");
        seedRegistrationWithEmail("ben.active", "ben@example.com", "confirmed");
        seedRegistrationWithEmail("carl.cancelled", "carl@example.com", "cancelled");
        seedRegistrationWithEmail("wanda.wait", "wanda@example.com", "waitlist");

        mockMvc.perform(get("/api/v1/events/{eventCode}/distribution-list/{kind}",
                        EVENT_CODE, "participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("participants"))
                .andExpect(jsonPath("$.emails", org.hamcrest.Matchers.hasItem("anna@example.com")))
                .andExpect(jsonPath("$.emails", org.hamcrest.Matchers.hasItem("ben@example.com")))
                .andExpect(jsonPath("$.emails",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("carl@example.com"))))
                .andExpect(jsonPath("$.emails",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("wanda@example.com"))));
    }

    @Test
    @DisplayName("Organizer GET /distribution-list with unknown event → 404")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_return404_when_unknownEventOnDistributionList() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/distribution-list/{kind}",
                        "BATbern99999", "speakers"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Organizer GET /distribution-list with unknown kind → 404 (defensive)")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_return404_when_unknownKind() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/distribution-list/{kind}",
                        EVENT_CODE, "foo"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Anonymous GET /distribution-list → denied, no email list leaves the service")
    void should_denyAccess_when_anonymousGetsDistributionList() throws Exception {
        // The production chain answers 401 before the controller (17.0b3).
        seedRegistrationWithEmail("anna.active", "anna@example.com", "registered");

        mockMvc.perform(get("/api/v1/events/{eventCode}/distribution-list/{kind}",
                        EVENT_CODE, "participants"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Attendee GET /distribution-list → 403")
    @WithMockUser(username = "joe.user", roles = {"ATTENDEE"})
    void should_return403_when_nonOrganizerGetsDistributionList() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/distribution-list/{kind}",
                        EVENT_CODE, "participants"))
                .andExpect(status().isForbidden());
    }

    // ---- POST /participants: single add (Story 4.10c mode username) ----

    @Test
    @DisplayName("Single add with notify → 201 confirmed, real attendee, exactly one organizer-added mail")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_addConfirmedAndSendOneMail_when_singleAddWithNotify() throws Exception {
        String jane = "success+jane.doe@simulator.amazonses.com";
        cums.user("jane.doe", "Jane", "Doe", jane);

        // notify omitted → defaults true.
        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jane.doe\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("confirmed"))
                .andExpect(jsonPath("$.attendeeUsername").value("jane.doe"))
                .andExpect(jsonPath("$.registrationCode").isNotEmpty())
                .andExpect(jsonPath("$.enrolled").doesNotExist())
                .andExpect(jsonPath("$.results").doesNotExist());

        Registration reg = registrationRepository
                .findByEventIdAndAttendeeUsername(event.getId(), "jane.doe").orElseThrow();
        assertThat(reg.getStatus()).isEqualTo("confirmed");
        assertThat(reg.isProgrammatic()).isFalse(); // real attendee — counts for capacity + delete-guard
        assertThat(reg.getMetadata()).containsEntry("addedByOrganizer", ORGANIZER_USERNAME);

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).singleElement().satisfies(mail -> {
            assertThat(mail.isAddressedTo(jane)).isTrue();
            // the organizer-added confirmation, not the waitlist promotion
            assertThat(mail.subject()).startsWith("Registration confirmed");
        });
    }

    @Test
    @DisplayName("Single add with notify=false → 201 and no mail; the notify add next to it sends one")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_sendNoMail_when_singleAddIsSilent() throws Exception {
        cums.user("jane.doe", "Jane", "Doe", "success+jane.doe@simulator.amazonses.com");
        addControlWithNotify();

        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jane.doe\",\"notify\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("confirmed"));

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(CONTROL_MAIL)).hasSize(1);
        assertThat(sesRecorder.mailsTo("success+jane.doe@simulator.amazonses.com")).isEmpty();
        assertThat(sesRecorder.mails()).hasSize(1);
    }

    @Test
    @DisplayName("Single add for an already-active registrant → 409")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_return409_when_userAlreadyRegistered() throws Exception {
        seedRegistration("jane.doe", "registered");
        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jane.doe\",\"notify\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Single add replaces a cancelled registration → 201 confirmed")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_replaceCancelled_when_organizerAddsParticipant() throws Exception {
        seedRegistration("jane.doe", "cancelled");
        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jane.doe\",\"notify\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("confirmed"));
    }

    @Test
    @DisplayName("Single add on a full event → 409 capacity_exceeded; force=true → 201")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_respectCapacity_unlessForced() throws Exception {
        Event full = eventRepository.save(Event.builder()
                .eventCode(FULL_EVENT_CODE).eventNumber(1998).title("Full Event")
                .eventType(EventType.EVENING).date(Instant.now().plus(30, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(20, ChronoUnit.DAYS))
                .venueName("V").venueAddress("Bern").venueCapacity(1).registrationCapacity(1)
                .organizerUsername(ORGANIZER_USERNAME)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION).build());
        // Fill the single seat.
        registrationRepository.save(Registration.builder()
                .registrationCode("BATbern1998-seed").eventId(full.getId())
                .attendeeUsername("first.attendee").attendeeFirstName("First").attendeeLastName("Attendee")
                .attendeeEmail("first@batbern.ch").status("confirmed").registrationDate(Instant.now()).build());

        // Without force → 409 with details.code = capacity_exceeded.
        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", FULL_EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jane.doe\",\"notify\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code").value("capacity_exceeded"));

        // With force → 201 over capacity.
        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", FULL_EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jane.doe\",\"force\":true,\"notify\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("confirmed"));
    }

    @Test
    @DisplayName("Single add for an unknown event → 404")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_return404_when_unknownEventOnAddParticipant() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", "BATbern99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jane.doe\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Single add for an unknown user → 404, nothing stored")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_return404_when_unknownUserOnAddParticipant() throws Exception {
        cums.unknownUser("ghost.user");
        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost.user\"}"))
                .andExpect(status().isNotFound());
        assertThat(registrationRepository.findByEventIdAndAttendeeUsername(event.getId(), "ghost.user")).isEmpty();
    }

    // ---- POST /participants: historical import (mode people) ----

    @Test
    @DisplayName("Import → 200 per person: new registered, existing skipped, one failing person keeps the others; no mail")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_importPerPerson_when_peopleGiven() throws Exception {
        List<String> getOrCreateEmails = new ArrayList<>();
        cums.answer(POST, "/internal/users/get-or-create", call -> {
            JsonNode body = readTree(call);
            String email = body.get("email").asText();
            getOrCreateEmails.add(email);
            assertThat(body.get("cognitoSync").asBoolean()).isFalse(); // ADR-005: no login
            if (email.startsWith("linked.")) {
                return new Reply(HttpStatus.CONFLICT, """
                        {"timestamp": "2026-11-08T10:00:00Z", "path": "/internal/users/get-or-create",
                         "status": 409, "error": "Conflict", "errorCode": "EMAIL_LINKED_TO_ACCOUNT",
                         "message": "This email is linked to an existing account, please sign in",
                         "severity": "LOW"}""");
            }
            String username = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            return Reply.ok(getOrCreateReply(username, body));
        });
        seedRegistration("ines.muster", "attended"); // already there → skipped
        addControlWithNotify();

        String body = """
                {"people": [
                  {"email": "hugo.muster@example.com", "firstName": "Hugo", "lastName": "Müster",
                   "companyId": "musterag", "status": "ATTENDED"},
                  {"email": "linked.person@example.com", "firstName": "Linked", "lastName": "Person",
                   "status": "ATTENDED"},
                  {"email": "ines.muster@example.com", "firstName": "Ines", "lastName": "Muster",
                   "status": "ATTENDED"},
                  {"email": "otto.cancel@example.com", "firstName": "Otto", "lastName": "Cancel",
                   "status": "CANCELLED"}
                ]}""";
        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(2))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.registrationCode").doesNotExist())
                .andExpect(jsonPath("$.results[0].outcome").value("REGISTERED"))
                .andExpect(jsonPath("$.results[0].username").value("hugo.muster"))
                .andExpect(jsonPath("$.results[1].outcome").value("FAILED"))
                .andExpect(jsonPath("$.results[1].email").value("linked.person@example.com"))
                .andExpect(jsonPath("$.results[1].reason").isNotEmpty())
                .andExpect(jsonPath("$.results[1].username").doesNotExist())
                .andExpect(jsonPath("$.results[2].outcome").value("SKIPPED"))
                .andExpect(jsonPath("$.results[3].outcome").value("REGISTERED"));

        assertThat(getOrCreateEmails).containsExactly("hugo.muster@example.com", "linked.person@example.com",
                "ines.muster@example.com", "otto.cancel@example.com");
        Registration hugo = registrationRepository
                .findByEventIdAndAttendeeUsername(event.getId(), "hugo.muster").orElseThrow();
        assertThat(hugo.getStatus()).isEqualTo("attended");
        assertThat(hugo.getAttendeeLastName()).isEqualTo("Müster");
        assertThat(hugo.getAttendeeCompanyId()).isEqualTo("musterag");
        assertThat(registrationRepository.findByEventIdAndAttendeeUsername(event.getId(), "otto.cancel")
                .orElseThrow().getStatus()).isEqualTo("cancelled");
        assertThat(registrationRepository.findByEventIdAndAttendeeUsername(event.getId(), "ines.muster")
                .orElseThrow().getStatus()).isEqualTo("attended"); // untouched

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(CONTROL_MAIL)).hasSize(1);
        assertThat(sesRecorder.mails()).hasSize(1);
    }

    @Test
    @DisplayName("Import of the same people twice → second call skips everyone (idempotent)")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_skipEveryone_when_importRepeated() throws Exception {
        cums.answer(POST, "/internal/users/get-or-create", call -> {
            JsonNode body = readTree(call);
            String email = body.get("email").asText();
            return Reply.ok(getOrCreateReply(email.substring(0, email.indexOf('@')), body));
        });
        String body = """
                {"people": [{"email": "hugo.muster@example.com", "firstName": "Hugo", "lastName": "Muster",
                             "status": "ATTENDED"}]}""";
        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(1));
        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(0))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    // ---- POST /participants: stakeholders ----

    @Test
    @DisplayName("Stakeholders → 200 {enrolled, skipped}, idempotent, no mail")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_enrolStakeholders_when_stakeholdersTrue() throws Exception {
        cums.usersWithRole("ORGANIZER", ORGANIZER_USERNAME, "organizer.bert");
        cums.usersWithRole("PARTNER", "partner.paula");
        seedRegistration("organizer.bert", "confirmed"); // already there → skipped
        addControlWithNotify();

        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stakeholders\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(2))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.results").doesNotExist())
                .andExpect(jsonPath("$.registrationCode").doesNotExist());

        assertThat(registrationRepository.findByEventIdAndAttendeeUsername(event.getId(), "partner.paula"))
                .get().extracting(Registration::getStatus).isEqualTo("confirmed");

        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stakeholders\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(0))
                .andExpect(jsonPath("$.skipped").value(3));

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(CONTROL_MAIL)).hasSize(1);
        assertThat(sesRecorder.mails()).hasSize(1);
    }

    // ---- POST /participants: mode errors and access ----

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "{}",
        "{\"username\":\"jane.doe\",\"stakeholders\":true}",
        "{\"username\":\"jane.doe\",\"people\":[{\"email\":\"a.b@example.com\",\"firstName\":\"A\",\"lastName\":\"B\",\"status\":\"ATTENDED\"}]}",
        "{\"stakeholders\":true,\"people\":[{\"email\":\"a.b@example.com\",\"firstName\":\"A\",\"lastName\":\"B\",\"status\":\"ATTENDED\"}]}",
        "{\"stakeholders\":false}",
        "{\"stakeholders\":true,\"notify\":false}",
        "{\"people\":[]}",
        "{\"people\":[{\"email\":\"not-an-email\",\"firstName\":\"A\",\"lastName\":\"B\",\"status\":\"ATTENDED\"}]}",
        "{\"people\":[{\"email\":\"a.b@example.com\",\"firstName\":\"A\",\"lastName\":\"B\",\"status\":\"WAITLIST\"}]}",
        "{\"username\":\"\"}"
    })
    @DisplayName("Mode error → 400, nothing stored")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_return400_when_modeIsNotExactlyOne(String body) throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        assertThat(registrationRepository.countByEventId(event.getId())).isZero();
    }

    @Test
    @DisplayName("More than 100 people → 400, nothing stored")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_return400_when_moreThan100People() throws Exception {
        StringBuilder people = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            people.append(i == 0 ? "" : ",").append("{\"email\":\"p").append(i)
                    .append(".x@example.com\",\"firstName\":\"P\",\"lastName\":\"X\",\"status\":\"ATTENDED\"}");
        }
        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"people\":[" + people + "]}"))
                .andExpect(status().isBadRequest());
        assertThat(registrationRepository.countByEventId(event.getId())).isZero();
    }

    @Test
    @DisplayName("Attendee POST /participants → 403 in every mode")
    @WithMockUser(username = "joe.user", roles = {"ATTENDEE"})
    void should_return403_when_nonOrganizerAddsParticipant() throws Exception {
        for (String body : List.of("{\"username\":\"jane.doe\"}", "{\"stakeholders\":true}")) {
            mockMvc.perform(post("/api/v1/events/{eventCode}/participants", EVENT_CODE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Anonymous POST /participants → 401")
    void should_return401_when_anonymousAddsParticipant() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stakeholders\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("The old create paths are gone: batch_registrations and enroll-stakeholders → 404/405")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void should_notServe_oldCreatePaths() throws Exception {
        int batch = mockMvc.perform(post("/api/v1/events/batch_registrations")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus();
        int enrol = mockMvc.perform(post("/api/v1/events/{eventCode}/enroll-stakeholders", EVENT_CODE))
                .andReturn().getResponse().getStatus();
        assertThat(Set.of(batch, enrol)).allMatch(code -> code == 404 || code == 405);
        assertThat(registrationRepository.countByEventId(event.getId())).isZero();
    }

    /** Positive control: a notify add whose mail must arrive, next to an absence assertion. */
    private void addControlWithNotify() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/participants", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + CONTROL + "\",\"notify\":true}"))
                .andExpect(status().isCreated());
    }

    private JsonNode readTree(Call call) {
        try {
            return objectMapper.readTree(call.body());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A {@code GetOrCreateUserResponse} for this username, with a complete user. */
    private String getOrCreateReply(String username, JsonNode request) {
        String user = CumsContractHarness.userJson(username, request.get("firstName").asText(),
                request.get("lastName").asText(), request.get("email").asText(),
                request.hasNonNull("companyId") ? request.get("companyId").asText() : null);
        return "{\"username\": \"" + username + "\", \"created\": true, \"companyUpdated\": false, "
                + "\"cognitoUserId\": null, \"user\": " + user + "}";
    }

    // ---- helpers ----

    private void seedScheduledSession(String username) {
        Session session = Session.builder()
                .eventId(event.getId())
                .eventCode(EVENT_CODE)
                .title("Scheduled Talk")
                .sessionSlug("scheduled-talk-" + System.nanoTime())
                .sessionType("presentation")
                .startTime(Instant.now().plus(30, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(30, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .build();
        session = sessionRepository.save(session);

        sessionUserRepository.save(SessionUser.builder()
                .session(session)
                .username(username)
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(false)
                .build());
    }

    private void seedSessionPrimarySpeaker(String username) {
        Session session = Session.builder()
                .eventId(event.getId())
                .eventCode(EVENT_CODE)
                .title("Talk")
                .sessionSlug("talk-" + System.nanoTime())
                .sessionType("presentation")
                .build();
        session = sessionRepository.save(session);

        sessionUserRepository.save(SessionUser.builder()
                .session(session)
                .username(username)
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(false)
                .build());
    }

    private void seedRegistration(String username, String status) {
        Registration r = Registration.builder()
                .registrationCode(EVENT_CODE + "-reg-" + System.nanoTime() % 1_000_000L)
                .eventId(event.getId())
                .attendeeUsername(username)
                .attendeeFirstName(username.substring(0, username.indexOf(".")))
                .attendeeLastName(username.substring(username.indexOf(".") + 1))
                .attendeeCompanyId("acme")
                .status(status)
                .registrationDate(Instant.now())
                .build();
        registrationRepository.save(r);
    }

    private void seedRegistrationWithEmail(String username, String email, String status) {
        Registration r = Registration.builder()
                .registrationCode(EVENT_CODE + "-reg-" + System.nanoTime() % 1_000_000L)
                .eventId(event.getId())
                .attendeeUsername(username)
                .attendeeFirstName(username.substring(0, username.indexOf(".")))
                .attendeeLastName(username.substring(username.indexOf(".") + 1))
                .attendeeEmail(email)
                .attendeeCompanyId("acme")
                .status(status)
                .registrationDate(Instant.now())
                .build();
        registrationRepository.save(r);
    }
}
