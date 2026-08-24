# HTTP API Contract

The API uses HTTP semantics directly for successful responses and RFC 9457 Problem Details for errors.

## Success responses

Responses are not wrapped in a universal `success/message/data` envelope.

| Situation | Status | Body / headers |
| --- | ---: | --- |
| Read resource or successful login/refresh | `200 OK` | Resource/action DTO |
| Create resource | `201 Created` | Resource DTO when useful |
| Successful command with no representation | `204 No Content` | No response body |

Login and refresh responses include `Cache-Control: no-store` and `Pragma: no-cache` because they contain bearer credentials.

## Authentication endpoints

| Method | Endpoint | Success | Authentication |
| --- | --- | ---: | --- |
| `POST` | `/api/v1/auth/register` | `201 Created` | Public |
| `POST` | `/api/v1/auth/login` | `200 OK` | Public |
| `POST` | `/api/v1/auth/refresh` | `200 OK` | Public; refresh token in request body |
| `POST` | `/api/v1/auth/email-verification` | `204 No Content` | Public |
| `POST` | `/api/v1/auth/email-verification/confirm` | `204 No Content` | Public |
| `POST` | `/api/v1/auth/password-reset` | `204 No Content` | Public |
| `POST` | `/api/v1/auth/password-reset/confirm` | `204 No Content` | Public |
| `POST` | `/api/v1/auth/logout` | `204 No Content` | Bearer access token |
| `POST` | `/api/v1/auth/logout-all` | `204 No Content` | Bearer access token |
| `GET` | `/api/v1/auth/sessions` | `200 OK` | Bearer access token |
| `DELETE` | `/api/v1/auth/sessions/{sessionId}` | `204 No Content` | Bearer access token |

Email-verification resend and password-reset request use the same outward-facing `204` response whether or not the supplied account can perform the requested flow. Clients must not infer account existence from those responses.

Session revocation is ownership-scoped by the authenticated user. Attempting to address another user's session does not expose or mutate that session.

## Client and server errors

Errors use `Content-Type: application/problem+json`.

| Situation | Status |
| --- | ---: |
| Malformed JSON / invalid request syntax | `400 Bad Request` |
| Missing or invalid authentication | `401 Unauthorized` |
| Authenticated but insufficient permission | `403 Forbidden` |
| Resource not found | `404 Not Found` |
| HTTP method not supported | `405 Method Not Allowed` |
| Requested response representation is not acceptable | `406 Not Acceptable` |
| Current resource state conflicts with request | `409 Conflict` |
| Unsupported request content type | `415 Unsupported Media Type` |
| Syntactically valid request with invalid body fields, object constraints or method parameters | `422 Unprocessable Content` |
| Rate limit exceeded | `429 Too Many Requests` |
| Unexpected server failure or invalid controller return value | `500 Internal Server Error` |
| Required rate-limit backend unavailable | `503 Service Unavailable` |

Authentication failures include `WWW-Authenticate: Bearer` where applicable. Rate-limit responses include `Retry-After`.

Spring MVC errors handled by the framework itself are enriched at the final response-entity boundary so RFC 9457 responses retain the same stable `code` and `timestamp` extensions as application-generated problems.

## Problem Details shape

```json
{
  "type": "urn:cambers:problem:validation-error",
  "title": "Request validation failed",
  "status": 422,
  "detail": "One or more request fields are invalid.",
  "instance": "/api/v1/auth/register",
  "code": "VALIDATION_ERROR",
  "timestamp": "2026-08-24T19:00:00Z",
  "errors": [
    {
      "detail": "must not be blank",
      "pointer": "#/email"
    }
  ]
}
```

`code`, not `detail`, is the stable machine-readable extension. Clients must not branch on human-readable text.

Validation failures may expose an `errors` extension with JSON-pointer-like locations. Field and method-parameter violations use a field/parameter pointer when available; object-level and cross-parameter violations use the root pointer `#`. Internal exception messages and stack traces are not part of the public response contract.

## Token response contract

Successful login and refresh return a token-pair representation containing:

- bearer access token
- opaque refresh token
- token type
- access-token expiration
- refresh-token expiration
- authentication session id

The access token is presented through the `Authorization: Bearer <token>` header on authenticated endpoints. Refresh tokens are not accepted as bearer access credentials.
