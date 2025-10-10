package ch.batbern.gateway.auth;

import ch.batbern.gateway.auth.model.UserContext;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserContextExtractor {

    private final ObjectMapper objectMapper;

    public UserContext extractUserContext(DecodedJWT jwt) {
        UserContext.UserContextBuilder builder = UserContext.builder()
                .userId(jwt.getSubject())
                .sessionId(jwt.getId());

        // Extract standard claims
        if (jwt.getClaim("email") != null && !jwt.getClaim("email").isNull()) {
            builder.email(jwt.getClaim("email").asString());
        }

        if (jwt.getClaim("email_verified") != null && !jwt.getClaim("email_verified").isNull()) {
            Boolean emailVerified = jwt.getClaim("email_verified").asBoolean();
            builder.emailVerified(emailVerified != null ? emailVerified : false);
        } else {
            builder.emailVerified(false);
        }

        // Extract custom claims
        if (jwt.getClaim("custom:role") != null && !jwt.getClaim("custom:role").isNull()) {
            builder.role(jwt.getClaim("custom:role").asString());
        }

        if (jwt.getClaim("custom:companyId") != null && !jwt.getClaim("custom:companyId").isNull()) {
            builder.companyId(jwt.getClaim("custom:companyId").asString());
        }

        // Extract preferences as JSON
        Map<String, Object> preferences = extractPreferences(jwt);
        builder.preferences(preferences);

        // Extract additional roles if present
        List<String> additionalRoles = extractAdditionalRoles(jwt);
        builder.additionalRoles(additionalRoles);

        // Extract token metadata
        if (jwt.getIssuedAt() != null) {
            builder.issuedAt(jwt.getIssuedAt().toInstant());
        }

        if (jwt.getExpiresAt() != null) {
            builder.expiresAt(jwt.getExpiresAt().toInstant());
        }

        return builder.build();
    }

    private Map<String, Object> extractPreferences(DecodedJWT jwt) {
        if (jwt.getClaim("custom:preferences") == null || jwt.getClaim("custom:preferences").isNull()) {
            return new HashMap<>();
        }

        String preferencesJson = jwt.getClaim("custom:preferences").asString();
        if (preferencesJson == null || preferencesJson.isEmpty()) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(preferencesJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse preferences JSON: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private List<String> extractAdditionalRoles(DecodedJWT jwt) {
        if (jwt.getClaim("custom:additionalRoles") == null || jwt.getClaim("custom:additionalRoles").isNull()) {
            return new ArrayList<>();
        }

        try {
            String[] roles = jwt.getClaim("custom:additionalRoles").asArray(String.class);
            return roles != null ? Arrays.asList(roles) : new ArrayList<>();
        } catch (Exception e) {
            log.warn("Failed to extract additional roles: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}