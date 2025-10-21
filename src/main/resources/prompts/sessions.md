# Diffusion Sessions Guide

## Understanding Sessions

Sessions represent connected clients to the Diffusion server. Each client connection creates a unique session with properties that can be monitored and analysed for operational insights.

### Session Lifecycle
- Created when client connects to server
- Assigned unique session ID
- Properties populated based on connection details and authentication
- Removed when client disconnects or connection is lost

### Why Monitor Sessions?
- **Operational awareness**: Understand who's connected and from where
- **Performance analysis**: Identify connection patterns and usage
- **Security monitoring**: Track authentication and access patterns  
- **Capacity planning**: Monitor concurrent connections and resource usage
- **Troubleshooting**: Diagnose client connection issues

## Session Properties

### Fixed Properties (Set by Server)
- `$Principal` - Authentication username/principal
- `$ClientIP` - Client's IP address  
- `$SessionId` - Unique session identifier
- `$Connector` - Connection type (WebSocket, HTTP, etc.)
- `$StartTime` - When the session started (timestamp)
- `$Transport` - Transport protocol (WEBSOCKET, HTTP\_LONG\_POLL, TCP)
- `$Roles` - Assigned security roles
- `$Country` - Client's country code (if GeoIP enabled)
- `$Language` - Client's language setting (if available)
- `$Environment` - Client environment (ANDROID, iOS, JAVASCRIPT\_BROWSER, etc.)
- `$Latitude` / `$Longitude` - Geographic coordinates (if GeoIP enabled)

### User Properties (Set by Applications)
Custom properties defined by your applications:

- Examples: `Status`, `Department`, `Tier`, `GameType`, `Region`, `UserType`
- Used for business logic, grouping, or filtering
- Can be updated during session lifecycle
- Often used in session filters for targeted operations
- Use the `trust_client_proposed_property` to permit user proposed properties

## Session Discovery

### Listing All Sessions
Use `get_sessions` to retrieve session IDs:

**Basic Usage:**

```
get_sessions                    # All connected sessions
```

**Filtered Results:**

```
get_sessions filter="$Principal is 'admin'"    # Admin sessions only
get_sessions filter="$Country in ['US','UK']"  # Specific countries  
get_sessions filter="has Location"             # Sessions with location data
```

### Getting Session Details
Use `get_session_details` to examine specific sessions:

**Required Parameter:**
- `sessionId` - The session ID to examine

**Optional Parameter:**  
- `properties` - Array of specific property keys to retrieve

**Examples:**

```
get_session_details sessionId="abc123"                                        # All properties
get_session_details sessionId="abc123" properties=["$Principal","$ClientIP"]  # Specific properties
get_session_details sessionId="abc123" properties=["ALL_FIXED_PROPERTIES"]    # All system properties
get_session_details sessionId="abc123" properties=["ALL_USER_PROPERTIES"]     # All custom properties
```

## Session Filtering

Session filters are powerful query expressions for selecting sessions based on their properties.

### Filter Syntax Overview
Session filters use a simple expression language with these components:

- **Property references**: Use property names (with `$` prefix for fixed properties)
- **Operators**: Comparison and logical operators
- **Values**: Strings in quotes, numbers, booleans
- **Logical combinations**: `and`, `or`, `not` with parentheses for grouping

### Equality Operators
- `is` or `eq` - equals (case sensitive)
- `ne` - not equals

**Examples:**

```
$Principal is "admin"                    # Sessions with principal "admin"  
$Country eq "US"                         # Sessions from United States
$Transport ne "WEBSOCKET"                # Non-WebSocket connections
Status is "Active"                       # Custom property equals "Active"
```

### Pattern Matching
- `matches` - match against regular expression

**Examples:**

```
$Environment matches "^(ANDROID.*|iOS.*)"  # Mobile environments  
$ClientIP matches "192\\.168\\..*"         # Local network IPs
$Principal matches "admin.*"               # Principals starting with "admin"
```

### Set Membership  
- `in` - value belongs to a set

**Examples:**

```
$Country in ["UK", "US", "FR"]           # Sessions from specific countries
$Principal in ["admin", "operator"]      # Admin or operator users  
$Transport in ["WEBSOCKET", "TCP"]       # Specific transport types
```

### Property Existence
- `has` - check if property exists
- `hasRoles` - check if session has specific security roles

**Examples:**

```
has Location                             # Sessions with Location property set
has $Latitude                           # Sessions with latitude data
hasRoles ["trader", "admin"]            # Sessions with both trader AND admin roles
hasRoles "operator"                     # Sessions with operator role
```

### Logical Operators
- `and` - logical AND (higher precedence than OR)
- `or` - logical OR  
- `not` - logical NOT
- `()` - grouping with parentheses

**Examples:**

```
$Transport eq "WEBSOCKET" and $Country is "FR" and Status eq "Active"
$Country eq "US" and not ($Principal is "guest" or $Principal is "demo")
not (Status is "Inactive" and Tier is "Free")  
($Department is "Trading" or $Department is "Sales") and hasRoles ["operator"]
```

### Special Filters

- `all` - matches all sessions

**Example:**

```
all                                      # Select all sessions
```

## Session Analysis Workflows

### Basic Session Discovery
1. **Get overview**: `get_sessions` to see total session count
2. **Sample examination**: Pick a few session IDs and use `get_session_details`  
3. **Identify patterns**: Look for common properties and values
4. **Focused analysis**: Use filters to examine specific session groups

### Connection Analysis

```
# Geographic distribution
get_sessions filter="has $Country"
get_sessions filter="$Country eq 'US'"
get_sessions filter="$Country in ['UK','DE','FR']"

# Client types  
get_sessions filter="$Environment matches 'ANDROID.*'"
get_sessions filter="$Environment matches 'iOS.*'"
get_sessions filter="$Transport eq 'WEBSOCKET'"

# Authentication patterns
get_sessions filter="$Principal matches 'admin.*'"
get_sessions filter="hasRoles ['trader']"
get_sessions filter="not ($Principal is 'guest')"
```

### User Behaviour Analysis  

```
# Active users
get_sessions filter="Status is 'Active'"
get_sessions filter="has LastActivity"

# User segments
get_sessions filter="Tier is 'Premium'"
get_sessions filter="Department in ['Sales','Marketing']"
get_sessions filter="$Language in ['en','es','fr']"

# Long-running sessions (requires timestamp analysis)
get_sessions filter="$StartTime exists"
```

### Security Monitoring

```  
# Administrative access
get_sessions filter="hasRoles ['admin']"
get_sessions filter="$Principal matches 'admin.*'"

# Suspicious patterns
get_sessions filter="$ClientIP matches '10\\.0\\..*' and $Principal is 'external_user'"
get_sessions filter="not has $Country"  # Sessions without GeoIP data

# Multiple connections from same source
get_sessions filter="$ClientIP is '192.168.1.100'"
```

## Session-Based Metrics

Sessions can be monitored using metric collectors for ongoing analysis.

### Creating Session Metric Collectors

Use `create_session_metric_collector` with session filters:

**Required Parameters:**

- `name` - unique identifier for the collector
- `sessionFilter` - which sessions to monitor (uses same syntax as session filtering)

**Optional Parameters:**

- `groupByProperties` - group metrics by session properties
- `exportToPrometheus` - export to Prometheus monitoring
- `maximumGroups` - limit number of groups to prevent resource issues
- `removeMetricsWithNoMatches` - clean up unused metrics

### Session Metric Examples

**Monitor All Sessions:**

```
create_session_metric_collector name="all_sessions" sessionFilter="all"
```

**Monitor by Client Type:**

```
create_session_metric_collector 
  name="mobile_sessions" 
  sessionFilter="$Environment matches '^(ANDROID.*|iOS.*)'" 
  groupByProperties="$Environment,$Country"
```

**Monitor VIP Users:**

```
create_session_metric_collector 
  name="vip_users" 
  sessionFilter="Tier is 'VIP' and Status is 'Active'"
  groupByProperties="$Principal"
```

**Monitor by Geographic Region:**

```  
create_session_metric_collector
  name="regional_sessions"
  sessionFilter="$Country in ['US','UK','DE','FR']"
  groupByProperties="$Country,$Transport"
```

**Monitor High-Frequency Clients:**

```
create_session_metric_collector
  name="hft_clients" 
  sessionFilter="hasRoles ['hft'] and $Transport eq 'TCP'"
  exportToPrometheus=true
```

## Advanced Session Analysis

### Property Distribution Analysis
1. Get sample of sessions across different filters
2. Examine property patterns and common values
3. Identify business-relevant groupings
4. Create metric collectors for ongoing monitoring

### Connection Pattern Recognition  

```
# Time-based patterns (manual analysis of $StartTime)
get_sessions filter="$StartTime exists"
get_session_details sessionId="sample_id" properties=["$StartTime"]

# Geographic clustering
get_sessions filter="has $Latitude"
get_session_details sessionId="geo_session" properties=["$Country","$Latitude","$Longitude"]

# Client diversity
get_sessions filter="$Environment exists"  
# Then analyze Environment property distribution
```

### Performance Impact Analysis

```
# High-value sessions
get_sessions filter="Tier in ['Premium','Enterprise']"

# Resource-intensive clients
get_sessions filter="$Transport eq 'TCP'"  # Typically high-performance
get_sessions filter="hasRoles ['hft','trading']"

# Mobile vs desktop usage  
get_sessions filter="$Environment matches '(ANDROID|iOS).*'"
get_sessions filter="$Environment matches 'JAVASCRIPT_BROWSER.*'"
```

## Session Filter Best Practices

### Start Simple

Begin with basic filters before building complex expressions:

```
$Principal is "admin"                    # Simple equality
$Country in ["US", "UK"]                 # Simple set membership  
has Location                             # Simple existence check
```

### Test Incrementally  

Use `get_sessions` with filters to verify expressions work:

```
# Test individual components first
get_sessions filter="$Principal is 'admin'"
get_sessions filter="Status is 'Active'"

# Then combine  
get_sessions filter="$Principal is 'admin' and Status is 'Active'"
```

### Handle Special Characters

Use escape characters `\` for quotes and backslashes in values:

```
$Department is "R\&D"                    # Escape & if needed
$Notes matches ".*admin.*"               # Escape regex special chars as needed
```

### Performance Considerations

- Simple filters perform better than complex regex patterns
- Property existence checks (`has`) are efficient
- Set membership (`in`) is faster than multiple OR conditions
- Consider the expected result set size

### Documentation

Document your filter expressions for team understanding:

```
# Monitor active trading sessions during market hours
"hasRoles ['trader'] and Status is 'Active' and $Country in ['US','UK']"

# Identify mobile users for mobile-specific features  
"$Environment matches '^(ANDROID|iOS).*' and Status is 'Active'"
```

## Troubleshooting Session Issues

### No Sessions Returned
1. Verify admin connection (session operations require admin privileges)
2. Check if any clients are actually connected
3. Verify filter syntax and property names
4. Test with simpler filters first

### Property Not Found
1. Check property name spelling and case sensitivity  
2. Verify property exists in your session data
3. Use `get_session_details` on sample session to see available properties
4. Remember `$` prefix for fixed properties

### Filter Syntax Errors
1. Verify quotes around string values
2. Check operator spelling (`eq` not `equals`)
3. Ensure proper parentheses grouping
4. Test components individually before combining

### Performance Issues
1. Use more specific filters to reduce result sets
2. Avoid overly broad regex patterns
3. Consider session churn rate when setting up monitoring
4. Monitor metric collector resource usage

This guide provides comprehensive coverage of session management, filtering, and analysis capabilities for effective monitoring and operational awareness.
