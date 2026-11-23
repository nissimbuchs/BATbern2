package ch.batbern.agent.config;

import ch.batbern.agent.security.SecurityErrorWriter;
import ch.batbern.shared.security.BatbernJwtDecoder;
import ch.batbern.shared.security.JwtRolesConverter;
import ch.batbern.shared.security.TokenKind;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.EnumSet;

/**
 * Security of agent-service. Roles are checked here, never in the gateways (ADR-008).
 *
 * <ul>
 *   <li>Decoder: Cognito tokens only. An agent token ({@code iss batbern-agent}, carrying
 *       {@code act}) is refused with 401: the agent never reads its own decision log (R8).</li>
 *   <li>Roles: from {@code custom:role} only. The converter gets no DataSource, so the local-dev DB
 *       fallback of {@link JwtRolesConverter} is off: it reads CUMS tables, and agent-service reads
 *       no other service's tables (ADR-016).</li>
 *   <li>Method security in every profile; every endpoint carries {@code @PreAuthorize}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /** Local development: every request passes the chain, tokens are still parsed, method security holds. */
    @Bean
    @Profile("local")
    public SecurityFilterChain localFilterChain(HttpSecurity http,
                                                JwtDecoder jwtDecoder,
                                                JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .decoder(jwtDecoder)
                .jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    /** Production chain, in every profile but {@code local}: the integration tests run it too. */
    @Bean
    @Profile("!local")
    public SecurityFilterChain productionFilterChain(HttpSecurity http,
                                                     JwtDecoder jwtDecoder,
                                                     JwtAuthenticationConverter jwtAuthenticationConverter,
                                                     SecurityErrorWriter errors)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                // The whole read API of agent-api.openapi.yml; each operation checks again, and
                // refuses a token carrying act (ConsoleAccess)
                .requestMatchers(HttpMethod.GET, "/api/v1/agent/**").hasRole("ORGANIZER")
                .anyRequest().authenticated())
            // 401 and 403 answer with the shared ErrorResponse, as the spec declares
            .exceptionHandling(handling -> handling.authenticationEntryPoint(errors).accessDeniedHandler(errors))
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationEntryPoint(errors)
                .accessDeniedHandler(errors)
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder)
                    .jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    /**
     * The shared decoder with Cognito only, unless {@code batbern.security.token-kinds} overrides
     * (it is set nowhere for agent-service).
     */
    @Bean
    public JwtDecoder jwtDecoder(Environment environment) {
        return BatbernJwtDecoder.fromEnvironment(environment, EnumSet.of(TokenKind.COGNITO));
    }

    /** Roles from {@code custom:role}; no DataSource, so no DB fallback into CUMS tables. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRolesConverter(null));
        return converter;
    }
}
