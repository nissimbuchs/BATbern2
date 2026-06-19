package ch.batbern.events.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Generates the name-badge DOCX for an event's participants — the printable
 * counterpart of {@link ParticipantsExportService}.
 *
 * <p>Uses a classpath template at {@code templates/name-badges-l4784.docx}
 * (built by {@code scripts/data/build-name-badge-template.py} from the
 * canonical {@code docs/Avery-Zweckform_L4784_BATLogo.docx}). The template
 * holds a single page-frame table with <strong>27 badge slots</strong>
 * (9 rows × 3 badge columns; cells 1 and 3 of each row are narrow gutters).
 *
 * <p>Each badge cell carries three plain-text placeholders that we replace via
 * {@link XWPFRun#setText(String, int)}:
 * <ul>
 *   <li>{@code «Name»} (bold, 12 pt)</li>
 *   <li>{@code «Firma»} (regular, 12 pt — appears alongside the anchored
 *       BAT-logo PNG that stays in place)</li>
 *   <li>{@code «Rolle»} (italic, 10 pt)</li>
 * </ul>
 *
 * <p>For events with more than 27 participants we deep-clone the page-frame
 * table once per additional page via {@link CTTbl#copy()} and insert each copy
 * back into the document body. Word/LibreOffice then paginates naturally based
 * on the fixed row height. Trailing slots on the last page get empty strings
 * so the cells render blank while keeping the badge layout (logo, spacing)
 * intact for clean printing.
 *
 * <p>The participant list comes from
 * {@link ParticipantsCollector#collect(String)} — XLSX and DOCX exports share
 * the same row order (role precedence → German-collator last name → first name).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantsDocxExportService {

    static final String TEMPLATE_RESOURCE = "templates/name-badges-l4784.docx";
    static final int BADGES_PER_PAGE = 27;
    /** Indices (within a 5-cell row) that carry a badge — gutter cells are 1 and 3. */
    private static final int[] BADGE_CELL_INDICES = {0, 2, 4};

    private static final String PLACEHOLDER_NAME = "«Name»";
    private static final String PLACEHOLDER_FIRMA = "«Firma»";
    private static final String PLACEHOLDER_ROLLE = "«Rolle»";

    private final ParticipantsCollector participantsCollector;

    /**
     * Generate the DOCX byte array for the event's name badges.
     *
     * @param eventCode meaningful event identifier (ADR-003)
     * @return raw DOCX bytes
     */
    @Transactional(readOnly = true)
    public byte[] generateNameBadgeDocx(String eventCode) {
        // Filter participants that can't usefully be printed as a badge — an
        // attendee row with neither firstName nor lastName produces an empty,
        // logo-only "Teilnehmer" badge that wastes paper and ink. The XLSX
        // keeps such rows (it's a data view, useful for spotting incomplete
        // registrations); the DOCX is a physical artifact and drops them.
        List<ParticipantRow> participants = participantsCollector.collect(eventCode).stream()
                .filter(ParticipantsDocxExportService::hasPrintableName)
                .toList();

        try (InputStream templateStream = openTemplate();
             XWPFDocument doc = new XWPFDocument(templateStream);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            List<XWPFTable> tables = doc.getTables();
            if (tables.isEmpty()) {
                throw new IllegalStateException(
                        "Template " + TEMPLATE_RESOURCE + " has no badge table");
            }
            XWPFTable pageOne = tables.get(0);

            int totalPages = Math.max(1, ceilDiv(participants.size(), BADGES_PER_PAGE));

            // Clone the page-frame table FIRST (while it still carries the empty
            // «Name»/«Firma»/«Rolle» placeholders) — once for each additional
            // page beyond page 1. Filling page 1 in-place after this is safe;
            // the clones each have their own unfilled DOM.
            List<XWPFTable> pages = new java.util.ArrayList<>(totalPages);
            pages.add(pageOne);
            for (int pageIdx = 1; pageIdx < totalPages; pageIdx++) {
                pages.add(clonePageFrame(doc, pageOne));
            }

            // Now fill every page from the participant list.
            for (int pageIdx = 0; pageIdx < totalPages; pageIdx++) {
                fillPage(pages.get(pageIdx), participants, pageIdx * BADGES_PER_PAGE);
            }

            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Failed to generate name-badge DOCX for event {}", eventCode, e);
            throw new RuntimeException("Word export failed for event " + eventCode, e);
        }
    }

    private InputStream openTemplate() throws IOException {
        ClassPathResource res = new ClassPathResource(TEMPLATE_RESOURCE);
        if (!res.exists()) {
            throw new IOException("Missing classpath resource: " + TEMPLATE_RESOURCE);
        }
        return res.getInputStream();
    }

    /**
     * Replace placeholders in every badge cell of {@code table}, drawing
     * participants {@code [startIndex, startIndex + BADGES_PER_PAGE)} from the
     * full list. Indexes past the end of the list get empty-string replacements
     * (so the badge slot renders blank without disturbing the layout).
     */
    private void fillPage(XWPFTable table, List<ParticipantRow> all, int startIndex) {
        int slot = 0;
        for (XWPFTableRow row : table.getRows()) {
            for (int cellIdx : BADGE_CELL_INDICES) {
                if (cellIdx >= row.getTableCells().size()) {
                    continue;
                }
                XWPFTableCell cell = row.getCell(cellIdx);
                int absIdx = startIndex + slot;
                if (absIdx < all.size()) {
                    ParticipantRow p = all.get(absIdx);
                    fillBadgeCell(cell, p);
                } else {
                    fillBadgeCell(cell, EMPTY_PARTICIPANT);
                }
                slot++;
            }
        }
    }

    private static final ParticipantRow EMPTY_PARTICIPANT =
            new ParticipantRow("", "", "", "");

    /**
     * Replace the three placeholders in {@code cell} with the participant's
     * full name, company, and role. The cell's existing paragraph structure
     * (name / logo+firma / rolle) is preserved — we only change text content.
     */
    private void fillBadgeCell(XWPFTableCell cell, ParticipantRow p) {
        String fullName = joinName(p.firstName(), p.lastName());
        String firma = nullToEmpty(p.companyDisplayName());
        String rolle = nullToEmpty(p.role());
        for (XWPFParagraph para : cell.getParagraphs()) {
            for (XWPFRun run : para.getRuns()) {
                String text = run.getText(0);
                if (text == null) {
                    continue;
                }
                String replaced = text
                        .replace(PLACEHOLDER_NAME, fullName)
                        .replace(PLACEHOLDER_FIRMA, firma)
                        .replace(PLACEHOLDER_ROLLE, rolle);
                if (!replaced.equals(text)) {
                    run.setText(replaced, 0);
                }
            }
        }
    }

    /**
     * Deep-clone the page-frame table and insert it directly after {@code source}
     * in the document body so Word paginates a fresh badge sheet. The clone
     * carries its own DOM — mutations don't bleed back into the source.
     *
     * <p>POI's body-elements list is unmodifiable; we have to insert via the
     * XmlCursor API. {@code insertNewTbl} both inserts an empty table at the
     * cursor position AND registers it in POI's internal {@code tables} /
     * {@code bodyElements} lists. We then overwrite the empty CT with our
     * deep-copied CT so the cloned content lands in the registered table.
     */
    private XWPFTable clonePageFrame(XWPFDocument doc, XWPFTable source) {
        // Step 1 — insert an empty <w:tbl> into the body and register it with POI's
        // internal tables / bodyElements lists. POI does this only via insertNewTbl
        // (the lists are unmodifiable from the outside).
        XmlCursor cursor = doc.getDocument().getBody().newCursor();
        XWPFTable inserted;
        try {
            cursor.toEndToken();
            inserted = doc.insertNewTbl(cursor);
        } finally {
            cursor.close();
        }

        // Step 2 — overwrite the inserted CTTbl content with a deep copy of source.
        // Word/LibreOffice now sees a second page-frame table identical to the first.
        inserted.getCTTbl().set((CTTbl) source.getCTTbl().copy());

        // Step 3 — after `set(...)`, `inserted`'s cached row/cell wrappers are
        // orphaned (XmlValueDisconnectedException on use). Re-wrap the CTTbl in
        // a fresh XWPFTable so its internal `tableRows` list rebuilds from the
        // new XML. Returning this fresh wrapper is safe — it points to the same
        // CTTbl that's already in the body, so mutations propagate to the
        // serialised document.
        return new XWPFTable(inserted.getCTTbl(), doc);
    }

    private static int ceilDiv(int numerator, int denominator) {
        if (denominator <= 0) {
            throw new IllegalArgumentException("denominator must be positive");
        }
        return (numerator + denominator - 1) / denominator;
    }

    /**
     * True iff this participant has at least one of firstName / lastName populated.
     * An attendee with both empty (common in legacy / partially-imported
     * registrations) wouldn't produce a useful printed badge.
     */
    private static boolean hasPrintableName(ParticipantRow p) {
        String fn = p.firstName();
        String ln = p.lastName();
        return (fn != null && !fn.isBlank()) || (ln != null && !ln.isBlank());
    }

    private static String joinName(String firstName, String lastName) {
        String fn = nullToEmpty(firstName).trim();
        String ln = nullToEmpty(lastName).trim();
        if (fn.isEmpty()) {
            return ln;
        }
        if (ln.isEmpty()) {
            return fn;
        }
        return fn + " " + ln;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
