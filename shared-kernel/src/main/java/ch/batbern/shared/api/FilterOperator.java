package ch.batbern.shared.api;

/**
 * Filter operators supported by FilterParser.
 *
 * These operators are used to build filter criteria for querying data.
 * Based on MongoDB query syntax for familiarity and expressiveness.
 */
public enum FilterOperator {
    // Comparison operators
    EQUALS,                    // $eq or implicit
    NOT_EQUALS,                // $ne
    GREATER_THAN,              // $gt
    GREATER_THAN_OR_EQUAL,     // $gte
    LESS_THAN,                 // $lt
    LESS_THAN_OR_EQUAL,        // $lte

    // Logical operators
    AND,                       // $and
    OR,                        // $or
    NOT,                       // $not

    // String operators
    CONTAINS,                  // $contains
    STARTS_WITH,               // $startsWith
    ENDS_WITH,                 // $endsWith

    // Array operators
    IN,                        // $in
    NOT_IN,                    // $nin
    SIZE,                      // $size

    // Null check
    IS_NULL                    // $isNull
}
