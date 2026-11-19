package ch.batbern.shared.logging;

import ch.batbern.shared.utils.LoggingUtils;
import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * {@code %m} with every e-mail address masked (#1202): the formatted message, arguments included,
 * through {@link LoggingUtils#maskEmailsIn(String)}. Registered for EMS and PCS by
 * {@code email-masking-conversion-rules.xml}; the rest of the line is Spring Boot's.
 */
public class EmailMaskingMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return LoggingUtils.maskEmailsIn(super.convert(event));
    }
}
