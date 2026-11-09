package ch.batbern.events.service;

import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * OpenAI at the wire for integration tests: binds a {@link MockRestServiceServer} to the OpenAI
 * client of the context's real {@link BatbernAiService} through its builder seam
 * ({@code useClientBuilder} + {@code init}). The service, its prompts, its parsing and its log run
 * for real; only the HTTP calls to OpenAI are answered by the test.
 *
 * <p>The context must switch AI on ({@code batbern.ai.enabled=true}, a non-blank
 * {@code openai.api-key}); otherwise the service builds no client. Call {@link #release} in
 * {@code @AfterEach}: it puts a fresh client back, so the next test of a cached context starts
 * with an empty result cache and no expectations.
 */
public final class OpenAiTestServer {

    private OpenAiTestServer() {
    }

    /** Binds a new server to the service's OpenAI client and clears its result cache. */
    public static MockRestServiceServer bindTo(BatbernAiService service) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BatbernAiService target = AopTestUtils.getTargetObject(service);
        target.useClientBuilder(builder);
        target.init();
        return server;
    }

    /** Detaches the test server; the service builds its production client again. */
    public static void release(BatbernAiService service) {
        BatbernAiService target = AopTestUtils.getTargetObject(service);
        target.useClientBuilder(null);
        target.init();
    }
}
