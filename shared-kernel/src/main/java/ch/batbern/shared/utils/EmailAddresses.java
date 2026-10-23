package ch.batbern.shared.utils;

import java.net.IDN;
import java.util.regex.Pattern;

/**
 * The one rule for which email addresses BATbern accepts (#1102).
 *
 * <p>We accept every address Amazon SES can deliver to, and nothing else:
 * <ul>
 *   <li><b>Local part</b>: any RFC 5322 dot-atom in ASCII, up to 64 characters. That includes
 *       {@code +}, {@code '} and the other atext characters people actually use.
 *       <b>Unicode local parts are rejected</b>: SES does not implement SMTPUTF8 (RFC 6531), so a
 *       message to {@code jürg@example.ch} cannot be sent at all. Quoted local parts and IP-literal
 *       domains are valid RFC 5322 but not accepted either; nobody registers with them.</li>
 *   <li><b>Domain</b>: any domain with at least two labels, internationalised ones included
 *       ({@code müller.ch}). SES needs those in Punycode, which {@link #toDeliveryForm} produces;
 *       addresses are stored as entered.</li>
 * </ul>
 *
 * <p>The same rule exists as {@link #OPENAPI_PATTERN} on every {@code format: email} field in
 * {@code docs/api/*.openapi.yml}, and as {@code web-frontend/src/utils/validation/email.ts} in the
 * frontend. The regex form cannot check lengths or IDNA validity, so it is deliberately looser;
 * {@code EmailAddressesTest} guards that it never rejects an address accepted here.
 */
public final class EmailAddresses {

    private static final String ATEXT = "A-Za-z0-9!#$%&'*+/=?^_`{|}~-";

    /** Regex form of the rule for OpenAPI {@code pattern:} and the generated {@code @Pattern}. */
    public static final String OPENAPI_PATTERN =
        "^[" + ATEXT + "]+(\\.[" + ATEXT + "]+)*@[^\\s@.]+(\\.[^\\s@.]+)+$";

    private static final Pattern LOCAL_PART = Pattern.compile("[" + ATEXT + "]+(\\.[" + ATEXT + "]+)*");
    private static final Pattern ASCII_LABEL = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?");
    private static final Pattern TOP_LEVEL = Pattern.compile("[A-Za-z]{2,}|xn--[A-Za-z0-9-]+");

    private static final int MAX_LOCAL_PART = 64;
    private static final int MAX_LABEL = 63;
    private static final int MAX_DOMAIN = 253;
    private static final int MAX_ADDRESS = 254;

    private EmailAddresses() {
    }

    /** True when {@code email} is an address we accept and SES can deliver to. Does not trim. */
    public static boolean isAcceptable(String email) {
        return toAsciiOrNull(email) != null;
    }

    /** True when the part before the {@code @} contains non-ASCII characters (undeliverable via SES). */
    public static boolean hasNonAsciiLocalPart(String email) {
        if (email == null) {
            return false;
        }
        int at = email.lastIndexOf('@');
        String local = at < 0 ? email : email.substring(0, at);
        return local.chars().anyMatch(c -> c > 0x7F);
    }

    /**
     * The address as SES needs it: trimmed, domain in Punycode, local part untouched.
     *
     * @throws IllegalArgumentException when the address is not acceptable
     */
    public static String toDeliveryForm(String email) {
        String ascii = toAsciiOrNull(email == null ? null : email.trim());
        if (ascii == null) {
            String reason = hasNonAsciiLocalPart(email)
                ? "the local part contains non-ASCII characters, which SES cannot deliver"
                : "it is not a valid address";
            throw new IllegalArgumentException("Cannot deliver to email address: " + reason);
        }
        return ascii;
    }

    private static String toAsciiOrNull(String email) {
        if (email == null || email.isEmpty()) {
            return null;
        }
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return null;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        if (local.length() > MAX_LOCAL_PART || !LOCAL_PART.matcher(local).matches()) {
            return null;
        }
        String asciiDomain = toAsciiDomainOrNull(domain);
        if (asciiDomain == null) {
            return null;
        }
        String address = local + "@" + asciiDomain;
        return address.length() <= MAX_ADDRESS ? address : null;
    }

    private static String toAsciiDomainOrNull(String domain) {
        if (domain.startsWith(".") || domain.endsWith(".") || domain.contains("..")) {
            return null;
        }
        String ascii;
        try {
            ascii = IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (ascii.length() > MAX_DOMAIN) {
            return null;
        }
        String[] labels = ascii.split("\\.", -1);
        if (labels.length < 2) {
            return null;
        }
        for (String label : labels) {
            if (label.isEmpty() || label.length() > MAX_LABEL || !ASCII_LABEL.matcher(label).matches()) {
                return null;
            }
        }
        return TOP_LEVEL.matcher(labels[labels.length - 1]).matches() ? ascii : null;
    }
}
