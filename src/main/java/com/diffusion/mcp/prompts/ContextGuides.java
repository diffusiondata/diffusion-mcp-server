package com.diffusion.mcp.prompts;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ContextGuides {

    // Guides
    public static final String INTRODUCTION = "introduction";
    public static final String TOPICS = "topics";
    public static final String TOPICS_ADVANCED = "topics_advanced";
    public static final String TOPIC_SELECTORS = "topic_selectors";
    public static final String SESSIONS = "sessions";
    public static final String METRICS = "metrics";
    public static final String TOPIC_VIEWS = "topic_views";
    public static final String TOPIC_VIEWS_ADVANCED = "topic_views_advanced";
    public static final String SESSION_TREES = "session_trees";
    public static final String REMOTE_SERVERS = "remote_servers";
    public static final String SECURITY = "security";

    /**
     * Key = guide name (also the markdown filename under /prompts/<name>.md)
     * Value = short description (used by tools/prompts UIs)
     */
    public static final Map<String, String> ALL;
    static {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("readme", "Read me first for using the Diffusion MCP server");
        m.put(INTRODUCTION, "Get started with Diffusion MCP - overview and basic concepts");
        m.put(TOPICS, "Explore the topic tree, fetch values, create and update topics");
        m.put(TOPICS_ADVANCED, "Advanced topic creation properties and behaviors");
        m.put(TOPIC_SELECTORS, "Use Diffusion topic selectors to target topics");
        m.put(SESSIONS, "Connect sessions, list sessions, and filter sessions");
        m.put(METRICS, "Understand Diffusion metrics and analytics");
        m.put(TOPIC_VIEWS, "Topic Views quick reference and maintenance");
        m.put(TOPIC_VIEWS_ADVANCED, "Advanced/remote topic views and customization");
        m.put(REMOTE_SERVERS, "Remote servers: understanding and creating");
        m.put(SECURITY, "Retrieve and modify security settings");
        m.put(SESSION_TREES, "Session trees and branch mapping tables");
        ALL = java.util.Collections.unmodifiableMap(m);
    }

    private ContextGuides() {}

    public static List<String> names() {
        // Preserve insertion order:
        return new java.util.ArrayList<>(ALL.keySet());
    }

    public static Optional<String> description(String name) {
        return Optional.ofNullable(ALL.get(name));
    }

    /** Classpath resource where the markdown lives. */
    public static String resourcePath(String name) {
        return "/prompts/" + name + ".md";
    }
}
