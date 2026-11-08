package ch.batbern.shared.search;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Criteria predicates for {@link SearchTerms}: every term is a substring of at least
 * one field, after {@code batbern_fold} on both sides.
 *
 * <p>The SQL function {@code batbern_fold} is created by each searching service's Flyway migration
 * from the canonical body in {@code shared-kernel/src/main/resources/db/search/batbern_fold.sql};
 * {@code SearchFoldContract} (test fixture) fails a service whose copy differs by one byte. A term
 * becomes {@code batbern_fold(field) LIKE '%' || batbern_fold(:term) || '%' ESCAPE '\'}, so Java
 * never folds and both sides cannot drift apart.
 */
public final class SearchPredicates {

    /** Name of the SQL fold function. */
    public static final String FOLD_FUNCTION = "batbern_fold";

    private SearchPredicates() {
    }

    /** {@code batbern_fold(x)}. */
    public static Expression<String> fold(CriteriaBuilder cb, Expression<String> x) {
        return cb.function(FOLD_FUNCTION, String.class, x);
    }

    /** {@code batbern_fold(field) LIKE '%' || batbern_fold(:term) || '%' ESCAPE '\'}; term is escaped. */
    public static Predicate contains(CriteriaBuilder cb, Expression<String> field, String escapedTerm) {
        Expression<String> pattern = cb.concat(
                cb.concat(cb.literal("%"), fold(cb, cb.literal(escapedTerm))), cb.literal("%"));
        return cb.like(fold(cb, field), pattern, SearchTerms.ESCAPE);
    }

    /**
     * Every term occurs in at least one of {@code fields}. No terms: always true, so a blank input
     * leaves the query unfiltered.
     */
    @SafeVarargs
    public static Predicate allTermsInAnyField(CriteriaBuilder cb, SearchTerms terms,
            Expression<String>... fields) {
        return allTerms(cb, terms, term -> {
            List<Predicate> any = new ArrayList<>(fields.length);
            for (Expression<String> field : fields) {
                any.add(contains(cb, field, term));
            }
            return cb.or(any.toArray(new Predicate[0]));
        });
    }

    /**
     * Every term satisfies {@code anyFieldMatcher}, which receives one escaped term and returns
     * "this term occurs somewhere in the record", typically an OR over {@link #contains} plus
     * {@code EXISTS} subqueries for child rows. No terms: always true.
     */
    public static Predicate allTerms(CriteriaBuilder cb, SearchTerms terms,
            Function<String, Predicate> anyFieldMatcher) {
        if (terms == null || terms.isEmpty()) {
            return cb.conjunction();
        }
        List<Predicate> all = new ArrayList<>(terms.terms().size());
        for (String term : terms.terms()) {
            all.add(anyFieldMatcher.apply(term));
        }
        return cb.and(all.toArray(new Predicate[0]));
    }

    /** {@code batbern_fold(field) = batbern_fold(:phrase)}, for ranking an exact match first. */
    public static Predicate foldedEquals(CriteriaBuilder cb, Expression<String> field, String phrase) {
        return cb.equal(fold(cb, field), fold(cb, cb.literal(phrase)));
    }

    /**
     * The folded field starts with the folded phrase, for ranking a prefix match second. Uses
     * {@code locate} rather than {@code LIKE}, so the phrase needs no escaping.
     */
    public static Predicate foldedStartsWith(CriteriaBuilder cb, Expression<String> field, String phrase) {
        return cb.equal(cb.locate(fold(cb, field), fold(cb, cb.literal(phrase))), 1);
    }
}
