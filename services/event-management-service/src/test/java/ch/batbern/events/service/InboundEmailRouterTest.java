package ch.batbern.events.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InboundEmailRouter (Story 10.17 — AC3, AC4, AC5, AC6).
 *
 * TDD: RED phase — tests define routing behaviour before implementation.
 */
@ExtendWith(MockitoExtension.class)
class InboundEmailRouterTest {

    @Mock
    private NewsletterSubscriberService newsletterSubscriberService;

    @Mock
    private DeregistrationService deregistrationService;

    @Mock
    private InboundEmailConfirmationEmailService confirmationEmailService;

    @Mock
    private InboundEmailRateLimiter rateLimiter;

    @InjectMocks
    private InboundEmailRouter router;

    // ─── Unsubscribe routing ────────────────────────────────────────────────────

    @Test
    void route_withUnsubscribeBody_callsUnsubscribeByEmail() {
        when(rateLimiter.isAllowed("user@example.com")).thenReturn(true);
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern newsletter", "unsubscribe");

        router.route(email);

        verify(newsletterSubscriberService).unsubscribeByEmail("user@example.com");
        verify(confirmationEmailService).sendUnsubscribeConfirmation("user@example.com");
        verifyNoInteractions(deregistrationService);
    }

    @Test
    void route_withUppercaseUnsubscribeBody_isCaseInsensitive() {
        when(rateLimiter.isAllowed("user@example.com")).thenReturn(true);
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: anything", "UNSUBSCRIBE");

        router.route(email);

        verify(newsletterSubscriberService).unsubscribeByEmail("user@example.com");
    }

    @Test
    void route_withAbmeldenBody_callsUnsubscribe() {
        when(rateLimiter.isAllowed("user@example.com")).thenReturn(true);
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern", "abmelden");

        router.route(email);

        verify(newsletterSubscriberService).unsubscribeByEmail("user@example.com");
    }

    @Test
    void route_withDesinscriptionBody_callsUnsubscribe() {
        when(rateLimiter.isAllowed("user@example.com")).thenReturn(true);
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern", "désinscription");

        router.route(email);

        verify(newsletterSubscriberService).unsubscribeByEmail("user@example.com");
    }

    // ─── Deregistration routing ─────────────────────────────────────────────────

    @Test
    void route_withCancelBodyAndEventCodeInSubject_callsDeregisterByEmail() {
        when(rateLimiter.isAllowed("user@example.com")).thenReturn(true);
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern42 Registration Confirmation", "cancel");

        router.route(email);

        verify(deregistrationService).deregisterByEmail("user@example.com", "BATbern42");
        verify(confirmationEmailService).sendCancelConfirmation("user@example.com", "BATbern42");
        verifyNoInteractions(newsletterSubscriberService);
    }

    @Test
    void route_withAbsagenBodyAndEventCode_callsDeregister() {
        when(rateLimiter.isAllowed("user@example.com")).thenReturn(true);
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern10 Anmeldebestätigung", "absagen");

        router.route(email);

        verify(deregistrationService).deregisterByEmail("user@example.com", "BATbern10");
    }

    // ─── Silent discard ──────────────────────────────────────────────────────────

    @Test
    void route_withUnrecognizedBody_doesNothing() {
        when(rateLimiter.isAllowed("user@example.com")).thenReturn(true);
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern42", "hello world");

        router.route(email);

        verifyNoInteractions(newsletterSubscriberService, deregistrationService, confirmationEmailService);
    }

    // ─── Acceptance routing ──────────────────────────────────────────────────────

    @Test
    void route_withAcceptBodyAndEventCodeInSubject_callsSendAcceptConfirmation() {
        when(rateLimiter.isAllowed("user@example.com")).thenReturn(true);
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern42 Registration Confirmation", "accept");

        router.route(email);

        verify(confirmationEmailService).sendAcceptConfirmation("user@example.com", "BATbern42");
        verifyNoInteractions(newsletterSubscriberService, deregistrationService);
    }

    @Test
    void route_withUppercaseAcceptBody_isCaseInsensitive() {
        when(rateLimiter.isAllowed("user@example.com")).thenReturn(true);
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern10 Einladung", "ACCEPT");

        router.route(email);

        verify(confirmationEmailService).sendAcceptConfirmation("user@example.com", "BATbern10");
    }

    @Test
    void route_withBestaetignenBody_callsAcceptConfirmation() {
        when(rateLimiter.isAllowed("user@example.com")).thenReturn(true);
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern5 Anmeldebestätigung", "bestätigen");

        router.route(email);

        verify(confirmationEmailService).sendAcceptConfirmation("user@example.com", "BATbern5");
    }

    @Test
    void route_withConfirmerBody_callsAcceptConfirmation() {
        when(rateLimiter.isAllowed("user@example.com")).thenReturn(true);
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern99 Inscription", "confirmer");

        router.route(email);

        verify(confirmationEmailService).sendAcceptConfirmation("user@example.com", "BATbern99");
    }

    @Test
    void route_withBevestigenBody_callsAcceptConfirmation() {
        when(rateLimiter.isAllowed("user@example.com")).thenReturn(true);
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern7", "bevestigen");

        router.route(email);

        verify(confirmationEmailService).sendAcceptConfirmation("user@example.com", "BATbern7");
    }

    @Test
    void route_withAcceptBodyButNoEventCodeInSubject_discardsSilently() {
        when(rateLimiter.isAllowed("user@example.com")).thenReturn(true);
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "user@example.com", "Re: BATbern newsletter", "accept");

        router.route(email);

        verifyNoInteractions(newsletterSubscriberService, deregistrationService, confirmationEmailService);
    }

    // ─── Rate limiting ───────────────────────────────────────────────────────────

    @Test
    void route_whenRateLimitExceeded_doesNothing() {
        when(rateLimiter.isAllowed("spammer@example.com")).thenReturn(false);
        InboundEmailRouter.ParsedEmail email = new InboundEmailRouter.ParsedEmail(
                "spammer@example.com", "anything", "unsubscribe");

        router.route(email);

        verifyNoInteractions(newsletterSubscriberService, deregistrationService, confirmationEmailService);
    }
}
