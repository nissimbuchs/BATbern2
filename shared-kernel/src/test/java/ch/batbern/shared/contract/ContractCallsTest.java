package ch.batbern.shared.contract;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ContractCallsTest {

    private static final String A = "http://holder-a:8080";
    private static final String B = "http://holder-b:8080";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private ContractCalls calls(String... implementers) {
        return new ContractCalls("things", List.of(implementers), restTemplate);
    }

    @Test
    void should_readImplementers_fromConfiguration_asListOrCommaSeparated() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("batbern.contracts.things.implementers", " http://a:8080/ , http://b:8080");

        assertThat(ContractCalls.fromEnvironment("things", env, restTemplate).implementers())
                .containsExactly("http://a:8080", "http://b:8080");
        assertThat(ContractCalls.fromEnvironment("other", env, restTemplate).implementers()).isEmpty();

        MockEnvironment yamlList = new MockEnvironment()
                .withProperty("batbern.contracts.things.implementers[0]", "http://a:8080")
                .withProperty("batbern.contracts.things.implementers[1]", "http://b:8080");
        assertThat(ContractCalls.fromEnvironment("things", yamlList, restTemplate).implementers())
                .containsExactly("http://a:8080", "http://b:8080");
    }

    @Test
    void should_postToEveryImplementer_inOrder_withEncodedPathVariables() {
        server.expect(requestTo(A + "/internal/things/a%20b/done")).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("{\"by\":\"x\"}"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(requestTo(B + "/internal/things/a%20b/done")).andRespond(withStatus(HttpStatus.NO_CONTENT));

        calls(A, B).post("/internal/things/{id}/done", Map.of("by", "x"), "a b");

        server.verify();
    }

    @Test
    void should_forwardTheUsersJwt_whenThereIsOne() {
        Jwt jwt = Jwt.withTokenValue("user-token").header("alg", "none").claim("sub", "u")
                .issuedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        server.expect(requestTo(A + "/internal/things"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        calls(A).post("/internal/things", Map.of());

        server.verify();
    }

    @Test
    void should_returnEveryAnswer_ofAGet() {
        server.expect(requestTo(A + "/internal/things?x=1%2B2")).andRespond(withSuccess("{\"v\":\"a\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(B + "/internal/things?x=1%2B2")).andRespond(withSuccess("{\"v\":\"b\"}", MediaType.APPLICATION_JSON));

        List<Map> answers = calls(A, B).get("/internal/things?x={x}", Map.of("x", "1+2"), Map.class);

        assertThat(answers).extracting(m -> m.get("v")).containsExactly("a", "b");
    }

    @Test
    void should_translate409_toRefusal_withTheImplementersMessage_andStop() {
        server.expect(requestTo(A + "/internal/things")).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"blocked by talk\"}"));
        server.expect(never(), requestTo(B + "/internal/things"));

        assertThatThrownBy(() -> calls(A, B).post("/internal/things", Map.of()))
                .isInstanceOfSatisfying(ContractRefusedException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo("blocked by talk");
                    assertThat(e.implementer()).isEqualTo(A);
                    assertThat(e.contract()).isEqualTo("things");
                });
        server.verify();
    }

    @Test
    void should_translateOtherStatus_toCallFailed() {
        server.expect(requestTo(A + "/internal/things")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> calls(A).post("/internal/things", Map.of()))
                .isInstanceOfSatisfying(ContractCallFailedException.class, e -> {
                    assertThat(e.implementer()).isEqualTo(A);
                    assertThat(e.getMessage()).contains("things", A, "status 500");
                });
    }

    @Test
    void should_translateNoAnswer_toCallFailed() {
        server.expect(requestTo(A + "/internal/things")).andRespond(request -> {
            throw new IOException("connection refused");
        });

        assertThatThrownBy(() -> calls(A).post("/internal/things", Map.of()))
                .isInstanceOf(ContractCallFailedException.class)
                .hasCauseInstanceOf(ResourceAccessException.class);
    }

    @Test
    void should_callNobody_withoutImplementers() {
        calls().post("/internal/things", Map.of());

        assertThat(calls().get("/internal/things", Map.of(), String.class)).isEmpty();
        server.verify();
    }

    @Test
    void should_warnOnce_whenNoImplementerIsConfigured() {
        Logger logger = (Logger) LoggerFactory.getLogger(ContractCalls.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ContractCalls.fromEnvironment("things", new MockEnvironment()
                    .withProperty("batbern.contracts.things.implementers", " "), restTemplate);
            calls(A);

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("contract=things")
                        .contains("batbern.contracts.things.implementers");
            });
        } finally {
            logger.detachAppender(appender);
        }
    }
}
