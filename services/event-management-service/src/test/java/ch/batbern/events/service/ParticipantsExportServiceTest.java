package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.shared.exception.NotFoundException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ParticipantsExportService}. We construct a real
 * {@link ParticipantsCollector} with mocked repositories so the test exercises the
 * data → XLSX path end-to-end. Spec:
 * {@code _bmad-output/implementation-artifacts/spec-auto-participant-email-aliases-excel-export.md}
 * (F3).
 *
 * <p>Sort assertions live in {@link ParticipantsCollectorTest}; this class focuses
 * on the XLSX output shape — header row, dedupe / precedence visible in rows,
 * status-filter behaviour.
 */
@ExtendWith(MockitoExtension.class)
class ParticipantsExportServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private RegistrationRepository registrationRepository;
    @Mock
    private SessionUserRepository sessionUserRepository;
    @Mock
    private UserApiClient userApiClient;

    private ParticipantsExportService service;

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final String EVENT_CODE = "BATbern57";

    private Event event;

    @BeforeEach
    void setUp() {
        ParticipantsCollector collector = new ParticipantsCollector(
                eventRepository, registrationRepository, sessionUserRepository, userApiClient);
        service = new ParticipantsExportService(collector);
        event = Event.builder()
                .id(EVENT_ID)
                .eventCode(EVENT_CODE)
                .build();
    }

    @Test
    @DisplayName("Output is a non-empty XLSX with the expected header row Vorname/Name/Firma/Rolle")
    void should_produceValidXlsx_withExpectedHeader() throws IOException {
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of());
        when(userApiClient.getCompanyDisplayNames()).thenReturn(Map.of());
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID)).thenReturn(List.of());
        when(registrationRepository.findByEventId(EVENT_ID)).thenReturn(List.of());

        byte[] xlsx = service.generateNameBadgeXlsx(EVENT_CODE);

        assertThat(xlsx).isNotEmpty();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Vorname");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Name");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Firma");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("Rolle");
        }
    }

    @Test
    @DisplayName("Union of organizers / speakers / attendees renders one row per username with correct role")
    void should_outputOneRowPerParticipant_withCorrectRoles() throws IOException {
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
        when(userApiClient.getCompanyDisplayNames()).thenReturn(Map.of("acme", "Acme Corp"));

        when(userApiClient.getOrganizerUsernames())
                .thenReturn(List.of("organizer.alice"));
        when(userApiClient.getUserByUsername("organizer.alice"))
                .thenReturn(user("organizer.alice", "Alice", "Organizer", "acme"));

        SessionUser speakerSu = new SessionUser();
        speakerSu.setUsername("speaker.bob");
        speakerSu.setSpeakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID))
                .thenReturn(List.of(speakerSu));
        when(userApiClient.getUserByUsername("speaker.bob"))
                .thenReturn(user("speaker.bob", "Bob", "Speaker", "acme"));

        Registration reg = Registration.builder()
                .attendeeUsername("attendee.carol")
                .attendeeFirstName("Carol")
                .attendeeLastName("Attendee")
                .attendeeCompanyId("acme")
                .status("confirmed")
                .build();
        when(registrationRepository.findByEventId(EVENT_ID)).thenReturn(List.of(reg));

        byte[] xlsx = service.generateNameBadgeXlsx(EVENT_CODE);

        Map<String, String[]> rowsByLastName = readDataRows(xlsx);
        assertThat(rowsByLastName).hasSize(3);
        assertThat(rowsByLastName.get("Organizer"))
                .containsExactly("Alice", "Organizer", "Acme Corp", "Organisator");
        assertThat(rowsByLastName.get("Speaker"))
                .containsExactly("Bob", "Speaker", "Acme Corp", "Referent");
        assertThat(rowsByLastName.get("Attendee"))
                .containsExactly("Carol", "Attendee", "Acme Corp", "Teilnehmer");
    }

    @Test
    @DisplayName("Precedence: a user who is both organizer and speaker renders once as Organisator")
    void should_pickHighestPrecedenceRole_when_userInMultipleSets() throws IOException {
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
        when(userApiClient.getCompanyDisplayNames()).thenReturn(Map.of());

        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of("dual.role"));
        when(userApiClient.getUserByUsername("dual.role"))
                .thenReturn(user("dual.role", "Dual", "Role", null));

        SessionUser speakerSu = new SessionUser();
        speakerSu.setUsername("dual.role");
        speakerSu.setSpeakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID))
                .thenReturn(List.of(speakerSu));

        Registration reg = Registration.builder()
                .attendeeUsername("dual.role")
                .attendeeFirstName("Dual")
                .attendeeLastName("Role")
                .status("registered")
                .build();
        when(registrationRepository.findByEventId(EVENT_ID)).thenReturn(List.of(reg));

        byte[] xlsx = service.generateNameBadgeXlsx(EVENT_CODE);

        Map<String, String[]> rowsByLastName = readDataRows(xlsx);
        assertThat(rowsByLastName).hasSize(1);
        assertThat(rowsByLastName.get("Role")[3]).isEqualTo("Organisator");
    }

    @Test
    @DisplayName("Precedence: a user who is both speaker and attendee renders once as Referent")
    void should_preferReferentOverTeilnehmer_when_userIsBoth() throws IOException {
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
        when(userApiClient.getCompanyDisplayNames()).thenReturn(Map.of());
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of());

        SessionUser speakerSu = new SessionUser();
        speakerSu.setUsername("speaker.bob");
        speakerSu.setSpeakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID))
                .thenReturn(List.of(speakerSu));
        when(userApiClient.getUserByUsername("speaker.bob"))
                .thenReturn(user("speaker.bob", "Bob", "Speaker", null));

        Registration reg = Registration.builder()
                .attendeeUsername("speaker.bob")
                .attendeeFirstName("Bob")
                .attendeeLastName("Speaker")
                .status("confirmed")
                .build();
        when(registrationRepository.findByEventId(EVENT_ID)).thenReturn(List.of(reg));

        byte[] xlsx = service.generateNameBadgeXlsx(EVENT_CODE);

        Map<String, String[]> rowsByLastName = readDataRows(xlsx);
        assertThat(rowsByLastName).hasSize(1);
        assertThat(rowsByLastName.get("Speaker")[3]).isEqualTo("Referent");
    }

    @Test
    @DisplayName("Registrations in non-badge statuses (waitlist, cancelled) are excluded")
    void should_excludeWaitlistAndCancelledFromBadgeList() throws IOException {
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
        when(userApiClient.getCompanyDisplayNames()).thenReturn(Map.of());
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of());
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID)).thenReturn(List.of());

        Registration cancelled = Registration.builder()
                .attendeeUsername("nope.one").attendeeFirstName("Nope").attendeeLastName("One")
                .status("cancelled").build();
        Registration waitlisted = Registration.builder()
                .attendeeUsername("nope.two").attendeeFirstName("Nope").attendeeLastName("Two")
                .status("waitlist").build();
        Registration confirmed = Registration.builder()
                .attendeeUsername("yes.one").attendeeFirstName("Yes").attendeeLastName("One")
                .status("confirmed").build();
        when(registrationRepository.findByEventId(EVENT_ID))
                .thenReturn(List.of(cancelled, waitlisted, confirmed));

        byte[] xlsx = service.generateNameBadgeXlsx(EVENT_CODE);

        Map<String, String[]> rowsByLastName = readDataRows(xlsx);
        assertThat(rowsByLastName).containsOnlyKeys("One");
        assertThat(rowsByLastName.get("One")[0]).isEqualTo("Yes");
    }

    @Test
    @DisplayName("Unknown event → NotFoundException")
    void should_throwNotFound_when_unknownEvent() {
        when(eventRepository.findByEventCode("BATbern999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateNameBadgeXlsx("BATbern999"))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- helpers ----

    private UserResponse user(String username, String firstName, String lastName, String companyId) {
        UserResponse user = new UserResponse()
                .id(username)
                .firstName(firstName)
                .lastName(lastName)
                .email(username + "@example.com");
        if (companyId != null) {
            user.companyId(companyId);
        }
        return user;
    }

    /**
     * Parse the XLSX bytes and return a map keyed by lastName → row values [Vorname,Name,Firma,Rolle].
     * Skips the header row.
     */
    private Map<String, String[]> readDataRows(byte[] xlsx) throws IOException {
        Map<String, String[]> rows = new LinkedHashMap<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row r = sheet.getRow(i);
                if (r == null) {
                    continue;
                }
                String[] values = new String[] {
                        cell(r, 0), cell(r, 1), cell(r, 2), cell(r, 3)
                };
                rows.put(values[1], values);
            }
        }
        return rows;
    }

    private String cell(Row row, int col) {
        return row.getCell(col) == null ? "" : row.getCell(col).getStringCellValue();
    }
}
