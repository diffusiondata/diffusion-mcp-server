# diffusion-mcp-server

An **MCP (Model Context Protocol)** that connects to a Diffusion&trade; server (from [DiffusionData](https://www.diffusiondata.com)) so AI assistants can interact with the Diffusion server to explore and/or create topics, configure features, and monitor the server.

> **Version:** 1.0.0  
> **Requires:** Java 17+

---

## Introduction

This is an MCP server that allows you to connect to any Diffusion server and perform various interactions with it.

It currently supports:-

- Exploring the Topic Tree and fetching topic values
- Value range queries on Time Series Topics
- Creating, updating and removing topics
- Getting the details of connected sessions - using filters if required
- Creating topic view specifications, creating, listing and removing topic views
- Creating remote servers to support remote topic views
- Getting metrics from the server
- Managing Metrics Collectors
- Managing Metric Alerts (Diffusion 6.12 and beyond only)
- Creating and maintaining session tree branch mapping tables, and connecting with session properties set
- Retrieving and updating of System Security and Authentication stores.
- STDIO and HTTP Transports
- Multi Client Tenancy (relevant to HTTP only)

The MCP server does **NOT** support subscribing to topics. It is recommended that it is used on conjunction with the Diffusion Management Console which can subscribe to topics and therefore observe updates that are performed via the MPC server. The console can also be used to verify what has  been created by the MCP server.

The MCP server also does not support all features of Diffusion (for example : messaging). Its main purpose if as a development aid and for server monitoring.

It can be used as a simple introduction to Diffusion and before even connecting to a Diffusion server you could ask it to provide you with information, for example:-

`Tell me about the Diffusion product?`

`What can I do with the Diffusion MCP Server?`

For ease of use it can connect to a locally running server with default credentials. To connect to any other server you will need to tell it the url, principal and password.

For example:-

`Connect to the local diffusion server as admin`

`connect to the diffusion server at ws://localhost:8080 with principal admin and password 'aSecret'`

You can then try asking it to do anything that it supports - so some suggestions:-

`Explore the structure of the topic tree`

`Tell me about the health of the server`

`What are topic views?`

`Suggest a topic view that would be useful for this server`

`Create a JSON topic called test/person which represents a person with some interesting details`

`Show me how to use session trees`

`Create some topics to test a session tree`

`Create a simple sportsbook hierarchy of topics`

---

## Use with a Diffusion Server

The MCP Server uses the Diffusion 6.12 client and so is ideally used with a Diffusion 6.12 server for full capability. However, it can connect to any Diffusion server running at a previous release but some capabilities (such as Metric Alerts) may be unavailable if they were not available within the Diffusion server at that version.

The MCP Server is best used for exploratory and training purposes on an out-of-the-box Diffusion server installation. In that case you can use the issued `admin` principal which allows full capabilities. 

You can also use on any other server that already has topic data, but if the issued security stores have been changed or are not in use you will be restricted to operations allowed by the principal you use for connecting.

You should not use it on a production server except when using a principal that is restricted to read operations only as the MCP server has the ability to create and remove topics and other components if it has sufficient permissions. You should always have a backup of your persistence files. DiffusionData takes no responsibility for accidental negative changes done to the persistence files. 

---

## Requirements 

This is a Java server - you will need to have Java 17 on your path. e.g.

`export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`

or on Windows:-

`set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot`

The server can work either using the STDIO transport (single local user), or the HTTP transport (powered by [Jetty](https://jetty.org)) which allows for multiple concurrent clients.

---

## Quick start

Grab the latest shaded JAR (one file): **[Releases → Latest](../../releases/latest)**

---

## Using Claude Desktop as the MCP Client

Claude Desktop is ideal for testing the MCP server.

[Download Claude Desktop](https://claude.ai/download)

You will need a 'pro' subscription to Claude.

Claude Desktop can connect to an MCP server either over the STDIO transport (single user with local MCP server) or over HTTP.
Though the STDIO transport is easiest, it is recommended to use the HTTP transport with Claude Desktop connecting locally over an STDIO bridge because of **a bug in Claude Desktop** which is described below.

To configure Claude Desktop to work with the MCP server you will need to edit the Claude Desktop Configuration file which may be found at:

```
    OSX: ~/Library/Application Support/Claude/claude_desktop_config.json
    Windows: %APPDATA%\Claude\claude_desktop_config.json
    Linux: .config/Claude/claude_desktop_config.json
```

If the file is not there you can create it or alternatively, in Claude Desktop, you can go to Settings->Developer and edit the config there. You will have to restart Claude Desktop before any such changes take effect.

The exact configuration depends on whether you want to use the STDIO transport or the HTTP transport (recommended). Full details are given below.

---

## Using with Claude Desktop with STDIO Transport

Claude Desktop can be configured to automatically start the MCP server locally.

**Edit Claude configuration file as follows:**

Replace `/pathToJar` with the full path to the jar.

```
{
  "mcpServers": {
    "diffusion-mcp-server": {
      "command": "java",
      "args": [
        "-Xmx512m",
        "-Dfile.encoding=UTF-8",
        "-jar",
        "/pathToJar/diffusion-mcp-server-1.0.0.jar"
      ]
    }
  }
}
```

> ⚠️ **Warning:** Claude Desktop has a bug

There is currently a bug in Claude Desktop (as at Version 0.13.64) where it leaves zombie processes if you get it to start the MCP server itself. It can leave both 'crashpad_handler' processes and java MCP server processes. This is because it restarts the MCP server as it closes - a bug report has been raised with Anthropic.

You can check if such processes exist using:-

```
ps aux | grep -i claude
ps aux | grep diffusion-mcp-server
```
or on Windows:-

```
tasklist | findstr /i claude
tasklist | findstr /i diffusion-mcp-server
```

If there are processes left behind after quitting Claude Desktop you should kill them as follows:-

```
pkill -fi claude
pkill -f "diffusion-mcp-server"
```
or on Windows (PowerShell):-

```
Get-Process | Where-Object {$_.ProcessName -like "*claude*"} | Stop-Process -Force
Get-Process | Where-Object {$_.ProcessName -like "*diffusion-mcp-server*"} | Stop-Process -Force
```

To avoid this you can start the MCP server separately using the HTTP transport and configure Claude Desktop to use an STDIO bridge to reach it as described below.

---

## Using HTTP Transport

To use the HTTP transport you must have set up a keystore. You can create
a self signed certificate as follows (on MacOS/Linux):-

```
keytool -genkeypair -alias mcp-local \
  -keyalg RSA -keysize 2048 -validity 3650 \
  -keystore keystore.jks -storepass changeit -keypass changeit \
  -dname "CN=localhost, OU=Dev, O=DiffusionData, L=Local, ST=., C=GB"
```

The server may then be started independently as an HTTP endpoint as follows:-

```
java \
 -Dmcp.transport=https \
 -Djavax.net.ssl.keyStore=keystore.jks \
 -Djavax.net.ssl.keyStorePassword=changeit \
 -jar diffusion-mcp-server-1.0.0.jar
```
This will listen on localhost port 7443. For more advanced use see below.

The above commands are the same on Windows except you use ^ instead of \ for line breaks.

> Logs print to stdout by default (SLF4J + slf4j-simple embedded). No extra setup required.
 
### Connecting to an HTTP Server using Claude Desktop

You can configure a simple STDIO bridge to connect Claude Desktop to a local Diffusion MCP server running with the HTTP transport using the following entry in the Claude Desktop configuration:- 

```
{
  "mcpServers": {
    "diffusion-mcp-server": {
      "command": "npx",
      "args": ["--yes", "mcp-remote@latest", "https://localhost:7443/mcp"],
      "env": {
        "NODE_TLS_REJECT_UNAUTHORIZED": "0"
      }
    }
  }
}
```

---

## Advanced Use of HTTP Transport

For more advanced use, additional parameters can be supplied as system properties, as in the following example:-

```
java \
 -Dmcp.transport=https \
 -Dmcp.host=localhost \
 -Dmcp.port=7443 \
 -Djavax.net.ssl.keyStore=keystore.jks \
 -Djavax.net.ssl.keyStorePassword=changeit \
 -Djavax.net.ssl.keyManagerPassword=changeit \
 -Dmcp.cors.origin=https://your-ui.example.com \
 -Dmcp.hsts="max-age=31536000; includeSubDomains" \
 -jar diffusion-mcp-server-1.0.0.jar
```
 
 The following environment variables are also supported:-
 
```
 MCP_TRANSPORT
 MCP_HOST
 MCP_PORT
 JAVAX_NET_SSL_KEYSTORE
 JAVAX_NET_SSL_KEYSTOREPASSWORD
 JAVAX_NET_SSL_KEYMANAGERPASSWORD
 MCP_CORS_ORIGIN
 MCP_HSTS
```
 
 So, for example a remotely accessible server could be started as follows:-
 
```
 java \
  -Dmcp.transport=https \
  -Dmcp.host=0.0.0.0 \
  -Dmcp.port=7443 \
  -Djavax.net.ssl.keyStore=/etc/mcp/keystore.p12 \
  -Djavax.net.ssl.keyStoreType=PKCS12 \
  -Djavax.net.ssl.keyStorePassword='*******' \
  -Djavax.net.ssl.keyManagerPassword='*******' \
  -Dmcp.cors.origin=https://your-ui.example.com \
  -Dmcp.hsts="max-age=31536000; includeSubDomains" \
  -jar diffusion-mcp-server-1.0.0.jar
```

**Notes**

* Swap `keystore.p12` for your real cert; if you’re using JKS, omit `-Djavax.net.ssl.keyStoreType=PKCS12`.

* `mcp.host=0.0.0.0` makes the server listen externally. Keep your firewall/security group open for `:7443` (or change to `:443` if you like).

* If you bind to 443 on Linux, you may need elevated perms or `setcap 'cap_net_bind_service=+ep' $(readlink -f $(which java))`.

* Set `mcp.cors.origin` to the exact origin of your frontend. (Multiple origins? front a reverse proxy or handle CORS in the proxy.)

* `mcp.hsts` is safe for public HTTPS. Add `; preload` only if you intend to submit your domain to browser preload lists.

If you’ll run behind a reverse proxy (Nginx/ALB/Cloudflare), this command is still fine as the server respects `X-Forwarded-Proto` so HSTS is only set on secure requests.

---

## Connecting Externally

If you run your server on a public, TLS-trusted URL and you don’t require auth, both Claude and ChatGPT can connect to it remotely.

**Claude**

* Remote MCP (in Claude Desktop): Settings → Connectors → Add custom connector → enter your HTTPS /mcp URL. If your cert is publicly trusted and the URL is reachable, it connects without credentials. 

* No CORS needed (it’s a native app making direct requests).

**ChatGPT**

* Custom connectors (MCP) are available in ChatGPT (Pro / Business / Enterprise). You can add a custom connector that points to your remote MCP URL; if your endpoint doesn’t require auth, ChatGPT connects as-is. 

* This is a server-to-server call from OpenAI’s side, so CORS doesn’t apply; you just need a publicly trusted cert and an internet-reachable host.

**What you still need to ensure**

* Public DNS/host + firewall open to your port (e.g., 443/7443).

* A public CA TLS certificate.

---

## Contributing

Issues and PRs are welcome. Please include:
- A clear description of the change
- Tests where practical
- Rationale for user‑facing behaviour changes

---

## License

Apache License 2.0 — see [`LICENSE`](./LICENSE).

---

## Feedback

Please send all feedback to [paddy.walsh@diffusiondata.com](mailto:paddy.walsh@diffusiondata.com)

