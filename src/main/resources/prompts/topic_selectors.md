# Diffusion Topic Selectors Guide

## What are Topic Selectors?

Topic selectors are patterns that allow you to match multiple topics efficiently. They're used in:

- **Topic fetching** (`fetch_topics`)
- **Topic removal** (`remove_topics`)
- **Topic view specifications** (source selectors in `map` statements)

## Topic Path Rules (Foundation)

Before using selectors, understand topic path structure:

- Hierarchical organisation using `/` separators
- Case-sensitive paths
- No level element can be empty
- Never start a path with `/`
- Never end a path with `/` unless using descendant qualifiers
- Example: `sensors/environmental/temperature/building_a/floor_2/room_201`

## Selector Types
A selector starts with `>`, `?`, `*`, or `#`. If none of these, it is a path selector.

### 1. Path Selector (Default)
**Syntax:** `path/to/topic` or `>path/to/topic`  
**Matches:** Only the topic at the specified exact path  
**Use Case:** When you know the exact topic path  

**Examples:**

```
sensors/temperature          # Only this exact topic
>markets/forex/EURUSD       # Explicit path selector (same as above)
```

### 2. Split Path Selector (Wildcard)
**Syntax:** `?path/regex/subtopic` - uses Java regular expressions for each path level  
**Matches:** Topics where each Java regex pattern matches the corresponding path level  
**Use Case:** Pattern matching across path hierarchies using Java Pattern syntax

**Java Regex Patterns:**

- `.*` - matches any characters (including none)
- `.+` - matches one or more characters  
- `[^/]+` - matches one or more characters that are NOT forward slashes (useful for direct children only)
- `\d+` - matches one or more digits
- `[a-zA-Z]+` - matches one or more letters
- `(pattern1|pattern2)` - matches either pattern1 OR pattern2

**Examples:**

```
?sensors/.*/temperature      # All temperature sensors regardless of location
?data/.*/daily              # All daily data topics  
?sensors/[^/]+/temperature  # Temperature sensors exactly one level under sensors (direct children only)
?buildings/floor\d+/.*      # All topics under numbered floors (floor1, floor2, etc.)
?data/\d{4}/\d{2}/.*        # Date patterns like data/2024/01/anything
?(building1|building2)/.*/temperature  # Temperature in specific buildings only
```


**⚠️ CRITICAL:** Uses Java regex syntax, NOT shell-style wildcards

- ❌ **WRONG:** `?path/*/subtopic` (shell wildcards don't work)
- ✅ **CORRECT:** `?path/.*/subtopic` (Java regex wildcards)
- ❌ **WRONG:** `?path/[0-9]/subtopic` (POSIX character class)
- ✅ **CORRECT:** `?path/\d/subtopic` (Java regex digit class)

### 3. Full Path Selector (Regex)
**Syntax:** `*java_regex_pattern` - single Java regex applied to the entire topic path  
**Matches:** Topics where the Java regex matches the complete path  
**Use Case:** Complex pattern matching across the full path using Java Pattern syntax

**Examples:**

```
*sensor.*temperature.*      # Any path containing "sensor" then "temperature"  
*data/\d{4}/\d{2}/.*       # Paths with year/month date patterns (2024/01/...)
*building[12]/floor\d+/.*   # Paths in building1 or building2, any numbered floor
```

### 4. Selector Set (Multiple)
**Syntax:** `#selector1////selector2////selector3`  
**Matches:** Topics that match ANY of the listed selectors (OR logic)  
**Use Case:** Combining multiple different selector patterns  

**Examples:**

```
#>sensors/temperature////>sensors/humidity           # Both temperature AND humidity sensors
#?data/.*/daily////data/.*/hourly                   # All daily OR hourly data topics  
#*sensor.*////markets/forex/.*                      # All sensors OR forex topics
```

## Descendant Pattern Qualifiers

Add qualifiers to split path (`?`) or full path (`*`) selectors:

### Single Slash `/` - Descendants Only
**Syntax:** `?path/` or `*pattern/`  
**Matches:** All descendants of selected topics (but NOT the selected topics themselves)  

**Examples:**

```
?sensors/                   # All topics under sensors (not sensors itself)
?games/.*/scores/          # All descendants of score topics
```

### Double Slash `//` - Topics AND Descendants  
**Syntax:** `?path//` or `*pattern//`  
**Matches:** Both the selected topics AND all their descendants  

**Examples:**

```
?sensors//                  # Sensors topic AND all subtopics
?buildings/.*/floor.*//     # Floor topics AND everything under them
```

## Common Selector Patterns

### Direct Children Only
```
?sensors/[^/]+              # Direct children of sensors (not grandchildren)
?buildings/[^/]+/status     # Status topics one level under buildings
```

### Structure Exploration
```
# Top-level exploration (use with depth parameter)
# No specific selector needed for structure

# Explore specific branches
?sensors//                  # All sensor-related topics
?data//                     # All data topics
?buildings//                # All building topics
```

### Content Retrieval
```
# Specific data types
?sensors/.*/temperature     # All temperature readings
?markets/.*/prices         # All price data
?logs/.*/errors            # All error logs

# Time-based patterns (Java regex)
*data/\d{4}/\d{2}/.*       # Date-organized data (YYYY/MM/...)
?data/.*/daily             # All daily summaries
?data/.*/realtime          # All real-time data

# Numbered patterns
?buildings/floor\d+/.*     # All numbered floors (floor1, floor2, etc.)
?sensors/sensor\d{3}/.*    # Three-digit sensor IDs (sensor001, sensor123, etc.)
```

### Monitoring
```
?alerts//                   # All alerts and sub-alerts
?status/.*/critical        # All critical status topics  
?events/.*/.*              # All event topics (two-level wildcard)
```

## Selector Syntax Validation

### Valid Examples ✅
```
sensors/temperature                    # Exact path
?sensors/.*/temperature               # Split path with regex
*sensor.*temperature.*                # Full path regex  
#>topic1////?topic2/.*/subtopic      # selector set
?data//                               # With descendant qualifier
```

### Invalid Examples ❌
```
?sensors/*/temperature                # Shell wildcards (*) don't work
/sensors/temperature                  # Don't start with /
sensors/temperature/                  # Don't end with / (unless using qualifiers)
?sensors//temperature                 # Mixed syntax (// is qualifier, not separator)
```

## Selector Performance Tips

### Efficient Selectors
- **Most specific first**: Use exact paths when you know them
- **Targeted wildcards**: `?sensors/.*/temperature` vs `?.*/.*/.*`
- **Appropriate qualifiers**: Use `/` vs `//` based on actual needs

### Less Efficient Patterns
- **Overly broad**: `*.*` matches everything
- **Deep nesting**: Multiple `.*/` levels can be slow
- **Large selector sets**: Many selectors in `#` syntax

## Usage Context Examples

### In Topic Fetching
```
// Structure exploration
fetch_topics topicSelector="?sensors//" depth=1

// Value retrieval  
fetch_topics topicSelector="?sensors/.*/temperature" values=true

// Specific monitoring
fetch_topics topicSelector="#>alerts/critical////?status/.*/error"
```

### In Topic Views (Source Selectors)
```
map ?sensors/.*/temperature to dashboard/temps/<path(1,2)>
map #>alerts/critical///?>status/.*/error to monitoring/<path(0)>
map ?data/.*/daily to summaries/<path(2)>/<scalar(/date)>
```

## Troubleshooting Selectors

### No Results Returned
1. **Check regex syntax**: Use `.*` or `.+` instead of `*`
2. **Verify path structure**: Use structure exploration first
3. **Test simpler patterns**: Start broad, then narrow down
4. **Check case sensitivity**: Paths are case-sensitive

### Too Many Results  
1. **Add more specific patterns**: Replace `.*` with specific terms
2. **Remove descendant qualifiers**: Use exact paths instead of `/` or `//`
3. **Use pagination**: Control result size with `number` parameter

### Performance Issues
1. **Narrow the scope**: Use more specific selectors
2. **Avoid deep wildcards**: Limit the number of `.*` segments
3. **Cache results**: Store frequently accessed topic lists

This guide covers all topic selector patterns and their proper usage across Diffusion operations.
