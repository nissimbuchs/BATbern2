package ch.batbern.companyuser.service;

import ch.batbern.companyuser.client.EventManagementMailCenterClient;
import ch.batbern.companyuser.domain.ProfileMergeRequest;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.repository.ProfileMergeRequestRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * #1237: claiming an earlier profile without access to its old address. The mail to the old address
 * ({@link ProfileClaimService}) is the ownership proof; without it a human decides. The person asks
 * (with an optional note), the request appears in the Mail Center, and any organizer approves (the
 * organizer merge, without carrying the unverifiable old addresses) or declines with a reason. The
 * person is mailed at their current address either way.
 *
 * <p>Guard rails: one open request per pair; a declined pair can ask again after
 * {@link #DECLINE_COOLDOWN}; no automatic merge; no knowledge questions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileMergeRequestService {

    static final Duration DECLINE_COOLDOWN = Duration.ofDays(30);

    private final ProfileClaimService claimService;
    private final ProfileMergeRequestRepository requests;
    private final UserRepository userRepository;
    private final UserMergeService mergeService;
    private final EventManagementMailCenterClient mailCenter;
    private final ProfileMergeRequestEmailService emailService;
    private final SecurityContextHelper securityContext;

    public record Decided(ProfileMergeRequest request, UserMergeService.Preview preview) {
    }

    /** The caller asks to merge one of their candidates; opens the Mail Center conversation. */
    @Transactional
    public ProfileMergeRequest request(UUID candidateId, String note) {
        ProfileClaimService.ClaimPair pair = claimService.pair(candidateId);
        String requester = pair.caller().getUsername();
        String source = pair.candidate().getUsername();
        if (requests.existsByRequesterUsernameAndSourceUsernameAndStatus(requester, source,
                ProfileMergeRequest.Status.OPEN)) {
            throw new RequestConflictException("MERGE_REQUEST_OPEN", "A request for this profile is already open");
        }
        if (requests.existsByRequesterUsernameAndSourceUsernameAndStatusAndDecidedAtAfter(requester, source,
                ProfileMergeRequest.Status.DECLINED, Instant.now().minus(DECLINE_COOLDOWN))) {
            throw new RequestConflictException("MERGE_REQUEST_DECLINED",
                    "This profile was declined recently; ask again after 30 days");
        }
        String trimmedNote = note == null || note.isBlank() ? null : note.trim();
        ProfileMergeRequest saved = requests.saveAndFlush(ProfileMergeRequest.builder()
                .requesterUsername(requester)
                .sourceUsername(source)
                .note(trimmedNote)
                .status(ProfileMergeRequest.Status.OPEN)
                .createdAt(Instant.now())
                .build());
        User caller = pair.caller();
        String name = (nullToEmpty(caller.getFirstName()) + " " + nullToEmpty(caller.getLastName())).trim();
        mailCenter.openMergeRequest(new EventManagementMailCenterClient.OpenRequest(saved.getId(), name,
                caller.getEmail(), "Profil zusammenführen: " + name, trimmedNote));
        log.info("PROFILE_MERGE_REQUESTED id={} requester={} source={}", saved.getId(), requester, source);
        return saved;
    }

    /** The request and, while it is open, the merge preview (organizers). */
    @Transactional(readOnly = true)
    public Decided get(UUID id) {
        ProfileMergeRequest request = load(id);
        UserMergeService.Preview preview = request.getStatus() == ProfileMergeRequest.Status.OPEN
                ? mergeService.preview(request.getRequesterUsername(), request.getSourceUsername())
                : null;
        return new Decided(request, preview);
    }

    /** Runs the organizer merge without carrying the old addresses, mails the requester. */
    @Transactional
    public ProfileMergeRequest approve(UUID id) {
        ProfileMergeRequest request = openRequest(id);
        String organizer = securityContext.getCurrentUsername();
        mergeService.merge(request.getRequesterUsername(), request.getSourceUsername(), organizer, "ORGANIZER",
                false);
        decide(request, ProfileMergeRequest.Status.APPROVED, organizer, null);
        // After the merge, nothing may roll this transaction back: event-management has already
        // moved its rows. The mail and closing the conversation are best effort.
        User requester = requester(request);
        afterDecision(request, organizer,
                () -> emailService.sendApproved(requester.getEmail(), requester.getFirstName(), locale(requester)));
        log.info("PROFILE_MERGE_REQUEST_APPROVED id={} by={}", id, organizer);
        return request;
    }

    /** Declines with a reason that is mailed to the requester; nothing is merged. */
    @Transactional
    public ProfileMergeRequest decline(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required");
        }
        ProfileMergeRequest request = openRequest(id);
        String organizer = securityContext.getCurrentUsername();
        decide(request, ProfileMergeRequest.Status.DECLINED, organizer, reason.trim());
        User requester = requester(request);
        afterDecision(request, organizer, () -> emailService.sendDeclined(requester.getEmail(),
                requester.getFirstName(), locale(requester), reason.trim()));
        log.info("PROFILE_MERGE_REQUEST_DECLINED id={} by={}", id, organizer);
        return request;
    }

    private void afterDecision(ProfileMergeRequest request, String organizer, Runnable mail) {
        try {
            mail.run();
        } catch (RuntimeException e) {
            log.warn("PROFILE_MERGE_REQUEST mail failed id={} cause={}", request.getId(), e.getClass().getSimpleName());
        }
        try {
            mailCenter.closeMergeRequest(request.getId(), organizer);
        } catch (RuntimeException e) {
            log.warn("PROFILE_MERGE_REQUEST conversation not closed id={} cause={}", request.getId(),
                    e.getClass().getSimpleName());
        }
    }

    private void decide(ProfileMergeRequest request, ProfileMergeRequest.Status status, String by, String reason) {
        request.setStatus(status);
        request.setDecidedBy(by);
        request.setDecidedAt(Instant.now());
        request.setReason(reason);
        requests.saveAndFlush(request);
    }

    private ProfileMergeRequest openRequest(UUID id) {
        ProfileMergeRequest request = load(id);
        if (request.getStatus() != ProfileMergeRequest.Status.OPEN) {
            throw new RequestConflictException("MERGE_REQUEST_DECIDED", "This request is already decided");
        }
        return request;
    }

    private ProfileMergeRequest load(UUID id) {
        return requests.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Merge request not found", true));
    }

    private User requester(ProfileMergeRequest request) {
        return userRepository.findByUsername(request.getRequesterUsername())
                .orElseThrow(() -> new UserNotFoundException("Requester not found", true));
    }

    private static String locale(User user) {
        if (user.getPreferences() != null && user.getPreferences().getLanguage() != null) {
            return user.getPreferences().getLanguage().toLowerCase(Locale.ROOT).startsWith("de") ? "de" : "en";
        }
        return "de";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 409 with an error code: MERGE_REQUEST_OPEN, MERGE_REQUEST_DECLINED or MERGE_REQUEST_DECIDED. */
    public static class RequestConflictException extends RuntimeException {
        private final String code;

        public RequestConflictException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
