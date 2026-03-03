package ch.batbern.events.controller;

import ch.batbern.events.dto.export.AssetImportResult;
import ch.batbern.events.dto.export.AssetManifestResponse;
import ch.batbern.events.dto.export.LegacyExportEnvelope;
import ch.batbern.events.dto.export.LegacyImportResult;
import ch.batbern.events.service.LegacyExportService;
import ch.batbern.events.service.LegacyImportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Admin controller for legacy BAT format export and import.
 * Story 10.20: AC1, AC2, AC3, AC4
 *
 * All endpoints are organizer-only (403 for other roles).
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ORGANIZER')")
@RequiredArgsConstructor
@Slf4j
public class AdminExportImportController {

    private final LegacyExportService exportService;
    private final LegacyImportService importService;
    private final ObjectMapper objectMapper;

    /**
     * AC1: Export all data in legacy BAT JSON format.
     * Returns a downloadable JSON file with Content-Disposition: attachment.
     */
    @GetMapping("/export/legacy")
    public ResponseEntity<byte[]> exportLegacy() throws JsonProcessingException {
        log.info("Admin: legacy data export requested");
        LegacyExportEnvelope envelope = exportService.exportAll();
        byte[] json = objectMapper.writeValueAsBytes(envelope);
        String filename = "batbern-export-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    /**
     * AC2: Export presigned-URL asset manifest (portraits, logos, materials).
     * Each URL valid for 1 hour.
     */
    @GetMapping("/export/assets")
    public ResponseEntity<AssetManifestResponse> exportAssets() {
        log.info("Admin: asset manifest export requested");
        return ResponseEntity.ok(exportService.exportAssetManifest());
    }

    /**
     * AC3: Import data from a legacy BAT JSON file (upsert, idempotent).
     * Returns counts of imported/skipped/errored entities.
     * Returns 400 if the uploaded file is not valid JSON.
     */
    @PostMapping(value = "/import/legacy", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LegacyImportResult> importLegacy(
            @RequestParam("file") MultipartFile file) throws IOException {
        log.info("Admin: legacy data import requested, file={}, size={}", file.getOriginalFilename(), file.getSize());
        try {
            LegacyExportEnvelope envelope = objectMapper.readValue(file.getBytes(), LegacyExportEnvelope.class);
            return ResponseEntity.ok(importService.importAll(envelope));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid import file: " + e.getOriginalMessage());
        }
    }

    /**
     * AC4: Import assets from a ZIP file, uploading each entry to S3 under imports/{timestamp}/.
     */
    @PostMapping(value = "/import/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetImportResult> importAssets(
            @RequestParam("file") MultipartFile file) throws IOException {
        log.info("Admin: asset import requested, file={}, size={}", file.getOriginalFilename(), file.getSize());
        return ResponseEntity.ok(importService.importAssets(file));
    }
}
