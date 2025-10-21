# Topic Views Quick Reference

## What are Topic Views?
Topic Views create **reference topics** from **source topics** using a **topic view specification string**. Reference topics are read-only and automatically updated when source topics change.

Reference topics can themselves be the source topics of other topic views.

Topic views support the mapping of all topic types to other parts of the topic tree, with additional options like delaying or throttling updates. Operations that transform the data within the topics are only available for JSON topics.

Capabilities include:-

* Mapping to other topic tree locations
* Delaying updates
* Throttling updates
* Changing topic properties
* Changing the type of topics
* Mapping to other topics with paths derived from the data (JSON topics only)
* Expanding JSON topics - splitting a JSON structure into multiple topics
* Transforming JSON data, performing calculations or conditional processing based on the content
* Inserting data from other topics into JSON topics
* Apply JSON patch commands to JSON topic data

## Topic View Management Tools

** Important ** Always show a suggested specification and ask the user if they want to create the view before actually creating it.

### Creating Topic Views
Use `create_topic_view` to create a new named topic view:

- **Parameters**: `name` (required), `specification` (required)
- Creates a new view or updates existing view with same name
- **Example**: `create_topic_view name="sensor_dashboard" specification="map ?sensors/.*/temperature to dashboard/temps/<path(1,2)>"`

### Listing Topic Views
Use `list_topic_views` to see all existing topic views:

- **Parameters**: None
- Returns all views with their names, specifications, and roles
- **Example**: `list_topic_views`

### Getting Topic View Details
Use `get_topic_view` to retrieve details of a specific view:

- **Parameters**: `name` (required)
- Returns view specification, roles, and other details
- **Example**: `get_topic_view name="sensor_dashboard"`

### Removing Topic Views
Use `remove_topic_view` to delete a topic view:

- **Parameters**: `name` (required) 
- Removes the view and all its reference topics
- **Example**: `remove_topic_view name="old_view"`

## Topic View Development Workflow

### 1. Design and Test
1. **Understand your data**: Use topic fetching tools to examine source topics
2. **Design specification**: Create topic view specification using DSL syntax
3. **Validate selectors**: Ensure your source selectors match intended topics (see topic selectors guide)

### 2. Create and Deploy
```
# Create the topic view
create_topic_view name="my_view" specification="map ?sensors/ to dashboard/<path(1)>"

# Verify creation
get_topic_view name="my_view"

# Check all views
list_topic_views
```

### 3. Monitor and Maintain
1. **Test reference topics**: Use topic fetching to verify reference topics are created correctly
2. **Update as needed**: Use same create command to update specification
3. **Clean up**: Remove unused views with `remove_topic_view`

## ⚠️ CRITICAL: Specification Format
**Topic view specifications are SINGLE DSL STRINGS, not JSON or other formats!**

❌ **WRONG:** `{"source": "a/b", "expand": {...}}`  
✅ **CORRECT:** `map a/b to split/<expand()>`

## Basic Syntax
```
map <source-selector> to <path-template> [transformations] [options]
```
- The source-selector is a Diffusion topic selector. The topic view will be applied to **every** topic that matches the selector.
- The path template specifies the path of the reference topic that will be created. It specifies path segments which can be string constants or  directives. Directives specify how to derive that path segment and are specified within `<` and `>` as delimiters.

**For complete topic selector documentation, see the topic_selectors context.**

**Example:** `map ?sensors/ to dashboard/<path(1)> throttle to 1 update every 5 seconds`

## Essential Directives

### Path Directives (All topic types)
Extract parts of source topic path:

- `<path(0)>` - entire path
- `<path(1)>` - path from segment 1 onwards  
- `<path(1,2)>` - 2 segments starting from segment 1

### Scalar Directives (JSON only)
Extract values from JSON using JSON Pointers:

- `<scalar(/field)>` - extract field value for path
- `<scalar(/nested/field)>` - extract nested field value

This directive will extract the patch segment from the named scalar field in the source topic. If the source topic has no such field then no reference topic is created.

### Expand Directives (JSON only)  
Create multiple topics from JSON objects/arrays:

- `<expand()>` - expand all properties at root
- `<expand(/array, /id)>` - expand array using id field for paths

## JSON Pointer Syntax
JSON Pointers (RFC 6901) reference parts of JSON documents:

- `/name` - property "name"
- `/users/0/name` - first user's name
- `/balance/amount` - nested property

## Common Patterns

### 1. Split JSON Topic
**Use Case:** Take JSON topic and create separate topics for each property

```
map customer/data to customer/fields/<expand()>
```

Result: If source has `{"name":"John", "age":30}`, creates:

- `customer/fields/name` → `"John"`
- `customer/fields/age` → `30`

### 2. Extract Specific Field
**Use Case:** Create topic using specific JSON field

```
map users to by_name/<scalar(/name)>
```

### 3. Array to Individual Topics
**Use Case:** Split array into separate topics

```
map orders to individual/<expand(/items, /id)>
```

### 4. Simple Mirroring
**Use Case:** Copy topic tree structure

```
map ?source/ to mirror/<path(1)>
```

### 5. Conditional Creation
**Use Case:** Only create topics meeting criteria

```
map events to alerts/<scalar(/id)>
process { if "/severity eq 'critical'" continue }
```

## Basic Transformations

### Process Transformations (JSON only)
```
process { <operations> }
```
**Operations:**

- `set(/field, "value")` - set field to literal value
- `set(/field, /other)` - copy field from input
- `set(/field, calc "/price * 2")` - calculated value
- `remove(/field)` - remove field
- `continue` - proceed unchanged

In calculations **always** use parentheses to make the operator precedence clear. So `/A - (/A * /B / 100)` is not the same as `/A - (/A * (/B / 100))`

**Conditions:**

- `if "/field > 100" operation(s)`
- `if "/status eq 'active'" operation(s) else other_operation(s)`
- `if "/status eq 'active'" operation(s) elseif operation(s) else operation(s)`

In the above examples there can be a single operation or a `;` separated list of operations in each branch.

Only the operations in the satisfied branches are executed. The `continue` operation is useful for filtering without changing the data in any way. For example:-

`if "/status eq 'live'" continue`

### Common Options
- `throttle to 1 update every 5 seconds` - limit update rate
- `delay by 15 minutes` - delay updates
- `type STRING` - convert to different topic type
- `as <value(/field)>` - use only part of processed value

## Quick Examples

### Split JSON Properties
```
map user/profile to user/data/<expand()>
```

### Create Index by Field  
```
map customers to by_country/<scalar(/country)>/customer_<scalar(/id)>
```

### Filter and Transform
```
map transactions to active/<scalar(/id)>
process { 
  if "/status eq 'active'" set(/processed, true)
}
```

### Array Processing
```
map order_batch to orders/<expand(/orders, /order_id)>
process { set(/total, calc "/quantity * /price") }
```

### Throttled Mirror
```
map ?live_data/ to cached/<path(1)>
throttle to 1 update every minute
```
## Precedence and Clashes with Existing Topics
- When more than one topic view maps to the same reference topic the earliest created view takes precedence.
- When a topic view is replaced it retains its place in the order of precedence.
- To change precedence order topic views would need to be removed and recreated.
- Reference topics will never replace a non reference topic.

## Validation Checklist
- ✅ Single string format (not JSON)
- ✅ Starts with `map` keyword
- ✅ Uses topic view DSL syntax (not jq/JavaScript)
- ✅ JSON Pointers start with `/`
- ✅ Directives in angle brackets `<>`
- ✅ Conditions in quotes

## Important: Use Appropriate Topic Selectors
- `?live_data/` - applies the view to every descendant. This may be OK to copy branches but is unlikely to be what you want in other cases, especially if you are using scalar directives which are unlikely to work at every level.
- `?live_data/[^/]+` - would select only the children of live_data and not all descendants.

## Common Mistakes
❌ `map a/b to {"expand": "select(.name)"}` - This is JSON, not DSL  
❌ `map a/b to <select(.name)>` - This is jq syntax, not topic view DSL  
❌ `map a/b to getValue("name")` - This is function syntax, not topic view DSL  
✅ `map a/b to split/<expand(, /name)>` - This is correct topic view DSL

## Need More Detail?
For comprehensive documentation including all directives, transformation types, advanced options, and detailed examples, use the `topic_views_advanced` context.
