package ch.batbern.shared.api;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a filter criterion for query building.
 *
 * A FilterCriteria can be:
 * - A leaf node: field + operator + value (e.g., status = "published")
 * - A composite node: logical operator + children (e.g., AND [criterion1, criterion2])
 *
 * This structure allows building complex nested filter expressions.
 *
 * Examples:
 * - Simple: {field: "status", operator: EQUALS, value: "published"}
 * - Complex: {operator: AND, children: [{field: "status", ...}, {field: "votes", ...}]}
 */
@Data
@Builder
public class FilterCriteria {
    /**
     * Field name for leaf criteria (e.g., "status", "votes", "author.name").
     * Null for composite criteria (AND, OR, NOT).
     */
    private String field;

    /**
     * Filter operator (EQUALS, GREATER_THAN, AND, OR, etc.)
     */
    private FilterOperator operator;

    /**
     * Value to compare against for leaf criteria.
     * Can be String, Number, Boolean, List, or null.
     */
    private Object value;

    /**
     * Child criteria for composite operators (AND, OR, NOT).
     * Empty for leaf criteria.
     */
    @Builder.Default
    private List<FilterCriteria> children = new ArrayList<>();

    /**
     * Checks if this is a composite criterion (has children).
     */
    public boolean isComposite() {
        return !children.isEmpty();
    }

    /**
     * Checks if this is a leaf criterion (has field and value).
     */
    public boolean isLeaf() {
        return field != null;
    }
}
