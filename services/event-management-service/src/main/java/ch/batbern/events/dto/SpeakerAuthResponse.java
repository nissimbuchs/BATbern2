package ch.batbern.events.dto;

import java.util.UUID;

public record SpeakerAuthResponse(
    UUID speakerPoolId,
    String speakerName,
    String eventCode,
    String eventTitle,
    String sessionToken
) {}
