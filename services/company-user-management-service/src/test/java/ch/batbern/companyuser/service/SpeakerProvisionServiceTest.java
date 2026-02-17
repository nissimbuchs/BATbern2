package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.SpeakerProvisionRequest;
import ch.batbern.companyuser.dto.SpeakerProvisionResponse;
import ch.batbern.companyuser.dto.SpeakerProvisionResponse.AccountAction;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SpeakerProvisionService
 * Story 9.2: Automatic Account Creation & Role Extension on Invitation Acceptance
 *
 * Tests cover:
 * - NEW branch: Cognito account creation with temporary password and compensating transaction
 * - EXTENDED branch: SPEAKER role added to existing Cognito account
 * - generateSecureTemporaryPassword: policy compliance (length, character classes)
 * - UserNotFoundException propagation when no local DB user found
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpeakerProvisionService Unit Tests")
class SpeakerProvisionServiceTest {

    @Mock
    private CognitoIntegrationService cognitoService;

    @Mock
    private RoleService roleService;

    @Mock
    private UserRepository userRepository;

    private SpeakerProvisionService service;

    private SpeakerProvisionRequest request;
    private User localUser;

    @BeforeEach
    void setUp() {
        service = new SpeakerProvisionService(cognitoService, roleService, userRepository);

        request = new SpeakerProvisionRequest();
        request.setEmail("jane@techcorp.ch");
        request.setFirstName("Jane");
        request.setLastName("Speaker");

        localUser = new User();
        localUser.setUsername("jane.speaker");
        localUser.setEmail("jane@techcorp.ch");
    }

    // ==================== NEW Account Branch ====================

    @Nested
    @DisplayName("NEW: Cognito account does not exist")
    class NewAccountTests {

        @Test
        @DisplayName("should return NEW action with temporary password when no Cognito user exists")
        void should_returnNew_withTemporaryPassword_when_noCognitoUser() {
            // Given
            when(cognitoService.findUserByEmail("jane@techcorp.ch")).thenReturn(Optional.empty());
            when(cognitoService.createCognitoSpeaker(anyString(), anyString(), anyString()))
                    .thenReturn("cognito-sub-123");
            when(userRepository.findByEmail("jane@techcorp.ch")).thenReturn(Optional.of(localUser));

            // When
            SpeakerProvisionResponse response = service.provision(request);

            // Then
            assertThat(response.getAction()).isEqualTo(AccountAction.NEW);
            assertThat(response.getCognitoUserId()).isEqualTo("cognito-sub-123");
            assertThat(response.getUsername()).isEqualTo("jane.speaker");
            assertThat(response.getTemporaryPassword()).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("should add SPEAKER role to local DB for new account")
        void should_addSpeakerRole_to_localDB_for_newAccount() {
            // Given
            when(cognitoService.findUserByEmail("jane@techcorp.ch")).thenReturn(Optional.empty());
            when(cognitoService.createCognitoSpeaker(anyString(), anyString(), anyString()))
                    .thenReturn("cognito-sub-123");
            when(userRepository.findByEmail("jane@techcorp.ch")).thenReturn(Optional.of(localUser));

            // When
            service.provision(request);

            // Then
            verify(roleService).addRole("jane.speaker", Role.SPEAKER);
        }

        @Test
        @DisplayName("should concatenate firstName + lastName as name passed to createCognitoSpeaker")
        void should_passCorrectName_to_createCognitoSpeaker() {
            // Given
            when(cognitoService.findUserByEmail("jane@techcorp.ch")).thenReturn(Optional.empty());
            when(cognitoService.createCognitoSpeaker(anyString(), anyString(), anyString()))
                    .thenReturn("cognito-sub-123");
            when(userRepository.findByEmail("jane@techcorp.ch")).thenReturn(Optional.of(localUser));

            // When
            service.provision(request);

            // Then
            verify(cognitoService).createCognitoSpeaker(eq("jane@techcorp.ch"), eq("Jane Speaker"), anyString());
        }

        @Test
        @DisplayName("should rollback Cognito account and throw when local DB update fails (compensating transaction)")
        void should_rollbackCognito_when_localDBUpdateFails() {
            // Given
            when(cognitoService.findUserByEmail("jane@techcorp.ch")).thenReturn(Optional.empty());
            when(cognitoService.createCognitoSpeaker(anyString(), anyString(), anyString()))
                    .thenReturn("cognito-sub-456");
            when(userRepository.findByEmail("jane@techcorp.ch"))
                    .thenThrow(new UserNotFoundException("No local user found"));

            // When / Then
            assertThatThrownBy(() -> service.provision(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Speaker provisioning rolled back");

            // Compensating transaction: Cognito account must be deleted
            verify(cognitoService).deleteCognitoAccount("cognito-sub-456");
        }

        @Test
        @DisplayName("should rollback Cognito account when roleService.addRole throws")
        void should_rollbackCognito_when_roleServiceFails() {
            // Given
            when(cognitoService.findUserByEmail("jane@techcorp.ch")).thenReturn(Optional.empty());
            when(cognitoService.createCognitoSpeaker(anyString(), anyString(), anyString()))
                    .thenReturn("cognito-sub-789");
            when(userRepository.findByEmail("jane@techcorp.ch")).thenReturn(Optional.of(localUser));
            org.mockito.Mockito.doThrow(new RuntimeException("DB constraint violation"))
                    .when(roleService).addRole("jane.speaker", Role.SPEAKER);

            // When / Then
            assertThatThrownBy(() -> service.provision(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Speaker provisioning rolled back");

            verify(cognitoService).deleteCognitoAccount("cognito-sub-789");
        }

        @Test
        @DisplayName("should NOT call addRoleToCognitoUser for new accounts")
        void should_not_callAddRoleToCognito_for_newAccount() {
            // Given
            when(cognitoService.findUserByEmail("jane@techcorp.ch")).thenReturn(Optional.empty());
            when(cognitoService.createCognitoSpeaker(anyString(), anyString(), anyString()))
                    .thenReturn("cognito-sub-123");
            when(userRepository.findByEmail("jane@techcorp.ch")).thenReturn(Optional.of(localUser));

            // When
            service.provision(request);

            // Then
            verify(cognitoService, never()).addRoleToCognitoUser(any(), any());
        }
    }

    // ==================== EXTENDED Account Branch ====================

    @Nested
    @DisplayName("EXTENDED: Cognito account already exists")
    class ExtendedAccountTests {

        private AdminGetUserResponse existingCognitoUser;

        @BeforeEach
        void setUpExistingUser() {
            existingCognitoUser = AdminGetUserResponse.builder()
                    .username("existing-cognito-sub")
                    .build();
        }

        @Test
        @DisplayName("should return EXTENDED action with null password when Cognito user exists")
        void should_returnExtended_withNullPassword_when_cognitoUserExists() {
            // Given
            when(cognitoService.findUserByEmail("jane@techcorp.ch"))
                    .thenReturn(Optional.of(existingCognitoUser));
            when(userRepository.findByEmail("jane@techcorp.ch")).thenReturn(Optional.of(localUser));

            // When
            SpeakerProvisionResponse response = service.provision(request);

            // Then
            assertThat(response.getAction()).isEqualTo(AccountAction.EXTENDED);
            assertThat(response.getCognitoUserId()).isEqualTo("existing-cognito-sub");
            assertThat(response.getTemporaryPassword()).isNull();
            assertThat(response.getUsername()).isEqualTo("jane.speaker");
        }

        @Test
        @DisplayName("should add SPEAKER role to Cognito for extended account")
        void should_addSpeakerRoleToCognito_for_extendedAccount() {
            // Given
            when(cognitoService.findUserByEmail("jane@techcorp.ch"))
                    .thenReturn(Optional.of(existingCognitoUser));
            when(userRepository.findByEmail("jane@techcorp.ch")).thenReturn(Optional.of(localUser));

            // When
            service.provision(request);

            // Then
            verify(cognitoService).addRoleToCognitoUser("jane@techcorp.ch", Role.SPEAKER);
        }

        @Test
        @DisplayName("should add SPEAKER role to local DB for extended account")
        void should_addSpeakerRole_to_localDB_for_extendedAccount() {
            // Given
            when(cognitoService.findUserByEmail("jane@techcorp.ch"))
                    .thenReturn(Optional.of(existingCognitoUser));
            when(userRepository.findByEmail("jane@techcorp.ch")).thenReturn(Optional.of(localUser));

            // When
            service.provision(request);

            // Then
            verify(roleService).addRole("jane.speaker", Role.SPEAKER);
        }

        @Test
        @DisplayName("should NOT call createCognitoSpeaker for extended accounts")
        void should_not_callCreateCognitoSpeaker_for_extendedAccount() {
            // Given
            when(cognitoService.findUserByEmail("jane@techcorp.ch"))
                    .thenReturn(Optional.of(existingCognitoUser));
            when(userRepository.findByEmail("jane@techcorp.ch")).thenReturn(Optional.of(localUser));

            // When
            service.provision(request);

            // Then
            verify(cognitoService, never()).createCognitoSpeaker(any(), any(), any());
        }

        @Test
        @DisplayName("should propagate UserNotFoundException when no local DB user found")
        void should_propagateUserNotFoundException_when_noLocalUser() {
            // Given
            when(cognitoService.findUserByEmail("jane@techcorp.ch"))
                    .thenReturn(Optional.of(existingCognitoUser));
            when(userRepository.findByEmail("jane@techcorp.ch")).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> service.provision(request))
                    .isInstanceOf(UserNotFoundException.class);

            // No compensating transaction for EXTENDED accounts
            verify(cognitoService, never()).deleteCognitoAccount(any());
        }
    }

    // ==================== generateSecureTemporaryPassword ====================

    @Nested
    @DisplayName("generateSecureTemporaryPassword: Cognito policy compliance")
    class PasswordGenerationTests {

        @Test
        @DisplayName("should generate password with exactly 20 characters")
        void should_generatePassword_with_20Characters() {
            String password = service.generateSecureTemporaryPassword();
            assertThat(password).hasSize(20);
        }

        @Test
        @DisplayName("should include at least one uppercase letter")
        void should_includeUppercase() {
            // Run multiple times to avoid random failures
            for (int i = 0; i < 20; i++) {
                String password = service.generateSecureTemporaryPassword();
                assertThat(password).matches(".*[A-Z].*");
            }
        }

        @Test
        @DisplayName("should include at least one lowercase letter")
        void should_includeLowercase() {
            for (int i = 0; i < 20; i++) {
                String password = service.generateSecureTemporaryPassword();
                assertThat(password).matches(".*[a-z].*");
            }
        }

        @Test
        @DisplayName("should include at least one digit")
        void should_includeDigit() {
            for (int i = 0; i < 20; i++) {
                String password = service.generateSecureTemporaryPassword();
                assertThat(password).matches(".*[0-9].*");
            }
        }

        @Test
        @DisplayName("should include at least one special character")
        void should_includeSpecialCharacter() {
            for (int i = 0; i < 20; i++) {
                String password = service.generateSecureTemporaryPassword();
                assertThat(password).containsPattern("[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?]");
            }
        }

        @Test
        @DisplayName("should generate different passwords on each call (entropy check)")
        void should_generateDifferentPasswords_on_eachCall() {
            String p1 = service.generateSecureTemporaryPassword();
            String p2 = service.generateSecureTemporaryPassword();
            String p3 = service.generateSecureTemporaryPassword();

            // Not all three identical — probability of collision is astronomically small
            assertThat(new java.util.HashSet<>(java.util.List.of(p1, p2, p3))).hasSizeGreaterThan(1);
        }
    }
}
