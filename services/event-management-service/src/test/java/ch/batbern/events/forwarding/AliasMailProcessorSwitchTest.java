package ch.batbern.events.forwarding;

import ch.batbern.events.mailcenter.ConversationNotices;
import ch.batbern.events.mailcenter.MailCenterSwitch;
import ch.batbern.events.mailcenter.inbound.InboundMailCenter;
import ch.batbern.events.service.InboundReplyProcessor;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Story 16.6 AC7–AC9 on the alias path: with a group switched on, the mail is stored first and the
 * forward becomes a notification; mail that was not stored is forwarded as today; a storage
 * failure is rethrown before anything is sent. Organizer sends are always forwarded.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Story 16.6: alias processing with the notify switch")
class AliasMailProcessorSwitchTest {

    private static final String KEY = "forwarding/k1";

    @Mock
    private EmailForwardingService forwardingService;
    @Mock
    private InboundMailCenter mailCenter;
    @Mock
    private InboundReplyProcessor replyProcessor;
    @Mock
    private MailCenterSwitch mailSwitch;
    @Mock
    private ConversationNotices notices;

    private AliasMailProcessor processor;
    private final InboundMailCenter.Stored stored =
            new InboundMailCenter.Stored("conv-1", "BATbern60", "A Speaker", "Frage");

    @BeforeEach
    void setUp() {
        processor = new AliasMailProcessor(forwardingService, mailCenter, replyProcessor, mailSwitch, notices,
                new EmailForwardingProperties(true, "q", "batbern.ch", null, "cfg", 0));
        when(forwardingService.forward(anyString(), any())).thenReturn(EmailForwardingService.Result.FORWARDED);
        when(forwardingService.forward(anyString(), any(), any())).thenReturn(EmailForwardingService.Result.NOTIFIED);
    }

    private static byte[] mail(String to) {
        return ("From: A <success+a@simulator.amazonses.com>\r\nTo: " + to + "\r\nSubject: Frage\r\n"
                + "Message-ID: <m1@x>\r\nContent-Type: text/plain\r\n\r\nHallo\r\n").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("group off: forward first, then store best effort (16.2a order)")
    void off() {
        when(mailSwitch.groups()).thenReturn(Set.of());
        byte[] raw = mail("batbern60-moderator@batbern.ch");

        processor.process(KEY, raw);

        InOrder order = inOrder(forwardingService, mailCenter);
        order.verify(forwardingService).forward(KEY, raw);
        order.verify(mailCenter).ingestAlias(eq(KEY), eq(raw), any());
        verify(forwardingService, never()).forward(anyString(), any(), any());
    }

    @Test
    @DisplayName("group on and stored: store first, then the notification instead of the copy")
    void onStored() {
        when(mailSwitch.groups()).thenReturn(Set.of(MailCenterSwitch.Group.MODERATOR));
        byte[] raw = mail("batbern60-moderator@batbern.ch");
        when(mailCenter.ingestAliasStrict(KEY, raw)).thenReturn(Optional.of(stored));

        processor.process(KEY, raw);

        InOrder order = inOrder(mailCenter, forwardingService);
        order.verify(mailCenter).ingestAliasStrict(KEY, raw);
        order.verify(forwardingService).forward(eq(KEY), eq(raw), any());
        verify(forwardingService, never()).forward(KEY, raw);
    }

    @Test
    @DisplayName("group on, not stored (e.g. out of office): forwarded as today, never lost")
    void onNotStored() {
        when(mailSwitch.groups()).thenReturn(Set.of(MailCenterSwitch.Group.CONTACT));
        byte[] raw = mail("info@batbern.ch");
        when(mailCenter.ingestAliasStrict(KEY, raw)).thenReturn(Optional.empty());

        processor.process(KEY, raw);

        verify(forwardingService).forward(KEY, raw);
        verify(forwardingService, never()).forward(anyString(), any(), any());
    }

    @Test
    @DisplayName("group on, storage fails: rethrown so SQS retries; nothing sent")
    void onFailure() {
        when(mailSwitch.groups()).thenReturn(Set.of(MailCenterSwitch.Group.CONTACT));
        byte[] raw = mail("support@batbern.ch");
        when(mailCenter.ingestAliasStrict(KEY, raw)).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> processor.process(KEY, raw)).isInstanceOf(IllegalStateException.class);
        verify(forwardingService, never()).forward(anyString(), any());
        verify(forwardingService, never()).forward(anyString(), any(), any());
    }

    @Test
    @DisplayName("organizer sends (-speaker@, -participants@, partner@, ok@) are always forwarded")
    void organizerSendsAlwaysForwarded() {
        when(mailSwitch.groups()).thenReturn(Set.of(MailCenterSwitch.Group.values()));
        List<String> organizerSends = List.of("batbern60-speaker@batbern.ch", "batbern60-participants@batbern.ch",
                "partner@batbern.ch", "ok@batbern.ch", "info@batbern.ch, batbern60-speaker@batbern.ch");
        for (String to : organizerSends) {
            byte[] raw = mail(to);
            processor.process(KEY, raw);
            verify(forwardingService).forward(KEY, raw);
        }
        verify(mailCenter, never()).ingestAliasStrict(anyString(), any());
    }
}
