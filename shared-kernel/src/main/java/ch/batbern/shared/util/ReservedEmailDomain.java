package ch.batbern.shared.util;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reserved-domain matcher for RFC 2606 (example.com, *.example, *.test, *.invalid,
 * *.localhost) and RFC 6761 (localhost). Pure utility — no Spring, no I/O.
 *
 * Used at the EmailService send boundary to refuse outbound mail to any address
 * on these domains. This prevents test fixtures, fuzzers, and security scanners
 * from consuming SES quota or dragging down bounce reputation.
 */
public final class ReservedEmailDomain {

    /** RFC 2606 reserved second-level domains. */
    private static final Set<String> RESERVED_DOMAINS = Set.of(
            "example.com", "example.org", "example.net", "localhost"
    );

    /** RFC 2606 / RFC 6761 reserved TLDs and sub-domain suffixes. */
    private static final List<String> RESERVED_SUFFIXES = List.of(
            ".example", ".example.com", ".example.org", ".example.net",
            ".test", ".invalid", ".localhost"
    );

    private ReservedEmailDomain() {
        // utility class
    }

    /**
     * @param email any address; null or malformed returns false (do not block on parse failure
     *              — the SES layer will reject malformed addresses on its own).
     * @return true if the domain part matches an RFC 2606 / RFC 6761 reserved pattern.
     */
    public static boolean isReserved(String email) {
        if (email == null) {
            return false;
        }
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return false;
        }
        String domain = email.substring(at + 1).toLowerCase(Locale.ROOT);
        if (RESERVED_DOMAINS.contains(domain)) {
            return true;
        }
        for (String suffix : RESERVED_SUFFIXES) {
            if (domain.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
