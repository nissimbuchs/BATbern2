package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserRequest;

/**
 * Service for integrating with AWS Cognito
 * Story 1.14-2 AC2: Cognito sync on user create/update
 */
public interface CognitoIntegrationService {

    /**
     * Sync user attributes to Cognito
     * @param user User to sync
     */
    void syncUserAttributes(User user);

    /**
     * Create user in Cognito
     * @param request User creation request
     * @return Cognito user ID
     */
    String createCognitoUser(GetOrCreateUserRequest request);
}
