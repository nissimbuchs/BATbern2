package ch.batbern.partners.security;

import ch.batbern.partners.config.CumsInternalUsers;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.MeetingType;
import ch.batbern.partners.domain.PartnerMeeting;
import ch.batbern.partners.mail.HttpOutboundMailRecorder;
import ch.batbern.partners.repository.PartnerMeetingRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.test.security.RoleStubTables;
import ch.batbern.shared.test.security.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agentic interaction story 7 (AD-15): the partner meeting invite and the cancellation of an invited
 * meeting mail every partner contact and organizer, so an agent token gets 403 there and nothing is
 * sent or changed. The organizer's Cognito-shaped token (real {@link JwtAuthenticationConverter}) is the
 * positive control in each test. Deleting a meeting before its invite mails nobody and stays open.
 * PCS answers every 403 with the generic "Access denied" (its handler never echoes the exception), so
 * the refusal is told apart from a role refusal by the positive control: same organizer, same request.
 *
 * <p>Real agent tokens ({@link TestTokens}) through the production chain and decoder. CUMS and EMS
 * answer at the wire ({@link ContractServer}); the mail record goes to an EMS stand-in on the real
 * {@link HttpOutboundMailRecorder}. PCS owns no {@code user_profiles} or {@code role_assignments}: this
 * test creates both as stubs ({@link RoleStubTables}), so the agent's roles resolve by its {@code sub}
 * as in production.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("PCS: audience sends refuse agent tokens (AD-15)")
class AudienceGuardIntegrationTest extends AbstractIntegrationTest {

    private static final String ORGANIZER = "olga.guard";
    private static final String ORGANIZER_SUB = "b0000000-0000-4000-8000-000000000707";
    private static final String ORGANIZER_MAIL = "success+guard-org@simulator.amazonses.com";
    private static final String PARTNER = "pia.guard";
    private static final String PARTNER_MAIL = "success+guard-partner@simulator.amazonses.com";
    private static final String CREATED_BY = "guard-test:" + ORGANIZER;
    private static final String RUN = "run-pcs-guard";
    private static final String EMS_INTERNAL = "docs/api/internal/ems-internal.openapi.yml";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private PartnerMeetingRepository meetingRepository;
    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private HttpOutboundMailRecorder mailRecorder;

    private ContractServer peers;
    private ContractServer emsMailRecords;
    private final List<String> recordedMails = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        RoleStubTables.create(jdbc);
        cleanUp();
        sesRecorder.reset();
        recordedMails.clear();
        RoleStubTables.grant(jdbc, ORGANIZER, ORGANIZER_SUB, "ORGANIZER");

        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        peers = ContractServer.bindTo(restTemplate, EMS_INTERNAL, CumsInternalUsers.CUMS_INTERNAL);
        peers.expect(HttpMethod.GET, "/internal/events/{eventCode}/schedule").times(ExpectedCount.manyTimes())
                .respondWith(HttpStatus.OK, """
                        {"eventCode": "BATbern57", "title": "BATbern Spring 2026", "date": "2026-05-14T16:00:00Z",
                         "venueName": "Bern Congress Centre", "venueAddress": "Bern", "eventType": "EVENING",
                         "startTime": "2026-05-14T16:00:00Z", "endTime": "2026-05-14T20:00:00Z"}""");
        Map<String, String> byRole = Map.of(
                "PARTNER", userWithMail(PARTNER, "AlphaAG", "Pia", PARTNER_MAIL),
                "ORGANIZER", userWithMail(ORGANIZER, null, "Olga", ORGANIZER_MAIL));
        peers.expect(HttpMethod.GET, "/internal/users").times(ExpectedCount.manyTimes())
                .respondPerRequest(call -> ContractServer.Reply.ok(page(byRole.get(call.queryParam("role")))));

        emsMailRecords = ContractServer.bindTo(
                (RestTemplate) ReflectionTestUtils.getField(mailRecorder, "restTemplate"), EMS_INTERNAL);
        emsMailRecords.expect(HttpMethod.POST, "/internal/mails").times(ExpectedCount.manyTimes())
                .respondPerRequest(call -> {
                    recordedMails.add(call.body());
                    return new ContractServer.Reply(HttpStatus.ACCEPTED, null);
                });
    }

    @AfterEach
    void cleanUp() {
        sesRecorder.awaitAsyncSends();
        if (peers != null) {
            peers.close();
            peers = null;
        }
        if (emsMailRecords != null) {
            emsMailRecords.close();
            emsMailRecords = null;
        }
        jdbc.update("DELETE FROM partner_meetings WHERE created_by = ?", CREATED_BY);
        RoleStubTables.remove(jdbc, ORGANIZER_SUB);
    }

    @Test
    @DisplayName("an agent's invite answers 403 and leaves the meeting; the person's invite reaches the partner")
    void should_refuseAgent_andInviteForPerson_when_sendInvite() throws Exception {
        UUID meetingId = meeting(null);

        mockMvc.perform(post("/api/v1/partner-meetings/{id}/send-invite", meetingId)
                        .header(HttpHeaders.AUTHORIZATION, agentBearer()))
                .andExpect(status().isForbidden());

        sesRecorder.awaitAsyncSends();
        PartnerMeeting unchanged = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(unchanged.getInviteSentAt()).isNull();
        assertThat(unchanged.getInviteSequence()).isZero();
        assertThat(sesRecorder.mails()).isEmpty();
        assertThat(recordedMails).isEmpty();

        mockMvc.perform(post("/api/v1/partner-meetings/{id}/send-invite", meetingId).with(person()))
                .andExpect(status().isAccepted());

        assertThat(sesRecorder.mailsTo(PARTNER_MAIL)).hasSize(1);
        assertThat(meetingRepository.findById(meetingId).orElseThrow().getInviteSentAt()).isNotNull();
    }

    @Test
    @DisplayName("an agent's delete of an invited meeting answers 403 and keeps it; the person's sends the cancel")
    void should_refuseAgent_andCancelForPerson_when_invitedMeetingDeleted() throws Exception {
        UUID meetingId = meeting(Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(delete("/api/v1/partner-meetings/{id}", meetingId)
                        .header(HttpHeaders.AUTHORIZATION, agentBearer()))
                .andExpect(status().isForbidden());

        sesRecorder.awaitAsyncSends();
        assertThat(meetingRepository.existsById(meetingId)).isTrue();
        assertThat(sesRecorder.mails()).isEmpty();
        assertThat(recordedMails).isEmpty();

        mockMvc.perform(delete("/api/v1/partner-meetings/{id}", meetingId).with(person()))
                .andExpect(status().isNoContent());

        sesRecorder.awaitAsyncSends();
        assertThat(meetingRepository.existsById(meetingId)).isFalse();
        assertThat(sesRecorder.mailsTo(PARTNER_MAIL)).hasSize(1);
        assertThat(recordedMails).isNotEmpty();
    }

    @Test
    @DisplayName("an agent may delete a meeting whose invite was never sent: nobody is mailed")
    void should_deleteForAgent_when_meetingNotInvited() throws Exception {
        UUID meetingId = meeting(null);

        mockMvc.perform(delete("/api/v1/partner-meetings/{id}", meetingId)
                        .header(HttpHeaders.AUTHORIZATION, agentBearer()))
                .andExpect(status().isNoContent());

        sesRecorder.awaitAsyncSends();
        assertThat(meetingRepository.existsById(meetingId)).isFalse();
        assertThat(sesRecorder.mails()).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String agentBearer() {
        return "Bearer " + TestTokens.agentToken(ORGANIZER_SUB, ORGANIZER, RUN, "organizer");
    }

    /** The organizer signed in as a person: Cognito-shaped claims through the real role mapping. */
    private JwtRequestPostProcessor person() {
        Converter<Jwt, Collection<GrantedAuthority>> realConversion =
                token -> jwtAuthenticationConverter.convert(token).getAuthorities();
        return jwt()
                .jwt(token -> token.subject(ORGANIZER_SUB)
                        .claim("custom:role", "ORGANIZER")
                        .claim("custom:username", ORGANIZER))
                .authorities(realConversion);
    }

    private UUID meeting(Instant inviteSentAt) {
        return meetingRepository.saveAndFlush(PartnerMeeting.builder()
                .eventCode("BATbern57")
                .meetingType(MeetingType.SPRING)
                .meetingDate(LocalDate.now().plusDays(40))
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(14, 0))
                .location("Lunch Hall")
                .inviteSentAt(inviteSentAt)
                .inviteSequence(inviteSentAt == null ? 0 : 1)
                .createdBy(CREATED_BY)
                .build()).getId();
    }

    /** A cums-internal {@code UserResponse} with a mailbox-simulator address. */
    private static String userWithMail(String id, String companyId, String firstName, String email) {
        return CumsInternalUsers.user(id, companyId, firstName, "Guard").replace(id + "@example.com", email);
    }

    private static String page(String user) {
        return """
                {"data": [%s], "pagination": {"page": 1, "limit": 100, "totalItems": 1, "totalPages": 1,
                 "hasNext": false, "hasPrev": false}}""".formatted(user);
    }
}
