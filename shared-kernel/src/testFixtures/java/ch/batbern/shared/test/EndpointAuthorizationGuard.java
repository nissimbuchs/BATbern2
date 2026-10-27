package ch.batbern.shared.test;

import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds write endpoints that do not declare who may call them.
 *
 * <p>Role checks live in the services (ADR-008): neither the AWS API Gateway nor the Spring API
 * gateway checks roles, the gateway only authenticates. A write endpoint without
 * {@code @PreAuthorize} is therefore open to every signed-in account. Every POST/PUT/PATCH/DELETE
 * handler must carry {@code @PreAuthorize} on the method or its class: a role rule, an
 * ownership rule, {@code isAuthenticated()} for self-service, or {@code permitAll()} for a
 * deliberately public endpoint (which also needs the matching {@code permitAll} in the filter
 * chain). Writing the rule down is the point: a missing annotation fails the build.
 *
 * <p>Paths under {@code /internal/} (VPC-only service API, not routed by the gateway),
 * {@code /dev/} (local profile only) and Spring's {@code /error} are not checked.
 */
public final class EndpointAuthorizationGuard {

    private static final Set<RequestMethod> WRITE_METHODS =
            EnumSet.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    private static final List<String> EXCLUDED_PREFIXES = List.of("/internal/", "/dev/", "/error");

    private EndpointAuthorizationGuard() {
    }

    /**
     * @return one line per unguarded write endpoint, e.g.
     *     {@code "DELETE /api/v1/events/{eventCode} -> EventController#deleteEvent"}; empty if all
     *     write endpoints declare their rule
     */
    public static List<String> unguardedWriteEndpoints(RequestMappingHandlerMapping mapping) {
        return unguardedWriteEndpoints(mapping, Set.of());
    }

    /**
     * Same, ignoring the given handler types. Only for handlers that authorize elsewhere by design,
     * such as the API gateway's catch-all proxy, which forwards to the services that check roles.
     */
    public static List<String> unguardedWriteEndpoints(
            RequestMappingHandlerMapping mapping, Set<Class<?>> excludedHandlerTypes) {
        List<String> unguarded = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();

            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            // A mapping without a method restriction answers every verb, writes included.
            Set<RequestMethod> writes = EnumSet.noneOf(RequestMethod.class);
            if (methods.isEmpty()) {
                writes.addAll(WRITE_METHODS);
            } else {
                methods.stream().filter(WRITE_METHODS::contains).forEach(writes::add);
            }
            if (writes.isEmpty() || isGuarded(handler)
                    || excludedHandlerTypes.contains(handler.getBeanType())) {
                continue;
            }
            for (String path : info.getPatternValues()) {
                if (EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith)) {
                    continue;
                }
                unguarded.add(writes + " " + path + " -> "
                        + handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName());
            }
        }
        unguarded.sort(String::compareTo);
        return unguarded;
    }

    /**
     * {@code @PreAuthorize} only takes effect with {@code @EnableMethodSecurity}; without it the
     * annotation is decoration and would satisfy {@link #unguardedWriteEndpoints} for nothing.
     */
    public static boolean methodSecurityEnabled(ApplicationContext context) {
        return context.containsBean("preAuthorizeAuthorizationMethodInterceptor");
    }

    private static boolean isGuarded(HandlerMethod handler) {
        return AnnotatedElementUtils.hasAnnotation(handler.getMethod(), PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), PreAuthorize.class);
    }
}
