package ch.batbern.shared.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MailContext send groups (Story 16.1a AC2)")
class MailContextTest {

    @Test
    @DisplayName("individual mails each get their own send group")
    void individual_freshGroupPerMail() {
        MailContext first = MailContext.individual("BATbern60", MailContext.Audience.REGISTRANTS, "deregistration-link");
        MailContext second = MailContext.individual("BATbern60", MailContext.Audience.REGISTRANTS, "deregistration-link");

        assertThat(first.sendGroupId()).isNotEqualTo(second.sendGroupId());
        assertThat(first.senderUsername()).isNull();
        assertThat(first.contentSource()).isEqualTo(MailContext.ContentSource.TEMPLATE);
    }

    @Test
    @DisplayName("one trigger groups by event and template key")
    void trigger_groupsByEventAndTemplate() {
        MailTrigger run = MailTrigger.system();

        MailContext a = MailContext.of(run, "BATbern60", MailContext.Audience.SPEAKERS, "speaker-reminder-tier1");
        MailContext b = MailContext.of(run, "BATbern60", MailContext.Audience.SPEAKERS, "speaker-reminder-tier1");
        MailContext otherTemplate = MailContext.of(run, "BATbern60", MailContext.Audience.SPEAKERS, "speaker-reminder-tier2");
        MailContext otherEvent = MailContext.of(run, "BATbern61", MailContext.Audience.SPEAKERS, "speaker-reminder-tier1");
        MailContext nextRun = MailContext.of(MailTrigger.system(), "BATbern60", MailContext.Audience.SPEAKERS,
                "speaker-reminder-tier1");

        assertThat(a.sendGroupId()).isEqualTo(b.sendGroupId());
        assertThat(a.sendGroupId()).isNotEqualTo(otherTemplate.sendGroupId())
                .isNotEqualTo(otherEvent.sendGroupId())
                .isNotEqualTo(nextRun.sendGroupId());
    }

    @Test
    @DisplayName("a send audit row keeps its group across retries and carries the organizer")
    void forSend_stableAcrossRetries() {
        UUID sendId = UUID.randomUUID();

        MailContext first = MailContext.of(MailTrigger.forSend(sendId, "organizer1"), "BATbern60",
                MailContext.Audience.NEWSLETTER, "newsletter-event");
        MailContext retry = MailContext.of(MailTrigger.forSend(sendId, "organizer1"), "BATbern60",
                MailContext.Audience.NEWSLETTER, "newsletter-event");

        assertThat(first.sendGroupId()).isEqualTo(retry.sendGroupId());
        assertThat(first.senderUsername()).isEqualTo("organizer1");
    }

    @Test
    @DisplayName("the group id is a valid SES tag value (letters, digits, _ and - only)")
    void groupId_isSesTagSafe() {
        MailContext ctx = MailContext.of(MailTrigger.organizer("org"), null, MailContext.Audience.SYSTEM, "a.b/c d");

        assertThat(ctx.sendGroupId()).matches("[A-Za-z0-9_-]{1,256}");
    }

    @Test
    @DisplayName("withSecrets and forNewsletterSend keep the group and add the detail")
    void copies_keepGroup() {
        UUID sendId = UUID.randomUUID();
        MailContext base = MailContext.individual("BATbern60", MailContext.Audience.SPEAKERS, "speaker-invitation");

        MailContext withSecrets = base.withSecrets("Pa55word!", null, " ");
        MailContext newsletter = base.forNewsletterSend(sendId, true);

        assertThat(withSecrets.sendGroupId()).isEqualTo(base.sendGroupId());
        assertThat(withSecrets.secrets()).containsExactly("Pa55word!");
        assertThat(newsletter.newsletterSendId()).isEqualTo(sendId);
        assertThat(newsletter.test()).isTrue();
    }
}
