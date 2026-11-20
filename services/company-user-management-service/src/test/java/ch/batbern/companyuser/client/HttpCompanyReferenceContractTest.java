package ch.batbern.companyuser.client;

import ch.batbern.companyuser.client.CompanyReferenceContract.CallFailedException;
import ch.batbern.companyuser.client.CompanyReferenceContract.MergeCall;
import ch.batbern.companyuser.client.CompanyReferenceContract.MoveBlockedException;
import ch.batbern.companyuser.client.CompanyReferenceContract.Preview;
import ch.batbern.companyuser.client.CompanyReferenceContract.Result;
import ch.batbern.companyuser.client.CompanyReferenceContract.Trigger;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.util.List;
import java.util.Map;

import static ch.batbern.companyuser.client.CompanyReferenceHolders.COUNTS;
import static ch.batbern.companyuser.client.CompanyReferenceHolders.MERGE;
import static ch.batbern.companyuser.client.CompanyReferenceHolders.PREVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

/**
 * Contract test of {@link HttpCompanyReferenceContract} against the company-reference contract CUMS
 * owns ({@code docs/api/internal/cums-internal.openapi.yml}, #1251): every request is validated
 * against the spec, at each configured holder in order. Without a user no JWT is sent.
 */
@DisplayName("HttpCompanyReferenceContract honours the company-reference contract")
class HttpCompanyReferenceContractTest {

    private static final String A = "holder-a";
    private static final String B = "holder-b";

    private ContractServer holders;
    private HttpCompanyReferenceContract contract;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplateBuilder().build();
        holders = ContractServer.bindTo(restTemplate, CompanyReferenceHolders.CUMS_INTERNAL);
        contract = new HttpCompanyReferenceContract(restTemplate,
                List.of("http://" + A + ":8080", "http://" + B + ":8080/"));
    }

    @AfterEach
    void tearDown() {
        holders.close();
    }

    private static void noJwt(ClientHttpRequest request) {
        assertThat(request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    private static String body(ClientHttpRequest request) {
        return ((MockClientHttpRequest) request).getBodyAsString();
    }

    @Test
    void should_readEveryHoldersPreview_inOrder() {
        holders.expect(GET, PREVIEW).atHost(A).withQueryParam("source", "unicag").withQueryParam("target", "unic")
                .andExpect(HttpCompanyReferenceContractTest::noJwt).respondWithExample("nothingHeld");
        holders.expect(GET, PREVIEW).atHost(B).respondWithExample("blocked");

        List<Preview> previews = contract.preview("unicag", "unic");

        assertThat(previews).hasSize(2);
        assertThat(previews.get(0).moves()).containsExactly(Map.entry("registrations.attendee_company_id", 0));
        assertThat(previews.get(0).blockers()).isEmpty();
        assertThat(previews.get(1).moves()).containsEntry("partners.company_name", 1);
        assertThat(previews.get(1).blockers()).containsExactly("MERGE_BLOCKED_BOTH_PARTNERS");
        holders.verify();
    }

    @Test
    @DisplayName("preview keys are strictly encoded ('+' is not a space, '&' does not cut the query)")
    void should_encodePreviewKeysStrictly() {
        holders.expect(GET, PREVIEW).times(ExpectedCount.twice()).withQueryParam("source", "a+b&c=d")
                .withQueryParam("target", "unic")
                .andExpect(r -> assertThat(r.getURI().getRawQuery()).isEqualTo("source=a%2Bb%26c%3Dd&target=unic"))
                .respondWithExample("clean");

        assertThat(contract.preview("a+b&c=d", "unic")).allSatisfy(p -> assertThat(p.blockers()).isEmpty());
        holders.verify();
    }

    @Test
    void should_mapInvalidPreview_toCallFailed() {
        holders.expect(GET, PREVIEW).atHost(A).respondWithExample(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> contract.preview("unic", "unic"))
                .isInstanceOf(CallFailedException.class).hasMessageContaining("400");
        holders.verify();
    }

    private static MergeCall mergeCall() {
        return new MergeCall("unicag", "unic", "organizer.alice", Trigger.MERGE);
    }

    @Test
    void should_mergeAtEveryHolder_andReturnEachResult() {
        holders.expect(POST, MERGE).atHost(A).andExpect(r -> {
            noJwt(r);
            assertThat(body(r)).isEqualTo("{\"source\":\"unicag\",\"target\":\"unic\","
                    + "\"performedBy\":\"organizer.alice\",\"trigger\":\"MERGE\"}");
        }).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(B).respondWithExample("merged");

        List<Result> results = contract.merge(mergeCall());

        assertThat(results).hasSize(2);
        assertThat(results.get(0).moved()).containsExactly(Map.entry("registrations.attendee_company_id", 0));
        assertThat(results.get(0).movedKeys()).isEmpty();
        assertThat(results.get(1).moved()).containsEntry("partners.company_name", 1);
        assertThat(results.get(1).removed()).containsEntry("topic_votes.company_name", 1);
        assertThat(results.get(1).movedKeys().get("partners.company_name"))
                .containsExactly("c4e1a2b3-5d6f-4a7b-9c8d-0e1f2a3b4c5d");
        assertThat(results.get(1).removedKeys().get("topic_votes.company_name")).hasSize(1);
        holders.verify();
    }

    @Test
    void should_sendRenameTrigger() {
        holders.expect(POST, MERGE).times(ExpectedCount.twice())
                .andExpect(r -> assertThat(body(r)).contains("\"trigger\":\"RENAME\""))
                .respondWithExample("nothingHeld");

        contract.merge(new MergeCall("oldkey", "newkey", "organizer.alice", Trigger.RENAME));

        holders.verify();
    }

    @Test
    @DisplayName("409 becomes MoveBlockedException with the holder's message; later holders are not called")
    void should_throwMoveBlocked_when_aHolderAnswers409() {
        holders.expect(POST, MERGE).atHost(A).respondWithExample(HttpStatus.CONFLICT);
        holders.expect(POST, MERGE).atHost(B).times(ExpectedCount.never()).respondWithExample();

        assertThatThrownBy(() -> contract.merge(mergeCall()))
                .isInstanceOf(MoveBlockedException.class)
                .hasMessage("Both companies are partners (unicag, unic); merge the partners first");
        holders.verify();
    }

    @Test
    @DisplayName("a holder that does not answer fails the merge, naming it")
    void should_failMerge_when_aHolderIsDown() {
        holders.expect(POST, MERGE).atHost(A).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(B).respondWith(request -> {
            throw new ConnectException("connection refused");
        });

        assertThatThrownBy(() -> contract.merge(mergeCall()))
                .isInstanceOf(CallFailedException.class).hasMessageContaining(B);
        holders.verify();
    }

    @Test
    @DisplayName("counts: one call per holder with every key, summed per key and table.column")
    void should_sumEveryHoldersCounts_perKey() {
        holders.expect(POST, COUNTS).atHost(A).andExpect(r -> {
            noJwt(r);
            assertThat(body(r)).isEqualTo("{\"keys\":[\"unic\",\"unicag\",\"puzzle\"]}");
        }).respondWithExample("registrations");
        // Holder B answers a column holder A answered too: the two are summed, not overwritten
        holders.expect(POST, COUNTS).atHost(B).respondWith(HttpStatus.OK, "{\"counts\": {\"unic\": "
                + "{\"registrations.attendee_company_id\": 6, \"partners.company_name\": 1, "
                + "\"topic_votes.company_name\": 5}, \"unicag\": {\"topic_votes.company_name\": 1}}}");

        Map<String, Map<String, Integer>> counts = contract.counts(List.of("unic", "unicag", "puzzle"));

        assertThat(counts.get("unic")).containsEntry(CompanyReferenceContract.REGISTRATIONS, 20)
                .containsEntry(CompanyReferenceContract.PARTNERS, 1)
                .containsEntry("topic_votes.company_name", 5);
        assertThat(counts.get("unicag")).containsEntry(CompanyReferenceContract.REGISTRATIONS, 3)
                .containsEntry("topic_votes.company_name", 1)
                .doesNotContainKey(CompanyReferenceContract.PARTNERS);
        assertThat(counts).doesNotContainKey("puzzle");
        holders.verify();
    }

    @Test
    @DisplayName("counts: a holder that does not answer fails the call; no keys asks nobody")
    void should_failCounts_when_aHolderIsDown() {
        holders.expect(POST, COUNTS).atHost(A).respondWithExample("nothingHeld");
        holders.expect(POST, COUNTS).atHost(B).respondWith(request -> {
            throw new ConnectException("connection refused");
        });

        assertThatThrownBy(() -> contract.counts(List.of("unic")))
                .isInstanceOf(CallFailedException.class).hasMessageContaining(B);
        assertThat(contract.counts(List.of())).isEmpty();
        holders.verify();
    }

    @Test
    @DisplayName("no holder configured: preview and merge fail instead of a move that would orphan every row")
    void should_refusePreviewAndMerge_when_noHolderIsConfigured() {
        HttpCompanyReferenceContract none = new HttpCompanyReferenceContract(new RestTemplateBuilder().build(),
                List.of());

        assertThatThrownBy(() -> none.preview("unicag", "unic"))
                .isInstanceOf(CallFailedException.class).hasMessageContaining("no holder");
        assertThatThrownBy(() -> none.merge(mergeCall()))
                .isInstanceOf(CallFailedException.class).hasMessageContaining("no holder");
        assertThatThrownBy(() -> none.counts(List.of("unic")))
                .isInstanceOf(CallFailedException.class).hasMessageContaining("no holder");
    }
}
