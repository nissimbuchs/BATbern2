package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;

public record SpeakerMagicLoginRequest(
    @NotBlank String jwtToken
) {}
