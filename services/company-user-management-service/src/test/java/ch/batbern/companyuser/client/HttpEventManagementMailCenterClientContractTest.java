package ch.batbern.companyuser.client;

import ch.batbern.companyuser.client.EventManagementMailCenterClient.MailCenterCallFailedException;
import ch.batbern.companyuser.client.EventManagementMailCenterClient.OpenRequest;
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
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;

/**
 * Contract test of {@link HttpEventManagementMailCenterClient} against
 * {@code docs/api/internal/ems-internal.openapi.yml} (Story 17.0b2): opening and closing the Mail
 * Center conversation of a merge request (#1237) and of a logo request (#1246), every request
 * validated against the spec. No JWT: service to service in the VPC.
 */
@DisplayName("HttpEventManagementMailCenterClient honours the EMS internal contract")
class HttpEventManagementMailCenterClientContractTest {

    private static final String EMS_INTERNAL = "docs/api/internal/ems-internal.openapi.yml";
    private static final UUID REQUEST = UUID.fromString("2f6d8c1a-5b7e-4c3d-9a0f-1e2d3c4b5a69");

    private ContractServer ems;
    private HttpEventManagementMailCenterClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplateBuilder().build();
        ems = ContractServer.bindTo(restTemplate, EMS_INTERNAL);
        client = new HttpEventManagementMailCenterClient(restTemplate, "http://event-management:8080");
    }

    @AfterEach
    void tearDown() {
        ems.close();
    }

    private static void noJwt(ClientHttpRequest request) {
        assertThat(request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    private static OpenRequest open(String note) {
        return new OpenRequest(REQUEST, "Anna Meier", "anna.meier@example.com", "Merge my two profiles", note);
    }

    @Test
    void should_openMergeRequestConversation() {
        ems.expect(POST, "/internal/mail-center/merge-requests")
                .andExpect(HttpEventManagementMailCenterClientContractTest::noJwt)
                .andExpect(r -> assertThat(((MockClientHttpRequest) r).getBodyAsString())
                        .contains("\"requestId\":\"" + REQUEST + "\"").contains("\"note\":\"I registered twice.\""))
                .respondWithExample();

        client.openMergeRequest(open("I registered twice."));

        ems.verify();
    }

    @Test
    void should_openLogoRequestConversation_withoutNote() {
        ems.expect(POST, "/internal/mail-center/logo-requests").respondWithExample();

        client.openLogoRequest(open(null));

        ems.verify();
    }

    @Test
    void should_closeMergeRequestConversation() {
        ems.expect(POST, "/internal/mail-center/merge-requests/{requestId}/done")
                .andExpect(r -> assertThat(r.getURI().getPath())
                        .isEqualTo("/internal/mail-center/merge-requests/" + REQUEST + "/done"))
                .andExpect(HttpEventManagementMailCenterClientContractTest::noJwt)
                .respondWithStatus(HttpStatus.NO_CONTENT);

        client.closeMergeRequest(REQUEST, "organizer.alice");

        ems.verify();
    }

    @Test
    void should_closeLogoRequestConversation() {
        ems.expect(POST, "/internal/mail-center/logo-requests/{requestId}/done")
                .respondWithStatus(HttpStatus.NO_CONTENT);

        client.closeLogoRequest(REQUEST, "organizer.alice");

        ems.verify();
    }

    @Test
    void should_failCall_when_emsRefuses() {
        ems.expect(POST, "/internal/mail-center/merge-requests").respondWithStatus(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> client.openMergeRequest(open(null))).isInstanceOf(MailCenterCallFailedException.class);
        ems.verify();
    }
}
