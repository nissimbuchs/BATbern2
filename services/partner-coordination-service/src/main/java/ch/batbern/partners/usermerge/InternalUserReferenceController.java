package ch.batbern.partners.usermerge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * partner-coordination implements the user-reference contract CUMS owns
 * ({@code /internal/user-references/*} in {@code docs/api/internal/cums-internal.openapi.yml},
 * ADR-016 decision 5). CUMS calls it for a profile merge (#1183) and a primary-address change
 * (#1244); the user's JWT is forwarded when there is one, never required. {@code /internal/**} is not
 * routed by the API gateway, which also denies it.
 */
@RestController
@RequiredArgsConstructor
public class InternalUserReferenceController {

    private final UserReferenceService service;

    @GetMapping("/internal/user-references/preview")
    public UserReferenceService.Preview preview(@RequestParam String source, @RequestParam String target) {
        return service.preview(source, target);
    }

    @PostMapping("/internal/user-references/merge")
    public UserReferenceService.Result merge(@Valid @RequestBody MergeRequest request) {
        return service.merge(request.sourceUsername(), request.targetUsername(), request.performedBy(),
                request.trigger());
    }

    /** Nothing here follows a primary address; the call is answered so CUMS can treat every holder alike. */
    @PostMapping("/internal/user-references/primary-email")
    public Map<String, Integer> primaryEmailChanged(@Valid @RequestBody PrimaryEmailChange request) {
        return Map.of("updated", 0);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "INVALID_MERGE", "message", e.getMessage()));
    }

    public record MergeRequest(
            @NotBlank @Size(max = 100) String sourceUsername,
            @NotBlank @Size(max = 100) String targetUsername,
            @NotBlank @Size(max = 100) String performedBy,
            @NotBlank @Pattern(regexp = "ORGANIZER|SELF_CLAIM") String trigger,
            Map<String, Object> sourceProfile) {
    }

    public record PrimaryEmailChange(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 255) String oldEmail,
            @NotBlank @Size(max = 255) String newEmail) {
    }
}
