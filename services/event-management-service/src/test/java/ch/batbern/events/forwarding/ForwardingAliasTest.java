package ch.batbern.events.forwarding;

import ch.batbern.events.forwarding.ForwardingAlias.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ForwardingAlias")
class ForwardingAliasTest {

    private static final String DOMAIN = "batbern.ch";

    @ParameterizedTest(name = "{0} → {1}, event {2}, open {3}")
    @CsvSource({
        "ok@batbern.ch,                     ORGANIZERS,                    ,          false",
        "info@batbern.ch,                   ORGANIZERS,                    ,          true",
        "events@batbern.ch,                 ORGANIZERS,                    ,          true",
        "partner@batbern.ch,                PARTNERS,                      ,          false",
        "support@batbern.ch,                SUPPORT,                       ,          true",
        "batbern60-speaker@batbern.ch,      EVENT_SPEAKERS,                BATbern60, false",
        "batbern60-moderator@batbern.ch,    EVENT_MODERATOR,               BATbern60, true",
        "batbern60-participants@batbern.ch, EVENT_PARTICIPANTS,            BATbern60, false",
        "batbern58@batbern.ch,              EVENT_PARTICIPANTS_DEPRECATED, BATbern58, false",
        "noreply@batbern.ch,                NO_REPLY,                      ,          false",
        "calendar@batbern.ch,               NO_REPLY,                      ,          false",
        "batbern60-speakerx@batbern.ch,     UNKNOWN,                       ,          false",
        "batbern60-moderators@batbern.ch,   UNKNOWN,                       ,          false",
        "someone@batbern.ch,                UNKNOWN,                       ,          false",
        "INFO@BATBERN.CH,                   ORGANIZERS,                    ,          true"
    })
    @DisplayName("should_classifyAlias_when_addressInForwardingDomain")
    void should_classifyAlias_when_addressInForwardingDomain(String address, Kind kind, String eventCode,
                                                             boolean open) {
        ForwardingAlias alias = ForwardingAlias.parse(address, DOMAIN).orElseThrow();

        assertThat(alias.kind()).isEqualTo(kind);
        assertThat(alias.eventCode()).isEqualTo(eventCode);
        assertThat(alias.openToAnyone()).isEqualTo(open);
    }

    @Test
    @DisplayName("should_beEmpty_when_addressInOtherDomain")
    void should_beEmpty_when_addressInOtherDomain() {
        assertThat(ForwardingAlias.parse("info@batbern.ch.evil.org", DOMAIN)).isEmpty();
        assertThat(ForwardingAlias.parse("info@staging.batbern.ch", DOMAIN)).isEmpty();
        assertThat(ForwardingAlias.parse("@batbern.ch", DOMAIN)).isEmpty();
        assertThat(ForwardingAlias.parse(null, DOMAIN)).isEmpty();
    }

    @Test
    @DisplayName("should_neverOpenMassMailAliases")
    void should_neverOpenMassMailAliases() {
        for (String localPart : new String[] {"ok", "partner", "batbern60", "batbern60-speaker",
            "batbern60-participants"}) {
            assertThat(ForwardingAlias.parse(localPart + "@batbern.ch", DOMAIN).orElseThrow().openToAnyone())
                    .as(localPart)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("should_notForward_when_noReplyOrUnknown")
    void should_notForward_when_noReplyOrUnknown() {
        assertThat(ForwardingAlias.parse("noreply@batbern.ch", DOMAIN).orElseThrow().forwardable()).isFalse();
        assertThat(ForwardingAlias.parse("whoever@batbern.ch", DOMAIN).orElseThrow().forwardable()).isFalse();
        assertThat(ForwardingAlias.parse("info@batbern.ch", DOMAIN).orElseThrow().forwardable()).isTrue();
    }

    @Test
    @DisplayName("should_buildModeratorAddress_when_eventCodeGiven")
    void should_buildModeratorAddress_when_eventCodeGiven() {
        assertThat(ForwardingAlias.moderatorAddress("BATbern60", DOMAIN)).isEqualTo("batbern60-moderator@batbern.ch");
        assertThat(ForwardingAlias.parse(ForwardingAlias.moderatorAddress("BATbern60", DOMAIN), DOMAIN)
                .orElseThrow().kind()).isEqualTo(ForwardingAlias.Kind.EVENT_MODERATOR);
    }

    @Test
    @DisplayName("should_returnNull_when_eventCodeIsNotAnEventNumber")
    void should_returnNull_when_eventCodeIsNotAnEventNumber() {
        assertThat(ForwardingAlias.moderatorAddress("TEST-EVENT", DOMAIN)).isNull();
        assertThat(ForwardingAlias.moderatorAddress(null, DOMAIN)).isNull();
    }
}
