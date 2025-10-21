/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import io.modelcontextprotocol.spec.McpSchema.JsonSchema;

/**
 * Utilities to build MCP JsonSchema instances programmatically.
 *
 * @author DiffusionData Limited
 */
public final class JsonSchemas {

    public static final String TYPE = "type";
    public static final String DESCRIPTION = "description";
    private static final String OBJECT = "object";
    private static final String MIN_LENGTH = "minLength";
    private static final String MAX_LENGTH = "maxLength";
    private static final String PATTERN = "pattern";
    private static final String FORMAT = "format";
    private static final String DEFAULT = "default";
    private static final String ENUM = "enum";
    private static final String STRING = "string";
    private static final String INTEGER = "integer";
    private static final String MINIMUM = "minimum";
    private static final String MAXIMUM = "maximum";
    private static final String BOOLEAN = "boolean";
    private static final String ARRAY = "array";
    private static final String ITEMS = "items";
    private static final String MIN_ITEMS = "minItems";
    private static final String MAX_ITEMS = "maxItems";
    private static final String UNIQUE_ITEMS = "uniqueItems";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String ADDITIONAL_PROPERTIES = "additionalProperties";
    public static final String CONSTANT = "const";
    private static final String REFERENCE = "$ref";
    private static final String ANY_OF = "anyOf";
    private static final String ONE_OF = "oneOf";
    private static final String ALL_OF = "allOf";


    private JsonSchemas() {
    }

    /**
     * A simple string property which must have at least one character.
     */
    public static Map<String, Object> stringProperty(String description) {
        return baseWith(STRING, description, MIN_LENGTH, 1);
    }

    /**
     * Fully specified string property.
     */
    public static Map<String, Object> stringProperty(
        String description,
        Integer minLength,
        Integer maxLength,
        String pattern,
        String format,
        String defaultValue,
        List<String> enumValues) {

        Map<String, Object> map = base(STRING, description);
        putIfNotNull(map, MIN_LENGTH, minLength);
        putIfNotNull(map, MAX_LENGTH, maxLength);
        putIfNotNull(map, PATTERN, pattern);
        putIfNotNull(map, FORMAT, format);
        putIfNotNull(map, DEFAULT, defaultValue);
        putIfNotNull(map, ENUM, enumValues);

        return map;
    }

    public static Map<String, Object> enumProperty(
        String description,
        List<String> values) {
        return baseWith(
            STRING,
            description,
            ENUM,
            values == null ? List.of() : values);
    }

    public static Map<String, Object> intProperty(
        String description,
        Integer minimum,
        Integer maximum,
        Integer defaultValue) {
        Map<String, Object> map = base(INTEGER, description);
        putIfNotNull(map, MINIMUM, minimum);
        putIfNotNull(map, MAXIMUM, maximum);
        putIfNotNull(map, DEFAULT, defaultValue);
        return map;
    }

    /**
     * An optional int property which if specified, must be at least 1.
     */
    public static Map<String, Object> intProperty(
        String description) {
        return baseWith(INTEGER, description, MINIMUM, 1);
    }

    /**
     * A boolean property with no default value.
     * <p>
     * Do not use for 'required' values.
     */
    public static Map<String, Object> boolProperty(String description) {
        return base(BOOLEAN, description);
    }

    /**
     * A boolean value with a default.
     */
    public static Map<String, Object> boolProperty(
        String description,
        Boolean defaultValue) {
        Map<String, Object> map = base(BOOLEAN, description);
        putIfNotNull(map, DEFAULT, defaultValue);
        return map;
    }

    /**
     * Array property with an items schema and for an array of unique items.
     */
    public static Map<String, Object> arrayProperty(
        Map<String, Object> itemsSchema,
        Integer minItems,
        Integer maxItems,
        Boolean uniqueItems,
        String description) {
        Map<String, Object> map = base(ARRAY, description);
        map.put(ITEMS, itemsSchema == null ? Map.of() : itemsSchema);
        putIfNotNull(map, MIN_ITEMS, minItems);
        putIfNotNull(map, MAX_ITEMS, maxItems);
        putIfNotNull(map, UNIQUE_ITEMS, uniqueItems);
        return map;
    }

    /**
     * Object property with nested
     * "properties"/"required"/"additionalProperties".
     */
    public static Map<String, Object> objectProperty(
        Map<String, Object> properties,
        List<String> required,
        Boolean additionalProperties,
        String description) {

        Map<String, Object> map = base(OBJECT, description);
        map.put(PROPERTIES, properties == null ? Map.of() : properties);
        map.put(REQUIRED, required == null ? List.of() : required);
        putIfNotNull(map, ADDITIONAL_PROPERTIES, additionalProperties);
        return map;
    }

    /** Constant value property. */
    public static Map<String, Object> constProperty(
        Object value,
        String description) {
        return baseWith(null, description, CONSTANT, value);
    }

    /**
     * Simple defs map builder.
     */
    public static Map<String, Object> defs(
        Consumer<Map<String, Object>> defsConsumer) {

        final Map<String, Object> map = new LinkedHashMap<>();
        defsConsumer.accept(map);
        return map;
    }

    /**
     * $ref property (e.g., to something in $defs).
     */
    public static Map<String, Object> ref(String refUri, String description) {
        return baseWith(null, description, REFERENCE, refUri);
    }

    public static Map<String, Object> anyOf(
        String description,
        List<Map<String, Object>> schemas) {
        return baseWith(
            null,
            description,
            ANY_OF,
            schemas == null ? List.of() : schemas);
    }

    public static Map<String, Object> oneOf(
        String description,
        List<Map<String, Object>> schemas) {
        return baseWith(
            null,
            description,
            ONE_OF,
            schemas == null ? List.of() : schemas);
    }

    public static Map<String, Object> allOf(
        String description,
        List<Map<String, Object>> schemas) {
        return baseWith(
            null,
            description,
            ALL_OF,
            schemas == null ? List.of() : schemas);
    }

    public static JsonSchemaBuilder jsonSchemaBuilder() {
        return new JsonSchemaBuilder();
    }

    /**
     * Fluent builder for the *top-level* object schema.
     */
    public static final class JsonSchemaBuilder {

        private final Map<String, Object> theProperties = new LinkedHashMap<>();
        private final List<String> isRequired = new ArrayList<>();
        private Boolean additionalProperties = null;
        private Map<String, Object> defs = null;

        public JsonSchemaBuilder property(
            String name,
            Map<String, Object> schema) {

            theProperties.put(
                Objects.requireNonNull(name),
                Objects.requireNonNull(schema));

            return this;
        }

        public JsonSchemaBuilder required(String... names) {
            if (names != null) {
                isRequired.addAll(Arrays.asList(names));
            }
            return this;
        }

        public JsonSchemaBuilder additionalProperties(Boolean allowed) {
            this.additionalProperties = allowed;
            return this;
        }

        public JsonSchemaBuilder defs(
            Consumer<Map<String, Object>> defsConsumer) {

            final Map<String, Object> map = new LinkedHashMap<>();
            defsConsumer.accept(map);
            this.defs = map;
            return this;
        }

        public JsonSchema build() {
            return defs == null
                ? object(theProperties, isRequired, additionalProperties)
                : objectWithDefs(
                    theProperties,
                    isRequired,
                    additionalProperties,
                    defs);
        }

        /**
         * Build a top-level object schema.
         */
        private static JsonSchema object(
            Map<String, Object> properties,
            List<String> required,
            Boolean additionalProperties) {

            return new JsonSchema(
                OBJECT,
                properties == null ? Map.of() : properties,
                required == null ? List.of() : required,
                additionalProperties, // null => JSON Schema default (true)
                null, // $defs
                null // legacy "definitions"
            );
        }

        /**
         * Build a top-level object schema with $defs.
         */
        private static JsonSchema objectWithDefs(
            Map<String, Object> properties,
            List<String> required,
            Boolean additionalProperties,
            Map<String, Object> defs) {

            return new JsonSchema(
                OBJECT,
                properties == null ? Map.of() : properties,
                required == null ? List.of() : required,
                additionalProperties,
                defs == null ? Map.of() : defs,
                null);
        }
    }

    private static Map<String, Object> base(String type, String description) {
        final Map<String, Object> map = new LinkedHashMap<>();
        putIfNotNull(map, TYPE, type);
        putIfNotNull(map, DESCRIPTION, description);
        return map;
    }

    private static Map<String, Object> baseWith(
        String type,
        String description,
        String key,
        Object value) {

        final Map<String, Object> map = base(type, description);
        putIfNotNull(map, key, value);
        return map;
    }

    private static void putIfNotNull(
        Map<String, Object> map,
        String key,
        Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
