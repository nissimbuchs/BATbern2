package ch.batbern.events.service;

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
import java.util.List;

/**
 * Generates the name-badge XLSX for an event's participants.
 *
 * <p>Columns (German — physical badges): {@code Vorname, Name, Firma, Rolle}.
 * Rows come from {@link ParticipantsCollector#collect(String)} — see that class
 * for the source-set union, dedupe and canonical sort (role precedence then
 * German-collator last name).
 *
 * <p>Pattern adopted verbatim from {@code PartnerAttendanceExportService}:
 * {@code SXSSFWorkbook(100)}, bold-header with grey background, try-with-resources +
 * {@code workbook.dispose()}, {@code ByteArrayOutputStream} → {@code byte[]}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantsExportService {

    private final ParticipantsCollector participantsCollector;

    /**
     * Generate the XLSX byte array for the event's name badges.
     *
     * @param eventCode meaningful event identifier (ADR-003)
     * @return raw XLSX bytes
     */
    @Transactional(readOnly = true)
    public byte[] generateNameBadgeXlsx(String eventCode) {
        List<ParticipantRow> rows = participantsCollector.collect(eventCode);

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
            for (ParticipantRow p : rows) {
                Row dataRow = sheet.createRow(rowIdx++);
                writeCell(dataRow, 0, nullToEmpty(p.firstName()), null);
                writeCell(dataRow, 1, nullToEmpty(p.lastName()), null);
                writeCell(dataRow, 2, nullToEmpty(p.companyDisplayName()), null);
                writeCell(dataRow, 3, p.role(), null);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            // try-with-resources close() disposes the SXSSF temp files (POI 5.x);
            // the deprecated explicit dispose() is redundant.
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Failed to generate name-badge XLSX for event {}", eventCode, e);
            throw new RuntimeException("Excel export failed for event " + eventCode, e);
        }
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
}
