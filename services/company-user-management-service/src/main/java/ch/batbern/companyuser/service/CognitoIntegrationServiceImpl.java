package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.GetOrCreateUserRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of CognitoIntegrationService
 * Story 1.14-2 Task 12: Cognito Integration
 * AC2: Cognito sync on user create/update
 */
@Slf4j
@Service
public class CognitoIntegrationServiceImpl implements CognitoIntegrationService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final String userPoolId;

    // Constructor for Spring (with @Value injection)
    public CognitoIntegrationServiceImpl(
            CognitoIdentityProviderClient cognitoClient,
            @Value("${aws.cognito.user-pool-id}") String userPoolId) {
        this.cognitoClient = cognitoClient;
        this.userPoolId = userPoolId;
    }

    @Override
    public void syncUserAttributes(User user) {
        // NO-OP: DB is source of truth for user attributes
        // Cognito is only used for authentication (JWT tokens)
        log.debug("Cognito sync disabled - DB is source of truth for user: {}", user.getUsername());
    }

    @Override
    public String createCognitoUser(GetOrCreateUserRequest request) {
        // NO-OP: Invitation-based flow
        // User will sign up via registration page, Cognito hook will populate cognitoUserId
        log.debug("Cognito user creation disabled - invitation flow for user: {}", request.getEmail());
        return null;  // cognitoUserId will be populated on first login
    }

    /**
     * Build list of Cognito user attributes
     * @param email User email
     * @param firstName User first name
     * @param lastName User last name
     * @param companyId Company ID (nullable)
     * @param roles User roles
     * @return List of AttributeType for Cognito
     */
    private List<AttributeType> buildUserAttributes(
            String email,
            String firstName,
            String lastName,
            String companyId,
            Set<Role> roles) {

        List<AttributeType> attributes = new ArrayList<>();

        attributes.add(AttributeType.builder()
                .name("email")
                .value(email)
                .build());

        attributes.add(AttributeType.builder()
                .name("given_name")
                .value(firstName)
                .build());

        attributes.add(AttributeType.builder()
                .name("family_name")
                .value(lastName)
                .build());

        // Add companyId as custom attribute if present
        if (companyId != null) {
            attributes.add(AttributeType.builder()
                    .name("custom:companyId")
                    .value(companyId)
                    .build());
        }

        // Add roles as comma-separated custom attribute
        String rolesStr = roles.stream()
                .map(Role::name)
                .sorted()
                .collect(Collectors.joining(","));

        attributes.add(AttributeType.builder()
                .name("custom:roles")
                .value(rolesStr)
                .build());

        return attributes;
    }
}
