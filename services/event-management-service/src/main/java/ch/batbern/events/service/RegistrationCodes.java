package ch.batbern.events.service;

import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.function.Predicate;

/**
 * The one producer of registration codes: {@code {eventCode}-reg-{6 of A-Z0-9}}, e.g.
 * {@code BATbern142-reg-A3X9K2}. The code is the access token of an anonymous registration, hence
 * {@link SecureRandom}.
 *
 * <p>{@code registrationCode} in {@code docs/api/event-registrations-api.openapi.yml} must accept
 * what this produces, plus the older shapes still stored (V4 backfill and a 2026-03 data load);
 * {@code SpecPatternsAcceptProducedValuesIntegrationTest} checks both. RegistrationService and
 * SpeakerAutoRegistrationService each had a copy of this until 17.0 deferred work.
 */
@Slf4j
public final class RegistrationCodes {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SUFFIX_LENGTH = 6;
    private static final int MAX_COLLISION_RETRIES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RegistrationCodes() {
    }

    /**
     * A new code for {@code eventCode} that {@code taken} does not report as used, retried on a
     * collision.
     *
     * @throws IllegalStateException when every retry collided
     */
    public static String newUnique(String eventCode, Predicate<String> taken) {
        for (int attempt = 0; attempt < MAX_COLLISION_RETRIES; attempt++) {
            String code = eventCode + "-reg-" + randomSuffix();
            if (!taken.test(code)) {
                return code;
            }
            log.warn("Registration code collision on attempt {}: {}", attempt + 1, code);
        }
        throw new IllegalStateException(
                "Failed to generate unique registration code after " + MAX_COLLISION_RETRIES + " attempts");
    }

    private static String randomSuffix() {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return suffix.toString();
    }
}
