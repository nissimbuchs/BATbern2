package ch.batbern.events.validation;

import ch.batbern.events.dto.SendInvitationRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for @ValidSpeakerIdentifier annotation.
 *
 * Ensures that SendInvitationRequest has either:
 * - A non-blank username, OR
 * - A non-null speakerPoolId
 */
public class SpeakerIdentifierValidator
        implements ConstraintValidator<ValidSpeakerIdentifier, SendInvitationRequest> {

    @Override
    public void initialize(ValidSpeakerIdentifier constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(SendInvitationRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true; // Let @NotNull handle null check if needed
        }

        boolean hasUsername = request.getUsername() != null && !request.getUsername().isBlank();
        boolean hasSpeakerPoolId = request.getSpeakerPoolId() != null;

        return hasUsername || hasSpeakerPoolId;
    }
}
