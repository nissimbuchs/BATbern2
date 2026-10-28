package ch.batbern.shared.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Builds every {@link Pageable} with a total, deterministic order (#1131).
 *
 * <p>Offset pagination ({@code LIMIT n OFFSET m}) is only correct when the {@code ORDER BY} is
 * total. With no sort, or a sort on a non-unique column, PostgreSQL may order the tied rows
 * differently for every query (top-N heapsort is not stable), so consecutive pages repeat some
 * rows and never show others. In production this hid 337 of 2087 newsletter subscribers, because
 * 1829 of them share one {@code subscribed_at}.
 *
 * <p>Every helper here appends the entity's unique key ({@code id} by default) as the last sort
 * order unless the sort already contains it. {@code DeterministicPagingGuardTest} in shared-kernel
 * fails the build for any raw {@code PageRequest.of(...)} in production code.
 *
 * <p>Sort property names are JPA attribute names for JPQL/Criteria queries and column names for
 * native queries; {@code id} is both for every BATbern entity.
 */
public final class DeterministicPaging {

    /** Unique key of every BATbern entity, used as the default tie-breaker. */
    public static final String DEFAULT_UNIQUE_PROPERTY = "id";

    private DeterministicPaging() {
    }

    /** Appends {@code id ASC} unless the sort already orders by {@code id}. Null means unsorted. */
    public static Sort withTieBreaker(Sort sort) {
        return withTieBreaker(sort, DEFAULT_UNIQUE_PROPERTY);
    }

    /**
     * Appends {@code uniqueProperty ASC} unless the sort already orders by it.
     *
     * @param sort the requested sort, null or unsorted for "no preference"
     * @param uniqueProperty a property that is unique per row (and not null)
     */
    public static Sort withTieBreaker(Sort sort, String uniqueProperty) {
        if (uniqueProperty == null || uniqueProperty.isBlank()) {
            throw new IllegalArgumentException("uniqueProperty must not be blank");
        }
        Sort base = sort != null ? sort : Sort.unsorted();
        if (base.getOrderFor(uniqueProperty) != null) {
            return base;
        }
        return base.and(Sort.by(Sort.Direction.ASC, uniqueProperty));
    }

    /** Page {@code zeroBasedPage} of {@code size} rows, ordered by {@code sort} then {@code id}. */
    public static PageRequest pageRequest(int zeroBasedPage, int size, Sort sort) {
        return PageRequest.of(zeroBasedPage, size, withTieBreaker(sort));
    }

    /** Page {@code zeroBasedPage} of {@code size} rows, ordered by {@code sort} then {@code uniqueProperty}. */
    public static PageRequest pageRequest(int zeroBasedPage, int size, Sort sort, String uniqueProperty) {
        return PageRequest.of(zeroBasedPage, size, withTieBreaker(sort, uniqueProperty));
    }

    /** The same page with {@code id} appended to its sort; an unpaged request stays unpaged. */
    public static Pageable withTieBreaker(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            Sort sort = pageable != null ? pageable.getSort() : Sort.unsorted();
            return Pageable.unpaged(withTieBreaker(sort));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), withTieBreaker(pageable.getSort()));
    }

    /**
     * Page {@code zeroBasedPage} of {@code size} rows of a query whose own {@code ORDER BY} (in the
     * JPQL, the native SQL or the derived method name) decides the order. No sort is added, because
     * Spring Data would append it after that ORDER BY. The query's ORDER BY must end in a unique
     * key, unless the caller only ever takes the first page as a top-N slice.
     */
    public static PageRequest orderedByQuery(int zeroBasedPage, int size) {
        return PageRequest.of(zeroBasedPage, size);
    }
}
