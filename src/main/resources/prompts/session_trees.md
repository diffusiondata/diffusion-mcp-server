# Session Trees Quick Reference

## What are Session Trees?
Session Trees create **virtual views** of the topic tree presented to sessions by fetch and subscription operations. They allow different sessions to see customized topic tree structures based on their session properties, enabling data security, optimisation, and personalisation.

## How Session Trees Work
- **Session Paths**: Virtual paths that sessions see and use for subscriptions/fetches
- **Topic Paths**: Actual paths where topics are stored in the server's topic tree
- **Branch Mappings**: Rules that map session paths to topic paths based on session filters
- **Branch Mapping Tables**: Ordered lists of branch mappings for a specific session tree branch

## Security Configuration Tools

### Trusting Client Proposed Properties
Use `trust_client_proposed_property` to allow clients to propose specific session properties:

- **Parameters**: `propertyName` (required), `allowedValues` (required array)
- **Required for user-defined properties**: Must trust properties before using them in branch mappings
- **Validates values**: Only listed values will be accepted from clients
- **Example**: `trust_client_proposed_property propertyName="USER_TIER" allowedValues=["premium","standard","basic"]`
- **Warning**: This will only work if the system authentication store is in use at the Diffusion server (default for out-of-the-box server configuration)

### Ignoring Client Proposed Properties  
Use `ignore_client_proposed_property` to remove trust for a property:

- **Parameters**: `propertyName` (required)
- **Security cleanup**: Prevents clients from proposing unused properties
- **Best practice**: Always remove trust when properties are no longer needed
- **Example**: `ignore_client_proposed_property propertyName="USER_TIER"`

## Session Tree Management Tools

### Creating/Updating Branch Mapping Tables
Use `put_branch_mapping_table` to create or replace a branch mapping table:

- **Parameters**: `sessionTreeBranch` (required), `branchMappings` (required array)
- Creates new table or replaces existing table with same session tree branch
- **Example**: See examples below

### Getting Branch Mapping Tables
Use `get_branch_mapping_table` to retrieve a specific table:

- **Parameters**: `sessionTreeBranch` (required)
- Returns complete table with all branch mappings
- Returns empty table if no mappings exist
- **Example**: `get_branch_mapping_table sessionTreeBranch="market/prices"`

### Listing Session Tree Branches
Use `list_session_tree_branches` to see all configured branches:

- **Parameters**: None
- Returns all session tree branches that have mapping tables
- Only shows branches you have READ_TOPIC permission for
- **Example**: `list_session_tree_branches`

### Removing Branch Mapping Tables
Use `remove_branch_mapping_table` to delete a table:

- **Parameters**: `sessionTreeBranch` (required)
- Removes all mappings for the specified branch
- Sessions will use identical topic paths after removal
- **Example**: `remove_branch_mapping_table sessionTreeBranch="market/prices"`

## Session Tree Development Workflow

### 1. Design Your Mapping Strategy
1. **Identify session tree structure**: What paths should sessions see?
2. **Map to topic tree**: Where is the actual data stored?
3. **Define session filters**: Which sessions get which mappings? (see session filters guide)

### 2. Create and Test
```
# Create mapping table
put_branch_mapping_table 
  sessionTreeBranch="market/prices"
  branchMappings=[
    {sessionFilter="USER_TIER is '1'", topicTreeBranch="backend/premium_prices"},
    {sessionFilter="$Principal is ''", topicTreeBranch="backend/delayed_prices"}
  ]

# Verify creation
get_branch_mapping_table sessionTreeBranch="market/prices"

# List all branches
list_session_tree_branches
```

### 3. Monitor and Maintain
1. **Test with different session types**: Verify correct mappings apply
2. **Update as needed**: Use same put command to modify mappings
3. **Clean up**: Remove unused tables with `remove_branch_mapping_table`

## Branch Mapping Format

### Input Structure
```json
{
  "sessionTreeBranch": "market/prices",
  "branchMappings": [
    {
      "sessionFilter": "USER_TIER is '1' or $Country is 'DE'",
      "topicTreeBranch": "backend/discounted_prices"
    },
    {
      "sessionFilter": "USER_TIER is '2'", 
      "topicTreeBranch": "backend/standard_prices"
    },
    {
      "sessionFilter": "$Principal is ''",
      "topicTreeBranch": "backend/delayed_prices"
    }
  ]
}
```

### Key Concepts
- **Session Tree Branch**: The virtual path prefix sessions will use
- **Session Filter**: Condition that determines if mapping applies to a session
- **Topic Tree Branch**: The actual topic path where data is stored
- **Order Matters**: First matching filter wins for each session

## Common Patterns

### 1. User Tier-Based Access
**Use Case:** Different data quality based on subscription level

```json
{
  "sessionTreeBranch": "market/data",
  "branchMappings": [
    {
      "sessionFilter": "USER_TIER is 'premium'",
      "topicTreeBranch": "live/market_data"
    },
    {
      "sessionFilter": "USER_TIER is 'standard'", 
      "topicTreeBranch": "delayed/market_data"
    },
    {
      "sessionFilter": "$Principal is ''",
      "topicTreeBranch": "public/market_data"
    }
  ]
}
```

### 2. Geographic Data Routing
**Use Case:** Route to regional data centers

```json
{
  "sessionTreeBranch": "regional/news",
  "branchMappings": [
    {
      "sessionFilter": "$Country is 'US'",
      "topicTreeBranch": "americas/news_feed"
    },
    {
      "sessionFilter": "$Country is 'DE' or $Country is 'FR'",
      "topicTreeBranch": "europe/news_feed" 
    },
    {
      "sessionFilter": "true",
      "topicTreeBranch": "global/news_feed"
    }
  ]
}
```

### 3. Department-Based Views
**Use Case:** Show different data to different departments

```json
{
  "sessionTreeBranch": "company/metrics",
  "branchMappings": [
    {
      "sessionFilter": "DEPARTMENT is 'finance'",
      "topicTreeBranch": "internal/financial_metrics"
    },
    {
      "sessionFilter": "DEPARTMENT is 'sales'",
      "topicTreeBranch": "internal/sales_metrics"
    },
    {
      "sessionFilter": "ROLE is 'executive'",
      "topicTreeBranch": "internal/all_metrics"
    }
  ]
}
```

### 4. Development Environment Routing
**Use Case:** Route test sessions to test data

```json
{
  "sessionTreeBranch": "app/data",
  "branchMappings": [
    {
      "sessionFilter": "ENVIRONMENT is 'test'",
      "topicTreeBranch": "test_data/app"
    },
    {
      "sessionFilter": "ENVIRONMENT is 'staging'", 
      "topicTreeBranch": "staging_data/app"
    },
    {
      "sessionFilter": "true",
      "topicTreeBranch": "production_data/app"
    }
  ]
}
```

## How Mappings Are Applied

### Resolution Process

1. Session subscribes to session path (e.g., `market/prices/EURUSD`)
2. Server finds branch mapping tables with prefixes of the session path
3. For each table, server checks session filters in order
4. First matching filter determines the topic tree branch
5. Session path suffix is appended to topic tree branch
6. Result: `backend/premium_prices/EURUSD` (for premium user)

### Example Resolution
Given session tree branch `market/prices` and session path `market/prices/EURUSD`:

- **Premium user** → `backend/premium_prices/EURUSD`
- **Anonymous user** → `backend/delayed_prices/EURUSD` 
- **No matching filter** → `market/prices/EURUSD` (identical path)

## Access Control

### Required Permissions
- **Create/Update**: `MODIFY_TOPIC` for session tree branch + `EXPOSE_BRANCH` for all topic tree branches
- **Retrieve**: `READ_TOPIC` for session tree branch
- **Subscribe/Fetch**: `SELECT_TOPIC` or `READ_TOPIC` for **session path** (not topic path)

### Security Benefits
- Sessions only need permissions for session paths they see
- No permissions needed for underlying topic paths
- Enables data hiding and access control simplification

## Integration with Topic Views
Session Trees work well with Topic Views:

- **Topic Views**: Transform and aggregate data
- **Session Trees**: Route sessions to appropriate data
- **Combined**: Route sessions to personalized, transformed data

```
Source Data → Topic Views → Processed Data → Session Trees → Session Views
```

## Precedence Rules
When multiple tables could apply to a session path:

1. **Longest prefix wins**: Most specific session tree branch takes precedence
2. **Within table**: First matching session filter wins
3. **No match**: Session path maps to identical topic path

## Validation Checklist
- ✅ Session tree branch is valid path format
- ✅ Topic tree branches are valid path formats  
- ✅ Session filters use proper syntax (see session filters guide)
- ✅ Mappings are ordered from most specific to most general
- ✅ Default/catch-all mapping exists (often `"true"` filter)
- ✅ Required permissions are granted

## Common Mistakes

❌ Wrong filter syntax: `USER_TIER == 'premium'` (use single `=`)

❌ Missing quotes: `USER_TIER is premium` (should be `'premium'`)

❌ No default mapping: Can leave some sessions without access

❌ Wrong order: General filters before specific ones

✅ Correct: Specific filters first, general filters last

## Troubleshooting
- **No data received**: Check session properties match filters
- **Wrong data**: Verify filter order and syntax
- **Permission errors**: Ensure session has rights to session paths (not topic paths)
- **Precedence issues**: Check for overlapping session tree branches

## Need More Detail?
For comprehensive session filter syntax and advanced mapping strategies, see the `sessions` context.
