//
// OpenAPIUtilities.swift
// BATbern-watch Watch App
//

import Foundation

public protocol JSONEncodable {
    func encodeToJSON() throws -> Any
}

extension JSONEncodable where Self: Encodable {
    public func encodeToJSON() throws -> Any {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        let data = try encoder.encode(self)
        return try JSONSerialization.jsonObject(with: data, options: [])
    }
}

public struct StringRule {
    public let minLength: Int?
    public let maxLength: Int?
    public let pattern: String?

    public init(minLength: Int? = nil, maxLength: Int? = nil, pattern: String? = nil) {
        self.minLength = minLength
        self.maxLength = maxLength
        self.pattern = pattern
    }
}

public struct NumericRule<T: Numeric> {
    public let minimum: T?
    public let exclusiveMinimum: Bool
    public let maximum: T?
    public let exclusiveMaximum: Bool
    public let multipleOf: T?

    public init(minimum: T? = nil, exclusiveMinimum: Bool = false, maximum: T? = nil, exclusiveMaximum: Bool = false, multipleOf: T? = nil) {
        self.minimum = minimum
        self.exclusiveMinimum = exclusiveMinimum
        self.maximum = maximum
        self.exclusiveMaximum = exclusiveMaximum
        self.multipleOf = multipleOf
    }
}

public struct ArrayRule {
    public let minItems: Int?
    public let maxItems: Int?
    public let uniqueItems: Bool

    public init(minItems: Int? = nil, maxItems: Int? = nil, uniqueItems: Bool = false) {
        self.minItems = minItems
        self.maxItems = maxItems
        self.uniqueItems = uniqueItems
    }
}

public struct AnyCodable: Codable, Hashable, Equatable {
    public let value: Any

    public init(_ value: Any) {
        self.value = value
    }

    // Hashable conformance
    public func hash(into hasher: inout Hasher) {
        switch value {
        case let value as Bool:
            hasher.combine(value)
        case let value as Int:
            hasher.combine(value)
        case let value as Double:
            hasher.combine(value)
        case let value as String:
            hasher.combine(value)
        default:
            // For complex types, use a constant hash to avoid crashes
            hasher.combine(0)
        }
    }

    // Equatable conformance
    public static func == (lhs: AnyCodable, rhs: AnyCodable) -> Bool {
        switch (lhs.value, rhs.value) {
        case (let lhs as Bool, let rhs as Bool):
            return lhs == rhs
        case (let lhs as Int, let rhs as Int):
            return lhs == rhs
        case (let lhs as Double, let rhs as Double):
            return lhs == rhs
        case (let lhs as String, let rhs as String):
            return lhs == rhs
        default:
            // For complex types, consider them not equal
            return false
        }
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()

        if container.decodeNil() {
            self.value = NSNull()
        } else if let bool = try? container.decode(Bool.self) {
            self.value = bool
        } else if let int = try? container.decode(Int.self) {
            self.value = int
        } else if let double = try? container.decode(Double.self) {
            self.value = double
        } else if let string = try? container.decode(String.self) {
            self.value = string
        } else if let array = try? container.decode([AnyCodable].self) {
            self.value = array.map { $0.value }
        } else if let dictionary = try? container.decode([String: AnyCodable].self) {
            self.value = dictionary.mapValues { $0.value }
        } else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Cannot decode value")
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()

        switch value {
        case is NSNull:
            try container.encodeNil()
        case let bool as Bool:
            try container.encode(bool)
        case let int as Int:
            try container.encode(int)
        case let double as Double:
            try container.encode(double)
        case let string as String:
            try container.encode(string)
        case let array as [Any]:
            try container.encode(array.map { AnyCodable($0) })
        case let dictionary as [String: Any]:
            try container.encode(dictionary.mapValues { AnyCodable($0) })
        default:
            throw EncodingError.invalidValue(value, EncodingError.Context(
                codingPath: container.codingPath,
                debugDescription: "Cannot encode value of type \(type(of: value))"
            ))
        }
    }
}
