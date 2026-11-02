package ch.batbern.events.forwarding;

import ch.batbern.events.mailcenter.ConversationNotices;
import ch.batbern.events.mailcenter.MailCenterSwitch;
import ch.batbern.events.mailcenter.inbound.InboundMail;
import ch.batbern.events.mailcenter.inbound.InboundMailCenter;
import ch.batbern.events.mailcenter.inbound.InboundMailParser;
import ch.batbern.events.service.InboundReplyProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Processes one mail to the {@code @batbern.ch} aliases (Story 16.2a, extracted from
 * {@link EmailForwardingListener} so the local dev simulator runs the same code): forwarding first,
 * unchanged, then the Mail Center records or counts it. The Mail Center never throws; a forwarding
 * failure propagates as before, so SQS redelivers.
 *
 * <p>Story 16.6: {@code replies+{token}@} is handed to the replies path. When every alias the mail
 * addresses belongs to a group switched to the Mail Center ({@link MailCenterSwitch}), the mail is
 * stored first (a failure is rethrown) and its recipients get a notification instead of the copy;
 * mail that was not stored is forwarded as today. Organizer sends to {@code -speaker@},
 * {@code -participants@}, {@code partner@} and {@code ok@} are always forwarded.
 */
@Component
@RequiredArgsConstructor
public class AliasMailProcessor {

    private final EmailForwardingService forwardingService;
    private final InboundMailCenter mailCenter;
    private final InboundReplyProcessor replyProcessor;
    private final MailCenterSwitch mailSwitch;
    private final ConversationNotices notices;
    private final EmailForwardingProperties properties;

    public EmailForwardingService.Result process(String s3Key, byte[] raw) {
        Set<MailCenterSwitch.Group> addressed = groups(raw);
        // Only replies@ addresses: otherwise the other aliases are forwarded and the token threads anyway
        boolean conversationReply = addressed.equals(Set.of(MailCenterSwitch.Group.REPLIES));
        if (conversationReply && !mailCenter.isConversationReply(raw, "alias")) {
            // Story 16.6 AC4: an unknown token is handled like any replies@ mail
            replyProcessor.process(s3Key, raw);
            return EmailForwardingService.Result.CONVERSATION_REPLY;
        }
        if (!addressed.isEmpty() && mailSwitch.groups().containsAll(addressed)) {
            return notifyInstead(s3Key, raw, conversationReply);
        }
        if (conversationReply) {
            // SES delivers replies+{token}@ through the catch-all rule, not RouteReplies
            forwardingService.forwardUnrecognizedReply(s3Key, raw);
            mailCenter.ingestAlias(s3Key, raw, EmailForwardingService.Result.CONVERSATION_REPLY);
            return EmailForwardingService.Result.CONVERSATION_REPLY;
        }
        EmailForwardingService.Result result = forwardingService.forward(s3Key, raw);
        mailCenter.ingestAlias(s3Key, raw, result);
        return result;
    }

    /** AC9: stored before anything is sent; not stored → forwarded as today, so nothing is lost. */
    private EmailForwardingService.Result notifyInstead(String s3Key, byte[] raw, boolean conversationReply) {
        Optional<InboundMailCenter.Stored> stored = mailCenter.ingestAliasStrict(s3Key, raw);
        if (conversationReply) {
            forwardingService.forwardUnrecognizedReply(s3Key, raw,
                    stored.map(notices::forStored).orElse(null));
            return EmailForwardingService.Result.CONVERSATION_REPLY;
        }
        return stored.isPresent()
                ? forwardingService.forward(s3Key, raw, notices.forStored(stored.get()))
                : forwardingService.forward(s3Key, raw);
    }

    /**
     * The switchable groups of the addressed aliases; empty when any addressed alias is an organizer
     * send or internal ({@code ok@}), so such mail is always forwarded.
     */
    private Set<MailCenterSwitch.Group> groups(byte[] raw) {
        List<String> addresses = new ArrayList<>();
        try {
            InboundMail mail = InboundMailParser.parse(raw);
            addresses.addAll(mail.to());
            addresses.addAll(mail.cc());
        } catch (RuntimeException e) {
            return Set.of(); // forward() reports the malformed mail
        }
        Set<MailCenterSwitch.Group> groups = EnumSet.noneOf(MailCenterSwitch.Group.class);
        for (String address : addresses) {
            Optional<ForwardingAlias> alias = ForwardingAlias.parse(address, properties.domain());
            if (alias.isEmpty()) {
                continue;
            }
            switch (alias.get().kind()) {
                case EVENT_MODERATOR -> groups.add(MailCenterSwitch.Group.MODERATOR);
                case CONVERSATION_REPLY -> groups.add(MailCenterSwitch.Group.REPLIES);
                case SUPPORT -> groups.add(MailCenterSwitch.Group.CONTACT);
                case ORGANIZERS -> {
                    if (!alias.get().openToAnyone()) {
                        return Set.of(); // ok@
                    }
                    groups.add(MailCenterSwitch.Group.CONTACT);
                }
                case NO_REPLY, UNKNOWN -> {
                    // not forwarded at all
                }
                default -> {
                    return Set.of(); // -speaker@, -participants@, batbern{N}@, partner@
                }
            }
        }
        return groups;
    }
}
