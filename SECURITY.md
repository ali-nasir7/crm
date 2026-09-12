# Security

## Authentication

* **Access token**: JWT (HS512, 15 min TTL) signed with `CRM_JWT_SECRET` (≥ 64 random chars in
  production). Claims: `sub` (user id), `org` (tenant id), `roles`, `perms` (granular permission
  keys), `jti`, `iat`/`exp`.
* **Refresh token**: 256-bit opaque random value returned to the client; only its SHA-256 hash is
  stored (`refresh_tokens`) with 30-day expiry. **Rotation on every use**; presenting a revoked or
  unknown token revokes the entire token lineage (reuse detection = stolen-token response).
* **Passwords**: BCrypt (cost 12). Minimum 10 chars with letter+digit requirement at registration and
  change-password. Password fields are never returned by any endpoint or logged.
* **Account lockout**: exponential lockout after repeated failed logins (5 fails → 15 min, capped),
  recorded on the user row and audited.
* **Rate limiting**: Redis sliding-window buckets — strict on `/auth/login` (per email+IP), general
  bucket per user/IP on the API. Fails open with a warning if Redis is down (documented trade-off).

## Authorization (RBAC)

Three enforcement layers, tested by integration tests:

1. **Route/service guard** — `@PreAuthorize("hasAuthority('X')")` where authorities are granular
   permission keys seeded per role (`LEAD_DELETE`, `CAMPAIGN_SEND`, `AUDIT_VIEW`, …). Custom roles
   are first-class: permissions are data.
2. **Data-visibility scope** — each role defines OWN / TEAM / ORG visibility. Translated into JPA
   Specifications (`LeadAccessPolicy`), so queries can never return rows outside scope, regardless of
   crafted filters. A rep requesting another rep's lead id gets `404` (not `403`, to avoid existence
   leaks).
3. **Tenant isolation** — `organization_id` is taken only from the JWT; a Hibernate `@Filter` enabled
   per request is the safety net, and `TenantIsolationIT` proves cross-org access is impossible.

## Input & injection safety

* All write bodies are validated (`jakarta.validation` + Zod on the client).
* JPA/Hibernate parameter binding everywhere — no string-concatenated SQL. Dynamic filters use
  the Criteria/Specification API.
* JSON bodies parsed with a hardened ObjectMapper (enums validated, unknown properties fail fast on
  write DTOs).
* File uploads: extension + content-type allowlist (`.csv`, `.xlsx`), 10 MB cap, magic-byte sniffing
  for xlsx (zip), stored outside the web root under generated keys; download always served with
  `Content-Disposition: attachment` and a fixed content type (no user-controlled headers).

## Transport & headers (Spring Security)

HSTS, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, referrer policy, and a
Content-Security-Policy for the API. CORS is an explicit allowlist from env (`CRM_CORS_ORIGINS`).

CSRF: the API is stateless and token-authenticated (no cookie sessions) — CSRF does not apply to the
API. The SPA stores the access token in memory and the refresh token client-side; the XSS mitigation
stack is CSP + React's escaping + no `dangerouslySetInnerHTML` for user content (email HTML is
rendered in a sandboxed iframe on the client).

## Secrets

* No secret ever lives in the repository. `.env` is git-ignored; `.env.example` documents every var.
* Email-account credentials are encrypted at rest with AES-256-GCM (`CRM_ENCRYPTION_KEY`).
* Production deployment expects Docker/K8s secrets or a vault (see DEPLOYMENT.md).

## Audit & monitoring

* Append-only `audit_logs` capture authentication events, CRUD on sensitive entities, permission
  changes, exports, imports, sends and conversions with actor, ip, user agent and before/after diffs.
* Login failures, lockouts, rate-limit hits and permission denials are logged (structured JSON) and
  visible to admins via the audit viewer.

## Explicit non-goals / TODO-Integration-Required

* Gmail / Microsoft OAuth mail integration (needs registered OAuth apps per deployment) — SMTP
  provider is fully functional; OAuth scaffolds are in place and marked TODO.
* MFA / SSO (SAML/OIDC) — the user model has `mfa_enabled` reserved fields; implementation is a
  planned phase.
* Signed provider webhooks for delivery/bounce events are scaffolded and must be configured with the
  provider's signing secret in production.
