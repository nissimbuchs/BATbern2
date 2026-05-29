package ch.batbern.events.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ParticipantsDocxExportService}. We mock the collector
 * and parse the generated DOCX bytes back with Apache POI XWPF to assert cell
 * contents, page (table) count, and trailing-blank behaviour.
 *
 * <p>Spec: extension of
 * {@code _bmad-output/implementation-artifacts/spec-auto-participant-email-aliases-excel-export.md}
 * — DOCX name-badge export.
 */
@ExtendWith(MockitoExtension.class)
class ParticipantsDocxExportServiceTest {

    private static final String EVENT_CODE = "BATbern57";

    @Mock
    private ParticipantsCollector participantsCollector;

    @InjectMocks
    private ParticipantsDocxExportService service;

    @BeforeEach
    void resetCollectorMock() {
        // No-op: @Mock is reset per test by MockitoExtension.
    }

    @Test
    @DisplayName("3 participants → 1 page; their cells filled, the remaining 24 slots blank")
    void should_fillThreeBadges_and_blankTheRest() throws IOException {
        when(participantsCollector.collect(EVENT_CODE)).thenReturn(List.of(
                new ParticipantRow("Alice", "Organizer", "elca", ParticipantsCollector.ROLE_ORGANIZER),
                new ParticipantRow("Bob", "Speaker", "bkw", ParticipantsCollector.ROLE_SPEAKER),
                new ParticipantRow("Carol", "Attendee", "mobiliar", ParticipantsCollector.ROLE_ATTENDEE)));

        byte[] docx = service.generateNameBadgeDocx(EVENT_CODE);
        assertThat(docx).isNotEmpty();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<XWPFTable> tables = doc.getTables();
            assertThat(tables).hasSize(1);

            List<BadgeText> badges = readBadges(tables.get(0));
            assertThat(badges).hasSize(ParticipantsDocxExportService.BADGES_PER_PAGE);

            assertThat(badges.get(0).name).isEqualTo("Alice Organizer");
            assertThat(badges.get(0).firma).isEqualTo("elca");
            assertThat(badges.get(0).rolle).isEqualTo("Organisator");

            assertThat(badges.get(1).name).isEqualTo("Bob Speaker");
            assertThat(badges.get(1).rolle).isEqualTo("Referent");

            assertThat(badges.get(2).name).isEqualTo("Carol Attendee");
            assertThat(badges.get(2).rolle).isEqualTo("Teilnehmer");

            // Slots 3..26 are blank: no leftover placeholders, no participant data.
            for (int i = 3; i < badges.size(); i++) {
                BadgeText b = badges.get(i);
                assertThat(b.name).as("slot %d name", i).isEmpty();
                assertThat(b.firma).as("slot %d firma", i).isEmpty();
                assertThat(b.rolle).as("slot %d rolle", i).isEmpty();
            }
        }
    }

    @Test
    @DisplayName("0 participants → 1 page with all 27 slots blank, no NPE")
    void should_produceBlankPage_when_noParticipants() throws IOException {
        when(participantsCollector.collect(EVENT_CODE)).thenReturn(List.of());

        byte[] docx = service.generateNameBadgeDocx(EVENT_CODE);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertThat(doc.getTables()).hasSize(1);
            List<BadgeText> badges = readBadges(doc.getTables().get(0));
            assertThat(badges).hasSize(ParticipantsDocxExportService.BADGES_PER_PAGE);
            assertThat(badges).allSatisfy(b -> {
                assertThat(b.name).isEmpty();
                assertThat(b.firma).isEmpty();
                assertThat(b.rolle).isEmpty();
            });
        }
    }

    @Test
    @DisplayName("28 participants → 2 pages (tables); 28th lands on first slot of page 2")
    void should_paginate_when_moreThanOnePageWorth() throws IOException {
        List<ParticipantRow> rows = new ArrayList<>();
        for (int i = 0; i < ParticipantsDocxExportService.BADGES_PER_PAGE; i++) {
            rows.add(new ParticipantRow("First" + i, "Last" + i, "Co" + i,
                    ParticipantsCollector.ROLE_ATTENDEE));
        }
        rows.add(new ParticipantRow("Overflow", "Person", "OverflowCo",
                ParticipantsCollector.ROLE_ATTENDEE));
        when(participantsCollector.collect(EVENT_CODE)).thenReturn(rows);

        byte[] docx = service.generateNameBadgeDocx(EVENT_CODE);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertThat(doc.getTables()).hasSize(2);

            // Page 1: all 27 slots filled.
            List<BadgeText> page1 = readBadges(doc.getTables().get(0));
            assertThat(page1.get(0).name).isEqualTo("First0 Last0");
            assertThat(page1.get(26).name).isEqualTo("First26 Last26");

            // Page 2: slot 0 = overflow person, the rest blank.
            List<BadgeText> page2 = readBadges(doc.getTables().get(1));
            assertThat(page2.get(0).name).isEqualTo("Overflow Person");
            assertThat(page2.get(0).firma).isEqualTo("OverflowCo");
            assertThat(page2.get(1).name).isEmpty();
        }
    }

    @Test
    @DisplayName("Logo (word/media/image1.png) survives the table-clone for additional pages")
    void should_keepLogoOnEveryPage() throws IOException {
        List<ParticipantRow> rows = new ArrayList<>();
        for (int i = 0; i < ParticipantsDocxExportService.BADGES_PER_PAGE + 5; i++) {
            rows.add(new ParticipantRow("F" + i, "L" + i, "C" + i,
                    ParticipantsCollector.ROLE_ATTENDEE));
        }
        when(participantsCollector.collect(EVENT_CODE)).thenReturn(rows);

        byte[] docx = service.generateNameBadgeDocx(EVENT_CODE);

        // Each badge row references the same image1 relationship via r:embed —
        // the cloned table must keep the references valid. We simply confirm the
        // single image part is still present in the package.
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertThat(doc.getAllPictures()).isNotEmpty();
            assertThat(doc.getTables()).hasSize(2);
        }
    }

    @Test
    @DisplayName("Name first/last fallback: empty firstName renders just lastName, no leading space")
    void should_handleMissingFirstName() throws IOException {
        when(participantsCollector.collect(EVENT_CODE)).thenReturn(List.of(
                new ParticipantRow("", "Lastonly", "co", ParticipantsCollector.ROLE_ATTENDEE)));

        byte[] docx = service.generateNameBadgeDocx(EVENT_CODE);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            BadgeText first = readBadges(doc.getTables().get(0)).get(0);
            assertThat(first.name).isEqualTo("Lastonly");
        }
    }

    @Test
    @DisplayName("Participants with empty firstName AND lastName are filtered out (no wasted badge)")
    void should_dropParticipantsWithNoName() throws IOException {
        when(participantsCollector.collect(EVENT_CODE)).thenReturn(List.of(
                new ParticipantRow("Alice", "Real", "elca", ParticipantsCollector.ROLE_ATTENDEE),
                // These three have no usable name — must be dropped from the badge list.
                new ParticipantRow("", "", "swisscom", ParticipantsCollector.ROLE_ATTENDEE),
                new ParticipantRow(null, null, null, ParticipantsCollector.ROLE_ATTENDEE),
                new ParticipantRow("   ", "  ", "bkw", ParticipantsCollector.ROLE_ATTENDEE),
                new ParticipantRow("Bob", "AlsoReal", "bls", ParticipantsCollector.ROLE_ATTENDEE)));

        byte[] docx = service.generateNameBadgeDocx(EVENT_CODE);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<BadgeText> badges = readBadges(doc.getTables().get(0));
            // Only the two named participants land in actual slots; remaining slots blank.
            assertThat(badges.get(0).name).isEqualTo("Alice Real");
            assertThat(badges.get(1).name).isEqualTo("Bob AlsoReal");
            // Remaining 25 slots stay blank — companies of the dropped rows must NOT
            // appear (they'd be visually orphaned next to the BAT logo).
            for (int i = 2; i < badges.size(); i++) {
                assertThat(badges.get(i).name).as("slot %d name", i).isEmpty();
                assertThat(badges.get(i).firma).as("slot %d firma", i).isEmpty();
            }
        }
    }

    // ---- helpers ----

    /** Plain-text content of one badge cell, concatenated across runs in each paragraph. */
    private record BadgeText(String name, String firma, String rolle) {
    }

    private List<BadgeText> readBadges(XWPFTable table) {
        List<BadgeText> badges = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            for (int cellIdx : new int[]{0, 2, 4}) {
                if (cellIdx >= row.getTableCells().size()) {
                    continue;
                }
                XWPFTableCell cell = row.getCell(cellIdx);
                List<String> paraTexts = cell.getParagraphs().stream()
                        .map(p -> p.getText() == null ? "" : p.getText())
                        .toList();
                String name = paraTexts.size() > 0 ? paraTexts.get(0) : "";
                String firma = paraTexts.size() > 1 ? paraTexts.get(1) : "";
                String rolle = paraTexts.size() > 2 ? paraTexts.get(2) : "";
                badges.add(new BadgeText(name, firma, rolle));
            }
        }
        return badges;
    }
}
