package ch.batbern.events.service;

import ch.batbern.events.domain.Registration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CalendarInviteUid (Story 16.0 AC7)")
class CalendarInviteUidTest {

    private static final UUID CALENDAR_UID = UUID.fromString("0b6e3f8a-1d2c-4b5a-9e8f-7a6b5c4d3e2f");

    @Test
    @DisplayName("should_roundTrip_when_uidWrittenAndReadBack")
    void should_roundTrip_when_uidWrittenAndReadBack() {
        Registration registration = Registration.builder().calendarUid(CALENDAR_UID).build();

        String local = CalendarInviteUid.forRegistration(registration);

        assertThat(local).isEqualTo("reg-0b6e3f8a-1d2c-4b5a-9e8f-7a6b5c4d3e2f");
        assertThat(CalendarInviteUid.parse(local + "@batbern.ch")).contains(CALENDAR_UID);
        // Clients may change case; RFC 5545 UIDs are compared as given, ours are lowercase
        assertThat(CalendarInviteUid.parse(" REG-0B6E3F8A-1D2C-4B5A-9E8F-7A6B5C4D3E2F@BATBERN.CH "))
                .contains(CALENDAR_UID);
    }

    @Test
    @DisplayName("should_beEmpty_when_uidIsNotARegistrationInvite")
    void should_beEmpty_when_uidIsNotARegistrationInvite() {
        assertThat(CalendarInviteUid.parse("7c9e6679-7425-40de-944b-e07fc1f90ae7@batbern.ch")).isEmpty();
        assertThat(CalendarInviteUid.parse("BATbern60@batbern.ch")).isEmpty();
        assertThat(CalendarInviteUid.parse("reg-0b6e3f8a-1d2c-4b5a-9e8f-7a6b5c4d3e2f@evil.example")).isEmpty();
        assertThat(CalendarInviteUid.parse(null)).isEmpty();
    }
}
