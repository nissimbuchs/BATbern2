package ch.batbern.events.config;

import ch.batbern.shared.test.contract.ContractServer;
import ch.batbern.shared.test.contract.ContractServer.Call;
import ch.batbern.shared.test.contract.ContractServer.Reply;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.springframework.http.HttpMethod.GET;

/**
 * CUMS behind the real {@code UserApiClientImpl} in every EMS integration test (Story 17.0b2): a
 * {@link ContractServer} on the shared {@code RestTemplate} that answers CUMS and validates every
 * request against the CUMS internal spec {@code docs/api/internal/cums-internal.openapi.yml}, the
 * only CUMS API EMS calls since Epic 17 entry 6. It replaces the Mockito {@code UserApiClient} of
 * the former {@code TestUserApiClientConfig}.
 *
 * <p>Only requests to {@code user-service.base-url} reach it; every other call of the shared
 * {@code RestTemplate} (PCS) goes to the real request factory, as before. The internal spec requires
 * no JWT, so a {@code @WithMockUser} caller (no JWT to forward) passes; the client contract test
 * proves that a JWT is forwarded when there is one.
 *
 * <p>Answers by default, the same data the Mockito default gave:
 * <ul>
 *   <li>{@code GET /internal/users/{username}}: a user with that username, e-mail
 *       {@code <username>@test.com}, "Test User", company {@code TestCompany}; or the body a test
 *       registered with {@link #user(String, String)}; 404 after {@link #unknownUser(String)}</li>
 *   <li>{@code GET /internal/users}: an empty page, or the users registered with
 *       {@link #usersWithRole(String, String...)} (no {@code company} filter) or
 *       {@link #usersOfCompany(String, String, String...)} (that role in that company only)</li>
 *   <li>{@code GET /internal/email-owners}: none, or those registered with {@link #emailOwners}</li>
 *   <li>{@code GET /internal/companies/{name}}: 404, so the client's display name falls back to the
 *       slug, as the Mockito default ({@code null}) did</li>
 *   <li>{@code GET /internal/mail-center-requests} (Epic 17 entry 7): an empty page whose
 *       {@code nextCursor} is the {@code since} sent, or {@link #EMPTY_FEED_CURSOR} without one, so
 *       every context tolerates {@code MailCenterRequestFeedJob}; a test of the job registers its
 *       pages with {@link #answer(HttpMethod, String, Function)}</li>
 * </ul>
 * Every other operation (get-or-create, provision, profile patch, invitation credentials) has no
 * default: a test of such a flow registers its answer with
 * {@link #answer(HttpMethod, String, Function)} and reads what was sent with
 * {@link #calls(HttpMethod, String)}. An unregistered call, or a path no spec has, fails the test.
 *
 * <p>The harness lives as long as the cached Spring context. A test that registers answers calls
 * {@link #reset()} afterwards; registering evicts the client's {@code userApiCache} entry, so the
 * real client asks again.
 *
 * <p><b>A test that binds its own {@code ContractServer} to the shared {@code RestTemplate}</b> (for
 * PCS, say) displaces this harness for that test: every request then goes to the test's server,
 * and a CUMS call fails as "not an operation of ...". Two ways to keep CUMS:
 * <ul>
 *   <li>route only the other provider's requests to the test's server; CUMS requests fall through
 *       to the factory it replaced, which is this harness:
 *       {@code ContractServer.bindTo(restTemplate, PARTNERS).onlyFor(uri -> !uri.toString()
 *       .startsWith(cumsBaseUrl))};</li>
 *   <li>or bind both specs, {@code ContractServer.bindTo(restTemplate, PARTNERS, CUMS_INTERNAL)},
 *       and expect the CUMS calls on that server too (this harness's answers do not apply then).</li>
 * </ul>
 * Either way the test calls {@code close()} in {@code @AfterEach}, which gives the harness back.
 */
public final class CumsContractHarness implements AutoCloseable {

    public static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";

    /** The operations a test can answer, besides the three internal reads with defaults. */
    private static final List<String> ANSWERABLE = List.of(
            "POST /internal/users/get-or-create",
            "POST /internal/users/provision",
            "PATCH /internal/users/{username}/profile",
            "POST /internal/users/{username}/issue-invitation-credentials",
            "GET /internal/companies/{name}",
            "GET /internal/mail-center-requests");

    /** The default page of the Mail Center request feed: nothing new, cursor unchanged. */
    public static final String EMPTY_FEED_CURSOR = "aGFybmVzcw";

    /** CUMS usernames: lower-case first and last name, optional numeric suffix (DB check). */
    private static final String USERNAME = "^[a-z]+\\.[a-z]+(\\.[0-9]+)?$";

    private final ContractServer server;
    private final CacheManager cacheManager;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, String> users = new ConcurrentHashMap<>();
    private final Map<String, List<String>> roles = new ConcurrentHashMap<>();
    private final Map<String, List<String>> owners = new ConcurrentHashMap<>();
    private final List<String> requestedUsers = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Function<Call, Reply>> answers = new ConcurrentHashMap<>();
    private final Map<String, List<Call>> calls = new ConcurrentHashMap<>();

    public CumsContractHarness(RestTemplate restTemplate, String cumsBaseUrl, CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        String cums = cumsBaseUrl + "/";
        this.server = ContractServer.bindTo(restTemplate, CUMS_INTERNAL)
                .onlyFor(uri -> uri.toString().startsWith(cums));
        for (String operation : ANSWERABLE) {
            String[] parts = operation.split(" ", 2);
            server.expect(HttpMethod.valueOf(parts[0]), parts[1]).times(ExpectedCount.manyTimes())
                    .respondPerRequest(call -> answerRegistered(operation, call));
        }
        defaults();
        server.expect(GET, "/internal/users/{username}").times(ExpectedCount.manyTimes())
                .respondPerRequest(this::user);
        server.expect(GET, "/internal/users").times(ExpectedCount.manyTimes())
                .respondPerRequest(HttpStatus.OK, this::page);
        server.expect(GET, "/internal/email-owners").times(ExpectedCount.manyTimes())
                .respondPerRequest(HttpStatus.OK, call -> toJson(owners.getOrDefault(
                        call.queryParam("email").trim().toLowerCase(Locale.ROOT), List.of())));
    }

    // ------------------------------------------------------------------ test API

    /** Default answers, restored by {@link #reset()}. */
    private void defaults() {
        answers.put("GET /internal/companies/{name}", call -> new Reply(HttpStatus.NOT_FOUND, null));
        answers.put("GET /internal/mail-center-requests", call -> Reply.ok(
                "{\"items\": [], \"nextCursor\": \"" + (call.queryParam("since") == null
                        ? EMPTY_FEED_CURSOR : call.queryParam("since")) + "\"}"));
    }

    /**
     * Answers {@code GET /internal/users/{username}} with this body, laid over a complete user
     * (CUMS always sends every {@code UserResponse} property, 4.9b): a test states the fields it
     * cares about. The result is checked against the spec.
     */
    public void user(String username, String userJson) {
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) parse(
                userJson(username, "Test", "User", username + "@test.com", null));
        @SuppressWarnings("unchecked")
        Map<String, Object> given = (Map<String, Object>) parse(userJson);
        user.putAll(given);
        if (user.get("additionalEmails") instanceof List<?> emails) {
            for (Object email : emails) {
                if (email instanceof Map<?, ?> entry) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> additional = (Map<String, Object>) entry;
                    // EMS reads an unset receiveEventMail as false (EventMailCopies)
                    additional.putIfAbsent("label", null);
                    additional.putIfAbsent("verifiedAt", null);
                    additional.putIfAbsent("receiveEventMail", false);
                }
            }
        }
        users.put(username, toJson(user));
        evict(username);
    }

    /** Answers {@code GET /internal/users/{username}} with this user's fields. */
    public void user(String username, String firstName, String lastName, String email) {
        user(username, userJson(username, firstName, lastName, email, null));
    }

    /**
     * Answers {@code GET /internal/users/{username}} with a user whose settings carry this
     * language ({@code UserApiClient#getPreferredLanguage}); every other setting is its default.
     */
    public void userWithLanguage(String username, String language) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("theme", "auto");
        settings.put("language", language);
        settings.put("timezone", "Europe/Zurich");
        settings.put("emailNotifications", true);
        settings.put("inAppNotifications", true);
        settings.put("pushNotifications", false);
        settings.put("notificationFrequency", "immediate");
        settings.put("qnaNotificationFrequency", "live");
        settings.put("quietHoursStart", null);
        settings.put("quietHoursEnd", null);
        settings.put("profileVisibility", "public");
        settings.put("showEmail", false);
        settings.put("showCompany", true);
        settings.put("showActivityHistory", true);
        settings.put("allowMessaging", true);
        settings.put("allowCalendarSync", false);
        settings.put("twoFactorEnabled", false);
        user(username, toJson(Map.of("settings", settings)));
    }

    /** Answers {@code GET /internal/users/{username}} with 404. */
    public void unknownUser(String username) {
        users.put(username, "");
        evict(username);
    }

    /** {@code GET /internal/users?role=...} lists these usernames (default users), on one page. */
    public void usersWithRole(String role, String... usernames) {
        roles.put(listKey(role, null), List.of(usernames));
    }

    /**
     * {@code GET /internal/users?role=...&company=...} lists these usernames for this company only;
     * any other company gets none, as in CUMS.
     */
    public void usersOfCompany(String company, String role, String... usernames) {
        roles.put(listKey(role, company), List.of(usernames));
    }

    private static String listKey(String role, String company) {
        return role + "|" + (company == null ? "" : company);
    }

    /** {@code GET /internal/email-owners?email=...} answers these usernames. */
    public void emailOwners(String email, String... usernames) {
        owners.put(email.toLowerCase(Locale.ROOT), List.of(usernames));
    }

    /** The usernames looked up with {@code GET /internal/users/{username}}, in order. */
    public List<String> requestedUsers() {
        synchronized (requestedUsers) {
            return List.copyOf(requestedUsers);
        }
    }

    /**
     * Answers one of the operations without a default, for example
     * {@code answer(POST, "/internal/users/provision", call -> Reply.ok(...))}. The reply is
     * checked against the spec.
     */
    public void answer(HttpMethod method, String pathTemplate, Function<Call, Reply> reply) {
        String operation = method.name() + " " + pathTemplate;
        if (!ANSWERABLE.contains(operation)) {
            throw new IllegalArgumentException(operation + " is not answerable here; have " + ANSWERABLE);
        }
        answers.put(operation, reply);
    }

    /** The calls made to an answerable operation since the last {@link #reset()}, in order. */
    public List<Call> calls(HttpMethod method, String pathTemplate) {
        List<Call> made = calls.getOrDefault(method.name() + " " + pathTemplate, List.of());
        synchronized (made) {
            return List.copyOf(made);
        }
    }

    /** Back to the defaults; clears the client's {@code userApiCache} too. */
    public void reset() {
        users.clear();
        roles.clear();
        owners.clear();
        requestedUsers.clear();
        answers.clear();
        calls.clear();
        defaults();
        Cache cache = cacheManager.getCache("userApiCache");
        if (cache != null) {
            cache.clear();
        }
    }

    @Override
    public void close() {
        server.close();
    }

    /** A valid {@code UserResponse} body; {@code companyId} may be {@code null}. */
    public static String userJson(String username, String firstName, String lastName, String email,
            String companyId) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", username);
        user.put("email", email);
        user.put("firstName", firstName);
        user.put("lastName", lastName);
        user.put("hasCognitoAccount", true);
        user.put("bio", null);
        user.put("companyId", companyId);
        user.put("roles", List.of());
        user.put("profilePictureUrl", null);
        user.put("active", true);
        user.put("createdAt", "2025-01-15T10:00:00Z");
        user.put("updatedAt", "2025-01-15T10:00:00Z");
        user.put("lastLoginAt", null);
        user.put("termsAcceptedAt", null);
        user.put("company", null);
        user.put("settings", null);
        user.put("additionalEmails", List.of());
        try {
            return new ObjectMapper().writeValueAsString(user);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ answers

    private Reply answerRegistered(String operation, Call call) {
        calls.computeIfAbsent(operation, k -> Collections.synchronizedList(new ArrayList<>())).add(call);
        Function<Call, Reply> reply = answers.get(operation);
        if (reply == null) {
            throw new AssertionError("CumsContractHarness: no answer for " + operation
                    + "; the test registers one with answer(...)");
        }
        return reply.apply(call);
    }

    private Reply user(Call call) {
        String username = call.pathVariables().get("username");
        requestedUsers.add(username);
        String registered = users.get(username);
        if (registered != null) {
            return registered.isEmpty() ? new Reply(HttpStatus.NOT_FOUND, null) : Reply.ok(registered);
        }
        if (!username.matches(USERNAME)) {
            throw new AssertionError("CumsContractHarness: '" + username + "' cannot be a CUMS username ("
                    + USERNAME + "); use a valid one or register an answer with user(...)/unknownUser(...)");
        }
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", username);
        user.put("email", username + "@test.com");
        user.put("firstName", "Test");
        user.put("lastName", "User");
        user.put("hasCognitoAccount", true);
        user.put("companyId", "TestCompany");
        user.put("bio", "Test user bio");
        user.put("profilePictureUrl", "https://cdn.test.com/profile.jpg");
        user.put("roles", List.of());
        user.put("active", true);
        user.put("createdAt", "2025-01-15T10:00:00Z");
        user.put("updatedAt", "2025-01-15T10:00:00Z");
        user.put("lastLoginAt", null);
        user.put("termsAcceptedAt", null);
        user.put("company", null);
        user.put("settings", null);
        user.put("additionalEmails", List.of());
        return Reply.ok(toJson(user));
    }

    private String page(Call call) {
        List<String> usernames = roles.getOrDefault(
                listKey(String.valueOf(call.queryParam("role")), call.queryParam("company")), List.of());
        String page = call.queryParam("page");
        List<Object> data = new ArrayList<>();
        if (page == null || "1".equals(page)) {
            for (String u : usernames) {
                data.add(parse(users.getOrDefault(u, userJson(u, "Test", "User", u + "@test.com", "TestCompany"))));
            }
        }
        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("page", page == null ? 1 : Integer.parseInt(page));
        pagination.put("limit", 100);
        pagination.put("totalItems", usernames.size());
        pagination.put("totalPages", usernames.isEmpty() ? 0 : 1);
        pagination.put("hasNext", false);
        pagination.put("hasPrev", page != null && !"1".equals(page));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("pagination", pagination);
        return toJson(body);
    }

    private void evict(String username) {
        Cache cache = cacheManager.getCache("userApiCache");
        if (cache != null) {
            cache.evict(username);
            cache.evict("prefLang:" + username);
            cache.evict("qnaFreq:" + username);
        }
    }

    private Object parse(String body) {
        try {
            return json.readValue(body, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
