package ch.batbern.companyuser.service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The rules that pair companies which are probably one (#1251, story 5): pure functions over the key,
 * the display name and the website, so every rule is unit tested. A pair is listed once, under the
 * first rule it matches:
 * <ol>
 *   <li>{@link Rule#SAME_WEBSITE}: the same host (lower case, without scheme, {@code www.}, port and
 *       path); a host without a dot (a placeholder such as {@code n/a}) never matches. On a shared
 *       platform (LinkedIn, Facebook, XING, Instagram, X, GitHub, Google Sites, YouTube, link
 *       shorteners and the like, see {@link #PLATFORMS}) the host is many companies, so the profile
 *       counts: the host plus the identifying path ({@code linkedin.com/company/acme}); a platform
 *       address without a profile never matches;</li>
 *   <li>{@link Rule#SAME_DISPLAY_NAME}: the same display name (the key when there is none), folded:
 *       lower case, umlauts as ae/oe/ue, ß as ss, accents removed, hyphen, underscore and slash as a
 *       space, other punctuation removed, spaces collapsed;</li>
 *   <li>{@link Rule#SAME_NAME_WITHOUT_LEGAL_FORM}: the same folded display name once the legal forms
 *       (ag, gmbh, sa, sarl, ltd, inc, llc, kg) and a trailing {@code co} are removed. Generic words
 *       such as Kanton, Uni, Bern or Schweiz stay, so "Kanton Bern" and "Kanton Zürich" are no pair;
 *       a name that is nothing but a legal form never matches;</li>
 *   <li>{@link Rule#SLUG_PREFIX}: one key starts with the other (ignoring case), the shorter one at
 *       least four characters.</li>
 * </ol>
 * About 700 companies: every pair is compared, no index needed.
 */
public final class CompanyDuplicateRules {

    public enum Confidence { HIGH, MEDIUM, LOW }

    public enum Rule {
        SAME_WEBSITE(Confidence.HIGH),
        SAME_DISPLAY_NAME(Confidence.HIGH),
        SAME_NAME_WITHOUT_LEGAL_FORM(Confidence.MEDIUM),
        SLUG_PREFIX(Confidence.LOW);

        private final Confidence confidence;

        Rule(Confidence confidence) {
            this.confidence = confidence;
        }

        public Confidence confidence() {
            return confidence;
        }
    }

    /** What the rules read of a company. */
    public record Named(String key, String displayName, String website) {
    }

    /** A company with its compared forms, computed once. */
    record Normalized(Named company, String site, String name, String nameWithoutLegalForm, String key) {
    }

    /** Two companies of one rule, in the order of the input list. */
    public record Match(Named a, Named b, Rule rule) {
    }

    /** What the suggested target is chosen by. */
    public record Facts(String key, boolean partner, boolean logo, long users, Instant createdAt) {
    }

    static final int MIN_PREFIX = 4;

    private static final Set<String> LEGAL_FORMS = Set.of("ag", "gmbh", "sa", "sarl", "ltd", "inc", "llc", "kg");
    /**
     * Hosts that many companies share, each with the path words that come before the identifying one
     * ({@code linkedin.com/company/acme}: "company", then "acme"). A subdomain counts as the platform
     * ({@code ch.linkedin.com}, {@code m.facebook.com}).
     */
    static final Map<String, Set<String>> PLATFORMS = Map.ofEntries(
            Map.entry("linkedin.com", Set.of("company", "in", "school", "showcase", "pub")),
            Map.entry("xing.com", Set.of("pages", "companies", "company", "profile")),
            Map.entry("facebook.com", Set.of("pages", "pg", "people", "groups")),
            Map.entry("fb.com", Set.of("pages", "pg", "people", "groups")),
            Map.entry("instagram.com", Set.of()),
            Map.entry("x.com", Set.of()),
            Map.entry("twitter.com", Set.of()),
            Map.entry("github.com", Set.of()),
            Map.entry("gitlab.com", Set.of()),
            Map.entry("bitbucket.org", Set.of()),
            Map.entry("sites.google.com", Set.of("view", "site")),
            Map.entry("youtube.com", Set.of("c", "channel", "user")),
            Map.entry("vimeo.com", Set.of()),
            Map.entry("medium.com", Set.of()),
            Map.entry("tiktok.com", Set.of()),
            Map.entry("threads.net", Set.of()),
            Map.entry("about.me", Set.of()),
            Map.entry("linktr.ee", Set.of()),
            Map.entry("bit.ly", Set.of()),
            Map.entry("goo.gl", Set.of()),
            Map.entry("tinyurl.com", Set.of()),
            Map.entry("t.co", Set.of()));

    private static final Pattern SCHEME = Pattern.compile("^[a-z][a-z0-9+.-]*://");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATORS = Pattern.compile("[-_/]");
    private static final Pattern PUNCTUATION = Pattern.compile("[^\\p{L}\\p{N}\\s]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private CompanyDuplicateRules() {
    }

    /** Every pair of the list that a rule matches, once, under its first rule. */
    public static List<Match> pairs(List<Named> companies) {
        List<Normalized> all = companies.stream().map(CompanyDuplicateRules::normalize).toList();
        List<Match> pairs = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                Normalized a = all.get(i);
                Normalized b = all.get(j);
                rule(a, b).ifPresent(rule -> pairs.add(new Match(a.company(), b.company(), rule)));
            }
        }
        return pairs;
    }

    static Normalized normalize(Named c) {
        String key = c.key() == null ? "" : c.key().toLowerCase(Locale.ROOT);
        String name = fold(c.displayName() == null || c.displayName().isBlank() ? c.key() : c.displayName());
        return new Normalized(c, site(c.website()), name, withoutLegalForm(name), key);
    }

    /** The first rule two companies match, if any. */
    static Optional<Rule> rule(Normalized a, Normalized b) {
        if (!a.site().isEmpty() && a.site().equals(b.site())) {
            return Optional.of(Rule.SAME_WEBSITE);
        }
        if (!a.name().isEmpty() && a.name().equals(b.name())) {
            return Optional.of(Rule.SAME_DISPLAY_NAME);
        }
        if (!a.nameWithoutLegalForm().isEmpty() && a.nameWithoutLegalForm().equals(b.nameWithoutLegalForm())) {
            return Optional.of(Rule.SAME_NAME_WITHOUT_LEGAL_FORM);
        }
        boolean aShorter = a.key().length() <= b.key().length();
        String shorter = aShorter ? a.key() : b.key();
        String longer = aShorter ? b.key() : a.key();
        if (shorter.length() >= MIN_PREFIX && longer.startsWith(shorter)) {
            return Optional.of(Rule.SLUG_PREFIX);
        }
        return Optional.empty();
    }

    /** The host of a website: lower case, without scheme, user, port, path and a leading {@code www.}. */
    static String host(String website) {
        if (website == null) {
            return "";
        }
        String host = SCHEME.matcher(website.trim().toLowerCase(Locale.ROOT)).replaceFirst("");
        int end = indexOfAny(host, "/?#");
        if (end >= 0) {
            host = host.substring(0, end);
        }
        host = host.substring(host.lastIndexOf('@') + 1);
        int port = host.indexOf(':');
        if (port >= 0) {
            host = host.substring(0, port);
        }
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        // A placeholder such as "-", "n/a" or "none" is no host and must not pair every company using it
        return host.contains(".") ? host : "";
    }

    /**
     * What the website rule compares: the {@link #host} of an own website; on a shared platform the
     * platform, the path words before the identifying one and the identifying one, lower case
     * ({@code https://ch.linkedin.com/company/Acme/about} is {@code linkedin.com/company/acme}). A
     * platform address without a profile ({@code linkedin.com}, {@code facebook.com/profile.php?id=1})
     * is empty and never matches.
     */
    static String site(String website) {
        String host = host(website);
        if (host.isEmpty()) {
            return "";
        }
        String platform = PLATFORMS.keySet().stream()
                .filter(p -> host.equals(p) || host.endsWith("." + p))
                .findFirst().orElse(null);
        if (platform == null) {
            return host;
        }
        Set<String> prefixes = PLATFORMS.get(platform);
        StringBuilder site = new StringBuilder(platform);
        for (String segment : pathSegments(website)) {
            site.append('/').append(segment);
            if (!prefixes.contains(segment)) {
                // A script such as profile.php identifies by its query, which is not compared
                return segment.contains(".") ? "" : site.toString();
            }
        }
        return "";
    }

    /** The path of a website split at slashes, lower case, without query, fragment and empty words. */
    private static List<String> pathSegments(String website) {
        String rest = SCHEME.matcher(website.trim().toLowerCase(Locale.ROOT)).replaceFirst("");
        int query = indexOfAny(rest, "?#");
        if (query >= 0) {
            rest = rest.substring(0, query);
        }
        int path = rest.indexOf('/');
        if (path < 0) {
            return List.of();
        }
        return Arrays.stream(rest.substring(path + 1).split("/")).filter(w -> !w.isEmpty()).toList();
    }

    /**
     * A name folded for comparing: lower case, umlauts as ae/oe/ue, ß as ss, accents removed, hyphen,
     * underscore and slash as a space, other punctuation removed, spaces collapsed.
     */
    static String fold(String name) {
        if (name == null) {
            return "";
        }
        String folded = Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        folded = MARKS.matcher(Normalizer.normalize(folded, Normalizer.Form.NFD)).replaceAll("");
        folded = SEPARATORS.matcher(folded).replaceAll(" ");
        folded = PUNCTUATION.matcher(folded).replaceAll("");
        return SPACES.matcher(folded).replaceAll(" ").trim();
    }

    /** A folded name without the legal-form words and then without a trailing {@code co}. */
    static String withoutLegalForm(String folded) {
        List<String> words = Arrays.stream(folded.split(" "))
                .filter(w -> !w.isEmpty() && !LEGAL_FORMS.contains(w))
                .collect(Collectors.toCollection(ArrayList::new));
        if (!words.isEmpty() && words.get(words.size() - 1).equals("co")) {
            words.remove(words.size() - 1);
        }
        return String.join(" ", words);
    }

    /**
     * Whether {@code a} is the suggested target (the company that stays): the partner, else the one
     * with a logo, else the one with more users, else the older one (a known date before none), else
     * the smaller key.
     */
    public static boolean firstIsTarget(Facts a, Facts b) {
        if (a.partner() != b.partner()) {
            return a.partner();
        }
        if (a.logo() != b.logo()) {
            return a.logo();
        }
        if (a.users() != b.users()) {
            return a.users() > b.users();
        }
        if (a.createdAt() != null && b.createdAt() != null && !a.createdAt().equals(b.createdAt())) {
            return a.createdAt().isBefore(b.createdAt());
        }
        if ((a.createdAt() == null) != (b.createdAt() == null)) {
            return a.createdAt() != null;
        }
        return a.key().compareTo(b.key()) <= 0;
    }

    private static int indexOfAny(String s, String chars) {
        for (int i = 0; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }
}
