package ch.batbern.companyuser.specification;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.shared.api.FilterCriteria;
import ch.batbern.shared.api.FilterOperator;
import jakarta.persistence.criteria.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JPA Specification builder for Company filtering
 * AC14: Convert FilterCriteria to JPA Specifications for SQL injection-safe queries
 */
@Slf4j
public class CompanySpecification {

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
            case CONTAINS -> cb.like(cb.lower(path.as(String.class)), "%" + value.toString().toLowerCase() + "%");
            case STARTS_WITH -> cb.like(cb.lower(path.as(String.class)), value.toString().toLowerCase() + "%");
            case ENDS_WITH -> cb.like(cb.lower(path.as(String.class)), "%" + value.toString().toLowerCase());
            case IN -> path.in((List<?>) value);
            case NOT_IN -> cb.not(path.in((List<?>) value));
            case IS_NULL -> value != null && (Boolean) value ? cb.isNull(path) : cb.isNotNull(path);
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
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
