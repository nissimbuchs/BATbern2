package ch.batbern.shared.logging;

import ch.batbern.shared.utils.LoggingUtils;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter;

/**
 * Spring Boot's {@code %wEx} (the default exception conversion word) with every e-mail address
 * masked (#1202): the exception message, each {@code Caused by:} and suppressed line, the frames.
 */
public class EmailMaskingThrowableConverter extends ExtendedWhitespaceThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return LoggingUtils.maskEmailsIn(super.convert(event));
    }
}
