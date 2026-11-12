package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.domain.CompanyUserUpload;
import ch.batbern.companyuser.uploads.api.generated.UploadsApi;
import ch.batbern.companyuser.uploads.dto.generated.ConfirmUploadRequest;
import ch.batbern.companyuser.uploads.dto.generated.CreateUploadRequest;
import ch.batbern.companyuser.uploads.dto.generated.ImportUploadRequest;
import ch.batbern.companyuser.uploads.dto.generated.Upload;
import ch.batbern.companyuser.uploads.dto.generated.UploadPurpose;
import ch.batbern.companyuser.uploads.dto.generated.UploadSlot;
import ch.batbern.companyuser.uploads.dto.generated.UploadTarget;
import ch.batbern.shared.upload.UploadRecord;
import ch.batbern.shared.upload.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * The upload contract for the purposes this service owns ({@code company-logo}), Story 17.11. Who may
 * do what is per purpose and target, so the method rule is "signed in" and the handler decides the
 * rest (403); another service's purpose is 404.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UploadsController implements UploadsApi {

    private final UploadService<CompanyUserUpload> uploads;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UploadSlot> createFileUpload(String purpose, CreateUploadRequest request) {
        UploadService.PresignedUpload slot = uploads.create(purpose, request.getFileName(),
                request.getContentType(), request.getSize(), target(request.getTarget()));
        return ResponseEntity.status(HttpStatus.CREATED).body(UploadSlot.builder()
                .uploadId(slot.uploadId())
                .uploadUrl(slot.uploadUrl())
                .expiresAt(slot.expiresAt().atOffset(ZoneOffset.UTC))
                .requiredHeaders(slot.requiredHeaders())
                .build());
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Upload> confirmFileUpload(String purpose, String uploadId, ConfirmUploadRequest request) {
        return ResponseEntity.ok(view(uploads.confirm(purpose, uploadId,
                target(request == null ? null : request.getTarget()))));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Upload> importFileUpload(String purpose, ImportUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(view(uploads.importFromUrl(purpose, request.getUrl(), request.getFileName())));
    }

    static Map<String, String> target(UploadTarget target) {
        Map<String, String> keys = new HashMap<>();
        if (target != null) {
            keys.put("companyName", target.getCompanyName());
            keys.put("eventCode", target.getEventCode());
        }
        return keys;
    }

    private static Upload view(UploadRecord upload) {
        return Upload.builder()
                .uploadId(upload.getUploadId())
                .purpose(UploadPurpose.fromValue(upload.getPurpose()))
                .status(Upload.StatusEnum.fromValue(upload.getStatus().name()))
                .fileUrl(upload.getFileUrl())
                .build();
    }
}
