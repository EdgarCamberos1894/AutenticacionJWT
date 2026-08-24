# Authentication Service Template

Production-minded authentication foundation built with Java 25, Spring Boot 4.1.1, Spring Security, PostgreSQL, Redis, Flyway and Testcontainers.

The `architecture/layered` branch is intentionally conventional: controllers own HTTP concerns, services coordinate application flows, repositories own persistence, entities model identity/session state, and security/token infrastructure stays behind focused components. The goal is a layered design that remains easy to navigate in collaborative projects without pushing authentication logic into controllers.

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
- Argon2id password hashing with legacy BCrypt verification/upgrade support
- RS256 JWT access tokens
- Testcontainers
- ArchUnit
- Maven Enforcer
- Docker

## Authentication model

### Access tokens

Access tokens are RS256-signed JWTs and are intentionally stateless. The default lifetime is 10 minutes.

Claims include `iss`, `sub`, `aud`, `iat`, `nbf`, `exp`, `jti`, `sid`, `roles` and `token_type=access`. Spring Security validates the signature, issuer, audience, token type, UUID-shaped subject and UUID-shaped session id before accepting the token.

Only the `local` and `test` profiles generate an ephemeral 3072-bit RSA key pair at startup. The `prod` profile requires explicit public/private key resources through `JWT_PUBLIC_KEY_LOCATION` and `JWT_PRIVATE_KEY_LOCATION`; configured production keys must belong to the same pair and be at least 3072 bits.

### Sessions and refresh tokens

Login creates a persistent authentication session with an absolute lifetime of 30 days by default. Refresh tokens are opaque random values with a default lifetime of 7 days and can never outlive their owning session.

Only SHA-256 hashes of refresh tokens are persisted. Refresh uses rotation: a successful refresh consumes the current token and issues a replacement linked to its parent. Reusing an already-consumed refresh token is treated as replay and revokes the complete session family.

Refresh rotation is serialized with a pessimistic database lock so concurrent refresh attempts cannot silently create multiple valid descendants.

Session revocation immediately prevents further refreshes. An access JWT already issued remains valid until its expiration, at most 10 minutes with the default configuration. The service deliberately avoids a database lookup on every authenticated request merely to simulate instant JWT revocation.

### Passwords

New password hashes use Argon2id. A `DelegatingPasswordEncoder` retains BCrypt support so legacy BCrypt hashes, including the unprefixed `$2a/$2b/$2y` form produced by the original implementation, can authenticate and be upgraded to Argon2id after a successful login. Plain-text passwords are never persisted.

### Account status and roles

New accounts start as `PENDING_VERIFICATION`; successful email verification activates them. Authentication and refresh require the account to satisfy the same authentication-allowed invariant.

Supported roles are closed application values (`USER`, `ADMIN`) persisted directly in `user_roles`. There is intentionally no dynamic role catalog or role repository in this template. JWT authorities are derived from those persisted role values as `ROLE_*` authorities.

### Email verification

Registration creates a one-time verification credential. Verification tokens are opaque, only their hashes are stored, and the default lifetime is 30 minutes.

Issuing/resending a token locks the user row, invalidates previously active tokens of the same purpose and creates one replacement. PostgreSQL also enforces the active-token invariant, and an integration test exercises concurrent resend attempts to ensure only one active token survives.

Verification/resend responses are generic where account discovery would otherwise leak information. Email delivery is triggered after the database transaction commits, so a delivery failure does not roll back a successfully persisted account or token lifecycle change.

### Password recovery

Password-reset requests use the same one-time, hash-only token model with a default lifetime of 15 minutes and generic outward-facing behavior for unknown accounts.

A successful reset consumes the token, changes the Argon2id password hash and revokes all existing authentication sessions and refresh tokens for that user.

## Distributed rate limiting

Sensitive public authentication endpoints are protected by a Redis-backed sliding-window limiter implemented with sorted sets and an atomic Lua script. Redis server time is used for window calculations, keeping the decision consistent across application instances.

Per-client identifiers are SHA-256 hashed before becoming part of Redis keys. Login additionally applies a separate normalized-email account bucket, so changing source IP does not bypass account-level throttling.

When a policy is exceeded, the API returns `429 Too Many Requests`, RFC 9457 Problem Details and `Retry-After`.

If Redis is unavailable while rate limiting is enabled, the protected authentication surface fails closed with `503 Service Unavailable` rather than silently bypassing enforcement.

`X-Forwarded-For` is consulted only when the immediate remote address is configured in `TRUSTED_PROXY_ADDRESSES`. Requests received directly from untrusted addresses cannot choose their rate-limit identity by supplying forwarding headers.

Rate limiting can be disabled with `RATE_LIMIT_ENABLED=false`, which removes the enforcement wiring instead of leaving a partially active limiter.

## Browser/CORS boundary

CORS uses an explicit origin allowlist. Cross-origin browser access is denied when the allowlist is empty; production therefore does not open any origin by default. The `local` profile allows only `http://localhost:3000` unless `CORS_ALLOWED_ORIGINS` overrides it.

Allowed cross-origin methods are limited to the methods used by the API (`GET`, `POST`, `DELETE`, `OPTIONS`), and request headers are limited to `Authorization` and `Content-Type`. Cookie credentials are not enabled. `Retry-After` and `WWW-Authenticate` are exposed so browser clients can consume authentication/rate-limit metadata.

## HTTP API

Public endpoints:

| Method | Endpoint | Success | Purpose |
| --- | --- | ---: | --- |
| `POST` | `/api/v1/auth/register` | `201` | Register a pending account |
| `POST` | `/api/v1/auth/login` | `200` | Authenticate and create a session |
| `POST` | `/api/v1/auth/refresh` | `200` | Rotate a refresh token and issue a new token pair |
| `POST` | `/api/v1/auth/email-verification` | `204` | Request/resend an email-verification token without exposing account existence |
| `POST` | `/api/v1/auth/email-verification/confirm` | `204` | Consume a verification token |
| `POST` | `/api/v1/auth/password-reset` | `204` | Request password recovery without exposing account existence |
| `POST` | `/api/v1/auth/password-reset/confirm` | `204` | Consume a reset token and replace the password |

Authenticated endpoints:

| Method | Endpoint | Success | Purpose |
| --- | --- | ---: | --- |
| `POST` | `/api/v1/auth/logout` | `204` | Revoke the current session |
| `POST` | `/api/v1/auth/logout-all` | `204` | Revoke all sessions for the authenticated user |
| `GET` | `/api/v1/auth/sessions` | `200` | List active sessions |
| `DELETE` | `/api/v1/auth/sessions/{sessionId}` | `204` | Revoke one owned session |

Session lookup/revocation is scoped by both `sessionId` and authenticated `userId` to prevent cross-account session access.

`GET /actuator/health` is public. Other application endpoints require a valid bearer access token unless explicitly permitted by the security configuration.

## API contract

- Successful responses use HTTP semantics and resource/action DTOs rather than a universal envelope.
- Login and refresh responses send `Cache-Control: no-store` and `Pragma: no-cache`.
- Successful commands with no representation return `204 No Content`.
- Client/server errors use RFC 9457 Problem Details with `application/problem+json`.
- Problem responses expose stable machine-readable `code` values; human-readable `detail` text is not an API contract.
- Request validation failures return `422 Unprocessable Content` with structured validation pointers.
- Malformed JSON remains `400 Bad Request`.
- Missing or invalid authentication returns `401 Unauthorized` with `WWW-Authenticate`.
- Authenticated requests without sufficient authority return `403 Forbidden`.
- Unsupported methods and media types retain `405` and `415` semantics.
- Internal exception messages and stack traces are not returned to clients.

See [`docs/http-contract.md`](docs/http-contract.md) for the compact contract reference.

## Local development

Start PostgreSQL and Redis:

```bash
docker compose up -d
```

Run the application with the local profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Local defaults:

- PostgreSQL: `jdbc:postgresql://localhost:5432/authdb`
- Redis: `redis://localhost:6379`
- JWT issuer: `http://localhost:8080`
- JWT audience: `authentication-api`
- Verification URL: `http://localhost:3000/verify-email`
- Password reset URL: `http://localhost:3000/reset-password`
- CORS origin: `http://localhost:3000`
- JWT key pair: ephemeral 3072-bit RSA key generated for that process

Because the local key pair is ephemeral, access tokens issued before an application restart will no longer validate after the restart. Production does not use ephemeral signing keys.

## Configuration

Core environment variables:

| Variable | Local default | Production |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/authdb` | required |
| `DB_USER` | `auth` | required |
| `DB_PASSWORD` | `auth` | required |
| `REDIS_URL` | `redis://localhost:6379` | required when rate limiting is enabled |
| `JWT_ISSUER` | `http://localhost:8080` | required |
| `JWT_AUDIENCE` | `authentication-api` | required |
| `JWT_PUBLIC_KEY_LOCATION` | ephemeral key pair used instead | required in `prod` |
| `JWT_PRIVATE_KEY_LOCATION` | ephemeral key pair used instead | required in `prod` |
| `JWT_ACCESS_TOKEN_TTL` | `PT10M` | optional positive-duration override |
| `AUTH_SESSION_TTL` | `P30D` | optional positive-duration override |
| `AUTH_REFRESH_TOKEN_TTL` | `P7D` | optional positive-duration override |
| `EMAIL_VERIFICATION_TTL` | `PT30M` | optional positive-duration override |
| `PASSWORD_RESET_TTL` | `PT15M` | optional positive-duration override |
| `VERIFICATION_PUBLIC_URL` | `http://localhost:3000/verify-email` | required |
| `PASSWORD_RESET_PUBLIC_URL` | `http://localhost:3000/reset-password` | required |
| `MAIL_FROM` | `no-reply@example.local` | required |
| `MAIL_HOST` | none | required |
| `MAIL_PORT` | `587` | optional |
| `MAIL_USERNAME` | none | required by the current SMTP production configuration |
| `MAIL_PASSWORD` | none | required by the current SMTP production configuration |
| `MAIL_SMTP_AUTH` | `true` | optional |
| `MAIL_STARTTLS` | `true` | optional |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` in `local`; empty otherwise | set explicit browser origins when cross-origin access is required |
| `TRUSTED_PROXY_ADDRESSES` | empty | configure only known proxy/load-balancer addresses |
| `RATE_LIMIT_ENABLED` | `true` | optional |

Each protected flow exposes independent `RATE_LIMIT_*_LIMIT` and `RATE_LIMIT_*_WINDOW` variables in `application.yml`. Login has both a client policy and a separate `login-account` policy.

## Verification and CI

Run the complete verification suite:

```bash
./mvnw verify
```

The suite includes PostgreSQL-backed authentication flows, Redis-backed rate-limit behavior, concurrent one-time-token issuance, browser CORS preflights, legacy BCrypt migration and architecture rules.

Maven Enforcer requires Java 25 and a supported Maven 3.9.x toolchain. ArchUnit prevents selected dependency inversions such as controllers accessing repositories directly or entities depending on application/HTTP/security layers.

GitHub Actions runs Maven verification and builds the application container image. Third-party workflow actions are pinned to immutable commit SHAs and the workflow token is restricted to read-only repository contents.

## Security boundaries and future extensions

This template intentionally does not yet implement MFA/passkeys, social/OIDC login, device attestation, guaranteed email delivery through an outbox/queue, or immediate per-request revocation checks for already-issued access JWTs.

Generic recovery responses reduce direct account enumeration but do not attempt artificial response-time equalization; a production system with stricter anti-enumeration requirements should move delivery behind a durable asynchronous boundary rather than adding sleeps to request threads.

If bearer credentials are ever moved to cookies, revisit CSRF protection. If deployment topology changes, update `TRUSTED_PROXY_ADDRESSES` deliberately instead of trusting arbitrary forwarded headers.
