package ch.batbern.agent.gateway;

import org.springframework.http.HttpMethod;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A public operation, named by its method and its path as {@code docs/api/*.openapi.yml} writes it,
 * below the {@code /api/v1} base path: {@code GET /events/{eventCode}/sessions}.
 *
 * @param method the HTTP method
 * @param path   the spec path template; a {@code {name}} segment stands for one path segment
 */
public record Operation(HttpMethod method, String path) {

    private static final Pattern TEMPLATE = Pattern.compile("^(/([A-Za-z0-9._-]+|\\{[A-Za-z][A-Za-z0-9]*}))+$");

    public Operation {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        if (!TEMPLATE.matcher(path).matches()) {
            throw new IllegalArgumentException("Not a spec path template: " + path);
        }
    }

    /** {@code "GET /events"}. */
    public static Operation of(String methodAndPath) {
        String[] parts = methodAndPath.trim().split(" +", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected \"METHOD /path\": " + methodAndPath);
        }
        return new Operation(HttpMethod.valueOf(parts[0]), parts[1]);
    }

    /** Whether this operation is GET (a read on the wire; its class still comes from the table). */
    public boolean isGet() {
        return HttpMethod.GET.equals(method);
    }

    /**
     * Whether a concrete call is this operation: same method, and the path (its query ignored) has
     * the template's segments, a {@code {name}} segment matching any one non-empty segment.
     */
    public boolean matches(HttpMethod callMethod, String concretePath) {
        if (!method.equals(callMethod) || concretePath == null) {
            return false;
        }
        int query = concretePath.indexOf('?');
        String callPath = query < 0 ? concretePath : concretePath.substring(0, query);
        String[] template = path.substring(1).split("/", -1);
        String[] call = callPath.startsWith("/") ? callPath.substring(1).split("/", -1) : new String[0];
        if (template.length != call.length) {
            return false;
        }
        for (int i = 0; i < template.length; i++) {
            boolean variable = template[i].startsWith("{");
            if (variable ? call[i].isEmpty() : !template[i].equals(call[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return method.name() + " " + path;
    }
}
