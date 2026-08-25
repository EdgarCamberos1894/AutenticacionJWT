# Authentication Service Template

Production-minded authentication foundation built with Java 25, Spring Boot 4.1.1, Spring Security, PostgreSQL, Redis, Flyway, Resend and Testcontainers.

The layered implementation is intentionally conventional: controllers own HTTP concerns, services coordinate application flows, repositories own persistence, entities model identity/session state, and security/provider infrastructure stays behind focused components.

## Branch strategy

- `legacy-jwt-v1`: immutable reference to the original implementation.
- `modern-base`: reviewed authentication baseline used as the common behavior reference.
- `architecture/layered`: conventional layered implementation reference.
- `architecture/modular`: reserved for the feature-oriented/modular comparison.

## Stack

- Java 25
- Spring Boot 4.1.1
- Spring Security OAuth2 Resource Server
- PostgreSQL 18.6
- Flyway
- Redis 8.10.1 reference image
- Resend Email API over Spring `RestClient`
- AES-256-GCM encrypted transactional email outbox
- Argon2id password hashing with legacy BCrypt verification/upgrade support
- RS256 JWT access tokens
- Testcontainers
- ArchUnit
- Maven Enforcer
- Docker

## Authentication model

### Access tokens

Access tokens are RS256-signed JWTs and are intentionally stateless. The default lifetime is 10 minutes.

Claims include `iss`, `sub`, `aud`, `iat`, `nbf`, `exp`, `jti`, `sid`, `roles` and `token_type=access`. Spring Security validates signature, issuer, audience, token type, UUID-shaped subject and UUID-shaped session id before accepting a token.

Only the `local` and `test` profiles generate an ephemeral 3072-bit RSA key pair. The `prod` profile requires explicit public/private key resources through `JWT_PUBLIC_KEY_LOCATION` and `JWT_PRIVATE_KEY_LOCATION`; configured keys must belong to the same pair and be at least 3072 bits.

### Sessions and refresh tokens

Login creates a persistent authentication session with an absolute lifetime of 30 days by default. Refresh tokens are opaque random values with a default lifetime of 7 days and can never outlive their owning session.

Only SHA-256 hashes of refresh tokens are persisted. Refresh uses rotation: a successful refresh consumes the current token and issues a replacement linked to its parent. Reusing an already-consumed refresh token is treated as replay and revokes the complete session family.

Refresh rotation is serialized with a pessimistic database lock so concurrent refresh attempts cannot silently create multiple valid descendants.

Session revocation immediately prevents further refreshes. An access JWT already issued remains valid until expiration, at most 10 minutes with the default configuration. The service deliberately avoids a database lookup on every authenticated request merely to simulate instant JWT revocation.

### Passwords

New password hashes use Argon2id. A `DelegatingPasswordEncoder` retains BCrypt support so legacy BCrypt hashes, including unprefixed `$2a/$2b/$2y` values produced by the original implementation, can authenticate and be upgraded to Argon2id after a successful login. Plain-text passwords are never persisted.

### Account status and roles

New accounts start as `PENDING_VERIFICATION`; successful email verification activates them. Authentication and refresh require the account to satisfy the same authentication-allowed invariant.

Supported roles are closed application values (`USER`, `ADMIN`) persisted directly in `user_roles`. There is intentionally no dynamic role catalog or role repository in this template. JWT authorities are derived from those persisted role values as `ROLE_*` authorities.

### Email verification and password recovery

Verification and password-reset credentials are opaque one-time values. Only SHA-256 hashes are stored in `one_time_tokens`. Issuance is serialized by locking the user row and PostgreSQL enforces at most one active token for each user/purpose.

Verification tokens expire after 30 minutes by default; password-reset tokens expire after 15 minutes by default. Successful password reset changes the Argon2id password hash and revokes every existing authentication session and refresh token for the user.

Recovery/request responses are deliberately generic where account discovery would otherwise leak information.

## Durable transactional email delivery

Email verification and password-reset delivery use a transactional outbox. Token creation and the corresponding delivery intent are committed in the **same PostgreSQL transaction**.

```text
one-time token (hash only)
        +
encrypted email outbox row
        |
      COMMIT
        |
  outbox worker
        |
      Resend
        |
verified webhook
```

This closes the failure window where application state could commit and the process could terminate before a provider request was made.

### Encrypted outbox

The outbox stores the recoverable email payload only while delivery is actionable. The payload contains the complete provider-neutral `TransactionalEmail`, including the action link with the one-time credential, and is protected with:

- AES-256-GCM;
- a random 96-bit nonce for each message;
- a 128-bit authentication tag;
- authenticated additional data containing `messageId`, purpose and encryption `keyId`.

The outbox message id is the same UUID as the associated one-time-token issuance. The raw token therefore remains hash-only in the identity/token tables; its only recoverable copy is inside the encrypted, short-lived outbox payload.

Terminal rows (`SENT`, `DEAD`, `CANCELLED`) have `nonce` and `ciphertext` scrubbed. Delivery metadata can remain for operational correlation without retaining a decryptable recovery credential.

When a verification/reset token is superseded, the previous outbox row is cancelled and scrubbed in the same transaction before the new token/outbox pair is created. This prevents a delayed worker from sending an already-invalid action link.

### Encryption-key rotation

Outbox encryption supports one active key and one previous key. New messages always use the active key; pending messages encrypted with the previous key remain readable during a controlled rotation.

Safe rotation procedure:

1. Move the current `EMAIL_OUTBOX_ACTIVE_KEY_ID` / `EMAIL_OUTBOX_ACTIVE_KEY` values to the corresponding `PREVIOUS` variables.
2. Generate a new random 32-byte AES key and a new key id and deploy them as the active pair.
3. Allow pending/processing messages encrypted with the previous key to reach a terminal state.
4. Verify no actionable outbox rows still reference the previous key id.
5. Remove the previous key pair in a later deployment.

Do not perform a second key rotation while actionable messages still require the configured previous key.

Example key generation:

```bash
openssl rand -base64 32
```

### Multi-instance worker, leases and retries

Workers claim due messages with PostgreSQL `FOR UPDATE SKIP LOCKED`, persist a lease in a short transaction, release the database lock, and only then call the email provider. Provider network latency is therefore never held inside the claim transaction.

If a worker dies, another instance can reclaim the row after `EMAIL_OUTBOX_LEASE_DURATION`. A stale worker cannot later complete a lease it no longer owns.

Retryable provider/network failures use bounded exponential backoff with jitter. Delivery stops when the configured attempt limit is exhausted or the one-time credential would expire before the next attempt. Expired messages become terminal rather than sending dead recovery links.

A crash after Resend accepted the email but before PostgreSQL recorded completion is also safe: retry uses the exact same issuance-scoped Resend `Idempotency-Key`.

### Resend adapter

Production email is sent through Resend's HTTPS Email API. SMTP and `JavaMailSender` are not part of the production path.

Authentication flows and the outbox worker depend on the provider-neutral `TransactionalEmailSender`; Resend-specific HTTP/request/response behavior is isolated under `email.resend`.

Each issuance uses a stable provider idempotency namespace:

```text
auth/email-verification/{issuanceId}
auth/password-reset/{issuanceId}
```

The adapter:

- authenticates with a server-side Bearer API key;
- requires HTTPS provider configuration;
- applies bounded connect/read timeouts;
- sends HTML and explicit plain-text bodies;
- sends an `Idempotency-Key` on every transactional email;
- classifies provider/network failures as retryable or permanent;
- never exposes Resend response messages to authentication clients;
- never logs the API key, recipient address or raw token in production;
- logs only safe correlation/status metadata.

Messages use low-cardinality provider tags such as `email_verification` and `password_reset`.

### Verified Resend webhooks

Delivery-event ingestion is exposed at:

```text
POST /api/v1/webhooks/resend
```

This route does not use the application's bearer JWT because Resend is the caller. Instead it authenticates every request using the Resend/Svix signing secret.

Signature verification occurs over the **raw request body before JSON parsing**. The implementation validates the Svix webhook id, timestamp and signature, computes HMAC-SHA256 over `id.timestamp.rawBody`, supports rotated `v1` signatures in the signature header, compares signatures in constant time and rejects timestamps outside the configured replay tolerance (5 minutes by default).

Webhook ids are persisted idempotently, so provider retries do not apply the same event twice. Only safe metadata is retained (`webhookId`, Resend email id, event type and timestamps); the complete webhook payload containing recipient/subject information is not stored.

Tracked delivery states include queued, accepted, delayed, delivered, cancelled, failed, suppressed, bounced and complained. State progression is monotonic so an older delayed event cannot regress a later delivered/bounced/complained state.

The worker/webhook race is also handled: if a webhook arrives before the worker stores Resend's email id, the event remains persisted and is reconciled when provider acceptance is committed.

### Resend production setup

For production:

1. Verify the sending domain in Resend and publish its required SPF/DKIM records.
2. Create a Resend API key with **Sending access**, restricted to that sending domain when possible.
3. Keep `RESEND_API_KEY` server-side only.
4. Set `RESEND_FROM_ADDRESS` to an address on the verified domain.
5. Create/configure the Resend webhook pointing at `/api/v1/webhooks/resend` for the required `email.*` delivery events.
6. Store that endpoint's signing secret as `RESEND_WEBHOOK_SIGNING_SECRET`.

`onboarding@resend.dev` and the development cryptographic defaults exist only so non-production profiles can bind without production secrets. `prod` requires explicit provider/outbox secrets.

## Distributed rate limiting

Sensitive public authentication endpoints are protected by a Redis-backed sliding-window limiter implemented with sorted sets and an atomic Lua script. Redis server time is used for window calculations, keeping decisions consistent across application instances.

Per-client identifiers are SHA-256 hashed before becoming Redis keys. Login additionally applies a separate normalized-email account bucket, so changing source IP does not bypass account-level throttling.

When a policy is exceeded, the API returns `429 Too Many Requests`, RFC 9457 Problem Details and `Retry-After`. If Redis is unavailable while rate limiting is enabled, the protected authentication surface fails closed with `503 Service Unavailable` rather than silently bypassing enforcement.

`X-Forwarded-For` is consulted only when the immediate remote address is configured in `TRUSTED_PROXY_ADDRESSES`.

## Browser/CORS boundary

CORS uses an explicit origin allowlist. Cross-origin browser access is denied when the allowlist is empty; production therefore does not open any origin by default. The `local` profile allows only `http://localhost:3000` unless `CORS_ALLOWED_ORIGINS` overrides it.

Allowed cross-origin methods are limited to those used by the API (`GET`, `POST`, `DELETE`, `OPTIONS`), request headers are limited to `Authorization` and `Content-Type`, and cookie credentials are not enabled.

## HTTP API

Public authentication endpoints:

| Method | Endpoint | Success | Purpose |
| --- | --- | ---: | --- |
| `POST` | `/api/v1/auth/register` | `201` | Register a pending account and atomically enqueue verification delivery |
| `POST` | `/api/v1/auth/login` | `200` | Authenticate and create a session |
| `POST` | `/api/v1/auth/refresh` | `200` | Rotate a refresh token and issue a new token pair |
| `POST` | `/api/v1/auth/email-verification` | `202` | Accept a generic verification resend request for durable delivery |
| `POST` | `/api/v1/auth/email-verification/confirm` | `204` | Consume a verification token |
| `POST` | `/api/v1/auth/password-reset` | `202` | Accept a generic password-recovery request for durable delivery |
| `POST` | `/api/v1/auth/password-reset/confirm` | `204` | Consume a reset token and replace the password |

Provider callback:

| Method | Endpoint | Success | Authentication |
| --- | --- | ---: | --- |
| `POST` | `/api/v1/webhooks/resend` | `204` | Resend/Svix HMAC signature over raw body |

Authenticated endpoints:

| Method | Endpoint | Success | Purpose |
| --- | --- | ---: | --- |
| `POST` | `/api/v1/auth/logout` | `204` | Revoke the current session |
| `POST` | `/api/v1/auth/logout-all` | `204` | Revoke all sessions for the authenticated user |
| `GET` | `/api/v1/auth/sessions` | `200` | List active sessions |
| `DELETE` | `/api/v1/auth/sessions/{sessionId}` | `204` | Revoke one owned session |

`202` responses for verification resend and password-reset request are deliberately indistinguishable for known/unknown accounts. They mean the request was accepted for the applicable durable workflow, not that an account necessarily exists or that a provider has already delivered an email.

Session lookup/revocation is scoped by both `sessionId` and authenticated `userId` to prevent cross-account session access.

`GET /actuator/health` is public. Other application endpoints require a valid bearer access token unless explicitly permitted by security configuration.

## API contract

- Successful responses use HTTP semantics and resource/action DTOs rather than a universal envelope.
- Login and refresh responses send `Cache-Control: no-store` and `Pragma: no-cache`.
- A genuinely deferred durable workflow may return `202 Accepted`.
- Successful commands with no deferred work and no representation return `204 No Content`.
- Client/server errors use RFC 9457 Problem Details with `application/problem+json`.
- Problem responses expose stable machine-readable `code` values; human-readable `detail` text is not an API contract.
- Request-body and method-parameter validation failures return `422 Unprocessable Content` with structured validation pointers.
- Malformed JSON remains `400 Bad Request`.
- Missing or invalid bearer authentication returns `401 Unauthorized` with `WWW-Authenticate`.
- Authenticated requests without sufficient authority return `403 Forbidden`.
- Unsupported methods/media negotiation retain `405`, `406` and `415` semantics.
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

The outbox worker is enabled by default. Under `local`/`test`, the provider-neutral worker drains the encrypted outbox through `LoggingTransactionalEmailSender` instead of calling Resend. That sender logs the plain-text email body (including its local action link) at DEBUG level. Treat those logs as development-sensitive and never enable that sender/profile in production.

Local defaults:

- PostgreSQL: `jdbc:postgresql://localhost:5432/authdb`
- Redis: `redis://localhost:6379`
- JWT issuer: `http://localhost:8080`
- JWT audience: `authentication-api`
- Verification URL: `http://localhost:3000/verify-email`
- Password reset URL: `http://localhost:3000/reset-password`
- CORS origin: `http://localhost:3000`
- JWT key pair: ephemeral 3072-bit RSA key generated for that process
- outbox AES key/signing secret: development-only defaults

Because the local JWT key pair is ephemeral, access tokens issued before an application restart will no longer validate after restart. Production does not use ephemeral signing keys.

Tests explicitly disable the scheduled outbox worker so assertions about queued/leased state are deterministic; worker behavior is covered separately by integration tests.

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
| `AUTH_EMAIL_PRODUCT_NAME` | `Authentication` | optional branding |
| `RESEND_API_KEY` | not used by local/test sender | required secret in `prod` |
| `RESEND_FROM_ADDRESS` | `onboarding@resend.dev` outside prod | required verified-domain sender in `prod` |
| `RESEND_FROM_NAME` | `Authentication` | optional |
| `RESEND_REPLY_TO` | empty | optional |
| `RESEND_BASE_URL` | `https://api.resend.com` | normally leave unchanged |
| `RESEND_CONNECT_TIMEOUT` | `PT3S` | optional positive duration |
| `RESEND_READ_TIMEOUT` | `PT5S` | optional positive duration |
| `RESEND_WEBHOOK_SIGNING_SECRET` | development-only value | required Resend/Svix `whsec_...` secret in `prod` |
| `RESEND_WEBHOOK_TOLERANCE` | `PT5M` | optional positive replay window |
| `EMAIL_OUTBOX_WORKER_ENABLED` | `true` | normally `true`; disable only for controlled worker separation/maintenance |
| `EMAIL_OUTBOX_ACTIVE_KEY_ID` | `local-v1` | required non-secret identifier |
| `EMAIL_OUTBOX_ACTIVE_KEY` | development-only AES key | required Base64-encoded 32-byte secret |
| `EMAIL_OUTBOX_PREVIOUS_KEY_ID` | empty | optional during key rotation |
| `EMAIL_OUTBOX_PREVIOUS_KEY` | empty | optional Base64-encoded previous key; configure together with previous id |
| `EMAIL_OUTBOX_POLL_INTERVAL` | `PT2S` | optional positive duration |
| `EMAIL_OUTBOX_LEASE_DURATION` | `PT30S` | optional positive duration; must exceed expected provider-call ownership window |
| `EMAIL_OUTBOX_BASE_BACKOFF` | `PT5S` | optional positive duration |
| `EMAIL_OUTBOX_MAX_BACKOFF` | `PT5M` | optional positive duration; cannot be below base backoff |
| `EMAIL_OUTBOX_BATCH_SIZE` | `20` | optional `1..100` |
| `EMAIL_OUTBOX_MAX_ATTEMPTS` | `8` | optional `1..20` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` in `local`; empty otherwise | set explicit browser origins when needed |
| `TRUSTED_PROXY_ADDRESSES` | empty | configure only known proxy/load-balancer addresses |
| `RATE_LIMIT_ENABLED` | `true` | optional |

Each protected flow exposes independent `RATE_LIMIT_*_LIMIT` and `RATE_LIMIT_*_WINDOW` variables in `application.yml`. Login has both a client policy and a separate account policy.

Secrets such as JWT private keys, Resend keys, webhook secrets and outbox AES keys must come from the deployment secret store rather than Git or container images.

## Verification and CI

Run the complete verification suite:

```bash
./mvnw verify
```

Coverage includes:

- PostgreSQL-backed authentication/session/refresh flows;
- Redis-backed rate-limit behavior;
- concurrent one-time-token issuance;
- RFC 9457 HTTP contract checks;
- browser CORS preflights;
- legacy BCrypt migration;
- transactional email composition and Resend HTTP/idempotency/error classification;
- AES-GCM round-trip, tamper detection and previous-key decryption;
- atomic token/outbox persistence and superseded-message cancellation/scrubbing;
- lease ownership/reclaim and retry/dead-letter behavior;
- verified/idempotent Resend webhook ingestion, including the official Svix reference signature vector;
- ArchUnit layered dependency rules.

Maven Enforcer requires Java 25 and a supported Maven 3.9.x toolchain. GitHub Actions runs Maven verification and builds the application container image. Third-party workflow actions are pinned to immutable commit SHAs and the workflow token is restricted to read-only repository contents.

## Security boundaries and future extensions

The template now includes durable encrypted email delivery and verified provider callbacks, but it intentionally does not yet implement MFA/passkeys, social/OIDC login, a JWT `kid`/JWK signing-key ring, immediate per-request revocation checks for already-issued access JWTs, full security-audit/telemetry pipelines, long-term metadata purge jobs, or OpenAPI documentation for every contract.

Generic recovery responses reduce direct account enumeration but do not attempt artificial response-time equalization. If bearer credentials are ever moved to cookies, revisit CSRF protection. If deployment topology changes, update `TRUSTED_PROXY_ADDRESSES` deliberately instead of trusting arbitrary forwarded headers.
