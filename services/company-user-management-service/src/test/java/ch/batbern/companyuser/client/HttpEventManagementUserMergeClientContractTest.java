package ch.batbern.companyuser.client;

import ch.batbern.companyuser.client.EventManagementUserMergeClient.MergeBlockedException;
import ch.batbern.companyuser.client.EventManagementUserMergeClient.MergeCall;
import ch.batbern.companyuser.client.EventManagementUserMergeClient.MergeCallFailedException;
import ch.batbern.companyuser.client.EventManagementUserMergeClient.Preview;
import ch.batbern.companyuser.client.EventManagementUserMergeClient.Result;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

/**
 * Contract test of {@link HttpEventManagementUserMergeClient} against
 * {@code docs/api/internal/ems-internal.openapi.yml} (Story 17.0b2): the real client on a
 * {@code RestTemplate} built like its production one, every request validated against the spec,
 * answers from its examples. No JWT: service to service in the VPC.
 */
@DisplayName("HttpEventManagementUserMergeClient honours the EMS internal contract")
class HttpEventManagementUserMergeClientContractTest {

    private static final String EMS_INTERNAL = "docs/api/internal/ems-internal.openapi.yml";
    private static final String BASE = "http://event-management:8080";

    private ContractServer ems;
    private HttpEventManagementUserMergeClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplateBuilder().build();
        ems = ContractServer.bindTo(restTemplate, EMS_INTERNAL);
        client = new HttpEventManagementUserMergeClient(restTemplate, BASE);
    }

    @AfterEach
    void tearDown() {
        ems.close();
    }

    private static void noJwt(ClientHttpRequest request) {
        assertThat(request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void should_readPreview() {
        ems.expect(GET, "/internal/users/merge-preview").withQueryParam("source", "anna.meier.2")
                .withQueryParam("target", "anna.meier").andExpect(HttpEventManagementUserMergeClientContractTest::noJwt)
                .respondWithExample("blocked");

        Preview preview = client.preview("anna.meier.2", "anna.meier");

        assertThat(preview.moves()).containsEntry("registrations.attendee_username", 1);
        assertThat(preview.conflicts()).containsEntry("registrations", 1);
        assertThat(preview.blockers()).containsExactly("BATbern58");
        ems.verify();
    }

    @Test
    @DisplayName("17.0b2: preview usernames are strictly encoded ('+' is not a space, '&' does not cut the query)")
    void should_encodePreviewUsernamesStrictly() {
        ems.expect(GET, "/internal/users/merge-preview").withQueryParam("source", "a+b&c=d")
                .withQueryParam("target", "anna meier")
                .andExpect(r -> assertThat(r.getURI().getRawQuery())
                        .isEqualTo("source=a%2Bb%26c%3Dd&target=anna%20meier"))
                .respondWithExample("clean");

        assertThat(client.preview("a+b&c=d", "anna meier").blockers()).isEmpty();
        ems.verify();
    }

    @Test
    void should_mapInvalidPreview_toCallFailed() {
        ems.expect(GET, "/internal/users/merge-preview").respondWithExample(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> client.preview("anna.meier", "anna.meier"))
                .isInstanceOf(MergeCallFailedException.class).hasMessageContaining("400");
        ems.verify();
    }

    private static MergeCall mergeCall() {
        return new MergeCall("anna.meier.2", "anna.meier", "organizer.alice", "ORGANIZER",
                Map.of("email", "anna.private@example.com", "firstName", "Anna"));
    }

    @Test
    void should_merge() {
        ems.expect(POST, "/internal/users/merge").andExpect(HttpEventManagementUserMergeClientContractTest::noJwt)
                .respondWithExample();

        Result result = client.merge(mergeCall());

        assertThat(result.moved()).containsEntry("registrations.attendee_username", 2);
        assertThat(result.removed()).containsEntry("registrations.attendee_username", 1);
        ems.verify();
    }

    @Test
    void should_mergeWithoutSourceProfile() {
        ems.expect(POST, "/internal/users/merge").respondWithExample();

        client.merge(new MergeCall("anna.meier.2", "anna.meier", "anna.meier", "SELF_CLAIM", null));

        ems.verify();
    }

    @Test
    @DisplayName("409 MERGE_BLOCKED becomes MergeBlockedException with EMS's message")
    void should_throwMergeBlocked_when_emsAnswers409() {
        ems.expect(POST, "/internal/users/merge").respondWithExample(HttpStatus.CONFLICT);

        assertThatThrownBy(() -> client.merge(mergeCall()))
                .isInstanceOf(MergeBlockedException.class)
                .hasMessage("Both profiles lead a different talk at BATbern58");
        ems.verify();
    }

    @Test
    void should_reportPrimaryEmailChange() {
        ems.expect(POST, "/internal/users/primary-email")
                .andExpect(HttpEventManagementUserMergeClientContractTest::noJwt)
                .respondWithExample();

        client.primaryEmailChanged("anna.meier", "anna.old@example.com", "anna.meier@example.com");

        ems.verify();
    }

    @Test
    void should_failCall_when_primaryEmailRefused() {
        ems.expect(POST, "/internal/users/primary-email").respondWithStatus(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> client.primaryEmailChanged("anna.meier", "a@example.com", "b@example.com"))
                .isInstanceOf(MergeCallFailedException.class);
        ems.verify();
    }
}
