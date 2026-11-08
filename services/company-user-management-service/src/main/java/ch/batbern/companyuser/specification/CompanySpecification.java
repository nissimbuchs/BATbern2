package ch.batbern.companyuser.specification;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.shared.api.FilterCriteria;
import ch.batbern.shared.api.FilterOperator;
import ch.batbern.shared.search.SearchPredicates;
import ch.batbern.shared.search.SearchTerms;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JPA Specification builder for Company filtering
 * AC14: Convert FilterCriteria to JPA Specifications for SQL injection-safe queries
 */
@Slf4j
public class CompanySpecification {

    /**
     * Every word of {@code search} occurs in the slug ({@code name}) or the display
     * name, folded by {@code batbern_fold}, in any order. Blank: unrestricted. Used by the paged
     * company list, the autocomplete and {@code GET /internal/companies}.
     */
    public static Specification<Company> matchesSearch(String search) {
        SearchTerms terms = SearchTerms.parse(search);
        if (terms.isEmpty()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> SearchPredicates.allTermsInAnyField(cb, terms,
                root.get("name"), root.get("displayName"));
    }

    /**
     * {@link #matchesSearch} ordered for an autocomplete: exact display name or slug first, then a
     * prefix, then any other match; display name and the unique slug break ties. Applied to the row
     * query only, never to Spring Data's count query.
     */
    public static Specification<Company> matchesSearchRanked(String search) {
        SearchTerms terms = SearchTerms.parse(search);
        return matchesSearch(search).and((root, query, cb) -> {
            if (!Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                List<Order> order = new ArrayList<>();
                if (!terms.isEmpty()) {
                    Expression<String> name = root.get("name");
                    Expression<String> displayName = root.get("displayName");
                    String phrase = terms.phrase();
                    order.add(cb.asc(cb.<Integer>selectCase()
                            .when(cb.or(SearchPredicates.foldedEquals(cb, displayName, phrase),
                                    SearchPredicates.foldedEquals(cb, name, phrase)), 0)
                            .when(cb.or(SearchPredicates.foldedStartsWith(cb, displayName, phrase),
                                    SearchPredicates.foldedStartsWith(cb, name, phrase)), 1)
                            .otherwise(2)));
                }
                order.add(cb.asc(root.get("displayName")));
                order.add(cb.asc(root.get("name")));
                query.orderBy(order);
            }
            return cb.conjunction();
        });
    }

    /**
     * Builds a JPA Specification from FilterCriteria
     * Uses JPA Criteria API with parameterized queries to prevent SQL injection
     */
    public static Specification<Company> fromFilterCriteria(FilterCriteria criteria) {
        if (criteria == null) {
            return null;
        }

        return (root, query, criteriaBuilder) -> buildPredicate(criteria, root, query, criteriaBuilder);
    }

    private static Predicate buildPredicate(FilterCriteria criteria, Root<Company> root,
                                           CriteriaQuery<?> query, CriteriaBuilder cb) {
        // Handle composite criteria (AND, OR, NOT)
        if (criteria.isComposite()) {
            return buildCompositePredicate(criteria, root, query, cb);
        }

        // Handle leaf criteria (field + operator + value)
        return buildLeafPredicate(criteria, root, cb);
    }

    private static Predicate buildCompositePredicate(FilterCriteria criteria, Root<Company> root,
                                                    CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Predicate> childPredicates = criteria.getChildren().stream()
                .map(child -> buildPredicate(child, root, query, cb))
                .collect(Collectors.toList());

        return switch (criteria.getOperator()) {
            case AND -> cb.and(childPredicates.toArray(new Predicate[0]));
            case OR -> cb.or(childPredicates.toArray(new Predicate[0]));
            case NOT -> cb.not(childPredicates.get(0));
            default -> throw new IllegalArgumentException("Unknown composite operator: " + criteria.getOperator());
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate buildLeafPredicate(FilterCriteria criteria, Root<Company> root, CriteriaBuilder cb) {
        String field = criteria.getField();
        FilterOperator operator = criteria.getOperator();
        Object value = criteria.getValue();

        // Get the path for the field (supports dot notation for nested fields)
        Path path = getPath(root, field);

        return switch (operator) {
            case EQUALS -> cb.equal(path, value);
            case NOT_EQUALS -> cb.notEqual(path, value);
            case GREATER_THAN -> cb.greaterThan(path, (Comparable) value);
            case GREATER_THAN_OR_EQUAL -> cb.greaterThanOrEqualTo(path, (Comparable) value);
            case LESS_THAN -> cb.lessThan(path, (Comparable) value);
            case LESS_THAN_OR_EQUAL -> cb.lessThanOrEqualTo(path, (Comparable) value);
            // % _ \ in the value match literally
            case CONTAINS -> cb.like(cb.lower(path.as(String.class)),
                    "%" + likeValue(value) + "%", SearchTerms.ESCAPE);
            case STARTS_WITH -> cb.like(cb.lower(path.as(String.class)), likeValue(value) + "%", SearchTerms.ESCAPE);
            case ENDS_WITH -> cb.like(cb.lower(path.as(String.class)), "%" + likeValue(value), SearchTerms.ESCAPE);
            case IN -> path.in((List<?>) value);
            case NOT_IN -> cb.not(path.in((List<?>) value));
            case IS_NULL -> value != null && (Boolean) value ? cb.isNull(path) : cb.isNotNull(path);
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
    }

    private static String likeValue(Object value) {
        return SearchTerms.escapeLike(value.toString().toLowerCase());
    }

    /**
     * Resolves path for field, supporting dot notation for nested fields
     * Example: "author.name" -> root.get("author").get("name")
     */
    private static Path<Object> getPath(Root<Company> root, String field) {
        if (field.contains(".")) {
            String[] parts = field.split("\\.");
            Path<Object> path = root.get(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                path = path.get(parts[i]);
            }
            return path;
        }
        return root.get(field);
    }
}
