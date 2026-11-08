package ch.batbern.events.mailcenter.inbound;

import ch.batbern.events.forwarding.EmailForwardingService;
import ch.batbern.events.forwarding.InboundEmailRouter;
import ch.batbern.events.mailcenter.ConversationNotices;
import ch.batbern.events.mailcenter.MailCenterSwitch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Story 16.6 AC7–AC9 on the replies path (group {@code replies}). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Story 16.6: replies processing with the notify switch")
class InboundReplyProcessorSwitchTest {

    private static final String KEY = "emails/k1";
    private static final byte[] RAW = ("From: A <success+a@simulator.amazonses.com>\r\nTo: replies@batbern.ch\r\n"
            + "Subject: Frage\r\nMessage-ID: <m1@x>\r\nContent-Type: text/plain\r\n\r\nWie komme ich hin?\r\n")
            .getBytes(StandardCharsets.UTF_8);

    @Mock
    private InboundEmailRouter router;
    @Mock
    private EmailForwardingService forwardingService;
    @Mock
    private InboundMailCenter mailCenter;
    @Mock
    private MailCenterSwitch mailSwitch;
    @Mock
    private ConversationNotices notices;

    private InboundReplyProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new InboundReplyProcessor(router, forwardingService, mailCenter, mailSwitch, notices);
        when(router.route(any())).thenReturn(InboundEmailRouter.RouteResult.UNRECOGNIZED);
    }

    @Test
    @DisplayName("off: forward, then store best effort")
    void off() {
        when(mailSwitch.isOn(MailCenterSwitch.Group.REPLIES)).thenReturn(false);
        processor.process(KEY, RAW);
        InOrder order = inOrder(forwardingService, mailCenter);
        order.verify(forwardingService).forwardUnrecognizedReply(KEY, RAW);
        order.verify(mailCenter).ingestReply(eq(KEY), eq(RAW), any());
    }

    @Test
    @DisplayName("on and stored: store first, then notify the organizers")
    void onStored() {
        when(mailSwitch.isOn(MailCenterSwitch.Group.REPLIES)).thenReturn(true);
        when(mailCenter.ingestReplyStrict(eq(KEY), eq(RAW), any()))
                .thenReturn(Optional.of(new InboundMailCenter.Stored("conv-2", null, "A", "Frage")));
        processor.process(KEY, RAW);
        InOrder order = inOrder(mailCenter, forwardingService);
        order.verify(mailCenter).ingestReplyStrict(eq(KEY), eq(RAW), any());
        order.verify(forwardingService).forwardUnrecognizedReply(eq(KEY), eq(RAW), any());
        verify(forwardingService, never()).forwardUnrecognizedReply(KEY, RAW);
    }

    @Test
    @DisplayName("on, not stored: forwarded as today")
    void onNotStored() {
        when(mailSwitch.isOn(MailCenterSwitch.Group.REPLIES)).thenReturn(true);
        when(mailCenter.ingestReplyStrict(eq(KEY), eq(RAW), any())).thenReturn(Optional.empty());
        processor.process(KEY, RAW);
        verify(forwardingService).forwardUnrecognizedReply(KEY, RAW);
    }

    @Test
    @DisplayName("on, storage fails: rethrown, nothing sent")
    void onFailure() {
        when(mailSwitch.isOn(MailCenterSwitch.Group.REPLIES)).thenReturn(true);
        when(mailCenter.ingestReplyStrict(eq(KEY), eq(RAW), any())).thenThrow(new IllegalStateException("db"));
        assertThatThrownBy(() -> processor.process(KEY, RAW)).isInstanceOf(IllegalStateException.class);
        verify(forwardingService, never()).forwardUnrecognizedReply(anyString(), any());
        verify(forwardingService, never()).forwardUnrecognizedReply(anyString(), any(), any());
    }
}
