package ch.batbern.companyuser.service;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Epic 17 entry 7 against real PostgreSQL: {@code GET /internal/mail-center-requests}, the change
 * feed event-management's Mail Center pulls (V30 trigger, {@link MailCenterRequestFeedService}).
 * Rows are backdated to fixed instants in 2000 so rows other tests left in the shared database
 * cannot sit between them. The settle window is 0 in tests; one test builds the service with 30 s.
 * Not {@code @Transactional}: the rows commit and are removed after each test.
 */
@Import(TestAwsConfig.class)
@DisplayName("Mail Center request feed (Epic 17 entry 7)")
class MailCenterRequestFeedIntegrationTest extends AbstractIntegrationTest {

    private static final String FEED = "/internal/mail-center-requests";
    private static final String REQUESTER = "feed.anna";
    private static final String REQUESTER_EMAIL = "feed.anna@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> created = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanUp();
        userRepository.saveAndFlush(User.builder().username(REQUESTER).email(REQUESTER_EMAIL)
                .firstName("Anna").lastName("Feed").cognitoUserId("cog-feed-anna")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
        companyRepository.saveAndFlush(Company.builder().name("feedco").displayName("Feed Co AG")
                .createdBy("orga.nizer").createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    @AfterEach
    void cleanUp() {
        for (UUID id : created) {
            jdbc.update("DELETE FROM profile_merge_requests WHERE id = ?", id);
            jdbc.update("DELETE FROM company_logo_requests WHERE id = ?", id);
        }
        created.clear();
        userRepository.findByUsername(REQUESTER).ifPresent(userRepository::delete);
        companyRepository.findByName("feedco").ifPresent(companyRepository::delete);
    }

    // ------------------------------------------------------------------ the V30 cursor

    @Test
    @DisplayName("the trigger sets changed_at on insert and on a decision, not on other updates")
    void trigger_setsChangedAt_onInsertAndDecision() {
        UUID id = merge("OPEN", null);
        Instant created = changedAt("profile_merge_requests", id);
        assertThat(created).isNotNull();

        jdbc.update("UPDATE profile_merge_requests SET note = 'edited' WHERE id = ?", id);
        assertThat(changedAt("profile_merge_requests", id)).isEqualTo(created);

        jdbc.update("UPDATE profile_merge_requests SET status = 'APPROVED', decided_by = 'orga.nizer',"
                + " decided_at = now() WHERE id = ?", id);
        assertThat(changedAt("profile_merge_requests", id)).isAfter(created);
    }

    // ------------------------------------------------------------------ the feed

    @Test
    @DisplayName("both kinds in change order, with requester, subject, note and the decision")
    void feed_bothKinds_inChangeOrder() throws Exception {
        UUID logo = logo("OPEN", null, "Neues Logo seit Juni");
        UUID merge = merge("DECLINED", "orga.nizer");
        backdate("company_logo_requests", logo, "2000-01-01T10:00:00Z");
        backdate("profile_merge_requests", merge, "2000-01-01T11:00:00Z");

        List<JsonNode> items = mine(page(since("2000-01-01T00:00:00Z"), 200), logo, merge);

        assertThat(items).extracting(i -> i.path("requestId").asText())
                .containsExactly(logo.toString(), merge.toString());
        JsonNode first = items.get(0);
        assertThat(first.path("kind").asText()).isEqualTo("LOGO");
        assertThat(first.path("status").asText()).isEqualTo("OPEN");
        assertThat(first.path("requesterName").asText()).isEqualTo("Anna Feed");
        assertThat(first.path("requesterEmail").asText()).isEqualTo(REQUESTER_EMAIL);
        assertThat(first.path("subject").asText()).isEqualTo("Neues Logo für Feed Co AG: Anna Feed");
        assertThat(first.path("note").asText()).isEqualTo("Neues Logo seit Juni");
        assertThat(first.path("decidedBy").isNull()).isTrue();
        assertThat(first.path("changedAt").asText()).isEqualTo("2000-01-01T10:00:00Z");
        JsonNode second = items.get(1);
        assertThat(second.path("kind").asText()).isEqualTo("MERGE");
        assertThat(second.path("status").asText()).isEqualTo("DECLINED");
        assertThat(second.path("subject").asText()).isEqualTo("Profil zusammenführen: Anna Feed");
        assertThat(second.path("note").isNull()).isTrue();
        assertThat(second.path("decidedBy").asText()).isEqualTo("orga.nizer");
    }

    @Test
    @DisplayName("limit pages the feed; the next cursor resumes after the last item, nothing twice")
    void cursor_resumesAfterLastItem() throws Exception {
        UUID a = merge("OPEN", null);
        UUID b = logo("OPEN", null, null);
        backdate("profile_merge_requests", a, "2000-01-02T10:00:00Z");
        backdate("company_logo_requests", b, "2000-01-02T10:00:00Z"); // same instant: id breaks the tie
        String start = since("2000-01-02T00:00:00Z");

        JsonNode first = page(start, 1);
        JsonNode second = page(first.path("nextCursor").asText(), 1);

        List<String> both = List.of(first.path("items").get(0).path("requestId").asText(),
                second.path("items").get(0).path("requestId").asText());
        assertThat(both).containsExactlyInAnyOrder(a.toString(), b.toString());
        JsonNode rest = page(second.path("nextCursor").asText(), 200);
        assertThat(rest.path("items").findValuesAsText("requestId")).doesNotContain(a.toString(), b.toString());
    }

    @Test
    @DisplayName("an empty page returns the cursor it was given, so the caller stays where it was")
    void emptyPage_keepsCursor() throws Exception {
        String future = new MailCenterRequestFeedService.Cursor(Instant.parse("2999-01-01T00:00:00Z"),
                new UUID(0, 0)).encode();

        JsonNode page = page(future, 100);

        assertThat(page.path("items")).isEmpty();
        assertThat(page.path("nextCursor").asText()).isEqualTo(future);
    }

    @Test
    @DisplayName("a decision is a new change: delivered again after the cursor of the open request")
    void decision_isDeliveredAfterTheOpenRequest() throws Exception {
        UUID id = merge("OPEN", null);
        JsonNode opened = page(since("2000-01-01T00:00:00Z"), 200);
        assertThat(opened.path("items").findValuesAsText("requestId")).contains(id.toString());

        jdbc.update("UPDATE profile_merge_requests SET status = 'APPROVED', decided_by = 'orga.nizer',"
                + " decided_at = now() WHERE id = ?", id);

        List<JsonNode> decided = mine(page(opened.path("nextCursor").asText(), 200), id);
        assertThat(decided).singleElement().satisfies(i -> {
            assertThat(i.path("status").asText()).isEqualTo("APPROVED");
            assertThat(i.path("decidedBy").asText()).isEqualTo("orga.nizer");
        });
    }

    @Test
    @DisplayName("first poll: starts at the oldest open request; decided history before it is not delivered")
    void firstPoll_startsAtOldestOpen() throws Exception {
        UUID history = merge("APPROVED", "orga.nizer");
        UUID open = logo("OPEN", null, null);
        UUID later = merge("DECLINED", "orga.nizer");
        backdate("profile_merge_requests", history, "1990-01-01T10:00:00Z");
        backdate("company_logo_requests", open, "1990-01-02T10:00:00Z");
        backdate("profile_merge_requests", later, "1990-01-03T10:00:00Z");

        List<JsonNode> items = mine(page(null, 200), history, open, later);

        assertThat(items).extracting(i -> i.path("requestId").asText())
                .containsExactly(open.toString(), later.toString());
    }

    @Test
    @DisplayName("first poll with no open request: starts now, delivers no history, still hands out a cursor")
    void firstPoll_withoutOpenRequest_startsNow() {
        // Closes every open request in the shared database, so it runs in a transaction that is
        // rolled back; the feed call goes straight to the service in that transaction.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            noOpenRequest();
        });
    }

    private void noOpenRequest() {
        jdbc.update("UPDATE profile_merge_requests SET status = 'DECLINED', decided_by = 'test', decided_at = now()"
                + " WHERE status = 'OPEN'");
        jdbc.update("UPDATE company_logo_requests SET status = 'DECLINED', decided_by = 'test', decided_at = now()"
                + " WHERE status = 'OPEN'");
        jdbc.update("UPDATE profile_merge_requests SET changed_at = '1990-01-01T00:00:00Z'");
        jdbc.update("UPDATE company_logo_requests SET changed_at = '1990-01-01T00:00:00Z'");

        MailCenterRequestFeedService.Page page = new MailCenterRequestFeedService(namedJdbc, 0).page(null, 200);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNotBlank();
    }

    @Test
    @DisplayName("a request whose requester profile is gone is not delivered")
    void missingRequester_isSkipped() throws Exception {
        UUID id = merge("OPEN", null);
        jdbc.update("UPDATE profile_merge_requests SET requester_username = 'gone.person' WHERE id = ?", id);

        assertThat(page(since("2000-01-01T00:00:00Z"), 200).path("items").findValuesAsText("requestId"))
                .doesNotContain(id.toString());
    }

    @Test
    @DisplayName("settle window: a row younger than 30 s is held back, an older one is delivered")
    void settleWindow_holdsBackFreshRows() {
        MailCenterRequestFeedService settled = new MailCenterRequestFeedService(namedJdbc, 30);
        UUID fresh = merge("OPEN", null);
        UUID settledRow = logo("OPEN", null, null);
        jdbc.update("UPDATE company_logo_requests SET changed_at = clock_timestamp() - interval '60 seconds'"
                + " WHERE id = ?", settledRow);

        MailCenterRequestFeedService.Page page = settled.page(since("2000-01-01T00:00:00Z"), 200);

        List<UUID> ids = page.items().stream().map(MailCenterRequestFeedService.Item::requestId).toList();
        assertThat(ids).contains(settledRow).doesNotContain(fresh);
    }

    @Test
    @DisplayName("400 for an unreadable cursor or a limit outside 1 to 200")
    void badRequests() throws Exception {
        mockMvc.perform(get(FEED).param("since", "not-a-cursor")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        mockMvc.perform(get(FEED).param("limit", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get(FEED).param("limit", "201")).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ helpers

    private UUID merge(String status, String decidedBy) {
        return track(jdbc.queryForObject("INSERT INTO profile_merge_requests (requester_username, source_username, status,"
                + " decided_by, decided_at) VALUES (?, 'feed.old', ?, ?, ?) RETURNING id", UUID.class, REQUESTER, status,
                decidedBy, decidedBy == null ? null : Timestamp.from(Instant.now())));
    }

    private UUID track(UUID id) {
        created.add(id);
        return id;
    }

    private UUID logo(String status, String decidedBy, String note) {
        return track(jdbc.queryForObject("INSERT INTO company_logo_requests (requester_username, company_name,"
                + " new_logo_upload_id, new_logo_url, note, status, decided_by, decided_at)"
                + " VALUES (?, 'feedco', 'up-feed', 'https://cdn.batbern.ch/logos/requests/up-feed/logo.png', ?, ?, ?, ?)"
                + " RETURNING id", UUID.class, REQUESTER, note, status, decidedBy,
                decidedBy == null ? null : Timestamp.from(Instant.now())));
    }

    private void backdate(String table, UUID id, String instant) {
        jdbc.update("UPDATE " + table + " SET changed_at = ?::timestamptz WHERE id = ?", instant, id);
    }

    private Instant changedAt(String table, UUID id) {
        return jdbc.queryForObject("SELECT changed_at FROM " + table + " WHERE id = ?", Timestamp.class, id)
                .toInstant();
    }

    private static String since(String instant) {
        return new MailCenterRequestFeedService.Cursor(Instant.parse(instant), new UUID(0, 0)).encode();
    }

    private JsonNode page(String since, int limit) throws Exception {
        MockHttpServletRequestBuilder request = get(FEED).param("limit", String.valueOf(limit));
        if (since != null) {
            request.param("since", since);
        }
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** The items of these requests, in feed order (other tests' rows may be in the page too). */
    private static List<JsonNode> mine(JsonNode page, UUID... ids) {
        Set<String> wanted = new HashSet<>();
        for (UUID id : ids) {
            wanted.add(id.toString());
        }
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode item : page.path("items")) {
            if (wanted.contains(item.path("requestId").asText())) {
                out.add(item);
            }
        }
        return out;
    }
}
