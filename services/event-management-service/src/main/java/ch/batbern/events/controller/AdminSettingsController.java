package ch.batbern.events.controller;

import ch.batbern.events.core.api.generated.AdminSettingsApi;
import ch.batbern.events.core.dto.generated.AdminSettingResponse;
import ch.batbern.events.core.dto.generated.SetAdminSettingRequest;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.AdminSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin settings (app_settings) read/write.
 *
 * <p>API-consolidation Phase 7: implements the generated {@link AdminSettingsApi}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AdminSettingsController implements AdminSettingsApi {

    private final AdminSettingsService adminSettingsService;
    private final SecurityContextHelper securityContextHelper;

    // No @PreAuthorize: GET is open for VPC-internal callers (Lambda forwarder, Story 10.26).
    // HTTP-level security (SecurityConfig) controls access; PUT retains the ORGANIZER check.
    @Override
    public ResponseEntity<AdminSettingResponse> getAdminSetting(String key) {
        String value = adminSettingsService.getSetting(key).orElse(null);
        return ResponseEntity.ok(buildResponse(key, value));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AdminSettingResponse> setAdminSetting(String key, SetAdminSettingRequest request) {
        String value = request.getValue();
        String resolved = securityContextHelper.getCurrentUsernameOrNull();
        String updatedBy = resolved != null ? resolved : "system";
        adminSettingsService.setSetting(key, value, updatedBy);
        return ResponseEntity.ok(buildResponse(key, value));
    }

    private AdminSettingResponse buildResponse(String key, String value) {
        return AdminSettingResponse.builder().key(key).value(value).build();
    }
}
