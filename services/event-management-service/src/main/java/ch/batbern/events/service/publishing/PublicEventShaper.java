package ch.batbern.events.service.publishing;

import ch.batbern.events.core.dto.generated.EventDetail;
import ch.batbern.events.core.dto.generated.PublicSpeaker;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.sessions.dto.generated.SessionResponse;
import ch.batbern.events.sessions.dto.generated.SessionSpeaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Shapes an event for the public website (Public Events read model, 2026-10-07).
 *
 * <p>Input: a DTO whose {@code sessions} already passed
 * {@link PublicSessionVisibilityService#publicSessions}. Output: {@code speakers[]} derived from
 * those sessions, and session list and talk titles only where
 * {@link PublicSessionVisibilityPolicy#sessionDetailsPublic} allows it. Every decision comes from the
 * policy; this class only applies it to the wire format.
 */
@Component
@RequiredArgsConstructor
public class PublicEventShaper {

    private final PublicSessionVisibilityPolicy policy;

    /** Shape a single-event response (sessions carry the rich {@link SessionResponse}). */
    public EventDetail shape(EventDetail dto, Event event) {
        List<SessionResponse> sessions = dto.getSessions() != null ? dto.getSessions() : List.of();
        boolean detailsPublic = policy.sessionDetailsPublic(event, Instant.now());
        dto.setSpeakers(speakers(sessions, SessionResponse::getSessionType, SessionResponse::getTitle,
                SessionResponse::getSessionSlug, SessionResponse::getSpeakers, detailsPublic));
        dto.setSessions(detailsPublic ? new ArrayList<>(sessions) : new ArrayList<>());
        return dto;
    }

    /** Shape a list item (sessions carry the lean {@code Session}). */
    public ch.batbern.events.core.dto.generated.Event shape(ch.batbern.events.core.dto.generated.Event dto,
                                                            Event event) {
        List<ch.batbern.events.sessions.dto.generated.Session> sessions =
                dto.getSessions() != null ? dto.getSessions() : List.of();
        boolean detailsPublic = policy.sessionDetailsPublic(event, Instant.now());
        dto.setSpeakers(speakers(sessions,
                ch.batbern.events.sessions.dto.generated.Session::getSessionType,
                ch.batbern.events.sessions.dto.generated.Session::getTitle,
                ch.batbern.events.sessions.dto.generated.Session::getSessionSlug,
                ch.batbern.events.sessions.dto.generated.Session::getSpeakers, detailsPublic));
        dto.setSessions(detailsPublic ? new ArrayList<>(sessions) : new ArrayList<>());
        return dto;
    }

    /** One entry per speaker of a talk (structural sessions have no speakers to show). */
    private static <S> List<PublicSpeaker> speakers(List<S> sessions,
                                                    Function<S, String> type,
                                                    Function<S, String> title,
                                                    Function<S, String> slug,
                                                    Function<S, List<SessionSpeaker>> speakersOf,
                                                    boolean detailsPublic) {
        Map<String, PublicSpeaker> byUsername = new LinkedHashMap<>();
        for (S session : sessions) {
            if (Session.isStructuralType(type.apply(session)) || speakersOf.apply(session) == null) {
                continue;
            }
            for (SessionSpeaker sp : speakersOf.apply(session)) {
                if (sp.getUsername() == null || byUsername.containsKey(sp.getUsername())) {
                    continue;
                }
                PublicSpeaker out = new PublicSpeaker()
                        .username(sp.getUsername())
                        .firstName(sp.getFirstName())
                        .lastName(sp.getLastName())
                        .company(sp.getCompany())
                        .companyDisplayName(sp.getCompanyDisplayName())
                        .companyLogoUrl(text(sp.getCompanyLogoUrl()))
                        .profilePictureUrl(text(sp.getProfilePictureUrl()))
                        .bio(sp.getBio())
                        .speakerRole(sp.getSpeakerRole() != null ? sp.getSpeakerRole().getValue() : null);
                if (detailsPublic) {
                    out.talkTitle(sp.getPresentationTitle() != null ? sp.getPresentationTitle() : title.apply(session))
                            .sessionSlug(slug.apply(session));
                }
                byUsername.put(sp.getUsername(), out);
            }
        }
        return new ArrayList<>(byUsername.values());
    }

    private static String text(URI uri) {
        return uri != null ? uri.toString() : null;
    }
}
