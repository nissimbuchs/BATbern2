package ch.batbern.shared.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Client IP resolution from {@code X-Forwarded-For} that a client cannot spoof (#1132).
 *
 * <p>Every hop in {@code X-Forwarded-For} left of the ones our own infrastructure appended is
 * whatever the client sent. The ALB runs in append mode, so a request carrying
 * {@code X-Forwarded-For: 10.1.0.5} arrives as {@code 10.1.0.5, <peer>}: the <b>first</b> hop is
 * attacker-controlled and must never be trusted. This class reads the header from the right.
 *
 * <p>Measured on production (2026-10-28): the hop the ALB appends for a request through
 * {@code api.batbern.ch} is the AWS HTTP API's egress address (3.71.120.0/22, 3.127.74.0/23), not
 * the end user's address. {@link #resolve(String, String)} therefore identifies the nearest public
 * peer, which is the API Gateway node for that path and the caller itself for a request sent to an
 * ALB directly.
 *
 * <p>#1152: the HTTP API now reaches the gateway through a VPC link to an internal ALB and
 * overwrites {@value #CLIENT_IP_HEADER} with the visitor's address. {@link #resolve(String, String,
 * String)} takes that header, but only when the request came over our private path (the hop
 * nearest to us is internal); a caller on the internet who sets the header on a request to a public
 * ALB is resolved from {@code X-Forwarded-For} as before. The gateway overwrites the header with
 * its resolved value before forwarding, so the services read the same address.
 */
public final class ClientIp {

    public static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /** #1152: set by the HTTP API to {@code $context.identity.sourceIp}, by the gateway to its result. */
    public static final String CLIENT_IP_HEADER = "X-BATbern-Client-IP";

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]*:[0-9a-fA-F:.]*$");

    private ClientIp() {
    }

    /**
     * The visitor's address: {@code clientIpHeader} when it is an IP literal and the request reached
     * us over a private hop (the rightmost {@code X-Forwarded-For} hop, or {@code remoteAddr} when
     * there is none, is internal); otherwise {@link #resolve(String, String)}.
     */
    public static String resolve(String clientIpHeader, String xForwardedFor, String remoteAddr) {
        String header = clientIpHeader == null ? null : clientIpHeader.trim();
        if (parseLiteral(header).isPresent() && cameOverPrivateHop(xForwardedFor, remoteAddr)) {
            return header;
        }
        return resolve(xForwardedFor, remoteAddr);
    }

    private static boolean cameOverPrivateHop(String xForwardedFor, String remoteAddr) {
        List<String> hops = hops(xForwardedFor);
        String nearest = hops.isEmpty() ? remoteAddr : hops.get(hops.size() - 1);
        return parseLiteral(nearest).map(ClientIp::isInternal).orElse(false);
    }

    /**
     * The rightmost {@code X-Forwarded-For} hop that is a public address, else {@code remoteAddr}.
     * Private, loopback and link-local hops are our own proxies (ALB, Service Connect) and are
     * skipped; unparseable entries are ignored.
     */
    public static String resolve(String xForwardedFor, String remoteAddr) {
        List<String> hops = hops(xForwardedFor);
        for (int i = hops.size() - 1; i >= 0; i--) {
            Optional<InetAddress> address = parseLiteral(hops.get(i));
            if (address.isPresent() && !isInternal(address.get())) {
                return hops.get(i);
            }
        }
        return remoteAddr;
    }

    /** The hops of an {@code X-Forwarded-For} value, trimmed, empty entries dropped. */
    public static List<String> hops(String xForwardedFor) {
        if (xForwardedFor == null || xForwardedFor.isBlank()) {
            return List.of();
        }
        return Arrays.stream(xForwardedFor.split(","))
                .map(String::trim)
                .filter(hop -> !hop.isEmpty())
                .toList();
    }

    /**
     * Parses an IP literal without ever resolving a host name: the input comes from the client,
     * and {@link InetAddress#getByName} would otherwise do a DNS lookup on it.
     */
    public static Optional<InetAddress> parseLiteral(String value) {
        if (value == null || !(IPV4.matcher(value).matches() || IPV6.matcher(value).matches())) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(value));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }

    private static boolean isInternal(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        // IPv6 unique local addresses (fc00::/7); isSiteLocalAddress covers only the deprecated fec0::/10
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
