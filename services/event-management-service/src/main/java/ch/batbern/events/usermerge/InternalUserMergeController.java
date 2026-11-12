package ch.batbern.events.usermerge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * #1183 C/D: event-management's part of the user-reference contract CUMS owns (ADR-016 decision 5,
 * {@code /internal/user-references/*} in {@code docs/api/internal/cums-internal.openapi.yml}). CUMS
 * merges two profiles of one person and every holder moves its rows ({@link UserMergeService}); here
 * also the merge log. The user's JWT is forwarded when there is one, never required.
 * {@code /internal/**} is not routed by the API gateway, which also denies it; the production
 * chain's permit is covered by {@code InternalPathSecurityTest}.
 */
@RestController
@RequiredArgsConstructor
public class InternalUserMergeController {

    private final UserMergeService mergeService;

    @GetMapping("/internal/user-references/preview")
    public UserMergeService.Preview preview(@RequestParam String source, @RequestParam String target) {
        return mergeService.preview(source, target);
    }

    @PostMapping("/internal/user-references/merge")
    public UserMergeService.Result merge(@Valid @RequestBody MergeRequest request) {
        return mergeService.merge(request.sourceUsername(), request.targetUsername(), request.performedBy(),
                request.trigger(), request.sourceProfile());
    }

    /**
     * #1244: the person made another address their primary one. Upcoming registrations made under
     * the old primary address follow, so deregistration links, slides and the -participants@ list
     * reach the new one. Past registrations and registrations under other addresses stay.
     */
    @PostMapping("/internal/user-references/primary-email")
    public Map<String, Integer> primaryEmailChanged(@Valid @RequestBody PrimaryEmailChange request) {
        return Map.of("updated", mergeService.primaryEmailChanged(request.username(), request.oldEmail(),
                request.newEmail()));
    }

    public record PrimaryEmailChange(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 255) String oldEmail,
            @NotBlank @Size(max = 255) String newEmail) {
    }

    @ExceptionHandler(UserMergeService.MergeBlockedException.class)
    public ResponseEntity<Map<String, String>> blocked(UserMergeService.MergeBlockedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "MERGE_BLOCKED", "message", e.getMessage()));
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
}
