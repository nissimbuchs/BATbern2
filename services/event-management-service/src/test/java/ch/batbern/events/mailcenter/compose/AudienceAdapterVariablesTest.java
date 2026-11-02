package ch.batbern.events.mailcenter.compose;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 16.11 AC9: an audience's allowed and credential variables come from the catalog scopes it
 * declares, not from a hand-kept list. Each adapter still offers every variable its hand-kept list
 * named before (16.8), so nothing a template uses today drops out.
 */
class AudienceAdapterVariablesTest {

    @Test
    @DisplayName("newsletter: catalog scopes cover the 16.8 list; the unsubscribe link is a credential")
    void newsletter() {
        AudienceAdapter adapter = new NewsletterAudienceAdapter(null, null, null);
        assertThat(adapter.allowedVariables()).contains("reminderPrefix", "eventNumber", "eventType", "eventTitle",
                "eventDate", "eventTime", "venue", "venueDirectionsUrl", "conferenceLanguage", "speakersSection",
                "currentYear", "eventDetailLink", "registrationLink", "upcomingEventsSection", "unsubscribeLink",
                "preferencesLink", "logoUrl");
        assertThat(adapter.credentialVariables()).containsExactly("unsubscribeLink");
    }

    @Test
    @DisplayName("registrants: catalog scopes cover the 16.8 list; registration credentials are flagged")
    void registrants() {
        AudienceAdapter adapter = new RegistrantAudienceAdapter(null);
        assertThat(adapter.allowedVariables()).contains("eventTitle", "eventNumber", "eventType", "eventDate",
                "eventDetailLink", "currentYear", "logoUrl", "eventUrl", "dashboardLink", "supportUrl",
                "deregistrationUrl", "recipientName", "attendeeFirstName");
        assertThat(adapter.credentialVariables()).contains("deregistrationUrl", "deregistrationLink")
                .doesNotContain("unsubscribeLink", "temporaryPassword");
    }

    @Test
    @DisplayName("speakers: catalog scopes cover the 16.8 list; no subscriber or registration variables")
    void speakers() {
        AudienceAdapter adapter = new SpeakerAudienceAdapter(null, null, null, null, null);
        assertThat(adapter.allowedVariables()).contains("speakerName", "eventTitle", "eventDate", "deadline",
                "daysRemaining", "portalLink", "organizerName", "organizerEmail", "currentYear", "logoUrl")
                .doesNotContain("unsubscribeLink", "deregistrationUrl");
    }

    @Test
    @DisplayName("venue: catalog scopes cover the 16.8 list; no credentials")
    void venue() {
        AudienceAdapter adapter = new VenueAudienceAdapter(null, null);
        assertThat(adapter.allowedVariables()).contains("eventDate", "eventTitle", "topicTitle", "venueName",
                "salutation", "agenda", "notes", "organizerSignature", "currentYear", "logoUrl");
        assertThat(adapter.credentialVariables()).isEmpty();
    }

    @Test
    @DisplayName("the layout's content slot is never offered as a variable")
    void layoutSlotNotOffered() {
        assertThat(new VenueAudienceAdapter(null, null).allowedVariables()).doesNotContain("content");
    }
}
