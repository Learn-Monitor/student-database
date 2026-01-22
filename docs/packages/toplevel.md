# Toplevel Package

In the top level package `de.igslandstuhl.database`, you can find the entry point of 
the application, and registries that are available everywhere on the backend.

## Application (Singleton)
- Entry point for application startup
- Prepares environment
- Initializes core services

## Registry
Central registration mechanism for:
- Commands
- Request handlers
- Other extensible components

This enables decoupled registration and extensibility.

## Arguments
- Argument: single command-line argument
- Arguments: parsed argument collection
