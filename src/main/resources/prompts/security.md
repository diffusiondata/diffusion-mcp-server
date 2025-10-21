# Diffusion Security Guide

## Understanding Diffusion Security

Diffusion provides a RBAC (Role Based Access Control) model for security purposes.

Diffusion provides two distinct security stores that control authentication and authorisation for server access and operations. Both stores are persistent databases maintained by the server, with changes replicated across cluster members when topic replication is configured.

### Security Store Types

* **Security Store**: Controls authorisation – what authenticated sessions can do (permissions and roles)
* **System Authentication Store**: Controls authentication – who can connect and what roles they receive (principals and credentials)

### Key Security Concepts

* **Roles**: Named groups that bundle permissions together
* **Permissions**: Specific rights to perform actions (global) or access paths (path‑scoped)
* **Principals**: Named users with credentials (passwords) used for authentication
* **Sessions**: Connected clients that are assigned roles based on their principal or anonymous status

### Permissions Scope Types

* **Global Permissions**: Server‑wide actions (e.g., `MODIFY_SECURITY`, `VIEW_SECURITY`, `REGISTER_HANDLER`)
* **Path Permissions**: Hierarchical access control for topics and message paths (e.g., `READ_TOPIC`, `UPDATE_TOPIC`, `SELECT_TOPIC`)

---

## Security Store (Authorisation)

The security store defines roles and their associated permissions, controlling what authenticated sessions are authorised to do.

### Retrieving Security Configuration

**Basic Security Store Query:**

```
get_security
```

Returns complete security configuration including:

* Default roles for anonymous sessions
* Default roles for named (authenticated) sessions
* All defined roles with their permissions
* Isolated paths in the hierarchy

**Understanding the Results:**

```json
{
  "rolesForAnonymousSessions": ["ANONYMOUS", "READ_ONLY"],
  "rolesForNamedSessions": ["AUTHENTICATED"],
  "roles": [
    {
      "name": "ADMIN",
      "globalPermissions": ["MODIFY_SECURITY", "VIEW_SECURITY"],
      "defaultPathPermissions": ["READ_TOPIC", "UPDATE_TOPIC"],
      "pathPermissions": {
        "admin/": ["READ_TOPIC", "UPDATE_TOPIC", "MODIFY_TOPIC"]
      },
      "includedRoles": ["AUTHENTICATED"],
      "lockingPrincipal": ""
    }
  ],
  "isolatedPaths": ["secure/"]
}
```

### Role Structure

**Role Components:**

* `name`: Role identifier referenced in authentication and other roles
* `globalPermissions`: Server‑wide permissions (e.g., `MODIFY_SECURITY`, `REGISTER_HANDLER`)
* `defaultPathPermissions`: Permissions applied to all paths unless overridden
* `pathPermissions`: Specific permissions for particular path branches
* `includedRoles`: Other roles whose permissions this role inherits
* `lockingPrincipal`: If set, only this principal can modify the role

### Permission Evaluation

**Global Permission Evaluation:**
A session has a global permission if any of its roles are assigned that permission.

**Path Permission Evaluation:**
Path permissions are evaluated hierarchically for each of a session's roles:

1. **Direct assignment**: If the path has permission assignments for any of the session's roles, those permissions apply
2. **Inheritance**: If no direct assignment and path is not isolated, permissions inherit from the closest parent path with assignments
3. **Default permissions**: If no path or parent has assignments, default path permissions apply
4. **No permissions**: If none of the above match, the session has no permissions for that path

**Path Isolation:**
Isolated paths do not inherit permissions from parent paths. This creates security boundaries in the path hierarchy.

### Common Security Store Patterns

**Read‑Only External Users:**

```json
{
  "name": "EXTERNAL_USER",
  "globalPermissions": [],
  "defaultPathPermissions": ["READ_TOPIC", "SELECT_TOPIC"],
  "pathPermissions": {},
  "includedRoles": []
}
```

**Regional Administrators:**

```json
{
  "name": "REGION_ADMIN",
  "globalPermissions": ["VIEW_SECURITY"],
  "defaultPathPermissions": ["READ_TOPIC"],
  "pathPermissions": {
    "regions/europe/": ["READ_TOPIC", "UPDATE_TOPIC", "MODIFY_TOPIC"]
  },
  "includedRoles": ["AUTHENTICATED"]
}
```

**Service Accounts with Specific Access:**

```json
{
  "name": "PRICING_SERVICE",
  "globalPermissions": ["REGISTER_HANDLER"],
  "defaultPathPermissions": [],
  "pathPermissions": {
    "prices/": ["READ_TOPIC", "UPDATE_TOPIC"]
  },
  "includedRoles": []
}
```

---

## System Authentication Store

The system authentication store defines principals (users) and their credentials, controlling who can connect and what roles they receive upon authentication.

### Retrieving System Authentication Configuration

**Basic System Authentication Query:**

```
get_system_authentication
```

Returns complete authentication configuration including:

* All defined principals with their assigned roles
* Anonymous connection policy (`ALLOW`, `DENY`, or `ABSTAIN`)
* Roles assigned to anonymous sessions (if allowed)
* Trusted client‑proposed session properties

**Understanding the Results:**

```json
{
  "principals": [
    {
      "name": "admin",
      "assignedRoles": ["ADMIN", "AUTHENTICATED"],
      "lockingPrincipal": "admin"
    },
    {
      "name": "service_account",
      "assignedRoles": ["PRICING_SERVICE"],
      "lockingPrincipal": ""
    }
  ],
  "anonymousAction": "ALLOW",
  "rolesForAnonymousSessions": ["ANONYMOUS"],
  "trustedClientProposedProperties": {
    "USER_TIER": {
      "type": "values",
      "values": ["premium", "standard", "basic"]
    },
    "DEPARTMENT": {
      "type": "regex",
      "regex": "^(sales|engineering|support)$"
    }
  }
}
```

### Principal Structure

**Principal Components:**

* `name`: Principal identifier used during authentication
* `assignedRoles`: Roles granted to sessions authenticated as this principal
* `lockingPrincipal`: If set, only this principal can modify the principal's configuration

**Principal Locking:**
Locked principals can only be modified by the specified locking principal, providing administrative segregation and preventing unauthorized changes.

### Anonymous Connection Policies

**ALLOW:**

* Anonymous connections are permitted
* Sessions receive roles specified in `rolesForAnonymousSessions`
* Useful for public‑facing applications with read‑only access

**DENY:**

* Anonymous connections are rejected
* All clients must provide valid credentials
* Appropriate for secure enterprise applications

**ABSTAIN:**

* System authentication handler defers decision to subsequent handlers
* Allows custom authentication logic via authentication handlers
* Advanced use case for complex authentication flows

### Trusted Client‑Proposed Properties

Clients can propose session properties during connection. The system authentication store can be configured to trust specific properties with validation rules.

**Why Trust Client Properties?**

* Enable session routing based on client characteristics
* Support session filtering and metric collection by client properties
* Maintain security by validating proposed values

**Validation Types:**

**Values‑Based Validation:**

```json
{
  "USER_TIER": {
    "type": "values",
    "values": ["premium", "standard", "basic"]
  }
}
```

Client must propose one of the allowed values exactly.

**Regex‑Based Validation:**

```json
{
  "DEPARTMENT": {
    "type": "regex",
    "regex": "^(sales|engineering|support)$"
  }
}
```

Client's proposed value must match the regular expression.

### Managing Trusted Properties

**Adding Trusted Property:**

```
trust_client_proposed_property
  propertyName="USER_TIER"
  allowedValues=["premium", "standard", "basic"]
```

This configures the system authentication store to accept and trust the `USER_TIER` property if clients propose one of the allowed values.

**Removing Trusted Property:**

```
ignore_client_proposed_property
  propertyName="USER_TIER"
```

This removes the trust configuration for a property, preventing clients from proposing values for it. Use this to:

* Remove properties that are no longer needed
* Update validation rules (ignore the old property, then trust with new rules)
* Tighten security by reducing accepted client properties

**Use Cases:**

* Session routing in session trees based on user tier
* Metric collection grouped by department
* Session filtering for administrative operations
* Business logic based on validated client attributes

---

## Using Security Update Tools

### System Authentication Store Operations

#### Managing Principals

**Adding a Principal:**

```
add_principal
  principalName="alice"
  password="secure_password_123"
  roles=["TRADER", "AUTHENTICATED"]
```

**Adding a Locked Principal:**

```
add_principal
  principalName="admin_account"
  password="admin_password"
  roles=["ADMIN"]
  lockingPrincipal="super_admin"
```

This creates a principal that can only be modified by sessions authenticated as "super_admin".

**Updating a Principal's Password:**

```
set_principal_password
  principalName="alice"
  password="new_secure_password_456"
```

**Changing Principal Roles:**

```
assign_principal_roles
  principalName="alice"
  roles=["SENIOR_TRADER", "AUTHENTICATED", "REPORTING"]
```

This replaces all existing roles with the new set.

**Removing a Principal:**

```
remove_principal
  principalName="old_service_account"
```

#### Configuring Anonymous Access

**Allow Anonymous Connections:**

```
set_anonymous_connection_policy
  action="allow"
  roles=["GUEST", "READ_ONLY"]
```

**Deny Anonymous Connections:**

```
set_anonymous_connection_policy
  action="deny"
```

**Abstain from Anonymous Decisions:**

```
set_anonymous_connection_policy
  action="abstain"
```

Use abstain when custom authentication handlers will make the decision.

### Security Store Operations

**WARNING** Setting global or path permissions replaces anything that was there before, so if you want add a permission you need to get what was there before first and add to it!

#### Setting Default Session Roles

**Configure Anonymous Session Roles:**

```
set_roles_for_anonymous_sessions
  roles=["GUEST", "PUBLIC_READ"]
```

**Configure Named Session Roles:**

```
set_roles_for_named_sessions
  roles=["AUTHENTICATED", "BASIC_ACCESS"]
```

These roles are assigned in addition to any principal‑specific roles.

#### Configuring Role Permissions

**Setting Global Permissions:**

```
set_role_global_permissions
  roleName="ADMIN"
  permissions=["VIEW_SECURITY", "MODIFY_SECURITY", "VIEW_SESSION", "MODIFY_SESSION"]
```

Valid global permissions include:

* `VIEW_SECURITY` – Query security stores
* `MODIFY_SECURITY` – Update security stores
* `VIEW_SESSION` – View session information
* `MODIFY_SESSION` – Close sessions, modify properties
* `REGISTER_HANDLER` – Register various request handlers
* `AUTHENTICATE` – Authenticate sessions
* `VIEW_SERVER` – View server runtime state/metrics
* `CONTROL_SERVER` – Change server runtime state
* `READ_TOPIC_VIEWS` – Read topic views
* `MODIFY_TOPIC_VIEWS` – Create and modify topic views

**Setting Default Path Permissions:**

```
set_role_default_path_permissions
  roleName="TRADER"
  permissions=["READ_TOPIC", "SELECT_TOPIC"]
```

These apply to all paths unless overridden.

**Setting Specific Path Permissions:**

```
set_role_path_permissions
  roleName="TRADER"
  path="markets/forex/"
  permissions=["READ_TOPIC", "UPDATE_TOPIC", "SELECT_TOPIC"]
```

Valid path permissions include:

* `READ_TOPIC` – Subscribe to or fetch topics
* `UPDATE_TOPIC` – Publish updates
* `SELECT_TOPIC` – Use a topic selector that selects a topic path
* `MODIFY_TOPIC` – Change topic specifications / add or remove topics
* `SEND_TO_MESSAGE_HANDLER` – Send to message handlers
* `SEND_TO_SESSION` – Send messages to a client session
* `QUERY_OBSOLETE_TIME_SERIES_EVENTS` – Query non‑current time series data
* `EDIT_TIME_SERIES_EVENTS` – Edit time series events
* `EDIT_OWN_TIME_SERIES_EVENTS` – Edit own time series events
* `ACQUIRE_LOCK` – Acquire session locks (path‑scoped)
* `EXPOSE_BRANCH` – Expose a branch as a virtual session tree

**Removing Path Permissions:**

```
remove_role_path_permissions
  roleName="TRADER"
  path="markets/commodities/"
```

This allows the role to inherit from parent paths or defaults.

#### Managing Role Hierarchy

**Setting Role Includes:**

```
set_role_includes
  roleName="SENIOR_TRADER"
  includedRoles=["TRADER", "AUTHENTICATED"]
```

`SENIOR_TRADER` now inherits all permissions from `TRADER` and `AUTHENTICATED`.

**Creating Multi‑Level Hierarchy:**

```
# Base role
set_role_global_permissions
  roleName="AUTHENTICATED"
  permissions=[]

set_role_default_path_permissions
  roleName="AUTHENTICATED"
  permissions=["READ_TOPIC"]

# Mid‑level role
set_role_includes
  roleName="TRADER"
  includedRoles=["AUTHENTICATED"]

set_role_path_permissions
  roleName="TRADER"
  path="markets/"
  permissions=["READ_TOPIC", "UPDATE_TOPIC"]

# Senior role
set_role_includes
  roleName="SENIOR_TRADER"
  includedRoles=["TRADER"]

set_role_global_permissions
  roleName="SENIOR_TRADER"
  permissions=["VIEW_SESSION"]
```

#### Managing Path Isolation

**Isolating Sensitive Paths:**

```
isolate_path
  path="admin/"
```

Now "admin/" and its descendants don't inherit from parent permissions.

**Removing Isolation:**

```
deisolate_path
  path="admin/"
```

#### Locking Roles

**Lock Critical Roles:**

```
lock_role_to_principal
  roleName="ADMIN"
  principalName="super_admin"
```

Only sessions authenticated as "super_admin" can now modify the `ADMIN` role.

### Initial Security Assessment

1. **Connect with admin credentials**: Both security stores require `VIEW_SECURITY` permission
2. **Review security store**: `get_security` to understand current authorization configuration
3. **Review authentication store**: `get_system_authentication` to understand authentication configuration
4. **Identify gaps**: Determine if current configuration meets security requirements

### Planning Security Changes

1. **Define roles hierarchy**: Plan role structure and inheritance relationships
2. **Map permissions**: Assign appropriate global and path permissions to roles
3. **Plan principal access**: Determine which principals need what roles
4. **Consider path isolation**: Identify security boundaries requiring isolated paths
5. **Plan client properties**: Determine which session properties need validation

### Security Store Updates (Available Now)

**Default Role Assignment:**

* `set_roles_for_anonymous_sessions`: Configure default roles for anonymous sessions
* `set_roles_for_named_sessions`: Configure default roles for authenticated sessions

**Role Permissions:**

* `set_role_global_permissions`: Assign global permissions to a role
* `set_role_default_path_permissions`: Set default path permissions for a role
* `set_role_path_permissions`: Assign permissions for specific paths
* `remove_role_path_permissions`: Remove path permission assignments

**Role Hierarchy:**

* `set_role_includes`: Configure which roles are included in other roles

**Path Isolation:**

* `isolate_path`: Mark paths to prevent permission inheritance
* `deisolate_path`: Remove path isolation

**Role Security:**

* `lock_role_to_principal`: Lock roles so only specific principals can modify them

### System Authentication Updates

**Principal Management:**

* `add_principal`: Create new principals with passwords, roles, and optional locking
* `remove_principal`: Delete existing principals from the store
* `set_principal_password`: Update a principal's password
* `assign_principal_roles`: Change the roles assigned to a principal

**Anonymous Connection Policy:**

* `set_anonymous_connection_policy`: Configure allow/deny/abstain with optional roles

**Client Property Trust:**

* `trust_client_proposed_property`: Add trusted client properties with value‑based validation
* `ignore_client_proposed_property`: Remove trust configuration for client properties

---

## Common Security Scenarios

### Gaming Applications

**Public Game Access with Premium Features:**

Security Store:

```json
{
  "rolesForAnonymousSessions": ["GUEST_PLAYER"],
  "rolesForNamedSessions": ["REGISTERED_PLAYER"],
  "roles": [
    {
      "name": "GUEST_PLAYER",
      "defaultPathPermissions": ["READ_TOPIC", "SELECT_TOPIC"],
      "pathPermissions": {
        "games/public/": ["READ_TOPIC", "UPDATE_TOPIC"]
      }
    },
    {
      "name": "PREMIUM_PLAYER",
      "defaultPathPermissions": ["READ_TOPIC", "SELECT_TOPIC"],
      "pathPermissions": {
        "games/": ["READ_TOPIC", "UPDATE_TOPIC"],
        "leaderboards/": ["READ_TOPIC", "UPDATE_TOPIC"]
      },
      "includedRoles": ["REGISTERED_PLAYER"]
    }
  ]
}
```

System Authentication:

```json
{
  "anonymousAction": "ALLOW",
  "rolesForAnonymousSessions": ["GUEST_PLAYER"],
  "trustedClientProposedProperties": {
    "GAME_REGION": {
      "type": "values",
      "values": ["NA", "EU", "ASIA", "LATAM"]
    }
  }
}
```

### Financial Applications

**Segregated Trading Access:**

Security Store:

```json
{
  "roles": [
    {
      "name": "TRADER",
      "globalPermissions": [],
      "defaultPathPermissions": [],
      "pathPermissions": {
        "markets/": ["READ_TOPIC", "SELECT_TOPIC"],
        "orders/": ["READ_TOPIC", "UPDATE_TOPIC", "SEND_TO_MESSAGE_HANDLER"]
      }
    },
    {
      "name": "MARKET_DATA_ADMIN",
      "globalPermissions": ["VIEW_SECURITY"],
      "pathPermissions": {
        "markets/": ["READ_TOPIC", "UPDATE_TOPIC", "MODIFY_TOPIC"]
      },
      "lockingPrincipal": "system_admin"
    }
  ],
  "isolatedPaths": ["orders/"]
}
```

System Authentication:

```json
{
  "anonymousAction": "DENY",
  "principals": [
    {
      "name": "trader_alice",
      "assignedRoles": ["TRADER"],
      "lockingPrincipal": "compliance_officer"
    }
  ],
  "trustedClientProposedProperties": {
    "TRADING_DESK": {
      "type": "regex",
      "regex": "^[A-Z]{2,4}$"
    }
  }
}
```

### E‑commerce Applications

**Customer Tier‑Based Access:**

Security Store:

```json
{
  "roles": [
    {
      "name": "CUSTOMER",
      "defaultPathPermissions": ["READ_TOPIC"],
      "pathPermissions": {
        "products/": ["READ_TOPIC", "SELECT_TOPIC"],
        "promotions/public/": ["READ_TOPIC"]
      }
    },
    {
      "name": "VIP_CUSTOMER",
      "pathPermissions": {
        "promotions/vip/": ["READ_TOPIC"]
      },
      "includedRoles": ["CUSTOMER"]
    }
  ]
}
```

System Authentication:

```json
{
  "trustedClientProposedProperties": {
    "CUSTOMER_TIER": {
      "type": "values",
      "values": ["standard", "vip", "enterprise"]
    },
    "REGION": {
      "type": "values",
      "values": ["US", "EU", "APAC"]
    }
  }
}
```

---

## Security Best Practices

### Role Design

* **Use role hierarchy**: Leverage `includedRoles` to avoid permission duplication
* **Principle of least privilege**: Grant only necessary permissions
* **Separate concerns**: Create distinct roles for different functions
* **Name roles clearly**: Use descriptive names that reflect business purpose

### Permission Assignment

* **Start restrictive**: Begin with minimal permissions and add as needed
* **Use default path permissions**: Set baseline access, override specific paths
* **Isolate sensitive paths**: Use path isolation for security boundaries
* **Review regularly**: Audit permissions to ensure they remain appropriate

### Principal Management

* **Lock sensitive principals**: Use principal locking for administrative accounts
* **Rotate credentials**: Regularly update passwords for service accounts
* **Minimal role assignment**: Assign only necessary roles to each principal
* **Service account segregation**: Use dedicated principals for automated systems

### Client‑Proposed Properties

* **Validate strictly**: Use specific allowed values or tight regex patterns
* **Limit properties**: Only trust properties needed for routing or filtering
* **Document validation**: Clearly communicate validation rules to client developers
* **Monitor usage**: Track which properties are being proposed and accepted

### Security Monitoring

* **Regular audits**: Periodically review security and authentication configurations
* **Track changes**: Monitor who makes security store updates and when
* **Test access**: Verify that permissions work as intended
* **Document policies**: Maintain clear documentation of security decisions

---

## Troubleshooting Security Issues

### Cannot Retrieve Security Configuration

1. **Verify permissions**: Tools require `VIEW_SECURITY` permission
2. **Check connection**: Ensure session is connected to server
3. **Confirm authentication**: Verify you're authenticated with appropriate principal
4. **Review error messages**: Check for specific permission or connection errors

### Cannot Update Security Configuration

1. **Verify permissions**: Update tools require `MODIFY_SECURITY` permission
2. **Check principal locks**: Locked principals/roles can only be modified by locking principal
3. **Review script errors**: Check error messages for specific validation failures
4. **Confirm entity exists**: Many operations require entities to exist (e.g., principals, roles)

### Update Operation Failures

**Principal Already Exists:**

```
Error: Principal 'alice' already exists
```

Use `set_principal_password` or `assign_principal_roles` instead of `add_principal`.

**Principal Not Found:**

```
Error: Principal 'bob' does not exist
```

Use `add_principal` to create the principal first.

**Invalid Permission Names:**

```
Error: Invalid global permission name: INVALID_PERM
```

Check the valid permission lists in the tool documentation. Permission names are case‑insensitive but must be valid enum values.

**Locked Role/Principal Modification:**

```
Error: Role 'ADMIN' is locked by principal 'super_admin'
```

You must be authenticated as the locking principal to modify locked entities.

### Unexpected Permission Denials

1. **Review role assignments**: Use `get_system_authentication` to check principal's roles
2. **Check permission inheritance**: Verify role includes and path inheritance
3. **Look for path isolation**: Isolated paths may block expected inheritance
4. **Test global vs path permissions**: Ensure you're checking the right permission scope
5. **Review role hierarchy**: Check which roles are included in the session's roles

### Client Properties Not Working

1. **Verify trust configuration**: Check `trustedClientProposedProperties` in authentication store
2. **Validate proposed values**: Ensure client proposes values matching validation rules
3. **Check property names**: Property names are case‑sensitive and must match exactly
4. **Review regex patterns**: Test regex validation rules independently
5. **Use ignore then trust**: To update validation, use `ignore_client_proposed_property` then `trust_client_proposed_property`

### Anonymous Connection Issues

1. **Check anonymous action**: Verify system authentication store has correct action (`ALLOW`/`DENY`/`ABSTAIN`)
2. **Review anonymous roles**: Check both system authentication and security store anonymous roles
3. **Test permissions**: Verify anonymous roles have appropriate permissions
4. **Consider custom handlers**: `ABSTAIN` may require custom authentication handler

### Path Permission Inheritance Problems

1. **Check for isolation**: Use `get_security` to see isolated paths
2. **Review permission hierarchy**: Trace from specific path up to root
3. **Verify role includes**: Ensure role hierarchy is configured correctly
4. **Test with direct assignments**: Assign permissions directly to troubleshoot inheritance

---

## Integration with Other Features

### Session Management

* Security roles control which sessions can be viewed and managed
* Use `get_sessions` with session filters to find sessions by role
* Principal information available through session properties

### Topic Operations

* Path permissions control topic access throughout hierarchy
* Use path isolation to create permission boundaries
* Topic views respect path permissions of the session

### Metrics and Monitoring

* Create session metric collectors grouped by roles
* Monitor security‑related session properties
* Track authentication patterns and access patterns

### Message Handling

* Message handlers require appropriate global permissions (`REGISTER_HANDLER`)
* Message paths respect path permission hierarchy
* Use path permissions to control message routing

---

This comprehensive security guide provides the foundation for understanding and managing authentication and authorisation in your Diffusion server infrastructure. As update tools become available, this guide will be extended to cover security store modification operations.
