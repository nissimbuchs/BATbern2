package ch.batbern.events.forwarding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The SPF/DKIM/DMARC verdict SES recorded for an inbound mail.
 *
 * <p>SES prepends an {@code Authentication-Results: amazonses.com; ...} header to every mail it
 * stores. Only the <b>topmost</b> such header is trusted: a sender can put any number of
 * forged {@code Authentication-Results} lines into the message they send, but they always end up
 * below the one SES adds.
 *
 * <p>The organizer-only aliases authorize by the From address, which anyone can type. This
 * verdict is what makes that From trustworthy: DMARC pass, or (when the sender's domain
 * publishes no DMARC policy) a passing DKIM signature from the From domain.
 */
public final class MailAuthentication {

    private static final String SES_AUTHSERV_ID = "amazonses.com";

    private final String dmarc;
    private final List<String> passingDkimDomains;

    private MailAuthentication(String dmarc, List<String> passingDkimDomains) {
        this.dmarc = dmarc;
        this.passingDkimDomains = passingDkimDomains;
    }

    /**
     * @param authenticationResultsHeaders all {@code Authentication-Results} values in message
     *                                     order (topmost first), as {@code MimeMessage.getHeader} returns them
     */
    public static MailAuthentication fromHeaders(String[] authenticationResultsHeaders) {
        if (authenticationResultsHeaders == null || authenticationResultsHeaders.length == 0) {
            return new MailAuthentication("none", List.of());
        }
        String top = authenticationResultsHeaders[0].replaceAll("\\r?\\n[ \\t]+", " ").trim();
        String[] parts = top.split(";");
        if (!parts[0].trim().toLowerCase(Locale.ROOT).equals(SES_AUTHSERV_ID)) {
            return new MailAuthentication("none", List.of());
        }
        String dmarc = "none";
        List<String> dkimDomains = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim().toLowerCase(Locale.ROOT);
            if (part.startsWith("dmarc=")) {
                dmarc = firstToken(part.substring("dmarc=".length()));
            } else if (part.startsWith("dkim=pass")) {
                String domain = dkimDomain(part);
                if (domain != null) {
                    dkimDomains.add(domain);
                }
            }
        }
        return new MailAuthentication(dmarc, List.copyOf(dkimDomains));
    }

    /**
     * Whether the From address of this mail is authenticated: DMARC pass, or no DMARC verdict and
     * a passing DKIM signature aligned (relaxed) with the sender's domain. DMARC fail is final.
     */
    public boolean verifiesSender(String senderEmail) {
        if (dmarc.equals("pass")) {
            return true;
        }
        if (dmarc.equals("fail") || senderEmail == null || !senderEmail.contains("@")) {
            return false;
        }
        String senderDomain = senderEmail.substring(senderEmail.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        return passingDkimDomains.stream().anyMatch(d -> aligned(d, senderDomain));
    }

    public String dmarc() {
        return dmarc;
    }

    private static boolean aligned(String signingDomain, String senderDomain) {
        return signingDomain.equals(senderDomain)
                || signingDomain.endsWith("." + senderDomain)
                || senderDomain.endsWith("." + signingDomain);
    }

    private static String dkimDomain(String dkimPart) {
        for (String token : dkimPart.split("\\s+")) {
            if (token.startsWith("header.d=")) {
                return token.substring("header.d=".length());
            }
            if (token.startsWith("header.i=")) {
                String value = token.substring("header.i=".length());
                return value.contains("@") ? value.substring(value.indexOf('@') + 1) : value;
            }
        }
        return null;
    }

    private static String firstToken(String value) {
        String[] tokens = value.trim().split("[\\s(]+", 2);
        return tokens.length == 0 ? "none" : tokens[0];
    }
}
