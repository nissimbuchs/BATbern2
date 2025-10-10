package ch.batbern.gateway.controller;

import ch.batbern.shared.api.FieldSelector;
import ch.batbern.shared.api.FilterCriteria;
import ch.batbern.shared.api.FilterOperator;
import ch.batbern.shared.api.FilterParser;
import ch.batbern.shared.api.IncludeParser;
import ch.batbern.shared.api.PaginationParams;
import ch.batbern.shared.api.PaginationUtils;
import ch.batbern.shared.api.SortCriteria;
import ch.batbern.shared.api.SortDirection;
import ch.batbern.shared.api.SortParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test controller for API Consolidation Foundation testing.
 * Demonstrates usage of query parameter utilities and middleware.
 *
 * This controller is ONLY used in tests to verify the API consolidation infrastructure.
 * It uses actual shared-kernel utilities to provide realistic testing.
 */
@RestController
@RequestMapping("/api/v1/test-resources")
@Tag(name = "Test Resources", description = "Test endpoints demonstrating API consolidation features")
public class TestResourceController {

    // In-memory test data
    private static final List<TestResource> TEST_DATA = createTestData();

    /**
     * GET endpoint demonstrating all query parameters:
     * - filter: JSON filter syntax
     * - sort: Sort specification
     * - page/limit: Pagination
     * - fields: Field selection
     * - include: Resource expansion
     */
    @GetMapping
    @Operation(
            summary = "Get test resources with advanced filtering",
            description = """
                    Retrieves a paginated list of test resources with support for:
                    - JSON-based filtering with comparison and logical operators
                    - Multi-field sorting (ascending/descending)
                    - Pagination with configurable page size
                    - Sparse fieldsets for reduced payload size
                    - Resource expansion to include related entities
                    """
    )
    @ApiResponses(value = {
        @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved resources",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "data": [
                                    {"id": "1", "title": "First Post", "status": "published", "votes": 15},
                                    {"id": "2", "title": "Second Post", "status": "published", "votes": 25}
                                  ],
                                  "pagination": {
                                    "page": 1,
                                    "limit": 20,
                                    "total": 45,
                                    "totalPages": 3,
                                    "hasNext": true,
                                    "hasPrev": false
                                  }
                                }
                                """)
                )
            ),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid query parameters",
                content = @Content(mediaType = "application/json")
            )
    })
    public ResponseEntity<List<Map<String, Object>>> getAll(
            @Parameter(
                description = "JSON filter expression. Supports: $eq, $ne, $gt, $gte, $lt, "
                    + "$lte, $and, $or, $not, $contains, $in",
                example = "{\"$and\":[{\"status\":\"published\"},{\"votes\":{\"$gte\":10}}]}"
            )
            @RequestParam(required = false) String filter,

            @Parameter(
                description = "Sort specification. Use +/- prefix for ascending/descending. "
                    + "Multiple fields separated by comma.",
                example = "-votes,+createdAt"
            )
            @RequestParam(required = false) String sort,

            @Parameter(
                    description = "Page number (1-indexed)",
                    example = "1",
                    schema = @Schema(minimum = "1", defaultValue = "1")
            )
            @RequestParam(required = false) Integer page,

            @Parameter(
                    description = "Items per page",
                    example = "20",
                    schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")
            )
            @RequestParam(required = false) Integer limit,

            @Parameter(
                    description = "Comma-separated list of fields to return (sparse fieldsets)",
                    example = "id,title,status"
            )
            @RequestParam(required = false) String fields,

            @Parameter(
                    description = "Comma-separated list of relations to expand",
                    example = "author,comments"
            )
            @RequestParam(required = false) String include,

            HttpServletRequest request
    ) {
        // Use actual shared-kernel utilities
        List<TestResource> filteredData = TEST_DATA;

        // AC2: Apply filter using FilterParser from shared-kernel
        if (filter != null && !filter.isEmpty()) {
            FilterCriteria criteria = FilterParser.parse(filter);
            filteredData = applyFilterCriteria(filteredData, criteria);
        }

        // AC3: Apply sort using SortParser from shared-kernel
        if (sort != null && !sort.isEmpty()) {
            List<SortCriteria> sortCriteria = SortParser.parse(sort);
            filteredData = applySortCriteria(filteredData, sortCriteria);
        }

        // AC4: Parse pagination parameters using PaginationUtils
        PaginationParams paginationParams = PaginationUtils.parseParams(page, limit);

        // Apply pagination
        long total = filteredData.size();
        int startIndex = paginationParams.getOffset();
        int endIndex = Math.min(startIndex + paginationParams.getLimit(), filteredData.size());

        List<TestResource> paginatedData = startIndex < filteredData.size()
                ? filteredData.subList(startIndex, endIndex)
                : Collections.emptyList();

        // AC5: Apply field selection using FieldSelector
        List<Map<String, Object>> selectedFields;
        if (fields != null && !fields.isEmpty()) {
            Set<String> fieldSet = FieldSelector.parse(fields);
            selectedFields = applyFieldSelection(paginatedData, fieldSet);
        } else {
            // Convert TestResource to Map for consistent response format
            selectedFields = paginatedData.stream()
                    .map(this::toMap)
                    .collect(Collectors.toList());
        }

        // AC6: Apply include/expand using IncludeParser
        if (include != null && !include.isEmpty()) {
            Set<String> includeSet = IncludeParser.parse(include);
            selectedFields = applyInclude(selectedFields, includeSet);
        }

        // AC9: Set totalCount attribute for ResponseFormattingMiddleware
        request.setAttribute("totalCount", total);

        // AC9: Return List - ResponseFormattingMiddleware will wrap with pagination metadata
        return ResponseEntity.ok(selectedFields);
    }

    // ========================================
    // Helper Methods Using Shared-Kernel Utilities
    // ========================================

    /**
     * Applies filter criteria to test data.
     * In a real application, this would be handled by database queries.
     */
    private List<TestResource> applyFilterCriteria(List<TestResource> data, FilterCriteria criteria) {
        if (criteria == null) {
            return data;
        }

        return data.stream()
                .filter(resource -> matchesCriteria(resource, criteria))
                .collect(Collectors.toList());
    }

    /**
     * Checks if a resource matches the filter criteria.
     */
    private boolean matchesCriteria(TestResource resource, FilterCriteria criteria) {
        FilterOperator operator = criteria.getOperator();

        // Logical operators
        if (operator == FilterOperator.AND) {
            return criteria.getChildren().stream()
                    .allMatch(child -> matchesCriteria(resource, child));
        } else if (operator == FilterOperator.OR) {
            return criteria.getChildren().stream()
                    .anyMatch(child -> matchesCriteria(resource, child));
        } else if (operator == FilterOperator.NOT) {
            return !matchesCriteria(resource, criteria.getChildren().get(0));
        }

        // Field-based operators
        String field = criteria.getField();
        Object value = criteria.getValue();
        Object fieldValue = getFieldValue(resource, field);

        return switch (operator) {
            case EQUALS -> Objects.equals(fieldValue, value);
            case NOT_EQUALS -> !Objects.equals(fieldValue, value);
            case GREATER_THAN -> compareValues(fieldValue, value) > 0;
            case GREATER_THAN_OR_EQUAL -> compareValues(fieldValue, value) >= 0;
            case LESS_THAN -> compareValues(fieldValue, value) < 0;
            case LESS_THAN_OR_EQUAL -> compareValues(fieldValue, value) <= 0;
            case CONTAINS -> fieldValue != null && fieldValue.toString().contains(value.toString());
            case STARTS_WITH -> fieldValue != null && fieldValue.toString().startsWith(value.toString());
            case ENDS_WITH -> fieldValue != null && fieldValue.toString().endsWith(value.toString());
            case IN -> value instanceof List && ((List<?>) value).contains(fieldValue);
            case NOT_IN -> value instanceof List && !((List<?>) value).contains(fieldValue);
            case IS_NULL -> value != null && (Boolean) value ? fieldValue == null : fieldValue != null;
            default -> true;
        };
    }

    private Object getFieldValue(TestResource resource, String field) {
        return switch (field) {
            case "id" -> resource.id;
            case "title" -> resource.title;
            case "status" -> resource.status;
            case "votes" -> resource.votes;
            case "createdAt" -> resource.createdAt;
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private int compareValues(Object a, Object b) {
        if (a instanceof Comparable && b instanceof Comparable) {
            return ((Comparable<Object>) a).compareTo(b);
        }
        return 0;
    }

    /**
     * Applies sort criteria to test data.
     */
    private List<TestResource> applySortCriteria(List<TestResource> data, List<SortCriteria> sortCriteria) {
        List<TestResource> sorted = new ArrayList<>(data);

        // Build a composite comparator
        Comparator<TestResource> comparator = null;
        for (SortCriteria criteria : sortCriteria) {
            Comparator<TestResource> fieldComparator = createFieldComparator(criteria);
            comparator = comparator == null ? fieldComparator : comparator.thenComparing(fieldComparator);
        }

        if (comparator != null) {
            sorted.sort(comparator);
        }

        return sorted;
    }

    @SuppressWarnings("unchecked")
    private Comparator<TestResource> createFieldComparator(SortCriteria criteria) {
        String field = criteria.getField();
        Comparator<TestResource> comparator = (r1, r2) -> {
            Object v1 = getFieldValue(r1, field);
            Object v2 = getFieldValue(r2, field);

            if (v1 == null && v2 == null) {
                return 0;
            }
            if (v1 == null) {
                return 1;
            }
            if (v2 == null) {
                return -1;
            }

            if (v1 instanceof Comparable && v2 instanceof Comparable) {
                return ((Comparable<Object>) v1).compareTo(v2);
            }
            return 0;
        };

        // Apply direction
        return criteria.getDirection() == SortDirection.DESC ? comparator.reversed() : comparator;
    }

    /**
     * Applies field selection to data.
     */
    private List<Map<String, Object>> applyFieldSelection(List<TestResource> data, Set<String> fields) {
        return data.stream()
                .map(r -> {
                    Map<String, Object> selected = new LinkedHashMap<>();
                    if (fields.contains("id")) {
                        selected.put("id", r.id);
                    }
                    if (fields.contains("title")) {
                        selected.put("title", r.title);
                    }
                    if (fields.contains("status")) {
                        selected.put("status", r.status);
                    }
                    if (fields.contains("votes")) {
                        selected.put("votes", r.votes);
                    }
                    if (fields.contains("createdAt")) {
                        selected.put("createdAt", r.createdAt);
                    }
                    return selected;
                })
                .collect(Collectors.toList());
    }

    /**
     * Applies include/expand to data (adds related resources).
     */
    private List<Map<String, Object>> applyInclude(List<Map<String, Object>> data, Set<String> includes) {
        return data.stream()
                .map(item -> {
                    Map<String, Object> expanded = new LinkedHashMap<>(item);
                    if (includes.contains("author")) {
                        expanded.put("author", Map.of("id", "author-123", "name", "Test Author"));
                    }
                    if (includes.contains("comments")) {
                        expanded.put("comments", List.of(
                                Map.of("id", "comment-1", "text", "Great post!")
                        ));
                    }
                    return expanded;
                })
                .collect(Collectors.toList());
    }

    /**
     * Converts TestResource to Map (all fields).
     */
    private Map<String, Object> toMap(TestResource r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", r.id);
        map.put("title", r.title);
        map.put("status", r.status);
        map.put("votes", r.votes);
        map.put("createdAt", r.createdAt);
        return map;
    }

    // ========================================
    // Test Data
    // ========================================

    private static List<TestResource> createTestData() {
        List<TestResource> data = new ArrayList<>();
        data.add(new TestResource("1", "First Post", "published", 15, Instant.parse("2024-01-01T10:00:00Z")));
        data.add(new TestResource("2", "Second Post", "published", 25, Instant.parse("2024-01-02T10:00:00Z")));
        data.add(new TestResource("3", "Draft Post", "draft", 5, Instant.parse("2024-01-03T10:00:00Z")));
        data.add(new TestResource("4", "Popular Post", "published", 100, Instant.parse("2024-01-04T10:00:00Z")));
        data.add(new TestResource("5", "Another Draft", "draft", 2, Instant.parse("2024-01-05T10:00:00Z")));
        data.add(new TestResource("6", "Trending Post", "published", 50, Instant.parse("2024-01-06T10:00:00Z")));
        data.add(new TestResource("7", "Old Post", "published", 8, Instant.parse("2024-01-07T10:00:00Z")));
        data.add(new TestResource("8", "Recent Post", "published", 12, Instant.parse("2024-01-08T10:00:00Z")));
        return data;
    }

    /**
     * Test resource model
     */
    public static class TestResource {
        public String id;
        public String title;
        public String status;
        public int votes;
        public Instant createdAt;

        public TestResource(String id, String title, String status, int votes, Instant createdAt) {
            this.id = id;
            this.title = title;
            this.status = status;
            this.votes = votes;
            this.createdAt = createdAt;
        }

        // Public fields for easy JSON serialization in tests
    }
}
