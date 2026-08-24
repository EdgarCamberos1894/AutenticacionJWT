# Authentication Service Template

Modern authentication foundation built on Java 25, Spring Boot 4.1.x, Spring Security, PostgreSQL, Flyway and Testcontainers.

## Branch strategy

- `legacy-jwt-v1`: immutable reference to the original implementation.
- `modern-base`: shared technical baseline, without authentication business flows.
- `architecture/layered`: conventional layered architecture used for simulations and collaborative projects.
- `architecture/modular`: future feature-oriented/modular variant.

## API contract principles

- Successful responses use HTTP semantics and resource DTOs rather than a universal `success/message/data` envelope.
- Resource creation returns `201 Created` and a `Location` header when a resource URI exists.
- Successful commands with no representation return `204 No Content`.
- Asynchronous work returns `202 Accepted` only when processing is genuinely deferred.
- Client and server errors use RFC 9457 Problem Details (`application/problem+json`).
- Machine-readable error codes are stable; human-readable `detail` text is not an API contract.
- Validation errors expose a structured `errors` extension.
- Internal exception messages and stack traces are never returned to clients.

## Local development

```bash
docker compose up -d
./mvnw spring-boot:run
```

## Verification

```bash
./mvnw verify
```

Integration tests use PostgreSQL through Testcontainers.
