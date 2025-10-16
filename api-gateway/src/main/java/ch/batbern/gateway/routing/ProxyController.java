package ch.batbern.gateway.routing;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * Proxy Controller
 * Routes incoming requests to appropriate microservices based on path patterns
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProxyController {

    private final DomainRouter domainRouter;

    /**
     * Catch-all endpoint for routing requests to microservices
     * Matches all HTTP methods (GET, POST, PUT, DELETE, etc.)
     */
    @RequestMapping(value = "/**", method = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.DELETE,
        RequestMethod.PATCH,
        RequestMethod.HEAD
    })
    public CompletableFuture<ResponseEntity<String>> proxyRequest(HttpServletRequest request) {
        String requestPath = request.getRequestURI();

        log.debug("Proxying request: {} {}", request.getMethod(), requestPath);

        // Determine target service based on request path
        String targetService = domainRouter.determineTargetService(requestPath);

        // Route the request to the target service
        return domainRouter.routeRequest(targetService, request);
    }
}
