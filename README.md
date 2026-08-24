# Authentication Service Template

Production-minded authentication foundation built with Java 25, Spring Boot 4.1.1, Spring Security, PostgreSQL, Redis, Flyway and Testcontainers.

The `architecture/layered` branch is intentionally conventional: controllers coordinate HTTP concerns, services own application flows, repositories own persistence, and security/token infrastructure stays behind focused components. It is meant to be understandable enough for collaborative projects without flattening security decisions into controller code.

## Branch strategy

- `legacy-jwt-v1`: immutable reference to the original implementation.
- `modern-base`: shared Java 25 / Spring Boot 4 technical baseline without authentication business flows.
- `architecture/layered`: complete layered authentication implementation.
- `architecture/modular`: reserved for a future feature-oriented/modular comparison.

## Stack

- Java 25
- Spring Boot 4.1.1
- Spring Security OAuth2 Resource Server
- PostgreSQL 18.6
- Flyway
- Redis 8.10.1 reference image
- Argon2id password hashing
- RS256 JWT access tokens
- Testcontainers
- Docker

## Authentication model

### Access tokens

Access tokens are signed RS256 JWTs and are intentionally stateless. The default lifetime is 10 minutes.

Claims include `iss`, `sub`, `aud`, `iat`, `nbf`, `exp`, `jti`, `sid`, `roles` and `token_type=access`. The decoder validates issuer, audience and token type before Spring Security accepts the token.

Non-production profiles generate an ephemeral 3072-bit RSA key pair at startup. Production requires explicit public/private key resources through `JWT_PUBLIC_KEY_LOCATION` and `JWT_PRIVATE_KEY_LOCATION`.

### Sessions and refresh tokens

Login creates a persistent authentication session with an absolute lifetime of 30 days by default. Refresh tokens are opaque random values with a default lifetime of 7 days.

Only SHA-256 hashes of refresh tokens are persisted. Refresh uses rotation: a successful refresh consumes the current token and issues a replacement. Reusing an already-consumed refresh token is treated as replay and revokes the complete session family.

Refresh rotation is serialized with a pessimistic database lock so concurrent refresh attempts cannot silently create multiple valid descendants.

Session revocation immediately prevents further refreshes. An access JWT already issued remains valid until its expiration, at most 10 minutes with the default configuration. The service deliberately does not perform a database lookup on every authenticated request merely to simulate instant JWT revocation.

### Passwords

Passwords are stored with Argon2id. Plain-text passwords are never persisted.

### Email verification

Registration creates the user as `PENDING_VERIFICATION`. Verification tokens are opaque, one-time values and only their hashes are stored. The default verification lifetime is 30 minutes.

Verification and resend flows are designed not to disclose whether an email address exists. Email delivery occurs after the database transaction commits, so an SMTP failure does not roll back a successfully created account. A resend can recover the flow.

### Password recovery

Password-reset requests also use generic `202 Accepted` responses to reduce account enumeration. Reset tokens are opaque, one-time, hash-only values with a default lifetime of 15 minutes.

A successful password reset changes the Argon2id password hash and revokes every existing authentication session and refresh token for the user.

## Distributed rate limiting

Sensitive public authentication endpoints are protected by a Redis-backed fixed-window limiter. The counter and TTL logic execute atomically in Redis through a Lua script, so the limit remains consistent across multiple application instances without a process-local counter.

The client identifier is hashed before becoming part of a Redis key.

When a policy is exceeded, the API returns `429 Too Many Requests`, RFC 9457 Problem Details and `Retry-After`.

If Redis is unavailable while rate limiting is enabled, the protected authentication surface fails closed with `503 Service Unavailable` rather than silently bypassing the control.

Rate limiting can be disabled with `RATE_LIMIT_ENABLED=false`, which removes its MVC interceptor/resolver wiring rather than leaving partially constructed components in the application context.

## HTTP API

Public endpoints:

| Method | Endpoint | Success | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/register` | `201` | Register a pending account |
| `POST` | `/api/v1/auth/login` | `200` | Authenticate and create a session |
| `POST` | `/api/v1/auth/refresh` | `200` | Rotate a refresh token and issue a new token pair |
| `POST` | `/api/v1/auth/email-verification` | `202` | Request/resend an email-verification token |
| `POST` | `/api/v1/auth/email-verification/confirm` | `204` | Consume a verification token |
| `POST` | `/api/v1/auth/password-reset` | `202` | Request password recovery |
| `POST` | `/api/v1/auth/password-reset/confirm` | `204` | Consume reset token and replace password |

Authenticated endpoints:

| Method | Endpoint | Success | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/logout` | `204` | Revoke the current session |
| `POST` | `/api/v1/auth/logout-all` | `204` | Revoke all sessions for the user |
| `GET` | `/api/v1/auth/sessions` | `200` | List active sessions |
| `DELETE` | `/api/v1/auth/sessions/{sessionId}` | `204` | Revoke one owned session |

Session lookup/revocation is scoped by both `sessionId` and authenticated `userId` to prevent IDOR-style cross-account access.

`GET /actuator/health` is public. All other application endpoints require a valid bearer access token unless explicitly listed above.

## API contract

- Successful responses use HTTP semantics and resource DTOs instead of a universal envelope.
- Token responses send `Cache-Control: no-store` and `Pragma: no-cache`.
- Successful commands with no representation return `204 No Content`.
- Deferred/generic request flows use `202 Accepted`.
- Client/server errors use RFC 9457 Problem Details with `application/problem+json`.
- Problem responses expose stable machine-readable `code` values.
- Request validation failures return `422 Unprocessable Content` with structured validation pointers.
- Malformed JSON remains `400 Bad Request`.
- Authentication failures return `401` and include `WWW-Authenticate`.
- Unsupported methods and media types retain `405` and `415` semantics.
- Internal exception details and stack traces are not returned to clients.

## Local development

Start PostgreSQL and Redis:

```bash
docker compose up -d
```

Run the application:

```bash
./mvnw spring-boot:run
```

Local defaults:

- PostgreSQL: `jdbc:postgresql://localhost:5432/authdb`
- Redis: `redis://localhost:6379`
- JWT issuer: `http://localhost:8080`
- JWT audience: `authentication-api`
- Verification URL: `http://localhost:3000/verify-email`
- Password reset URL: `http://localhost:3000/reset-password`

## Configuration

Core environment variables:

| Variable | Local default | Production |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/authdb` | required |
| `DB_USER` | `auth` | required |
| `DB_PASSWORD` | `auth` | required |
| `REDIS_URL` | `redis://localhost:6379` | required |
| `JWT_ISSUER` | `http://localhost:8080` | required |
| `JWT_AUDIENCE` | `authentication-api` | required |
| `JWT_PUBLIC_KEY_LOCATION` | ephemeral key pair used instead | required in `prod` |
| `JWT_PRIVATE_KEY_LOCATION` | ephemeral key pair used instead | required in `prod` |
| `JWT_ACCESS_TOKEN_TTL` | `PT10M` | optional override |
| `AUTH_SESSION_TTL` | `P30D` | optional override |
| `AUTH_REFRESH_TOKEN_TTL` | `P7D` | optional override |
| `EMAIL_VERIFICATION_TTL` | `PT30M` | optional override |
| `PASSWORD_RESET_TTL` | `PT15M` | optional override |
| `VERIFICATION_PUBLIC_URL` | `http://localhost:3000/verify-email` | required for real delivery |
| `PASSWORD_RESET_PUBLIC_URL` | `http://localhost:3000/reset-password` | required for real delivery |
| `MAIL_FROM` | `no-reply@example.local` | required |
| `MAIL_HOST` | none | required for SMTP delivery |
| `MAIL_PORT` | `587` | optional |
| `MAIL_USERNAME` | none | required when SMTP authenticates |
| `MAIL_PASSWORD` | none | required when SMTP authenticates |
| `MAIL_SMTP_AUTH` | `true` | optional |
| `MAIL_STARTTLS` | `true` | optional |
| `RATE_LIMIT_ENABLED` | `true` | optional |

Each protected flow also exposes independent `RATE_LIMIT_*_LIMIT` and `RATE_LIMIT_*_WINDOW` environment variables in `application.yml` so policy can be tuned without code changes.

## Verification and CI

Run the complete test suite:

```bash
./mvnw verify
```

Integration tests use real PostgreSQL containers. The rate-limit integration test also launches Redis and verifies that the shared limit returns `429` with `Retry-After` after the configured number of attempts.

CI validates both Maven verification and construction of the application container image.

## Security boundaries and future extensions

This template intentionally does not yet implement MFA/passkeys, social/OIDC login, device attestation or a distributed outbox for guaranteed email delivery. Those concerns can be added without changing the core access-token/refresh-session model.

If bearer credentials are ever moved to cookies, revisit CSRF protection. If the application is deployed behind a proxy and rate limits must identify original client IPs, configure a trusted-proxy strategy rather than trusting arbitrary forwarded headers from the public internet.
