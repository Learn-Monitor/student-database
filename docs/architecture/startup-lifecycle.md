# Application Startup Lifecycle

1. Application singleton is initialized
2. Command-line arguments are parsed
3. Commands are registered and the command line is set up for commands
4. Database connection is established
5. Holidays data is fetched
6. Modules, Handlers and Web Paths are registered
7. WebServer and SessionManager are started