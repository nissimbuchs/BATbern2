package ch.batbern.partners.config;

import ch.batbern.shared.test.contract.ContractServer;
import ch.batbern.shared.test.contract.ContractServer.Reply;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.ExpectedCount;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpMethod.GET;

/**
 * CUMS {@code GET /internal/users/{username}} for PCS integration tests (Story 17.0b2): the real
 * {@code UserServiceClient} asks, a {@link ContractServer} bound with
 * {@link #CUMS_INTERNAL} answers, and both the request and every answer are checked against the
 * CUMS internal spec. Replaces {@code @MockitoBean UserServiceClient}.
 *
 * <pre>{@code
 * cums = ContractServer.bindTo(restTemplate, CumsInternalUsers.CUMS_INTERNAL).ignoringMissingAuthorization();
 * users = CumsInternalUsers.answerOn(cums);
 * users.put("alice", "alice.alpha", "AlphaCo");   // the caller "alice" belongs to AlphaCo
 * }</pre>
 *
 * A username nobody registered answers 404, as CUMS does. The test clears the client caches in
 * {@code @BeforeEach}: the real client caches per username.
 */
public final class CumsInternalUsers {

    public static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";

    private final Map<String, String> users = new ConcurrentHashMap<>();

    private CumsInternalUsers() {
    }

    /** Registers the user lookup on {@code server} (bound with {@link #CUMS_INTERNAL}). */
    public static CumsInternalUsers answerOn(ContractServer server) {
        CumsInternalUsers registry = new CumsInternalUsers();
        server.expect(GET, "/internal/users/{username}").times(ExpectedCount.manyTimes())
                .respondPerRequest(call -> {
                    String body = registry.users.get(call.pathVariables().get("username"));
                    return body == null ? new Reply(HttpStatus.NOT_FOUND, null) : Reply.ok(body);
                });
        return registry;
    }

    /**
     * {@code GET /internal/users/{lookedUp}} answers a user {@code id} of {@code companyId}. The
     * id must be a CUMS username ({@code first.last}); the looked-up name is what the test's
     * principal is called.
     */
    public CumsInternalUsers put(String lookedUp, String id, String companyId) {
        users.put(lookedUp, """
                {"id": "%s", "email": "%s@example.com", "firstName": "Test", "lastName": "User", %s
                 "roles": ["PARTNER"], "active": true, "createdAt": "2025-01-15T10:00:00Z",
                 "updatedAt": "2025-01-15T10:00:00Z", "additionalEmails": []}"""
                .formatted(id, id, companyId == null ? "" : "\"companyId\": \"" + companyId + "\","));
        return this;
    }
}
