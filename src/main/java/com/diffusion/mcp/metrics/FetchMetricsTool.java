/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.metrics;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.METRICS;
import static com.diffusion.mcp.tools.JsonSchemas.enumProperty;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.stringProperty;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.monoToolException;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.stringArgument;
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pushtechnology.diffusion.client.features.control.Metrics;
import com.pushtechnology.diffusion.client.features.control.Metrics.MetricSample;
import com.pushtechnology.diffusion.client.features.control.Metrics.MetricSampleCollection;
import com.pushtechnology.diffusion.client.features.control.Metrics.MetricsRequest;
import com.pushtechnology.diffusion.client.features.control.Metrics.MetricsResult;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Fetch metrics tool for retrieving server metrics.
 *
 * @author DiffusionData Limited
 */
final class FetchMetricsTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(FetchMetricsTool.class);

    static final String TOOL_NAME = "fetch_metrics";

    private static final String TOOL_DESCRIPTION =
        "Fetches metrics from the Diffusion server with optional filtering and server selection. " +
            "Need VIEW_SERVER permission. " +
            "See " + METRICS + " context for more information about metrics.";

    /*
     * Parameters.
     */
    private static final String SERVER_NAME = "server";
    private static final String CURRENT_SERVER = "current";
    private static final String FILTER = "filter";
    private static final String FILTER_TYPE = "filterType";
    private static final String NAMES_FILTER = "names";
    private static final String REGEX_FILTER = "regex";
    private static final String FORMAT = "format";
    private static final String DETAILED_FORMAT = "detailed";
    private static final String SUMMARY_FORMAT = "summary";

    /**
     * Tool input schema.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                SERVER_NAME,
                stringProperty(
                    "Specific server name to fetch metrics from (optional). " +
                        "Use '" + CURRENT_SERVER + "' for current server. " +
                        "Defaults to all servers."))
            .property(
                FILTER,
                stringProperty(
                    "Filter metrics by name pattern (optional). " +
                        "Can be exact names separated by commas or a regex pattern."))
            .property(
                FILTER_TYPE,
                enumProperty(
                    "Type of filter - '" + NAMES_FILTER +
                        "' for comma-separated exact names, " +
                        "'" + REGEX_FILTER + "' for regular expression. " +
                        "Defaults to '" + NAMES_FILTER + "'.",
                    List.of(NAMES_FILTER, REGEX_FILTER)))
            .property(
                FORMAT,
                enumProperty(
                    "Output format - '" + DETAILED_FORMAT +
                        "' includes all samples, " +
                        "'" + SUMMARY_FORMAT +
                        "' shows overview. Defaults to '" + SUMMARY_FORMAT +
                        "'.",
                    List.of(DETAILED_FORMAT, SUMMARY_FORMAT)))
            .additionalProperties(false)
            .build();

    private FetchMetricsTool() {
    }

    /**
     * Create the tool.
     */
    static AsyncToolSpecification create(SessionManager sessionManager) {

        return AsyncToolSpecification.builder()

            .tool(Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(INPUT_SCHEMA)
                .build())

            .callHandler((exchange, request) -> {

                // Check session
                final Session session =
                    sessionManager.get(exchange.sessionId());
                if (session == null) {
                    return noActiveSession();
                }

                // Extract parameters
                final Map<String, Object> arguments = request.arguments();
                final String server = stringArgument(arguments, SERVER_NAME);
                final String filter = stringArgument(arguments, FILTER);
                final String filterType =
                    stringArgument(arguments, FILTER_TYPE, NAMES_FILTER);
                final String format =
                    stringArgument(arguments, FORMAT, SUMMARY_FORMAT);

                LOG.info(
                    "Fetching metrics - server: {}, filter: {}, filterType: {}, format: {}",
                    server,
                    filter,
                    filterType,
                    format);

                return executeMetricsRequest(
                    session,
                    server,
                    filter,
                    filterType,
                    format);

            })
            .build();
    }

    private static Mono<CallToolResult> executeMetricsRequest(
        Session session,
        String server,
        String filter,
        String filterType,
        String format) {

        try {
            // Build the metrics request
            MetricsRequest request =
                session.feature(Metrics.class).metricsRequest();

            // Configure server selection
            if (server != null) {
                if (CURRENT_SERVER.equalsIgnoreCase(server.trim())) {
                    request = request.currentServer();
                }
                else {
                    request = request.server(server.trim());
                }
            }

            // Configure filtering
            if (filter != null) {
                request = applyFilter(request, filter.trim(), filterType);
            }

            // Execute the request
            return Mono
                .fromFuture(request.fetch())
                .timeout(TEN_SECONDS)
                .doOnNext(
                    result -> LOG.debug("Metrics fetch completed successfully"))
                .map(result -> formatMetricsResult(result, format))
                .onErrorMap(TimeoutException.class, e -> timex())
                .onErrorResume(ex -> monoToolException(TOOL_NAME, ex, LOG));

        }
        catch (Exception ex) {
            return monoToolException(TOOL_NAME, ex, LOG);
        }
    }

    private static MetricsRequest applyFilter(
        MetricsRequest request,
        String filter,
        String filterType) {

        try {
            if (REGEX_FILTER.equalsIgnoreCase(filterType)) {
                return request.filter(Pattern.compile(filter));
            }
            else {
                // Default to names - split by comma and trim
                final Set<String> filterNames = Set.of(filter.split(","));
                final Set<String> trimmedNames =
                    filterNames.stream()
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .collect(Collectors.toSet());
                return request.filter(trimmedNames);
            }
        }
        catch (PatternSyntaxException e) {
            throw new IllegalArgumentException(
                "Invalid regex pattern: " + e.getMessage());
        }
    }

    private static CallToolResult formatMetricsResult(
        MetricsResult result,
        String format) {

        try {
            final ObjectNode response = OBJECT_MAPPER.createObjectNode();
            final Set<String> serverNames = result.getServerNames();

            response.put("serverCount", serverNames.size());
            response.put(FORMAT, format);

            final ObjectNode serversNode = response.putObject("servers");

            int totalCollections = 0;
            int totalSamples = 0;

            for (String serverName : serverNames) {
                final List<MetricSampleCollection> metrics =
                    result.getMetrics(serverName);
                final ObjectNode serverNode = serversNode.putObject(serverName);

                serverNode.put("collectionCount", metrics.size());

                if (DETAILED_FORMAT.equalsIgnoreCase(format)) {
                    final ArrayNode collectionsArray =
                        serverNode.putArray("collections");

                    for (MetricSampleCollection collection : metrics) {
                        addSamples(
                            createCollectionNode(collectionsArray, collection),
                            collection);
                        totalSamples += collection.getSamples().size();
                    }
                }
                else {
                    // Summary format
                    final ArrayNode summaryArray =
                        serverNode.putArray(SUMMARY_FORMAT);

                    for (MetricSampleCollection collection : metrics) {
                        createCollectionNode(summaryArray, collection);
                        totalSamples += collection.getSamples().size();
                    }
                }

                totalCollections += metrics.size();
            }

            response.put("totalCollections", totalCollections);
            response.put("totalSamples", totalSamples);

            return toolResult(response.toString());

        }
        catch (Exception ex) {
            LOG.error("Error formatting metrics result", ex);
            return toolResult(
                "Error formatting metrics result: " + ex.getMessage());
        }
    }

    private static ObjectNode createCollectionNode(
        ArrayNode parent,
        MetricSampleCollection collection) {

        final ObjectNode summaryNode = parent.addObject();
        summaryNode.put("name", collection.getName());
        summaryNode.put("type", collection.getType().toString());
        summaryNode.put("unit", collection.getUnit());
        summaryNode.put("sampleCount", collection.getSamples().size());
        return summaryNode;
    }

    private static void addSamples(
        ObjectNode parent,
        MetricSampleCollection collection) {

        final ArrayNode samplesArray = parent.putArray("samples");
        for (MetricSample sample : collection.getSamples()) {
            final ObjectNode sampleNode = samplesArray.addObject();
            sampleNode.put("name", sample.getName());
            sampleNode.put("value", sample.getValue());

            final Optional<Long> timeStamp = sample.getTimestamp();
            if (timeStamp.isPresent()) {
                sampleNode.put("timestamp", timeStamp.get());
            }

            if (!sample.getLabelNames().isEmpty()) {
                final ObjectNode labelsNode = sampleNode.putObject("labels");
                final List<String> names = sample.getLabelNames();
                final List<String> values = sample.getLabelValues();
                for (int i = 0; i < names.size() &&
                    i < values.size(); i++) {
                    labelsNode.put(names.get(i), values.get(i));
                }
            }
        }
    }
}