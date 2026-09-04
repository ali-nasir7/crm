# Architecture

## 1. System overview

```
┌────────────────────────────────────────────────────────────────────────────┐
│                              Browser (SPA)                                 │
│   React 18 + TS + Vite + Tailwind + TanStack Query + React Hook Form       │
└───────────────▲───────────────────────────────────────────▲────────────────┘
                │ HTTPS / JSON / SSE                        │ static assets
┌───────────────┴───────────────────────────────────────────┴────────────────┐
│                        Nginx (frontend container)                          │
│            /  → SPA     /api → backend:8080 (reverse proxy)                │
└───────────────▲────────────────────────────────────────────────────────────┘
                │
┌───────────────┴────────────────────────────────────────────────────────────┐
│                     Backend — Spring Boot modular monolith                 │
│                                                                            │
│  Security layer     JWT filter → UserPrincipal (org, roles, permissions)   │
│                     Method security (@PreAuthorize hasAuthority)           │
│                     Redis rate limiting (login + API buckets)              │
│                                                                            │
│  Modules (package-per-domain, Controller→Service→Repository)               │
│   identity/org · companies · contacts · leads · pipeline · activities      │
│   calls · tasks · meetings · email · campaigns · deals · proposals         │
│   clients · import · documents · notifications · audit · search            │
│   reports · automation · ai                                                │
│                                                                            │
│  Cross-cutting      Tenant scoping (Hibernate filter + Specification)      │
│                     JPA auditing (created/updated by/at, soft delete)      │
│                     AOP audit log · Global exception handler · Async jobs  │
└──────┬───────────────────────┬───────────────────────────┬─────────────────┘
       │                       │                           │
┌──────▼───────┐      ┌────────▼────────┐        ┌─────────▼──────────┐
│ PostgreSQL16 │      │   Redis 7       │        │ SMTP (MailHog dev, │
│ Flyway       │      │  cache, rate    │        │  prod: SES/Postmark│
│ migrations   │      │  limits, SSE    │        │  or Gmail/M365 OAuth│
└──────────────┘      │  fan-out        │        └────────────────────┘
                      └─────────────────┘
```

## 2. Key decisions and WHY

### 2.1 Modular monolith, not microservices
WHY: a single deployable with strict package-per-domain boundaries (Controller→Service→Repository
inside each module) gives the fastest development velocity and the simplest ops story for an internal
sales team, while the module boundaries keep every domain independently testable and extractable into
services later if scale demands it. Microservices now would add distributed-transaction, networking
and observability cost with zero benefit at 100k–1M lead scale, which a single well-indexed
PostgreSQL handles comfortably.

### 2.2 Multi-tenancy: shared schema + `organization_id` discriminator
Every business table carries `organization_id`. Enforcement is layered:
1. **No trust in input** — the tenant id comes only from the JWT (`UserPrincipal.orgId`), never from
   request parameters.
2. **Hibernate `@Filter`** on the `TenantEntity` base class, enabled around every service call by an
   aspect — a safety net if a query forgets explicit scoping.
3. **Repository convention** — list queries are built via Specifications that always AND
   `organization_id = :org`.
4. **Integration tests** prove a user from org B cannot read org A data (`TenantIsolationIT`).

WHY shared-schema: the initial deployment is single-company; shared-schema is the cheapest design
that still guarantees isolation and makes the SaaS leap a configuration/deployment exercise rather
than a rewrite. Schema-per-tenant would multiply migration cost; a dedicated cluster per large
customer remains possible later without code change (one DB per tenant, same schema).

### 2.3 UUID primary keys
WHY: non-enumerable public ids (safe in URLs), merge-friendly imports, and distributed-generation
without coordination. Performance concern of random UUIDs in indexes is mitigated by keeping indexes
narrow and adding `created_at` composite indexes for range scans.

### 2.4 Flyway migrations only
WHY: `ddl-auto` is never acceptable for production; versioned, reviewable SQL migrations give
deterministic deploys and rollbacks. Hibernate is configured `validate`.

### 2.5 Stateless JWT auth + rotating refresh tokens
Access token: 15 min JWT (HS512) carrying `userId`, `orgId`, role keys and granular permission keys.
Refresh token: opaque 256-bit random value, stored **hashed** (SHA-256) in `refresh_tokens`, rotated
on every use with **reuse detection** (presenting a revoked token revokes the whole lineage).
WHY: stateless request auth scales horizontally; opaque refresh tokens allow immediate revocation
(logout, lockout, compromise) which pure-JWT refresh cannot do.

### 2.6 RBAC + granular permissions + data-visibility scopes
- 5 seeded system roles (SUPER_ADMIN, ADMIN, SALES_MANAGER, SALES_REP, VIEWER) mapped to ~45
  permission keys (`LEAD_CREATE`, `CAMPAIGN_SEND`, `AUDIT_VIEW`, …). Permissions are data, not code:
  custom roles are assignable per organization.
- HTTP layer: `@PreAuthorize("hasAuthority('LEAD_CREATE')")` — authorities are the permission keys.
- Data layer: visibility scopes OWN / TEAM / ORG per role, translated into Specifications
  (`LeadAccessPolicy`), so a rep only ever receives their own leads even if they craft arbitrary
  filter combinations.
WHY both layers: role names alone cannot express "can export but not delete"; permissions alone
cannot express "only my team's records". The combination is the industry-standard pattern.

### 2.7 Audit trail as a first-class subsystem
Append-only `audit_logs` written via (a) an AOP aspect for annotated service methods and (b) explicit
domain events for sensitive flows (login, export, permission change). No update/delete API exists for
audit rows; the service account has no code path that mutates them.
WHY: the spec's core promise — "know exactly what happened with every lead" — requires an immutable
historical record independent of entity tables.

### 2.8 Background work: thread pools + job tables (broker optional)
Imports, bulk operations, campaign sending and report generation run on dedicated `@Async` executors
and track progress in database job tables (`import_jobs`, `bulk_jobs`, `campaign_recipients`),
making them observable, resumable and safe against restarts.
WHY not Kafka/RabbitMQ on day 1: at current scale a bounded executor + durable job tables gives the
same user-visible behaviour (non-blocking API, progress, retries) with radically less operational
surface. Every async entry point is a small interface (`JobExecutor`-style), so swapping in a Kafka
consumer later touches infrastructure only, not domain logic.

### 2.9 Email: provider abstraction, not one vendor
`EmailProvider` interface with `SmtpEmailProvider` implemented (works with MailHog in dev, SES
SMTP/Postmark in prod) and `GmailEmailProvider` / `MicrosoftGraphEmailProvider` scaffolds marked
`TODO / Integration Required` (they require OAuth apps + token storage which are product decisions).
Credentials are encrypted at rest (AES-256-GCM, key from env). Sending always checks the
organization suppression list (unsubscribes/bounces). Open/reply tracking uses a per-email tracking
id and a public pixel endpoint; provider webhooks for delivery/bounce events are scaffolded.
WHY: the spec demands no hard-coded provider and explicit anti-spam compliance.

### 2.10 AI as a pluggable, audited assistant
`AiProvider` port with a deterministic `RuleBasedAiProvider` fallback (works offline: lead summaries
and next-best-action derived from CRM data) and an OpenAI-compatible HTTP adapter that activates only
when `CRM_AI_API_KEY` is set. Every AI call is persisted to `ai_actions` (who, what lead, prompt
kind, output) and all outputs are presented as *drafts for human review* — the CRM never
auto-sends AI content.
WHY: avoid vendor lock-in; keep a human in the loop; make AI usage auditable.

### 2.11 Frontend architecture
- **Feature-sliced** (`src/features/<domain>`), with a thin shared `ui` kit (shadcn-style components:
  Dialog, Table, Tabs, Toast, …) — no UI framework lock-in.
- **TanStack Query** is the single source of server state: caching, retries, optimistic updates where
  safe, and invalidation on mutations. Component state stays local.
- **Zod schemas** mirror API DTOs; React Hook Form binds forms to them — validation exists in both
  browser and server and can never drift.
- Route-level code-splitting; permission-aware navigation (menu items hidden when the user lacks the
  permission, and guarded at the router level too).
WHY: this is the current mainstream production stack for CRUD-heavy dashboards; every choice is
highly hireable and well-documented.

### 2.12 API conventions
- Versioned path prefix `/api/v1/…`.
- Pagination envelope: `{content, page, size, totalElements, totalPages}` everywhere a list is
  returned. No endpoint ever returns unbounded collections.
- Errors: consistent envelope `{code, message, details, traceId}` with correct HTTP statuses
  (400 validation, 401, 403, 404, 409, 422 business rule, 429 rate-limited, 500 with trace id).
- Filtering via query parameters translated to JPA Specifications server-side (composable, type-safe,
  SQL-injection-safe).

### 2.13 Caching & real time
Redis is used for: login/API rate limiting (sliding window), short-TTL dashboard counters, and
pub/sub fan-out for the in-app notification SSE stream. All uses are fail-open (the app runs
degraded, not down, if Redis is unavailable).

### 2.14 Observability
Spring Boot Actuator (`/health`, `/metrics`, `/prometheus`-ready), structured JSON logging with
trace ids (logback), a `DatabaseHealthIndicator` and `RedisHealthIndicator`, plus job tables that
double as background-work monitoring. Error tracking is architecture-ready (Sentry hook point
documented in DEPLOYMENT.md).

## 3. Backend module map

```
com.crm
├── CrmApplication / config / common (errors, pagination, auditing, jsonb utils)
├── security      JWT, filters, principal, rate limiting, permission authorities
└── modules
    ├── identity     users, roles, permissions, teams          (org-scoped)
    ├── organization tenant + org settings + bootstrap seeding
    ├── companies    companies + ownership
    ├── contacts     people under companies / leads
    ├── leads        lead entity, specs, scoring, tags, saved views, bulk ops,
    │                conversion to client, custom fields
    ├── pipeline     pipelines, stages, stage history
    ├── activity     unified activity timeline (all event types)
    ├── calls        call log + metrics
    ├── tasks        follow-ups / task management
    ├── meetings     meetings (calendar-ready)
    ├── email        accounts (provider abstraction), messages, templates,
    │                tracking, suppression list
    ├── campaigns    campaign + steps + recipients + worker
    ├── deals        opportunities/deals + pipeline math
    ├── proposals    proposals + items + PDF rendering
    ├── clients      converted accounts
    ├── import       CSV/XLSX wizard backend (parse→map→validate→dedupe→run)
    ├── documents    storage abstraction (local fs impl; S3 impl hook)
    ├── notifications in-app + SSE (+ email hook)
    ├── audit        append-only audit log + AOP
    ├── search       global cross-entity search
    ├── reports      dashboard/report aggregations + CSV export
    ├── automation   trigger→condition→action rules engine
    └── ai           AiProvider port + rule-based impl + usage log
```

Dependency rule: modules may depend on `common/security`, and on other modules only through their
service interfaces — never on another module's repository or internals. (Enforced by convention and
review; ArchUnit rules are a straightforward follow-up.)

## 4. Request lifecycle example — "salesperson logs a call"

1. `POST /api/v1/leads/{id}/calls` with JWT → `JwtAuthFilter` authenticates, `UserPrincipal` set.
2. Rate-limit filter checks Redis bucket (per-user).
3. `@PreAuthorize("hasAuthority('CALL_CREATE')")` passes.
4. `CallService.create()`:
   - loads lead through `LeadAccessPolicy` (rep sees only own leads → 404 if not owner),
   - persists `Call`, updates `lead.lastContactedAt`, optionally sets `nextFollowUpAt`,
   - appends an `Activity` row (type CALL) so the timeline stays unified,
   - writes `audit_log` entry, emits notification to the lead's owner if different actor,
   - automation engine evaluates "call logged with no answer → create follow-up task in 2 days".
5. Response 201 with the call DTO; SSE pushes the timeline event to any open lead page.

## 5. Scaling path (design now, act later)

| Pressure | Response |
|----------|----------|
| Read load | PostgreSQL read replicas + query-level routing; Redis caching already in place |
| Write/import load | Batch inserts (JDBC batching enabled), partition `activities`/`audit_logs` by month |
| Background jobs | Swap executors for Kafka/RabbitMQ consumers behind the same job-table contract |
| Multi-region | Stateless API behind LB; RDS/Aurora + read replica; Redis cluster |
| SaaS | Per-tenant DB deployment option (same schema), org-level encryption keys |
