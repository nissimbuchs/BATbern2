package ch.batbern.companyuser.client;

import ch.batbern.companyuser.client.MailCenterRequestContract.Decided;
import ch.batbern.companyuser.client.MailCenterRequestContract.Kind;
import ch.batbern.companyuser.client.MailCenterRequestContract.MailCenterCallFailedException;
import ch.batbern.companyuser.client.MailCenterRequestContract.Opened;
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

import java.net.ConnectException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static ch.batbern.companyuser.client.MailCenterContract.DECIDED;
import static ch.batbern.companyuser.client.MailCenterContract.OPENED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;

/**
 * Contract test of {@link HttpMailCenterRequestContract} against the Mail Center request contract
 * CUMS owns ({@code docs/api/internal/cums-internal.openapi.yml}, Epic 17 entry 7): every request
 * is validated against the spec, at the configured implementer. Without a user no JWT is sent.
 */
@DisplayName("HttpMailCenterRequestContract honours the Mail Center request contract")
class HttpMailCenterRequestContractTest {

    private static final UUID REQUEST = UUID.fromString("2f6d8c1a-5b7e-4c3d-9a0f-1e2d3c4b5a69");
    private static final String IMPLEMENTER = "http://implementer:8080";

    private ContractServer implementer;
    private HttpMailCenterRequestContract contract;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplateBuilder().build();
        implementer = ContractServer.bindTo(restTemplate, MailCenterContract.CUMS_INTERNAL);
        contract = new HttpMailCenterRequestContract(restTemplate, List.of(IMPLEMENTER));
    }

    @AfterEach
    void tearDown() {
        implementer.close();
    }

    private static void noJwt(ClientHttpRequest request) {
        assertThat(request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    private static String body(ClientHttpRequest request) {
        return ((MockClientHttpRequest) request).getBodyAsString();
    }

    @Test
    void should_openMergeRequest_atTheImplementer() {
        implementer.expect(POST, OPENED)
                .andExpect(r -> {
                    noJwt(r);
                    assertThat(r.getURI().toString()).isEqualTo(IMPLEMENTER + OPENED);
                    assertThat(body(r)).contains("\"kind\":\"MERGE\"", "\"requestId\":\"" + REQUEST + "\"",
                            "\"requesterEmail\":\"anna.meier@example.com\"", "\"note\":\"Firma X bis 2024\"");
                })
                .respondWithStatus(HttpStatus.NO_CONTENT);

        contract.opened(new Opened(Kind.MERGE, REQUEST, "Anna Meier", "anna.meier@example.com",
                "Profil zusammenführen: Anna Meier", "Firma X bis 2024"));

        implementer.verify();
    }

    @Test
    void should_openLogoRequest_withoutNoteOrName() {
        implementer.expect(POST, OPENED)
                .andExpect(r -> assertThat(body(r)).contains("\"kind\":\"LOGO\"", "\"note\":null"))
                .respondWithStatus(HttpStatus.NO_CONTENT);

        contract.opened(new Opened(Kind.LOGO, REQUEST, null, "anna.meier@example.com", "Neues Logo für ACME AG: ",
                null));

        implementer.verify();
    }

    @Test
    void should_sendTheDecision_withDecider_andTime() {
        implementer.expect(POST, DECIDED)
                .andExpect(r -> {
                    noJwt(r);
                    assertThat(r.getURI().getPath()).isEqualTo("/internal/mail-center-requests/" + REQUEST + "/decision");
                    assertThat(body(r)).contains("\"kind\":\"LOGO\"", "\"status\":\"DECLINED\"",
                            "\"decidedBy\":\"organizer.alice\"", "\"decidedAt\":\"2026-11-11T10:00:05.654321Z\"");
                })
                .respondWithStatus(HttpStatus.NO_CONTENT);

        contract.decided(REQUEST, new Decided(Kind.LOGO, "DECLINED", "organizer.alice",
                Instant.parse("2026-11-11T10:00:05.654321Z")));

        implementer.verify();
    }

    @Test
    void should_fail_when_theImplementerRefuses() {
        implementer.expect(POST, OPENED).respondWithStatus(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> contract.opened(new Opened(Kind.MERGE, REQUEST, "A", "a@example.com", "S", null)))
                .isInstanceOf(MailCenterCallFailedException.class);
        implementer.verify();
    }

    @Test
    void should_fail_when_theImplementerDoesNotAnswer() {
        implementer.expect(POST, DECIDED).respondWith(request -> {
            throw new ConnectException("Connection refused");
        });

        assertThatThrownBy(() -> contract.decided(REQUEST, new Decided(Kind.MERGE, "APPROVED", "organizer.alice",
                Instant.now()))).isInstanceOf(MailCenterCallFailedException.class)
                .hasMessageContaining(IMPLEMENTER);
    }
}
