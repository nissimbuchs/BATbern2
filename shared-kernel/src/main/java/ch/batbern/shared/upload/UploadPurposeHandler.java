package ch.batbern.shared.upload;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * One upload purpose in the service that owns it (ADR-002, Story 17.11). {@link UploadService} runs
 * the mechanics; the handler says what is purpose-specific.
 */
public interface UploadPurposeHandler {

    /** The path value, e.g. {@code company-logo}. */
    String purpose();

    /** Type and size rules. */
    UploadRules rules();

    /** The key of the target in the API, e.g. {@code companyName}. */
    String targetKey();

    /** Stored with an associated upload, e.g. {@code COMPANY}. */
    String targetType();

    /**
     * The caller may upload for this purpose at all (403 otherwise). Default: any signed-in user, as
     * the file does nothing until an entity the caller may change takes it.
     */
    default void checkMayUpload() {
    }

    /**
     * The caller may attach a file to this target and it exists. Throws {@link UploadException}
     * (403, 404) otherwise.
     */
    void checkTarget(String targetId);

    /** The final S3 key once attached to the target. */
    String finalKey(UploadRecord upload, String targetId);

    /** Puts the attached file on its target (e.g. the company logo). Runs in the confirm transaction. */
    void attach(UploadRecord upload, String targetId);

    /** Whether {@code POST /uploads/{purpose}/import} exists for this purpose. */
    default boolean supportsImport() {
        return false;
    }

    /** The caller may import (403 otherwise). Only called when {@link #supportsImport()}. */
    default void checkMayImport() {
        throw UploadException.forbidden("Import is not allowed for " + purpose());
    }

    /** The temporary key of a reserved upload; logos keep their historic {@code logos/temp/} prefix. */
    default String tempKey(String uploadId, String extension) {
        return String.format("logos/temp/%s/logo-%s.%s", uploadId, uploadId, extension);
    }

    /** True when the signed-in caller has the role (without the {@code ROLE_} prefix). */
    static boolean callerHasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (("ROLE_" + role).equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /** The signed-in caller's name, or null. */
    static String callerName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }
}
