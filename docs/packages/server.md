# Server Package

The server package represents the runtime environment of the
application.

It contains long-lived core components and subsystems that are
initialized by the Application class and used throughout the
application's lifetime.


## Responsibilities
- Hold and expose core runtime components
- Provide shared infrastructure to application subsystems
- Group and structure server-side functionality
- Contain protocol- and domain-specific subsystems
This includes:
- The WebServer instance
- Session management infrastructure
- Database access infrastructure
- Resource access

## Non-Responsibilities

The server package does NOT:
- Define application startup or shutdown order
- Parse command-line arguments

## Subpackages
- webserver: HTTP handling and request/response processing
- sql: database connectivity
- resources: file-based resources
- commands: server-side CLI commands

## WebServer Class

The WebServer class represents the concrete HTTP server instance used
by the application.

Although it closely cooperates with the webserver subpackage, it
resides in the server package because it represents a top-level server
component rather than a low-level HTTP abstraction.
