package ch.batbern.shared.api;

import ch.batbern.shared.exception.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Parses MongoDB-style JSON filter syntax into FilterCriteria objects.
 *
 * <p><strong>SECURITY: SQL Injection Protection</strong></p>
 * <p>This parser generates FilterCriteria objects from user input. To prevent SQL injection attacks,
 * FilterCriteria <strong>MUST</strong> be converted to database queries using one of these safe methods:</p>
 *
 * <ol>
 *   <li><strong>JPA Specifications (Recommended):</strong>
 *   <pre>{@code
 *   // Safe: Uses JPA Criteria API with parameterized queries
 *   Specification<Event> spec = (root, query, cb) -> {
 *       if (criteria.getOperator() == FilterOperator.EQUALS) {
 *           return cb.equal(root.get(criteria.getField()), criteria.getValue());
 *       }
 *       // ... handle other operators
 *   };
 *   repository.findAll(spec, pageable);
 *   }</pre>
 *   </li>
 *
 *   <li><strong>Parameterized JDBC Queries:</strong>
 *   <pre>{@code
 *   // Safe: Uses PreparedStatement with bind parameters
 *   String sql = "SELECT * FROM events WHERE status = ?";
 *   PreparedStatement stmt = conn.prepareStatement(sql);
 *   stmt.setString(1, criteria.getValue().toString());
 *   }</pre>
 *   </li>
 *
 *   <li><strong>JPA Query Builder:</strong>
 *   <pre>{@code
 *   // Safe: Uses CriteriaBuilder with type-safe queries
 *   CriteriaBuilder cb = em.getCriteriaBuilder();
 *   CriteriaQuery<Event> cq = cb.createQuery(Event.class);
 *   Root<Event> root = cq.from(Event.class);
 *   cq.where(cb.equal(root.get("status"), criteria.getValue()));
 *   }</pre>
 *   </li>
 * </ol>
 *
 * <p><strong>DANGEROUS - Never Do This:</strong></p>
 * <pre>{@code
 * // UNSAFE: String concatenation creates SQL injection vulnerability!
 * String sql = "SELECT * FROM events WHERE " + field + " = '" + value + "'";
 * // Attacker could provide: field="status' OR '1'='1" to bypass filters
 * }</pre>
 *
 * <p><strong>Additional Security Best Practices:</strong></p>
 * <ul>
 *   <li>Validate field names against an allowed whitelist before querying</li>
 *   <li>Use database column permissions to restrict access</li>
 *   <li>Implement query complexity limits (see recommendations in code review)</li>
 *   <li>Log and monitor suspicious filter patterns</li>
 * </ul>
 *
 * <p><strong>Supported Operators:</strong></p>
 * <ul>
 *   <li>Comparison: $eq, $ne, $gt, $gte, $lt, $lte</li>
 *   <li>Logical: $and, $or, $not</li>
 *   <li>String: $contains, $startsWith, $endsWith</li>
 *   <li>Array: $in, $nin, $size</li>
 *   <li>Null: $isNull</li>
 * </ul>
 *
 * <p><strong>Examples:</strong></p>
 * <pre>
 * - Simple equality: {"status":"published"}
 * - Comparison: {"votes":{"$gte":10}}
 * - Logical AND: {"$and":[{"status":"published"},{"votes":{"$gte":10}}]}
 * - Nested fields: {"author.name":"John Doe"}
 * </pre>
 *
 * @see FilterCriteria
 * @see FilterOperator
 */
@Slf4j
public class FilterParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Operator mappings
    private static final Map<String, FilterOperator> OPERATOR_MAP = Map.ofEntries(
            // Comparison operators
            Map.entry("$eq", FilterOperator.EQUALS),
            Map.entry("$ne", FilterOperator.NOT_EQUALS),
            Map.entry("$gt", FilterOperator.GREATER_THAN),
            Map.entry("$gte", FilterOperator.GREATER_THAN_OR_EQUAL),
            Map.entry("$lt", FilterOperator.LESS_THAN),
            Map.entry("$lte", FilterOperator.LESS_THAN_OR_EQUAL),

            // Logical operators
            Map.entry("$and", FilterOperator.AND),
            Map.entry("$or", FilterOperator.OR),
            Map.entry("$not", FilterOperator.NOT),

            // String operators
            Map.entry("$contains", FilterOperator.CONTAINS),
            Map.entry("$startsWith", FilterOperator.STARTS_WITH),
            Map.entry("$endsWith", FilterOperator.ENDS_WITH),

            // Array operators
            Map.entry("$in", FilterOperator.IN),
            Map.entry("$nin", FilterOperator.NOT_IN),
            Map.entry("$size", FilterOperator.SIZE),

            // Null check
            Map.entry("$isNull", FilterOperator.IS_NULL)
    );

    /**
     * Parses filter JSON string into FilterCriteria.
     *
     * @param filterJson JSON filter string (MongoDB-style syntax)
     * @return FilterCriteria object, or null if filter is null/empty
     * @throws ValidationException if JSON is invalid or contains unknown operators
     */
    public static FilterCriteria parse(String filterJson) {
        // Handle null or empty filter
        if (filterJson == null || filterJson.trim().isEmpty()) {
            return null;
        }

        try {
            // Parse JSON string to JsonNode
            JsonNode rootNode = objectMapper.readTree(filterJson);

            // Empty object check
            if (rootNode.isEmpty()) {
                throw new ValidationException("Filter cannot be empty");
            }

            // Parse the JSON node into FilterCriteria
            return parseNode(rootNode);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse filter JSON: {}", filterJson, e);
            throw new ValidationException("Invalid filter JSON: " + e.getMessage());
        }
    }

    /**
     * Recursively parses a JsonNode into FilterCriteria.
     */
    private static FilterCriteria parseNode(JsonNode node) {
        if (node.isObject()) {
            return parseObject(node);
        } else {
            throw new ValidationException("Invalid filter structure: expected object");
        }
    }

    /**
     * Parses a JSON object node.
     * Can be:
     * - Logical operator: {"$and": [...]}
     * - Field with operator: {"votes": {"$gte": 10}}
     * - Simple equality: {"status": "published"}
     * - Multiple fields (implicit AND): {"status": "published", "category": "tech"}
     */
    private static FilterCriteria parseObject(JsonNode node) {
        List<FilterCriteria> criteria = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = entry.getKey();
            JsonNode fieldValue = entry.getValue();

            // Check if it's a logical operator
            if (isLogicalOperator(fieldName)) {
                return parseLogicalOperator(fieldName, fieldValue);
            }

            // Otherwise, it's a field criterion
            FilterCriteria criterion = parseFieldCriterion(fieldName, fieldValue);
            criteria.add(criterion);
        }

        // If multiple criteria, combine with implicit AND
        if (criteria.size() > 1) {
            return FilterCriteria.builder()
                    .operator(FilterOperator.AND)
                    .children(criteria)
                    .build();
        } else if (criteria.size() == 1) {
            return criteria.get(0);
        } else {
            throw new ValidationException("Filter cannot be empty");
        }
    }

    /**
     * Parses a field criterion.
     * Can be:
     * - Simple value: {"status": "published"} -> EQUALS
     * - Operator object: {"votes": {"$gte": 10}} -> GREATER_THAN_OR_EQUAL
     */
    private static FilterCriteria parseFieldCriterion(String fieldName, JsonNode fieldValue) {
        // If value is an object, it contains operators
        if (fieldValue.isObject()) {
            return parseFieldWithOperators(fieldName, fieldValue);
        }

        // Otherwise, it's a simple equality
        return FilterCriteria.builder()
                .field(fieldName)
                .operator(FilterOperator.EQUALS)
                .value(extractValue(fieldValue))
                .build();
    }

    /**
     * Parses a field with explicit operators.
     * Example: {"votes": {"$gte": 10}}
     */
    private static FilterCriteria parseFieldWithOperators(String fieldName, JsonNode operatorNode) {
        // Get the first (and should be only) operator
        Iterator<Map.Entry<String, JsonNode>> fields = operatorNode.fields();

        if (!fields.hasNext()) {
            throw new ValidationException("Empty operator object for field: " + fieldName);
        }

        Map.Entry<String, JsonNode> operatorEntry = fields.next();
        String operatorStr = operatorEntry.getKey();
        JsonNode operatorValue = operatorEntry.getValue();

        // Map operator string to enum
        FilterOperator operator = OPERATOR_MAP.get(operatorStr);
        if (operator == null) {
            throw new ValidationException("Unknown operator: " + operatorStr);
        }

        // Extract value (handle array for IN/NIN operators)
        Object value;
        if (operator == FilterOperator.IN || operator == FilterOperator.NOT_IN) {
            value = extractArrayValue(operatorValue);
        } else {
            value = extractValue(operatorValue);
        }

        return FilterCriteria.builder()
                .field(fieldName)
                .operator(operator)
                .value(value)
                .build();
    }

    /**
     * Parses logical operators ($and, $or, $not).
     */
    private static FilterCriteria parseLogicalOperator(String operatorStr, JsonNode valueNode) {
        FilterOperator operator = OPERATOR_MAP.get(operatorStr);

        if (operator == FilterOperator.AND || operator == FilterOperator.OR) {
            // $and and $or expect an array of conditions
            if (!valueNode.isArray()) {
                throw new ValidationException(operatorStr + " operator requires an array of conditions");
            }

            List<FilterCriteria> children = new ArrayList<>();
            for (JsonNode childNode : valueNode) {
                children.add(parseNode(childNode));
            }

            if (children.isEmpty()) {
                throw new ValidationException(operatorStr + " operator requires at least one condition");
            }

            return FilterCriteria.builder()
                    .operator(operator)
                    .children(children)
                    .build();

        } else if (operator == FilterOperator.NOT) {
            // $not expects a single condition (object)
            if (!valueNode.isObject()) {
                throw new ValidationException("$not operator requires an object");
            }

            FilterCriteria child = parseNode(valueNode);

            return FilterCriteria.builder()
                    .operator(operator)
                    .children(List.of(child))
                    .build();
        }

        throw new ValidationException("Unknown logical operator: " + operatorStr);
    }

    /**
     * Checks if a field name is a logical operator.
     */
    private static boolean isLogicalOperator(String fieldName) {
        return fieldName.equals("$and") || fieldName.equals("$or") || fieldName.equals("$not");
    }

    /**
     * Extracts a value from a JsonNode.
     * Handles: String, Number, Boolean, null.
     */
    private static Object extractValue(JsonNode node) {
        if (node.isNull()) {
            return null;
        } else if (node.isTextual()) {
            return node.asText();
        } else if (node.isNumber()) {
            // Preserve number type (int vs long vs double)
            if (node.isInt()) {
                return node.asInt();
            } else if (node.isLong()) {
                return node.asLong();
            } else {
                return node.asDouble();
            }
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else {
            throw new ValidationException("Unsupported value type: " + node.getNodeType());
        }
    }

    /**
     * Extracts an array value from a JsonNode.
     * Used for IN and NIN operators.
     */
    private static List<Object> extractArrayValue(JsonNode node) {
        if (!node.isArray()) {
            throw new ValidationException("Expected array value for IN/NIN operator");
        }

        List<Object> values = new ArrayList<>();
        for (JsonNode element : node) {
            values.add(extractValue(element));
        }

        return values;
    }
}
