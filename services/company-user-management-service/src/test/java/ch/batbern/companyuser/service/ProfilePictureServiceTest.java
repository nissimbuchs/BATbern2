package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.User;
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
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The server-side store of a profile picture (the Google avatar import) and the key layout of every
 * profile picture. Uploads by users and organizers run on the upload contract (Story 17.11a,
 * {@code ProfilePictureUploadIntegrationTest}).
 */
@ExtendWith(MockitoExtension.class)
class ProfilePictureServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private UserRepository userRepository;

    private ProfilePictureService profilePictureService;

    private static final String BUCKET_NAME = "batbern-test-profile-pictures";
    private static final String CLOUDFRONT_DOMAIN = "https://test-cdn.batbern.ch";

    @BeforeEach
    void setUp() {
        profilePictureService = new ProfilePictureService(s3Client, userRepository, BUCKET_NAME, CLOUDFRONT_DOMAIN);
    }

    @Test
    void should_buildKeyWithoutYear_when_pictureKeyIsAsked() {
        assertThat(ProfilePictureService.pictureKey("john.doe", "f3e8d1a4", "png"))
                .isEqualTo("profile-pictures/john.doe/profile-f3e8d1a4.png");
    }

    @Test
    void should_storeAtKeyWithoutYear_when_uploadedDirectly() {
        User user = new User();
        user.setUsername("john.doe");
        when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(user));

        String url = profilePictureService.uploadProfilePictureDirectly("john.doe", new byte[] {1, 2, 3},
                "avatar.jpg", "image/jpeg");

        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(put.capture(), any(RequestBody.class));
        assertThat(put.getValue().key()).matches("profile-pictures/john\\.doe/profile-[0-9a-f-]{36}\\.jpg");
        assertThat(user.getProfilePictureS3Key()).isEqualTo(put.getValue().key());
        assertThat(url).isEqualTo(user.getProfilePictureUrl()).startsWith(CLOUDFRONT_DOMAIN)
                .endsWith(put.getValue().key());
    }

    @Test
    void should_throwException_when_directUploadExceeds5MB() {
        assertThatThrownBy(() -> profilePictureService.uploadProfilePictureDirectly("john.doe",
                new byte[5 * 1024 * 1024 + 1], "large.jpg", "image/jpeg"))
                .isInstanceOf(FileSizeExceededException.class)
                .hasMessageContaining("5MB");
        verifyNoInteractions(s3Client);
    }

    @Test
    void should_throwException_when_directUploadHasInvalidType() {
        assertThatThrownBy(() -> profilePictureService.uploadProfilePictureDirectly("john.doe", new byte[] {1},
                "document.pdf", "application/pdf"))
                .isInstanceOf(InvalidFileTypeException.class)
                .hasMessageContaining("PNG, JPG, JPEG, SVG");
        verifyNoInteractions(s3Client);
    }

    @Test
    void should_throwException_when_directUploadUserUnknown() {
        when(userRepository.findByUsername("nobody.here")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profilePictureService.uploadProfilePictureDirectly("nobody.here", new byte[] {1},
                "avatar.png", "image/png"))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(s3Client);
    }
}
