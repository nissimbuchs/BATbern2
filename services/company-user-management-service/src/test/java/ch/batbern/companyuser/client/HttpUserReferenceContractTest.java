package ch.batbern.companyuser.client;

import ch.batbern.companyuser.client.UserReferenceContract.MergeBlockedException;
import ch.batbern.companyuser.client.UserReferenceContract.MergeCall;
import ch.batbern.companyuser.client.UserReferenceContract.MergeCallFailedException;
import ch.batbern.companyuser.client.UserReferenceContract.Preview;
import ch.batbern.companyuser.client.UserReferenceContract.Result;
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

import static ch.batbern.companyuser.client.UserReferenceHolders.MERGE;
import static ch.batbern.companyuser.client.UserReferenceHolders.PREVIEW;
import static ch.batbern.companyuser.client.UserReferenceHolders.PRIMARY_EMAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

/**
 * Contract test of {@link HttpUserReferenceContract} against the user-reference contract CUMS owns
 * ({@code docs/api/internal/cums-internal.openapi.yml}, Epic 17 entry 7b): every request is
 * validated against the spec, at each configured holder in order. Without a user no JWT is sent.
 */
@DisplayName("HttpUserReferenceContract honours the user-reference contract")
class HttpUserReferenceContractTest {

    private static final String A = "holder-a";
    private static final String B = "holder-b";

    private ContractServer holders;
    private HttpUserReferenceContract contract;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplateBuilder().build();
        holders = ContractServer.bindTo(restTemplate, UserReferenceHolders.CUMS_INTERNAL);
        contract = new HttpUserReferenceContract(restTemplate,
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
        holders.expect(GET, PREVIEW).atHost(A).withQueryParam("source", "anna.meier.2")
                .withQueryParam("target", "anna.meier").andExpect(HttpUserReferenceContractTest::noJwt)
                .respondWithExample("blocked");
        holders.expect(GET, PREVIEW).atHost(B).respondWith(HttpStatus.OK, """
                {"moves": {"partner_notes.author_username": 2}, "conflicts": {}, "blockers": []}""");

        List<Preview> previews = contract.preview("anna.meier.2", "anna.meier");

        assertThat(previews).hasSize(2);
        assertThat(previews.get(0).moves()).containsEntry("registrations.attendee_username", 1);
        assertThat(previews.get(0).conflicts()).containsEntry("registrations", 1);
        assertThat(previews.get(0).blockers()).containsExactly("BATbern58");
        assertThat(previews.get(1).moves()).containsExactly(Map.entry("partner_notes.author_username", 2));
        assertThat(previews.get(1).blockers()).isEmpty();
        holders.verify();
    }

    @Test
    @DisplayName("17.0b2: preview usernames are strictly encoded ('+' is not a space, '&' does not cut the query)")
    void should_encodePreviewUsernamesStrictly() {
        holders.expect(GET, PREVIEW).times(ExpectedCount.twice()).withQueryParam("source", "a+b&c=d")
                .withQueryParam("target", "anna meier")
                .andExpect(r -> assertThat(r.getURI().getRawQuery())
                        .isEqualTo("source=a%2Bb%26c%3Dd&target=anna%20meier"))
                .respondWithExample("clean");

        assertThat(contract.preview("a+b&c=d", "anna meier")).allSatisfy(p -> assertThat(p.blockers()).isEmpty());
        holders.verify();
    }

    @Test
    void should_mapInvalidPreview_toCallFailed() {
        holders.expect(GET, PREVIEW).atHost(A).respondWithExample(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> contract.preview("anna.meier", "anna.meier"))
                .isInstanceOf(MergeCallFailedException.class).hasMessageContaining("400");
        holders.verify();
    }

    private static MergeCall mergeCall() {
        return new MergeCall("anna.meier.2", "anna.meier", "organizer.alice", "ORGANIZER",
                Map.of("email", "anna.private@example.com", "firstName", "Anna"));
    }

    @Test
    void should_mergeAtEveryHolder_andReturnEachResult() {
        holders.expect(POST, MERGE).atHost(A).andExpect(r -> {
            noJwt(r);
            assertThat(body(r)).contains("\"sourceUsername\":\"anna.meier.2\"", "\"trigger\":\"ORGANIZER\"",
                    "\"sourceProfile\":{");
        }).respondWithExample();
        holders.expect(POST, MERGE).atHost(B).respondWithExample("nothingHeld");

        List<Result> results = contract.merge(mergeCall());

        assertThat(results).hasSize(2);
        assertThat(results.get(0).moved()).containsEntry("registrations.attendee_username", 2);
        assertThat(results.get(0).removed()).containsEntry("registrations.attendee_username", 1);
        assertThat(results.get(1).moved()).isEmpty();
        holders.verify();
    }

    @Test
    void should_mergeWithoutSourceProfile() {
        holders.expect(POST, MERGE).times(ExpectedCount.twice()).respondWithExample();

        contract.merge(new MergeCall("anna.meier.2", "anna.meier", "anna.meier", "SELF_CLAIM", null));

        holders.verify();
    }

    @Test
    @DisplayName("409 MERGE_BLOCKED becomes MergeBlockedException with the holder's message; later holders are not called")
    void should_throwMergeBlocked_when_aHolderAnswers409() {
        holders.expect(POST, MERGE).atHost(A).respondWithExample(HttpStatus.CONFLICT);
        holders.expect(POST, MERGE).atHost(B).times(ExpectedCount.never()).respondWithExample();

        assertThatThrownBy(() -> contract.merge(mergeCall()))
                .isInstanceOf(MergeBlockedException.class)
                .hasMessage("Both profiles lead a different talk at BATbern58");
        holders.verify();
    }

    @Test
    @DisplayName("a holder that does not answer fails the merge, naming it")
    void should_failMerge_when_aHolderIsDown() {
        holders.expect(POST, MERGE).atHost(A).respondWithExample();
        holders.expect(POST, MERGE).atHost(B).respondWith(request -> {
            throw new ConnectException("connection refused");
        });

        assertThatThrownBy(() -> contract.merge(mergeCall()))
                .isInstanceOf(MergeCallFailedException.class).hasMessageContaining(B);
        holders.verify();
    }

    @Test
    @DisplayName("no holder configured: preview and merge fail instead of a merge that would orphan every row")
    void should_refusePreviewAndMerge_when_noHolderIsConfigured() {
        HttpUserReferenceContract none = new HttpUserReferenceContract(new RestTemplateBuilder().build(), List.of());

        assertThatThrownBy(() -> none.preview("anna.meier.2", "anna.meier"))
                .isInstanceOf(MergeCallFailedException.class).hasMessageContaining("no holder");
        assertThatThrownBy(() -> none.merge(mergeCall()))
                .isInstanceOf(MergeCallFailedException.class).hasMessageContaining("no holder");
    }

    @Test
    void should_reportPrimaryEmailChange_toEveryHolder() {
        holders.expect(POST, PRIMARY_EMAIL).atHost(A).andExpect(r -> {
            noJwt(r);
            assertThat(body(r)).contains("\"username\":\"anna.meier\"", "\"oldEmail\":\"anna.old@example.com\"",
                    "\"newEmail\":\"anna.meier@example.com\"");
        }).respondWithExample();
        holders.expect(POST, PRIMARY_EMAIL).atHost(B).respondWithExample("nothingHeld");

        contract.primaryEmailChanged("anna.meier", "anna.old@example.com", "anna.meier@example.com");

        holders.verify();
    }

    @Test
    @DisplayName("primary email: a holder that is down does not keep the next one from being told")
    void should_tellEveryHolder_when_anEarlierHolderIsDown() {
        holders.expect(POST, PRIMARY_EMAIL).atHost(A).respondWith(request -> {
            throw new ConnectException("connection refused");
        });
        holders.expect(POST, PRIMARY_EMAIL).atHost(B).respondWithExample("nothingHeld");

        assertThatThrownBy(() -> contract.primaryEmailChanged("anna.meier", "a@example.com", "b@example.com"))
                .isInstanceOf(MergeCallFailedException.class).hasMessageContaining(A);
        holders.verify();
    }

    @Test
    void should_failCall_when_primaryEmailRefused() {
        holders.expect(POST, PRIMARY_EMAIL).atHost(A).respondWithStatus(HttpStatus.BAD_REQUEST);
        holders.expect(POST, PRIMARY_EMAIL).atHost(B).respondWithExample("nothingHeld");

        assertThatThrownBy(() -> contract.primaryEmailChanged("anna.meier", "a@example.com", "b@example.com"))
                .isInstanceOf(MergeCallFailedException.class);
        holders.verify();
    }
}
