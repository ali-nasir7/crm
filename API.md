# API Reference

Base URL: `/api/v1`. Interactive docs: **`/swagger-ui.html`** (OpenAPI 3 at `/v3/api-docs`).

## Conventions

* **Auth**: `Authorization: Bearer <accessToken>` on every endpoint except the public ones listed
  below. Tokens from `POST /auth/login` / `POST /auth/refresh`.
* **Pagination**: `?page=0&size=20&sort=createdAt,desc` →
  `{ "content": [...], "page": 0, "size": 20, "totalElements": 1042, "totalPages": 53 }`
* **Errors**: `{ "code": "LEAD_NOT_FOUND", "message": "...", "details": {...}, "traceId": "..." }`
  with status `400` validation · `401` unauthenticated · `403` missing permission · `404` · `409`
  conflict · `422` business rule · `429` rate limited.
* All list endpoints accept the documented filters **combined** (AND logic) and are additionally
  constrained by the caller's data-visibility scope (OWN/TEAM/ORG) — a sales rep filtering for
  "all leads" silently receives only their own.

## Public endpoints
| Method | Path | Notes |
|---|---|---|
| POST | `/auth/login` | rate-limited; lockout after repeated failures |
| POST | `/auth/refresh` | rotates refresh token |
| GET | `/track/open/{id}` | 1×1 tracking pixel (public by design) |
| POST | `/track/events` | email provider webhooks (signed; TODO integration) |
| GET | `/actuator/health` | liveness/readiness |

## Auth
| Method | Path | Permission |
|---|---|---|
| POST | `/auth/logout` | authenticated |
| GET | `/auth/me` | authenticated → profile + roles + permissions |
| POST | `/auth/change-password` | authenticated |

## Identity & administration
| Method | Path | Permission |
|---|---|---|
| GET/POST | `/users` | USER_VIEW / USER_CREATE |
| GET/PUT/DELETE | `/users/{id}` | USER_VIEW / USER_UPDATE / USER_DELETE |
| PUT | `/users/{id}/roles` | USER_UPDATE |
| GET/POST | `/teams` | TEAM_VIEW / TEAM_CREATE |
| GET/PUT/DELETE | `/teams/{id}` | TEAM_VIEW / TEAM_UPDATE / TEAM_DELETE |
| POST/DELETE | `/teams/{id}/members[/{userId}]` | TEAM_UPDATE |
| GET | `/roles`, `/permissions` | ROLE_VIEW |
| POST/PUT/DELETE | `/roles[/{id}]` | ROLE_CREATE / ROLE_UPDATE / ROLE_DELETE |
| GET/PUT | `/org` | ORG_VIEW / ORG_UPDATE |
| GET | `/audit-logs` | AUDIT_VIEW (immutable, no write API exists) |

## CRM core
| Method | Path | Permission |
|---|---|---|
| GET/POST | `/companies` | COMPANY_VIEW / COMPANY_CREATE |
| GET/PUT/DELETE | `/companies/{id}` | COMPANY_VIEW / COMPANY_UPDATE / COMPANY_DELETE |
| GET/POST | `/contacts` | CONTACT_VIEW / CONTACT_CREATE |
| GET/PUT/DELETE | `/contacts/{id}` | CONTACT_VIEW / CONTACT_UPDATE / CONTACT_DELETE |
| GET/POST | `/leads` | LEAD_VIEW / LEAD_CREATE |
| GET/PUT/DELETE | `/leads/{id}` | LEAD_VIEW / LEAD_UPDATE / LEAD_DELETE |
| POST | `/leads/{id}/assign` | LEAD_ASSIGN |
| POST | `/leads/{id}/stage` | LEAD_UPDATE |
| POST | `/leads/{id}/status` | LEAD_UPDATE |
| POST | `/leads/{id}/tags` | LEAD_UPDATE |
| POST | `/leads/{id}/notes` | LEAD_UPDATE (creates timeline activity) |
| GET | `/leads/{id}/activities` | LEAD_VIEW |
| GET | `/leads/{id}/timeline` | LEAD_VIEW (unified chronological feed) |
| POST | `/leads/{id}/convert` | LEAD_CONVERT |
| POST | `/leads/bulk` | LEAD_UPDATE (assign/stage/status/tag/delete) → async job |
| GET | `/leads/export` (CSV) | LEAD_EXPORT (audited) |
| GET/POST/DELETE | `/lead-views[/{id}]` | LEAD_VIEW (saved filters, shareable) |
| GET/PUT | `/scoring-rules` | SCORING_VIEW / SCORING_UPDATE |
| GET/POST/PUT/DELETE | `/tags[/{id}]`, `/lead-sources[/{id}]` | reference data |
| GET/POST/PUT/DELETE | `/pipelines[/{id}]` | PIPELINE_VIEW / PIPELINE_UPDATE |
| POST/PUT/DELETE | `/pipelines/{id}/stages[/{stageId}]` | PIPELINE_UPDATE (create/reorder) |

## Activity resources
| Method | Path | Permission |
|---|---|---|
| POST | `/leads/{id}/calls` | CALL_CREATE |
| GET | `/calls`, `/leads/{id}/calls` | CALL_VIEW |
| POST/PUT | `/leads/{id}/tasks`, `/tasks[/{id}]` | TASK_CREATE / TASK_UPDATE |
| POST | `/tasks/{id}/complete` | TASK_UPDATE |
| GET | `/tasks` | TASK_VIEW (filters: mine, due, status, priority) |
| GET/POST/PUT/DELETE | `/meetings[/{id}]` | MEETING_VIEW / MEETING_CREATE |

## Email & campaigns
| Method | Path | Permission |
|---|---|---|
| GET/POST | `/email-accounts` | EMAIL_ACCOUNT_VIEW / EMAIL_ACCOUNT_CREATE |
| PUT/DELETE, POST `/{id}/verify` | `/email-accounts/{id}` | EMAIL_ACCOUNT_UPDATE / EMAIL_ACCOUNT_DELETE |
| GET/POST/PUT/DELETE | `/email-templates[/{id}]` | TEMPLATE_VIEW / TEMPLATE_CREATE / TEMPLATE_UPDATE / TEMPLATE_DELETE |
| POST | `/email-templates/{id}/render?leadId=` | TEMPLATE_VIEW (personalization preview) |
| POST | `/leads/{id}/emails` | EMAIL_SEND (sends via provider, logs activity, respects suppression) |
| GET | `/emails` | EMAIL_VIEW (filters: direction, status, campaign, lead) |
| GET/POST/DELETE | `/suppressions[/{id}]` | SUPPRESSION_VIEW / SUPPRESSION_UPDATE |
| GET/POST | `/campaigns` | CAMPAIGN_VIEW / CAMPAIGN_CREATE |
| GET/PUT/DELETE | `/campaigns/{id}` | CAMPAIGN_VIEW / CAMPAIGN_UPDATE |
| POST | `/campaigns/{id}/recipients` | CAMPAIGN_UPDATE (lead ids or filter) |
| POST | `/campaigns/{id}/start` · `/pause` · `/resume` · `/cancel` | CAMPAIGN_SEND |
| GET | `/campaigns/{id}/stats`, `/recipients` | CAMPAIGN_VIEW |

## Sales
| Method | Path | Permission |
|---|---|---|
| GET/POST | `/deals` | DEAL_VIEW / DEAL_CREATE |
| GET/PUT/DELETE | `/deals/{id}` | DEAL_VIEW / DEAL_UPDATE / DEAL_DELETE |
| POST | `/deals/{id}/stage`, `/deals/{id}/status` | DEAL_UPDATE |
| GET | `/deals/summary` | DEAL_VIEW (pipeline value, weighted, won/lost) |
| GET/POST | `/proposals` | PROPOSAL_VIEW / PROPOSAL_CREATE |
| GET/PUT/DELETE | `/proposals/{id}` | PROPOSAL_VIEW / PROPOSAL_UPDATE / PROPOSAL_DELETE |
| POST | `/proposals/{id}/items`, `/{id}/status`, `/{id}/send` | PROPOSAL_UPDATE / PROPOSAL_SEND |
| GET | `/proposals/{id}/pdf` | PROPOSAL_VIEW (server-rendered PDF) |
| GET | `/clients` | CLIENT_VIEW (converted accounts) |
| GET/PUT | `/clients/{id}` | CLIENT_VIEW / CLIENT_UPDATE |

## Platform
| Method | Path | Permission |
|---|---|---|
| GET | `/search?q=` | SEARCH (global, cross-entity) |
| GET | `/notifications` · POST `/{id}/read` · `/read-all` · GET `/stream` (SSE) | authenticated |
| GET/POST/DELETE | `/documents[/{id}]` (+ `/{id}/download`) | DOCUMENT_VIEW / DOCUMENT_CREATE / DOCUMENT_DELETE |
| GET | `/imports` (history) | IMPORT_VIEW |
| POST | `/imports` (multipart csv/xlsx) | IMPORT_CREATE |
| GET | `/imports/{id}` | IMPORT_VIEW (status, mapping suggestion, summary) |
| GET | `/imports/{id}/rows?status=` | IMPORT_VIEW (preview / invalid rows / duplicates) |
| PUT | `/imports/{id}/mapping` | IMPORT_CREATE (column map + options → runs import async) |
| GET | `/imports/{id}/errors.csv` | IMPORT_VIEW (downloadable error report) |
| GET | `/dashboard/executive` · `/dashboard/me` · `/dashboard/team` | REPORT_VIEW (scoped) |
| GET | `/reports/{type}?from&to&format=csv` | REPORT_VIEW (lead/activity/call/email/pipeline/revenue/conversion/team/source/campaign) |
| POST | `/ai/lead-summary/{leadId}` · `/ai/email-draft` · `/ai/next-action/{leadId}` | AI_USE (all outputs logged, human-reviewed) |
| GET | `/ai/history` | AI_USE |
| GET/PUT | `/settings` | SETTINGS_VIEW / SETTINGS_UPDATE (org: duplicate rules, sending window, targets) |
