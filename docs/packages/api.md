# API Package

The API package provides structured access to database-backed data
and encapsulates domain-level data manipulation logic.

It acts as the primary interface between the database layer and the
server logic.

## Responsibilities
- Represent database tables and derived views as Java objects
- Provide list-based access to related records where appropriate
- Encapsulate domain-specific data modification logic
  (e.g. password changes, state updates)
- Enforce consistency and validation rules close to the data

## Data Representation

- Most API classes map to a primary database table
- Some API objects expose related tables as lists or collections
  to simplify common access patterns
- These collections represent *conceptual ownership*, not raw SQL joins

## Data Modification Rules

- API classes may modify persistent data when the change:
  - directly affects the represented entity
  - enforces domain-specific invariants
- Examples:
  - Setting or validating passwords
  - Updating status fields
  - Maintaining referential integrity

- API classes must NOT:
  - Implement cross-entity workflows
  - Coordinate multiple unrelated domain objects
  - Perform request- or session-specific logic

## Layer Interaction

- API classes use the sql package for persistence
- Server logic orchestrates API objects but does not modify database
  state directly
- Request handlers and commands interact with data exclusively
  through the API package

### Example

Changing a student's password is implemented in the Student API class,
as it:
- modifies only the Student entity
- enforces password rules
- ensures consistent persistence

The server layer is responsible only for deciding *when* this happens,
not *how*.
