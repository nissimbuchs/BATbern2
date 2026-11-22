package ch.batbern.agent.tools.read;

import java.util.List;

/** The fields the read tools hand the model (no e-mail, picture, bio or material links). */
final class Projections {

    static final List<String> EVENT = List.of("eventCode", "eventNumber", "title", "date", "eventType",
            "workflowState", "venueName");
    static final List<String> SESSION = List.of("sessionSlug", "title", "sessionType", "startTime", "endTime",
            "room", "language");
    static final List<String> SPEAKER = List.of("username", "firstName", "lastName", "companyDisplayName",
            "speakerRole", "isConfirmed", "presentationTitle");

    private Projections() {
    }
}
