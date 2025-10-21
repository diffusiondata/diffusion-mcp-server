# Diffusion Topics Advanced Guide

## Overview

This guide covers advanced topic properties that control topic behaviour, performance, and lifecycle. These properties are specified when creating topics using the `add_topic` tool.

For basic topic operations (fetching, exploring structure, topic selectors), see the main `topics` context.

## Topic Properties Reference

### COMPRESSION - Bandwidth Optimisation

**Purpose**: Reduce bandwidth by compressing topic messages at the cost of CPU

**Values**: 

- `off` - No compression
- `low` - Light compression, low CPU cost (default)
- `medium` - Moderate compression, moderate CPU cost
- `high` - Maximum compression, high CPU cost
- `true` / `false` - Legacy values (true=medium, false=off)

**Applies to**: `STRING`, `JSON`, `BINARY`, `TIME_SERIES`

**When to use**:

- Use `high` for large values over limited bandwidth connections
- Use `low` for balance of bandwidth savings and CPU cost
- Use `off` if CPU is constrained or bandwidth is plentiful

**Example**:

```
add_topic topicPath="data/large_dataset" type="JSON" compression="high"
```

**Trade-offs**: Higher compression saves bandwidth but increases server CPU usage. The server must also support compression and the client must be capable of decompressing.

---

### CONFLATION - Queue Management

**Purpose**: Control how queued updates are merged or discarded when sessions fall behind

**Values**:

- `off` - Never conflate (all updates delivered)
- `conflate` - Conflate when back pressure detected (default for non-time series)
- `unsubscribe` - Unsubscribe when back pressure detected
- `always` - Always conflate (only latest value queued)

**Applies to**: `STRING`, `JSON`, `BINARY`, `DOUBLE`, `INT64`

**Restrictions**: `TIME_SERIES` topics only support `off` or `unsubscribe`

**When to use**:

- `always` - For topics where only latest value matters (e.g., current stock price)
- `conflate` - For most topics (good default)
- `off` - When every update must be delivered (e.g., financial transactions)
- `unsubscribe` - For topics where outdated data is worse than no data

**Example**:

```
add_topic topicPath="stock/AAPL/price" type="DOUBLE" conflation="always"
```

**Important**: Back pressure occurs when a session's queue exceeds configured limits. Conflation reduces memory and prevents session termination.

---

### DONT\_RETAIN\_VALUE - Transient Data

**Purpose**: Prevent topic from retaining its last value

**Values**: `true` or `false` (default: `false`)

**Applies to**: `STRING`, `JSON`, `BINARY`, `DOUBLE`, `INT64`

**Note**: `TIME_SERIES` topics always retain latest value

**When to use**:

- Data is only transiently valid (real-time streams)
- Want to reduce memory usage
- Data has high update rate and is replicated across cluster

**Side effects**:

- New subscribers receive no initial value
- Fetch operations return nothing
- Delta streams disabled (impacts performance if values are related)

**Example**:

```
add_topic topicPath="telemetry/stream" type="JSON" dontRetainValue=true
```

**Trade-off**: Saves memory and improves cluster replication throughput, but disables delta streams which normally improve performance.

---

### OWNER - Topic Ownership

**Purpose**: Grant specific principal `UPDATE_TOPIC`, `MODIFY_TOPIC`, and `READ_TOPIC` permissions

**Values**: Session filter expression (e.g., `$Principal is "username"`)

**Applies to**: All topic types

**Format**: 

```
$Principal is "principalName"
```
Use single or double quotes. Escape special characters with `\`.

**When to use**:

- Creating topics on behalf of other users
- Per-user topics that should be managed by that user
- Temporary topics tied to a specific principal

**Common pattern with REMOVAL**:

```
add_topic 
  topicPath="users/john/preferences" 
  type="JSON"
  owner="$Principal is 'john'"
  removal="when no session has '$Principal is \"john\"' for 5m"
```

**Important**: Owner still needs `SELECT_TOPIC` permission from security store to subscribe/fetch.

---

### PERSISTENT - Persistence Control

**Purpose**: Prevent topic from being persisted even when server persistence is enabled

**Values**: `true` (default) or `false`

**Applies to**: All topic types

**When to use**:

- Transient data that shouldn't survive server restart
- Reduce persistence overhead for high-throughput topics
- Testing or temporary topics

**Example**:

```
add_topic topicPath="session/temp/data" type="STRING" persistent=false
```

---

### PRIORITY - Delivery Priority

**Purpose**: Control delivery order when multiple updates are queued

**Values**:

- `low` - Delivered last
- `default` - Normal priority (default)
- `high` - Delivered first

**Applies to**: All topic types

**When to use**:

- `high` - Critical updates (alerts, important notifications)
- `default` - Most topics
- `low` - Background data, bulk updates

**Example**:

```
add_topic topicPath="alerts/critical" type="JSON" priority="high"
add_topic topicPath="logs/debug" type="STRING" priority="low"
```

**Impact**: Most visible when there's a backlog. On lightly loaded systems, priority has minimal effect.

---

### PUBLISH\_VALUES\_ONLY - Disable Delta Streams

**Purpose**: Always publish complete values instead of deltas

**Values**: `true` or `false` (default: `false`)

**Applies to**: `STRING`, `JSON`, `BINARY`, `TIME_SERIES`

**When to use**:

- Successive values are unrelated (no benefit from deltas)
- High network capacity (bandwidth savings not needed)
- Want to reduce server/client CPU (delta calculation overhead)

**Trade-off**: Transmits more data but reduces CPU for delta calculation. Generally NOT recommended unless values are completely unrelated.

**Example**:

```
add_topic topicPath="random/data" type="JSON" publishValuesOnly=true
```

---

### REMOVAL - Automatic Topic Removal

**Purpose**: Define conditions for automatic topic removal

**Format**: 

```
when <conditions> [remove "<selector>"]
```

**Condition Types**:

**1. Time After (Absolute Time)**

The MCP `add_topic` tool provides a convenient `time after <period>` condition syntax that is automatically converted to absolute time.

**MCP Tool Convenience Syntax** (recommended):

```
when time after <period>
```

Period format: number followed by unit

- `s` - seconds (e.g., `30s`)
- `m` - minutes (e.g., `10m`)
- `h` - hours (e.g., `2h`)
- `d` - days (e.g., `1d`)

**Examples**:

```
removal="when time after 10m"
removal="when time after 2h"
removal="when time after 30s"
removal="when time after 1d"
```

**Important**: The `time after <period>` syntax is a convenience feature of the MCP tool. It converts relative times to absolute milliseconds when the topic is created, based on the MCP server's current time. Diffusion itself receives the absolute timestamp.

**Native Diffusion Formats** (also supported):

```
when time after <milliseconds>
when time after "<RFC 1123 datetime>"
```

Examples:

```
removal="when time after 1735689600000"
removal="when time after 'Tue, 3 Jun 2025 11:05:30 GMT'"
```

**2. Subscriptions Less Than**

```
when [local] subscriptions < <n> for <period> [after <period>]
```

**3. No Updates For**

```
when no updates for <period> [after <period>]
```

**4. No Session Has** (multiple allowed)

```
when no [local] session has "<criteria>" [for <period>] [after <period>]
when this session closes  // shorthand
```

**Time Periods**: `10s` (seconds), `5m` (minutes), `2h` (hours), `1d` (days)

**Logical Operators**: `and`, `or`, parentheses for grouping

**Complex Expressions**:

```
removal="when no updates for 2h or time after 3h"
removal="when subscriptions < 1 for 10m or time after 24h"
```

**Whitespace Handling**: Whitespace variations are supported (e.g., `time  after`, `time\tafter`)

**Examples**:

Remove after 10 minutes (convenience syntax):

```
removal="when time after 10m"
```

Remove when no subscriptions:

```
removal="when subscriptions < 1 for 20m"
```

Remove when creator disconnects:

```
removal="when this session closes"
```

Remove when no users from department:

```
removal="when no session has 'Department is \"Engineering\"' for 30m"
```

Complex condition with convenience syntax:

```
removal="when no updates for 1h or time after 24h"
```

Traditional complex conditions:

```
removal="when time after 'Tue, 3 Jun 2025 11:05:30 GMT' and (subscriptions < 2 for 10m or no updates for 20m)"
```

Remove multiple topics:

```
removal="when subscriptions < 1 for 5m remove '*temp//'"
```

**The `local` keyword**: Restricts evaluation to local cluster only, ignoring fanout replicas on downstream remote servers.

---

### TIDY\_ON\_UNSUBSCRIBE - Queue Cleanup

**Purpose**: Remove queued updates when session unsubscribes

**Values**: `true` or `false` (default: `false`)

**Applies to**: All topic types

**When to use**:
- Want to prevent outdated data being sent after unsubscribe
- Topic has high update rate

**Trade-off**: Performance overhead (must scan queue to remove updates) but prevents unwanted data transmission.

**Example**:

```
add_topic topicPath="realtime/feed" type="JSON" tidyOnUnsubscribe=true
```

---

### VALIDATE\_VALUES - Server-Side Validation

**Purpose**: Validate inbound values before storing/publishing

**Values**: `true` or `false` (default: `false`)

**Applies to**: All topic types

**When to use**:
- Untrusted update sources
- Critical data requiring validation
- Debugging value corruption issues

**Trade-off**: Performance overhead for validation. Generally NOT recommended as TopicUpdate API already validates.

**Example**:

```
add_topic topicPath="critical/config" type="JSON" validateValues=true
```

---

## Time Series Properties

These properties apply ONLY to `TIME_SERIES` topics.

### TIME\_SERIES\_EVENT\_VALUE\_TYPE - Event Data Type

**Purpose**: Specify the data type for time series events

**Values**: `string`, `json`, `binary`, `double`, `int64`

**Required**: YES (mandatory for `TIME_SERIES` topics)

**Example**:

```
add_topic 
  topicPath="sensors/temperature" 
  type="TIME_SERIES"
  timeSeriesEventValueType="double"
```

---

### TIME\_SERIES\_RETAINED\_RANGE - Event Retention

**Purpose**: Define which events to retain in the time series

**Format**: Range expression with constraints

**Constraints**:

- `limit <n>` - Keep last n events
- `last <duration>` - Keep events within duration from latest

**Duration units**: `MS` (milliseconds), `S` (seconds), `H` (hours)

**Default**: `limit 10` (last 10 events)

**Examples**:

Keep last 5 events:

```
timeSeriesRetainedRange="limit 5"
```

Keep events from last 10 seconds:

```
timeSeriesRetainedRange="last 10S"
```

Combined constraints (smallest range wins):

```
timeSeriesRetainedRange="last 10S limit 5"
```

**Multiple constraints**: When combined, the constraint selecting the smallest range applies.

---

### TIME\_SERIES\_SUBSCRIPTION\_RANGE - Initial Events

**Purpose**: Define which events to send to new subscribers

**Format**: Same as `TIME_SERIES_RETAINED_RANGE`

**Default**: 

- Latest event (if delta streams enabled)
- No events (if delta streams disabled)

**Examples**:

Send last 3 events:

```
timeSeriesSubscriptionRange="limit 3"
```

Send last minute of events:

```
timeSeriesSubscriptionRange="last 60S"
```

---

## Property Combinations and Patterns

### High-Performance Topic
For maximum throughput with minimal server overhead:

```
add_topic
  topicPath="fast/updates"
  type="JSON"
  compression="off"
  conflation="always"
  dontRetainValue=true
  publishValuesOnly=true
```

### Critical Data Topic
For data requiring every update and validation:

```
add_topic
  topicPath="transactions/financial"
  type="JSON"
  conflation="off"
  validateValues=true
  priority="high"
  persistent=true
```

### User Session Topic
Topic that exists only while user is connected:

```
add_topic
  topicPath="users/alice/session"
  type="JSON"
  owner="$Principal is 'alice'"
  removal="when this session closes"
  persistent=false
```

### Temporary Cache Topic
Topic that removes itself after 10 minutes (using convenience syntax):

```
add_topic
  topicPath="cache/temp_data"
  type="JSON"
  removal="time after 10m"
  persistent=false
```

### Idle Data Cleanup
Topic that removes when inactive for 1 hour or after 24 hours maximum:

```
add_topic
  topicPath="session/workspace"
  type="JSON"
  removal="when no updates for 1h or time after 24h"
```

### Time Series Sensor
Retain 1 hour of data, send last 5 minutes to subscribers:

```
add_topic
  topicPath="sensors/temp_01"
  type="TIME_SERIES"
  timeSeriesEventValueType="double"
  timeSeriesRetainedRange="last 1H"
  timeSeriesSubscriptionRange="last 5M"
```

---

## Property Compatibility Matrix

| Property | `STRING`/`JSON`/`BINARY` | `DOUBLE`/`INT64` | `TIME_SERIES` |
|----------|-------------------|--------------|-------------|
| `COMPRESSION` | ✓ | – | ✓ |
| `CONFLATION` | ✓ | ✓ | ✓* |
| `DONT_RETAIN_VALUE` | ✓ | ✓ | – |
| `OWNER` | ✓ | ✓ | ✓ |
| `PERSISTENT` | ✓ | ✓ | ✓ |
| `PRIORITY` | ✓ | ✓ | ✓ |
| `PUBLISH_VALUES_ONLY` | ✓ | – | ✓ |
| `REMOVAL` | ✓ | ✓ | ✓ |
| `TIDY_ON_UNSUBSCRIBE` | ✓ | ✓ | ✓ |
| `VALIDATE_VALUES` | ✓ | ✓ | ✓ |
| `TIME_SERIES_*` | – | – | ✓ |

\* `TIME_SERIES` only supports `off` or `unsubscribe` for `CONFLATION`

---

## Best Practices

### Performance Optimisation
1. Use `conflation="always"` for topics where only latest value matters
2. Set `compression="off"` if bandwidth is plentiful and CPU is constrained
3. Use `dontRetainValue=true` for high-rate transient data in clusters
4. Disable delta streams (`publishValuesOnly=true`) only if values are unrelated

### Resource Management
1. Use `REMOVAL` policies to clean up unused topics automatically
2. Use the `time after <period>` convenience syntax for time-based removal
3. Set `persistent=false` for temporary or session-specific topics
4. Use appropriate `TIME_SERIES` retention ranges to limit memory
5. Set `OWNER` for user-specific topics combined with removal conditions

### Data Integrity
1. Use `conflation="off"` when every update must be delivered
2. Enable `validateValues=true` only when necessary (performance cost)
3. Use `priority="high"` for critical updates
4. Use `tidyOnUnsubscribe=true` to prevent stale data after unsubscribe

### Common Pitfalls
- ✗ Setting `dontRetainValue=true` without understanding delta stream impact
- ✗ Using `publishValuesOnly=true` for related values (wastes bandwidth)
- ✗ Not setting `REMOVAL` policies for temporary topics (resource leak)
- ✗ Enabling `validateValues=true` unnecessarily (performance overhead)
- ✗ Using `conflation="off"` when not required (increases memory usage)
- ✗ Forgetting that `time after <period>` is converted at topic creation time, not evaluated continuously

---

## Troubleshooting

### Topic Creation Fails
- Check `TIME_SERIES` topics have `timeSeriesEventValueType` set
- Verify `REMOVAL` expression syntax
- For `time after` syntax, ensure period format is correct (e.g., `10m`, `2h`)
- Ensure `OWNER` value is properly quoted
- Check conflation value is valid for topic type

### Performance Issues  
- Too much compression? Try `low` or `off`
- Delta streams causing CPU load? Consider `publishValuesOnly=true`
- Queue buildup? Review conflation policy
- High memory? Check `TIME_SERIES` retention ranges

### Unexpected Topic Removal
- Review `REMOVAL` conditions carefully
- Remember: `time after <period>` is converted to absolute time at creation
- Check `after` periods in removal conditions
- Verify session filter criteria in `no session has` conditions
- Remember: `this session closes` removes topic when creator disconnects

### Missing Initial Values
- Check if `dontRetainValue=true` is set
- For `TIME_SERIES`, verify `timeSeriesSubscriptionRange`
- Ensure topic has been updated at least once

---

## Related Guides

- **Main Topics Guide**: Basic operations, fetching, structure exploration - `topics` context.
- **Topic Selectors Guide**: Selector syntax for `REMOVAL` policies - `topic_selectors` context.
- **Sessions Guide**: Session filters for `OWNER` and `REMOVAL` conditions - `sessions` context.

This guide covers all advanced topic properties for fine-grained control over topic behaviour, performance, and lifecycle management.
