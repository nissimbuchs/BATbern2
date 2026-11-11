package ch.batbern.companyuser.service;

import ch.batbern.companyuser.client.EventManagementUserMergeClient;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.exception.AdditionalEmailNotFoundException;
import ch.batbern.companyuser.exception.AdditionalEmailNotVerifiedException;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.repository.UserAdditionalEmailRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.security.SecurityContextHelper;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * #1244 step 2 — "Als Hauptadresse verwenden": a confirmed additional address becomes the primary
 * (login) address, for example the private address after a job change.
 *
 * <p>Order: Cognito moves the login first (it refuses an address another login holds, which is the
 * case for the account merge); then the profile swaps the two addresses, the old primary stays as a
 * confirmed additional address (a new row, so the ids of the addresses change). If the profile
 * step fails, the login is moved back. Last, event-management moves upcoming registrations made
 * under the old address; that step is best effort, because those registrations are still found
 * through the old address.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrimaryEmailService {

    private final UserRepository userRepository;
    private final UserAdditionalEmailRepository additionalEmailRepository;
    private final CognitoIntegrationService cognitoService;
    private final EventManagementUserMergeClient eventManagement;
    private final SecurityContextHelper securityContext;

    @Transactional
    public void makePrimary(UUID emailId) {
        String username = securityContext.getCurrentUsername();
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new UserNotFoundException("Current user profile not found", true));
        // Story 4.10d: loaded by id, scoped to the caller; another user's or an unknown id is 404
        UserAdditionalEmail row = additionalEmailRepository.findByIdAndUser(emailId, user)
                .orElseThrow(() -> new AdditionalEmailNotFoundException(emailId));
        String newEmail = row.getEmail().toLowerCase(Locale.ROOT);
        if (row.getVerifiedAt() == null) {
            throw new AdditionalEmailNotVerifiedException(newEmail);
        }
        String oldEmail = user.getEmail();
        boolean hasLogin = user.getCognitoUserId() != null;

        if (hasLogin) {
            cognitoService.changeLoginEmail(oldEmail, newEmail);
        }
        try {
            swap(user, row, oldEmail, newEmail);
        } catch (RuntimeException e) {
            if (hasLogin) {
                log.error("Primary address swap failed for {}; moving the login back", username);
                cognitoService.changeLoginEmail(newEmail, oldEmail);
            }
            throw e;
        }

        try {
            eventManagement.primaryEmailChanged(username, oldEmail, newEmail);
        } catch (RuntimeException e) {
            log.warn("event-management not told about the new primary address of {}: {}", username,
                    e.getMessage());
        }
        log.info("PRIMARY_EMAIL_CHANGED user={} from={} to={}", username, LoggingUtils.maskEmail(oldEmail),
                LoggingUtils.maskEmail(newEmail));
    }

    /**
     * Hibernate flushes inserts before deletes; the explicit flushes keep the global address
     * uniqueness (primary and additional) satisfied at every step.
     */
    private void swap(User user, UserAdditionalEmail row, String oldEmail, String newEmail) {
        user.removeAdditionalEmail(row);
        additionalEmailRepository.delete(row);
        additionalEmailRepository.flush();

        user.setEmail(newEmail);
        userRepository.flush();

        user.addAdditionalEmail(UserAdditionalEmail.builder()
                .email(oldEmail.toLowerCase(Locale.ROOT))
                .verifiedAt(Instant.now())
                .createdAt(Instant.now())
                .build());
        userRepository.flush();
    }
}
