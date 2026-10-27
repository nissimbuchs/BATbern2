package ch.batbern.events.forwarding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Ports T11 of the Lambda's email-forwarder.test.ts, plus auto-reply detection. */
@DisplayName("MailClassifier")
class MailClassifierTest {

    @Test
    @DisplayName("should_detectReply_when_topLevelTextCalendarMethodReply")
    void should_detectReply_when_topLevelTextCalendarMethodReply() {
        String raw = "From: a@example.org\r\nContent-Type: text/calendar; method=REPLY; charset=UTF-8\r\n\r\nBEGIN:VCALENDAR";
        assertThat(MailClassifier.isCalendarReply(raw)).isTrue();
    }

    @Test
    @DisplayName("should_detectReply_when_multipartContainsCalendarReplyPart")
    void should_detectReply_when_multipartContainsCalendarReplyPart() {
        String raw = "Content-Type: multipart/alternative; boundary=\"b\"\r\n\r\n--b\r\n"
                + "Content-Type: text/plain\r\n\r\nAccepted\r\n--b\r\n"
                + "Content-Type: text/calendar; charset=utf-8; method=REPLY\r\n\r\nBEGIN:VCALENDAR\r\n--b--";
        assertThat(MailClassifier.isCalendarReply(raw)).isTrue();
    }

    @Test
    @DisplayName("should_detectReply_when_methodOnContinuationLine")
    void should_detectReply_when_methodOnContinuationLine() {
        String raw = "Content-Type: text/calendar;\r\n\tcharset=utf-8;\r\n method=REPLY\r\n\r\nBEGIN:VCALENDAR";
        assertThat(MailClassifier.isCalendarReply(raw)).isTrue();
    }

    @Test
    @DisplayName("should_detectReply_when_methodValueIsQuotedOrLowercase")
    void should_detectReply_when_methodValueIsQuotedOrLowercase() {
        assertThat(MailClassifier.isCalendarReply("Content-Type: text/calendar; method=\"REPLY\"\r\n\r\nx")).isTrue();
        assertThat(MailClassifier.isCalendarReply("content-type: TEXT/CALENDAR; Method=reply\r\n\r\nx")).isTrue();
    }

    @Test
    @DisplayName("should_returnFalse_when_calendarRequestOrCancel")
    void should_returnFalse_when_calendarRequestOrCancel() {
        assertThat(MailClassifier.isCalendarReply("Content-Type: text/calendar; method=REQUEST\r\n\r\nx")).isFalse();
        assertThat(MailClassifier.isCalendarReply("Content-Type: text/calendar; method=CANCEL\r\n\r\nx")).isFalse();
    }

    @Test
    @DisplayName("should_returnFalse_when_plainTextMentionsReply")
    void should_returnFalse_when_plainTextMentionsReply() {
        String raw = "Subject: Re: method=REPLY\r\nContent-Type: text/plain\r\n\r\nmethod=REPLY text/calendar";
        assertThat(MailClassifier.isCalendarReply(raw)).isFalse();
    }

    @Test
    @DisplayName("should_detectAutoReply_when_autoSubmittedOrVendorHeaders")
    void should_detectAutoReply_when_autoSubmittedOrVendorHeaders() {
        assertThat(MailClassifier.isAutoReply("auto-replied", null, null, null)).isTrue();
        assertThat(MailClassifier.isAutoReply("auto-generated", null, null, null)).isTrue();
        assertThat(MailClassifier.isAutoReply(null, "auto_reply", null, null)).isTrue();
        assertThat(MailClassifier.isAutoReply(null, null, "yes", null)).isTrue();
        assertThat(MailClassifier.isAutoReply(null, null, null, "yes")).isTrue();
    }

    @Test
    @DisplayName("should_notDetectAutoReply_when_humanOrNewsletter")
    void should_notDetectAutoReply_when_humanOrNewsletter() {
        assertThat(MailClassifier.isAutoReply(null, null, null, null)).isFalse();
        assertThat(MailClassifier.isAutoReply("no", null, null, null)).isFalse();
        assertThat(MailClassifier.isAutoReply(null, "bulk", null, null)).isFalse();
        assertThat(MailClassifier.isAutoReply(null, "list", null, null)).isFalse();
    }
}
