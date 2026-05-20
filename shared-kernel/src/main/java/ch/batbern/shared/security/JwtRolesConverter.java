package ch.batbern.shared.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * JWT → Spring Security {@link GrantedAuthority} converter with a database fallback.
 *
 * <p><b>Primary path (staging / production):</b> roles come from the JWT's
 * {@code custom:role} claim (comma-separated), populated by the
 * PreTokenGeneration Lambda from the {@code role_assignments} table. The
 * Watch-app JWT variant uses the singular {@code role} claim. Result is
 * mapped to {@code ROLE_<X>} authorities used by {@code @PreAuthorize}.
 *
 * <p><b>Fallback path (local development):</b> in local dev, CUMS provisions
 * a speaker by creating the Cognito user in staging Cognito but writing the
 * {@code user_profiles}/{@code role_assignments} rows into the LOCAL DB only.
 * The PreTokenGen Lambda runs against the staging DB, finds nothing, and the
 * JWT comes back without {@code custom:role}. Without a fallback, every
 * locally-promoted speaker hits a blank dashboard. When both role claims are
 * empty, this converter looks the user up by {@code sub == cognito_user_id}
 * directly against the local DB. In staging the JWT always carries roles, so
 * this branch is dormant.
 *
 * <p>Any database error returns an empty authority list — identical to the
 * pre-fallback behavior, preserving graceful degradation if the DB is briefly
 * unreachable during a token validation.
 */
public class JwtRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtRolesConverter.class);

    private static final String ROLE_LOOKUP_SQL =
            "SELECT ra.role "
          + "FROM user_profiles u "
          + "JOIN role_assignments ra ON ra.user_id = u.id "
          + "WHERE u.cognito_user_id = ?";

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param dataSource the data source the DB fallback queries; may be {@code null}
     *                   (e.g. in {@code @WebMvcTest} slices that don't include JPA).
     *                   With a null data source the converter still validates the
     *                   primary {@code custom:role} / {@code role} claims; only the
     *                   DB fallback is disabled. Production / staging / dev always
     *                   supply a real data source, so this only matters for test
     *                   slices.
     */
    public JwtRolesConverter(DataSource dataSource) {
        this.jdbcTemplate = dataSource != null ? new JdbcTemplate(dataSource) : null;
        if (dataSource == null) {
            LOGGER.warn("JwtRolesConverter constructed without a DataSource — DB fallback "
                    + "disabled. Expected only in @WebMvcTest slices; production must supply "
                    + "a DataSource.");
        }
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        String rolesString = jwt.getClaimAsString("custom:role");
        if (rolesString == null || rolesString.isEmpty()) {
            rolesString = jwt.getClaimAsString("role"); // Watch JWT
        }

        if (rolesString != null && !rolesString.isEmpty()) {
            return toAuthorities(Arrays.stream(rolesString.split(",")));
        }

        return fetchRolesFromDb(jwt.getSubject());
    }

    private Collection<GrantedAuthority> fetchRolesFromDb(String cognitoUserId) {
        if (cognitoUserId == null || cognitoUserId.isEmpty()) {
            return Collections.emptyList();
        }
        if (jdbcTemplate == null) {
            // Constructed without a DataSource (test slice) — fallback is dormant.
            return Collections.emptyList();
        }
        try {
            List<String> roles = jdbcTemplate.queryForList(ROLE_LOOKUP_SQL, String.class, cognitoUserId);
            if (roles.isEmpty()) {
                LOGGER.debug("JWT roles fallback: no role_assignments for cognito_user_id={}", cognitoUserId);
                return Collections.emptyList();
            }
            LOGGER.info("JWT roles fallback hit for cognito_user_id={} — roles={} "
                    + "(local-dev path; staging JWTs always carry custom:role)",
                    cognitoUserId, roles);
            return toAuthorities(roles.stream());
        } catch (Exception e) {
            LOGGER.warn("JWT roles fallback failed for cognito_user_id={}: {}",
                    cognitoUserId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private static Collection<GrantedAuthority> toAuthorities(Stream<String> roles) {
        return roles
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }
}
