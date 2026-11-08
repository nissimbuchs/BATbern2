package ch.batbern.events.client.impl;

import ch.batbern.events.client.PartnerMeetingRsvpClient;
import ch.batbern.events.config.RestClientConfig;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.http.HttpMethod.POST;

/**
 * Contract test of {@link PartnerMeetingRsvpClientImpl} against
 * {@code docs/api/internal/pcs-internal.openapi.yml} (Story 17.0b2): the real client on the real
 * {@code RestTemplate}, the request validated against the spec, answers from it.
 *
 * <p>It reaches PCS on the configured {@code partner-service.base-url}, the property every other
 * EMS to PCS call uses (17.0b1: it used to read an undefined property and only worked where its
 * default happened to be right). Errors are logged and discarded: inbound mail processing must
 * never fail on an RSVP.
 */
@SpringBootTest(classes = {PartnerMeetingRsvpClientImpl.class, RestClientConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "partner-service.base-url=http://pcs.configured:9104")
@ImportAutoConfiguration({
    RestTemplateAutoConfiguration.class,
    Jackson2AutoConfiguration.class,
    HttpMessageConvertersAutoConfiguration.class
})
class PartnerMeetingRsvpClientImplContractTest {

    private static final String PCS_INTERNAL = "docs/api/internal/pcs-internal.openapi.yml";
    private static final String RSVPS = "/internal/partner-meetings/rsvps";
    private static final UUID MEETING = UUID.fromString("0b6c1f7e-2c1d-4c55-9a43-2d6f4e8a9b10");

    @Autowired
    private PartnerMeetingRsvpClient client;

    @Autowired
    private RestTemplate restTemplate;

    private ContractServer pcs;

    @BeforeEach
    void bind() {
        pcs = ContractServer.bindTo(restTemplate, PCS_INTERNAL);
    }

    @AfterEach
    void release() {
        pcs.close();
    }

    @Test
    void should_postRsvpToConfiguredPartnerServiceUrl_withoutJwt() {
        pcs.expect(POST, RSVPS)
                .andExpect(r -> {
                    assertThat(r.getURI().toString()).isEqualTo("http://pcs.configured:9104" + RSVPS);
                    assertThat(r.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
                    String body = ((MockClientHttpRequest) r).getBodyAsString();
                    assertThat(body).contains("\"meetingId\":\"" + MEETING + "\"")
                            .contains("\"partStat\":\"ACCEPTED\"");
                })
                .respondWithExample();

        client.recordRsvp(MEETING, "partner@example.com", "ACCEPTED");

        pcs.verify();
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"NOT_FOUND", "BAD_REQUEST"})
    void should_discardRsvp_when_pcsRefusesIt(HttpStatus status) {
        pcs.expect(POST, RSVPS).respondWithStatus(status);

        assertThatCode(() -> client.recordRsvp(MEETING, "partner@example.com", "MAYBE")).doesNotThrowAnyException();
        pcs.verify();
    }
}
