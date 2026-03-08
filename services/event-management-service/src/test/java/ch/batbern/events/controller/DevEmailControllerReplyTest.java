package ch.batbern.events.controller;

import ch.batbern.events.service.InboundEmailRouter;
import ch.batbern.shared.service.CapturedEmail;
import ch.batbern.shared.service.LocalEmailCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DevEmailController#simulateReply (Story 10.17 — AC11).
 */
@ExtendWith(MockitoExtension.class)
class DevEmailControllerReplyTest {

    @Mock
    private LocalEmailCapture localEmailCapture;

    @Mock
    private InboundEmailRouter inboundEmailRouter;

    @InjectMocks
    private DevEmailController controller;

    @Test
    void simulateReply_withKnownId_routesViaInboundEmailRouter() {
        UUID id = UUID.randomUUID();
        CapturedEmail captured = new CapturedEmail(
            id,
            "attendee@example.com",
            "BATbern42 Registration Confirmation",
            "<p>You are registered</p>",
            "noreply@batbern.ch",
            "BATbern Platform",
            Instant.now(),
            List.of()
        );
        when(localEmailCapture.getAll()).thenReturn(List.of(captured));

        ResponseEntity<String> response = controller.simulateReply(
            id, new DevEmailController.ReplyRequest("cancel")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("cancel");

        ArgumentCaptor<InboundEmailRouter.ParsedEmail> captor =
            ArgumentCaptor.forClass(InboundEmailRouter.ParsedEmail.class);
        verify(inboundEmailRouter).route(captor.capture());

        InboundEmailRouter.ParsedEmail routed = captor.getValue();
        assertThat(routed.senderEmail()).isEqualTo("attendee@example.com");
        assertThat(routed.subject()).isEqualTo("Re: BATbern42 Registration Confirmation");
        assertThat(routed.bodyFirstLine()).isEqualTo("cancel");
    }

    @Test
    void simulateReply_withUnknownId_returns404() {
        when(localEmailCapture.getAll()).thenReturn(List.of());

        ResponseEntity<String> response = controller.simulateReply(
            UUID.randomUUID(), new DevEmailController.ReplyRequest("unsubscribe")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(inboundEmailRouter);
    }
}
