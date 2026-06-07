package ch.batbern.events.service;

import ch.batbern.events.client.PartnerMeetingRsvpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InboundEmailRouter (Story 10.17 — AC3, AC4, AC5, AC6).
 */
@ExtendWith(MockitoExtension.class)
class InboundEmailRouterTest {

    @Mock
    private NewsletterSubscriberService newsletterSubscriberService;

    @Mock
    private RegistrationService registrationService;

    @Mock
    private InboundEmailConfirmationEmailService confirmationEmailService;

    @Mock
    private InboundEmailRateLimiter rateLimiter;

    @Mock
    private PartnerMeetingRsvpClient partnerMeetingRsvpClient;

    @InjectMocks
    private InboundEmailRouter router;

    @BeforeEach
    void allowAll() {
        // Default: allow all senders — individual tests override for rate-limit scenarios
        when(rateLimiter.isAllowed(any())).thenReturn(true);
    }

    // ─── Unsubscribe routing ────────────────────────────────────────────────────

    @Test
    void route_withUnsubscribeBody_callsUnsubscribeByEmail() {
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern newsletter", "unsubscribe");

        router.route(email);

        verify(newsletterSubscriberService).unsubscribeByEmail("user@example.com");
        verify(confirmationEmailService).sendUnsubscribeConfirmation("user@example.com", "en");
        verifyNoInteractions(registrationService);
    }

    @Test
    void route_withUppercaseUnsubscribeBody_isCaseInsensitive() {
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: anything", "UNSUBSCRIBE");

        router.route(email);

        verify(newsletterSubscriberService).unsubscribeByEmail("user@example.com");
        verify(confirmationEmailService).sendUnsubscribeConfirmation("user@example.com", "en");
    }

    @Test
    void route_withAbmeldenBody_callsUnsubscribeWithDeLocale() {
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern", "abmelden");

        router.route(email);

        verify(newsletterSubscriberService).unsubscribeByEmail("user@example.com");
        verify(confirmationEmailService).sendUnsubscribeConfirmation("user@example.com", "de");
    }

    @Test
    void route_withDesinscriptionBody_callsUnsubscribeWithFrLocale() {
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern", "désinscription");

        router.route(email);

        verify(newsletterSubscriberService).unsubscribeByEmail("user@example.com");
        verify(confirmationEmailService).sendUnsubscribeConfirmation("user@example.com", "fr");
    }

    // ─── Deregistration routing ─────────────────────────────────────────────────

    @Test
    void route_withCancelBodyAndEventCodeInSubject_callsCancelByEmail() {
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern42 Registration Confirmation", "cancel");

        router.route(email);

        verify(registrationService).cancelByEmail("user@example.com", "BATbern42");
        verify(confirmationEmailService).sendCancelConfirmation("user@example.com", "BATbern42", "en");
        verifyNoInteractions(newsletterSubscriberService);
    }

    @Test
    void route_withCancelBodyButNoEventCode_logsWarnAndDoesNotDeregister() {
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern newsletter", "cancel");

        router.route(email);

        verifyNoInteractions(registrationService, newsletterSubscriberService, confirmationEmailService);
    }

    @Test
    void route_withAbsagenBodyAndEventCode_callsDeregisterWithDeLocale() {
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern10 Anmeldebestätigung", "absagen");

        router.route(email);

        verify(registrationService).cancelByEmail("user@example.com", "BATbern10");
        verify(confirmationEmailService).sendCancelConfirmation("user@example.com", "BATbern10", "de");
    }

    // ─── Silent discard ──────────────────────────────────────────────────────────

    @Test
    void route_withUnrecognizedBody_doesNothing() {
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern42", "hello world");

        router.route(email);

        verifyNoInteractions(newsletterSubscriberService,
                registrationService, confirmationEmailService);
    }

    // ─── Acceptance routing ──────────────────────────────────────────────────────

    @Test
    void route_withAcceptBodyAndEventCodeInSubject_callsConfirmByEmail() {
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern42 Registration Confirmation", "accept");

        router.route(email);

        verify(registrationService).confirmByEmail("user@example.com", "BATbern42");
        verifyNoInteractions(newsletterSubscriberService);
    }

    @Test
    void route_withUppercaseAcceptBody_isCaseInsensitive() {
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern10 Einladung", "ACCEPT");

        router.route(email);

        verify(registrationService).confirmByEmail("user@example.com", "BATbern10");
    }

    @Test
    void route_withBestaetignenBody_callsConfirmByEmail() {
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern5 Anmeldebestätigung", "bestätigen");

        router.route(email);

        verify(registrationService).confirmByEmail("user@example.com", "BATbern5");
    }

    @Test
    void route_withConfirmerBody_callsConfirmByEmail() {
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern99 Inscription", "confirmer");

        router.route(email);

        verify(registrationService).confirmByEmail("user@example.com", "BATbern99");
    }

    @Test
    void route_withBevestigenBody_callsConfirmByEmail() {
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern7", "bevestigen");

        router.route(email);

        verify(registrationService).confirmByEmail("user@example.com", "BATbern7");
    }

    @Test
    void route_withAcceptBodyButNoEventCodeInSubject_discardsSilently() {
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern newsletter", "accept");

        router.route(email);

        verifyNoInteractions(newsletterSubscriberService,
                registrationService, confirmationEmailService);
    }

    // ─── Rate limiting ───────────────────────────────────────────────────────────

    @Test
    void route_whenRateLimitExceeded_doesNothing() {
        when(rateLimiter.isAllowed("spammer@example.com")).thenReturn(false);
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "spammer@example.com", "anything", "unsubscribe");

        router.route(email);

        verifyNoInteractions(newsletterSubscriberService,
                registrationService, confirmationEmailService);
    }

    // ─── T13.1: routeIcsReply with valid UID → client called ─────────────────

    @Test
    void routeIcsReply_withValidUid_callsRecordRsvp() {
        UUID meetingId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String uid = meetingId + "@batbern.ch";
        InboundEmailRouter.IcsReply reply = new InboundEmailRouter.IcsReply(
                uid, "alice@partner.com", "ACCEPTED");

        router.routeIcsReply(reply);

        verify(partnerMeetingRsvpClient).recordRsvp(meetingId, "alice@partner.com", "ACCEPTED");
    }

    // ─── T13.2: UID does not end with @batbern.ch → WARN, client NOT called ──

    @Test
    void routeIcsReply_withWrongUidDomain_discardsGracefully() {
        InboundEmailRouter.IcsReply reply = new InboundEmailRouter.IcsReply(
                "11111111-1111-1111-1111-111111111111@other-domain.com",
                "alice@partner.com", "ACCEPTED");

        router.routeIcsReply(reply);

        verifyNoInteractions(partnerMeetingRsvpClient);
    }

    // ─── T13.3: UID prefix is not a valid UUID → WARN, client NOT called ──────

    @Test
    void routeIcsReply_withMalformedUuidInUid_discardsGracefully() {
        InboundEmailRouter.IcsReply reply = new InboundEmailRouter.IcsReply(
                "not-a-uuid@batbern.ch", "alice@partner.com", "ACCEPTED");

        router.routeIcsReply(reply);

        verifyNoInteractions(partnerMeetingRsvpClient);
    }

    // ─── T13.4: rate limiter blocks → client NOT called ──────────────────────

    @Test
    void routeIcsReply_whenRateLimitExceeded_discardsGracefully() {
        when(rateLimiter.isAllowed("spammer@partner.com")).thenReturn(false);
        InboundEmailRouter.IcsReply reply = new InboundEmailRouter.IcsReply(
                "11111111-1111-1111-1111-111111111111@batbern.ch",
                "spammer@partner.com", "ACCEPTED");

        router.routeIcsReply(reply);

        verifyNoInteractions(partnerMeetingRsvpClient);
    }
}
