package ch.batbern.events.controller;

import ch.batbern.shared.security.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Small helper for speaker-portal controllers to read the caller's client IP for diagnostic
 * logging without taking an {@link HttpServletRequest} method parameter.
 *
 * <p>API-consolidation Phase 7: the speaker-portal controllers now implement generated
 * interfaces whose method signatures carry no {@code HttpServletRequest}. The IP-for-logging
 * is resolved here from the current request (via {@link RequestContextHolder}) so the audit
 * log lines on the portal endpoints are preserved unchanged.
 */
final class SpeakerPortalHttp {

    private SpeakerPortalHttp() {
    }

    /**
     * Current caller's client IP, the rightmost public {@code X-Forwarded-For} hop (#1132).
     * Returns {@code "unknown"} when no request is bound to the current thread.
     */
    static String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return "unknown";
        }
        HttpServletRequest request = attrs.getRequest();
        return ClientIp.resolve(request.getHeader(ClientIp.X_FORWARDED_FOR), request.getRemoteAddr());
    }
}
