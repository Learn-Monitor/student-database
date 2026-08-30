# Holidays Package

The Holidays package is responsible for retrieving and providing
holiday information from an external API.

## Responsibilities
- Fetch holiday data from a remote endpoint during application startup
- Cache and normalize holiday information
- Provide a stable API for querying holiday data

## Lifecycle
1. Holiday data is accessed from an API endpoint (using an http client)
2. Current school year is determined
3. Current week and total weeks in this school year is determined
4. This data is passed to the API.

## Design Rationale
This functionality is isolated due to:
- External dependency
- Increased complexity
- Different failure modes compared to database-backed data