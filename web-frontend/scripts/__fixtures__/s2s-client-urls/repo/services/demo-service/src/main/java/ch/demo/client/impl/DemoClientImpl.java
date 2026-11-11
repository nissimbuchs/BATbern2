package ch.demo.client.impl;

/**
 * Calls GET /api/v1/ignored-in-javadoc on the peer.
 */
public class DemoClientImpl {

    // A comment naming "/api/v1/ignored-in-comment" does not count.
    private static final String INTERNAL = "/internal/things";

    String publicCall(String base) {
        return base + "/api/v1/x";
    }

    String internalCall(String base) {
        return base + INTERNAL + "/{name}";
    }

    String withQuery(String base) {
        return base + "/api/v1/partners?include=company&size=200";
    }

    String textBlock() {
        return """
                GET /api/v1/in-text-block
                """;
    }

    char quote() {
        return '"';
    }

    String afterChar() {
        return "/api/v1/after-char";
    }
}
