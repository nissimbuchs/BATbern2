package ch.batbern.events.controller;

import ch.batbern.events.service.AdminSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final AdminSettingsService adminSettingsService;

    @GetMapping("/{key}")
    // No @PreAuthorize: GET is open for VPC-internal callers (Lambda forwarder, Story 10.26).
    // HTTP-level security (SecurityConfig) controls access; PUT retains ORGANIZER check.
    public ResponseEntity<Map<String, String>> getSetting(@PathVariable String key) {
        String value = adminSettingsService.getSetting(key).orElse(null);
        return ResponseEntity.ok(buildResponse(key, value));
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Map<String, String>> setSetting(
            @PathVariable String key,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String value = body.get("value");
        String updatedBy = authentication != null ? authentication.getName() : "system";
        adminSettingsService.setSetting(key, value, updatedBy);
        return ResponseEntity.ok(buildResponse(key, value));
    }

    private Map<String, String> buildResponse(String key, String value) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("key", key);
        response.put("value", value);
        return response;
    }
}
