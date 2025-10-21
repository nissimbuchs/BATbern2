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
        log.debug("Syncing user attributes to Cognito: {}", user.getUsername());

        try {
            List<AttributeType> attributes = buildUserAttributes(
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getCompanyId(),
                    user.getRoles()
            );

            AdminUpdateUserAttributesRequest request = AdminUpdateUserAttributesRequest.builder()
                    .userPoolId(userPoolId)
                    .username(user.getCognitoUserId())
                    .userAttributes(attributes)
                    .build();

            cognitoClient.adminUpdateUserAttributes(request);
            log.info("Successfully synced user attributes to Cognito: {}", user.getUsername());

        } catch (CognitoIdentityProviderException e) {
            log.error("Failed to sync user attributes to Cognito: {}", user.getUsername(), e);
            String errorMessage = e.awsErrorDetails() != null
                    ? e.awsErrorDetails().errorMessage()
                    : e.getMessage();
            throw new RuntimeException("Failed to sync user attributes to Cognito: " + errorMessage, e);
        }
    }

    @Override
    public String createCognitoUser(GetOrCreateUserRequest request) {
        log.debug("Creating user in Cognito: {}", request.getEmail());

        try {
            // Default to ATTENDEE role if no roles specified
            Set<Role> roles = Set.of(Role.ATTENDEE);

            List<AttributeType> attributes = buildUserAttributes(
                    request.getEmail(),
                    request.getFirstName(),
                    request.getLastName(),
                    request.getCompanyId(),
                    roles
            );

            // Add email_verified attribute for new users
            attributes.add(AttributeType.builder()
                    .name("email_verified")
                    .value("true")
                    .build());

            AdminCreateUserRequest createRequest = AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(request.getEmail())  // Use email as Cognito username
                    .userAttributes(attributes)
                    .desiredDeliveryMediums(DeliveryMediumType.EMAIL)
                    .messageAction(MessageActionType.SUPPRESS)  // Don't send welcome email
                    .build();

            AdminCreateUserResponse response = cognitoClient.adminCreateUser(createRequest);
            String cognitoUserId = response.user().username();

            log.info("Successfully created user in Cognito: {}", cognitoUserId);
            return cognitoUserId;

        } catch (CognitoIdentityProviderException e) {
            log.error("Failed to create user in Cognito: {}", request.getEmail(), e);
            String errorMessage = e.awsErrorDetails() != null
                    ? e.awsErrorDetails().errorMessage()
                    : e.getMessage();
            throw new RuntimeException("Failed to create user in Cognito: " + errorMessage, e);
        }
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
