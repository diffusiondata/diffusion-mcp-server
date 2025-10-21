# Diffusion Topics Guide

## Topic Structure and Naming

### Hierarchical Organisation
- Topics are organised in a tree structure using `/` separators
- Example: `sensors/environmental/temperature/building_a/floor_2/room_201`
- Case-sensitive paths
- Use descriptive, hierarchical naming that reflects data relationships

### Topic Path Rules
- No level element can be empty
- Never start a path with `/`
- Never end a path with `/` unless using descendant pattern qualifiers
- Use meaningful names that indicate the data purpose
- Consider how paths will be used for filtering

## Topic Selectors

**For complete topic selector documentation, see the topic_selectors context.**

Topic selectors allow you to match multiple topics using patterns. Key selector types include:

- **Path selectors** (`path/to/topic`) - exact path matching
- **Split path selectors** (`?path/.*/subtopic`) - regex wildcards per level
- **Full path selectors** (`*regex_pattern`) - regex on entire path
- **Selector sets** (`#selector1////selector2`) - multiple selectors with OR logic

**Refer to the topic selectors guide for:**

- Complete syntax documentation
- Descendant qualifiers (`/` and `//`)
- Common patterns and examples
- Performance optimisation
- Troubleshooting selector issues

## Topic Operations

### Single Topic Fetching
Use `fetch_topic` to retrieve a single topic value by its exact path.

**Parameters:**
- `topicPath` (required) - the exact path to the topic

**Returns:**

- Topic value, type, and properties
- Generally users want just the value, but type and properties available if requested

**Example:**

```
fetch_topic topicPath="sensors/temperature/room1"
```

**Error Handling:**

- Handle 'Topic not found' errors by verifying path spelling and case
- Ensure you have READ permissions for the topic path

### Time Series Topic Fetching
Use `time_series_value_range_query` to retrieve events from a `TIME_SERIES` topic.

**Key Parameters:**

- `topicPath` (required) - the exact path to the time series topic
- `eventValueType` (required) - the data type of events (`STRING`, `INT64`, `DOUBLE`, `JSON`, or `BINARY`)
- `maxResults` - maximum number of events to return (default: 100)

**Range Parameters (all optional):**

- **Start position (anchor):**
  - `fromSequence` - start from a specific sequence number
  - `fromTimestamp` - start from an ISO-8601 timestamp
  - `fromLast` - start N events from the end
  - If none specified, starts from the beginning
  
- **End position (span):**
  - `toSequence` - end at a specific sequence number
  - `toTimestamp` - end at an ISO-8601 timestamp
  - `next` - return N events after the start position
  - If none specified, continues to the end

**Returns:**

- Event metadata (sequence number, timestamp, author)
- Event values formatted according to the event type
- Indication if edited events are included
- Completion status (whether all matching events were returned)

**Examples:**

```
# Get last 10 events from a time series
time_series_value_range_query topicPath="metrics/cpu" eventValueType="DOUBLE" fromLast=10

# Get events from a specific timestamp onwards
time_series_value_range_query topicPath="logs/system" eventValueType="JSON" fromTimestamp="2025-01-15T10:00:00Z"

# Get events in a sequence range
time_series_value_range_query topicPath="trades/stock_xyz" eventValueType="JSON" fromSequence=1000 toSequence=2000

# Get next 50 events after sequence 5000
time_series_value_range_query topicPath="sensors/data" eventValueType="DOUBLE" fromSequence=5000 next=50
```

**Important Notes:**

- This performs a **value range query** which returns a merged view with latest edits
- If an event has been edited, the query returns the edited value, not the original
- The `eventValueType` must match the `TIME_SERIES_EVENT_VALUE_TYPE` of the topic
- If the event type is wrong, the query will fail with an `IncompatibleTopicException`
- Use `fetch_topic` first to check the topic type and event value type if unsure

**Error Handling:**

- Verify the topic is actually a `TIME_SERIES` topic
- Ensure `eventValueType` matches the topic's configured event value type
- Check `READ_TOPIC` permissions for the topic path
- Handle timestamp parsing errors for invalid ISO-8601 formats

### Multiple Topic Fetching  
Use `fetch_topics` to retrieve multiple topics using topic selectors.

**Key Parameters:**

- `topicSelector` - pattern to select topics (if not specified, returns all topics)
- `values` - set to `true` to return topic values (default: false, returns paths only). Ignored if `depth` specified.
- `number` - limit results per call (default: 5000 without values, 100 with values)
- `after` - for pagination, specify last topic path from previous call
- `depth` - explore structure (e.g., `1` returns one topic per branch)
- `sizes` - return the size of each topic
- `unpublishedDelayedTopics` - include topic views generated delayed topics that have not yet been published

**Structure Exploration:**

```
fetch_topics depth=1          # Explore top-level structure  
```
Examining the first part of each returned path shows available root branches. Values never returned when `depth` is specified.

**Topic Fetching Examples:**

```
fetch_topics                                         # First 5000 topic paths
fetch_topics values=true                             # First 100 topics with values
fetch_topics topicSelector="?sensors//"              # All sensor topics
fetch_topics topicSelector="?sensors//" values=true  # Sensor topics with values
fetch_topics topicSelector="?games/*/scores" number=50  # Limit results
```

**Note:** These examples use topic selectors - see the topic selectors guide for complete selector syntax and patterns.

**Pagination for Large Result Sets:**

- Use the `number` parameter to control batch size
- If results indicate more topics available, use `after` parameter with the last topic path from previous call
- Continue until all topics are retrieved

**Example Pagination:**

```
# First batch
fetch_topics topicSelector="?data//" number=100

# Next batch (assuming last topic was "data/sensors/temp_99")  
fetch_topics topicSelector="?data//" number=100 after="data/sensors/temp_99"
```

### Creating Topics

Use `add_topic` to create a new topic.

Always check what topics are there already before creating new ones.

**Key Parameters:**

- `topicPath` - The path of the topic to create
- `type` - The topic type
- `initialValue` - an optional initial value for the topic

**Other Parameters:**

All of the other parameters are for advanced use and would normally only need to be supplied if the user has specific requirements for the topic.
To find out more about these parameters and how to specify them use the `get_context` tool to obtain the `topics_advanced` context.

### Updating Topics

Use `update_topic` to set a new value for an existing topic

**Key Parameters:**

- `topicPath` - The path of the topic to create
- `type` - The topic type
- `value` - the new value
- `eventType` - the value type for TIME_SERIES type topics

### Removing Topics

- Use `remove_topics` to remove all topics that match a given topic selector
- Always ensure full understanding of topic selectors (context `topic_selectors`) before attempting topic removal
- Always present the selector you are going to use to the user and confirm they want to go ahead before issuing a removal as it is a destructive operation.

**Key Parameters:**

- `topicSelector` - The topic selector
  
## Topic Discovery Workflows

### Basic Server Exploration

1. **Explore structure**: Use `fetch_topics depth=1` to see all branches
2. **Discover topics**: Use `fetch_topics topicSelector="?branch//"` to explore specific branches  
3. **Get specific values**: Use `fetch_topic topicPath="exact/path"` for individual topics
4. **Batch fetch values**: Use `fetch_topics topicSelector="pattern" values=true` for multiple topics
5. **Query time series**: Use `time_series_value_range_query` for `TIME_SERIES` topics

### Progressive Discovery Pattern

```
# Step 1: See what's available at the top level
fetch_topics depth=1

# Step 2: Explore interesting branches  
fetch_topics topicSelector="?sensors//" depth=1
fetch_topics topicSelector="?data//" depth=1

# Step 3: Get actual data from promising areas
fetch_topics topicSelector="?sensors/temperature//" values=true number=20

# Step 4: Examine specific topics of interest
fetch_topic topicPath="sensors/temperature/building_a/room_1"

# Step 5: For time series topics, query event ranges
time_series_value_range_query topicPath="sensors/temperature/history" eventValueType="DOUBLE" fromLast=100
```

### Large Dataset Handling
For topic trees with thousands of topics:

1. Use `depth` parameter to understand structure before fetching values
2. Use specific selectors rather than broad patterns (see topic selectors guide)
3. Use appropriate `number` limits to control memory usage
4. Implement pagination for complete data extraction
5. For time series topics, use range queries to retrieve manageable chunks of event data

## Topic Types and Data Formats

### Supported Topic Types

- `JSON` - Structured data, supports topic view transformations
- `STRING` - Text data, simple and efficient
- `INT64` - Integer numbers
- `DOUBLE` - Floating point numbers  
- `BINARY` - Raw binary data
- `TIME_SERIES` - Time-ordered data with event types

### Working with JSON Topics
JSON topics are the most flexible for analysis and transformation:

- Can be queried using JSON Pointers (RFC 6901)
- Support topic view transformations
- Enable complex filtering and processing
- Allow nested data structures

### Working with TIME\_SERIES Topics
`TIME_SERIES` topics store sequences of time-ordered events:

- Each event has a sequence number, timestamp, and author
- Events can have different value types (`STRING`, `INT64`, `DOUBLE`, `JSON`, `BINARY`)
- Events can be edited, with the system retaining edit history
- Use `time_series_value_range_query` to retrieve event ranges
- Use `fetch_topic` to get only the latest event
- Configure retention policies to manage storage

## Best Practices

### Topic Path Design
- Use hierarchical structures that make sense for consumers
- Avoid deep nesting unless necessary (impacts performance)
- Consider how paths will be used for subscriptions and filtering
- Use consistent naming conventions across your topic tree
- Group related data logically

### Performance Considerations
- Use specific topic selectors rather than broad ones (see topic selectors guide)
- Request only the data you need (use `values=false` for structure exploration)
- Use pagination for large result sets
- Monitor server performance when fetching large numbers of topics
- Cache frequently accessed topic structures
- For time series, use range queries with appropriate limits rather than fetching all events

### Topic Selection Strategy
- **Broad exploration**: Start with `depth=1` to understand structure
- **Focused discovery**: Use specific selectors like `?area_of_interest//`
- **Targeted fetching**: Use exact paths when you know what you want
- **Batch operations**: Group related topic operations together
- **Time series analysis**: Use range queries to analyze specific time periods

### Error Prevention
- Verify topic paths for exact spelling and case sensitivity
- Check permissions before attempting to read topics
- Handle missing topics gracefully in your applications
- Use topic selectors to avoid hardcoding specific topic paths
- For time series queries, verify the event value type before querying

### Topic Organization Patterns

#### By Function
```
sensors/temperature/...
sensors/humidity/... 
sensors/pressure/...
```

#### By Location
```
buildings/north/floor1/...
buildings/north/floor2/...
buildings/south/floor1/...
```

#### By Time
```
data/daily/2024/01/15/...
data/hourly/2024/01/15/14/...
data/realtime/current/...
```

#### By User/Department
```
departments/sales/metrics/...
departments/engineering/metrics/...
users/john_doe/preferences/...
```

## Troubleshooting Topic Issues

### Topic Not Found
1. Verify exact spelling and case sensitivity
2. Confirm you have READ permissions for the topic
3. Use topic selectors to find similarly named topics (see topic selectors guide)

### Expected topics not returned in a fetch
1. You may not have the necessary permission to read the topics.
2. If you have been working with delayed topics they may not have been published yet, so try again with the `unpublishedDelayedTopics` option.

### Topic Value Truncation
`fetch_topics` may truncate very large topic values. Use `fetch_topic` to retrieve the full value.

### Time Series Query Issues

1. **IncompatibleTopicException**: Verify the `eventValueType` matches the topic's `TIME_SERIES_EVENT_VALUE_TYPE`
2. **Empty results**: Check that events exist in the specified range
3. **Permission denied**: Ensure you have `READ_TOPIC` permission for the topic path
4. Use `fetch_topic` first to verify the topic type and properties

### Permission Denied  

1. Verify your session has appropriate permissions
2. Check if topic requires specific roles or credentials
3. Ensure connection was established with sufficient privileges
4. Contact server administrator for permission requirements

### Performance Issues
1. Reduce scope of topic selectors (see topic selectors guide)
2. Use pagination for large result sets  
3. Fetch structure before fetching values
4. Consider topic organisation and indexing
5. For time series, use smaller range queries with appropriate limits

### Selector-Related Issues

- **Invalid syntax**: Refer to the topic selectors guide for proper syntax
- **No results**: Check selector patterns and regex usage
- **Too many results**: Use more specific selectors

This guide provides comprehensive coverage of topic operations, structure, and best practices for working effectively with Diffusion's topic tree. For detailed selector syntax and patterns, always refer to the dedicated `topic_selectors` context.
