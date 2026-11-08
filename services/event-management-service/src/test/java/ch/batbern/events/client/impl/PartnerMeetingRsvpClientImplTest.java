package ch.batbern.events.client.impl;

import ch.batbern.events.client.PartnerMeetingRsvpClient;
import ch.batbern.events.config.RestClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The RSVP client reaches PCS on the configured {@code partner-service.base-url}, the same
 * property every other EMS→PCS call uses (Story 17.0b1). It used to read an undefined
 * {@code partner-coordination-service.base-url}, so it only worked where its hard-coded default
 * happened to be right, and never on local dev. The call is {@code /internal}: no spec yet (0b2),
 * so plain MockRestServiceServer here.
 */
@SpringBootTest(classes = {PartnerMeetingRsvpClientImpl.class, RestClientConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "partner-service.base-url=http://pcs.configured:9104")
@ImportAutoConfiguration({
    RestTemplateAutoConfiguration.class,
    Jackson2AutoConfiguration.class,
    HttpMessageConvertersAutoConfiguration.class
})
class PartnerMeetingRsvpClientImplTest {

    @Autowired
    private PartnerMeetingRsvpClient client;

    @Autowired
    private RestTemplate restTemplate;

    @Test
    void should_postRsvpToConfiguredPartnerServiceUrl() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        UUID meetingId = UUID.fromString("0b6c1f7e-2c1d-4c55-9a43-2d6f4e8a9b10");
        server.expect(requestTo("http://pcs.configured:9104/internal/partner-meetings/rsvps"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.meetingId").value(meetingId.toString()))
                .andExpect(jsonPath("$.partStat").value("ACCEPTED"))
                .andRespond(withSuccess());

        client.recordRsvp(meetingId, "partner@example.com", "ACCEPTED");

        server.verify();
    }
}
