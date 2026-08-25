# Security audit and observability

This template treats security audit telemetry as an operational detection and forensic signal. It is **not** an immutable compliance ledger and authentication availability does not depend on a remote telemetry backend.

## Signals

### Structured audit logs

Production console logging uses Spring Boot's Logstash JSON format. Security-relevant events are written through the dedicated `security.audit` logger with stable key/value fields:

- `event.name`
- `event.category=authentication`
- `auth.action`
- `auth.outcome`
- `auth.reason`
- optional `user.id`
- optional `session.id`

Spring/Micrometer tracing contributes trace/span correlation to logging context when a request span exists. The application therefore uses the distributed trace as the request-correlation primitive instead of inventing a second unrelated request-id system.

### Metrics

`auth.security.events` is a counter tagged only with bounded enums:

- `action`
- `outcome`
- `reason`

Never add email addresses, user ids, session ids, IP addresses, user agents, token ids, routes containing ids, exception messages or any other unbounded value as a metric tag. This rule protects the metrics backend from cardinality explosion.

The Actuator metrics endpoint is exposed but requires `ROLE_ADMIN`. Health endpoints remain public for orchestrator probes.

### Traces

The current request span receives a static security event name such as `auth.login` and bounded attributes:

- `auth.action`
- `auth.outcome`
- `auth.reason`

User and session ids are intentionally not added to spans by this template. W3C trace propagation is provided by Spring Boot/Micrometer Tracing.

OTLP trace export is opt-in. `OTEL_TRACING_EXPORT_ENABLED` defaults to `false`, so local/test/template startup does not require a collector.

OTLP metric export is also opt-in. Although the OpenTelemetry starter includes an OTLP meter registry, `OTEL_METRICS_EXPORT_ENABLED` defaults to `false` in this template to prevent accidental publication to a local collector. Metrics remain available through the in-process registry and the protected Actuator endpoint.

Spring Boot does not bridge application SLF4J logs to OpenTelemetry logs by default. This template therefore uses structured JSON console logs as its logging transport and does not configure a second OTLP log pipeline.

## Privacy and secret handling

Audit events must never contain:

- passwords or password hashes;
- bearer, refresh, verification or password-reset tokens;
- email addresses;
- request or response bodies;
- Resend API keys, webhook secrets or JWT private keys;
- IP addresses or user-agent strings in this security-audit channel.

Internal UUID user/session identifiers are allowed only in structured audit logs where they are useful for forensic correlation. They are prohibited from metric labels and deliberately omitted from trace attributes.

## Transaction semantics

A success audit event should describe committed state, not an attempted state change.

Operations that are still inside a database transaction publish a Spring application event and consume it with `@TransactionalEventListener(AFTER_COMMIT)`. If the transaction rolls back, the success event is not recorded.

Failed authentication attempts and validation of invalid/replayed credentials are recorded immediately because the surrounding transaction is expected to fail or roll back.

Operations whose transactional service call has already returned successfully can record immediately through the same publisher fallback path because their database commit has already completed.

Metrics and trace enrichment are best-effort auxiliary signals. An instrumentation failure is logged but does not intentionally turn an otherwise valid authentication operation into a client-visible failure.

## Event catalog

| Action | Typical outcomes | Reasons |
| --- | --- | --- |
| `registration` | `success`, `failure` | `none`, `account_already_exists` |
| `login` | `success`, `failure` | `none`, `invalid_credentials` |
| `refresh` | `success`, `failure`, `detected` | `none`, `invalid_refresh_token`, `refresh_token_reuse` |
| `email_verification_request` | `accepted` | `none` |
| `email_verification_confirm` | `success`, `failure` | `none`, `invalid_verification_token` |
| `password_reset_request` | `accepted` | `none` |
| `password_reset_confirm` | `success`, `failure` | `password_reset`, `invalid_password_reset_token` |
| `session_revocation` | `success` | `logout`, `logout_all`, `manual_revocation` |
| `authorization` | `denied` | `authentication_required`, `access_denied` |
| `rate_limit` | `denied`, `failure` | `rate_limit_exceeded`, `rate_limit_backend_unavailable` |

Generic verification-resend and password-reset-request events deliberately contain no account identifier and have the same action/outcome/reason whether or not the supplied email maps to an eligible account. Telemetry must not recreate an account-enumeration side channel that the HTTP contract intentionally avoids.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `OTEL_TRACE_SAMPLE_PROBABILITY` | `0.1` | Probability that request traces are sampled |
| `OTEL_TRACING_EXPORT_ENABLED` | `false` | Enables OTLP trace export |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | OTLP trace endpoint when export is enabled |
| `OTEL_METRICS_EXPORT_ENABLED` | `false` | Enables OTLP metrics export |
| `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT` | `http://localhost:4318/v1/metrics` | OTLP metrics endpoint when export is enabled |
| `DEPLOYMENT_ENVIRONMENT` | `local` | Low-cardinality OpenTelemetry resource environment name |

Production deployments can point traces and metrics at an OpenTelemetry Collector or compatible backend without changing application code. Collector credentials/headers, TLS and vendor-specific routing belong in deployment configuration or collector configuration rather than source code.

## Detection ideas

Useful operational alerts include:

- abnormal increases in `login / failure / invalid_credentials`;
- any sustained increase in `refresh / detected / refresh_token_reuse`;
- spikes in `authorization / denied`;
- increases in `rate_limit / denied / rate_limit_exceeded`;
- any `rate_limit / failure / rate_limit_backend_unavailable` signal;
- divergence between accepted password-reset requests and provider delivery health;
- outbox dead-letter growth or webhook delivery failures from the existing email-delivery subsystem.

Alerts should aggregate bounded dimensions. Do not alert by creating one metric series per user, session, IP or email.
