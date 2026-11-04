package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.exception.VerificationTokenExpiredException;
import ch.batbern.companyuser.exception.VerificationTokenInvalidException;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.security.SecurityContextHelper;
import ch.batbern.shared.utils.LoggingUtils;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * #1183 D: a person with a login claims an earlier profile under their name (an imported profile or a
 * guest registration without login). Proof is the mailbox: the link goes to the earlier profile's
 * address, and it only works under the claimer's login. Confirming runs the same merge as an organizer
 * ({@link UserMergeService}), recorded as SELF_CLAIM.
 *
 * <p>Candidates: profiles without a login, active, with the same first and last name (case-insensitive,
 * trimmed). Only a masked address and the company are shown, so a namesake's full address never leaks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileClaimService {

    public static final String TRIGGER = "SELF_CLAIM";

    private final UserRepository userRepository;
    private final SecurityContextHelper securityContext;
    private final ProfileClaimTokenService tokenService;
    private final ProfileClaimEmailService emailService;
    private final UserMergeService mergeService;

    public record Candidate(UUID id, String maskedEmail, String companyName) {
    }

    @Transactional(readOnly = true)
    public List<Candidate> candidates() {
        User caller = currentUser();
        return candidatesOf(caller).stream()
                .map(u -> new Candidate(u.getId(), LoggingUtils.maskEmail(u.getEmail()), u.getCompanyId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public void requestClaim(UUID candidateId) {
        User caller = currentUser();
        User candidate = candidatesOf(caller).stream().filter(u -> u.getId().equals(candidateId)).findFirst()
                .orElseThrow(() -> new UserNotFoundException("No earlier profile to claim", true));
        String token = tokenService.generateToken(caller.getId(), candidate.getId(), candidate.getEmail());
        emailService.sendClaimEmail(candidate.getEmail(), token, locale(caller),
                caller.getFirstName() + " " + caller.getLastName());
        log.info("PROFILE_CLAIM_REQUESTED claimer={} candidate={}", caller.getUsername(), candidate.getUsername());
    }

    /** @return true if merged now, false if the earlier profile was already merged (link used twice) */
    @Transactional
    public boolean confirm(String token) {
        ProfileClaimTokenService.Claim claim = parse(token);
        User caller = currentUser();
        if (!caller.getId().equals(claim.claimerId())) {
            throw new ClaimOtherAccountException();
        }
        User candidate = userRepository.findById(claim.candidateId()).orElse(null);
        if (candidate == null) {
            return false;
        }
        if (candidate.getEmail() == null || !candidate.getEmail().equalsIgnoreCase(claim.candidateEmail())) {
            // the earlier profile's address changed since the link was sent: the mailbox proof no longer holds
            throw new VerificationTokenInvalidException();
        }
        mergeService.merge(caller.getUsername(), candidate.getUsername(), caller.getUsername(), TRIGGER);
        return true;
    }

    private List<User> candidatesOf(User caller) {
        if (caller.getFirstName() == null || caller.getLastName() == null
                || caller.getFirstName().isBlank() || caller.getLastName().isBlank()) {
            return List.of();
        }
        return userRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCase(
                        caller.getFirstName().trim(), caller.getLastName().trim()).stream()
                .filter(u -> !u.getId().equals(caller.getId()))
                .filter(u -> u.getCognitoUserId() == null)
                .filter(User::isActive)
                .filter(u -> u.getEmail() != null)
                .toList();
    }

    private ProfileClaimTokenService.Claim parse(String token) {
        if (token == null || token.isBlank()) {
            throw new VerificationTokenInvalidException();
        }
        try {
            return tokenService.validate(token);
        } catch (ExpiredJwtException e) {
            throw new VerificationTokenExpiredException();
        } catch (RuntimeException e) {
            throw new VerificationTokenInvalidException();
        }
    }

    private User currentUser() {
        String username = securityContext.getCurrentUsername();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("Current user profile not found", true));
    }

    private static String locale(User user) {
        if (user.getPreferences() != null && user.getPreferences().getLanguage() != null) {
            return user.getPreferences().getLanguage().toLowerCase(Locale.ROOT).startsWith("de") ? "de" : "en";
        }
        return "de";
    }

    /** 403 CLAIM_OTHER_ACCOUNT: the link was requested under another login. */
    public static class ClaimOtherAccountException extends RuntimeException {
        public ClaimOtherAccountException() {
            super("This link belongs to another account");
        }
    }
}
