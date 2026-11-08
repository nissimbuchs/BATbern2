package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.api.DeterministicPaging;
import ch.batbern.shared.api.FilterCriteria;
import ch.batbern.shared.api.FilterOperator;
import ch.batbern.shared.api.FilterParser;
import ch.batbern.shared.api.PaginationMetadata;
import ch.batbern.shared.api.PaginationParams;
import ch.batbern.shared.api.PaginationUtils;
import ch.batbern.shared.api.SortCriteria;
import ch.batbern.shared.api.SortDirection;
import ch.batbern.shared.api.SortParser;
import ch.batbern.shared.dto.PaginatedResponse;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.shared.search.SearchPredicates;
import ch.batbern.shared.search.SearchTerms;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
     * @param includeArchived Include archived events (default: false)
     * @return Paginated event results
     */
    public PaginatedResponse<Event> searchEvents(
            String filterJson, String sortStr, Integer page, Integer limit, boolean includeArchived) {
        // Parse pagination parameters
        PaginationParams paginationParams = PaginationUtils.parseParams(page, limit);
        int pageNum = paginationParams.getPage();
        int pageSize = paginationParams.getLimit();

        // Parse sort parameters
        List<SortCriteria> sortCriteria = parseSortCriteria(sortStr);
        Sort sort = buildSort(sortCriteria);

        // Parse filter parameters
        Specification<Event> spec = buildFilterSpecification(filterJson);

        // Exclude ARCHIVED events if includeArchived is false
        if (!includeArchived) {
            Specification<Event> notArchivedSpec = (root, query, criteriaBuilder) ->
                criteriaBuilder.notEqual(
                    root.get("workflowState"),
                    ch.batbern.shared.types.EventWorkflowState.ARCHIVED);
            spec = spec == null ? notArchivedSpec : spec.and(notArchivedSpec);
        }

        // Create pageable
        // Convert to 0-indexed; id breaks ties of the requested sort (#1131)
        Pageable pageable = DeterministicPaging.pageRequest(pageNum - 1, pageSize, sort);

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
     * Upcoming events the public website may list: a published phase (topic, speakers, agenda),
     * not archived, and not before today (Bern), nearest first. The selection is fixed here so a
     * public caller can never widen it to unpublished drafts (Public Events, 2026-10-07).
     */
    public PaginatedResponse<Event> searchUpcomingPublicEvents(Integer page, Integer limit) {
        PaginationParams paginationParams = PaginationUtils.parseParams(page, limit);
        int pageNum = paginationParams.getPage();
        int pageSize = paginationParams.getLimit();

        ZoneId bern = ZoneId.of("Europe/Zurich");
        Instant startOfToday = LocalDate.now(bern).atStartOfDay(bern).toInstant();
        Specification<Event> spec = (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("date"), startOfToday),
                cb.notEqual(root.get("workflowState"), EventWorkflowState.ARCHIVED),
                cb.lower(root.get("currentPublishedPhase")).in(PUBLISHED_PHASES));

        Page<Event> eventPage = eventRepository.findAll(
                spec, DeterministicPaging.pageRequest(pageNum - 1, pageSize, Sort.by(Sort.Direction.ASC, "date")));
        return PaginatedResponse.<Event>builder()
                .data(eventPage.getContent())
                .pagination(PaginationUtils.generateMetadata(pageNum, pageSize, eventPage.getTotalElements()))
                .build();
    }

    private static final List<String> PUBLISHED_PHASES = List.of("topic", "speakers", "agenda");

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
     * Maps logical field names to database column names (Story BAT-109)
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

            // Map logical field names to actual database column names
            String mappedField = mapSortField(criteria.getField());
            orders.add(new Sort.Order(direction, mappedField));
        }

        return Sort.by(orders);
    }

    /**
     * Map logical sort field names to database column names
     * Story BAT-109: Archive browsing sort requirements
     */
    private String mapSortField(String logicalField) {
        return switch (logicalField) {
            case "attendeeCount" -> "currentAttendeeCount";
            default -> logicalField; // Use as-is for standard fields (date, eventNumber, etc.)
        };
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

        // Special handling for virtual 'year' field - extract year from date column
        if ("year".equals(field) && operator == FilterOperator.EQUALS) {
            return (root, query, criteriaBuilder) -> {
                Integer year = ((Number) value).intValue();
                // Use Hibernate 6's year() method via HibernateCriteriaBuilder
                org.hibernate.query.criteria.HibernateCriteriaBuilder hcb =
                    (org.hibernate.query.criteria.HibernateCriteriaBuilder) criteriaBuilder;
                Expression<Integer> yearExpression = hcb.year(root.get("date"));
                return criteriaBuilder.equal(yearExpression, year);
            };
        }

        return (root, query, criteriaBuilder) -> {
            // Convert value to enum if the field is an enum type
            Object convertedValue = convertValueForField(root, field, value);

            switch (operator) {
                case EQUALS:
                    return criteriaBuilder.equal(root.get(field), convertedValue);

                case NOT_EQUALS:
                    return criteriaBuilder.notEqual(root.get(field), convertedValue);

                case GREATER_THAN:
                    return criteriaBuilder.greaterThan(root.get(field),
                        (Comparable) parseComparableValue(convertedValue));

                case GREATER_THAN_OR_EQUAL:
                    return criteriaBuilder.greaterThanOrEqualTo(root.get(field),
                        (Comparable) parseComparableValue(convertedValue));

                case LESS_THAN:
                    return criteriaBuilder.lessThan(root.get(field),
                        (Comparable) parseComparableValue(convertedValue));

                case LESS_THAN_OR_EQUAL:
                    return criteriaBuilder.lessThanOrEqualTo(root.get(field),
                        (Comparable) parseComparableValue(convertedValue));

                case IN:
                    if (value instanceof List) {
                        List<?> listValue = (List<?>) value;
                        List<Object> convertedList = new ArrayList<>();
                        for (Object item : listValue) {
                            convertedList.add(convertValueForField(root, field, item));
                        }
                        return root.get(field).in(convertedList);
                    }
                    return root.get(field).in(convertedValue);

                case NOT_IN:
                    if (value instanceof List) {
                        List<?> listValue = (List<?>) value;
                        List<Object> convertedList = new ArrayList<>();
                        for (Object item : listValue) {
                            convertedList.add(convertValueForField(root, field, item));
                        }
                        return criteriaBuilder.not(root.get(field).in(convertedList));
                    }
                    return criteriaBuilder.not(root.get(field).in(convertedValue));

                case CONTAINS:
                    // "Title contains" is the event and archive search box. Every word
                    // must occur in the event title, description or code, a session title or
                    // abstract, or a speaker name, folded by batbern_fold. Substring, any order,
                    // never a parse error (the former to_tsquery on raw input threw on "(test").
                    if ("title".equals(field)) {
                        return buildSearchPredicate(root, query, criteriaBuilder, value);
                    }
                    return criteriaBuilder.like(criteriaBuilder.lower(root.get(field)),
                            "%" + likeValue(value) + "%", SearchTerms.ESCAPE);

                case STARTS_WITH:
                    return criteriaBuilder.like(criteriaBuilder.lower(root.get(field)),
                            likeValue(value) + "%", SearchTerms.ESCAPE);

                case ENDS_WITH:
                    return criteriaBuilder.like(criteriaBuilder.lower(root.get(field)),
                            "%" + likeValue(value), SearchTerms.ESCAPE);

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

    /**
     * Convert filter value to match the field type
     * Handles enum conversions when filtering by enum fields
     *
     * @param root JPA Root for accessing field metadata
     * @param field Field name
     * @param value Filter value (may be String representation of enum)
     * @return Converted value matching the field type
     */
    @SuppressWarnings("rawtypes")
    private Object convertValueForField(jakarta.persistence.criteria.Root<Event> root, String field, Object value) {
        if (value == null) {
            return null;
        }

        try {
            // Get the Java type of the field
            Class<?> fieldType = root.get(field).getJavaType();

            // If field is an enum and value is a String, convert to enum
            if (fieldType.isEnum() && value instanceof String) {
                @SuppressWarnings("unchecked")
                Class<? extends Enum> enumType = (Class<? extends Enum>) fieldType;
                return Enum.valueOf(enumType, (String) value);
            }

            return value;
        } catch (IllegalArgumentException e) {
            log.warn("Failed to convert value '{}' for field '{}': {}", value, field, e.getMessage());
            return value;
        }
    }

    /** Lower-cased, LIKE-escaped filter value: {@code %}, {@code _} and {@code \} match literally. */
    private static String likeValue(Object value) {
        return SearchTerms.escapeLike(value.toString().toLowerCase());
    }

    /**
     * The event search: every word of {@code value} occurs in the event title,
     * description or event code ("56" and "BATbern56" find BATbern56), in the title or abstract of
     * one of its sessions, or in the first or last name of one of its speakers, folded by
     * {@code batbern_fold} on both sides. A blank value matches every event. Results keep the
     * caller's explicit sort (date by default); substring matches are a superset of the former
     * whole-word full-text matches, so nothing that was found before is lost.
     */
    private Predicate buildSearchPredicate(Root<Event> root, CriteriaQuery<?> query, CriteriaBuilder cb,
            Object value) {
        SearchTerms terms = SearchTerms.parse(value != null ? value.toString() : null);
        return SearchPredicates.allTerms(cb, terms, term -> cb.or(
                SearchPredicates.contains(cb, root.get("title"), term),
                SearchPredicates.contains(cb, root.get("description"), term),
                SearchPredicates.contains(cb, root.get("eventCode"), term),
                cb.exists(sessionMatches(root, query, cb, term)),
                cb.exists(speakerMatches(root, query, cb, term))));
    }

    private static Subquery<Integer> sessionMatches(Root<Event> root, CriteriaQuery<?> query, CriteriaBuilder cb,
            String term) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<Session> session = sub.from(Session.class);
        return sub.select(cb.literal(1)).where(
                cb.equal(session.get("eventId"), root.get("id")),
                cb.or(SearchPredicates.contains(cb, session.get("title"), term),
                        SearchPredicates.contains(cb, session.get("description"), term)));
    }

    private static Subquery<Integer> speakerMatches(Root<Event> root, CriteriaQuery<?> query, CriteriaBuilder cb,
            String term) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<SessionUser> speaker = sub.from(SessionUser.class);
        Join<SessionUser, Session> session = speaker.join("session");
        return sub.select(cb.literal(1)).where(
                cb.equal(session.get("eventId"), root.get("id")),
                cb.or(SearchPredicates.contains(cb, speaker.get("speakerFirstName"), term),
                        SearchPredicates.contains(cb, speaker.get("speakerLastName"), term)));
    }
}
