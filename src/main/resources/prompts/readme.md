## CRITICAL: How to use the Diffusion MCP Server

### MANDATORY First Step - Read Context

**BEFORE using ANY Diffusion MCP tools, you MUST use the `get_context` tool to read the appropriate guide.**

**Start EVERY Diffusion task by getting context type `introduction` first.**

Never attempt to use tools without first reading the relevant context. Do not guess or infer - the tools have specific requirements and syntax that must be understood.

### Required Reading Order

1. **ALWAYS START HERE**: `get_context type="introduction"` - Read this before doing ANYTHING
2. **For topic operations**: `get_context type="topics"` - Before fetching, creating or updating topics
3. **For advanced topic operations**: `get_context type="topics_advanced"` - To understand additional properties when creating topics
4. **For topic selectors**: `get_context type="topic_selectors"` - VITAL before using `fetch_topics`, `remove_topics` or `create_topic_view`
5. **For sessions**: `get_context type="sessions"` - Before using any session tools or session trees or anything that needs session filters
6. **For metrics**: `get_context type="metrics"` - Before checking server health or using metrics or metric alerts
7. **For topic views**: `get_context type="topic_views"` - Before creating topic views
8. **For advanced topic views**: `get_context type="topic_views_advanced"` - For advanced topic view features
9. **For session trees**: `get_context type="session_trees"` - Before using branch mapping tables for session trees
10. **For remote servers**: `get_context type="remote_servers"` - Before using remote topic views (advanced)
11. **For security**: `get_context type="security"` - To understand Diffusion security, authentication and permissions and how to change them

### Workflow Rules

1. **Read context FIRST, use tools SECOND** - Never reverse this order
2. **Topic selectors are complex** - Do not attempt to use them without reading the topic_selectors guide
3. **Always confirm destructive operations** - Show the user the exact topic selector for `remove_topics` and get explicit confirmation
4. **Topic Views have a complex language** - Read both guides to understand how they work and how to specify them
5. **Session trees require setup** - Read `session_trees` context to understand the full workflow including trusting client properties

### Topic Removal Warning

**NEVER remove topics without:**

1. Reading the topic_selectors context guide
2. Showing the user the EXACT topic selector you will use
3. Getting explicit confirmation that they want to proceed
4. Understanding that this is a DESTRUCTIVE operation that cannot be undone

### Security settings warning

**NEVER change security settings without:**

1. Reading the security context guide
2. Showing the user exactly what you are going to change
3. Getting explicit confirmation that they want to proceed
