package ch.batbern.shared.test.contract;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Provider half of the S2S contract harness (Story 17.0b1; internal specs 17.0b2): proves that what a consumer's
 * {@link ContractServer} answers with is what the real controller produces.
 *
 * <ul>
 *   <li>{@link #assertResponseMatchesSpec} checks a real {@code MockMvc} response of the
 *       provider's controller against the spec's response schema.</li>
 *   <li>{@link #assertExamplesMatchSchema} checks every JSON response example of an operation
 *       against its own schema, so the canned answers the consumers get are valid.</li>
 * </ul>
 */
public final class ProviderContract {

    private ProviderContract() {
    }

    /** Fails unless the real response is valid for its operation in {@code specPath}. */
    public static void assertResponseMatchesSpec(String specPath, MvcResult result) {
        OpenApiContract contract = OpenApiContract.load(specPath);
        MockHttpServletRequest request = result.getRequest();
        MockHttpServletResponse response = result.getResponse();
        String method = request.getMethod();
        String path = request.getRequestURI();
        contract.requireOperation(method, path);
        String body = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        List<String> errors = contract.validateResponse(method, path, response.getStatus(), body);
        if (!errors.isEmpty()) {
            throw new AssertionError("Real response of " + method + " " + path + " (" + response.getStatus()
                    + ") violates " + specPath + ":\n  - " + String.join("\n  - ", errors) + "\nBody: " + body);
        }
    }

    /**
     * Fails unless every JSON response example of the operation (path as a spec template, base
     * path included) is valid against its schema, and unless the success response, when it has a
     * JSON body (not a bodiless 202 or 204), has one.
     */
    public static void assertExamplesMatchSchema(String specPath, String method, String pathTemplate) {
        OpenApiContract contract = OpenApiContract.load(specPath);
        int success = contract.successStatus(method, pathTemplate);
        if (contract.hasJsonBody(method, pathTemplate, success)) {
            contract.example(method, pathTemplate, success, null);
        }
        String concrete = pathTemplate.replaceAll("\\{[^}]+}", "x");
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> e : contract.responseExamples(method, pathTemplate).entrySet()) {
            String code = e.getKey().substring(0, e.getKey().indexOf(' '));
            if (!code.matches("\\d{3}")) {
                continue;
            }
            int status = Integer.parseInt(code);
            List<String> errors = contract.validateResponse(method, concrete, status, e.getValue());
            errors.forEach(err -> problems.add(e.getKey() + ": " + err));
        }
        if (!problems.isEmpty()) {
            throw new AssertionError("Examples of " + method + " " + pathTemplate + " in " + specPath
                    + " violate their schema:\n  - " + String.join("\n  - ", problems));
        }
    }
}
