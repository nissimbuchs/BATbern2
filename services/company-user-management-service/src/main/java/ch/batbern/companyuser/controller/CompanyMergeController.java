package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.api.generated.CompanyMergeApi;
import ch.batbern.companyuser.client.CompanyReferenceContract;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.dto.generated.CompanyLogo;
import ch.batbern.companyuser.dto.generated.CompanyMergePreview;
import ch.batbern.companyuser.dto.generated.CompanyMergeRequest;
import ch.batbern.companyuser.dto.generated.CompanyMergeResult;
import ch.batbern.companyuser.dto.generated.CompanyMergeSide;
import ch.batbern.companyuser.security.SecurityContextHelper;
import ch.batbern.companyuser.service.CompanyKeyMover;
import ch.batbern.companyuser.service.CompanyMergeService;
import ch.batbern.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * #1251 (issue sweep epic 4, story 4): an organizer merges a duplicate company into another.
 * Contract: {@code companies-api.openapi.yml}, tag Company Merge. The model is
 * {@link UserMergeController} (profile merge).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class CompanyMergeController implements CompanyMergeApi {

    private final CompanyMergeService mergeService;
    private final SecurityContextHelper securityContext;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<CompanyMergePreview> getCompanyMergePreview(String name, String source) {
        CompanyMergeService.Preview p = mergeService.preview(name, source);
        return ResponseEntity.ok(new CompanyMergePreview()
                .target(side(p.target()))
                .source(side(p.source()))
                .moves(p.moves())
                .conflicts(p.conflicts())
                .blockers(p.blockers())
                .fills(p.fills())
                .canMerge(p.canMerge())
                .reasons(p.reasons())
                .canSwap(p.canSwap()));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<CompanyMergeResult> mergeCompany(String name, CompanyMergeRequest request) {
        CompanyMergeService.Result r = mergeService.merge(name, request.getSourceName(),
                securityContext.getCurrentUsername());
        return ResponseEntity.ok(new CompanyMergeResult()
                .targetName(r.targetName())
                .sourceName(r.sourceName())
                .moved(r.moved())
                .removed(r.removed())
                .filled(r.filled()));
    }

    private static CompanyMergeSide side(CompanyMergeService.Side s) {
        Company c = s.company();
        return new CompanyMergeSide()
                .name(c.getName())
                .displayName(c.getDisplayName())
                .website(c.getWebsite())
                .industry(c.getIndustry())
                .description(c.getDescription())
                .isVerified(c.isVerified())
                .userCount((int) s.userCount())
                .logo(c.getLogoUrl() == null || c.getLogoUrl().isBlank() ? null : new CompanyLogo()
                        .url(uri(c.getLogoUrl()))
                        .s3Key(c.getLogoS3Key())
                        .fileId(c.getLogoFileId()));
    }

    private static URI uri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            return null; // as CompanyService: a malformed stored URL is left out, the rest of the logo shows
        }
    }

    /** Same company 400, a key reason 409; {@code details.reasons} and {@code details.canSwap}. */
    @ExceptionHandler(CompanyMergeService.MergeNotAllowedException.class)
    public ResponseEntity<ErrorResponse> notAllowed(CompanyMergeService.MergeNotAllowedException e,
            HttpServletRequest request) {
        return error(e.sameCompany() ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT, "MERGE_NOT_ALLOWED",
                e.getMessage(), request, Map.of("reasons", e.reasons(), "canSwap", e.canSwap()));
    }

    /**
     * A holder refused: both companies are partners ({@code MERGE_BLOCKED_BOTH_PARTNERS}, from the
     * preview) or another refusal, also one at the merge call itself ({@code MERGE_BLOCKED}). Nothing moved.
     */
    @ExceptionHandler(CompanyReferenceContract.MoveBlockedException.class)
    public ResponseEntity<ErrorResponse> blocked(CompanyReferenceContract.MoveBlockedException e,
            HttpServletRequest request) {
        String code = e.blockers().contains(CompanyMergeService.BOTH_PARTNERS_BLOCKER)
                ? CompanyMergeService.BOTH_PARTNERS_BLOCKER : "MERGE_BLOCKED";
        return error(HttpStatus.CONFLICT, code, e.getMessage(), request, null);
    }

    /** A holder did not answer the preview, or did not complete its part of the merge (502). */
    @ExceptionHandler(CompanyReferenceContract.CallFailedException.class)
    public ResponseEntity<ErrorResponse> holderFailed(CompanyReferenceContract.CallFailedException e,
            HttpServletRequest request) {
        if (HttpMethod.GET.matches(request.getMethod())) {
            log.error("Company merge preview incomplete: {}", e.getMessage(), e);
            return error(HttpStatus.BAD_GATEWAY, "MERGE_PREVIEW_INCOMPLETE",
                    "Not every service answered; try the preview again.", request, null);
        }
        return mergeIncomplete(e, request);
    }

    /** The holders moved, but the merge here did not complete (502); the same merge finishes it. */
    @ExceptionHandler(CompanyKeyMover.MoveIncompleteException.class)
    public ResponseEntity<ErrorResponse> incomplete(CompanyKeyMover.MoveIncompleteException e,
            HttpServletRequest request) {
        return mergeIncomplete(e, request);
    }

    private static ResponseEntity<ErrorResponse> mergeIncomplete(RuntimeException e, HttpServletRequest request) {
        log.error("Company merge incomplete: {}", e.getMessage(), e);
        return error(HttpStatus.BAD_GATEWAY, "MERGE_INCOMPLETE",
                "Not every service could move its references; the companies were not merged. "
                        + "Merge them again to finish.", request, null);
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message,
            HttpServletRequest request, Map<String, Object> details) {
        return ResponseEntity.status(status).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(status.value())
                .error(status.getReasonPhrase())
                .errorCode(code)
                .message(message)
                .details(details)
                .build());
    }
}
