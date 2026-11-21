package ch.batbern.shared.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The claim rules of an agent token (agentic interaction, AD-3), applied after the signature, issuer
 * and timestamp checks of {@link BatbernJwtDecoder}:
 *
 * <ul>
 *   <li>{@code sub}, {@code custom:username}, {@code iat} and {@code exp} are present;</li>
 *   <li>{@code exp - iat} is at most {@link #MAX_LIFETIME} (300 s): an agent token is short-lived;
 *       {@code iat} is not after {@code exp} and not later than now plus {@link #CLOCK_SKEW}, so a
 *       token dated into the future cannot stretch its lifetime;</li>
 *   <li>{@code act} is an object with a non-blank {@code run} and {@code principal} (who the agent
 *       acts for, in which run);</li>
 *   <li>no {@code custom:role} and no {@code role} claim: an agent's roles come from
 *       {@code role_assignments} by its {@code sub}, a token never carries its own.</li>
 * </ul>
 */
public final class AgentTokenValidator implements OAuth2TokenValidator<Jwt> {

    /** Longest lifetime ({@code exp - iat}) an agent token may have. */
    public static final Duration MAX_LIFETIME = Duration.ofSeconds(300);

    /** How far {@code iat} may lie in the future (clock skew between issuer and service). */
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private final Clock clock;

    /** Validator on the system clock (UTC). */
    public AgentTokenValidator() {
        this(Clock.systemUTC());
    }

    /** Validator on {@code clock}, for tests. */
    public AgentTokenValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<OAuth2Error> errors = new ArrayList<>();
        requireText(jwt.getSubject(), "sub", errors);
        requireText(stringClaim(jwt, "custom:username"), "custom:username", errors);

        Instant issuedAt = jwt.getIssuedAt();
        Instant expiresAt = jwt.getExpiresAt();
        if (issuedAt == null) {
            errors.add(error("Agent token has no iat"));
        }
        if (expiresAt == null) {
            errors.add(error("Agent token has no exp"));
        }
        if (issuedAt != null && expiresAt != null) {
            if (issuedAt.isAfter(expiresAt)) {
                errors.add(error("Agent token has iat after exp"));
            } else if (Duration.between(issuedAt, expiresAt).compareTo(MAX_LIFETIME) > 0) {
                errors.add(error("Agent token lives longer than " + MAX_LIFETIME.toSeconds() + " s"));
            }
        }
        if (issuedAt != null && issuedAt.isAfter(clock.instant().plus(CLOCK_SKEW))) {
            errors.add(error("Agent token has iat in the future"));
        }

        Object act = jwt.getClaims().get("act");
        if (act instanceof Map<?, ?> actor) {
            requireText(asText(actor.get("run")), "act.run", errors);
            requireText(asText(actor.get("principal")), "act.principal", errors);
        } else {
            errors.add(error("Agent token has no act object"));
        }

        for (String roleClaim : List.of("custom:role", "role")) {
            if (jwt.hasClaim(roleClaim)) {
                errors.add(error("Agent token must not carry " + roleClaim));
            }
        }
        return errors.isEmpty() ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(errors);
    }

    private static String stringClaim(Jwt jwt, String name) {
        return asText(jwt.getClaims().get(name));
    }

    private static String asText(Object value) {
        return value instanceof String text ? text : null;
    }

    private static void requireText(String value, String name, List<OAuth2Error> errors) {
        if (value == null || value.isBlank()) {
            errors.add(error("Agent token has no " + name));
        }
    }

    private static OAuth2Error error(String description) {
        return new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null);
    }
}
