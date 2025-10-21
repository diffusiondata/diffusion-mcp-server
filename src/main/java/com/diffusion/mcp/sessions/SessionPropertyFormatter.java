/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.sessions;

import static com.pushtechnology.diffusion.client.session.Session.CLIENT_TYPE;
import static com.pushtechnology.diffusion.client.session.Session.COUNTRY;
import static com.pushtechnology.diffusion.client.session.Session.LANGUAGE;
import static com.pushtechnology.diffusion.client.session.Session.LATITUDE;
import static com.pushtechnology.diffusion.client.session.Session.LONGITUDE;
import static com.pushtechnology.diffusion.client.session.Session.START_TIME;
import static com.pushtechnology.diffusion.client.session.Session.TRANSPORT;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class for formatting session properties to be more human-readable.
 *
 * @author DiffusionData Limited
 */
final class SessionPropertyFormatter {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private SessionPropertyFormatter() {
        // Utility class
    }

    /**
     * Formats session properties to be more human-readable.
     *
     * @param properties the raw session properties map
     * @return a new map with formatted, human-readable values
     */
    static Map<String, String> formatProperties(
        Map<String, String> properties) {

        if (properties == null || properties.isEmpty()) {
            return properties;
        }

        final Map<String, String> formatted = new HashMap<>(properties);

        // Format timestamp properties
        formatTimestamp(formatted, START_TIME);

        // Format country code to country name
        formatCountry(formatted);

        // Format language code to display name
        formatLanguage(formatted);

        // Format client type
        formatClientType(formatted);

        // Format transport type
        formatTransport(formatted);

        // Format coordinates
        formatCoordinates(formatted);

        return formatted;
    }

    private static void formatTimestamp(
        Map<String, String> properties,
        String key) {

        properties.computeIfPresent(key, (k, timestamp) -> {
            if (timestamp.trim().isEmpty()) {
                return timestamp;
            }
            try {
                final long millis = Long.parseLong(timestamp);
                final ZoneId systemZone = ZoneId.systemDefault();
                final ZonedDateTime dateTime =
                    ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(millis),
                        systemZone);
                return dateTime.format(DATE_TIME_FORMATTER);
            }
            catch (NumberFormatException | DateTimeException e) {
                // keep original if formatting fails
                return timestamp;
            }
        });
    }

    private static void formatCountry(Map<String, String> properties) {
        properties.computeIfPresent(COUNTRY, (key, countryCode) -> {
            if (countryCode.trim().isEmpty()) {
                return countryCode;
            }
            final String countryName =
                new Locale("", countryCode)
                    .getDisplayCountry(Locale.getDefault());
            return (!countryName.isEmpty() && !countryName.equals(countryCode))
                ? countryName + " (" + countryCode + ")"
                : countryCode;
        });
    }

    private static void formatLanguage(Map<String, String> properties) {
        properties.computeIfPresent(LANGUAGE, (key, languageCode) -> {
            if (languageCode.trim().isEmpty()) {
                return languageCode;
            }
            final String languageName =
                new Locale(languageCode)
                    .getDisplayLanguage(Locale.getDefault());
            return (!languageName.isEmpty() && !languageName.equals(languageCode))
                ? languageName + " (" + languageCode + ")"
                : languageCode;
        });
    }

    private static void formatClientType(Map<String, String> properties) {
        properties.computeIfPresent(CLIENT_TYPE, (key, clientType) ->
            switch (clientType) {
                case "ANDROID" -> "Android Client";
                case "C" -> "C/C++ Client";
                case "DOTNET" -> ".NET Client";
                case "IOS" -> "iOS Client";
                case "JAVA" -> "Java Client";
                case "JAVASCRIPT_BROWSER" -> "JavaScript Browser Client";
                case "MQTT" -> "MQTT Client";
                case "PYTHON" -> "Python Client";
                case "REST" -> "REST Client";
                case "OTHER" -> "Other Client Type";
                default -> clientType; // Keep original if unknown
            }
        );
    }

    private static void formatTransport(Map<String, String> properties) {
        properties.computeIfPresent(TRANSPORT, (key, transport) ->
            switch (transport) {
                case "WEBSOCKET" -> "WebSocket Transport";
                case "HTTP_LONG_POLL" -> "HTTP Long Polling Transport";
                case "TCP" -> "TCP Transport";
                case "OTHER" -> "Other Transport Type";
                default -> transport; // Keep original if unknown
            }
        );
    }

    private static void formatCoordinates(Map<String, String> properties) {
        formatCoordinate(properties, LATITUDE, "N", "S");
        formatCoordinate(properties, LONGITUDE, "E", "W");
    }

    private static void formatCoordinate(
        Map<String, String> properties,
        String key,
        String positiveDirection,
        String negativeDirection) {

        properties.computeIfPresent(key, (k, coordinate) -> {
            if (coordinate.trim().isEmpty() || "NaN".equals(coordinate)) {
                return coordinate;
            }
            try {
                final double value = Double.parseDouble(coordinate);
                return String.format("%.6f°%s",
                    Math.abs(value),
                    value >= 0 ? positiveDirection : negativeDirection);
            }
            catch (NumberFormatException e) {
                // Keep original value if parsing fails
                return coordinate;
            }
        });
    }

}