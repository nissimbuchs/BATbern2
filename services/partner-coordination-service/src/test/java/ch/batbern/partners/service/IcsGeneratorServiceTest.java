package ch.batbern.partners.service;

import ch.batbern.partners.client.dto.EventSummaryDTO;
import ch.batbern.partners.domain.MeetingType;
import ch.batbern.partners.domain.PartnerMeeting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for IcsGeneratorService — verifies RFC 5545 compliance.
 *
 * Covers:
 * - SEQUENCE field presence and correctness
 * - DTSTAMP field presence (required per RFC 5545 §3.6.1 for METHOD:REQUEST)
 * - ORGANIZER field presence
 * - Stable UID across repeated sends
 * - METHOD:REQUEST at VCALENDAR level
 */
class IcsGeneratorServiceTest {

    private IcsGeneratorService service;

    private static final UUID MEETING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ORGANIZER_EMAIL = "noreply@batbern.ch";

    @BeforeEach
    void setUp() {
        service = new IcsGeneratorService();
        ReflectionTestUtils.setField(service, "organizerEmail", ORGANIZER_EMAIL);
    }

    @Test
    void should_containSequenceZero_when_inviteSequenceIsZero() {
        String ics = generate(buildMeeting(0));
        assertThat(ics).contains("SEQUENCE:0");
    }

    @Test
    void should_containSequenceTwo_when_inviteSequenceIsTwo() {
        String ics = generate(buildMeeting(2));
        assertThat(ics).contains("SEQUENCE:2");
    }

    @Test
    void should_containDtstamp_inMeetingVEvent() {
        String ics = generate(buildMeeting(0));
        assertThat(ics).contains("DTSTAMP:");
    }

    @Test
    void should_containOrganizer_withOrganizerEmail() {
        String ics = generate(buildMeeting(0));
        assertThat(ics).contains("ORGANIZER:mailto:" + ORGANIZER_EMAIL);
    }

    @Test
    void should_containMethodRequest_atVcalendarLevel() {
        String ics = generate(buildMeeting(0));
        assertThat(ics).contains("METHOD:REQUEST");
    }

    @Test
    void should_containStableUid_regardless_of_sequence() {
        String expectedUid = "UID:" + MEETING_ID + "@batbern.ch";
        assertThat(generate(buildMeeting(0))).contains(expectedUid);
        assertThat(generate(buildMeeting(5))).contains(expectedUid);
    }

    @Test
    void should_containTwoVevents_partnerMeetingAndMainEvent() {
        String ics = generate(buildMeeting(0));
        long count = ics.lines().filter("BEGIN:VEVENT"::equals).count();
        assertThat(count).isEqualTo(2);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String generate(PartnerMeeting meeting) {
        EventSummaryDTO event = new EventSummaryDTO(
                "BATbern57", "BATbern Spring 2026",
                LocalDate.of(2026, 5, 14),
                LocalTime.of(18, 0), LocalTime.of(22, 0),
                "Bern Congress Centre"
        );
        return new String(service.generate(meeting, event), StandardCharsets.UTF_8);
    }

    private PartnerMeeting buildMeeting(int sequence) {
        return PartnerMeeting.builder()
                .id(MEETING_ID)
                .eventCode("BATbern57")
                .meetingType(MeetingType.SPRING)
                .meetingDate(LocalDate.of(2026, 5, 14))
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(14, 0))
                .location("Lunch Hall")
                .agenda("1. Welcome\n2. Partnership")
                .createdBy("organizer")
                .inviteSequence(sequence)
                .build();
    }
}
