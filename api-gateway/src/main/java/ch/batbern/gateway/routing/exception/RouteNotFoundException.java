package ch.batbern.gateway.routing.exception;

/**
 * No backend route matches the request path. Answered with 404, unlike other
 * {@link RoutingException}s (backend unreachable, unreadable body), which stay 5xx.
 */
public class RouteNotFoundException extends RoutingException {

    public RouteNotFoundException(String path) {
        super("No route found for path: " + path);
    }
}
