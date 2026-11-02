package ch.batbern.events.mailcenter.variables;

import ch.batbern.events.domain.Event;
import ch.batbern.events.service.EventTimeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The shared values of the variable catalog (Story 16.11 AC2, AC4), built once: every mail gets
 * the SYSTEM variables, every mail about an event the EVENT variables. One formatting rule per
 * value: {@code eventDate} in the long form (owner decision 2026-11-01), {@code supportUrl} =
 * {@code {baseUrl}/support}.
 */
@Component
@RequiredArgsConstructor
public class MailVariableFactory {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final String TBA = "TBA";

    private final EventTimeResolver eventTimeResolver;

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl = "https://batbern.ch";

    @Value("${app.email.organizer-name:BATbern Team}")
    private String organizerName = "BATbern Team";

    @Value("${app.email.organizer-email:events@batbern.ch}")
    private String organizerEmail = "events@batbern.ch";

    /** The variables every mail has. */
    public MailVariables system() {
        return new MailVariables()
                .put(MailVariable.CURRENT_YEAR, Year.now(ZURICH).getValue())
                .put(MailVariable.LOGO_URL, baseUrl + "/BATbern_white_logo.png")
                .put(MailVariable.BASE_URL, baseUrl)
                .put(MailVariable.SUPPORT_URL, baseUrl + "/support")
                .put(MailVariable.ORGANIZER_NAME, organizerName)
                .put(MailVariable.ORGANIZER_EMAIL, organizerEmail);
    }

    /** SYSTEM plus the variables of an event, in {@code locale} ({@code de} unless {@code en}). */
    public MailVariables event(Event event, String locale) {
        boolean de = !"en".equalsIgnoreCase(locale);
        String code = event.getEventCode();
        return system()
                .put(MailVariable.EVENT_CODE, code)
                .put(MailVariable.EVENT_NUMBER, event.getEventNumber())
                .put(MailVariable.EVENT_TITLE, event.getTitle())
                .put(MailVariable.EVENT_TYPE, eventType(event, de))
                .put(MailVariable.EVENT_DATE, longDate(event, de))
                .put(MailVariable.EVENT_TIME, event.getDate() == null ? ""
                        : eventTimeResolver.formatStartTime(event) + (de ? " Uhr" : ""))
                .put(MailVariable.VENUE_NAME, event.getVenueName() != null ? event.getVenueName() : TBA)
                .put(MailVariable.VENUE_ADDRESS, event.getVenueAddress() != null ? event.getVenueAddress() : TBA)
                .put(MailVariable.VENUE_DIRECTIONS_URL, "")
                .put(MailVariable.EVENT_URL, baseUrl + "/events/" + code)
                .put(MailVariable.REGISTRATION_LINK, baseUrl + "/register/" + code)
                .put(MailVariable.CONFERENCE_LANGUAGE, de ? "Deutsch / Englisch" : "German / English");
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** The one event date format: {@code Freitag, 5. März 2027} / {@code Friday, 5. March 2027}. */
    public static String longDate(Event event, boolean de) {
        if (event.getDate() == null) {
            return "";
        }
        return DateTimeFormatter.ofPattern(de ? "EEEE, d. MMMM yyyy" : "EEEE, d MMMM yyyy",
                        de ? Locale.GERMAN : Locale.ENGLISH)
                .withZone(ZURICH).format(event.getDate());
    }

    private static String eventType(Event event, boolean de) {
        if (event.getEventType() == null) {
            return de ? "Abend-BAT" : "Evening BAT";
        }
        return switch (event.getEventType()) {
            case EVENING -> de ? "Abend-BAT" : "Evening BAT";
            case FULL_DAY -> de ? "Ganztages-BAT" : "Full-Day BAT";
            case AFTERNOON -> de ? "Nachmittags-BAT" : "Afternoon BAT";
            default -> event.getEventType().name().replace("_", " ");
        };
    }
}
