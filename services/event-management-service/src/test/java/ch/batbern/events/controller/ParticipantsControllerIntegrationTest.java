package ch.batbern.events.controller;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.AdditionalEmail;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link ParticipantsController}: XLSX participants export and
 * per-event distribution-list resolution.
 *
 * <p>Spec: {@code _bmad-output/implementation-artifacts/spec-auto-participant-email-aliases-excel-export.md}.
 */
@Transactional
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

    @MockitoBean
    private UserApiClient userApiClient;

    private static final String EVENT_CODE = "BATbern1999";
    private static final String ORGANIZER_USERNAME = "organizer.alice";

    private Event event;

    @BeforeEach
    void setUp() {
        registrationRepository.deleteAll();
        sessionUserRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();

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

        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of(ORGANIZER_USERNAME));
        when(userApiClient.getCompanyDisplayName("acme")).thenReturn("Acme AG");
        when(userApiClient.getUserByUsername(anyString())).thenAnswer(inv -> {
            String u = inv.getArgument(0);
            return new UserResponse()
                    .id(u)
                    .firstName(u.contains(".") ? u.substring(0, u.indexOf(".")) : u)
                    .lastName(u.contains(".") ? u.substring(u.indexOf(".") + 1) : "User")
                    .email(u + "@batbern.ch")
                    .companyId("acme");
        });
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
    @DisplayName("Anonymous GET /distribution-list/speakers → 200 with scheduled primary speakers")
    void should_return200_when_lambdaCallsSpeakersList() throws Exception {
        seedScheduledSession("speaker.bob");

        // UserApiClient stub for additionalEmails fan-out
        when(userApiClient.getUserByUsername("speaker.bob")).thenReturn(
                new UserResponse()
                        .id("speaker.bob")
                        .firstName("Speaker")
                        .lastName("Bob")
                        .email("bob@example.com")
                        .addAdditionalEmailsItem(
                                new AdditionalEmail("bob.work@example.com", OffsetDateTime.now())));

        mockMvc.perform(get("/api/v1/events/{eventCode}/distribution-list/{kind}",
                        EVENT_CODE, "speakers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(EVENT_CODE))
                .andExpect(jsonPath("$.kind").value("speakers"))
                .andExpect(jsonPath("$.emails", org.hamcrest.Matchers.hasItem("bob@example.com")))
                .andExpect(jsonPath("$.emails", org.hamcrest.Matchers.hasItem("bob.work@example.com")));
    }

    @Test
    @DisplayName("Anonymous GET /distribution-list/moderator → 200 with organizer email")
    void should_return200_when_lambdaCallsModeratorList() throws Exception {
        when(userApiClient.getUserByUsername(ORGANIZER_USERNAME)).thenReturn(
                new UserResponse()
                        .id(ORGANIZER_USERNAME)
                        .firstName("Alice")
                        .lastName("Organizer")
                        .email("alice@batbern.ch"));

        mockMvc.perform(get("/api/v1/events/{eventCode}/distribution-list/{kind}",
                        EVENT_CODE, "moderator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("moderator"))
                .andExpect(jsonPath("$.emails[0]").value("alice@batbern.ch"));
    }

    @Test
    @DisplayName("Anonymous GET /distribution-list with unknown event → 404")
    void should_return404_when_unknownEventOnDistributionList() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/distribution-list/{kind}",
                        "BATbern99999", "speakers"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Anonymous GET /distribution-list with unknown kind → 404 (defensive)")
    void should_return404_when_unknownKind() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/distribution-list/{kind}",
                        EVENT_CODE, "foo"))
                .andExpect(status().isNotFound());
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
}
