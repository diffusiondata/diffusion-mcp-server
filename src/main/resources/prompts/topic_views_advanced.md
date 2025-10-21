# Topic Views Advanced Guide

**Prerequisites:** This guide assumes familiarity with basic topic view concepts and syntax. For quick reference and common patterns, see the Topic Views Guide.

## Advanced Path Mapping Directives

### Path Directives - Advanced Usage
Beyond basic `<path(start)>` and `<path(start, count)>`:

**Complex Path Combinations:**

```
map ?data/regions/*/cities/ to summary/<path(2)>_<path(3)>/<path(4)>
```
Source: `data/regions/europe/cities/london` → Target: `summary/europe_cities/london`

### Scalar Directives - Advanced Features

**Foreign Scalar Directives:**
Read values from topics higher in the hierarchy:

```
map ?accounts// to <scalar(/department, 0)>/<path(0)>
```
- Second parameter specifies hierarchy level (0 = root)
- Useful for inheriting properties from parent topics
- Creates dependencies that trigger re-evaluation when parent values change

**Performance Considerations:**

- Scalar values should be stable (changing them recreates reference topics)
- Use `separator` option when scalar values contain path separators

### Expand Directives - Advanced Patterns

**Multi-level Expansion:**

```
map data to customers/<expand(/customers, /name)>/orders/<expand(/orders, /id)>
```

**Array Index Usage:**
When no key pointer specified, array indices are used:

```
map data to items/<expand(/items)>
```
Creates: `items/0`, `items/1`, `items/2`

**Expansion Performance Notes:**

- Children should be relatively stable for efficiency
- Value updates are fine, but structure changes cause topic recreation
- Limit expansion depth to avoid excessive topic creation

## Advanced Transformations

### Process Transformations - Complete Reference

**Advanced Set Operations:**

- `set(, empty_object)` - Replace entire value with empty object
- `set(/data, empty_object)` - Set or replace with empty object
- `set(/data, empty_array)` - Set or replace with empty array
- `set(/items/-, /new_item)` - Append to array
- `set(/field, /source f"type=string,scale=2")` - Formatting
- `set(/data)` - Copy structure or field unchanged - useful after emptying

**Calculation Expressions:**

- Operators: `+`, `-`, `*`, `/`
- Supports parentheses for precedence
- Scientific notation allowed: `3.2e-1`, `3E2`
- Integer vs decimal handling with automatic precision

In calculations **always** use parentheses to make the operator precedence clear. So `/A - (/A * /B / 100)` is **not** the same as `/A - (/A * (/B / 100))`

**Formatting Options:**

```
set(/price, calc "/amount * /rate" format "type=string,scale=2,round=half_up")
```
- `type`: `number` or `string`
- `scale`: decimal places (0 or positive)
- `round`: `up`, `down`, `ceiling`, `floor`, `half_up`, `half_down`, `half_even`, `unnecessary`

**Complex Conditional Logic:**

```
process {
  if "/tier eq 'premium' and /balance > 10000" set(/priority, "high")
  elseif "/tier eq 'gold' or /years_active > 5" set(/priority, "medium") 
  else set(/priority, "standard")
}
```

Note that conditional branches can have multiple operations, for example:

```
process {
  if "/tier eq 'premium' and /balance > 10000" 
  set(/priority, "high") ;
  set(/balance) 
  else set(/priority, "standard")
}
```
Thus operations for all branches must be repeated:

```
process {
  if "/tier eq 'premium' and /balance > 10000" 
  set(/priority, "high") ; set(/balance)
  else set(/priority, "standard") ; set(/balance)
}
```


**Boolean Operations:**

- `and`, `&` (higher precedence)  
- `or`, `|`
- `not`
- Parentheses for grouping

**Comparison Operators:**

- `=`, `eq`, `!=`, `ne` (all scalar types)
- `>`, `gt`, `<`, `lt`, `>=`, `ge`, `<=`, `le` (numbers only)
- `=~`, `matches` (regex against string representation)

### Patch Transformations - JSON Patch Reference

**All JSON Patch Operations (RFC 6902):**

```
patch '[
  {"op": "add", "path": "/new_field", "value": "new_value"},
  {"op": "remove", "path": "/old_field"},
  {"op": "replace", "path": "/field", "value": "updated_value"},
  {"op": "move", "from": "/old_path", "path": "/new_path"},
  {"op": "copy", "from": "/source", "path": "/destination"},
  {"op": "test", "path": "/field", "value": "expected_value"}
]'
```

**Important Notes:**
- `test` operation compares CBOR representations (order matters)
- Patch failure removes reference topic
- Atomic operation - all operations succeed or all fail

### Insert Transformations - Advanced Usage

Format is 
`insert path [key fromKey] at insertionKey [default defaultValue]`

- `path` is the path of a topic to insert data from.
- `path` is specified in exactly the same way as for the path mapping `to` clause, except it may not contain `expand` directives. 
- path directives operate on the path of the source topic, whereas scalar directives operate on the current value being processed by the view.
- `key` optionally specifies a JSON pointer indicating the part of the insertion topic to be inserted. Default is the whole value.
- `insertionKey` is a JSON pointer indicating where to insert within the reference topic. If nested the parent must exist. `/-` may be used to append to the end of an existing array.
- `default` optionally specifies a value to insert if the insertion topic did not exist or the key did not exist.

**Dynamic Topic Path Construction:**

```
insert rates/<scalar(/base_currency)>/<scalar(/target_currency)> 
key /rate at /exchange_rate default 1.0
```

**Conditional Insertion:**

```
insert config/<scalar(/region)>/settings 
key /timezone at /user_timezone default "UTC"
```

**Array Insertion:**

```
insert lookup_data/<scalar(/category)> key /items at /category_items/-
```

## Advanced Options

### Throttle Option
- Only applies when source updates exceed configured rate
- Provides topic-scoped conflation
- **Ignored for TIME\_SERIES topics** (no efficient conflation)
- Updates during throttle period are conflated into single update

Format is

`throttle to x updates every period`

Where `x` is the number of updates and the `period` can be a number of `seconds`, `minutes` or `hours`.

Example:

`map ?a/ to b/<path(1)> throttle to 2 updates every 5 seconds`

### Delay Option
- Uses local file buffering for delayed updates
- Buffer size = delay duration × update rate × average update size
- **Delayed events not persisted** - lost on server restart

Format:

`delay by duration`

Where `duration` is a number of `seconds`, `minutes`, or `hours`.

Example:

`map ?a/ to b/<path(1)> delay by 5 minutes`

### Type Option

- Allows mapping to a topic type that is different from the source topic.
- Generally works between primitive types, STRING, INT64, DOUBLE.
- Primitive to JSON results in single value JSON.
- JSON to primitive only works of JSON has a single value.
- Mapping from TIME_SERIES to single value works, latest event becomes reference topic value.
- Mapping a single value to a TIME_SERIES should be avoided as it does not work in a clustered environment.

Example:

`map ?a/ to b/<path(1)> type STRING`

### With Properties Option

- Used to change certain topic properties on the reference topic.
- Those not set inherit from the source topic.

All can be set with the following exceptions:

- `PERSISTENT`: Always "Not set" as reference topics never persisted)
- `OWNER`: Always "Not set" (no direct ownership)
- `REMOVAL`: Always "Not set" (managed by source topic lifecycle)
- `VALIDATE_VALUES`: Always "Not set" (cannot reject updates)
- `SCHEMA`: Always copied, cannot be overridden
- `TIME_SERIES_RETAINED_RANGE`: Cannot be increased beyond source value

Example:-

`map ?a/ to b/<path(1)> with properties 'CONFLATION':'off', 'COMPRESSION':'false'`

Note that the property key and the value have to be quoted.

### Separator option

- used to translate path separators found in scalar values
- avoid extra topic tree levels being created when using scalar mappings

Example:-

`map ?a/path/ to b/<scalar(/markets/name)> separator '-'`

If markets/name has one or more path separators (`/`) they will be translated to `-`.

## Remote Topic Views

Topic views can use source topics from a different Diffusion cluster through **remote servers**. The server hosting the topic views is the **secondary server**, and the server with the source topics is the **primary server**.

### Specifying Remote Servers in Topic Views

To use remote topics as sources, add a `from` clause after the `map` clause:

```
map ?selector from remoteName to target/path
```

**Syntax:**

- `from` keyword followed by the remote server name
- Remote server name must match a configured remote server
- The `from` clause is placed between `map` and `to` clauses

**Examples:**

```
// Basic remote topic view
map ?sensors/ from primary to local/sensors/<path(1)>

// Map all descendants from remote server
map ?A// from server1 to <path(0)>

// With transformations
map ?data/ from prodCluster to processed/<path(1)>
process { set(/timestamp, "2025-09-29") }

// With expansion
map ?accounts/ from mainServer to users/<expand(/customers, /id)>/<path(1)>
```

### Key Behaviours

**Topic View and Remote Server Lifecycle:**

- Topic view can be created before the remote server exists
- Topic view remains dormant until remote server connects
- When connection establishes, reference topics are created automatically
- If remote server is removed, all reference topics are removed and topic view becomes dormant
- Topic view can be updated to reference a different remote server

**Connection Loss:**

- Complete connection loss removes all reference topics for that remote server
- Reference topics recreated when connection re-establishes
- For SECONDARY_INITIATOR: connection maintained only when topic views need it
- For PRIMARY_INITIATOR/SECONDARY_ACCEPTOR: connection always maintained

**Selector Scope:**

- Selector matches topics at the **primary server**, not the secondary server
- Source topic at primary can have same path as different topic on secondary server
- Multiple topic views can use the same remote server

### Remote Server Configuration

Remote servers must be configured separately using the remote servers feature. For complete details on creating and managing remote servers, **see the Remote Servers Guide** (use the `get_context` tool with `remote_servers`).

Configuration covers:

- Creating remote servers with `create_remote_server` tool
- Remote server types (SECONDARY_INITIATOR, PRIMARY_INITIATOR, SECONDARY_ACCEPTOR)
- Connection options, authentication, and parameters
- Deployment patterns and troubleshooting

### Precedence Rules

Remote topic views follow the same precedence rules as local topic views:

- If remote server doesn't exist or isn't connected, topic view is not evaluated
- When remote server connects, precedence determines if reference topics replace existing ones
- Topic views evaluated based on creation order, regardless of remote vs local sources

## Access Control and Security

### Required Permissions
**For Topic View Management:**

- `READ_TOPIC_VIEWS` - list topic views
- `MODIFY_TOPIC_VIEWS` - create/update/remove topic views
- `SELECT_TOPIC` - on source topic selector path prefix

**For Topic View Execution:**

- Topic view inherits creating session's security context
- Security context used for each reference topic creation
- Requires `READ_TOPIC` permission for source topics
- Requires `MODIFY_TOPIC` permission for reference topic paths

**For Remote Topic Views:**

- Security context applies to both source topics (on primary) and reference topics (on secondary)
- Remote server authentication credentials separate from topic view security context

### Security Context Persistence
- Security context captured at creation time
- Persisted with topic view definition
- **Not updated** if session roles change later
- Complete security re-evaluation requires topic view replacement

## Persistence and Replication

### Topic View Persistence
- Topic views themselves are persisted and replicated
- Reference topics are **never** persisted or replicated
- Topic views restored on server restart
- Reference topics recreated from available source topics

### Clustering Behaviour
- Topic views replicated to all cluster members
- Each server evaluates topic views locally
- Different reference topics possible on different servers (for non-replicated sources)
- Remote server configurations automatically distributed across cluster

### Remote Topic View Clustering
- Both topic views and remote server definitions distributed automatically
- Each secondary cluster member connects to primary server
- All members produce same reference topics from remote topic views
- Connection behaviour depends on remote server type (SECONDARY_INITIATOR vs PRIMARY_INITIATOR)

## Advanced Patterns and Use Cases

### Performance Optimisation Patterns
```
// Efficient filtering before expensive operations
map ?high_volume_data/ to processed/<path(1)>
process { if "/important eq true" continue }
throttle to 1 update every 10 seconds

// Minimize expansion scope
map data to customers/<expand(/active_customers, /id)>  // Better
// vs
map data to all/<expand(/all_customers, /id)> process { if "/active" continue }  // Worse
```

### Remote Data Aggregation Pattern
```
// Aggregate data from multiple primary servers
map ?region_data/ from primary_us to global/us/<path(1)>
map ?region_data/ from primary_eu to global/eu/<path(1)>
map ?region_data/ from primary_asia to global/asia/<path(1)>
```

### Error Recovery Patterns
```
// Graceful handling of missing data
insert lookup/<scalar(/category)> at /category_info default "unknown"

// Conditional processing with fallbacks
process {
  if "/primary_value exists" set(/value, /primary_value)
  elseif "/secondary_value exists" set(/value, /secondary_value)  
  else set(/value, "default")
}
```

### Data Quality Patterns
```
// Validation and cleaning
process {
  if "/email matches '^[^@]+@[^@]+\\.[^@]+$'" continue;
  remove(/invalid_email);
  set(/email, "invalid@example.com")
}

// Data enrichment
insert geo_data/<scalar(/country)> key /timezone at /user_timezone
process { set(/local_time, calc "/timestamp + /timezone_offset") }
```

### Delayed Remote Data Pattern
```
// Delayed publication of remote data (e.g., for free tier users)
map ?market_data/ from prod_primary to delayed/<path(1)>
  delay by 15 minutes
  throttle to 1 update every minute
```

## Troubleshooting and Debugging

### Common Issues
1. **Reference topics not created:** Check source topic permissions and selector accuracy
2. **Topics created then removed:** Verify scalar/expand values are stable
3. **Performance issues:** Review expansion scope and throttling settings
4. **Security errors:** Validate topic view security context permissions
5. **Remote connection failures:** Check URL, credentials, firewall rules, and remote server state
6. **Remote topics intermittent:** Review reconnection settings for SECONDARY_INITIATOR

### Debugging Techniques
1. Test with simple specifications first
2. Use console topic browser to verify source topic structure
3. Check server logs for topic view evaluation errors
4. Verify JSON structure matches pointer references
5. Test scalar/expand directives with sample data
6. For remote topic views: Use `list_remote_servers` to check connection state
7. Monitor connection logs for reconnection attempts and failures

This comprehensive guide focuses on advanced features, detailed behaviour, and reference information while avoiding duplication with the `topic_views` guide.
