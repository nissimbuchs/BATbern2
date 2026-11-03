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
 * #1183 C/D: company-user-management merges two profiles of one person and asks event-management to
 * move its rows ({@link UserMergeService}). No JWT: service to service in the VPC. {@code /internal/**}
 * is not routed by the API gateway, which also denies it; the production chain's permit is covered by
 * {@code InternalPathSecurityTest}. Not part of a public OpenAPI contract, like {@code InternalMailController}.
 */
@RestController
@RequiredArgsConstructor
public class InternalUserMergeController {

    private final UserMergeService mergeService;

    @GetMapping("/internal/users/merge-preview")
    public UserMergeService.Preview preview(@RequestParam String source, @RequestParam String target) {
        return mergeService.preview(source, target);
    }

    @PostMapping("/internal/users/merge")
    public UserMergeService.Result merge(@Valid @RequestBody MergeRequest request) {
        return mergeService.merge(request.sourceUsername(), request.targetUsername(), request.performedBy(),
                request.trigger(), request.sourceProfile());
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
