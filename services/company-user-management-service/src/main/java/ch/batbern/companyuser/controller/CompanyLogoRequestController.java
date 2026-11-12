package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.api.generated.CompanyLogoRequestsApi;
import ch.batbern.companyuser.client.MailCenterRequestContract;
import ch.batbern.companyuser.domain.CompanyLogoRequest;
import ch.batbern.companyuser.dto.generated.CompanyLogoRequestCreate;
import ch.batbern.companyuser.dto.generated.CompanyLogoRequestDecline;
import ch.batbern.companyuser.dto.generated.CompanyLogoRequestView;
import ch.batbern.companyuser.service.CompanyLogoRequestService;
import ch.batbern.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * #1246: participants ask for a new company logo, organizers decide in the Mail Center. Contract:
 * {@code users-api.openapi.yml}, tag Company Logo Requests.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CompanyLogoRequestController implements CompanyLogoRequestsApi {

    private final CompanyLogoRequestService service;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CompanyLogoRequestView> createCompanyLogoRequest(CompanyLogoRequestCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(view(service.request(request.getLogoUploadId(), request.getNote())));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CompanyLogoRequestView> getMyOpenCompanyLogoRequest() {
        return service.openForCaller()
                .map(d -> ResponseEntity.ok(view(d)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<CompanyLogoRequestView> getCompanyLogoRequest(UUID requestId) {
        return ResponseEntity.ok(view(service.get(requestId)));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<CompanyLogoRequestView> approveCompanyLogoRequest(UUID requestId) {
        return ResponseEntity.ok(view(service.approve(requestId)));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<CompanyLogoRequestView> declineCompanyLogoRequest(UUID requestId,
            CompanyLogoRequestDecline request) {
        return ResponseEntity.ok(view(service.decline(requestId, request == null ? null : request.getReason())));
    }

    private static CompanyLogoRequestView view(CompanyLogoRequestService.Details d) {
        CompanyLogoRequest r = d.request();
        return new CompanyLogoRequestView()
                .requestId(r.getId())
                .status(CompanyLogoRequestView.StatusEnum.fromValue(r.getStatus().name()))
                .requesterUsername(r.getRequesterUsername())
                .requesterName(d.requester() == null ? null : CompanyLogoRequestService.fullName(d.requester()))
                .companyName(r.getCompanyName())
                .companyDisplayName(d.company() == null ? null : d.company().getDisplayName())
                .oldLogoUrl(r.getOldLogoUrl())
                .newLogoUrl(r.getNewLogoUrl())
                .note(r.getNote())
                .createdAt(r.getCreatedAt().atOffset(ZoneOffset.UTC))
                .decidedBy(r.getDecidedBy())
                .decidedAt(r.getDecidedAt() == null ? null : r.getDecidedAt().atOffset(ZoneOffset.UTC))
                .reason(r.getReason());
    }

    @ExceptionHandler(CompanyLogoRequestService.RequestConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(CompanyLogoRequestService.RequestConflictException e,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, e.code(), e.getMessage(), request);
    }

    @ExceptionHandler(CompanyLogoRequestService.LogoNotReadyException.class)
    public ResponseEntity<ErrorResponse> notReady(CompanyLogoRequestService.LogoNotReadyException e,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "LOGO_NOT_READY", e.getMessage(), request);
    }

    @ExceptionHandler(MailCenterRequestContract.MailCenterCallFailedException.class)
    public ResponseEntity<ErrorResponse> mailCenterFailed(
            MailCenterRequestContract.MailCenterCallFailedException e, HttpServletRequest request) {
        return error(HttpStatus.BAD_GATEWAY, "LOGO_REQUEST_NOT_DELIVERED",
                "The request could not be passed to the organizers; nothing was stored. Try again.", request);
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(status.value())
                .error(status.getReasonPhrase())
                .errorCode(code)
                .message(message)
                .build());
    }
}
