package ch.batbern.partners.contract;

import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.MeetingType;
import ch.batbern.partners.domain.PartnerMeeting;
import ch.batbern.partners.repository.PartnerMeetingRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.OpenApiContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.batbern.shared.test.contract.ProviderContract.assertExamplesMatchSchema;
import static ch.batbern.shared.test.contract.ProviderContract.assertResponseMatchesSpec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider half of the S2S contract (Story 17.0b2): the one PCS operation EMS calls,
 * {@code POST /internal/partner-meetings/rsvps} (EMS {@code PartnerMeetingRsvpClientImpl}), runs
 * through the real controller and must match {@code docs/api/internal/pcs-internal.openapi.yml},
 * the spec EMS's contract test answers from.
 *
 * <p>PCS also implements the user-reference contract CUMS owns (Epic 17 entry 7b): those operations
 * are checked against {@code docs/api/internal/cums-internal.openapi.yml}, the spec the CUMS
 * contract caller is tested against.
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
    private static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";
    private static final String MERGE_SOURCE = "contract7b.source";
    private static final String MERGE_TARGET = "contract7b.target";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PartnerMeetingRepository meetingRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        meetingRepository.findAll().stream().filter(m -> "contract-0b2".equals(m.getCreatedBy()))
                .forEach(meetingRepository::delete);
        jdbc.update("DELETE FROM topic_suggestions WHERE suggested_by IN (?, ?)", MERGE_SOURCE, MERGE_TARGET);
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

    // ------------------------------------------------------------------ contract owned by CUMS (entry 7b)

    /** A contract request CUMS would send: valid against the CUMS spec, answered as that spec says. */
    private MvcResult contractCall(MockHttpServletRequestBuilder request, String method, String path,
            Map<String, List<String>> query, String body, int expectedStatus) throws Exception {
        if (expectedStatus < 300) {
            Map<String, List<String>> headers = body == null ? Map.of()
                    : Map.of("Content-Type", List.of("application/json"));
            assertThat(OpenApiContract.load(CUMS_INTERNAL).validateRequest(method, path, query, headers, body))
                    .as("a valid contract request").isEmpty();
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        assertResponseMatchesSpec(CUMS_INTERNAL, result);
        return result;
    }

    private MvcResult preview(String source, String target, int expectedStatus) throws Exception {
        Map<String, List<String>> query = new LinkedHashMap<>();
        MockHttpServletRequestBuilder request = get("/internal/user-references/preview");
        if (source != null) {
            query.put("source", List.of(source));
            request.param("source", source);
        }
        if (target != null) {
            query.put("target", List.of(target));
            request.param("target", target);
        }
        return contractCall(request, "GET", "/internal/user-references/preview", query, null, expectedStatus);
    }

    private MvcResult postContract(String path, String body, int expectedStatus) throws Exception {
        return contractCall(post(path).contentType(MediaType.APPLICATION_JSON).content(body), "POST", path, Map.of(),
                body, expectedStatus);
    }

    @ParameterizedTest(name = "forwarded JWT: {0}")
    @CsvSource({"true", "false"})
    @DisplayName("user-reference contract: preview, merge and primary email, as CUMS calls them, with and without the user's JWT")
    void userReferenceContract_matchesCumsSpec(boolean user) throws Exception {
        jdbc.update("INSERT INTO topic_suggestions (company_name, suggested_by, title, source) VALUES (?, ?, ?, ?)",
                "contractco", MERGE_SOURCE, "Contract topic", "PARTNER");
        if (user) {
            mockMvc.perform(get("/internal/user-references/preview").param("source", MERGE_SOURCE)
                            .param("target", MERGE_TARGET)
                            .with(jwt().jwt(j -> j.claim("custom:username", "organizer.alice")
                                    .claim("custom:role", "ORGANIZER"))))
                    .andExpect(status().isOk());
        }

        MvcResult previewed = preview(MERGE_SOURCE, MERGE_TARGET, 200);
        assertThat(previewed.getResponse().getContentAsString()).contains("topic_suggestions.suggested_by");
        preview(MERGE_TARGET, MERGE_TARGET, 400);
        preview(MERGE_SOURCE, null, 400);
        String merge = """
                {"sourceUsername": "%s", "targetUsername": "%s", "performedBy": "organizer.alice",
                 "trigger": "ORGANIZER", "sourceProfile": {"email": "contract.source@example.com"}}"""
                .formatted(MERGE_SOURCE, MERGE_TARGET);
        postContract("/internal/user-references/merge", merge, 200);
        postContract("/internal/user-references/merge", """
                {"sourceUsername": "%s", "targetUsername": "%s", "performedBy": "anna",
                 "trigger": "SELF_CLAIM", "sourceProfile": null}""".formatted(MERGE_SOURCE, MERGE_TARGET), 200);
        postContract("/internal/user-references/merge", "{\"sourceUsername\": \"x\"}", 400);
        postContract("/internal/user-references/primary-email", """
                {"username": "%s", "oldEmail": "old@example.com", "newEmail": "new@example.com"}"""
                .formatted(MERGE_TARGET), 200);
        postContract("/internal/user-references/primary-email", "{\"username\": \"x\"}", 400);
    }
}
