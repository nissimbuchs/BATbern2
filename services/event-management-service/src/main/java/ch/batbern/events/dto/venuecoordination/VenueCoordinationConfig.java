package ch.batbern.events.dto.venuecoordination;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Persistent admin configuration shared across every event for venue-coordination
 * mails. Stored as JSON under app_settings key {@code venue.coordination.config};
 * managed via the existing AdminSettings key/value endpoints (no new persistence
 * layer needed).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VenueCoordinationConfig {

    public static final String SETTING_KEY = "venue.coordination.config";

    private Contact venue;
    private Contact catering;

    /** Organizer username whose email lands as Reply-To on every venue-coordination send. */
    private String coordinatorUsername;

    public Contact contactFor(Role role) {
        return role == Role.VENUE ? venue : catering;
    }

    public enum Role { VENUE, CATERING }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Contact {
        private String salutation;
        private String name;
        private String email;

        public boolean isComplete() {
            return name != null && !name.isBlank() && email != null && !email.isBlank();
        }
    }
}
