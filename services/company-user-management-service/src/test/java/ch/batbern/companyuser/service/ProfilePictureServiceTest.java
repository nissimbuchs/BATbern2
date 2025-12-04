package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.PresignedUploadUrl;
import ch.batbern.companyuser.exception.FileSizeExceededException;
import ch.batbern.companyuser.exception.InvalidFileTypeException;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ProfilePictureService
 * AC10: Profile picture upload with presigned S3 URLs
 *
 * Test Coverage:
 * - Presigned URL generation
 * - File validation (type, size)
 * - Upload confirmation
 * - S3 error handling
 */
@ExtendWith(MockitoExtension.class)
class ProfilePictureServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private software.amazon.awssdk.services.s3.S3Client s3Client;

    @Mock
    private UserRepository userRepository;

    private ProfilePictureService profilePictureService;

    private static final String BUCKET_NAME = "batbern-test-profile-pictures";
    private static final String CLOUDFRONT_DOMAIN = "https://test-cdn.batbern.ch";

    @BeforeEach
    void setUp() {
        profilePictureService = new ProfilePictureService(
            s3Presigner,
            s3Client,
            userRepository,
            BUCKET_NAME,
            CLOUDFRONT_DOMAIN
        );
    }

    @Test
    void should_generatePresignedUrl_when_validFileProvided() throws MalformedURLException {
        // Given
        UUID userId = UUID.randomUUID();
        String username = "john.doe";
        String filename = "profile-picture.png";
        long fileSizeBytes = 2 * 1024 * 1024; // 2 MB

        // Mock S3 Presigner
        URL mockPresignedUrl = new URL("https://s3.amazonaws.com/test-bucket/presigned-url");
        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(mockPresignedUrl);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
            .thenReturn(mockPresignedRequest);

        // When
        PresignedUploadUrl result = profilePictureService.generateProfilePictureUploadUrl(
            userId, username, filename, fileSizeBytes
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUploadUrl()).isEqualTo(mockPresignedUrl.toString());
        assertThat(result.getFileId()).isNotBlank();
        assertThat(result.getS3Key()).startsWith("profile-pictures/");
        assertThat(result.getS3Key()).contains(username);
        assertThat(result.getFileExtension()).isEqualTo("png");
        assertThat(result.getExpiresInMinutes()).isEqualTo(15);
        assertThat(result.getRequiredHeaders()).containsEntry("Content-Type", "image/png");

        verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void should_throwException_when_fileSizeExceeds5MB() {
        // Given
        UUID userId = UUID.randomUUID();
        String username = "john.doe";
        String filename = "large-picture.jpg";
        long fileSizeBytes = 6 * 1024 * 1024; // 6 MB (exceeds 5 MB limit)

        // When / Then
        assertThatThrownBy(() -> profilePictureService.generateProfilePictureUploadUrl(
            userId, username, filename, fileSizeBytes
        ))
            .isInstanceOf(FileSizeExceededException.class)
            .hasMessageContaining("5MB");
    }

    @Test
    void should_throwException_when_invalidFileType() {
        // Given
        UUID userId = UUID.randomUUID();
        String username = "john.doe";
        String filename = "document.pdf";
        long fileSizeBytes = 1 * 1024 * 1024; // 1 MB

        // When / Then
        assertThatThrownBy(() -> profilePictureService.generateProfilePictureUploadUrl(
            userId, username, filename, fileSizeBytes
        ))
            .isInstanceOf(InvalidFileTypeException.class)
            .hasMessageContaining("PNG, JPG, JPEG, SVG");
    }

    @Test
    void should_acceptAllValidImageTypes() throws MalformedURLException {
        // Given
        UUID userId = UUID.randomUUID();
        String username = "john.doe";
        long fileSizeBytes = 1 * 1024 * 1024; // 1 MB
        String[] validFilenames = {"pic.png", "pic.jpg", "pic.jpeg", "pic.svg"};

        // Mock S3 Presigner
        URL mockPresignedUrl = new URL("https://s3.amazonaws.com/test-bucket/presigned-url");
        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(mockPresignedUrl);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
            .thenReturn(mockPresignedRequest);

        // When / Then
        for (String filename : validFilenames) {
            assertThatCode(() -> profilePictureService.generateProfilePictureUploadUrl(
                userId, username, filename, fileSizeBytes
            )).doesNotThrowAnyException();
        }

        verify(s3Presigner, times(4)).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void should_confirmUpload_when_validFileIdProvided() {
        // Given
        UUID userId = UUID.randomUUID();
        String username = "john.doe";
        String fileId = UUID.randomUUID().toString();
        String fileExtension = "png";

        User user = new User();
        user.setId(userId);
        user.setUsername(username);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        profilePictureService.confirmProfilePictureUpload(userId, username, fileId, fileExtension);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getProfilePictureUrl()).isNotBlank();
        assertThat(savedUser.getProfilePictureUrl()).startsWith(CLOUDFRONT_DOMAIN);
        assertThat(savedUser.getProfilePictureS3Key()).startsWith("profile-pictures/");
        assertThat(savedUser.getProfilePictureS3Key()).contains(username);
        assertThat(savedUser.getProfilePictureS3Key()).contains(fileId);
    }

    @Test
    void should_throwException_when_userNotFoundOnConfirm() {
        // Given
        UUID userId = UUID.randomUUID();
        String username = "john.doe";
        String fileId = UUID.randomUUID().toString();
        String fileExtension = "png";

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> profilePictureService.confirmProfilePictureUpload(
            userId, username, fileId, fileExtension
        ))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessageContaining(username);
    }

    @Test
    void should_generateUniqueS3Keys_when_multipleUploads() throws MalformedURLException {
        // Given
        UUID userId = UUID.randomUUID();
        String username = "john.doe";
        String filename = "profile.png";
        long fileSizeBytes = 1 * 1024 * 1024;

        // Mock S3 Presigner
        URL mockPresignedUrl = new URL("https://s3.amazonaws.com/test-bucket/presigned-url");
        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(mockPresignedUrl);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
            .thenReturn(mockPresignedRequest);

        // When
        PresignedUploadUrl result1 = profilePictureService.generateProfilePictureUploadUrl(
            userId, username, filename, fileSizeBytes
        );
        PresignedUploadUrl result2 = profilePictureService.generateProfilePictureUploadUrl(
            userId, username, filename, fileSizeBytes
        );

        // Then
        assertThat(result1.getFileId()).isNotEqualTo(result2.getFileId());
        assertThat(result1.getS3Key()).isNotEqualTo(result2.getS3Key());
    }
}
