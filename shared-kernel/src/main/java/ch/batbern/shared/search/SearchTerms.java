package ch.batbern.shared.search;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A free-text search input split into terms.
 *
 * <p>Every search in BATbern means the same thing: each term must occur as a substring of at least
 * one searchable field, in any order, after both sides are folded by the SQL function
 * {@code batbern_fold} (lower case, {@code unaccent}, {@code ae/oe/ue} as {@code a/o/u}, hyphen,
 * apostrophe and underscore as a space). The fold runs in SQL on both sides, so this class does no
 * folding; it only splits and escapes. {@link SearchPredicates} turns the terms into a predicate.
 *
 * <p>Terms are split on whitespace, {@code -}, {@code '} and {@code _} (the characters the fold
 * turns into a space), so a term never contains a space after folding. At most {@link #MAX_TERMS}
 * terms are kept. Each term is escaped for {@code LIKE ... ESCAPE '\'}: {@code \}, {@code %} and
 * {@code _} match literally. A blank input has no terms and means "no search filter".
 */
public final class SearchTerms {

    /** Upper bound on terms, so a pasted paragraph cannot build an unbounded predicate. */
    public static final int MAX_TERMS = 8;

    /** The escape character used in every {@code LIKE} built from these terms. */
    public static final char ESCAPE = '\\';

    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-'_]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final SearchTerms NONE = new SearchTerms(List.of(), "");

    private final List<String> terms;
    private final String phrase;

    private SearchTerms(List<String> terms, String phrase) {
        this.terms = terms;
        this.phrase = phrase;
    }

    /**
     * Split {@code input} into escaped terms. Null, empty or separator-only input yields
     * {@link #isEmpty() no terms}.
     */
    public static SearchTerms parse(String input) {
        if (input == null || input.isBlank()) {
            return NONE;
        }
        List<String> terms = new ArrayList<>();
        for (String token : SEPARATORS.split(input.strip())) {
            if (!token.isEmpty()) {
                terms.add(escapeLike(token));
                if (terms.size() == MAX_TERMS) {
                    break;
                }
            }
        }
        if (terms.isEmpty()) {
            return NONE;
        }
        return new SearchTerms(List.copyOf(terms), WHITESPACE.matcher(input.strip()).replaceAll(" "));
    }

    /**
     * Escape {@code value} for {@code LIKE ... ESCAPE '\'}: {@code \}, {@code %} and {@code _} match
     * literally. Also used by filter operators ({@code $contains}, {@code $startsWith}) that take a
     * single raw value.
     */
    public static String escapeLike(String value) {
        StringBuilder out = new StringBuilder(value.length() + 4);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ESCAPE || c == '%' || c == '_') {
                out.append(ESCAPE);
            }
            out.append(c);
        }
        return out.toString();
    }

    /** The escaped terms, in input order; empty for a blank input. */
    public List<String> terms() {
        return terms;
    }

    /** True for a blank input: the caller applies no search filter. */
    public boolean isEmpty() {
        return terms.isEmpty();
    }

    /**
     * The whole input, trimmed, inner whitespace collapsed, NOT escaped. Used for ranking only
     * (exact match, prefix), never inside a {@code LIKE}.
     */
    public String phrase() {
        return phrase;
    }

    @Override
    public String toString() {
        return "SearchTerms" + terms;
    }
}
