package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.dto.venuecoordination.VenueCoordinationConfig;
import ch.batbern.events.repository.AppSettingRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The venue coordination config ({@code venue.coordination.config}) is validated on write with
 * the parse {@code VenueCoordinationService} applies on read: a value it could not read is a 400
 * and is not stored (Story 4.9). A contact on a reserved domain (RFC 2606/6761), which SES would
 * never be asked to deliver to, is a 400 as well (16.8c).
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class VenueCoordinationConfigSettingIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/admin/settings/" + VenueCoordinationConfig.SETTING_KEY;
    private static final String VALID = """
            {"venue": {"salutation": "Liebe Frau Brünnimann", "name": "Kornhausforum",
                       "email": "success+venue@simulator.amazonses.com"},
             "catering": {"name": "Apéro AG", "email": "success+catering@simulator.amazonses.com"},
             "coordinatorUsername": "carla.muster"}""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppSettingRepository appSettingRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    @AfterEach
    void removeStoredValue() {
        appSettingRepository.findBySettingKey(VenueCoordinationConfig.SETTING_KEY)
                .ifPresent(appSettingRepository::delete);
    }

    @Test
    @WithMockUser(username = "carla.muster", roles = "ORGANIZER")
    void should_storeTheConfig_when_valueIsValid() throws Exception {
        putValue(VALID, 200);

        String stored = appSettingRepository.findBySettingKey(VenueCoordinationConfig.SETTING_KEY)
                .orElseThrow().getSettingValue();
        VenueCoordinationConfig config = objectMapper.readValue(stored, VenueCoordinationConfig.class);
        assertThat(config.getVenue().getName()).isEqualTo("Kornhausforum");
        assertThat(config.getCoordinatorUsername()).isEqualTo("carla.muster");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "[]", "{\"venue\": \"Kornhausforum\"}"})
    @WithMockUser(username = "carla.muster", roles = "ORGANIZER")
    void should_return400AndKeepTheStoredValue_when_valueIsNotAConfig(String value) throws Exception {
        putValue(VALID, 200);

        putValue(value, 400);

        String stored = appSettingRepository.findBySettingKey(VenueCoordinationConfig.SETTING_KEY)
                .orElseThrow().getSettingValue();
        assertThat(stored).isEqualTo(VALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"venue@example.com", "kontakt@kornhaus.test", "catering@caterer.invalid"})
    @WithMockUser(username = "carla.muster", roles = "ORGANIZER")
    void should_return400AndKeepTheStoredValue_when_aContactIsOnAReservedDomain(String address) throws Exception {
        putValue(VALID, 200);

        putValue(VALID.replace("success+catering@simulator.amazonses.com", address), 400);

        String stored = appSettingRepository.findBySettingKey(VenueCoordinationConfig.SETTING_KEY)
                .orElseThrow().getSettingValue();
        assertThat(stored).isEqualTo(VALID);
    }

    private void putValue(String value, int expectedStatus) throws Exception {
        mockMvc.perform(put(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("value", value))))
                .andExpect(status().is(expectedStatus));
    }
}
