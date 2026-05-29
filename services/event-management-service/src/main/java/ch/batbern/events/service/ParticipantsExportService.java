package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the name-badge XLSX for an event's participants.
 *
 * <p>Spec: {@code _bmad-output/implementation-artifacts/spec-auto-participant-email-aliases-excel-export.md}
 * (F3). Columns (German — physical badges): {@code Vorname, Name, Firma, Rolle}. Role values:
 * {@code Organisator, Referent, Teilnehmer} with precedence
 * {@code Organisator > Referent > Teilnehmer} — a user appearing in multiple source sets
 * renders once with the highest-precedence role.
 *
 * <p>Source-set union, dedup-by-username (case-sensitive):
 * <ol>
 *   <li>All ORGANIZER-role users from CUMS (via {@link UserApiClient#getOrganizerUsernames()})</li>
 *   <li>All PRIMARY_SPEAKER + CO_SPEAKER session_users of the event</li>
 *   <li>All registrations with status in {@code [registered, confirmed, attended]}</li>
 * </ol>
 *
 * <p>Pattern adopted verbatim from
 * {@code PartnerAttendanceExportService} — {@code SXSSFWorkbook(100)}, bold-header with grey
 * background, {@code try-with-resources} + {@code workbook.dispose()},
 * {@code ByteArrayOutputStream} → {@code byte[]}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantsExportService {

    private static final String ROLE_ORGANIZER = "Organisator";
    private static final String ROLE_SPEAKER = "Referent";
    private static final String ROLE_ATTENDEE = "Teilnehmer";

    /**
     * Statuses that count as participants for badge printing. Mirrors
     * {@link Registration#CONFIRMED_STATUSES} but is reproduced literally here so a future
     * change to {@code CONFIRMED_STATUSES} (e.g. adding {@code "waitlist"}) does NOT
     * silently widen the badge list.
     */
    private static final List<String> BADGE_STATUSES =
            List.of("registered", "confirmed", "attended");

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final SessionUserRepository sessionUserRepository;
    private final UserApiClient userApiClient;

    /**
     * Generate the XLSX byte array for the event's name badges.
     *
     * @param eventCode meaningful event identifier (ADR-003)
     * @return raw XLSX bytes
     * @throws NotFoundException if the event does not exist
     */
    @Transactional(readOnly = true)
    public byte[] generateNameBadgeXlsx(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));

        // Build the (username -> ParticipantRow) map applying role precedence.
        // LinkedHashMap to keep insertion order (organizers first, then speakers, then
        // attendees) — slightly more useful for the printer's eye.
        Map<String, ParticipantRow> rows = new LinkedHashMap<>();

        collectOrganizers(rows);
        collectEventSpeakers(event, rows);
        collectRegisteredAttendees(event, rows);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Namensschilder");
            sheet.setColumnWidth(0, 6000); // Vorname
            sheet.setColumnWidth(1, 6000); // Name
            sheet.setColumnWidth(2, 8000); // Firma
            sheet.setColumnWidth(3, 4000); // Rolle

            CellStyle headerStyle = buildHeaderStyle(workbook);

            Row header = sheet.createRow(0);
            writeCell(header, 0, "Vorname", headerStyle);
            writeCell(header, 1, "Name", headerStyle);
            writeCell(header, 2, "Firma", headerStyle);
            writeCell(header, 3, "Rolle", headerStyle);

            int rowIdx = 1;
            for (ParticipantRow p : rows.values()) {
                Row dataRow = sheet.createRow(rowIdx++);
                writeCell(dataRow, 0, nullToEmpty(p.firstName()), null);
                writeCell(dataRow, 1, nullToEmpty(p.lastName()), null);
                writeCell(dataRow, 2, nullToEmpty(p.companyDisplayName()), null);
                writeCell(dataRow, 3, p.role(), null);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Failed to generate name-badge XLSX for event {}", eventCode, e);
            throw new RuntimeException("Excel export failed for event " + eventCode, e);
        }
    }

    private void collectOrganizers(Map<String, ParticipantRow> rows) {
        List<String> usernames;
        try {
            usernames = userApiClient.getOrganizerUsernames();
        } catch (Exception e) {
            log.warn("Could not fetch organizer list for badge export: {}", e.getMessage());
            return;
        }
        Map<String, String> companyNames = safeCompanyDisplayNames();
        for (String username : usernames) {
            try {
                UserResponse user = userApiClient.getUserByUsername(username);
                // Key by username, matching collectEventSpeakers / collectRegisteredAttendees so
                // role-precedence dedupe works when the same physical user appears in multiple sets.
                // Using user.getId() here would split organizer-who-is-also-speaker into two rows
                // whenever id != username (review finding 2026-05-28 — patch #1).
                rows.put(username, new ParticipantRow(
                        user.getFirstName(),
                        user.getLastName(),
                        resolveCompany(user.getCompanyId(), companyNames),
                        ROLE_ORGANIZER));
            } catch (UserNotFoundException ignore) {
                log.warn("Organizer username {} not resolvable — skipping in XLSX", username);
            }
        }
    }

    private void collectEventSpeakers(Event event, Map<String, ParticipantRow> rows) {
        List<SessionUser> speakers =
                sessionUserRepository.findEventSpeakersByEventId(event.getId());
        Map<String, String> companyNames = safeCompanyDisplayNames();
        for (SessionUser su : speakers) {
            String username = su.getUsername();
            if (username == null || username.isBlank()) {
                continue;
            }
            // Precedence: Organisator > Referent > Teilnehmer.
            ParticipantRow existing = rows.get(username);
            if (existing != null && ROLE_ORGANIZER.equals(existing.role())) {
                continue; // higher-precedence row already present
            }
            try {
                UserResponse user = userApiClient.getUserByUsername(username);
                rows.put(username, new ParticipantRow(
                        user.getFirstName(),
                        user.getLastName(),
                        resolveCompany(user.getCompanyId(), companyNames),
                        ROLE_SPEAKER));
            } catch (UserNotFoundException ignore) {
                // Fall back to cached fields on session_users
                rows.put(username, new ParticipantRow(
                        su.getSpeakerFirstName(),
                        su.getSpeakerLastName(),
                        null,
                        ROLE_SPEAKER));
            }
        }
    }

    private void collectRegisteredAttendees(Event event, Map<String, ParticipantRow> rows) {
        List<Registration> registrations =
                registrationRepository.findByEventId(event.getId()).stream()
                        .filter(r -> r.getStatus() != null
                                && BADGE_STATUSES.contains(r.getStatus().toLowerCase()))
                        .toList();
        Map<String, String> companyNames = safeCompanyDisplayNames();
        for (Registration r : registrations) {
            String username = r.getAttendeeUsername();
            if (username == null || username.isBlank()) {
                continue;
            }
            ParticipantRow existing = rows.get(username);
            // Both ORGANIZER and SPEAKER outrank ATTENDEE.
            if (existing != null
                    && (ROLE_ORGANIZER.equals(existing.role()) || ROLE_SPEAKER.equals(existing.role()))) {
                continue;
            }
            rows.put(username, new ParticipantRow(
                    r.getAttendeeFirstName(),
                    r.getAttendeeLastName(),
                    resolveCompany(r.getAttendeeCompanyId(), companyNames),
                    ROLE_ATTENDEE));
        }
    }

    private Map<String, String> safeCompanyDisplayNames() {
        try {
            return userApiClient.getCompanyDisplayNames();
        } catch (Exception e) {
            log.warn("Could not resolve company display names — falling back to slugs: {}",
                    e.getMessage());
            return new HashMap<>();
        }
    }

    private String resolveCompany(String companySlug, Map<String, String> companyDisplayNames) {
        if (companySlug == null || companySlug.isBlank()) {
            return "";
        }
        return companyDisplayNames.getOrDefault(companySlug, companySlug);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private CellStyle buildHeaderStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void writeCell(Row row, int col, String value, CellStyle style) {
        var cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    /**
     * Row materialised for one participant in the XLSX. Public-package so unit tests can
     * cross-check via {@link #collectEventSpeakers(Event, Map)} indirectly.
     */
    private record ParticipantRow(String firstName, String lastName, String companyDisplayName,
                                  String role) {
    }
}
