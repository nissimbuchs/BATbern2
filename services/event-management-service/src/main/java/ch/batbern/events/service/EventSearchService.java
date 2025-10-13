package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.api.*;
import ch.batbern.shared.dto.PaginatedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Event Search Service
 * Story 1.15a.1: Events API Consolidation
 *
 * Handles event search with filtering, sorting, and pagination.
 * Uses utilities from Story 1.15a (API Consolidation Foundation).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventSearchService {

    private final EventRepository eventRepository;

    /**
     * Search events with filter, sort, and pagination
     *
     * @param filterJson JSON filter string (from FilterParser)
     * @param sortStr Sort string (from SortParser)
     * @param page Page number (1-indexed)
     * @param limit Items per page
     * @return Paginated event results
     */
    public PaginatedResponse<Event> searchEvents(String filterJson, String sortStr, Integer page, Integer limit) {
        // Parse pagination parameters
        PaginationParams paginationParams = PaginationUtils.parseParams(page, limit);
        int pageNum = paginationParams.getPage();
        int pageSize = paginationParams.getLimit();

        // Parse sort parameters
        List<SortCriteria> sortCriteria = parseSortCriteria(sortStr);
        Sort sort = buildSort(sortCriteria);

        // Parse filter parameters
        Specification<Event> spec = buildFilterSpecification(filterJson);

        // Create pageable
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort); // Convert to 0-indexed

        // Execute query
        Page<Event> eventPage = eventRepository.findAll(spec, pageable);

        // Generate pagination metadata
        PaginationMetadata metadata = PaginationUtils.generateMetadata(
                pageNum,
                pageSize,
                eventPage.getTotalElements()
        );

        // Build response
        return PaginatedResponse.<Event>builder()
                .data(eventPage.getContent())
                .pagination(metadata)
                .build();
    }

    /**
     * Parse sort string using SortParser
     */
    private List<SortCriteria> parseSortCriteria(String sortStr) {
        if (sortStr == null || sortStr.trim().isEmpty()) {
            // Default sort: newest events first
            return List.of(new SortCriteria("date", SortDirection.DESC));
        }

        // Let SortParser throw ValidationException for invalid syntax
        return SortParser.parse(sortStr);
    }

    /**
     * Build Spring Data Sort from SortCriteria list
     */
    private Sort buildSort(List<SortCriteria> sortCriteria) {
        if (sortCriteria == null || sortCriteria.isEmpty()) {
            return Sort.by(Sort.Direction.DESC, "date");
        }

        List<Sort.Order> orders = new ArrayList<>();
        for (SortCriteria criteria : sortCriteria) {
            Sort.Direction direction = criteria.getDirection() == SortDirection.ASC
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;
            orders.add(new Sort.Order(direction, criteria.getField()));
        }

        return Sort.by(orders);
    }

    /**
     * Build JPA Specification from filter JSON
     */
    private Specification<Event> buildFilterSpecification(String filterJson) {
        if (filterJson == null || filterJson.trim().isEmpty()) {
            return null;
        }

        // Let FilterParser throw ValidationException for invalid syntax
        FilterCriteria rootCriteria = FilterParser.parse(filterJson);
        if (rootCriteria == null) {
            return null;
        }
        return buildSpecificationFromCriteria(rootCriteria);
    }

    /**
     * Convert FilterCriteria tree to JPA Specification
     * Handles nested AND/OR operators
     */
    private Specification<Event> buildSpecificationFromCriteria(FilterCriteria criteria) {
        if (criteria == null) {
            return null;
        }

        FilterOperator operator = criteria.getOperator();

        // Handle logical operators (AND, OR, NOT)
        if (operator == FilterOperator.AND || operator == FilterOperator.OR || operator == FilterOperator.NOT) {
            return buildLogicalSpecification(criteria);
        }

        // Handle field-level operators
        return buildSingleFilter(criteria);
    }

    /**
     * Build specification for logical operators (AND, OR, NOT)
     */
    private Specification<Event> buildLogicalSpecification(FilterCriteria criteria) {
        List<FilterCriteria> children = criteria.getChildren();
        if (children == null || children.isEmpty()) {
            return null;
        }

        FilterOperator operator = criteria.getOperator();

        if (operator == FilterOperator.AND) {
            // Combine children with AND
            Specification<Event> spec = buildSpecificationFromCriteria(children.get(0));
            for (int i = 1; i < children.size(); i++) {
                Specification<Event> childSpec = buildSpecificationFromCriteria(children.get(i));
                if (childSpec != null) {
                    spec = spec == null ? childSpec : spec.and(childSpec);
                }
            }
            return spec;

        } else if (operator == FilterOperator.OR) {
            // Combine children with OR
            Specification<Event> spec = buildSpecificationFromCriteria(children.get(0));
            for (int i = 1; i < children.size(); i++) {
                Specification<Event> childSpec = buildSpecificationFromCriteria(children.get(i));
                if (childSpec != null) {
                    spec = spec == null ? childSpec : spec.or(childSpec);
                }
            }
            return spec;

        } else if (operator == FilterOperator.NOT) {
            // Negate first child
            Specification<Event> childSpec = buildSpecificationFromCriteria(children.get(0));
            return childSpec == null ? null : Specification.not(childSpec);
        }

        return null;
    }

    /**
     * Build JPA Specification for a single FilterCriteria
     * Converts FilterCriteria operators to JPA CriteriaBuilder predicates
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Specification<Event> buildSingleFilter(FilterCriteria filter) {
        String field = filter.getField();
        FilterOperator operator = filter.getOperator();
        Object value = filter.getValue();

        return (root, query, criteriaBuilder) -> {
            switch (operator) {
                case EQUALS:
                    return criteriaBuilder.equal(root.get(field), value);

                case NOT_EQUALS:
                    return criteriaBuilder.notEqual(root.get(field), value);

                case GREATER_THAN:
                    return criteriaBuilder.greaterThan(root.get(field),
                        (Comparable) parseComparableValue(value));

                case GREATER_THAN_OR_EQUAL:
                    return criteriaBuilder.greaterThanOrEqualTo(root.get(field),
                        (Comparable) parseComparableValue(value));

                case LESS_THAN:
                    return criteriaBuilder.lessThan(root.get(field),
                        (Comparable) parseComparableValue(value));

                case LESS_THAN_OR_EQUAL:
                    return criteriaBuilder.lessThanOrEqualTo(root.get(field),
                        (Comparable) parseComparableValue(value));

                case IN:
                    if (value instanceof List) {
                        return root.get(field).in((List<?>) value);
                    }
                    return root.get(field).in(value);

                case NOT_IN:
                    if (value instanceof List) {
                        return criteriaBuilder.not(root.get(field).in((List<?>) value));
                    }
                    return criteriaBuilder.not(root.get(field).in(value));

                case CONTAINS:
                    return criteriaBuilder.like(
                            criteriaBuilder.lower(root.get(field)),
                            "%" + value.toString().toLowerCase() + "%"
                    );

                case STARTS_WITH:
                    return criteriaBuilder.like(
                            criteriaBuilder.lower(root.get(field)),
                            value.toString().toLowerCase() + "%"
                    );

                case ENDS_WITH:
                    return criteriaBuilder.like(
                            criteriaBuilder.lower(root.get(field)),
                            "%" + value.toString().toLowerCase()
                    );

                default:
                    log.warn("Unsupported filter operator: {}", operator);
                    return criteriaBuilder.conjunction(); // Always true
            }
        };
    }

    /**
     * Parse value to Comparable, handling ISO 8601 date strings for Instant fields
     *
     * @param value The value to parse (String for dates, or already Comparable)
     * @return Comparable value ready for JPA comparison
     */
    private Object parseComparableValue(Object value) {
        if (value instanceof String) {
            // Handle ISO 8601 date strings for Instant fields
            return Instant.parse((String) value);
        }
        return value;
    }
}
