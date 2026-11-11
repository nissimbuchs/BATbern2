package ch.batbern.partners.contract;

import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.MeetingType;
import ch.batbern.partners.domain.PartnerMeeting;
import ch.batbern.partners.repository.PartnerMeetingRepository;
import ch.batbern.partners.repository.PartnerMeetingRsvpRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider half of the contracts partner-coordination implements (ADR-016 decision 5). PCS serves
 * no internal operation of its own ({@code docs/api/internal/pcs-internal.openapi.yml} has no path):
 * every call into it is a contract its upstream owns, checked here against the owner's spec, the
 * spec the owner's contract caller is tested against.
 *
 * <ul>
 *   <li>The calendar reply contract EMS owns (Epic 17 entry 8b): {@code POST /internal/calendar-replies}
 *       in {@code docs/api/internal/ems-internal.openapi.yml} (EMS {@code HttpCalendarReplyContract}).
 *       It replaced {@code POST /internal/partner-meetings/rsvps} (Story 17.0b2).</li>
 *   <li>The user-reference contract CUMS owns (Epic 17 entry 7b): {@code /internal/user-references/*}
 *       in {@code docs/api/internal/cums-internal.openapi.yml}.</li>
 * </ul>
 *
 * <p>Until Epic 17 entry 8 this class also proved the two public reads EMS's
 * {@code PartnerApiClientImpl} made for the topic canvas; the canvas now loads them in the browser
 * and EMS calls no public PCS operation.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("PCS honours the contracts its upstreams own")
class S2sProviderContractIntegrationTest extends AbstractIntegrationTest {

    private static final String EMS_INTERNAL = "docs/api/internal/ems-internal.openapi.yml";
    private static final String CALENDAR_REPLIES = "/internal/calendar-replies";
    private static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";
    private static final String MERGE_SOURCE = "contract7b.source";
    private static final String MERGE_TARGET = "contract7b.target";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PartnerMeetingRepository meetingRepository;
    @Autowired
    private PartnerMeetingRsvpRepository rsvpRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        meetingRepository.findAll().stream().filter(m -> "contract-8b".equals(m.getCreatedBy()))
                .forEach(meetingRepository::delete);
        jdbc.update("DELETE FROM topic_suggestions WHERE suggested_by IN (?, ?)", MERGE_SOURCE, MERGE_TARGET);
    }

    @Test
    void should_haveValidExamples_forContractOperations() {
        assertExamplesMatchSchema(EMS_INTERNAL, "POST", CALENDAR_REPLIES);
    }

    // ------------------------------------------------------------------ contract owned by EMS (entry 8b)

    /** A calendar reply as EMS sends it: valid against the EMS spec, answered as that spec says. */
    private MvcResult calendarReply(String body, boolean user, int expectedStatus) throws Exception {
        assertThat(OpenApiContract.load(EMS_INTERNAL).validateRequest("POST", CALENDAR_REPLIES, Map.of(),
                Map.of("Content-Type", List.of("application/json")), body)).as("a valid contract request").isEmpty();
        MockHttpServletRequestBuilder request = post(CALENDAR_REPLIES).contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (user) {
            request.with(jwt().jwt(j -> j.claim("custom:username", "organizer.alice")
                    .claim("custom:role", "ORGANIZER")));
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        assertResponseMatchesSpec(EMS_INTERNAL, result);
        return result;
    }

    @ParameterizedTest(name = "forwarded JWT: {0}")
    @CsvSource({"true", "false"})
    @DisplayName("calendar reply contract: 204 recorded at once, 400 unknown partStat, 404 unknown meeting")
    void calendarReplyContract_matchesEmsSpec(boolean user) throws Exception {
        UUID meeting = meetingRepository.save(PartnerMeeting.builder()
                .eventCode("BATbern9017").meetingType(MeetingType.SPRING).meetingDate(LocalDate.of(2026, 5, 14))
                .startTime(LocalTime.of(12, 0)).endTime(LocalTime.of(14, 0)).location("Kursaal")
                .createdBy("contract-8b").build()).getId();
        String body = """
                {"meetingId": "%s", "attendeeEmail": "Partner.Contact@Example.com", "partStat": "%s"}""";

        calendarReply(body.formatted(meeting, "ACCEPTED"), user, 204);
        assertThat(rsvpRepository.findByMeetingIdAndAttendeeEmailIgnoreCase(meeting, "partner.contact@example.com"))
                .get().extracting(r -> r.getStatus().name()).isEqualTo("ACCEPTED");
        calendarReply(body.formatted(meeting, "DECLINED"), user, 204);
        assertThat(rsvpRepository.findByMeetingIdAndAttendeeEmailIgnoreCase(meeting, "partner.contact@example.com"))
                .get().extracting(r -> r.getStatus().name()).isEqualTo("DECLINED");

        MvcResult unknownPartStat = calendarReply(body.formatted(meeting, "NEEDS-ACTION"), user, 400);
        assertThat(unknownPartStat.getResponse().getContentAsString()).contains("Unknown partStat: NEEDS-ACTION");
        calendarReply(body.formatted(UUID.randomUUID(), "DECLINED"), user, 404);
        // A body the contract rejects (meetingId missing) is a 400 too, with the shared ErrorResponse
        MvcResult invalid = mockMvc.perform(post(CALENDAR_REPLIES).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendeeEmail\": \"partner.contact@example.com\", \"partStat\": \"ACCEPTED\"}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertResponseMatchesSpec(EMS_INTERNAL, invalid);
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
