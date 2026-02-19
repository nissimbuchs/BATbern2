package ch.batbern.events.watch.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Speaker data for Watch active events response.
 * W2.3: Event Join & Schedule Sync
 */
@Getter
@AllArgsConstructor
public class SpeakerDetail {
    private final String username;
    private final String firstName;
    private final String lastName;
    private final String company;
    private final String companyLogoUrl;
    private final String profilePictureUrl;
    private final String bio;
    private final String speakerRole;
}
