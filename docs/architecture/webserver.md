# Webserver Package

The webserver package implements a custom HTTP server abstraction.
It translates low-level HTTP requests into structured application
requests and produces corresponding responses.

## Responsibilities

- Abstract HTTP request and response handling
- Provide a structured request/response model
- Route requests to appropriate handlers
- Manage HTTP-specific concerns (headers, cookies, status codes)
- Integrate session management into request handling

## Request Handlers

Handlers implement request-specific logic.

- HttpHandler  
  Base abstraction for all handlers

- GetRequestHandler  
  Handles HTTP GET requests

- PostRequestHandler  
  Handles HTTP POST requests

- WebResourceHandler  
  Handles the mapping of web resource paths to resource locations.

Handlers are responsible for:
- Interpreting requests
- Validating parameters
- Delegating business logic to the server or API layer

Handlers must not access the database directly.

## Request Model

The request package defines structured representations of incoming
HTTP requests.

- HttpRequest  
  Common base abstraction

- GetRequest / PostRequest  
  Specialized request types

- HttpHeader  
  Used to determine request characteristics

- RequestType 
  Enum defining supported request types

- APIPostRequest  
  Specialized POST request providing optimized access to API objects
  (for example when entity IDs are passed as parameters)

## Response Model

The response package defines structured HTTP responses.

- HttpResponse  
  Base response abstraction

- GetResponse / PostResponse  
  Specialized response types

- TemplatingPreprocessor  
  Helper for preparing template-based responses

## Session Management

Session handling is integrated into the webserver package.

- Session  
  Represents a single user session

- SessionManager  
  Manages session lifecycle and access

- SessionStorage  
  Defines session persistence and storage strategy

## Supporting HTTP Types

The following classes represent HTTP-level concepts and are used
throughout the webserver package:

- AccessManager / AccessLevel
- ContentType
- Cookie
- Status
- WebPath

## Design Principles

- The webserver package is protocol-focused
- It must not contain domain or business logic
- It must remain independent of database details
- All application-specific logic is delegated outward
