package ch.batbern.companyuser.security;

import ch.batbern.shared.security.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.net.InetAddress;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Authorization manager that allows requests from VPC internal network.
 *
 * Security Model:
 * - All microservices run in private VPC subnets (no public internet access)
 * - Service-to-service calls originate from VPC CIDR (10.1.0.0/16)
 * - Network-level isolation provides first layer of security
 * - This manager allows authenticated service calls from VPC internal IPs
 *
 * Use Cases:
 * - Event Management Service → Company User Service (user profile lookups)
 * - Speaker Coordination Service → Company User Service (speaker data)
 * - Partner Coordination Service → Company User Service (partner data)
 *
 * Note: This does NOT bypass authentication - requests must still have valid JWT
 * or be from trusted VPC internal sources. External requests (via API Gateway)
 * still require full JWT authentication.
 */
@Slf4j
public class VpcInternalAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final long networkAddress;
    private final long networkMask;

    /**
     * Create authorization manager for VPC CIDR.
     *
     * @param vpcCidr VPC CIDR notation (e.g., "10.1.0.0/16")
     */
    public VpcInternalAuthorizationManager(String vpcCidr) {
        String[] parts = vpcCidr.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid CIDR format: " + vpcCidr);
        }

        this.networkAddress = ipToLong(parts[0]);
        int prefixLength = Integer.parseInt(parts[1]);
        this.networkMask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;

        log.info("VPC Internal Authorization Manager initialized for CIDR: {}", vpcCidr);
    }

    /**
     * Spring Security 7 entry point. {@code authorize} is the single abstract method
     * (the deprecated {@code check} was removed in SS7); note the {@code ? extends}
     * wildcard on the authentication supplier added in SS7.
     */
    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
            RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        String xForwardedFor = request.getHeader(ClientIp.X_FORWARDED_FOR);

        if (isVpcInternal(request.getRemoteAddr(), xForwardedFor)) {
            log.debug("Allowing VPC internal request from: {}", request.getRemoteAddr());
            return new AuthorizationDecision(true);
        }

        // Not from VPC - check if authenticated with JWT
        Authentication auth = authentication.get();
        boolean isAuthenticated = auth != null && auth.isAuthenticated()
            && !"anonymousUser".equals(auth.getPrincipal());

        if (isAuthenticated) {
            log.debug("Allowing authenticated external request via: {}", xForwardedFor);
            return new AuthorizationDecision(true);
        }
        log.warn("Blocking unauthenticated external request from: {}",
            ClientIp.resolve(xForwardedFor, request.getRemoteAddr()));
        return new AuthorizationDecision(false);
    }

    /**
     * A request is VPC internal only if the connection AND every {@code X-Forwarded-For} hop are
     * VPC or loopback addresses (#1132). Service Connect delivers every request from 127.0.0.1,
     * so the connection alone proves nothing; the header's first hop is client-controlled, so it
     * proves nothing either. A request through the gateway always carries the public hop the ALB
     * appended, which makes it external whatever the client put in front of it.
     */
    private boolean isVpcInternal(String remoteAddr, String xForwardedFor) {
        if (!isInternalAddress(remoteAddr)) {
            return false;
        }
        return ClientIp.hops(xForwardedFor).stream().allMatch(this::isInternalAddress);
    }

    /** VPC CIDR or loopback. Never resolves a host name: the value may come from the client. */
    private boolean isInternalAddress(String value) {
        Optional<InetAddress> address = ClientIp.parseLiteral(value);
        if (address.isEmpty()) {
            return false;
        }
        if (address.get().isLoopbackAddress()) {
            return true;
        }
        byte[] bytes = address.get().getAddress();
        if (bytes.length != 4) {
            return false;
        }
        return (toLong(bytes) & networkMask) == (networkAddress & networkMask);
    }

    private static long toLong(byte[] bytes) {
        long result = 0;
        for (byte b : bytes) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }

    private static long ipToLong(String ipAddress) {
        return ClientIp.parseLiteral(ipAddress)
            .map(address -> toLong(address.getAddress()))
            .orElseThrow(() -> new IllegalArgumentException("Invalid IP address: " + ipAddress));
    }
}
