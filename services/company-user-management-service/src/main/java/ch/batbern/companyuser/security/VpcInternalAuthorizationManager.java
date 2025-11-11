package ch.batbern.companyuser.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.net.InetAddress;
import java.net.UnknownHostException;
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

    private final String vpcCidr;
    private final long networkAddress;
    private final long networkMask;

    /**
     * Create authorization manager for VPC CIDR.
     *
     * @param vpcCidr VPC CIDR notation (e.g., "10.1.0.0/16")
     */
    public VpcInternalAuthorizationManager(String vpcCidr) {
        this.vpcCidr = vpcCidr;

        String[] parts = vpcCidr.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid CIDR format: " + vpcCidr);
        }

        this.networkAddress = ipToLong(parts[0]);
        int prefixLength = Integer.parseInt(parts[1]);
        this.networkMask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;

        log.info("VPC Internal Authorization Manager initialized for CIDR: {}", vpcCidr);
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        String remoteAddr = getClientIpAddress(request);

        try {
            boolean isVpcInternal = isIpInVpc(remoteAddr);

            if (isVpcInternal) {
                log.debug("Allowing VPC internal request from: {}", remoteAddr);
                return new AuthorizationDecision(true);
            } else {
                // Not from VPC - check if authenticated with JWT
                Authentication auth = authentication.get();
                boolean isAuthenticated = auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal());

                if (isAuthenticated) {
                    log.debug("Allowing authenticated external request from: {}", remoteAddr);
                    return new AuthorizationDecision(true);
                } else {
                    log.warn("Blocking unauthenticated external request from: {}", remoteAddr);
                    return new AuthorizationDecision(false);
                }
            }

        } catch (UnknownHostException e) {
            log.error("Failed to parse IP address: {}", remoteAddr, e);
            return new AuthorizationDecision(false);
        }
    }

    /**
     * Check if IP address is within VPC CIDR range or is localhost/loopback.
     * Localhost (127.0.0.1, ::1) indicates ECS Service Connect internal routing.
     */
    private boolean isIpInVpc(String ipAddress) throws UnknownHostException {
        // Allow localhost/loopback (ECS Service Connect uses localhost for service-to-service calls)
        if ("127.0.0.1".equals(ipAddress) || "localhost".equals(ipAddress) || "::1".equals(ipAddress)) {
            return true;
        }

        // Check VPC CIDR range
        long ip = ipToLong(ipAddress);
        return (ip & networkMask) == (networkAddress & networkMask);
    }

    /**
     * Convert IP address string to long for bitwise operations.
     */
    private long ipToLong(String ipAddress) {
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);
            byte[] bytes = addr.getAddress();

            long result = 0;
            for (byte b : bytes) {
                result = (result << 8) | (b & 0xFF);
            }
            return result;

        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP address: " + ipAddress, e);
        }
    }

    /**
     * Extract client IP address from request.
     * Handles X-Forwarded-For header from API Gateway/ALB.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // Check X-Forwarded-For header (set by API Gateway/ALB)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs: client, proxy1, proxy2
            // First IP is the original client
            return xForwardedFor.split(",")[0].trim();
        }

        // Fallback to remote address (direct connection)
        return request.getRemoteAddr();
    }
}
