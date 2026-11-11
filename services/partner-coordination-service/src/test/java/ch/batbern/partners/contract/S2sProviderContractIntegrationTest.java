package ch.batbern.partners.contract;

import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.MeetingType;
import ch.batbern.partners.domain.PartnerMeeting;
import ch.batbern.partners.repository.PartnerMeetingRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static ch.batbern.shared.test.contract.ProviderContract.assertExamplesMatchSchema;
import static ch.batbern.shared.test.contract.ProviderContract.assertResponseMatchesSpec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider half of the S2S contract (Story 17.0b2): the one PCS operation EMS calls,
 * {@code POST /internal/partner-meetings/rsvps} (EMS {@code PartnerMeetingRsvpClientImpl}), runs
 * through the real controller and must match {@code docs/api/internal/pcs-internal.openapi.yml},
 * the spec EMS's contract test answers from.
 *
 * <p>Until Epic 17 entry 8 this class also proved the two public reads EMS's
 * {@code PartnerApiClientImpl} made for the topic canvas; the canvas now loads them in the browser
 * and EMS calls no public PCS operation.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("PCS honours the S2S contract EMS is tested against")
class S2sProviderContractIntegrationTest extends AbstractIntegrationTest {

    private static final String PCS_INTERNAL = "docs/api/internal/pcs-internal.openapi.yml";
    private static final String RSVPS = "/internal/partner-meetings/rsvps";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PartnerMeetingRepository meetingRepository;

    @AfterEach
    void cleanUp() {
        meetingRepository.findAll().stream().filter(m -> "contract-0b2".equals(m.getCreatedBy()))
                .forEach(meetingRepository::delete);
    }

    @Test
    void should_haveValidExamples_forInternalOperations() {
        assertExamplesMatchSchema(PCS_INTERNAL, "POST", RSVPS);
    }

    private MvcResult rsvp(String json, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(RSVPS).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expectedStatus)).andReturn();
        assertResponseMatchesSpec(PCS_INTERNAL, result);
        return result;
    }

    @Test
    @DisplayName("POST /internal/partner-meetings/rsvps: 200 recorded, 400 unknown partStat, 404 unknown meeting")
    void recordRsvp_matchesSpec() throws Exception {
        UUID meeting = meetingRepository.save(PartnerMeeting.builder()
                .eventCode("BATbern9017").meetingType(MeetingType.SPRING).meetingDate(LocalDate.of(2026, 5, 14))
                .startTime(LocalTime.of(12, 0)).endTime(LocalTime.of(14, 0)).location("Kursaal")
                .createdBy("contract-0b2").build()).getId();
        String body = """
                {"meetingId": "%s", "attendeeEmail": "Partner.Contact@Example.com", "partStat": "%s"}""";

        MvcResult recorded = rsvp(body.formatted(meeting, "accepted"), 200);
        // Unknown partStat: 400 without a body, a known deviation from the spec's 400 (see the
        // spec's description; Story 17.6), so status only. EMS's client discards any 4xx.
        mockMvc.perform(post(RSVPS).contentType(MediaType.APPLICATION_JSON).content(body.formatted(meeting, "MAYBE")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        rsvp("{\"attendeeEmail\": \"partner.contact@example.com\", \"partStat\": \"ACCEPTED\"}", 400);
        rsvp(body.formatted(UUID.randomUUID(), "DECLINED"), 404);

        assertThat(recorded.getResponse().getContentAsString())
                .containsPattern("\"status\"\\s*:\\s*\"ACCEPTED\"").contains("partner.contact@example.com");
    }
}
