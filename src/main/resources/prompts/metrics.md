# Diffusion Metrics Guide

## Understanding Diffusion Metrics

Diffusion provides comprehensive server performance and operational metrics for monitoring, alerting, and capacity planning. Metrics cover JVM performance, topic activity, session behaviour, and custom application measurements.

### Types of Metrics
- **Server Metrics**: JVM memory, thread counts, CPU usage, network I/O
- **Topic Metrics**: Topic counts, update rates, subscription activity  
- **Session Metrics**: Connection counts, authentication patterns, geographic distribution
- **Custom Metrics**: Application-defined measurements via metric collectors

### Metrics vs Metric Collectors
- **Server Metrics**: Built-in measurements available immediately
- **Metric Collectors**: User-defined aggregators that organise and group metrics around sessions or topics

## Fetching Server Metrics

### Basic Metrics Retrieval
Use `fetch_metrics` to get current server performance data:

This should always be used to determine what metrics are actually available.

**Basic Usage:**

```
fetch_metrics                  # All metrics, summary format
```

**Server Selection:**

- `server="current"` - Current server (default)
- `server="server_name"` - Specific named server in cluster

### Filtering Metrics

**By Metric Names:**

```
fetch_metrics 
  filter="jvm_memory,thread_count" 
  filterType="names"
```

**By Regex Pattern:**

```
fetch_metrics 
  filter="jvm_.*" 
  filterType="regex"
```

**Common Filter Patterns:**

- `jvm_.*` - All JVM-related metrics
- `.*memory.*` - All memory-related metrics  
- `topic_.*` - All topic-related metrics
- `session_.*` - All session-related metrics

### Output Formats

**Summary Format (Default):**

- Overview with metric names, types, and sample counts
- Efficient for monitoring dashboards and alerts
- Good for understanding what metrics are available

```
fetch_metrics format="summary"
```

**Detailed Format:**

- Full data including all samples, values, labels, and timestamps
- Complete information for analysis and troubleshooting
- Larger data volume, use selectively

```
fetch_metrics format="detailed" filter="jvm_memory.*" filterType="regex"
```

## Metric Alerts

Metric alerts allow you to react to changes in system metrics by defining conditions under which notifications are automatically triggered. When a metric crosses a specified threshold, the alert creates or updates a Diffusion topic containing JSON data about the triggered metric.

**Metric Alerts are only supported on Diffusion Servers at release 6.12 and beyond**

**IMPORTANT** Always use the `fetch_metrics` tool to see what metrics are available before attempting to create a metric alert. Do not try to guess metric names.
### Why Use Metric Alerts?
- **Proactive monitoring**: Automatically detect threshold violations without polling
- **Real-time notifications**: Immediate awareness of critical conditions
- **Automated response**: Alert topics can trigger downstream actions
- **Flexible conditions**: Support for complex boolean logic and comparisons
- **Self-managing**: Alerts can disable themselves until conditions normalize
- **Server-specific**: Target specific servers in a cluster or monitor all servers

### Alert Lifecycle
1. **Creation**: Alert is defined with a metric, conditions, and target topic
2. **Monitoring**: Server continuously evaluates the metric against conditions
3. **Triggering**: When conditions are met, creates/updates the alert topic
4. **Notification**: Topic contains JSON representation of metric data
5. **Re-arming**: Alert can be disabled until specified conditions are met (DISABLE_UNTIL clause)

### Managing Metric Alerts

**View All Alerts:**

```
list_metric_alerts
```

**Create or Update an Alert:**

```
set_metric_alert 
  name="high_memory_alert"
  specification="select jvm_memory_used into topic alerts/memory where value > 1000000000"
```

**Remove an Alert:**

```
remove_metric_alert name="high_memory_alert"
```

### Metric Alert Specification Language

Alerts use a domain-specific language (DSL) to specify conditions and behaviour.

**Basic Syntax:**

```
SELECT metric_name
INTO_TOPIC topic_path
WHERE condition
```

**Full Syntax:**

```
SELECT metric_name [ FROM_SERVER server_name ]
    INTO_TOPIC topic_path
    [ WITH_PROPERTIES { property_name: property_value [, ...] } ]
    [ WHERE condition ]
    [ DISABLE_UNTIL condition ]
```

### Specification Components

**SELECT metric_name** (Required)

- The name of the metric to monitor
- Examples: `os_system_load_average`, `jvm_memory_used`, `session_count`

**FROM\_SERVER server\_name** (Optional)

- Monitor a specific server in the cluster
- If omitted, monitors all servers in the cluster
- Example: `from_server production-1`

**INTO\_TOPIC topic\_path** (Required)

- Topic path where alert notifications are published
- Can include JSON pointers in angle brackets for dynamic paths
- Examples: 
  - `into topic alerts/memory`
  - `into topic metrics/<server>/load`
  - `into topic alerts/<server>/<metric_name>`

**WITH\_PROPERTIES** (Optional)

- Topic properties for the alert topic
- Uses standard Diffusion topic property names
- Example: `with_properties { REMOVAL: 'when no updates for 30s' }`
- Any topic properties can be set. `REMOVAL` is useful for tidying the alert topic if not active.
- See `topics_advanced` context for full details of topic properties.

**WHERE condition** (Optional)

- Defines when the alert should trigger
- Supports logical operators: `and`, `or`
- Supports comparisons: `>`, `<`, `>=`, `<=`, `=`, `!=`
- Can compare dimensional data using `dimensions` object
- Example: `where value > 5 and dimensions = {name: 'heap'}`

**DISABLE\_UNTIL condition** (Optional)

- Temporarily disables alert after triggering
- Alert re-enables when these conditions are met
- Prevents alert flooding for oscillating metrics
- Example: `disable_until value < 500000000`

### Basic Alert Examples

**High CPU Load Alert:**

```
set_metric_alert
  name="cpu_load_alert"
  specification="select os_system_load_average into topic alerts/cpu where value > 5"
```

**Memory Usage Alert:**

```
set_metric_alert
  name="memory_alert"
  specification="select jvm_memory_used into topic alerts/memory where value > 8000000000"
```

**Session Count Alert:**

```
set_metric_alert
  name="high_sessions_alert"
  specification="select session_count into topic alerts/sessions where value > 1000"
```

**Topic Count Alert:**

```
set_metric_alert
  name="topic_limit_alert"
  specification="select topic_count into topic alerts/topics where value > 10000"
```

### Server-Specific Alerts

**Monitor Specific Server:**

```
set_metric_alert
  name="prod_server_memory"
  specification="select jvm_memory_used from_server production-1 into topic alerts/prod-1/memory where value > 6000000000"
```

**Dynamic Topic Path by Server:**

```
set_metric_alert
  name="server_load_alerts"
  specification="select os_system_load_average into topic metrics/<server>/load where value > 8"
```
*Note: `<server>` is a JSON pointer that will be replaced with the actual server name*

### Alerts with Topic Properties

**Alert Topic with Auto-Cleanup:**

The following alert would publish to the specified alert topic whenever the number of sessions exceeds 10, but the alert topic would automatically be removed if it had not been updated for 30 seconds.

```
set_metric_alert
  name="too_many_sesions_alert"
  specification="select diffusion_sessions_open into topic alerts/sessions/multiple with properties { REMOVAL: 'when no updates for 30s' } where value > 10"
```

**Alert Topic with Retention:**

```
set_metric_alert
  name="critical_load_alert"
  specification="select os_system_load_average into topic alerts/critical/load with_properties { REMOVAL: 'when no session has \"$Principal is 'admin'\" for 5m' } where value > 10"
```

### Alerts with Hysteresis (DISABLE\_UNTIL)

**Memory Alert with Recovery Threshold:**

```
set_metric_alert
  name="memory_with_recovery"
  specification="select jvm_memory_used into topic alerts/memory where value > 8000000000 disable_until value < 4000000000"
```

**CPU Load with Cooldown:**

```
set_metric_alert
  name="cpu_with_cooldown"
  specification="select os_system_load_average into topic alerts/cpu where value > 8 disable_until value < 3"
```

**Thread Count Alert:**

```
set_metric_alert
  name="thread_alert"
  specification="select thread_count into topic alerts/threads where value > 500 disable_until value < 300"
```

### Complex Alert Conditions

**Multiple Conditions with AND:**

```
set_metric_alert
  name="critical_memory"
  specification="select jvm_memory_used into topic alerts/critical/memory where value > 9000000000 and dimensions = {type: 'heap'}"
```

**Multiple Conditions with OR:**

```
set_metric_alert
  name="resource_pressure"
  specification="select os_system_load_average into topic alerts/resources where value > 10 or value < 0.5"
```

**Dimensional Data Comparison:**

```
set_metric_alert
  name="heap_memory_alert"
  specification="select jvm_memory_used into topic alerts/heap where dimensions = {name: 'heap'} and value > 5000000000"
```

### Complete Alert Examples

**Production Memory Monitoring:**

```
set_metric_alert
  name="production_memory"
  specification="select jvm_memory_used from_server prod-cluster-1 into topic alerts/<server>/memory with_properties { TIDY_ON_UNSUBSCRIBE: true } where value > 8000000000 disable_until value < 4000000000"
```

**Cluster-Wide Load Monitoring:**

```
set_metric_alert
  name="cluster_load"
  specification="select os_system_load_average into topic alerts/<server>/load where value > 8 disable_until value < 3"
```

**Session Capacity Warning:**

```
set_metric_alert
  name="session_capacity"
  specification="select session_count into topic alerts/capacity/sessions with_properties { REMOVAL: 'when no session for 1h' } where value > 5000 disable_until value < 4000"
```

## Metric Alert Use Cases

### System Health Monitoring

**Critical Memory Alert:**

```
set_metric_alert
  name="critical_memory"
  specification="select jvm_memory_used into topic alerts/critical/memory where value > 9500000000"
```

**High Thread Count:**

```
set_metric_alert
  name="thread_warning"
  specification="select thread_count into topic alerts/threads where value > 800 disable_until value < 500"
```

**System Load Average:**

```
set_metric_alert
  name="system_load"
  specification="select os_system_load_average into topic alerts/system/load where value > 12 disable_until value < 5"
```

### Capacity Planning

**Session Growth Alert:**

```
set_metric_alert
  name="session_growth"
  specification="select session_count into topic alerts/capacity/sessions where value > 8000"
```

**Topic Growth Alert:**

```
set_metric_alert
  name="topic_growth"
  specification="select topic_count into topic alerts/capacity/topics where value > 50000"
```

**Connection Rate Alert:**

```
set_metric_alert
  name="connection_spike"
  specification="select connection_rate into topic alerts/capacity/connections where value > 100 disable_until value < 50"
```

### Gaming Applications

**Player Connection Alert:**

```
set_metric_alert
  name="player_surge"
  specification="select session_count into topic alerts/game/players where value > 10000 disable_until value < 8000"
```

**Game Server Load:**

```
set_metric_alert
  name="game_server_load"
  specification="select os_system_load_average from_server game-server-1 into topic alerts/game/<server>/load where value > 9"
```

### Financial Applications

**Trading System Capacity:**

```
set_metric_alert
  name="trading_capacity"
  specification="select session_count into topic alerts/trading/capacity where value > 5000"
```

**Market Data Server Health:**

```
set_metric_alert
  name="market_data_health"
  specification="select jvm_memory_used from_server market-data-1 into topic alerts/market/<server>/health where value > 7000000000 disable_until value < 3500000000"
```

### E-commerce Applications

**Customer Connection Peak:**

```
set_metric_alert
  name="customer_peak"
  specification="select session_count into topic alerts/customers/peak where value > 15000 disable_until value < 12000"
```

**Catalog Update Rate:**

```
set_metric_alert
  name="catalog_updates"
  specification="select topic_update_rate into topic alerts/catalog/updates where value > 1000"
```

## Alert Topic Content

When an alert triggers, it creates or updates a topic with JSON content describing the metric state. The JSON includes:

- **Metric name**: The monitored metric identifier
- **Server name**: Which server triggered the alert
- **Value**: Current metric value
- **Timestamp**: When the threshold was crossed
- **Dimensions**: Any dimensional data associated with the metric
- **Labels**: Additional metric metadata

Applications can subscribe to alert topics to:

- Display real-time alerts in dashboards
- Send notifications (email, SMS, Slack, etc.)
- Trigger automated remediation actions
- Log alert events for analysis
- Escalate critical issues

## Alert Management Best Practices

### Design Guidelines
- **Use meaningful names**: Alert names should clearly indicate what they monitor
- **Set appropriate thresholds**: Based on baseline performance and capacity planning
- **Implement hysteresis**: Use DISABLE_UNTIL to prevent alert flooding
- **Organise alert topics**: Use consistent topic path structure (e.g., `alerts/<category>/<metric>`)
- **Consider server-specific vs cluster-wide**: Choose based on monitoring needs

### Threshold Selection
- **Start conservative**: Begin with higher thresholds and refine based on experience
- **Account for peaks**: Consider normal peak usage patterns
- **Use historical data**: Base thresholds on actual metric history
- **Test thoroughly**: Verify alerts trigger appropriately before production use
- **Document thresholds**: Keep records of why specific values were chosen

### Alert Topic Strategy
- **Use topic properties**: Configure cleanup, persistence, and retention policies
- **Dynamic paths**: Use JSON pointers for server-specific or metric-specific topics
- **Hierarchical structure**: Organise alerts by severity, category, or system
- **Subscription patterns**: Design for efficient consumption by monitoring systems

### Operational Management
- **Regular review**: Periodically assess alert effectiveness and adjust thresholds
- **Remove obsolete alerts**: Clean up alerts that are no longer needed
- **Monitor alert frequency**: Track how often alerts trigger to refine thresholds
- **Test alert responses**: Verify downstream systems handle alert topics correctly
- **Version control**: Keep history of alert specifications for audit and rollback

### Performance Considerations
- **Limit alert count**: Each alert adds evaluation overhead
- **Avoid redundant alerts**: Don't create multiple alerts for the same condition
- **Use appropriate conditions**: Complex conditions cost more to evaluate
- **Consider evaluation frequency**: Alerts are evaluated continuously
- **Monitor server impact**: Watch for alert evaluation affecting performance

## Integrating Alerts with Monitoring Systems

### Alert Topic Subscriptions

The Diffusion MCP server does not support subscribing to topics, but applications using the Diffusion SDKs, or the Diffusion Console can be used to subscribe to alert topics. The following examples show some example topic selectors that might be used.

**Subscribe to All Alerts:**

```
?alerts//
```

**Subscribe to Critical Alerts:**

```
?alerts/critical//
```

**Subscribe to Server-Specific Alerts:**

```
?alerts/prod-server-1//
```

### Alert Response Patterns

**Dashboard Integration:**

- Subscribe to `?alerts//` for real-time alert display
- Update UI indicators based on alert topic content
- Provide alert history and timeline visualization

**Notification Systems:**

- Subscribe to critical alert topics
- Parse JSON content for alert details
- Send notifications via email, SMS, or messaging platforms
- Include metric values, timestamps, and server information

**Automated Remediation:**

- Subscribe to specific alert topics
- Trigger automated responses (scaling, restart, etc.)
- Log remediation actions for audit
- Re-evaluate after action to confirm resolution

**Logging and Analytics:**

- Subscribe to all alert topics
- Store alert history in time-series database
- Analyze alert patterns and trends
- Generate reports on system health and capacity

### External Monitoring Integration

**Prometheus/Grafana:**

- Export metric collectors to Prometheus
- Create Grafana dashboards with alert topics as data source
- Configure Grafana alerts based on Diffusion alert topics
- Visualise alert history and metric trends

**SIEM Integration:**

- Subscribe to alert topics in security monitoring systems
- Correlate Diffusion alerts with security events
- Create dashboards for operational security
- Generate compliance reports

**Incident Management:**

- Subscribe to critical alert topics
- Automatically create incidents in ticketing systems
- Include alert context and metric data
- Update incidents when alerts clear (via DISABLE_UNTIL)

## Troubleshooting Metric Alerts

### Alert Not Triggering

**Verify Alert Configuration:**

```
list_metric_alerts
```
- Check alert name and specification
- Verify metric name is correct
- Confirm conditions are achievable

**Test Metric Availability:**

```
fetch_metrics filter="metric_name" filterType="names"
```
- Ensure metric exists and is being collected
- Verify metric values are in expected range
- Check if FROM_SERVER matches actual server name

**Review Conditions:**

- Confirm threshold values are correct
- Check for typos in metric names or conditions
- Verify dimensional data comparisons match actual dimensions

### Alert Triggering Too Frequently

**Add Hysteresis:**

```
set_metric_alert
  name="existing_alert"
  specification="... where value > threshold disable_until value < lower_threshold"
```

**Adjust Thresholds:**

- Increase trigger threshold if too sensitive
- Analyze metric patterns to find appropriate values
- Consider using moving averages (if available)

**Review Metric Volatility:**

```
fetch_metrics filter="metric_name" filterType="names" format="detailed"
```
- Check for oscillating values
- Consider metric smoothing or different evaluation approach

### Alert Topic Not Created

**Verify Permissions:**

- Alert topic is created using the principal of the session that created the alert
- Ensure the creating session has permissions to create topics at the specified path

**Check Topic Path:**

- Verify topic path syntax is valid
- Confirm JSON pointers (e.g., `<server>`) are correctly formatted
- Test topic creation manually at the same path

**Review Topic Properties:**

- Ensure WITH_PROPERTIES syntax is correct
- Verify property names and values are valid
- Check for conflicting property settings

### Server Performance Impact

**Reduce Alert Count:**

```
list_metric_alerts
```
- Remove unnecessary or redundant alerts
- Consolidate similar alerts when possible

**Simplify Conditions:**

- Use simpler boolean expressions
- Avoid complex dimensional comparisons if not needed
- Consider fewer conditions per alert

**Monitor Server Metrics:**

```
fetch_metrics filter="jvm_.*" filterType="regex"
```
- Watch CPU and memory usage
- Check thread counts
- Monitor evaluation overhead

## Metric Collectors

Metric collectors aggregate and organise server metrics for specific monitoring needs. They create focused metric groupings around sessions or topics.

### Why Use Metric Collectors?
- **Focused monitoring**: Track specific session or topic groups
- **Business alignment**: Metrics organised around business concepts
- **Efficient alerting**: Targeted metrics reduce noise
- **Prometheus export**: Integration with external monitoring systems
- **Historical tracking**: Persistent metric collection over time

### Viewing Existing Collectors
```
list_session_metric_collectors    # Show session-based collectors
list_topic_metric_collectors      # Show topic-based collectors
```

### Removing Collectors
```
remove_session_metric_collector name="collector_name"
remove_topic_metric_collector name="collector_name"  
```

## Session Metric Collectors

Monitor metrics for specific groups of sessions based on session properties.

### Creating Session Metric Collectors
Use `create_session_metric_collector`:

**Required Parameters:**

- `name` - Unique identifier for the collector
- `sessionFilter` - Which sessions to monitor (uses session filter syntax)

**Optional Parameters:**

- `groupByProperties` - Group metrics by session properties  
- `exportToPrometheus` - Export to Prometheus monitoring system
- `maximumGroups` - Limit number of groups to prevent resource issues
- `removeMetricsWithNoMatches` - Clean up unused metrics automatically

### Session Filter Examples (for Metric Collectors)

**Monitor All Sessions:**

```
create_session_metric_collector 
  name="all_sessions" 
  sessionFilter="all"
```

**Monitor by Authentication:**

```
create_session_metric_collector 
  name="admin_sessions" 
  sessionFilter="$Principal is \"admin\""
```

**Monitor by Location:**

```
create_session_metric_collector 
  name="regional_sessions" 
  sessionFilter="$Country in [\"US\",\"UK\",\"FR\"]" 
  groupByProperties="$Country"
```

**Monitor by Role:**

```
create_session_metric_collector 
  name="trader_sessions" 
  sessionFilter="hasRoles [\"trader\"]" 
  groupByProperties="$UserType,$Region"
```

**Monitor by Connection Type:**

```
create_session_metric_collector 
  name="websocket_sessions" 
  sessionFilter="$Transport eq \"WEBSOCKET\"" 
  groupByProperties="$Transport,$Country"
```

**Monitor by Custom Properties:**

```
create_session_metric_collector 
  name="active_users" 
  sessionFilter="Status is \"Active\" and $Transport eq \"WEBSOCKET\"" 
  groupByProperties="$Principal,$ClientIP"
```

**Monitor High-Value Clients:**

```
create_session_metric_collector 
  name="vip_sessions" 
  sessionFilter="Tier is \"VIP\" and Status is \"Active\"" 
  groupByProperties="$Region" 
  exportToPrometheus=true
```

**Monitor Mobile Users:**

```
create_session_metric_collector 
  name="mobile_sessions" 
  sessionFilter="$Environment matches \"^(ANDROID.*|iOS.*)\""
  groupByProperties="$Environment,$Country" 
  maximumGroups=50
```

**Monitor Specific IP Ranges:**

```
create_session_metric_collector 
  name="internal_sessions" 
  sessionFilter="$ClientIP matches \"192\\.168\\..*\"" 
  removeMetricsWithNoMatches=true
```

## Topic Metric Collectors

Monitor metrics for specific groups of topics based on topic paths and properties.

### Creating Topic Metric Collectors
Use `create_topic_metric_collector`:

**Required Parameters:**

- `name` - Unique identifier for the collector
- `topicSelector` - Which topics to monitor (uses topic selector syntax - see topic selectors guide)

**Optional Parameters:**

- `groupByTopicType` - Group by topic data type (JSON, STRING, etc.)
- `groupByTopicView` - Group by topic view association
- `groupByPathPrefixParts` - Group by first N path segments
- `exportToPrometheus` - Export to Prometheus monitoring  
- `maximumGroups` - Limit number of groups

### Topic Metric Collector Examples

**Monitor All Sensor Topics:**

```
create_topic_metric_collector
  name="sensor_metrics" 
  topicSelector="?sensors//"
  groupByPathPrefixParts=2
```

**Monitor Game Performance:**

```
create_topic_metric_collector
  name="game_metrics"
  topicSelector="?games//"
  groupByPathPrefixParts=2  # Groups like "games/chess", "games/poker"
  exportToPrometheus=true
```

**Monitor Market Data by Type:**

```
create_topic_metric_collector
  name="market_data_metrics"
  topicSelector="?markets//"
  groupByTopicType=true
  groupByPathPrefixParts=2
  maximumGroups=100
```

**Monitor User-Specific Data:**

```  
create_topic_metric_collector
  name="user_data_metrics"
  topicSelector="?users/.*/data//"
  groupByPathPrefixParts=2  # Groups by user
```

**Monitor High-Volume Topics:**

```
create_topic_metric_collector
  name="high_volume_topics"
  topicSelector="?realtime//"
  exportToPrometheus=true
  maximumGroups=25
```

**Monitor Specific Topic Path:**

```
create_topic_metric_collector
  name="forex_metrics"
  topicSelector="markets/forex/EURUSD"
  exportToPrometheus=true
```

## Common Metrics Use Cases

### Gaming Applications

**Monitor Player Sessions by Game:**

```
create_session_metric_collector
  name="player_metrics"
  sessionFilter="$GameType exists and Status is \"Active\""
  groupByProperties="$GameType,$Region"
```

**Monitor Game Topic Performance:**

```
create_topic_metric_collector
  name="game_performance" 
  topicSelector="?games//"
  groupByPathPrefixParts=2
```

**Monitor VIP Players:**

```
create_session_metric_collector
  name="vip_players"
  sessionFilter="Tier is \"VIP\" and Status is \"Playing\"" 
  groupByProperties="$GameType"
  exportToPrometheus=true
```

### Financial Applications  

**Monitor Trading Sessions:**

```
create_session_metric_collector
  name="trader_metrics"
  sessionFilter="$UserType is \"trader\""
  groupByProperties="$UserType,$Region"
```

**Monitor Market Data Topics:**

```
create_topic_metric_collector
  name="market_metrics"
  topicSelector="?markets//"
  groupByTopicType=true
  exportToPrometheus=true
```

**Monitor High-Frequency Trading:**

```
create_session_metric_collector
  name="hft_sessions"
  sessionFilter="hasRoles [\"hft\"] and $Transport eq \"TCP\""
  exportToPrometheus=true
  removeMetricsWithNoMatches=true
```

### E-commerce Applications

**Monitor Customer Sessions by Tier:**

```
create_session_metric_collector
  name="customer_metrics"
  sessionFilter="CustomerType exists"
  groupByProperties="CustomerType,$Country"
```

**Monitor Product Data Updates:**

```
create_topic_metric_collector
  name="product_metrics"
  topicSelector="?products//"
  groupByPathPrefixParts=2
```

**Monitor Shopping Cart Activity:**

```
create_topic_metric_collector
  name="cart_metrics"
  topicSelector="?carts/active//"
  groupByTopicView=true
```

## Metrics Setup and Monitoring Workflow

### Initial Setup
1. **Connect with admin credentials**: Required for metrics access
2. **Assess current state**: `list_session_metric_collectors` and `list_topic_metric_collectors`
3. **Get baseline metrics**: `fetch_metrics` to understand current performance
4. **Identify monitoring needs**: Determine which sessions/topics need tracking
5. **Set up alerts**: Create metric alerts for critical thresholds

### Creating Monitoring Strategy
1. **Session analysis**: Use session tools to understand client patterns
2. **Topic discovery**: Use topic tools to understand data structure  
3. **Business alignment**: Map technical metrics to business requirements
4. **Create collectors**: Start with broad collectors, then add specific ones
5. **Define alerts**: Establish alerts for critical metrics and thresholds

### Ongoing Management
1. **Regular review**: `fetch_metrics` with appropriate filters
2. **Collector maintenance**: Remove unused collectors to optimize performance
3. **Alert tuning**: Adjust thresholds based on alert frequency and accuracy
4. **Capacity monitoring**: Watch `maximumGroups` limits and resource usage
5. **Integration**: Export to Prometheus for alerting and dashboards

## Performance Optimization

### Efficient Metric Collection
- **Use specific filters** rather than broad patterns
- **Set appropriate maximumGroups** limits to prevent resource exhaustion
- **Remove unused collectors** regularly
- **Use Prometheus export** for long-term monitoring and alerting
- **Limit alert count** to reduce evaluation overhead

### Resource Management
- **Monitor collector overhead**: Each collector uses server resources
- **Balance granularity vs performance**: More groups = more resources
- **Use removeMetricsWithNoMatches** to clean up automatically
- **Consider session churn rate** when setting up session collectors
- **Implement alert hysteresis** to prevent notification storms

### Scaling Considerations
- **Start with summary format** for regular monitoring
- **Use detailed format** selectively for troubleshooting
- **Group by stable properties** (avoid frequently changing values)
- **Implement collector lifecycle management**
- **Design efficient alert topic hierarchies**

## Troubleshooting Metrics Issues

### No Metrics Available
1. **Verify admin connection**: Metrics require administrative privileges
2. **Check server status**: Ensure server is running and accessible
3. **Verify permissions**: Confirm user has metrics access rights
4. **Test basic fetch**: Start with `fetch_metrics` with no filters

### Collector Not Creating Metrics
1. **Verify filter syntax**: Test session/topic filters independently  
2. **Check matching criteria**: Ensure sessions/topics match the filters
3. **Review maximumGroups**: May be hitting group limits
4. **Check server logs**: Look for collector evaluation errors

### Performance Issues  
1. **Review collector count**: Too many collectors can impact performance
2. **Check group counts**: High group counts use more resources
3. **Optimize filters**: Use more specific session/topic selectors
4. **Monitor server metrics**: Watch JVM memory and CPU usage
5. **Review alert complexity**: Simplify conditions if possible

### Missing Expected Metrics
1. **Verify collector configuration**: Check filter and grouping settings
2. **Test source data**: Confirm sessions/topics exist and match filters
3. **Check timing**: New collectors may take time to accumulate data
4. **Review export settings**: Prometheus export may have delays

## Advanced Metrics Patterns

### Hierarchical Monitoring
```
# Top-level monitoring
create_session_metric_collector name="all_users" sessionFilter="all"

# Regional breakdown  
create_session_metric_collector 
  name="regional_users" 
  sessionFilter="$Country exists" 
  groupByProperties="$Country"

# Detailed user analysis
create_session_metric_collector 
  name="premium_users" 
  sessionFilter="Tier in [\"Premium\",\"Enterprise\"]" 
  groupByProperties="$Country,$UserType"
```

### Time-Based Monitoring
```
# Real-time data monitoring
create_topic_metric_collector
  name="realtime_metrics"
  topicSelector="?realtime//"
  groupByPathPrefixParts=2

# Historical data monitoring  
create_topic_metric_collector
  name="historical_metrics"
  topicSelector="?history//"
  groupByPathPrefixParts=3  # Include date grouping
```

### Business KPI Tracking
```
# Revenue-generating sessions
create_session_metric_collector
  name="revenue_sessions"
  sessionFilter="UserType in [\"Paying\",\"Premium\"] and Status is \"Active\""
  groupByProperties="UserType,$Country"
  exportToPrometheus=true

# Critical system topics
create_topic_metric_collector  
  name="critical_systems"
  topicSelector="?system/critical//"
  exportToPrometheus=true
  maximumGroups=10
```

### Coordinated Metrics and Alerts
```
# Create collector for VIP sessions
create_session_metric_collector
  name="vip_session_metrics"
  sessionFilter="Tier is \"VIP\""
  exportToPrometheus=true

# Alert when VIP session count drops
set_metric_alert
  name="vip_session_alert"
  specification="select session_count into topic alerts/vip/sessions where value < 10"
```

## Integration with External Systems

### Prometheus Export

When `exportToPrometheus=true`:

- Metrics become available at Prometheus scrape endpoints
- Enable integration with Grafana dashboards
- Support for alerting rules and notifications
- Historical data retention and analysis

### Monitoring Dashboard Patterns

```
# Core system health
fetch_metrics filter="jvm_.*" filterType="regex" format="summary"

# Application performance  
fetch_metrics filter="session_count,topic_count" filterType="names"

# Business metrics (via collectors)
fetch_metrics filter=".*revenue.*" filterType="regex"
```

### Alerting Strategies
1. **Threshold-based**: CPU usage, memory consumption, connection counts
2. **Rate-based**: Message throughput, connection rate, error rates  
3. **Business-based**: VIP user connections, critical topic updates
4. **Capacity-based**: Approaching configured limits or thresholds
5. **Hybrid approach**: Combine metric collectors with metric alerts for comprehensive monitoring

## Best Practices

### General Guidelines
- **Monitor server performance** with metrics before creating many collectors
- **Limit maximum groups** in collectors to prevent resource exhaustion
- **Use Prometheus export** for long-term monitoring and alerting
- **Clean up unused metric collectors** regularly
- **Start with broad collectors** then add specific ones based on needs
- **Implement alert hysteresis** to prevent notification flooding
- **Document alert thresholds** and their business justification

### Session Metric Collectors
- **Test session filters** with `get_sessions` before creating collectors
- **Consider session churn rate** when setting up monitoring
- **Group by stable properties** (avoid frequently changing session properties)
- **Use role-based filtering** for business-relevant monitoring

### Topic Metric Collectors  
- **Use specific topic selectors** rather than overly broad patterns
- **Test selectors** with topic fetching tools before creating collectors
- **Group by path prefixes** that align with business structure
- **Consider topic update frequency** when setting up monitoring

### Metric Alerts
- **Start conservative** with higher thresholds and refine based on experience
- **Always use DISABLE_UNTIL** for volatile metrics to prevent alert storms
- **Organize alert topics** in a clear hierarchy (e.g., by severity or category)
- **Test alerts** in non-production environments first
- **Monitor alert effectiveness** and adjust thresholds regularly
- **Use meaningful alert names** that clearly indicate the condition
- **Document alert specifications** for team knowledge sharing
- **Subscribe to alert topics** to verify notifications work as expected

This comprehensive metrics guide provides the foundation for effective monitoring, performance analysis, and operational awareness of your Diffusion server infrastructure.
