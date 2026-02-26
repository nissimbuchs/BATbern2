package ch.batbern.partners.service;

import ch.batbern.partners.client.dto.AttendanceSummaryDTO;
import ch.batbern.partners.dto.PartnerDashboardDTO;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Excel export service for partner attendance data.
 * Story 8.1: AC4 — Export attendance table as XLSX.
 *
 * Uses SXSSFWorkbook for streaming (memory-efficient for large datasets).
 * Columns: Event, Date, Your Attendees, Total Attendees, Percentage
 * Footer: Totals + Cost Per Attendee
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerAttendanceExportService {

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Europe/Zurich"));

    /**
     * Generate XLSX bytes for the attendance dashboard.
     *
     * @param companyName company name for the sheet title
     * @param dashboard   dashboard data from PartnerAnalyticsService
     * @return raw XLSX bytes
     */
    public byte[] generateXlsx(String companyName, PartnerDashboardDTO dashboard) {
        log.debug("Generating attendance XLSX export for company={}", companyName);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Attendance - " + companyName);
            sheet.setColumnWidth(0, 3500); // Event Code
            sheet.setColumnWidth(1, 7000); // Event Title
            sheet.setColumnWidth(2, 4000); // Date
            sheet.setColumnWidth(3, 4500); // Your Attendees
            sheet.setColumnWidth(4, 4500); // Total Attendees
            sheet.setColumnWidth(5, 3500); // Percentage

            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle footerStyle = buildFooterStyle(workbook);

            // Header row
            Row header = sheet.createRow(0);
            writeCell(header, 0, "Event Code", headerStyle);
            writeCell(header, 1, "Event Title", headerStyle);
            writeCell(header, 2, "Date", headerStyle);
            writeCell(header, 3, "Your Attendees", headerStyle);
            writeCell(header, 4, "Total Attendees", headerStyle);
            writeCell(header, 5, "Percentage (%)", headerStyle);

            // Data rows
            List<AttendanceSummaryDTO> summaries = dashboard.attendanceSummary();
            long totalCompanyAttendees = 0;
            long totalAttendees = 0;

            for (int i = 0; i < summaries.size(); i++) {
                AttendanceSummaryDTO s = summaries.get(i);
                Row row = sheet.createRow(i + 1);

                double percentage = s.totalAttendees() > 0
                    ? (double) s.companyAttendees() / s.totalAttendees() * 100.0
                    : 0.0;

                writeCell(row, 0, s.eventCode(), null);
                writeCell(row, 1, s.eventTitle() != null ? s.eventTitle() : "", null);
                writeCell(row, 2, DATE_FORMATTER.format(s.eventDate()), null);
                writeNumericCell(row, 3, s.companyAttendees());
                writeNumericCell(row, 4, s.totalAttendees());
                writeCell(row, 5, String.format("%.1f%%", percentage), null);

                totalCompanyAttendees += s.companyAttendees();
                totalAttendees += s.totalAttendees();
            }

            // Footer row — totals
            int footerIdx = summaries.size() + 1;
            Row footerRow = sheet.createRow(footerIdx);
            writeCell(footerRow, 0, "TOTAL", footerStyle);
            writeCell(footerRow, 1, "", footerStyle);
            writeCell(footerRow, 2, "", footerStyle);
            writeNumericCellStyled(footerRow, 3, totalCompanyAttendees, footerStyle);
            writeNumericCellStyled(footerRow, 4, totalAttendees, footerStyle);

            double overallPct = totalAttendees > 0
                ? (double) totalCompanyAttendees / totalAttendees * 100.0
                : 0.0;
            writeCell(footerRow, 5, String.format("%.1f%%", overallPct), footerStyle);

            // Cost per attendee row
            if (dashboard.costPerAttendee() != null) {
                Row kpiRow = sheet.createRow(footerIdx + 1);
                writeCell(kpiRow, 0, "Cost Per Attendee (CHF)", footerStyle);
                writeCell(kpiRow, 1,
                    dashboard.costPerAttendee().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    footerStyle);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();

        } catch (IOException e) {
            log.error("Failed to generate XLSX for company={}", companyName, e);
            throw new RuntimeException("Excel export failed", e);
        }
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

    private CellStyle buildFooterStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void writeCell(Row row, int col, String value, CellStyle style) {
        var cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private void writeNumericCell(Row row, int col, long value) {
        row.createCell(col).setCellValue(value);
    }

    private void writeNumericCellStyled(Row row, int col, long value, CellStyle style) {
        var cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }
}
