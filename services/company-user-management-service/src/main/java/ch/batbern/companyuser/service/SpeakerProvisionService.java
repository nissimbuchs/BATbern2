package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.dto.SpeakerProvisionRequest;
import ch.batbern.companyuser.dto.SpeakerProvisionResponse;
import ch.batbern.companyuser.dto.SpeakerProvisionResponse.AccountAction;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;

import java.security.SecureRandom;
import java.util.Optional;

/**
 * Story 9.2: Orchestrates speaker account provisioning in Cognito and local DB.
 *
 * Flow:
 * 1. Check if email exists in Cognito (findUserByEmail)
 * 2a. NEW: generate temp password → createCognitoSpeaker → addRole(SPEAKER) to local DB
 * 2b. EXTENDED: addRoleToCognitoUser → addRole(SPEAKER) to local DB
 *
 * Idempotent: safe to call twice — duplicate Cognito or DB role is a no-op.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerProvisionService {

    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()_+-=[]{}|;:,.<>?";
    private static final int PASSWORD_LENGTH = 20;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CognitoIntegrationService cognitoService;
    private final RoleService roleService;
    private final UserRepository userRepository;

    /**
     * Provision a speaker account: creates or extends Cognito + local DB roles.
     *
     * @param request Email, firstName, lastName of the speaker
     * @return Provision result with action (NEW|EXTENDED) and temporary password (NEW only)
     * @throws UserNotFoundException if no local DB user matches the email
     */
    @Transactional
    public SpeakerProvisionResponse provision(SpeakerProvisionRequest request) {
        String email = request.getEmail();
        String name = request.getFirstName() + " " + request.getLastName();
        log.info("Provisioning speaker account for email: {}", maskEmail(email));

        // 1. Check Cognito for existing user
        Optional<AdminGetUserResponse> existingUser = cognitoService.findUserByEmail(email);

        String cognitoUserId;
        String temporaryPassword = null;
        AccountAction action;
        String username;

        if (existingUser.isEmpty()) {
            // NEW account: generate password and create Cognito user
            temporaryPassword = generateSecureTemporaryPassword();
            cognitoUserId = cognitoService.createCognitoSpeaker(email, name, temporaryPassword);
            action = AccountAction.NEW;
            log.info("Created NEW Cognito speaker account for: {}", maskEmail(email));

            // Task 4.4: Compensating transaction — if local DB update fails, rollback Cognito
            try {
                username = findUsernameByEmail(email);
                roleService.addRole(username, Role.SPEAKER);
            } catch (Exception e) {
                log.error("Local DB update failed after NEW Cognito account creation for: {}. "
                        + "Rolling back.", maskEmail(email), e);
                cognitoService.deleteCognitoAccount(cognitoUserId);
                throw new RuntimeException("Speaker provisioning rolled back for: " + maskEmail(email), e);
            }
        } else {
            // EXTENDED: add SPEAKER role to existing Cognito user
            cognitoUserId = existingUser.get().username();
            cognitoService.addRoleToCognitoUser(email, Role.SPEAKER);
            action = AccountAction.EXTENDED;
            log.info("Extended existing Cognito account with SPEAKER role for: {}", maskEmail(email));

            // For EXTENDED accounts, Cognito role is already set — no clean rollback possible,
            // but addRole is idempotent so a retry will self-heal.
            username = findUsernameByEmail(email);
            roleService.addRole(username, Role.SPEAKER);
        }

        log.info("Speaker provisioning complete for {}: action={}", maskEmail(email), action);

        return SpeakerProvisionResponse.builder()
                .username(username)
                .cognitoUserId(cognitoUserId)
                .action(action)
                .temporaryPassword(temporaryPassword) // null for EXTENDED
                .build();
    }

    /**
     * Generate a cryptographically secure temporary password meeting Cognito policy:
     * - 20 characters
     * - At least 1 uppercase, 1 lowercase, 1 digit, 1 special character
     */
    String generateSecureTemporaryPassword() {
        SecureRandom random = SECURE_RANDOM;
        String allChars = UPPERCASE + LOWERCASE + DIGITS + SPECIAL;

        char[] password = new char[PASSWORD_LENGTH];

        // Guarantee at least one character from each required class
        password[0] = UPPERCASE.charAt(random.nextInt(UPPERCASE.length()));
        password[1] = LOWERCASE.charAt(random.nextInt(LOWERCASE.length()));
        password[2] = DIGITS.charAt(random.nextInt(DIGITS.length()));
        password[3] = SPECIAL.charAt(random.nextInt(SPECIAL.length()));

        // Fill remaining positions with random chars from all classes
        for (int i = 4; i < PASSWORD_LENGTH; i++) {
            password[i] = allChars.charAt(random.nextInt(allChars.length()));
        }

        // Shuffle to avoid predictable positions for required chars
        for (int i = PASSWORD_LENGTH - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = password[i];
            password[i] = password[j];
            password[j] = temp;
        }

        return new String(password);
    }

    /**
     * Find local DB username by email address.
     * Throws UserNotFoundException if no local user matches the email.
     */
    private String findUsernameByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(user -> user.getUsername())
                .orElseThrow(() -> new UserNotFoundException(
                        "No local user found for email: " + maskEmail(email)));
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIdx = email.indexOf('@');
        return email.substring(0, Math.min(3, atIdx)) + "***" + email.substring(atIdx);
    }
}
