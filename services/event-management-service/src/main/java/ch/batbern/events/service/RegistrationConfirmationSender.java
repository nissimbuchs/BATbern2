package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.AfterCommit;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.mailcenter.render.MailLocales;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Sends the "please confirm your registration" mail of a pending registration: a fresh confirmation
 * token (Story 4.1.5c), the deregistration link (Story 10.12), to the attendee as CUMS knows them, in
 * their preferred language. The one sender for the anonymous registration, the organizer's resend
 * and the daily resend job (#1315; there were three copies, all in German).
 *
 * <p>A bean of its own: {@link RegistrationEmailService#sendRegistrationConfirmation} is
 * {@code @Async}, and a call from inside that class would bypass the proxy.
 */
@Service
@Slf4j
public class RegistrationConfirmationSender {

    private final UserApiClient userApiClient;
    private final ConfirmationTokenService confirmationTokenService;
    private final RegistrationEmailService registrationEmailService;
    private final String appBaseUrl;

    public RegistrationConfirmationSender(
            UserApiClient userApiClient,
            ConfirmationTokenService confirmationTokenService,
            RegistrationEmailService registrationEmailService,
            @Value("${app.base-url:https://batbern.ch}") String appBaseUrl) {
        this.userApiClient = userApiClient;
        this.confirmationTokenService = confirmationTokenService;
        this.registrationEmailService = registrationEmailService;
        this.appBaseUrl = appBaseUrl;
    }

    /**
     * Queues the confirmation mail, after the commit when called inside a transaction (none for a
     * registration that rolls back). German when the attendee's language is unset or German, English
     * otherwise (the rule of {@code RegistrationService#notifyRegistrationConfirmed}).
     *
     * @return the attendee the mail goes to, or empty when CUMS has no address for them (nothing sent)
     * @throws ch.batbern.events.exception.UserNotFoundException CUMS does not know the attendee
     */
    public Optional<UserResponse> send(Registration registration, Event event) {
        String username = registration.getAttendeeUsername();
        UserResponse attendee = userApiClient.getUserByUsername(username);
        if (attendee == null || attendee.getEmail() == null || attendee.getEmail().isBlank()) {
            log.warn("No confirmation mail for registration {}: attendee {} has no e-mail address",
                    registration.getRegistrationCode(), username);
            return Optional.empty();
        }

        String confirmationToken = confirmationTokenService.generateConfirmationToken(
                registration.getId(), event.getEventCode());
        String deregistrationUrl = registration.getDeregistrationToken() != null
                ? appBaseUrl + "/deregister?token=" + registration.getDeregistrationToken()
                : null;
        String language = userApiClient.getPreferredLanguage(username);
        Locale locale = MailLocales.fromPreference(language);

        AfterCommit.run(() -> registrationEmailService.sendRegistrationConfirmation(
                registration, attendee, event, confirmationToken, deregistrationUrl, locale));
        log.info("Confirmation email queued for registration {} ({})", registration.getRegistrationCode(),
                locale.getLanguage());
        return Optional.of(attendee);
    }
}
