# Diffusion MCP Server Getting Started Guide

This guide describe how to get started with the Diffusion MCP Server. 

Other guides are available via the `get_context` tool for more specific usage.

**All tools require properly structured JSON input, using double quotes (") not single or back-ticks!**

## What is Diffusion?
Diffusion is a real-time data streaming platform that uses a publish-subscribe model to distribute data through topics. This MCP server provides tools to connect to, monitor, and interact with Diffusion servers.

## Essential Concepts

### Topics
**Topics** are the core data containers in Diffusion:

- Organised in a hierarchical tree structure (like a file system)
- Use forward slashes for paths: `sensors/temperature/room1`
- Can contain different data types (JSON, STRING, numbers, binary)
- Support real-time updates to subscribers

### Sessions  
**Sessions** represent connected clients:

- Each client connection creates a session with unique properties
- Properties include authentication info, IP addresses, connection details
- Can be filtered and monitored for analysis and metrics
- An MCP client can connect a single Diffusion session to enable other tools

### Topic Views
**Topic Views** create virtual topics from existing ones:

- Transform data in real-time using declarative specifications
- Can split, filter, aggregate, or reshape topic data
- Reference topics are read-only and auto-updated
- Can create topics from source topics on another server, using remote servers

### Session Trees
**Session Trees** allow different mappings to the same topic paths for different sessions based on their session properties:

- Supports personalisation - different views of the same topic paths for different sessions

### Metrics
Comprehensive **Metrics** are provided by the Diffusion server to support monitoring of the system.

### Security
Diffusion provides a powerful **RBAC (Role Based Access Control)** model which enables permissions to be defined which restrict access to topics and all major features.

## Getting Connected

### Basic Connection Workflow
1. **Connect**: Use `connect` tool with server details
2. **Explore**: Discover topics, sessions, or set up monitoring
3. **Work**: Explore Topic Tree, Fetch data, Create and update topics, Analyse sessions, Explore Metrics, Create Topic Views, Create session trees
4. **Disconnect**: Use `disconnect` tool when finished

Sessions automatically disconnect after 5 minutes of inactivity, so you may need to reconnect

### Common Connection Patterns

When used with the out-of-the-box system authentication and security stores the MCP server is best used with the pre-configured `admin` principal.


**Local Development:**

```
connect url="ws://localhost:8080" principal="client" password="password"
```

**Admin Access (required for sessions, metrics, topic views, session trees):**

```
connect url="ws://localhost:8080" principal="admin" password="password"
```

**Quick Defaults:**

- "local connection" → assumes `ws://localhost:8080`
- "default connection" → tries `principal="client", password="password"`
- "admin connection" → tries `principal="admin", password="password"`

**Remote Access or Non Default Credentials:**

- A remote Diffusion server can be accessed via a full URL
- If default credentials do not work, ask the user for his specific principal and password for the server.
- The actions that are possible will be dependent upon the configured permissions for the principal that is specified.

## Core Workflows

### 1. Topic Data Exploration
**Goal:** Understand what data is available and how it's structured

**Quick Start:**

- `fetch_topics depth=1` - See top-level structure - shows tree branches that have topics
- `fetch_topics topicSelector="?interesting_branch//"` - Explore a branch
- `fetch_topic topicPath="specific/topic/path"` - Get a specific topic's value and its properties

**📖 For detailed topic operations:** Use context `type="topics"`

### 2. Session Analysis  
**Goal:** Monitor and analyse connected clients

**Quick Start:**

- `get_sessions` - List all connected session IDs
- `get_sessions filter="$Principal is 'admin'"` - Filter by criteria
- `get_session_details sessionId="abc123"` - Get session properties

**📖 For comprehensive session management:** Use context `type="sessions"`

### 3. Performance Monitoring
**Goal:** Set up monitoring and collect server metrics

**Quick Start:**

- `fetch_metrics` - Get current server metrics
- `create_session_metric_collector` - Monitor specific sessions
- `create_topic_metric_collector` - Monitor topic performance
- `set_metric_alert` - Set up alerts that publish to topics when certain conditions are met

**📖 For complete metrics and monitoring:** Use context `type="metrics"`

### 4. Data Transformation
**Goal:** Create virtual topics that transform existing data using topic views

**Quick Start:**

- Split JSON: `map source_topic to split/<expand()>`
- Mirror structure: `map ?source/ to mirror/<path(1)>`  
- Filter data: `map source to filtered/<path(1)> process { if "/active eq true" continue }`

**📖 For topic view syntax:** Use context `type="topic_views"`

**📖 For advanced topic views:** Use context `type="topic_views_advanced"`

### 5. Session Trees - Personalisation
**Goal:** Create session specific mappings onto the topic tree

**📖 For full details of how to use session trees:** Use context `type="session_trees"`

## Common Use Cases

### Development & Testing
1. Connect to local Diffusion server
2. Explore topic structure to understand data
3. Test topic views with sample data

### Operations & Monitoring  
1. Connect with admin credentials
2. Monitor active sessions and connection patterns
3. Fetch Metrics to query server health
4. Set up metric collectors for key performance indicators
5. Create dashboards using topic views for data aggregation
6. Set up metric alerts

### Data Integration
1. Use topic views to reshape data for different consumers
2. Create secondary indexes using JSON field values
3. Filter and transform data streams in real-time
4. Set up delayed feeds for different user tiers

### Personalisation
1. Create mapping tables that present a view of the topic tree based on a session's properties
2. Show different values for the same topic path to different sessions
3. Example use - different users see different prices
4. Test by reconnecting with session properties as required

## Specialised Context Areas

Before using any specific features use the `get_context` tool to retrieve the appropriate context.

When you need detailed information about specific areas, use these contexts:

### 📖 `type="topics"` - Topic Operations
- Complete topic fetching reference
- Topic tree exploration techniques
- Creating, updating, and removing topics
- Best practices for topic organisation

### 📖 `type="topic_selectors"` - Topic Operations that select multiple topics
- Topic selector syntax and patterns 
- Use of topic selectors when selecting topics to fetch, remove, or as source of topic views

### 📖 `type="sessions"` - Session Management
- Session filtering expressions and examples
- Session property analysis
- Connection pattern monitoring
- Session-based metric collection
- Client behaviour analysis

### 📖 `type="metrics"` - Performance Monitoring  
- Server metrics collection and filtering
- Metric collector setup and management
- Performance analysis patterns
- Monitoring best practices
- Troubleshooting performance issues
- Metric Alerts

### 📖 `type="topic_views"` - Topic Views Quick Reference
- Essential syntax for creating topic view specifications
- Common transformation patterns
- Quick examples and templates
- Format validation checklist

### 📖 `type="topic_views_advanced"` - Advanced Topic Views
- Detailed transformation options
- Advanced directive usage
- Remote topic views and clustering
- Performance optimisation
- Troubleshooting topic view issues

### 📖 `type="session_trees"` - Session Trees
- How to use session trees
- Creating and maintaining branch mapping tables

### 📖 `type="remote_servers"` - Remote Servers
- How to set up remote server connections for use by remote topic views

## Next Steps

1. **Start Simple:** Connect to your server and explore the topic structure
2. **Choose Your Focus:** Pick the area most relevant to your needs
3. **Get Specialised Help:** Use the appropriate context type for detailed guidance
4. **Build Gradually:** Start with basic operations before moving to advanced features

## IMPORTANT

Never remove topics without showing the user the topic selector you are using and double checking that is what they really want to do!

Remember: Most operations require an active connection, and most operations, other than fetching topics, typically need `admin` credentials. Always disconnect when finished to free server resources.
