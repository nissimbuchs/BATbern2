package ch.batbern.events.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validation annotation to ensure either username OR speakerPoolId is provided.
 *
 * Used on SendInvitationRequest to enforce that at least one speaker identifier
 * is present. This supports both:
 * - Existing flow: username (for speakers with user accounts)
 * - New flow: speakerPoolId (for speakers without user accounts)
 */
@Documented
@Constraint(validatedBy = SpeakerIdentifierValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSpeakerIdentifier {

    String message() default "Either username or speakerPoolId must be provided";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
