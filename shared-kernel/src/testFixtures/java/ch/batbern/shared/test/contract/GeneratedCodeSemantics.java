package ch.batbern.shared.test.contract;

import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a spec the way our generated code and services do, before validation. Three rules:
 *
 * <ol>
 *   <li><b>{@code nullable: true} in a 3.1 spec.</b> 3.1 dropped the keyword in favour of
 *       {@code type: [string, 'null']}, so a strict validator ignores it (the parser keeps it as an
 *       extension) and rejects {@code null}. The 3.1 specs in {@code docs/api/} (users, companies,
 *       partners) still use it, and openapi-generator and openapi-typescript both honour it.</li>
 *   <li><b>An optional property may be {@code null}.</b> The services serialise an absent
 *       optional as {@code null} (Jackson's default inclusion; CUMS {@code UserResponse} sends
 *       {@code "bio": null}, checked 2026-11-08 by the CUMS provider contract test), and every
 *       generated Java model reads an optional field as {@code @Nullable}. For the S2S contract,
 *       {@code null} in a non-required property therefore means "absent". A required property
 *       still has to be present and non-null unless the spec says it is nullable.</li>
 *   <li><b>{@code format: time} means {@code HH:mm}.</b> The specs use it for local wall-clock
 *       times ({@code typicalStartTime: '16:00'}, every example), EMS sends {@code "18:00"}, and
 *       openapi-generator maps it to a plain {@code String}. JSON Schema's {@code time} is RFC 3339
 *       ({@code 18:00:00Z}), which nothing here produces or parses; the rule checks
 *       {@code HH:mm[:ss]} instead.</li>
 * </ol>
 *
 * <p>Rule 2 is lenient on purpose and on record: the generated frontend types declare such
 * fields as {@code bio?: string}, without {@code null}. That mismatch belongs to the frontend
 * harness, not to this one.
 */
final class GeneratedCodeSemantics {

    private static final String LOCAL_TIME = "^([01][0-9]|2[0-3]):[0-5][0-9](:[0-5][0-9])?$";

    private GeneratedCodeSemantics() {
    }

    static void apply(OpenAPI api) {
        boolean openapi31 = api.getOpenapi() != null && api.getOpenapi().startsWith("3.1");
        Visitor visitor = new Visitor(openapi31);
        if (api.getComponents() != null) {
            if (api.getComponents().getSchemas() != null) {
                api.getComponents().getSchemas().values().forEach(visitor::visit);
            }
            if (api.getComponents().getResponses() != null) {
                api.getComponents().getResponses().values().forEach(visitor::visit);
            }
            if (api.getComponents().getParameters() != null) {
                api.getComponents().getParameters().values().forEach(visitor::visit);
            }
        }
        if (api.getPaths() != null) {
            api.getPaths().values().forEach(item -> {
                if (item.getParameters() != null) {
                    item.getParameters().forEach(visitor::visit);
                }
                item.readOperations().forEach(op -> {
                    if (op.getParameters() != null) {
                        op.getParameters().forEach(visitor::visit);
                    }
                    if (op.getRequestBody() != null) {
                        visitor.visit(op.getRequestBody().getContent());
                    }
                    if (op.getResponses() != null) {
                        op.getResponses().values().forEach(visitor::visit);
                    }
                });
            });
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class Visitor {
        private final boolean openapi31;
        private final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        Visitor(boolean openapi31) {
            this.openapi31 = openapi31;
        }

        void visit(ApiResponse response) {
            if (response != null) {
                visit(response.getContent());
            }
        }

        void visit(Parameter parameter) {
            if (parameter != null) {
                visit(parameter.getSchema());
            }
        }

        void visit(Content content) {
            if (content != null) {
                content.values().forEach(media -> visit(media.getSchema()));
            }
        }

        void visit(Schema schema) {
            if (schema == null || !seen.add(schema)) {
                return;
            }
            if ("time".equals(schema.getFormat())) {
                schema.setFormat(null);
                if (schema.getPattern() == null) {
                    schema.setPattern(LOCAL_TIME);
                }
            }
            if (openapi31 && schema.getExtensions() != null
                    && Boolean.TRUE.equals(schema.getExtensions().get("nullable"))) {
                allowNull(schema);
            }
            Map<String, Schema> properties = schema.getProperties();
            if (properties != null) {
                List<String> required = schema.getRequired() == null ? List.of() : schema.getRequired();
                for (String name : new ArrayList<>(properties.keySet())) {
                    Schema property = properties.get(name);
                    if (property != null && !required.contains(name) && !allowsNull(property)) {
                        // A copy: after full resolution one schema instance can sit in several
                        // places, some of them required.
                        Schema copy = AnnotationsUtils.clone(property, openapi31);
                        allowNull(copy);
                        properties.put(name, copy);
                        property = copy;
                    }
                    visit(property);
                }
            }
            visit(schema.getItems());
            if (schema.getAdditionalProperties() instanceof Schema additional) {
                visit(additional);
            }
            for (List list : new List[] {schema.getAllOf(), schema.getAnyOf(), schema.getOneOf()}) {
                if (list != null) {
                    list.forEach(s -> visit((Schema) s));
                }
            }
        }

        private boolean allowsNull(Schema schema) {
            return Boolean.TRUE.equals(schema.getNullable())
                    || (schema.getTypes() != null && schema.getTypes().contains("null"));
        }

        private void allowNull(Schema schema) {
            if (openapi31) {
                Set<String> types = schema.getTypes() == null
                        ? new LinkedHashSet<>()
                        : new LinkedHashSet<>(schema.getTypes());
                if (schema.getType() != null) {
                    types.add(schema.getType());
                }
                if (!types.isEmpty()) {
                    types.add("null");
                    schema.setTypes(types);
                }
            } else {
                schema.setNullable(true);
            }
            if (schema.getEnum() != null && !schema.getEnum().contains(null)) {
                schema.getEnum().add(null);
            }
        }
    }
}
