# Student Database – Developer Documentation

The Student Database is a Java-based server application designed to
manage and persist student-related data for educational institutions.

It provides:
- A database-backed backend for managing students, classes, subjects,
  and related entities
- A web interface for students and teachers
- A command-line interface for administrative tasks

This documentation is intended for developers who want to understand,
maintain, or extend the backend system or the web interface.

## Scope of this documentation

This documentation focuses on:
- Backend and frontend architecture and design decisions
- Package responsibilities and boundaries
- Extension points and integration guidelines

End-user documentation (usage of the web interface or CLI commands)
is maintained separately in the project Wiki.

## Technology Stack

- Java 17
- Gradle
- SQLite (via JDBC)
- Custom HTTP server and templating

## Getting Started

- Backend Architecture Overview: architecture/backend-overview.md
- Application Startup Flow: architecture/startup-lifecycle.md
- Package Responsibilities: packages/
