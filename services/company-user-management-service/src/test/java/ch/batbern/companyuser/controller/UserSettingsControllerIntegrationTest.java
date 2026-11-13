package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserPreferences;
import ch.batbern.companyuser.domain.UserSettings;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The caller's settings as one resource (Story 4.9): {@code GET} and {@code PATCH
 * /users/me/settings} carry display, notification and privacy settings together. The former
 * {@code /users/me/preferences} is gone; the storage stays the two embeddables on user_profiles.
 *
 * <p>Every test commits, as production does (no class-level {@code @Transactional}): what a test
 * reads back is the committed state. {@code @BeforeEach} and {@code @AfterEach} remove the users
 * and clear the caches, so nothing leaks into the next class in the shared container.
 */
@Import(TestAwsConfig.class)
@DisplayName("User settings, one resource")
class UserSettingsControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/users/me/settings";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CacheManager cacheManager;

    @AfterEach
    void tearDown() {
        cleanUpUsers();
    }

    /** Users and their child rows (role assignments, addresses, history cascade with the user). */
    private void cleanUpUsers() {
        jdbcTemplate.update("DELETE FROM watch_pairings");
        jdbcTemplate.update("DELETE FROM user_profiles");
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @BeforeEach
    void setUp() {
        cleanUpUsers();
        userRepository.save(User.builder()
                .username("max.muster")
                .email("max.muster@example.com")
                .firstName("Max")
                .lastName("Muster")
                .cognitoUserId("max.muster")
                .companyId("SwissCorpZH")
                .roles(new HashSet<>(Set.of(Role.PARTNER)))
                .preferences(UserPreferences.builder()
                        .theme("light")
                        .language("de")
                        .notificationFrequency("immediate")
                        .qnaNotificationFrequency("daily")
                        .quietHoursStart(LocalTime.of(22, 0))
                        .quietHoursEnd(LocalTime.of(7, 0))
                        .build())
                .settings(UserSettings.builder()
                        .profileVisibility("public")
                        .showActivityHistory(true)
                        .timezone("Europe/Zurich")
                        .build())
                .build());
    }

    @Test
    @WithMockUser(username = "max.muster")
    void should_returnBothGroupsInOneObject_when_get() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("light"))
                .andExpect(jsonPath("$.language").value("de"))
                .andExpect(jsonPath("$.notificationFrequency").value("immediate"))
                .andExpect(jsonPath("$.qnaNotificationFrequency").value("daily"))
                .andExpect(jsonPath("$.quietHoursStart").value("22:00"))
                .andExpect(jsonPath("$.quietHoursEnd").value("07:00"))
                .andExpect(jsonPath("$.profileVisibility").value("public"))
                .andExpect(jsonPath("$.showActivityHistory").value(true))
                .andExpect(jsonPath("$.timezone").value("Europe/Zurich"));
    }

    @Test
    @WithMockUser(username = "max.muster")
    void should_returnDefaults_when_nothingStored() throws Exception {
        User user = userRepository.findByUsername("max.muster").orElseThrow();
        user.setPreferences(null);
        user.setSettings(null);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("auto"))
                .andExpect(jsonPath("$.language").value("de"))
                .andExpect(jsonPath("$.qnaNotificationFrequency").value("live"))
                .andExpect(jsonPath("$.profileVisibility").value("public"))
                .andExpect(jsonPath("$.timezone").value("Europe/Zurich"));
    }

    /** The I/O matrix case: theme and timezone stored together, GET returns the merged object. */
    @Test
    @WithMockUser(username = "max.muster")
    void should_storeThemeAndTimezoneAndKeepEverythingElse_when_patched() throws Exception {
        mockMvc.perform(patch(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"theme": "dark", "timezone": "America/New_York"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("dark"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.qnaNotificationFrequency").value("daily"))
                .andExpect(jsonPath("$.quietHoursStart").value("22:00"))
                .andExpect(jsonPath("$.profileVisibility").value("public"));

        mockMvc.perform(get(URL))
                .andExpect(jsonPath("$.theme").value("dark"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.language").value("de"))
                .andExpect(jsonPath("$.notificationFrequency").value("immediate"))
                .andExpect(jsonPath("$.qnaNotificationFrequency").value("daily"))
                .andExpect(jsonPath("$.showActivityHistory").value(true));

        User stored = userRepository.findByUsername("max.muster").orElseThrow();
        assertThat(stored.getPreferences().getTheme()).isEqualTo("dark");
        assertThat(stored.getSettings().getTimezone()).isEqualTo("America/New_York");
        assertThat(stored.getPreferences().getQnaNotificationFrequency()).isEqualTo("daily");
    }

    @Test
    @WithMockUser(username = "max.muster")
    void should_storeEveryField_when_patchedWithAll() throws Exception {
        mockMvc.perform(patch(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"theme": "auto", "language": "en", "timezone": "Asia/Tokyo",
                                 "emailNotifications": false, "inAppNotifications": false,
                                 "pushNotifications": true, "notificationFrequency": "weekly_digest",
                                 "qnaNotificationFrequency": "off",
                                 "quietHoursStart": "23:30", "quietHoursEnd": "06:15",
                                 "profileVisibility": "private", "showEmail": true, "showCompany": false,
                                 "showActivityHistory": false, "allowMessaging": false,
                                 "allowCalendarSync": true, "twoFactorEnabled": true}
                                """))
                .andExpect(status().isOk());

        User stored = userRepository.findByUsername("max.muster").orElseThrow();
        UserPreferences p = stored.getPreferences();
        UserSettings s = stored.getSettings();
        assertThat(p.getTheme()).isEqualTo("auto");
        assertThat(p.getLanguage()).isEqualTo("en");
        assertThat(p.isEmailNotifications()).isFalse();
        assertThat(p.isInAppNotifications()).isFalse();
        assertThat(p.isPushNotifications()).isTrue();
        assertThat(p.getNotificationFrequency()).isEqualTo("weekly_digest");
        assertThat(p.getQnaNotificationFrequency()).isEqualTo("off");
        assertThat(p.getQuietHoursStart()).isEqualTo(LocalTime.of(23, 30));
        assertThat(p.getQuietHoursEnd()).isEqualTo(LocalTime.of(6, 15));
        assertThat(s.getTimezone()).isEqualTo("Asia/Tokyo");
        assertThat(s.getProfileVisibility()).isEqualTo("private");
        assertThat(s.isShowEmail()).isTrue();
        assertThat(s.isShowCompany()).isFalse();
        assertThat(s.isShowActivityHistory()).isFalse();
        assertThat(s.isAllowMessaging()).isFalse();
        assertThat(s.isAllowCalendarSync()).isTrue();
        assertThat(s.isTwoFactorEnabled()).isTrue();
    }

    @Test
    @WithMockUser(username = "max.muster")
    void should_setAndThenClearQuietHours_when_patchedWithAnEmptyString() throws Exception {
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quietHoursStart\": \"23:00\", \"quietHoursEnd\": \"6:30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quietHoursStart").value("23:00"))
                .andExpect(jsonPath("$.quietHoursEnd").value("06:30"));
        assertThat(userRepository.findByUsername("max.muster").orElseThrow().getPreferences()
                .getQuietHoursStart()).isEqualTo(LocalTime.of(23, 0));

        // null keeps them
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\": \"dark\", \"quietHoursStart\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quietHoursStart").value("23:00"));

        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quietHoursStart\": \"\", \"quietHoursEnd\": \"\"}"))
                .andExpect(status().isOk());

        UserPreferences stored = userRepository.findByUsername("max.muster").orElseThrow().getPreferences();
        assertThat(stored.getQuietHoursStart()).isNull();
        assertThat(stored.getQuietHoursEnd()).isNull();
        assertThat(stored.getTheme()).isEqualTo("dark");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"theme\": \"neon\"}",
        "{\"language\": \"xx\"}",
        "{\"qnaNotificationFrequency\": \"hourly\"}",
        "{\"quietHoursStart\": \"25:00\"}",
        "{\"timezone\": \"Mars/Olympus\"}"
    })
    @WithMockUser(username = "max.muster")
    void should_return400AndStoreNothing_when_valueIsInvalid(String body) throws Exception {
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        User stored = userRepository.findByUsername("max.muster").orElseThrow();
        assertThat(stored.getPreferences().getTheme()).isEqualTo("light");
        assertThat(stored.getSettings().getTimezone()).isEqualTo("Europe/Zurich");
    }

    @Test
    @WithMockUser(username = "max.muster")
    void should_notServeTheFormerPreferencesOrPut() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/me/preferences"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"theme\": \"dark\"}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
    }

    @Test
    @WithMockUser(username = "max.muster")
    void should_mergeSettingsIntoTheUser_when_includeSettings() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").param("include", "settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.theme").value("light"))
                .andExpect(jsonPath("$.settings.timezone").value("Europe/Zurich"))
                .andExpect(jsonPath("$.preferences").doesNotExist());
    }

    /**
     * Story "What's new": the time the user last closed the card is stored on the settings, so
     * every browser of the same account sees the same state.
     */
    @Test
    @WithMockUser(username = "max.muster")
    void should_storeWhatsNewSeenAtAndReturnItEverywhere_when_patched() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsNewSeenAt").doesNotExist());

        Instant seen = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"whatsNewSeenAt\": \"" + seen + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsNewSeenAt").exists())
                .andExpect(jsonPath("$.theme").value("light"));

        assertThat(userRepository.findByUsername("max.muster").orElseThrow().getSettings()
                .getWhatsNewSeenAt()).isEqualTo(seen);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_profiles WHERE username = 'max.muster'"
                        + " AND settings_whats_new_seen_at IS NOT NULL", Integer.class);
        assertThat(rows).isEqualTo(1);

        mockMvc.perform(get("/api/v1/users/me").param("include", "settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.whatsNewSeenAt").exists());

        // another field later keeps it
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"theme\": \"dark\"}"))
                .andExpect(status().isOk());
        assertThat(userRepository.findByUsername("max.muster").orElseThrow().getSettings()
                .getWhatsNewSeenAt()).isEqualTo(seen);
    }

    /**
     * A client clock running fast must not keep the card open: a time in the future is stored as
     * the server's now (later entries still come back), and nothing else is refused.
     */
    @ParameterizedTest
    @ValueSource(longs = {2, 60 * 24})
    @WithMockUser(username = "max.muster")
    void should_storeTheServerNow_when_whatsNewSeenAtIsInTheFuture(long minutesAhead) throws Exception {
        Instant before = Instant.now();
        Instant future = before.plus(minutesAhead, ChronoUnit.MINUTES);
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\": \"dark\", \"whatsNewSeenAt\": \"" + future + "\"}"))
                .andExpect(status().isOk());
        Instant after = Instant.now();

        User stored = userRepository.findByUsername("max.muster").orElseThrow();
        assertThat(stored.getSettings().getWhatsNewSeenAt())
                // PostgreSQL keeps microseconds (rounded): allow a millisecond either side
                .isBetween(before.minusMillis(1), after.plusMillis(1));
        assertThat(stored.getPreferences().getTheme()).isEqualTo("dark");
    }

    @Test
    void should_return401_when_notAuthenticated() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"theme\": \"dark\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"whatsNewSeenAt\": \"" + Instant.now() + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
