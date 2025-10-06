package ch.batbern.shared.util;

import java.util.UUID;

public class CorrelationIdGenerator {
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static String getHeaderName() {
        return CORRELATION_ID_HEADER;
    }
}
