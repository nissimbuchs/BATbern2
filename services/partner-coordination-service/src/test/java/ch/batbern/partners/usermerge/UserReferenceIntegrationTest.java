package ch.batbern.partners.usermerge;

import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.MeetingType;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnerMeeting;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.repository.PartnerMeetingRepository;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * partner-coordination's part of the user-reference contract (Epic 17 entry 7b) against real
 * PostgreSQL: a profile merge moves who suggested a topic, who wrote a partner note and who created a
 * partner meeting from the source username to the target; a rerun moves nothing. Every test commits;
 * {@code @BeforeEach} and {@code @AfterEach} remove the rows. The request and answer shapes are
 * checked against the CUMS contract in {@code S2sProviderContractIntegrationTest}.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("User-reference contract: partner-coordination moves its usernames")
class UserReferenceIntegrationTest extends AbstractIntegrationTest {

    private static final String SOURCE = "merge7b.source";
    private static final String TARGET = "merge7b.target";
    private static final String OTHER = "merge7b.other";
    private static final String COMPANY = "merge7bco";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PartnerRepository partnerRepository;
    @Autowired
    private PartnerMeetingRepository meetingRepository;

    @BeforeEach
    void seed() {
        cleanUp();
        UUID partner = partnerRepository.save(Partner.builder().companyName(COMPANY)
                .partnershipLevel(PartnershipLevel.GOLD).partnershipStartDate(LocalDate.now().minusYears(1))
                .build()).getId();
        topic(SOURCE, "Topic by the source");
        topic(SOURCE, "Second topic by the source");
        topic(OTHER, "Topic by someone else");
        jdbc.update("INSERT INTO partner_notes (partner_id, title, content, author_username) VALUES (?, ?, ?, ?)",
                partner, "Call", "Spoke about 2027", SOURCE);
        jdbc.update("INSERT INTO partner_notes (partner_id, title, content, author_username) VALUES (?, ?, ?, ?)",
                partner, "Mail", "Sent the offer", TARGET);
        meetingRepository.save(PartnerMeeting.builder()
                .eventCode("BATbern9077").meetingType(MeetingType.SPRING).meetingDate(LocalDate.of(2027, 5, 14))
                .startTime(LocalTime.of(12, 0)).endTime(LocalTime.of(14, 0)).location("Kursaal")
                .createdBy(SOURCE).build());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM topic_suggestions WHERE suggested_by IN (?, ?, ?)", SOURCE, TARGET, OTHER);
        jdbc.update("DELETE FROM partner_meetings WHERE created_by IN (?, ?, ?)", SOURCE, TARGET, OTHER);
        partnerRepository.findByCompanyName(COMPANY).ifPresent(partnerRepository::delete);
    }

    private void topic(String by, String title) {
        jdbc.update("INSERT INTO topic_suggestions (company_name, suggested_by, title, source) VALUES (?, ?, ?, ?)",
                COMPANY, by, title, "PARTNER");
    }

    private int count(String sql, String username) {
        Integer n = jdbc.queryForObject(sql, Integer.class, username);
        return n == null ? 0 : n;
    }

    private String mergeBody() {
        return """
                {"sourceUsername": "%s", "targetUsername": "%s", "performedBy": "organizer.alice",
                 "trigger": "ORGANIZER", "sourceProfile": {"email": "source@example.com"}}"""
                .formatted(SOURCE, TARGET);
    }

    @Test
    @DisplayName("preview counts the source's rows per table.column and changes nothing; never blocks")
    void preview_countsTheSourcesRows() throws Exception {
        mockMvc.perform(get("/internal/user-references/preview").param("source", SOURCE).param("target", TARGET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moves['topic_suggestions.suggested_by']").value(2))
                .andExpect(jsonPath("$.moves['partner_notes.author_username']").value(1))
                .andExpect(jsonPath("$.moves['partner_meetings.created_by']").value(1))
                .andExpect(jsonPath("$.conflicts").isEmpty())
                .andExpect(jsonPath("$.blockers").isEmpty());

        assertThat(count("SELECT count(*) FROM topic_suggestions WHERE suggested_by = ?", SOURCE)).isEqualTo(2);
    }

    @Test
    @DisplayName("merge moves every row of the source to the target; others' rows stay; a rerun moves nothing")
    void merge_movesEveryRow_andIsIdempotent() throws Exception {
        mockMvc.perform(post("/internal/user-references/merge").contentType(MediaType.APPLICATION_JSON)
                        .content(mergeBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved['topic_suggestions.suggested_by']").value(2))
                .andExpect(jsonPath("$.moved['partner_notes.author_username']").value(1))
                .andExpect(jsonPath("$.moved['partner_meetings.created_by']").value(1))
                .andExpect(jsonPath("$.removed").isEmpty());

        assertThat(count("SELECT count(*) FROM topic_suggestions WHERE suggested_by = ?", TARGET)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM partner_notes WHERE author_username = ?", TARGET)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM partner_meetings WHERE created_by = ?", TARGET)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM topic_suggestions WHERE suggested_by = ?", OTHER)).isEqualTo(1);
        for (String table : List.of("topic_suggestions WHERE suggested_by", "partner_notes WHERE author_username",
                "partner_meetings WHERE created_by")) {
            assertThat(count("SELECT count(*) FROM " + table + " = ?", SOURCE)).as(table).isZero();
        }

        mockMvc.perform(post("/internal/user-references/merge").contentType(MediaType.APPLICATION_JSON)
                        .content(mergeBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved['topic_suggestions.suggested_by']").value(0))
                .andExpect(jsonPath("$.moved['partner_notes.author_username']").value(0))
                .andExpect(jsonPath("$.moved['partner_meetings.created_by']").value(0));
        assertThat(count("SELECT count(*) FROM topic_suggestions WHERE suggested_by = ?", TARGET)).isEqualTo(2);
    }

    @Test
    @DisplayName("source equal to target is 400 INVALID_MERGE, nothing moves")
    void merge_sameUsername_is400() throws Exception {
        mockMvc.perform(post("/internal/user-references/merge").contentType(MediaType.APPLICATION_JSON)
                        .content(mergeBody().replace(TARGET, SOURCE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_MERGE"));
        mockMvc.perform(get("/internal/user-references/preview").param("source", SOURCE).param("target", SOURCE))
                .andExpect(status().isBadRequest());
        assertThat(count("SELECT count(*) FROM topic_suggestions WHERE suggested_by = ?", SOURCE)).isEqualTo(2);
    }

    @Test
    @DisplayName("a new primary address moves nothing here")
    void primaryEmail_movesNothing() throws Exception {
        mockMvc.perform(post("/internal/user-references/primary-email").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "oldEmail": "old@example.com", "newEmail": "new@example.com"}"""
                                .formatted(TARGET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(0));
    }
}
