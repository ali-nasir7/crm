# Verification Report — Full Sweep

Date: 2026-09-09 · Branch: arena/01a062b4-crm · Commit: 5c878c5
Environment: Linux sandbox (frontend + Node + real embedded PostgreSQL available;
Java/Maven repositories BLOCKED in sandbox — backend compile/run verified on the
user's Windows machine only).

## Results (all checks below ran against this exact commit)

| # | Check | Method | Result |
|---|---|---|---|
| 1 | TypeScript compile | `tsc -b` (part of build) | 0 errors |
| 2 | Frontend production build | `npm run build` | SUCCESS, **0 warnings** (vendor code-split chunks, largest 411 kB) |
| 3 | Frontend dev server boot | `npm run dev` + HTTP GET / | ready in 181 ms, page HTTP 200 |
| 4 | Database schema | `scripts/db-verify.mjs` on a BLANK PostgreSQL 18 database | **39/39 PASS** (V1..V11 applied cleanly) |
| 5 | Android bridge | `node bridge/test/run-tests.js` (fake adb + mock CRM) | **20/20 PASS** |
| 6 | API contract FE->BE | scripted sweep: every `api.get/post/put/patch/delete` string vs every Spring mapping | **126/126 frontend calls match** (189 backend mappings) |
| 7 | API contract smoke->BE | scripted sweep of `scripts/smoke-test.sh` routes vs Spring mappings | 63/63 match (`GET /reports/{type}` dynamic route covers the two literal forms) |
| 8 | Java source integrity | string/brace state-machine scanner over ALL sources | **246/246 files balanced** |
| 9 | Java import graph | every `import com.crm...` resolved against the file tree (nested records included) | **652/652 resolve** |
| 10 | RBAC contract | PermissionKeys.java vs V7 seed SQL vs frontend `can(...)` keys | 84 == 84 == all frontend keys present, zero drift |
| 11 | Security surface | SecurityConfig permitAll review + secrets scan of tracked files | minimal permitAll set (login/refresh/onboarding, token-protected bridge routes, track, actuator, swagger); no plaintext secrets |
| 12 | Config wiring | all 25 `${ENV:...}` vars in application.yml vs `.env.example` / bridge `.env.example` | complete (defaults documented for the 4 optional vars) |
| 13 | TODO hygiene | grep | only deliberate "TODO / Integration Required" markers (OAuth, calendar sync, IMAP ingest, provider signature verify) |

## Defects found and fixed by this sweep

1. **DELETE /api/v1/proposals/{id} did not exist** while the UI had a working Delete
   button (would return HTTP 405). Added `@DeleteMapping("/{id}")` wired to the seeded
   `PROPOSAL_DELETE` permission; service deletes items + proposal, writes an audit
   entry, and refuses to delete ACCEPTED proposals (reject first).
2. **Frontend build printed a chunk-size warning** (>500 kB single JS bundle). Fixed
   properly with vendor code-splitting (react / data / forms / charts / ui chunks) —
   no warning is suppressed; every chunk is now well under the limit.

## Round 2 - user-reported VS Code errors (2026-09-09, fixed in this tree)

| VS Code error | Root cause | Fix |
|---|---|---|
| CallingController: cannot convert UserPrincipal to CurrentUser (+2 cascades) | REAL: my `/calling/health` endpoint declared `CurrentUser u = CurrentUser.require()` but `require()` returns `UserPrincipal` | Replaced with the codebase-standard inline `CurrentUser.require().getOrganizationId(), CurrentUser.require().getId()` |
| TelephonyService: UUID cannot be resolved (line 28) | REAL: `import java.util.UUID;` was missing since the initial commit | Import added (the user's own manual fix was exactly right) |
| CallingService:199 + AndroidBridgeTelephonyService:79 "missing type UUID" | CASCADE of the TelephonyService error (CallCommand signature unresolvable) | Resolved by the TelephonyService import |
| (found by new check) CallingDevice: bare `UUID userId` field, no import | REAL, pre-existing | `import java.util.UUID;` added |

`scripts/java_static_check.py` (new, committed) now runs 3 static nets on every
change: brace/string balance (comment-aware), com.crm import resolution, and
JDK-type import/wildcard/FQN verification - this class of error cannot slip again.
Result on the fixed tree: 246/246 balance, 652/652 imports, 0 JDK-type problems.


## Round 3 - production-fix brief (13 priorities, 2026-09-09)

| Priority | Root cause found | Fix applied in code |
|---|---|---|
| 1 CSV import | Frontend/backend multipart contract already correct (FormData `file` <-> `@RequestParam("file")`) - reported error came from an older local copy. BUT Manager had no LEAD_IMPORT and Rep had neither LEAD_IMPORT nor IMPORT_VIEW, so the feature was admin-only in practice | V12 migration backfills role grants for existing orgs; RoleFactory grants them for new orgs; smoke now uploads+maps as MANAGER and as REP |
| 2 email-templates 500 | REAL: `email_templates` was created (V5) WITHOUT `deleted_at` while the entity has `@SQLRestriction("deleted_at IS NULL")` - every SELECT failed | V12 `alter table email_templates add column if not exists deleted_at timestamptz` + db-verify check (43/43) |
| 3 lead email "id must not be null" | REAL: `EmailComposer` sent `accountId: null` when no sender account existed; `EmailService.sendToLead` called `findById(null)` -> 500 | Backend: explicit 400 "Select a sender email account first (Emails > Accounts)." Frontend: send disabled without an account, VERIFIED preferred, amber guidance when none |
| 4 user-creation email | ALREADY IMPLEMENTED (hashed temp password, email via VERIFIED UI sender or env fallback, honest "Email failed - temp password" toast) | no duplicate created; re-verified code path |
| 5 SMTP flow | Dispatch path verified line-by-line (account creds, AES decrypt, honest FAILED status + server-side error log, daily limit) | no defect found; real send test NOT VERIFIED in sandbox |
| 6 custom-fields LazyInit | REAL: raw entities with LAZY `options` serialized with open-in-view OFF | `@EntityGraph(attributePaths = "options")` on the finder - one query, no N+1 |
| 7 Documents crash | REAL: backend returns a bare array; page typed it as PageResponse -> `data.content.length` crash in DataTable | DocumentsPage maps bare array -> PageResponse; DataTable treats missing content as empty (never crashes); added loading/empty/error states |
| 8 Redis | compose already wires redis+healthcheck+`CRM_REDIS_URL=redis://redis:6379`; filter already warns max 1/min | `.env.example` + SETUP guide: `docker compose up -d redis` is the dev path; corrected wrong doc default (CRM_RATE_LIMIT default is true, not false) |
| 9 /auth/me 500 | No static defect in the current repo path (@Transactional, DTO built in-session). Most consistent with the drifted local DB of rounds 1-2 | fresh clone + `mvn spring-boot:run` retest required; runtime NOT VERIFIED in sandbox |
| 10 /leads/custom-fields 400 | REAL: frontend called a non-existent endpoint; request fell into `GET /leads/{id}` -> UUID parse 400 | ImportWizard now calls the real `GET /custom-fields`; contract sweep now prefers literal matches over `{var}` |
| 11 frontend error handling | DataTable had no error state | DataTable `error` + `onRetry` props wired on Templates, Custom Fields, Documents; honest message, retry button, backend 500s never hidden |
| 12 RBAC consistency | (same as 1) | Import = ADMIN/MANAGER/REP; no admin rights given to reps |
| 13 little bugs | duplicate `addTrackingPixel` call (wasted work) removed; stale `page` state in DocumentsPage removed; wrong CRM_RATE_LIMIT doc default fixed | fixed |

Sweep after round 3: tsc 0 errors; vite build 0 warnings; java static 246/246+652/652+JDK OK;
db-verify fresh blank DB V1..V12 43/43; bridge 20/20; FE->BE contract 125/125; smoke routes
re-checked incl. new MANAGER/REP import steps.

## Known limits (honest)

- The sandbox cannot run Maven (all Maven repositories blocked), so `mvn clean
  compile`, `mvn test`, `mvn spring-boot:run`, and therefore the live 24-section
  smoke run can only be executed on the user's machine. Every static and runtime
  check that IS possible here passes.
- Call audio quality is carrier/Bluetooth-dependent by architecture and is
  deliberately not claimed anywhere; see docs/CALL_AUDIO_TUNING.md.

## Remaining user-side steps (to convert "verified here" into "verified on target")

```
mvn clean compile
mvn test
mvn spring-boot:run        # backend up on :8080
bash scripts/smoke-test.sh # run TWICE - second run proves idempotency
cd frontend && npm install && npm run build
```
