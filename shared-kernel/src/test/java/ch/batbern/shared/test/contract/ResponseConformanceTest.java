package ch.batbern.shared.test.contract;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The response conformance gate on its own (the services' ITs run it on every response). */
class ResponseConformanceTest {

    private static final String SPEC = "docs/api/companies-api.openapi.yml";
    private static final String FIXTURES = "shared-kernel/src/test/resources/contract/";
    private static final String VALID = """
            {"name":"swissit","displayName":"Swiss IT","isVerified":true,
             "createdAt":"2026-01-01T10:00:00Z","updatedAt":"2026-01-01T10:00:00Z","createdBy":"lead.organizer"}""";
    private static final String BAD_NAME = """
            {"name":"Swiss IT AG","displayName":"Swiss IT","isVerified":true,
             "createdAt":"2026-01-01T10:00:00Z","updatedAt":"2026-01-01T10:00:00Z","createdBy":"lead.organizer"}""";
    private static final String MISSING_UPDATED_AT = """
            {"name":"swissit","isVerified":true,"createdAt":"2026-01-01T10:00:00Z","createdBy":"lead.organizer"}""";

    @TempDir
    Path tmp;

    private ResponseConformance gate(Set<String> baseline, boolean update) {
        return new ResponseConformance(List.of(OpenApiContract.load(SPEC)), baseline, tmp, update);
    }

    private static MvcResult result(String method, String uri, int status, String contentType, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        if (contentType != null) {
            response.setContentType(contentType);
        }
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.getWriter().write(body);
            response.getWriter().flush();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        MvcResult result = mock(MvcResult.class);
        when(result.getRequest()).thenReturn(request);
        when(result.getResponse()).thenReturn(response);
        return result;
    }

    @Test
    void should_pass_when_responseMatchesSpec() {
        assertThatCode(() -> gate(Set.of(), false)
                .verify(result("GET", "/api/v1/companies/swissit", 200, "application/json", VALID)))
                .doesNotThrowAnyException();
    }

    @Test
    void should_fail_when_violationIsNotInBaseline() {
        assertThatThrownBy(() -> gate(Set.of(), false)
                .verify(result("GET", "/api/v1/companies/swissit", 200, "application/json", BAD_NAME)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("getCompany /name pattern")
                .hasMessageContaining("response-conformance-baseline.txt");
    }

    @Test
    void should_acceptAndRecord_when_violationIsInBaseline() throws Exception {
        gate(Set.of("getCompany /name pattern"), false)
                .verify(result("GET", "/api/v1/companies/swissit", 200, "application/json", BAD_NAME));

        assertThat(hits()).containsExactly("getCompany /name pattern");
    }

    @Test
    void should_nameTheMissingProperty_when_requiredPropertyMissing() {
        assertThatThrownBy(() -> gate(Set.of(), false)
                .verify(result("GET", "/api/v1/companies/swissit", 200, "application/json", MISSING_UPDATED_AT)))
                .hasMessageContaining("getCompany / required:updatedAt");
    }

    @Test
    void should_writeArrayIndicesAsStar_when_violationIsInsideAList() {
        String page = "{\"data\":[" + VALID + "," + BAD_NAME + "],"
                + "\"pagination\":{\"page\":1,\"limit\":20,\"totalItems\":2,\"totalPages\":1,"
                + "\"hasNext\":false,\"hasPrevious\":false}}";

        assertThatThrownBy(() -> gate(Set.of(), false)
                .verify(result("GET", "/api/v1/companies", 200, "application/json", page)))
                .hasMessageContaining("listCompanies /data/*/name pattern");
    }

    @Test
    void should_recordEverything_when_updating() throws Exception {
        gate(Set.of(), true)
                .verify(result("GET", "/api/v1/companies/swissit", 200, "application/json", BAD_NAME));

        assertThat(hits()).containsExactly("getCompany /name pattern");
    }

    @Test
    void should_ignore_when_notASpecOperationOrNotA2xxJsonResponse() {
        ResponseConformance gate = gate(Set.of(), false);
        assertThatCode(() -> {
            gate.verify(result("GET", "/actuator/health", 200, "application/json", "{\"status\":\"UP\"}"));
            gate.verify(result("GET", "/api/v1/companies/swissit", 404, "application/json", "{\"x\":1}"));
            gate.verify(result("GET", "/api/v1/companies/swissit", 200, "text/csv", "name\nswissit"));
            gate.verify(result("GET", "/api/v1/companies/swissit", 200, "application/json", ""));
        }).doesNotThrowAnyException();
    }

    @Test
    void should_pickTheMostSpecificTemplate_when_severalSpecsMatch() {
        // Synthetic pair: since Story 4.10b no docs/api spec has a literal that another spec's
        // template also matches (the /events literals left the {eventCode} namespace), so the
        // rule is shown on two fixture specs: /events/archive in one, /events/{eventCode} in the other.
        OpenApiContract template = OpenApiContract.load(FIXTURES + "template-spec.openapi.yml");
        OpenApiContract literal = OpenApiContract.load(FIXTURES + "literal-spec.openapi.yml");
        ResponseConformance gate = new ResponseConformance(List.of(template, literal), Set.of(), tmp, false);

        assertThat(template.operationVariables("GET", "/api/v1/events/archive")).isPresent();
        assertThat(gate.owner("GET", "/api/v1/events/archive")).contains(literal);
        assertThat(gate.owner("GET", "/api/v1/events/BATbern57")).contains(template);
    }

    @Test
    void should_readBaselineIgnoringCommentsAndBlankLines() throws Exception {
        Path file = tmp.resolve("baseline.txt");
        Files.writeString(file, "# header\n\ngetCompany /name pattern\n  listCompanies /data/*/name pattern \n");

        assertThat(ResponseConformance.readBaseline(file))
                .containsExactly("getCompany /name pattern", "listCompanies /data/*/name pattern");
    }

    private List<String> hits() throws Exception {
        try (var files = Files.list(tmp)) {
            Path hits = files.filter(p -> p.getFileName().toString().startsWith("hits-")).findFirst().orElseThrow();
            return Files.readAllLines(hits).stream().filter(l -> !l.isBlank()).toList();
        }
    }
}
