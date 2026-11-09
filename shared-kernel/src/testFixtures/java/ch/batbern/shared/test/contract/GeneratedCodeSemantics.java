package ch.batbern.shared.test.contract;

import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a spec the way our generated code and services do, before validation. Four rules:
 *
 * <ol>
 *   <li><b>{@code nullable: true} in a 3.1 spec.</b> 3.1 dropped the keyword in favour of
 *       {@code type: [string, 'null']}, so a strict validator ignores it (the parser keeps it as an
 *       extension) and rejects {@code null}. The 3.1 specs in {@code docs/api/} (users, companies,
 *       partners) still use it, and openapi-generator and openapi-typescript both honour it.</li>
 * *   <li><b>A nullable reference admits {@code null}.</b> The specs write a nullable object
 *       property as {@code nullable: true} next to {@code allOf: [$ref]} (3.0 and 3.1) or as
 *       {@code oneOf: [$ref, {type: 'null'}]} (3.1). The validator merges the {@code allOf} into the
 *       referenced schema and drops the {@code nullable}, and does not take the {@code null}
 *       branch of the {@code oneOf}, so both rejected {@code null}. The property becomes a
 *       nullable copy of the referenced schema.</li>
 *   <li><b>{@code format: time} means {@code HH:mm}.</b> The specs use it for local wall-clock
 *       times ({@code typicalStartTime: '16:00'}, every example), EMS sends {@code "18:00"}, and
 *       openapi-generator maps it to a plain {@code String}. JSON Schema's {@code time} is RFC 3339
 *       ({@code 18:00:00Z}), which nothing here produces or parses; the rule checks
 *       {@code HH:mm[:ss]} instead.</li>
 *   <li><b>{@code format: email} / {@code idn-email} is a mailbox shape, not a TLD lookup.</b>
 *       The validator checks both formats with Apache Commons' {@code EmailValidator}, which also
 *       requires a top-level domain from its copy of the IANA list. That rejects the reserved
 *       {@code .example} (RFC 2606) that test data must use ({@code juerg.brunner@bücherstube.example},
 *       a 17.0d production shape), although RFC 5321/6531 ask for no registry. The rule checks the
 *       shape instead: {@code email} an ASCII local part and domain, {@code idn-email} any
 *       characters (RFC 6531), each with a dotted domain. Where the spec also has a pattern (the
 *       #1102 rule, {@code EmailAddresses.OPENAPI_PATTERN}), that pattern decides, as in the
 *       generated {@code @Pattern}. Found by 4.9b, 2026-11-09.</li>
 * </ol>
 *
 * <p>{@code null} is valid only where the spec says {@code nullable} (owner decision 2026-11-09,
 * option A of {@code null-vs-optional-options.md}): the services write an unset field as
 * {@code null} (the shared {@code ObjectMapper} keeps Jackson's default inclusion), so every
 * response property they can leave unset is marked {@code nullable} in the spec, and a {@code null}
 * the spec does not allow fails the test. Until 2026-11-09 this class let any optional property be
 * {@code null}.
 */
final class GeneratedCodeSemantics {

    private static final String LOCAL_TIME = "^([01][0-9]|2[0-3]):[0-5][0-9](:[0-5][0-9])?$";
    /** {@code format: email}: printable ASCII without {@code @} and space, dotted ASCII domain. */
    static final String EMAIL_SHAPE = "^[!-?A-~]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$";
    /** {@code format: idn-email}: any characters but {@code @} and white space, dotted domain. */
    static final String IDN_EMAIL_SHAPE = "^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$";

    private GeneratedCodeSemantics() {
    }

    static void apply(OpenAPI api, OpenAPI asWritten) {
        boolean openapi31 = api.getOpenapi() != null && api.getOpenapi().startsWith("3.1");
        Visitor visitor = new Visitor(openapi31, nullableReferences(api, asWritten));
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

    /**
     * Properties written {@code nullable: true} next to {@code allOf: [$ref]}: full resolution (3.0)
     * replaces them by the referenced schema itself, without the {@code nullable}, and copies that
     * instance into every schema merged from the component (an {@code allOf} of it). Returns the
     * resolved instances with the property names under which they are nullable.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map<Schema, Set<String>> nullableReferences(OpenAPI api, OpenAPI asWritten) {
        Map<Schema, Set<String>> out = new IdentityHashMap<>();
        if (asWritten == null || asWritten.getComponents() == null || asWritten.getComponents().getSchemas() == null
                || api.getComponents() == null || api.getComponents().getSchemas() == null) {
            return out;
        }
        asWritten.getComponents().getSchemas().forEach((name, written) -> {
            Schema resolved = api.getComponents().getSchemas().get(name);
            if (written.getProperties() == null || resolved == null || resolved.getProperties() == null) {
                return;
            }
            ((Map<String, Schema>) written.getProperties()).forEach((prop, w) -> {
                boolean nullable = Boolean.TRUE.equals(w.getNullable())
                        || (w.getExtensions() != null && Boolean.TRUE.equals(w.getExtensions().get("nullable")));
                Schema r = (Schema) resolved.getProperties().get(prop);
                if (nullable && w.getAllOf() != null && w.getAllOf().size() == 1 && r != null && r.getAllOf() == null) {
                    out.computeIfAbsent(r, k -> new HashSet<>()).add(prop);
                }
            });
        });
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class Visitor {
        private final boolean openapi31;
        private final Map<Schema, Set<String>> nullableReferences;
        private final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        Visitor(boolean openapi31, Map<Schema, Set<String>> nullableReferences) {
            this.openapi31 = openapi31;
            this.nullableReferences = nullableReferences;
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
            if ("email".equals(schema.getFormat()) || "idn-email".equals(schema.getFormat())) {
                String shape = "email".equals(schema.getFormat()) ? EMAIL_SHAPE : IDN_EMAIL_SHAPE;
                schema.setFormat(null);
                if (schema.getPattern() == null) {
                    schema.setPattern(shape);
                }
            }
            if (openapi31 && schema.getExtensions() != null
                    && Boolean.TRUE.equals(schema.getExtensions().get("nullable"))) {
                allowNull(schema);
            }
            Map<String, Schema> properties = schema.getProperties();
            if (properties != null) {
                for (Map.Entry<String, Schema> entry : properties.entrySet()) {
                    Set<String> names = nullableReferences.get(entry.getValue());
                    Schema target = names != null && names.contains(entry.getKey())
                            ? entry.getValue()
                            : nullableReferenceTarget(entry.getValue());
                    if (target != null) {
                        // A copy: after full resolution the referenced schema sits elsewhere too,
                        // not nullable there.
                        Schema copy = AnnotationsUtils.clone(target, openapi31);
                        allowNull(copy);
                        entry.setValue(copy);
                    }
                    visit(entry.getValue());
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

        /**
         * The referenced schema of a property written as a nullable reference ({@code nullable}
         * next to a one-element {@code allOf}, or a {@code oneOf}/{@code anyOf} of one schema and
         * {@code type: 'null'}); null for any other property.
         */
        private Schema nullableReferenceTarget(Schema property) {
            if (property == null || property.getProperties() != null) {
                return null;
            }
            boolean nullable = Boolean.TRUE.equals(property.getNullable())
                    || (property.getExtensions() != null
                        && Boolean.TRUE.equals(property.getExtensions().get("nullable")));
            List<Schema> allOf = property.getAllOf();
            if (nullable && allOf != null && allOf.size() == 1) {
                return allOf.get(0);
            }
            for (List<Schema> branches : List.of(
                    property.getOneOf() == null ? List.<Schema>of() : property.getOneOf(),
                    property.getAnyOf() == null ? List.<Schema>of() : property.getAnyOf())) {
                List<Schema> others = branches.stream().filter(b -> !isNullType(b)).toList();
                if (branches.size() == 2 && others.size() == 1) {
                    return others.get(0);
                }
            }
            return null;
        }

        private boolean isNullType(Schema schema) {
            return "null".equals(schema.getType())
                    || (schema.getTypes() != null && schema.getTypes().equals(Set.of("null")));
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
