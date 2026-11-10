package ch.demo.client.impl;

/** The same public literal twice: each occurrence is a baseline line of its own. */
public class DupClientImpl {

    String first(String base) {
        return base + "/api/v1/dup";
    }

    String second(String base) {
        return base + "/api/v1/dup";
    }
}
