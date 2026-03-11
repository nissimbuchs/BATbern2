package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.entity.AppSettingEntity;
import ch.batbern.events.repository.AppSettingRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class AdminSettingsControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String SETTINGS_URL = "/api/v1/admin/settings";
    private static final String SUPPORT_CONTACTS_KEY = "email-forwarding.support-contacts";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        appSettingRepository.deleteAll();
    }

    // T2.1 — GET returns setting value
    @Test
    @DisplayName("should_returnSettingValue_when_settingExists")
    @WithMockUser(roles = "ORGANIZER")
    void should_returnSettingValue_when_settingExists() throws Exception {
        appSettingRepository.save(AppSettingEntity.builder()
                .settingKey(SUPPORT_CONTACTS_KEY)
                .settingValue("a@b.ch,c@d.ch")
                .updatedBy("test-user")
                .build());

        mockMvc.perform(get(SETTINGS_URL + "/{key}", SUPPORT_CONTACTS_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key", is(SUPPORT_CONTACTS_KEY)))
                .andExpect(jsonPath("$.value", is("a@b.ch,c@d.ch")));
    }

    // T2.2 — PUT stores setting
    @Test
    @DisplayName("should_storeSetting_when_validPutRequest")
    @WithMockUser(username = "organizer.user", roles = "ORGANIZER")
    void should_storeSetting_when_validPutRequest() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("value", "a@b.ch,c@d.ch"));

        mockMvc.perform(put(SETTINGS_URL + "/{key}", SUPPORT_CONTACTS_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key", is(SUPPORT_CONTACTS_KEY)))
                .andExpect(jsonPath("$.value", is("a@b.ch,c@d.ch")));

        // Verify persisted
        mockMvc.perform(get(SETTINGS_URL + "/{key}", SUPPORT_CONTACTS_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value", is("a@b.ch,c@d.ch")));
    }

    // T2.2 — PUT updates existing setting
    @Test
    @DisplayName("should_updateSetting_when_settingAlreadyExists")
    @WithMockUser(username = "organizer.user", roles = "ORGANIZER")
    void should_updateSetting_when_settingAlreadyExists() throws Exception {
        appSettingRepository.save(AppSettingEntity.builder()
                .settingKey(SUPPORT_CONTACTS_KEY)
                .settingValue("old@email.ch")
                .updatedBy("old-user")
                .build());

        String body = objectMapper.writeValueAsString(Map.of("value", "new@email.ch"));

        mockMvc.perform(put(SETTINGS_URL + "/{key}", SUPPORT_CONTACTS_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value", is("new@email.ch")));
    }

    // T2.3 — GET returns 200 with empty value when setting doesn't exist
    @Test
    @DisplayName("should_returnEmptyValue_when_settingDoesNotExist")
    @WithMockUser(roles = "ORGANIZER")
    void should_returnEmptyValue_when_settingDoesNotExist() throws Exception {
        mockMvc.perform(get(SETTINGS_URL + "/{key}", SUPPORT_CONTACTS_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key", is(SUPPORT_CONTACTS_KEY)))
                .andExpect(jsonPath("$.value").value(nullValue()));
    }

    // T2.4 — GET is open for any caller (Story 10.26: Lambda forwarder reads settings without JWT)
    @Test
    @DisplayName("should_return200_when_nonOrganizerAccessesGet")
    @WithMockUser(roles = "PARTNER")
    void should_return200_when_nonOrganizerAccessesGet() throws Exception {
        mockMvc.perform(get(SETTINGS_URL + "/{key}", SUPPORT_CONTACTS_KEY))
                .andExpect(status().isOk());
    }

    // T2.4 — PUT requires ORGANIZER role (403 for non-organizers)
    @Test
    @DisplayName("should_return403_when_nonOrganizerAccessesPut")
    @WithMockUser(roles = "SPEAKER")
    void should_return403_when_nonOrganizerAccessesPut() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("value", "x@y.ch"));

        mockMvc.perform(put(SETTINGS_URL + "/{key}", SUPPORT_CONTACTS_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // Story 10.26 — GET is open for VPC-internal callers (Lambda email forwarder)
    @Test
    @DisplayName("should_return200_when_unauthenticatedUserAccessesGet")
    void should_return200_when_unauthenticatedUserAccessesGet() throws Exception {
        mockMvc.perform(get(SETTINGS_URL + "/{key}", SUPPORT_CONTACTS_KEY))
                .andExpect(status().isOk());
    }
}
