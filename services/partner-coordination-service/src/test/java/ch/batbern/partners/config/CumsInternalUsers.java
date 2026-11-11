package ch.batbern.partners.config;

import ch.batbern.shared.test.contract.ContractServer;
import ch.batbern.shared.test.contract.ContractServer.Reply;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.ExpectedCount;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpMethod.GET;

/**
 * CUMS {@code GET /internal/users/{username}} (and, with {@link #partnerContacts}, the paged
 * {@code GET /internal/users}) for PCS integration tests (Story 17.0b2): the real
 * {@code UserServiceClient} asks, a {@link ContractServer} bound with
 * {@link #CUMS_INTERNAL} answers, and both the request and every answer are checked against the
 * CUMS internal spec. Replaces {@code @MockitoBean UserServiceClient}.
 *
 * <pre>{@code
 * cums = ContractServer.bindTo(restTemplate, CumsInternalUsers.CUMS_INTERNAL);
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
        users.put(lookedUp, user(id, companyId, "Test", "User"));
        return this;
    }

    /** Partner contacts per bound server, then per company (see {@link #partnerContacts}). */
    private static final Map<ContractServer, Map<String, String[]>> CONTACTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * {@code GET /internal/users?company=...&role=PARTNER} (every page call of
     * {@code getUsersByCompanyAndRole}) answers one page with {@code users} (from {@link #user}) for
     * {@code company}, and an empty page for a company nobody registered. Call it once per company
     * on the same server: one expectation per server answers by the {@code company} parameter, so
     * a later call adds a company instead of shadowing the first. Replaces a stubbed
     * {@code UserServiceClient.getUsersByCompanyAndRole}.
     */
    public static void partnerContacts(ContractServer server, String company, String... users) {
        Map<String, String[]> byCompany = CONTACTS.computeIfAbsent(server, s -> {
            Map<String, String[]> companies = new ConcurrentHashMap<>();
            s.expect(GET, "/internal/users").times(ExpectedCount.manyTimes())
                    .respondPerRequest(call -> Reply.ok(page(companies.getOrDefault(
                            String.valueOf(call.queryParam("company")), new String[0]))));
            return companies;
        });
        byCompany.put(company, users);
    }

    /** One {@code UserResponse} of the cums-internal spec: a PARTNER {@code id} of {@code companyId}. */
    public static String user(String id, String companyId, String firstName, String lastName) {
        return """
                {"id": "%s", "hasCognitoAccount": true, "email": "%s@example.com", "firstName": "%s",
                 "lastName": "%s", "bio": null, "companyId": %s, "roles": ["PARTNER"],
                 "profilePictureUrl": null, "active": true, "createdAt": "2025-01-15T10:00:00Z",
                 "updatedAt": "2025-01-15T10:00:00Z", "lastLoginAt": null, "termsAcceptedAt": null,
                 "company": null, "settings": null, "additionalEmails": []}"""
                .formatted(id, id, firstName, lastName, companyId == null ? "null" : "\"" + companyId + "\"");
    }

    /** The only page of {@code GET /internal/users} holding {@code users}. */
    private static String page(String... users) {
        return """
                {"data": [%s], "pagination": {"page": 1, "limit": 100, "totalItems": %d, "totalPages": %d,
                 "hasNext": false, "hasPrev": false}}""".formatted(String.join(",", users), users.length,
                users.length == 0 ? 0 : 1);
    }
}
