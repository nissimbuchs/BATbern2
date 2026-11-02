package ch.batbern.events.mailcenter;

import ch.batbern.events.service.AdminSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Story 16.6 AC7: which alias groups get a notification instead of a forwarded copy. */
@DisplayName("Story 16.6: notify-instead-of-forward switch")
class MailCenterSwitchTest {

    private final AdminSettingsService settings = mock(AdminSettingsService.class);
    private final MailCenterSwitch mailSwitch = new MailCenterSwitch(settings);

    @Test
    @DisplayName("not set or empty: today's forwarding for every group")
    void defaultOff() {
        when(settings.getSetting(MailCenterSwitch.SETTING_KEY)).thenReturn(Optional.empty());
        assertThat(mailSwitch.groups()).isEmpty();
        when(settings.getSetting(MailCenterSwitch.SETTING_KEY)).thenReturn(Optional.of("  "));
        assertThat(mailSwitch.groups()).isEmpty();
    }

    @Test
    @DisplayName("comma or space separated, any case; unknown values ignored")
    void parse() {
        when(settings.getSetting(MailCenterSwitch.SETTING_KEY)).thenReturn(Optional.of("moderator, REPLIES foo"));
        assertThat(mailSwitch.groups())
                .containsExactlyInAnyOrder(MailCenterSwitch.Group.MODERATOR, MailCenterSwitch.Group.REPLIES);
    }

    @Test
    @DisplayName("read per mail: removing a group switches it back at once")
    void readPerCall() {
        when(settings.getSetting(MailCenterSwitch.SETTING_KEY)).thenReturn(Optional.of("contact"));
        assertThat(mailSwitch.isOn(MailCenterSwitch.Group.CONTACT)).isTrue();
        when(settings.getSetting(MailCenterSwitch.SETTING_KEY)).thenReturn(Optional.of(""));
        assertThat(mailSwitch.isOn(MailCenterSwitch.Group.CONTACT)).isFalse();
    }
}
