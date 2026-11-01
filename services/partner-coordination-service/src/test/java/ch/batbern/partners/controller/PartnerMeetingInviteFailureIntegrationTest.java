package ch.batbern.partners.controller;

import ch.batbern.partners.client.EventManagementClient;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.dto.EventSummaryDTO;
import ch.batbern.partners.client.user.dto.UserResponse;
import ch.batbern.partners.domain.MeetingType;
import ch.batbern.partners.domain.PartnerMeeting;
import ch.batbern.partners.repository.PartnerMeetingRepository;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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

    @MockitoBean
    EventManagementClient eventManagementClient;
    @MockitoBean
    UserServiceClient userServiceClient;
    @MockitoSpyBean
    EmailService emailService;

    @BeforeEach
    void setUp() {
        meetingRepository.deleteAll();
        when(eventManagementClient.getEventSummary(anyString()))
                .thenReturn(new EventSummaryDTO("BATbern60", "Digitale Souveränität",
                        LocalDate.of(2026, 11, 6), LocalTime.of(16, 0), LocalTime.of(22, 0), "Bern"));
        var partner = new UserResponse();
        partner.setEmail("contact@partner.ch");
        when(userServiceClient.getUsersByRole("PARTNER")).thenReturn(List.of(partner));
        when(userServiceClient.getUsersByRole("ORGANIZER")).thenReturn(List.of());
    }

    @AfterEach
    void cleanUp() {
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
        doThrow(new IllegalArgumentException("Cannot send group mail: none of the 1 recipients is deliverable"))
                .when(emailService).sendGroupHtmlEmailWithAttachments(anyList(), any(), any(), anyList());

        mockMvc.perform(post("/api/v1/partner-meetings/" + meeting.getId() + "/send-invite")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isBadGateway());

        PartnerMeeting after = meetingRepository.findById(meeting.getId()).orElseThrow();
        assertThat(after.getInviteSentAt()).isNull();
        assertThat(after.getInviteSequence()).isEqualTo(meeting.getInviteSequence());
    }
}
