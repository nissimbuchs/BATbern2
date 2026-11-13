package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.security.UserOwnership;
import ch.batbern.shared.upload.UploadException;
import ch.batbern.shared.upload.UploadPurposeHandler;
import ch.batbern.shared.upload.UploadRecord;
import ch.batbern.shared.upload.UploadRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Purpose {@code profile-picture} (Story 17.11a): the picture of a user. The user always exists, so
 * the target {@code username} is required and confirm attaches at once. The user themself or an
 * organizer may upload it (the ownership rule of {@code /users/{username}}). Import from a URL is the
 * organizer's speaker batch import: import, then confirm with the target.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfilePictureUploadHandler implements UploadPurposeHandler {

    public static final String PURPOSE = "profile-picture";
    public static final String TARGET_TYPE = "USER";

    private final UserRepository userRepository;
    private final UserOwnership userOwnership;

    @Override
    public String purpose() {
        return PURPOSE;
    }

    @Override
    public UploadRules rules() {
        return UploadRules.images5Mb();
    }

    @Override
    public String targetKey() {
        return "username";
    }

    @Override
    public String targetType() {
        return TARGET_TYPE;
    }

    @Override
    public boolean requiresTarget() {
        return true;
    }

    @Override
    public void checkTarget(String username) {
        if (!UploadPurposeHandler.callerHasRole("ORGANIZER") && !userOwnership.isCurrentUser(username)) {
            throw UploadException.forbidden("Only the user themself or an organizer changes this picture");
        }
        if (!userRepository.existsByUsername(username)) {
            throw UploadException.notFound("User not found: " + username);
        }
    }

    @Override
    public String tempKey(String uploadId, String extension) {
        return String.format("profile-pictures/temp/%s/profile-%s.%s", uploadId, uploadId, extension);
    }

    @Override
    public String finalKey(UploadRecord upload, String username) {
        return ProfilePictureService.pictureKey(username, upload.getUploadId(), upload.getFileExtension());
    }

    @Override
    public void attach(UploadRecord upload, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> UploadException.notFound("User not found: " + username));
        user.setProfilePictureUrl(upload.getFileUrl());
        user.setProfilePictureS3Key(upload.getS3Key());
        userRepository.save(user);
        log.info("Profile picture attached to user: {}, uploadId: {}", username, upload.getUploadId());
    }

    @Override
    public boolean supportsImport() {
        return true;
    }

    @Override
    public void checkMayImport() {
        if (!UploadPurposeHandler.callerHasRole("ORGANIZER")) {
            throw UploadException.forbidden("Only organizers import profile pictures");
        }
    }
}
