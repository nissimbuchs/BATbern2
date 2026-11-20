package ch.batbern.partners.usermerge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

import java.util.List;
import java.util.Map;

/**
 * #1251: partner-coordination implements the company-reference contract CUMS owns
 * ({@code /internal/company-references/*} in {@code docs/api/internal/cums-internal.openapi.yml},
 * ADR-016 decision 5). CUMS calls it for a company merge, a rename and the counts of its list of
 * candidate duplicate companies; the user's JWT is forwarded
 * when there is one, never required. {@code /internal/**} is not routed by the API gateway, which
 * also denies it.
 */
@RestController
@RequiredArgsConstructor
public class InternalCompanyReferenceController {

    private final CompanyReferenceService service;

    @GetMapping("/internal/company-references/preview")
    public CompanyReferenceService.Preview preview(@RequestParam String source, @RequestParam String target) {
        return service.preview(source, target);
    }

    @PostMapping("/internal/company-references/merge")
    public CompanyReferenceService.Result merge(@Valid @RequestBody MergeRequest request) {
        return service.merge(request.source(), request.target(), request.performedBy(), request.trigger());
    }

    @PostMapping("/internal/company-references/counts")
    public CountResult counts(@Valid @RequestBody CountRequest request) {
        return new CountResult(service.counts(request.keys()));
    }

    @ExceptionHandler(CompanyReferenceService.BothPartnersException.class)
    public ResponseEntity<Map<String, String>> bothPartners(CompanyReferenceService.BothPartnersException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", CompanyReferenceService.BOTH_PARTNERS, "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "INVALID_MERGE", "message", e.getMessage()));
    }

    public record MergeRequest(
            @NotBlank @Size(max = 255) String source,
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9]{1,12}$") String target,
            @NotBlank @Size(max = 100) String performedBy,
            @NotBlank @Pattern(regexp = "MERGE|RENAME") String trigger) {
    }

    public record CountRequest(@NotNull @Size(min = 1, max = 2000) List<@NotBlank @Size(max = 255) String> keys) {
    }

    public record CountResult(Map<String, Map<String, Integer>> counts) {
    }
}
