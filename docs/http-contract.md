# HTTP API Contract

The API uses HTTP semantics directly for successful responses and RFC 9457 Problem Details for errors.

## Success responses

Do not wrap every response in a custom `success/message/data` envelope.

| Situation | Status | Body / headers |
| --- | ---: | --- |
| Read resource or successful login/refresh | `200 OK` | Resource/action DTO |
| Create resource | `201 Created` | Resource DTO when useful; `Location` header when a canonical URI exists |
| Accepted for genuinely deferred processing | `202 Accepted` | Optional status/operation representation |
| Successful command with no representation | `204 No Content` | No response body |

## Client and server errors

Errors use `Content-Type: application/problem+json`.

| Situation | Status |
| --- | ---: |
| Malformed JSON / invalid request syntax | `400 Bad Request` |
| Missing or invalid authentication | `401 Unauthorized` |
| Authenticated but insufficient permission | `403 Forbidden` |
| Resource not found | `404 Not Found` |
| HTTP method not supported | `405 Method Not Allowed` |
| Current resource state conflicts with request | `409 Conflict` |
| Unsupported request content type | `415 Unsupported Media Type` |
| Syntactically valid request with invalid fields/instructions | `422 Unprocessable Content` |
| Rate limit exceeded | `429 Too Many Requests` |
| Unexpected server failure | `500 Internal Server Error` |
| Temporarily unavailable dependency/service | `503 Service Unavailable` when applicable |

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

## Planned authentication semantics

| Endpoint/action | Expected success |
| --- | ---: |
| Register account | `201 Created` |
| Login | `200 OK` |
| Refresh access token | `200 OK` |
| Logout current session | `204 No Content` |
| Revoke one session | `204 No Content` |
| Revoke all sessions | `204 No Content` |
| Verify email | `204 No Content` |
| Reset password | `204 No Content` |
| Request password reset | `202 Accepted` only if delivery is actually deferred; otherwise `204 No Content` |

Authentication-sensitive endpoints must use generic outward-facing responses where revealing whether an account exists would enable account enumeration.
