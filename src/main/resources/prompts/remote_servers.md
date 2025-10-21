# Remote Servers Quick Reference

## What are Remote Servers?
Remote Servers enable **remote topic views** - topic views where source topics come from a different Diffusion cluster. The server hosting the topic views is the **secondary server**, and the server with the actual topics is the **primary server**.

For details of how remote servers are used within topic views see the **Advanced Topic Views Guide** (Use the `get_context` tool with `topic_views_advanced`).

**Important** To try out this feature you will need access to two Diffusion servers (or server clusters), one to act as the primary server (where topics are consumed from), and one to act as the secondary server (where reference topics will be created). For simple SECONDARY_INITIATOR mode, the MCP client only needs to connect to the secondary server.

## Remote Server Management Tools

### Creating Remote Servers
Use `create_remote_server` to configure a remote server:

- **Parameters**: `type` (required), `name` (required), plus type-specific parameters
- Creates a new remote server configuration or returns error if name exists
- **Example**: `create_remote_server type="SECONDARY_INITIATOR" name="primary_cluster" url="ws://primary.example.com:8080"`

### Listing Remote Servers
Use `list_remote_servers` to see all configured remote servers:

- **Parameters**: None
- Returns all remote servers with their configurations
- **Example**: `list_remote_servers`

### Checking Remote Servers
Use `check_remote_server` to check the current state of a remote server:

- **Parameters**: `name` (required)
- Checks the current connection state of a remote server
- **Example**: `list_remote_servers`

### Removing Remote Servers
Use `remove_remote_server` to delete a remote server:

- **Parameters**: `name` (required)
- Removes the configuration; any dependent topic views will be disabled
- **Example**: `check_remote_server name="old_primary"`

## Remote Server Types

### SECONDARY\_INITIATOR (Most Common)
**Use Case**: Secondary server initiates connection to primary server

**Configuration**:

- Define **only on secondary server**
- Connection maintained only when topic views need it
- Automatically distributed across secondary cluster

**Required Parameters**:

- `name` - unique identifier for this remote server
- `url` - WebSocket URL to primary server (e.g., `ws://primary.example.com:8080`)

**Optional Parameters**:

- `principal` - username for authentication (empty string = anonymous)
- `password` - password for authentication
- `connectionOptions` - map of connection settings (see Connection Options below)
- `missingTopicNotificationFilter` - topic selector for propagating subscription notifications

**Example**:

```
create_remote_server 
  type="SECONDARY_INITIATOR" 
  name="primary_prod"
  url="wss://primary.example.com:8080"
  principal="secondary_user"
  password="secure_password"
  connectionOptions={"RECONNECTION_TIMEOUT": "60000", "RETRY_DELAY": "5000"}
```

### PRIMARY\_INITIATOR (Very rarely used)
**Use Case**: Primary server initiates connection to secondary cluster

**Configuration**:

- Define **on primary server**
- Requires matching SECONDARY_ACCEPTOR on secondary server
- Maintains connections even without active topic views
- Single primary server connects to all secondary cluster members
- Only used when inbound connections to primary server are not permitted for business security reasons

**Required Parameters**:

- `name` - must match SECONDARY_ACCEPTOR name on secondary
- `urls` - list of WebSocket URLs to secondary servers
- `connector` - name of connector for establishing connections

**Optional Parameters**:

- `retryDelay` - milliseconds between connection retries (default: 1000)

**Example**:

```
create_remote_server
  type="PRIMARY_INITIATOR"
  name="secondary_cluster"
  urls=["ws://secondary1.example.com:8080", "ws://secondary2.example.com:8080"]
  connector="remote_connector"
  retryDelay=2000
```

### SECONDARY\_ACCEPTOR
**Use Case**: Secondary server accepts connection from primary server

**Configuration**:

- Define **on secondary server**
- Requires matching PRIMARY_INITIATOR on primary server
- Name must match the PRIMARY_INITIATOR name

**Required Parameters**:

- `name` - must match PRIMARY_INITIATOR name on primary
- `primaryHostName` - primary server hostname for SSL validation

**Optional Parameters**:

- `principal` - username for authentication
- `password` - password for authentication
- `connectionOptions` - limited options (see Connection Options below)
- `missingTopicNotificationFilter` - topic selector for notifications

**Example**:

```
create_remote_server
  type="SECONDARY_ACCEPTOR"
  name="secondary_cluster"
  primaryHostName="primary.example.com"
  principal="primary_user"
  password="secure_password"
```

## Connection Options

Connection options are specified as a map of option names to string values.

### Reconnection Options
**Not available for SECONDARY_ACCEPTOR**

- `RECONNECTION_TIMEOUT` - Total time (ms) allowed for reconnection (default: system default)
  - Requires server-side reconnection support
  - Example: `"60000"` (60 seconds)

- `RETRY_DELAY` - Delay (ms) before attempting reconnection after disconnect (default: 1000)
  - Example: `"5000"` (5 seconds)

- `RECOVERY_BUFFER_SIZE` - Number of messages buffered for replay after reconnect (default: 10000)
  - Used to re-send messages server hasn't received
  - Ignored if RECONNECTION_TIMEOUT is 0
  - Example: `"20000"`

### Buffer Size Options
**Available for all types**

- `INPUT_BUFFER_SIZE` - Size of input buffer for receiving messages (default: 1024k)
  - Should match output buffer at remote server
  - Example: `"2048"`

- `OUTPUT_BUFFER_SIZE` - Size of output buffer for sending messages (default: 1024k)
  - Should match input buffer at remote server
  - Example: `"2048"`

### Queue and Timeout Options

- `MAXIMUM_QUEUE_SIZE` - Maximum queued messages before connection closes (default: 10000)
  - Must accommodate messages queued during disconnection
  - Example: `"15000"`

- `CONNECTION_TIMEOUT` - Connection establishment timeout (ms) (default: system default)
  - Example: `"30000"` (30 seconds)

- `WRITE_TIMEOUT` - Write operation timeout (ms) (default: system default)
  - Example: `"10000"` (10 seconds)

## Missing Topic Notification Filter

Controls which subscription notifications propagate from secondary to primary.

**Purpose**: When a client subscribes to a topic selector matching no existing topics, the notification can be propagated to the primary server to potentially create the topic.

**Syntax**: Standard Diffusion topic selector

- Path prefix matching only (regular expressions ignored)
- `"*.*"` - propagate all notifications
- `null` or omitted - propagate no notifications

**Examples**:

- `"?sensors//"` - propagate for all sensor topics
- `"?data/critical/"` - propagate for critical data only
- `"*.*"` - propagate everything

## Common Deployment Patterns

### Pattern 1: Secondary Initiator (Simple)
**When to use**: Standard setup, secondary connects to primary

1. On secondary server:

```
create_remote_server
  type="SECONDARY_INITIATOR"
  name="primary"
  url="ws://primary.example.com:8080"
  principal="remote_user"
  password="password123"
```

2. Create topic views referencing "primary":

This example mirrors the sensors topic tree branch exactly as it is on the primary server which is an efficient duplication mechanism, but any of the features of topic views can be used to map to different structures or transform the primary data.

```
map ?sensors// from primary to <path(0)>
```

### Pattern 2: Primary Initiator (Reverse Connection)
**When to use**: Primary needs to push to multiple secondaries, or firewall rules require primary to initiate

1. On primary server:

```
create_remote_server
  type="PRIMARY_INITIATOR"
  name="primary_cluster"
  urls=["ws://sec1.example.com:8080", "ws://sec2.example.com:8080"]
  connector="outbound_connector"
```

2. On secondary server:

```
create_remote_server
  type="SECONDARY_ACCEPTOR"
  name="primary_cluster"
  primaryHostName="primary.example.com"
```

3. Create topic views on secondary referencing "primary_cluster" in the same way as for the simple pattern.

## Important Notes

### Persistence and Replication
- Remote server configurations are **persisted to disk**
- Configurations are **replicated across cluster** automatically
- Changes on one cluster member apply to all members

### Access Control
- Creating/removing remote servers requires **CONTROL_SERVER** permission
- Listing remote servers requires **VIEW_SERVER** permission

### Topic View Dependencies
- Topic views can reference a remote server **before it exists**
- Connection establishes automatically when remote server is created
- Removing a remote server **disables** all dependent topic views
- For SECONDARY_INITIATOR, connection exists only when topic views need it

### Connection Behaviour
- **SECONDARY\_INITIATOR**: Connection maintained only when topic views active
- **PRIMARY\_INITIATOR/SECONDARY\_ACCEPTOR**: Connection always maintained
- Lost connections trigger automatic reconnection attempts
- Topic views disabled during connection loss, re-enabled on reconnection

## Troubleshooting

### Connection Issues
1. Verify URL format: `ws://` or `wss://` for secure connections
2. Check firewall rules allow connection in correct direction
3. Verify credentials if using authentication
4. Review connection options (timeouts, retry delays)
5. Test connection : `check_remote_server`
6. Check server logs for detailed error messages

### Topic View Issues
1. Verify remote server exists: `list_remote_servers`
2. Check remote server name matches topic view specification
3. Ensure connection is active (PRIMARY_INITIATOR shows CONNECTED state) : `check_remote_server`
4. Verify source topics exist on primary server

### Configuration Issues
1. Name conflicts: Each remote server name must be unique
2. Type mismatch: PRIMARY_INITIATOR and SECONDARY_ACCEPTOR names must match
3. Invalid options: Check connection option names and values
4. Missing required parameters: Each type has specific requirements

## Validation Checklist
- ✅ Correct type for your use case
- ✅ Unique name within your cluster
- ✅ Valid WebSocket URL(s) format
- ✅ Matching names for PRIMARY\_INITIATOR/SECONDARY\_ACCEPTOR pairs
- ✅ Valid connection option names (see Connection Options)
- ✅ Appropriate timeout and buffer values for your network
- ✅ Required permissions granted (CONTROL\_SERVER)

## Quick Examples

### Basic Remote Connection

```
# On secondary
create_remote_server
  type="SECONDARY_INITIATOR"
  name="main_primary"
  url="ws://10.0.1.50:8080"
```

### Secure Connection with Reconnection

```
create_remote_server
  type="SECONDARY_INITIATOR"
  name="prod_primary"
  url="wss://primary.prod.example.com:8443"
  principal="secondary_service"
  password="secure_pass"
  connectionOptions={
    "RECONNECTION_TIMEOUT": "120000",
    "RETRY_DELAY": "10000",
    "RECOVERY_BUFFER_SIZE": "50000"
  }
```

### Reverse Connection Setup

Rarely used.

```
# On primary
create_remote_server
  type="PRIMARY_INITIATOR"
  name="primary"
  urls=["ws://edge1:8080", "ws://edge2:8080"]
  connector="edge_connector"

# On secondary (edge servers)
create_remote_server
  type="SECONDARY_ACCEPTOR"
  name="primary"
  primaryHostName="primary.datacenter.example.com"
```

