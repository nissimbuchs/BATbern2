package ch.batbern.partners.controller;

import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.user.dto.UserResponse;
import ch.batbern.partners.domain.MeetingType;
import ch.batbern.partners.domain.PartnerMeeting;
import ch.batbern.partners.repository.PartnerMeetingRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.partners.mail.HttpOutboundMailRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.test.contract.ContractServer;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A partner meeting invite that could not be sent must not be shown as sent.
 *
 * <p>BATbern60, 2026-10-30: the send threw inside the old {@code @Async} dispatch, the error was only
 * logged, and the meeting showed "invite sent". The send is synchronous now: a failure answers
 * 502 to the organizer and rolls back {@code invite_sent_at} and the ICS sequence.
 *
 * <p>Deliberately NOT {@code @Transactional}: the assertion is about what the service's own
 * transaction commits, which a test-wide transaction would hide.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class PartnerMeetingInviteFailureIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    PartnerMeetingRepository meetingRepository;

    @Autowired
    RestTemplate restTemplate;
    @Autowired
    CacheManager cacheManager;
    /** EMS behind the real EventManagementClient, answering per ems-internal (Story 17.0b1, 17.0 deferred work). */
    ContractServer ems;
    @MockitoBean
    UserServiceClient userServiceClient;
    @Autowired
    SesRecorder sesRecorder;
    /** The real mail recorder; its POST /internal/mails goes to an EMS stand-in per ems-internal. */
    @Autowired
    HttpOutboundMailRecorder mailRecorder;
    ContractServer emsInternal;
    final List<String> recordedMails = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        meetingRepository.deleteAll();
        sesRecorder.reset();
        recordedMails.clear();
        emsInternal = ContractServer.bindTo(
                (RestTemplate) ReflectionTestUtils.getField(mailRecorder, "restTemplate"),
                "docs/api/internal/ems-internal.openapi.yml");
        emsInternal.expect(HttpMethod.POST, "/internal/mails").times(ExpectedCount.manyTimes())
                .respondPerRequest(call -> {
                    recordedMails.add(call.body());
                    return new ContractServer.Reply(HttpStatus.ACCEPTED, null);
                });
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        // EMS's internal schedule read (ems-internal spec); it needs no JWT (17.0 deferred work).
        ems = ContractServer.bindTo(restTemplate, "docs/api/internal/ems-internal.openapi.yml");
        ems.expect(HttpMethod.GET, "/internal/events/{eventCode}/schedule").times(ExpectedCount.manyTimes())
                .respondWith(HttpStatus.OK, """
                        {"eventCode": "BATbern60", "title": "Digitale Souveränität", "date": "2026-11-06T15:00:00Z",
                         "venueName": "Bern", "venueAddress": "Bern", "eventType": "EVENING",
                         "sessions": []}""");
        var partner = new UserResponse();
        partner.setEmail("success+contact@simulator.amazonses.com");
        when(userServiceClient.getUsersByRole("PARTNER")).thenReturn(List.of(partner));
        when(userServiceClient.getUsersByRole("ORGANIZER")).thenReturn(List.of());
    }

    @AfterEach
    void cleanUp() {
        ems.close();
        emsInternal.close();
        meetingRepository.deleteAll();
    }

    @Test
    void should_answer502AndKeepInviteUnsent_when_sendFails() throws Exception {
        PartnerMeeting meeting = meetingRepository.save(PartnerMeeting.builder()
                .eventCode("BATbern60")
                .meetingType(MeetingType.AUTUMN)
                .meetingDate(LocalDate.of(2026, 11, 6))
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(14, 0))
                .createdBy("organizer")
                .build());
        sesRecorder.failWith(() -> SesRecorder.sesError("MessageRejected", "Email address is not verified."));

        mockMvc.perform(post("/api/v1/partner-meetings/" + meeting.getId() + "/send-invite")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isBadGateway());

        PartnerMeeting after = meetingRepository.findById(meeting.getId()).orElseThrow();
        assertThat(after.getInviteSentAt()).isNull();
        assertThat(after.getInviteSequence()).isEqualTo(meeting.getInviteSequence());

        // The refused send is in EMS's mail log with SES's error code, nothing reached SES.
        assertThat(sesRecorder.mails()).isEmpty();
        assertThat(recordedMails).hasSize(1);
        JsonNode record = new ObjectMapper().readTree(recordedMails.get(0));
        assertThat(record.get("failureReason").asText()).isEqualTo("MessageRejected");
        assertThat(record.get("templateKey").asText()).isEqualTo("partner-meeting-invite");
        assertThat(record.get("to").asText()).isEqualTo("success+contact@simulator.amazonses.com");
    }
}
