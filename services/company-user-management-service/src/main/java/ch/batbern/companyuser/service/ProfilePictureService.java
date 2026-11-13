package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.exception.FileSizeExceededException;
import ch.batbern.companyuser.exception.InvalidFileTypeException;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.utils.CloudFrontUrlBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Set;
import java.util.UUID;

/**
 * Server-side store of a profile picture (the Google avatar import, Story 12.12) and the key layout
 * of every profile picture. Uploads by a user or an organizer run on the upload contract, purpose
 * {@code profile-picture} ({@link ProfilePictureUploadHandler}, Story 17.11a).
 */
@Service
@Transactional
@Slf4j
public class ProfilePictureService {

    private final S3Client s3Client;
    private final UserRepository userRepository;
    private final String bucketName;
    private final String cloudFrontDomain;

    public ProfilePictureService(
            S3Client s3Client,
            UserRepository userRepository,
            @Value("${aws.s3.bucket-name:batbern-development-company-logos}") String bucketName,
            @Value("${aws.cloudfront.domain:https://cdn.batbern.ch}") String cloudFrontDomain) {
        this.s3Client = s3Client;
        this.userRepository = userRepository;
        this.bucketName = bucketName;
        this.cloudFrontDomain = cloudFrontDomain;
    }

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "svg");

    /**
     * The final key of a profile picture, the same for an upload of purpose {@code profile-picture}
     * and the Google avatar import: {@code profile-pictures/{username}/profile-{fileId}.{ext}}. No
     * year (Story 17.11a): the upload year carries no meaning and broke a confirm across new year.
     * Pictures stored earlier keep their {@code profile-pictures/{year}/…} key and URL.
     */
    public static String pictureKey(String username, String fileId, String extension) {
        return String.format("profile-pictures/%s/profile-%s.%s", username, fileId, extension);
    }

    /**
     * Extract file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new InvalidFileTypeException("Invalid file type. No file extension found.");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Upload profile picture directly to S3 and associate with user (server-side only)
     * Used by the Google avatar import ({@link FederatedAvatarImportService}), which has the bytes
     * already and no upload row
     *
     * @param username User's username
     * @param imageData Binary image data
     * @param fileName Original filename with extension
     * @param mimeType MIME type (image/png, image/jpeg, etc.)
     * @return CloudFront URL of the uploaded profile picture
     * @throws FileSizeExceededException if file size exceeds 5MB
     * @throws InvalidFileTypeException if file type is not allowed
     * @throws UserNotFoundException if user not found
     */
    public String uploadProfilePictureDirectly(String username, byte[] imageData, String fileName, String mimeType) {
        log.info("Uploading profile picture directly to S3 for user: {}, file: {}, size: {} bytes",
                username, fileName, imageData.length);

        // Validate file size (max 5MB)
        if (imageData.length > MAX_FILE_SIZE_BYTES) {
            throw new FileSizeExceededException("Profile picture file size exceeds 5MB limit");
        }

        // Validate file type
        String fileExtension = getFileExtension(fileName);
        if (!ALLOWED_FILE_EXTENSIONS.contains(fileExtension)) {
            throw new InvalidFileTypeException("Invalid file type. Allowed types: PNG, JPG, JPEG, SVG");
        }

        // Get user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        // Generate unique file ID and S3 key
        String fileId = UUID.randomUUID().toString();
        String s3Key = pictureKey(username, fileId, fileExtension);

        // Upload directly to S3
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(mimeType)
                    .contentLength((long) imageData.length)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(imageData));

            log.info("Uploaded profile picture to S3: {}", s3Key);

        } catch (Exception e) {
            log.error("Failed to upload profile picture to S3: {}", s3Key, e);
            throw new RuntimeException("Failed to upload profile picture to S3", e);
        }

        // Build CloudFront URL and update user
        String cloudFrontUrl = CloudFrontUrlBuilder.buildUrl(cloudFrontDomain, bucketName, s3Key);
        user.setProfilePictureUrl(cloudFrontUrl);
        user.setProfilePictureS3Key(s3Key);
        userRepository.save(user);

        log.info("Profile picture uploaded directly for user: {}, CloudFront URL: {}", username, cloudFrontUrl);

        return cloudFrontUrl;
    }
}
