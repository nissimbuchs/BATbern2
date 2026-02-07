package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.dto.ProfileUpdateRequest;
import ch.batbern.events.dto.SpeakerProfileDto;
import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.InvalidTokenException;
import ch.batbern.events.exception.SpeakerNotFoundException;
import ch.batbern.events.repository.SpeakerRepository;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SpeakerProfileService
 * Story 6.2b: Speaker Profile Update Portal - Task 1 (RED Phase)
 *
 * RED PHASE (TDD): These tests will FAIL until SpeakerProfileService is implemented.
 *
 * Tests cover:
 * - AC1: Profile view (combined User + Speaker data)
 * - AC2: Basic profile fields update (firstName, lastName, bio)
 * - AC3-4: Expertise areas and speaking topics management
 * - AC5: Languages selection
 * - AC6: LinkedIn URL validation
 * - AC8: Profile completeness calculation
 * - AC9: Validation
 * - AC10: Cross-service sync
 */
@ExtendWith(MockitoExtension.class)
class SpeakerProfileServiceTest {

    @Mock
    private SpeakerRepository speakerRepository;

    @Mock
    private MagicLinkService magicLinkService;

    @Mock
    private UserApiClient userApiClient;

    private SpeakerProfileService speakerProfileService;

    private UUID testSpeakerId;
    private Speaker testSpeaker;
    private UserResponse testUserResponse;
    private String validToken;
    private TokenValidationResult validTokenResult;

    @BeforeEach
    void setUp() {
        speakerProfileService = new SpeakerProfileService(
                speakerRepository,
                magicLinkService,
                userApiClient
        );

        testSpeakerId = UUID.randomUUID();
        validToken = "valid-test-token-abc123";

        // Setup test Speaker
        testSpeaker = Speaker.builder()
                .id(testSpeakerId)
                .username("john.doe")
                .workflowState(SpeakerWorkflowState.ACCEPTED)
                .expertiseAreas(List.of("Cloud", "Security"))
                .speakingTopics(List.of("AWS", "DevOps"))
                .linkedInUrl("https://linkedin.com/in/johndoe")
                .languages(List.of("en", "de"))
                .build();

        // Setup test UserResponse (from Company Service)
        testUserResponse = new UserResponse();
        testUserResponse.setId("john.doe");
        testUserResponse.setEmail("john.doe@example.com");
        testUserResponse.setFirstName("John");
        testUserResponse.setLastName("Doe");
        testUserResponse.setBio("Cloud architect with 10 years experience");

        // Setup valid token result
        validTokenResult = TokenValidationResult.valid(
                UUID.randomUUID(),  // speakerPoolId
                "john.doe",         // username
                "BAT2025Q1",       // eventCode
                TokenAction.VIEW
        );
    }

    @Nested
    @DisplayName("AC1: Profile View (getProfile)")
    class GetProfileTests {

        @Test
        @DisplayName("should return combined profile when token is valid")
        void should_returnCombinedProfile_when_tokenIsValid() {
            // Given
            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));
            when(userApiClient.getUserByUsername("john.doe")).thenReturn(testUserResponse);

            // When
            SpeakerProfileDto result = speakerProfileService.getProfile(validToken);

            // Then
            assertThat(result).isNotNull();
            // User fields
            assertThat(result.getUsername()).isEqualTo("john.doe");
            assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
            assertThat(result.getFirstName()).isEqualTo("John");
            assertThat(result.getLastName()).isEqualTo("Doe");
            assertThat(result.getBio()).isEqualTo("Cloud architect with 10 years experience");
            // Speaker fields
            assertThat(result.getExpertiseAreas()).containsExactly("Cloud", "Security");
            assertThat(result.getSpeakingTopics()).containsExactly("AWS", "DevOps");
            assertThat(result.getLinkedInUrl()).isEqualTo("https://linkedin.com/in/johndoe");
            assertThat(result.getLanguages()).containsExactly("en", "de");
        }

        @Test
        @DisplayName("should throw InvalidTokenException when token is expired")
        void should_throwInvalidTokenException_when_tokenIsExpired() {
            // Given
            TokenValidationResult expiredResult = TokenValidationResult.expired();
            when(magicLinkService.validateToken(validToken)).thenReturn(expiredResult);

            // When/Then
            assertThatThrownBy(() -> speakerProfileService.getProfile(validToken))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("EXPIRED");
        }

        @Test
        @DisplayName("should throw InvalidTokenException when token is not found")
        void should_throwInvalidTokenException_when_tokenNotFound() {
            // Given
            TokenValidationResult notFoundResult = TokenValidationResult.notFound();
            when(magicLinkService.validateToken(validToken)).thenReturn(notFoundResult);

            // When/Then
            assertThatThrownBy(() -> speakerProfileService.getProfile(validToken))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("NOT_FOUND");
        }

        @Test
        @DisplayName("should throw SpeakerNotFoundException when speaker does not exist")
        void should_throwSpeakerNotFoundException_when_speakerNotExists() {
            // Given
            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> speakerProfileService.getProfile(validToken))
                    .isInstanceOf(SpeakerNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("AC8: Profile Completeness Calculation")
    class CompletenessTests {

        @Test
        @DisplayName("should calculate 100% completeness for full profile")
        void should_calculate100Percent_when_profileIsComplete() {
            // Given - full profile
            testUserResponse.setProfilePictureUrl(URI.create("https://cdn.example.com/photo.jpg"));
            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));
            when(userApiClient.getUserByUsername("john.doe")).thenReturn(testUserResponse);

            // When
            SpeakerProfileDto result = speakerProfileService.getProfile(validToken);

            // Then
            assertThat(result.getProfileCompleteness()).isEqualTo(100);
            assertThat(result.getMissingFields()).isEmpty();
        }

        @Test
        @DisplayName("should calculate correct completeness for partial profile")
        void should_calculatePartialCompleteness_when_fieldsMissing() {
            // Given - partial profile (missing bio and photo = -40%)
            testUserResponse.setBio(null);
            testUserResponse.setProfilePictureUrl(null);
            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));
            when(userApiClient.getUserByUsername("john.doe")).thenReturn(testUserResponse);

            // When
            SpeakerProfileDto result = speakerProfileService.getProfile(validToken);

            // Then
            // firstName=15 + lastName=15 + expertiseAreas=15 + languages=15 = 60%
            assertThat(result.getProfileCompleteness()).isEqualTo(60);
            assertThat(result.getMissingFields()).contains("bio", "profilePictureUrl");
        }

        @Test
        @DisplayName("should include missing fields list")
        void should_includeMissingFieldsList_when_fieldsAreMissing() {
            // Given - missing firstName and expertiseAreas
            testUserResponse.setFirstName(null);
            testSpeaker.setExpertiseAreas(List.of());
            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));
            when(userApiClient.getUserByUsername("john.doe")).thenReturn(testUserResponse);

            // When
            SpeakerProfileDto result = speakerProfileService.getProfile(validToken);

            // Then
            assertThat(result.getMissingFields()).contains("firstName", "expertiseAreas");
        }
    }

    @Nested
    @DisplayName("AC2: Basic Profile Update")
    class BasicUpdateTests {

        @Test
        @DisplayName("should update user fields via Company Service")
        void should_updateUserFields_when_patchWithUserData() {
            // Given
            ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                    .firstName("Jonathan")
                    .lastName("Smith")
                    .bio("Updated bio")
                    .build();

            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));
            when(userApiClient.getUserByUsername("john.doe")).thenReturn(testUserResponse);

            // When
            speakerProfileService.updateProfile(validToken, request);

            // Then
            verify(userApiClient).updateUser(eq("john.doe"), any());
        }

        @Test
        @DisplayName("should update speaker fields locally")
        void should_updateSpeakerFields_when_patchWithSpeakerData() {
            // Given
            ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                    .expertiseAreas(List.of("Kubernetes", "Docker"))
                    .linkedInUrl("https://linkedin.com/in/johnsmith")
                    .build();

            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));
            when(userApiClient.getUserByUsername("john.doe")).thenReturn(testUserResponse);

            // When
            speakerProfileService.updateProfile(validToken, request);

            // Then
            ArgumentCaptor<Speaker> speakerCaptor = ArgumentCaptor.forClass(Speaker.class);
            verify(speakerRepository).save(speakerCaptor.capture());

            Speaker savedSpeaker = speakerCaptor.getValue();
            assertThat(savedSpeaker.getExpertiseAreas()).containsExactly("Kubernetes", "Docker");
            assertThat(savedSpeaker.getLinkedInUrl()).isEqualTo("https://linkedin.com/in/johnsmith");
        }

        @Test
        @DisplayName("should return updated profile after save")
        void should_returnUpdatedProfile_when_updateSuccessful() {
            // Given
            ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                    .firstName("Jonathan")
                    .build();

            testUserResponse.setFirstName("Jonathan"); // Simulate updated response

            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));
            when(userApiClient.getUserByUsername("john.doe")).thenReturn(testUserResponse);

            // When
            SpeakerProfileDto result = speakerProfileService.updateProfile(validToken, request);

            // Then
            assertThat(result.getFirstName()).isEqualTo("Jonathan");
        }
    }

    @Nested
    @DisplayName("AC9: Validation")
    class ValidationTests {

        @Test
        @DisplayName("should reject bio exceeding 500 characters")
        void should_validateBioLength_when_bioExceeds500Chars() {
            // Given
            String longBio = "a".repeat(501);
            ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                    .bio(longBio)
                    .build();

            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));

            // When/Then
            assertThatThrownBy(() -> speakerProfileService.updateProfile(validToken, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("bio")
                    .hasMessageContaining("500");
        }

        @Test
        @DisplayName("should reject more than 10 expertise areas")
        void should_validateExpertiseCount_when_moreThan10Items() {
            // Given
            List<String> tooManyAreas = List.of(
                    "Area1", "Area2", "Area3", "Area4", "Area5",
                    "Area6", "Area7", "Area8", "Area9", "Area10", "Area11"
            );
            ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                    .expertiseAreas(tooManyAreas)
                    .build();

            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));

            // When/Then
            assertThatThrownBy(() -> speakerProfileService.updateProfile(validToken, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("expertiseAreas")
                    .hasMessageContaining("10");
        }

        @Test
        @DisplayName("should reject more than 10 speaking topics")
        void should_validateTopicsCount_when_moreThan10Items() {
            // Given
            List<String> tooManyTopics = List.of(
                    "Topic1", "Topic2", "Topic3", "Topic4", "Topic5",
                    "Topic6", "Topic7", "Topic8", "Topic9", "Topic10", "Topic11"
            );
            ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                    .speakingTopics(tooManyTopics)
                    .build();

            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));

            // When/Then
            assertThatThrownBy(() -> speakerProfileService.updateProfile(validToken, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("speakingTopics")
                    .hasMessageContaining("10");
        }

        @Test
        @DisplayName("should reject invalid LinkedIn URL")
        void should_validateLinkedInUrl_when_invalidFormat() {
            // Given
            ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                    .linkedInUrl("not-a-valid-url")
                    .build();

            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));

            // When/Then
            assertThatThrownBy(() -> speakerProfileService.updateProfile(validToken, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("linkedInUrl");
        }

        @Test
        @DisplayName("should accept valid LinkedIn URL")
        void should_acceptValidLinkedInUrl_when_validFormat() {
            // Given
            ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                    .linkedInUrl("https://linkedin.com/in/validprofile")
                    .build();

            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));
            when(userApiClient.getUserByUsername("john.doe")).thenReturn(testUserResponse);

            // When
            SpeakerProfileDto result = speakerProfileService.updateProfile(validToken, request);

            // Then - no exception thrown
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should accept empty LinkedIn URL")
        void should_acceptEmptyLinkedInUrl_when_clearing() {
            // Given
            ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                    .linkedInUrl("")
                    .build();

            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));
            when(userApiClient.getUserByUsername("john.doe")).thenReturn(testUserResponse);

            // When - should not throw
            SpeakerProfileDto result = speakerProfileService.updateProfile(validToken, request);

            // Then
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("AC3-4: Expertise and Topics Management")
    class ExpertiseTopicsTests {

        @Test
        @DisplayName("should update expertise areas")
        void should_updateExpertiseAreas_when_provided() {
            // Given
            ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                    .expertiseAreas(List.of("AI/ML", "Data Engineering"))
                    .build();

            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));
            when(userApiClient.getUserByUsername("john.doe")).thenReturn(testUserResponse);

            // When
            speakerProfileService.updateProfile(validToken, request);

            // Then
            ArgumentCaptor<Speaker> speakerCaptor = ArgumentCaptor.forClass(Speaker.class);
            verify(speakerRepository).save(speakerCaptor.capture());
            assertThat(speakerCaptor.getValue().getExpertiseAreas())
                    .containsExactly("AI/ML", "Data Engineering");
        }

        @Test
        @DisplayName("should update speaking topics")
        void should_updateSpeakingTopics_when_provided() {
            // Given
            ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                    .speakingTopics(List.of("LLM", "RAG", "Vector Databases"))
                    .build();

            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));
            when(userApiClient.getUserByUsername("john.doe")).thenReturn(testUserResponse);

            // When
            speakerProfileService.updateProfile(validToken, request);

            // Then
            ArgumentCaptor<Speaker> speakerCaptor = ArgumentCaptor.forClass(Speaker.class);
            verify(speakerRepository).save(speakerCaptor.capture());
            assertThat(speakerCaptor.getValue().getSpeakingTopics())
                    .containsExactly("LLM", "RAG", "Vector Databases");
        }
    }

    @Nested
    @DisplayName("AC5: Languages Selection")
    class LanguagesTests {

        @Test
        @DisplayName("should update languages")
        void should_updateLanguages_when_provided() {
            // Given
            ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                    .languages(List.of("en", "de", "fr"))
                    .build();

            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));
            when(userApiClient.getUserByUsername("john.doe")).thenReturn(testUserResponse);

            // When
            speakerProfileService.updateProfile(validToken, request);

            // Then
            ArgumentCaptor<Speaker> speakerCaptor = ArgumentCaptor.forClass(Speaker.class);
            verify(speakerRepository).save(speakerCaptor.capture());
            assertThat(speakerCaptor.getValue().getLanguages())
                    .containsExactly("en", "de", "fr");
        }
    }

    @Nested
    @DisplayName("AC10: Cross-Service Sync")
    class CrossServiceSyncTests {

        @Test
        @DisplayName("should not call Company Service when only speaker fields updated")
        void should_notCallCompanyService_when_onlySpeakerFieldsUpdated() {
            // Given
            ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                    .expertiseAreas(List.of("New Area"))
                    .languages(List.of("en"))
                    .build();

            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));
            when(userApiClient.getUserByUsername("john.doe")).thenReturn(testUserResponse);

            // When
            speakerProfileService.updateProfile(validToken, request);

            // Then
            verify(userApiClient, never()).updateUser(anyString(), any());
        }

        @Test
        @DisplayName("should call Company Service when user fields updated")
        void should_callCompanyService_when_userFieldsUpdated() {
            // Given
            ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                    .firstName("NewFirstName")
                    .bio("New bio")
                    .build();

            when(magicLinkService.validateToken(validToken)).thenReturn(validTokenResult);
            when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(testSpeaker));
            when(userApiClient.getUserByUsername("john.doe")).thenReturn(testUserResponse);

            // When
            speakerProfileService.updateProfile(validToken, request);

            // Then
            verify(userApiClient).updateUser(eq("john.doe"), any());
        }
    }
}
