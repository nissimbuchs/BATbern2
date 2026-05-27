package ch.batbern.companyuser.service;

import ch.batbern.shared.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.LimitExceededException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.cognitoidentityprovider.paginators.ListUsersIterable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Daily job that re-sends the Cognito sign-up confirmation code to accounts that registered but
 * never confirmed, after a grace window (default 2 days). The fresh code re-triggers the
 * CustomEmailSender Lambda ({@code CustomEmailSender_SignUp}), which delivers a new branded
 * verification email.
 *
 * <p>Why this exists: Cognito's sign-up confirmation code is fixed at 24h and cannot be lengthened
 * (only our own registration-confirmation token is configurable). Accounts whose first code expired
 * hit a recovery dead-end — re-register reports "email already exists" and password-reset reports
 * "user not registered" — leaving them permanently locked out without manual support. This nudge
 * hands them a fresh, valid code automatically.
 *
 * <p>Stateless anti-spam: Cognito UNCONFIRMED users have no {@code user_profiles} row to track resend
 * counts (the PostConfirmation trigger only creates one on confirmation), so this job only nudges
 * accounts whose signup age is within {@code [after-hours, after-hours + window-hours)} — a bounded
 * window so each account is nudged at most a few times across daily runs, then left alone.
 *
 * <p>Resend uses the email alias ({@code ResendConfirmationCode} resolves it). Confirmed accounts and
 * accounts outside the window are skipped. ShedLock ensures a single ECS instance runs it.
 */
@Component
@Slf4j
public class CognitoConfirmationResendJob {

    private static final int PAGE_SIZE = 60; // Cognito max page size

    private final CognitoIdentityProviderClient cognitoClient;
    private final String userPoolId;
    private final String clientId;
    private final boolean enabled;
    private final long afterHours;
    private final long windowHours;

    public CognitoConfirmationResendJob(
            CognitoIdentityProviderClient cognitoClient,
            @Value("${aws.cognito.user-pool-id:}") String userPoolId,
            @Value("${aws.cognito.client-id:}") String clientId,
            @Value("${cognito.resend.enabled:true}") boolean enabled,
            @Value("${cognito.resend.after-hours:48}") long afterHours,
            @Value("${cognito.resend.window-hours:48}") long windowHours) {
        this.cognitoClient = cognitoClient;
        this.userPoolId = userPoolId;
        this.clientId = clientId;
        this.enabled = enabled;
        this.afterHours = afterHours;
        this.windowHours = windowHours;
    }

    @Scheduled(cron = "${cognito.resend.cron:0 15 8 * * *}")
    @SchedulerLock(
            name = "resendUnconfirmedCognitoSignups",
            lockAtLeastFor = "PT1M",
            lockAtMostFor = "PT15M"
    )
    public void resendUnconfirmedSignups() {
        if (!enabled) {
            log.debug("Cognito confirmation resend disabled; skipping scheduled execution");
            return;
        }
        if (isBlank(userPoolId) || isBlank(clientId)) {
            log.warn("Cognito confirmation resend skipped — aws.cognito.user-pool-id / client-id not configured");
            return;
        }

        log.info("Starting scheduled Cognito confirmation resend for unconfirmed sign-ups");

        Instant now = Instant.now();
        // Eligible signup age is in [afterHours, afterHours + windowHours):
        //   newest eligible signup time = now - afterHours        (older than the grace window)
        //   oldest eligible signup time = now - (afterHours+window) (still within the bounded window)
        Instant newestEligible = now.minus(afterHours, ChronoUnit.HOURS);
        Instant oldestEligible = now.minus(afterHours + windowHours, ChronoUnit.HOURS);

        int resent = 0;
        int eligibleScanned = 0;
        try {
            ListUsersRequest request = ListUsersRequest.builder()
                    .userPoolId(userPoolId)
                    .limit(PAGE_SIZE)
                    .build();
            ListUsersIterable paginator = cognitoClient.listUsersPaginator(request);

            for (ListUsersResponse page : paginator) {
                for (UserType user : page.users()) {
                    if (user.userStatus() != UserStatusType.UNCONFIRMED) {
                        continue;
                    }
                    Instant created = user.userCreateDate();
                    if (created == null
                            || created.isAfter(newestEligible)   // too new — still inside grace window
                            || created.isBefore(oldestEligible)) { // too old — past the bounded window
                        continue;
                    }
                    eligibleScanned++;

                    String email = extractEmail(user);
                    if (isBlank(email)) {
                        log.warn("Skipping resend for unconfirmed user {} — no email attribute", user.username());
                        continue;
                    }
                    if (resendFor(email)) {
                        resent++;
                    }
                }
            }
        } catch (CognitoIdentityProviderException e) {
            log.error("Cognito confirmation resend scan failed: {}", e.getMessage());
        }

        log.info("Cognito confirmation resend complete: {} resent of {} unconfirmed-in-window",
                resent, eligibleScanned);
    }

    private boolean resendFor(String email) {
        try {
            cognitoClient.resendConfirmationCode(ResendConfirmationCodeRequest.builder()
                    .clientId(clientId)
                    .username(email)
                    .build());
            log.info("Resent Cognito confirmation code to {}", LoggingUtils.maskEmail(email));
            return true;
        } catch (InvalidParameterException e) {
            // Most commonly: the account was confirmed between scan and resend — benign, just skip.
            log.debug("Skipping resend for {} — {}", LoggingUtils.maskEmail(email),
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : e.getMessage());
            return false;
        } catch (LimitExceededException e) {
            log.warn("Rate limited resending confirmation to {} — will retry next run",
                    LoggingUtils.maskEmail(email));
            return false;
        } catch (CognitoIdentityProviderException e) {
            log.error("Failed to resend confirmation to {}: {}", LoggingUtils.maskEmail(email), e.getMessage());
            return false;
        }
    }

    private String extractEmail(UserType user) {
        return user.attributes().stream()
                .filter(attr -> "email".equals(attr.name()))
                .map(AttributeType::value)
                .findFirst()
                .orElse(null);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
