package ch.batbern.shared.upload;

import java.util.Set;

/**
 * What a purpose accepts.
 *
 * @param maxBytes     largest file, for presign and import
 * @param contentTypes allowed MIME types (parameters such as {@code ; charset} are ignored)
 * @param extensions   allowed file name extensions, lower case
 */
public record UploadRules(long maxBytes, Set<String> contentTypes, Set<String> extensions) {

    /** PNG, JPEG and SVG up to 5 MB: logos and theme images. */
    public static UploadRules images5Mb() {
        return new UploadRules(5L * 1024 * 1024, Set.of("image/png", "image/jpeg", "image/svg+xml"),
                Set.of("png", "jpg", "jpeg", "svg"));
    }
}
