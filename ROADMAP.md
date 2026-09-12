# Roadmap & implementation status

The spec is phased (§58). This file states exactly what is implemented, what is scaffolded, and what
is deliberately marked **TODO / Integration Required** — no pretending.

## Frontend ✅
- React 18 + TypeScript + Vite + Tailwind, TanStack Query, React Hook Form + Zod
- 33 screens: login, executive/team dashboards, My Day, leads list w/ saved views + bulk ops,
  lead detail (timeline + calls + emails + tasks + meetings + deals + proposals + documents tabs),
  import wizard (upload → mapping → strategy → summary → error download + history), kanban
  pipeline with drag & drop, deals w/ weighted forecast, tasks, calls, companies, contacts, clients,
  emails + templates + accounts + suppressions, campaigns + recipient detail, meetings, proposals
  (line-item editor + PDF), reports (7 types + CSV/Excel/PDF export), documents, and the full admin
  panel (users, teams, roles, pipelines, tags & sources, custom fields, scoring, automations, audit,
  org settings)
- Global search (⌘K), notification bell with unread polling, toasts, permission-gated navigation

## Phase 0 — Foundation ✅
- Modular monolith structure, config, global error handling, pagination, OpenAPI
- Flyway schema (V1–V7), UUID pk, tenancy columns, soft delete, auditing columns, index plan
- Docker Compose stack (PG, Redis, Mailpit, backend, frontend), .env.example, CI

## Phase 1 — Core CRM ✅
- Auth: JWT access + rotating refresh with reuse detection, lockout, rate limiting, change password
- RBAC: 5 system roles + 85 granular permission keys + role editing; method security
- Data visibility: OWN/TEAM/ORG scopes via Specifications (reps see only their leads)
- Multi-tenant isolation (Hibernate filter + IT proof)
- Users, Teams, Roles, Permissions, Org settings admin APIs
- Companies, Contacts CRUD + search
- Leads: full profile (spec fields + clinic niche via custom fields), search/filter/sort/pagination,
  assignment, tags, sources, statuses, saved views, lead scoring (configurable rules), export CSV
- Pipelines: configurable pipelines + stages + reorder + stage history w/ time-in-stage
- Activities: unified timeline (all types) per lead
- Calls: log + outcomes + metrics
- Tasks: follow-ups, priorities, due dates, dashboards buckets
- Meetings: scheduling + link + participants
- Import: CSV/XLSX upload → header detection → mapping → preview → validation → duplicate detection
  (email/phone/website/linkedin/company rules) → async import → summary + error CSV download
- Bulk operations: async bulk jobs (assign, stage, status, tag, delete)
- Notifications: in-app + SSE stream
- Audit logging: append-only, viewer API, AOP + explicit events
- Global search, Executive/Rep/Team dashboards, Reports + CSV export
- Documents: storage abstraction + local provider

## Phase 2 — Sales execution ✅ (email OAuth = TODO)
- Deals/opportunities with pipeline math (value, weighted, won/lost)
- Lead conversion: lead → company + contact + client + deal, history preserved, idempotent
- Clients: profiles, statuses, revenue rollups
- Proposals: builder + line items + totals + status lifecycle + server-rendered PDF (OpenPDF)
- Email: EmailProvider abstraction; SMTP provider fully working; per-user accounts with encrypted
  credentials; templates with `{{variables}}`; send-from-CRM as activities; open/reply tracking
  pixel + webhook scaffold; suppression list enforced on every send
- Campaigns: sequences (step + delay), recipients, background sender worker honoring schedule,
  pause/resume/cancel, stats

## Phase 3 — Automation & AI ✅/TODO
- Automation rules: trigger → condition → action engine (LEAD_CREATED, STAGE_CHANGED,
  NO_REPLY_AFTER, TASK_OVERDUE / CREATE_TASK, ADD_TAG, NOTIFY, SEND_EMAIL placeholder) + seeded
  examples ✅
- AI: AiProvider port + deterministic RuleBasedAiProvider (summaries, next-best-action, email drafts)
  working offline ✅; OpenAI-compatible adapter scaffolded, activates with `CRM_AI_API_KEY` —
  **TODO / Integration Required** for production key + prompt hardening ✅/TODO

## Phase 4 — SaaS & hardening 🔜 scaffolded
- Tenant isolation ✅ (shared-schema design) — per-tenant DB deployment option, billing, subscription
  management: TODO
- Partitioning of activities/audit by month at >10M rows: documented (DATABASE.md)
- MFA/SSO: reserved fields + TODO
- Gmail/M365 OAuth senders, telephony (Twilio) inbound/outbound call providers: architecture present
  (provider interfaces), implementations **TODO / Integration Required**

## Test matrix (critical cases from §52)
| # | Test | Where |
|---|---|---|
| 1 | User cannot access another organization's data | `TenantIsolationIT` |
| 2 | Rep cannot access unauthorized leads | `LeadAccessPolicyIT` |
| 3 | Manager can access team leads | `LeadAccessPolicyIT` |
| 4 | Admin can manage users | `RbacIT` |
| 5 | Import handles duplicates | `LeadImportIT` |
| 6 | Conversion preserves history | `LeadConversionIT` |
| 7 | Campaign cannot send to unsubscribed contacts | `CampaignSuppressionIT` |
| 8 | Unauthorized users cannot send emails | `RbacIT` |
| 9 | Proposal permissions work | `RbacIT` |
| 10 | Audit logs generated correctly | `AuditLogIT` |

Plus unit tests for JWT, scoring, duplicate detection, template personalization, CSV mapping.
