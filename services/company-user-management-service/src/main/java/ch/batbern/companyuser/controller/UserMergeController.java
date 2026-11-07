package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.api.generated.ProfileMergeApi;
import ch.batbern.companyuser.client.EventManagementMailCenterClient;
import ch.batbern.companyuser.client.EventManagementUserMergeClient;
import ch.batbern.companyuser.domain.ProfileMergeRequest;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.dto.generated.AccountMergeRequest;
import ch.batbern.companyuser.dto.generated.ConfirmProfileClaimRequest;
import ch.batbern.companyuser.dto.generated.DuplicateCandidate;
import ch.batbern.companyuser.dto.generated.DuplicateCandidateProfile;
import ch.batbern.companyuser.dto.generated.DuplicateDismissal;
import ch.batbern.companyuser.dto.generated.ProfileClaimCandidate;
import ch.batbern.companyuser.dto.generated.ProfileClaimConfirmResponse;
import ch.batbern.companyuser.dto.generated.ProfileClaimRequest;
import ch.batbern.companyuser.dto.generated.ProfileMergeRequestCreate;
import ch.batbern.companyuser.dto.generated.ProfileMergeRequestCreated;
import ch.batbern.companyuser.dto.generated.ProfileMergeRequestDecline;
import ch.batbern.companyuser.dto.generated.ProfileMergeRequestView;
import ch.batbern.companyuser.dto.generated.UserMergePreview;
import ch.batbern.companyuser.dto.generated.UserMergeProfileSummary;
import ch.batbern.companyuser.dto.generated.UserMergeRequest;
import ch.batbern.companyuser.dto.generated.UserMergeResult;
import ch.batbern.companyuser.security.SecurityContextHelper;
import ch.batbern.companyuser.service.DuplicateCandidateService;
import ch.batbern.companyuser.service.ProfileClaimService;
import ch.batbern.companyuser.service.ProfileMergeRequestService;
import ch.batbern.companyuser.service.UserMergeService;
import ch.batbern.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.Map;

/**
 * #1183 C (an organizer merges a profile without login into another profile) and D (a person claims
 * an earlier profile under their name). Contract: {@code users-api.openapi.yml}, tag Profile Merge.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class UserMergeController implements ProfileMergeApi {

    private final UserMergeService mergeService;
    private final ProfileClaimService claimService;
    private final ProfileMergeRequestService mergeRequestService;
    private final DuplicateCandidateService duplicateService;
    private final SecurityContextHelper securityContext;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<UserMergePreview> getUserMergePreview(String username, String source) {
        UserMergeService.Preview p = mergeService.preview(username, source);
        return ResponseEntity.ok(new UserMergePreview()
                .target(summary(p.target()))
                .source(summary(p.source()))
                .moves(p.events().moves())
                .conflicts(p.events().conflicts())
                .blockers(p.events().blockers())
                .canMerge(p.canMerge())
                .reasons(p.reasons()));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<UserMergeResult> mergeUserProfile(String username, UserMergeRequest request) {
        UserMergeService.Result r = mergeService.merge(username, request.getSourceUsername(),
                securityContext.getCurrentUsername(), "ORGANIZER");
        return ResponseEntity.ok(new UserMergeResult()
                .targetUsername(r.targetUsername())
                .sourceUsername(r.sourceUsername())
                .moved(r.moved())
                .removed(r.removed()));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProfileClaimCandidate>> listProfileClaimCandidates() {
        return ResponseEntity.ok(claimService.candidates().stream()
                .map(c -> new ProfileClaimCandidate()
                        .candidateId(c.id())
                        .maskedEmail(c.maskedEmail())
                        .companyName(c.companyName())
                        .requestPending(c.requestPending())
                        .placeholderAddress(c.placeholderAddress()))
                .toList());
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> requestProfileClaim(ProfileClaimRequest request) {
        claimService.requestClaim(request.getCandidateId());
        return ResponseEntity.accepted().build();
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileClaimConfirmResponse> confirmProfileClaim(ConfirmProfileClaimRequest request) {
        boolean merged = claimService.confirm(request.getToken());
        return ResponseEntity.ok(new ProfileClaimConfirmResponse().merged(merged).alreadyMerged(!merged));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> requestAccountMerge(AccountMergeRequest request) {
        claimService.requestAccountMerge(request.getEmail());
        return ResponseEntity.accepted().build();
    }

    // ---------------------------------------------------------------- duplicate candidates

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<DuplicateCandidate>> listDuplicateCandidates() {
        return ResponseEntity.ok(duplicateService.candidates().stream()
                .map(c -> new DuplicateCandidate()
                        .rule(DuplicateCandidate.RuleEnum.fromValue(c.rule().name()))
                        .confidence(DuplicateCandidate.ConfidenceEnum.fromValue(c.confidence().name()))
                        .organizerCanMerge(c.organizerCanMerge())
                        .target(candidateProfile(c.target()))
                        .source(candidateProfile(c.source())))
                .toList());
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> dismissDuplicateCandidate(DuplicateDismissal request) {
        duplicateService.dismiss(request.getUsernameA(), request.getUsernameB(),
                securityContext.getCurrentUsername());
        return ResponseEntity.noContent().build();
    }

    private static DuplicateCandidateProfile candidateProfile(DuplicateCandidateService.Profile p) {
        return new DuplicateCandidateProfile()
                .username(p.username())
                .firstName(p.firstName())
                .lastName(p.lastName())
                .email(p.email())
                .companyId(p.companyId())
                .hasLogin(p.hasLogin())
                .placeholderAddress(p.placeholderAddress())
                .createdAt(p.createdAt() == null ? null : p.createdAt().atOffset(ZoneOffset.UTC));
    }

    // ---------------------------------------------------------------- #1237 merge requests

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileMergeRequestCreated> requestProfileMerge(ProfileMergeRequestCreate request) {
        ProfileMergeRequest created = mergeRequestService.request(request.getCandidateId(), request.getNote());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ProfileMergeRequestCreated().requestId(created.getId()));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ProfileMergeRequestView> getProfileMergeRequest(UUID requestId) {
        ProfileMergeRequestService.Decided d = mergeRequestService.get(requestId);
        return ResponseEntity.ok(view(d.request(), d.preview()));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ProfileMergeRequestView> approveProfileMergeRequest(UUID requestId) {
        return ResponseEntity.ok(view(mergeRequestService.approve(requestId), null));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ProfileMergeRequestView> declineProfileMergeRequest(UUID requestId,
            ProfileMergeRequestDecline request) {
        return ResponseEntity.ok(view(mergeRequestService.decline(requestId, request.getReason()), null));
    }

    private ProfileMergeRequestView view(ProfileMergeRequest r, UserMergeService.Preview preview) {
        return new ProfileMergeRequestView()
                .requestId(r.getId())
                .status(ProfileMergeRequestView.StatusEnum.fromValue(r.getStatus().name()))
                .requesterUsername(r.getRequesterUsername())
                .sourceUsername(r.getSourceUsername())
                .note(r.getNote())
                .createdAt(r.getCreatedAt().atOffset(ZoneOffset.UTC))
                .decidedBy(r.getDecidedBy())
                .decidedAt(r.getDecidedAt() == null ? null : r.getDecidedAt().atOffset(ZoneOffset.UTC))
                .reason(r.getReason())
                .preview(preview == null ? null : previewDto(preview));
    }

    private static UserMergePreview previewDto(UserMergeService.Preview p) {
        return new UserMergePreview()
                .target(summary(p.target()))
                .source(summary(p.source()))
                .moves(p.events().moves())
                .conflicts(p.events().conflicts())
                .blockers(p.events().blockers())
                .canMerge(p.canMerge())
                .reasons(p.reasons());
    }

    @ExceptionHandler(ProfileMergeRequestService.RequestConflictException.class)
    public ResponseEntity<ErrorResponse> requestConflict(ProfileMergeRequestService.RequestConflictException e,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, e.code(), e.getMessage(), request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException e, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage(), request, null);
    }

    @ExceptionHandler(EventManagementMailCenterClient.MailCenterCallFailedException.class)
    public ResponseEntity<ErrorResponse> mailCenterFailed(
            EventManagementMailCenterClient.MailCenterCallFailedException e, HttpServletRequest request) {
        return error(HttpStatus.BAD_GATEWAY, "MERGE_REQUEST_NOT_DELIVERED",
                "The request could not be passed to the organizers; nothing was stored. Try again.", request, null);
    }

    private static UserMergeProfileSummary summary(User u) {
        return new UserMergeProfileSummary()
                .username(u.getUsername())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .companyId(u.getCompanyId())
                .hasLogin(u.getCognitoUserId() != null)
                .roles(u.getRoles().stream().map(Enum::name).sorted().toList())
                .additionalEmails(u.getAdditionalEmails().stream().map(UserAdditionalEmail::getEmail)
                        .sorted().toList());
    }

    @ExceptionHandler(UserMergeService.MergeNotAllowedException.class)
    public ResponseEntity<ErrorResponse> notAllowed(UserMergeService.MergeNotAllowedException e,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "MERGE_NOT_ALLOWED", e.getMessage(), request,
                Map.of("reasons", e.reasons()));
    }

    @ExceptionHandler(EventManagementUserMergeClient.MergeBlockedException.class)
    public ResponseEntity<ErrorResponse> blocked(EventManagementUserMergeClient.MergeBlockedException e,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "MERGE_BLOCKED", e.getMessage(), request, null);
    }

    @ExceptionHandler(EventManagementUserMergeClient.MergeCallFailedException.class)
    public ResponseEntity<ErrorResponse> failed(EventManagementUserMergeClient.MergeCallFailedException e,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_GATEWAY, "MERGE_EVENT_SERVICE_FAILED",
                "Event data could not be moved; nothing was merged. Try again.", request, null);
    }

    @ExceptionHandler(ProfileClaimService.PlaceholderAddressException.class)
    public ResponseEntity<ErrorResponse> placeholder(ProfileClaimService.PlaceholderAddressException e,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "PLACEHOLDER_ADDRESS", e.getMessage(), request, null);
    }

    @ExceptionHandler(ProfileClaimService.ClaimOtherAccountException.class)
    public ResponseEntity<ErrorResponse> otherAccount(ProfileClaimService.ClaimOtherAccountException e,
            HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "CLAIM_OTHER_ACCOUNT", e.getMessage(), request, null);
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
