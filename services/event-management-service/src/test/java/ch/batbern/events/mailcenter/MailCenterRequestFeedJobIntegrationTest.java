package ch.batbern.events.mailcenter;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer.Call;
import ch.batbern.shared.test.contract.ContractServer.Reply;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Epic 17 entry 7 against real PostgreSQL: {@link MailCenterRequestFeedJob} pulls the CUMS request
 * feed through the real {@link MailCenterRequestFeedClient} on the {@link CumsContractHarness}, which
 * validates every request and every page against {@code cums-internal.openapi.yml}, and opens or
 * closes the conversations through the real {@link RequestConversations}. Not
 * {@code @Transactional}: the inbound store commits in its own transaction. The scheduler is off in
 * tests; the test runs {@link MailCenterRequestFeedJob#pull()} itself.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Mail Center pulls merge and logo requests from CUMS (Epic 17 entry 7)")
class MailCenterRequestFeedJobIntegrationTest extends AbstractIntegrationTest {

    private static final String FEED_PATH = "/internal/mail-center-requests";
    private static final UUID MERGE = UUID.fromString("7a1c0000-0000-4000-8000-000000001707");
    private static final UUID LOGO = UUID.fromString("7a1c0000-0000-4000-8000-000000001717");

    @Autowired
    private MailCenterRequestFeedJob job;
    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectProvider<MeterRegistry> meterRegistry;

    private final ObjectMapper json = new ObjectMapper();
    /** The pages CUMS answers, one per call, keyed by the {@code since} sent ({@code ""} for none). */
    private final Map<String, String> pages = Collections.synchronizedMap(new LinkedHashMap<>());

    @BeforeEach
    void setUp() {
        cleanUp();
        cums.answer(GET, FEED_PATH, this::page);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM mail_conversation WHERE merge_request_id = ? OR logo_request_id = ?", MERGE, LOGO);
        jdbc.update("DELETE FROM mail_message WHERE send_group_id IN (?, ?)",
                RequestConversations.Kind.MERGE.groupId(MERGE), RequestConversations.Kind.LOGO.groupId(LOGO));
        jdbc.update("DELETE FROM feed_cursors WHERE feed = ?", MailCenterRequestFeedJob.FEED);
        pages.clear();
        cums.reset();
    }

    // ------------------------------------------------------------------ opening and closing

    @Test
    @DisplayName("a new request: the first poll asks without a cursor, opens the conversation as before, stores the cursor")
    void openRequest_opensConversation() {
        pages.put("", page("c1", item("MERGE", MERGE, "OPEN", null, "Ich war bis 2024 bei Firma X.")));

        assertThat(job.pull()).isEqualTo(1);

        assertThat(feedCalls()).singleElement().satisfies(call -> {
            assertThat(call.queryParam("since")).isNull();
            assertThat(call.queryParam("limit")).isEqualTo("100");
        });
        Map<String, Object> row = jdbc.queryForMap("SELECT c.status, c.audience, c.event_code, c.counterpart_address,"
                + " c.subject, m.body_text FROM mail_conversation c JOIN mail_message m ON m.conversation_id = c.id"
                + " WHERE c.merge_request_id = ?", MERGE);
        assertThat(row.get("status")).isEqualTo("OPEN");
        assertThat(row.get("audience")).isEqualTo("SYSTEM");
        assertThat(row.get("event_code")).isNull();
        assertThat(row.get("counterpart_address")).isEqualTo("anna.private@example.com");
        assertThat(row.get("subject")).isEqualTo("Profil zusammenführen: Anna Muster");
        assertThat((String) row.get("body_text")).contains("Anna Muster").contains("Ich war bis 2024 bei Firma X.");
        assertThat(storedCursor()).isEqualTo("c1");
    }

    @Test
    @WithMockUser(username = "orga.nizer", roles = "ORGANIZER")
    @DisplayName("the Mail Center shows the request id on the pulled conversation")
    void openRequest_conversationCarriesRequestId() throws Exception {
        pages.put("", page("c1", item("LOGO", LOGO, "OPEN", null, null)));
        job.pull();
        String key = jdbc.queryForObject("SELECT conversation_key FROM mail_conversation WHERE logo_request_id = ?",
                String.class, LOGO);

        mockMvc.perform(get("/api/v1/conversations/{key}", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.logoRequestId").value(LOGO.toString()))
                .andExpect(jsonPath("$.conversation.mergeRequestId").doesNotExist());
    }

    @Test
    @DisplayName("a decided request: its open conversation turns DONE, by the organizer who decided")
    void decidedRequest_closesConversation() {
        pages.put("", page("c1", item("MERGE", MERGE, "OPEN", null, null)));
        job.pull();
        pages.put("c1", page("c2", item("MERGE", MERGE, "APPROVED", "orga.nizer", null)));

        assertThat(job.pull()).isEqualTo(1);

        assertThat(conversation(MERGE, "merge_request_id")).containsEntry("status", "DONE")
                .containsEntry("updated_by", "orga.nizer");
        assertThat(count("merge_request_id", MERGE)).isEqualTo(1);
        assertThat(storedCursor()).isEqualTo("c2");
    }

    @Test
    @DisplayName("opened and decided between two polls: opened, then closed in the same pass")
    void decidedWithoutConversation_isOpenedThenClosed() {
        pages.put("", page("c1", item("LOGO", LOGO, "DECLINED", "orga.nizer", "Neues Logo seit Juni")));

        assertThat(job.pull()).isEqualTo(1);

        assertThat(conversation(LOGO, "logo_request_id")).containsEntry("status", "DONE")
                .containsEntry("updated_by", "orga.nizer")
                .containsEntry("subject", "Neues Logo für ACME AG: Anna Muster");
        assertThat(jdbc.queryForObject("SELECT m.body_text FROM mail_message m JOIN mail_conversation c"
                + " ON m.conversation_id = c.id WHERE c.logo_request_id = ?", String.class, LOGO))
                .contains("Neues Logo seit Juni");
    }

    @Test
    @DisplayName("an IDN domain in the requester address is kept as it is (#1102)")
    void idnAddress_isKept() {
        Map<String, Object> item = item("MERGE", MERGE, "OPEN", null, null);
        item.put("requesterEmail", "juerg.brunner@bücherstube.example");
        pages.put("", page("c1", item));

        job.pull();

        assertThat(conversation(MERGE, "merge_request_id"))
                .containsEntry("counterpart_address", "juerg.brunner@bücherstube.example");
    }

    @ParameterizedTest(name = "requester replied after the decision: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("a decision read again closes a reopened conversation only when no mail came in after it")
    void redeliveredDecision_keepsARequesterReplyOpen(boolean replied) {
        pages.put("", page("c1", item("MERGE", MERGE, "OPEN", null, null)));
        job.pull();
        Map<String, Object> decided = item("MERGE", MERGE, "APPROVED", "orga.nizer", null);
        pages.put("c1", page("c2", decided));
        job.pull();
        assertThat(conversation(MERGE, "merge_request_id")).containsEntry("status", "DONE");
        // Reopened: by a reply of the requester after the decision (as InboundMailStore does it),
        // or, as the control, by hand without any new mail.
        jdbc.update("UPDATE mail_conversation SET status = 'OPEN'"
                + (replied ? ", last_inbound_at = now() + interval '1 second'" : "")
                + " WHERE merge_request_id = ?", MERGE);
        jdbc.update("DELETE FROM feed_cursors WHERE feed = ?", MailCenterRequestFeedJob.FEED);
        pages.put("", page("c2", decided));

        job.pull();

        assertThat(conversation(MERGE, "merge_request_id")).containsEntry("status", replied ? "OPEN" : "DONE");
    }

    @Test
    @DisplayName("requester profile gone: nothing is opened, but the decision still closes the conversation")
    void withoutAddress_opensNothing_butCloses() {
        Map<String, Object> logo = item("LOGO", LOGO, "OPEN", null, null);
        logo.put("requesterEmail", null);
        logo.put("requesterName", null);
        pages.put("", page("c1", item("MERGE", MERGE, "OPEN", null, null), logo));
        job.pull();
        assertThat(count("logo_request_id", LOGO)).isZero();
        Map<String, Object> decided = item("MERGE", MERGE, "DECLINED", "orga.nizer", null);
        decided.put("requesterEmail", null);
        decided.put("requesterName", null);
        pages.put("c1", page("c2", decided));

        job.pull();

        assertThat(conversation(MERGE, "merge_request_id")).containsEntry("status", "DONE");
    }

    @Test
    @DisplayName("a conversation stored but never linked to its request (crash between the steps) is linked")
    void unlinkedConversation_isLinkedOnTheNextOpen() {
        pages.put("", page("c1", item("MERGE", MERGE, "OPEN", null, null)));
        job.pull();
        String key = jdbc.queryForObject("SELECT conversation_key FROM mail_conversation WHERE merge_request_id = ?",
                String.class, MERGE);
        jdbc.update("UPDATE mail_conversation SET merge_request_id = NULL WHERE conversation_key = ?", key);
        jdbc.update("DELETE FROM feed_cursors WHERE feed = ?", MailCenterRequestFeedJob.FEED);

        job.pull();

        assertThat(jdbc.queryForObject("SELECT conversation_key FROM mail_conversation WHERE merge_request_id = ?",
                String.class, MERGE)).isEqualTo(key);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_message WHERE send_group_id = ?", Integer.class,
                RequestConversations.Kind.MERGE.groupId(MERGE))).isEqualTo(1);
    }

    // ------------------------------------------------------------------ cursor

    @Test
    @DisplayName("first poll without decided history: CUMS's start cursor is stored, nothing is opened")
    void firstPoll_storesStartCursor_opensNothing() {
        // CUMS starts the feed just before the oldest OPEN request, so a request decided before
        // that is never in a page (CUMS MailCenterRequestFeedIntegrationTest); here: an empty start.
        pages.put("", page("start"));

        assertThat(job.pull()).isZero();

        assertThat(storedCursor()).isEqualTo("start");
        assertThat(count("merge_request_id", MERGE) + count("logo_request_id", LOGO)).isZero();
    }

    @Test
    @DisplayName("replay: the same items read again change nothing")
    void replay_isIdempotent() {
        pages.put("", page("c1", item("MERGE", MERGE, "OPEN", null, null),
                item("LOGO", LOGO, "APPROVED", "orga.nizer", null)));
        job.pull();
        Timestamp closedAt = jdbc.queryForObject("SELECT updated_at FROM mail_conversation WHERE logo_request_id = ?",
                Timestamp.class, LOGO);
        // The cursor moved back: the same page comes again.
        jdbc.update("DELETE FROM feed_cursors WHERE feed = ?", MailCenterRequestFeedJob.FEED);

        assertThat(job.pull()).isEqualTo(2);

        assertThat(count("merge_request_id", MERGE)).isEqualTo(1);
        assertThat(count("logo_request_id", LOGO)).isEqualTo(1);
        assertThat(conversation(MERGE, "merge_request_id")).containsEntry("status", "OPEN");
        assertThat(conversation(LOGO, "logo_request_id")).containsEntry("status", "DONE")
                .containsEntry("updated_by", "orga.nizer");
        assertThat(jdbc.queryForObject("SELECT updated_at FROM mail_conversation WHERE logo_request_id = ?",
                Timestamp.class, LOGO)).isEqualTo(closedAt);
    }

    @Test
    @DisplayName("a stored cursor is sent as since; a full page is followed by the next one at once")
    void storedCursor_isResumed_andFullPagesAreFollowed() {
        jdbc.update("INSERT INTO feed_cursors (feed, cursor, updated_at) VALUES (?, 'c0', now())",
                MailCenterRequestFeedJob.FEED);
        List<Map<String, Object>> full = new ArrayList<>();
        for (int i = 0; i < MailCenterRequestFeedJob.PAGE_SIZE; i++) {
            full.add(item("MERGE", MERGE, "OPEN", null, null));
        }
        pages.put("c0", page("c1", full));
        pages.put("c1", page("c1"));

        assertThat(job.pull()).isEqualTo(MailCenterRequestFeedJob.PAGE_SIZE);

        assertThat(feedCalls()).extracting(call -> call.queryParam("since")).containsExactly("c0", "c1");
        assertThat(count("merge_request_id", MERGE)).isEqualTo(1);
        assertThat(storedCursor()).isEqualTo("c1");
    }

    @Test
    @DisplayName("CUMS answers 500: the cursor stays, the failure is counted, nothing escapes the job")
    void cumsDown_keepsCursor() {
        jdbc.update("INSERT INTO feed_cursors (feed, cursor, updated_at) VALUES (?, 'c0', now())",
                MailCenterRequestFeedJob.FEED);
        cums.answer(GET, FEED_PATH, call -> {
            throw HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", new HttpHeaders(),
                    new byte[0], StandardCharsets.UTF_8);
        });
        double before = failures();

        assertThat(job.pull()).isZero();

        assertThat(storedCursor()).isEqualTo("c0");
        assertThat(failures()).isEqualTo(before + 1);
    }

    // ------------------------------------------------------------------ helpers

    private Reply page(Call call) {
        String since = call.queryParam("since");
        String body = pages.get(since == null ? "" : since);
        if (body == null) {
            throw new AssertionError("no page for since=" + since);
        }
        return Reply.ok(body);
    }

    private static Map<String, Object> item(String kind, UUID id, String status, String decidedBy, String note) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("kind", kind);
        item.put("requestId", id.toString());
        item.put("status", status);
        item.put("requesterName", "Anna Muster");
        item.put("requesterEmail", "Anna.Private@Example.com");
        item.put("subject", "MERGE".equals(kind) ? "Profil zusammenführen: Anna Muster"
                : "Neues Logo für ACME AG: Anna Muster");
        item.put("note", note);
        item.put("decidedBy", decidedBy);
        item.put("changedAt", Instant.now().toString());
        return item;
    }

    @SafeVarargs
    private String page(String nextCursor, Map<String, Object>... items) {
        return page(nextCursor, List.of(items));
    }

    private String page(String nextCursor, List<Map<String, Object>> items) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("items", items);
        page.put("nextCursor", nextCursor);
        try {
            return json.writeValueAsString(page);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Call> feedCalls() {
        return cums.calls(GET, FEED_PATH);
    }

    private String storedCursor() {
        return jdbc.queryForObject("SELECT cursor FROM feed_cursors WHERE feed = ?", String.class,
                MailCenterRequestFeedJob.FEED);
    }

    private Map<String, Object> conversation(UUID id, String column) {
        return jdbc.queryForMap("SELECT status, updated_by, subject, counterpart_address FROM mail_conversation WHERE "
                + column + " = ?", id);
    }

    private int count(String column, UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM mail_conversation WHERE " + column + " = ?", Integer.class,
                id);
    }

    private double failures() {
        Counter counter = meterRegistry.getIfAvailable(() -> Metrics.globalRegistry)
                .find(MailCenterRequestFeedJob.FAILURES).counter();
        return counter == null ? 0 : counter.count();
    }
}
