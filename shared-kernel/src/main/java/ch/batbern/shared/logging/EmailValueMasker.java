package ch.batbern.shared.logging;

import ch.batbern.shared.utils.LoggingUtils;
import net.logstash.logback.mask.ValueMasker;
import tools.jackson.core.TokenStreamContext;

/**
 * Masks every e-mail address in each string value a {@code LogstashEncoder} writes (#1202):
 * message, arguments, {@code stack_trace}, MDC. Field names are not values and stay. Registered in
 * the gateway's and CUMS's {@code logback-spring.xml} through {@code MaskingJsonGeneratorDecorator}.
 */
public class EmailValueMasker implements ValueMasker {

    /**
     * @return the masked value, or {@code null} when there is nothing to mask (the generator then
     *         writes the original)
     */
    @Override
    public Object mask(TokenStreamContext context, Object value) {
        if (value instanceof CharSequence text) {
            String original = text.toString();
            String masked = LoggingUtils.maskEmailsIn(original);
            return masked.equals(original) ? null : masked;
        }
        return null;
    }
}
