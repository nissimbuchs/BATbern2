package ch.batbern.events.dto.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Legacy speaker DTO matching the BATspa referenten schema.
 * Story 10.20: AC1
 *
 * Used both inline in session.referenten[] and in the global speakers[] list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LegacySpeakerDto {

    /** Speaker username — legacy speakerId field */
    private String speakerId;

    /** Full name: firstName + " " + lastName */
    private String name;

    private String bio;

    /** Company name the speaker belongs to */
    private String company;

    /** Portrait filename or CloudFront URL */
    private String portrait;

    private String linkedInUrl;
    private String twitterHandle;
}
