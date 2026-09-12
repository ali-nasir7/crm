# Nexus CRM - Setup and Configuration Guide

Written for someone who did NOT build this application. Follow it top to bottom.
Every section says: WHAT to configure, WHY, WHERE, where the value comes from, an EXAMPLE,
and whether it is REQUIRED or OPTIONAL.

## Quick checklist

| # | Item | Required? | Where |
|---|------|-----------|-------|
| 1 | Node.js 20+ | Required (frontend + bridge) | nodejs.org |
| 2 | Java 21 + Maven 3.9+ | Required (backend) | adoptium.net, maven.apache.org |
| 3 | PostgreSQL 14+ | Required | database | docker, installer, or cloud |
| 4 | Backend environment variables | Required | `.env` for Docker, or terminal env vars for mvn |
| 5 | Frontend API base URL | Required (only if not same-origin) | `VITE_API_BASE_URL` |
| 6 | Admin account identity | Required on first boot | `CRM_ADMIN_EMAIL`, `CRM_ADMIN_PASSWORD` |
| 7 | Email sending (SMTP account) | Required for email/campaigns/onboarding mail | CRM > Emails > Accounts (UI) |
| 8 | Gmail app password (if Gmail sender) | Required for Gmail SMTP | Google Account settings |
| 9 | DNS: SPF / DKIM / DMARC | Required for production deliverability | your DNS provider |
| 10 | Redis | Optional (rate limiting only) | `docker compose up -d redis` or `CRM_RATE_LIMIT=false` |
| 11 | Android bridge + adb | Required for the Call button only | `bridge/README.md` |
| 12 | Countries / currencies / stages | Pre-configured, changeable in UI | CRM Admin pages |

---

## 1. Environment (what must be installed)

- **Node.js 20 or newer** - runs the frontend dev server, the production frontend build,
  and the Android calling bridge. Check: `node -v`.
- **Java 21** and **Maven 3.9+** - backend only. Check: `java -version`, `mvn -v`.
- **PostgreSQL 14+** - the only database. Nothing else is stored anywhere else.
  Easiest: the included `docker-compose.yml` starts Postgres (and Redis + Mailpit) for you:
  `docker compose up -d postgres redis mailpit`
- **Redis: OPTIONAL.** The CRM does NOT store CRM data in Redis. It is only used by the
  optional rate limiter. Without Redis the app runs fine and logs one warning; to silence it
  set `CRM_RATE_LIMIT=false`, or start Redis.

## 2. Backend configuration

### How the backend reads configuration (IMPORTANT)

The backend reads **environment variables only** - it does NOT read a `.env` file when run
with `mvn spring-boot:run`. Two supported ways:

- **Docker:** values in the root `.env` file are passed through by `docker-compose.yml`.
- **Plain mvn (Windows PowerShell):** set variables in the terminal before starting:
  ```powershell
  $env:CRM_BRIDGE_TOKEN = "some-long-value"
  mvn spring-boot:run
  ```
  `$env:` only affects that window. For a permanent value use `setx NAME "value"` and open
  a NEW terminal afterwards.

### Variables

| Variable | Required | Example | Notes |
|---|---|---|---|
| `CRM_DB_URL` | Yes (or default) | `jdbc:postgresql://localhost:5432/crm` | Postgres JDBC URL |
| `CRM_DB_USERNAME` / `CRM_DB_PASSWORD` | Yes | `crm` / `crm` | Database user |
| `CRM_JWT_SECRET` | **Yes in production** | 64+ random chars | Signs login tokens. `openssl rand -base64 64` |
| `CRM_ENCRYPTION_KEY` | **Yes in production** | 32+ random chars | Encrypts SMTP passwords at rest |
| `CRM_ADMIN_EMAIL` / `CRM_ADMIN_PASSWORD` | First boot | `alinasir.swe@gmail.com` / strong password | The seed admin. Seeding is skipped once users exist |
| `CRM_ADMIN_PASSWORD` | First boot | strong password | CHANGE IT - never ship the default |
| `CRM_CORS_ORIGINS` | Production | `https://crm.yourdomain.com` | Browser origins allowed to call the API |
| `CRM_APP_URL` | Production | `https://crm.yourdomain.com` | Used in links inside emails |
| `CRM_BRIDGE_TOKEN` | For calling | long random string | Shared secret between backend and Android bridge |
| `CRM_BRIDGE_URL` | Optional | `http://127.0.0.1:9090` | Global FALLBACK bridge URL. Per-device URLs are announced automatically by the bridge |
| `CRM_MAIL_*` | Optional | see Email section | Bootstrap mail for onboarding emails (the CRM's own sender accounts are configured in the UI) |
| Mail setup | - | see `docs/MAIL_SETUP.md` | EASY step-by-step: Gmail app password, Mailpit local testing, troubleshooting every mail symptom |
| `CRM_RATE_LIMIT` | Optional | `true` | set `false` ONLY in dev to disable the rate limiter (silences Redis). Keep it enabled in production. |
| `CRM_SEED` | Optional | `true` | Seed permissions/roles/admin on first boot |
| `SERVER_PORT` | Optional | `8080` | Default 8080 |

Database tables are created by **Flyway migrations** (`backend/src/main/resources/db/migration`,
V1..V11) automatically on first start. Never run Hibernate auto-create; `ddl-auto=validate`
checks the schema matches.

## 3. Frontend configuration

- Dev server: `cd frontend && npm install && npm run dev` (http://localhost:5173).
- API base: the app calls `/api/v1` on its own origin by default; in Docker the Vite dev
  server / nginx proxies to the backend. Override with `VITE_API_BASE_URL=/api/v1` if needed.
- Production build: `npm run build` then serve `frontend/dist` (any static host or the
  included Dockerfile).

## 4. Email sending configuration (IN THE CRM UI - not a config file)

The CRM sends email through **sending accounts configured in the UI** (brief: user/admin
selects which account sends):

WHERE: log in as admin > **Emails > Accounts > Connect account**.

| Field | What to put | Where to get it |
|---|---|---|
| Email address | The sender, e.g. `hazeljones.cse@gmail.com` | Your mailbox |
| Display name | e.g. `Hazel from Nexus` | Your choice (shown to recipients) |
| Reply-To | Where replies should land if different | Optional |
| SMTP host | `smtp.gmail.com` (Gmail) or your provider's host | Provider docs |
| Port | `587` (STARTTLS) or `465` (SSL) | Provider docs |
| Username | Usually the full email address | Provider docs |
| Password | **App password** (see below) | Provider settings |
| Daily limit | Keep under provider limits (Gmail: ~500/day) | Provider policy |

Then click **Verify** (performs a REAL SMTP login test) and **Send test email** (sends a
real message to any address). SMTP errors are shown verbatim in the toast so problems are
diagnosable. Passwords are stored encrypted (AES with `CRM_ENCRYPTION_KEY`) and are never
returned by the API.

### Gmail app password (do NOT use your normal Gmail password)

1. myaccount.google.com > **Security** > enable **2-Step Verification**.
2. Search "App passwords" (myaccount.google.com/apppasswords).
3. Create one (name: "Nexus CRM"). Google shows a **16-character password**.
4. Use that 16-char value as the SMTP Password in the CRM. Never write it in code or Git.

## 5. DNS / email deliverability (production - REQUIRED for inbox delivery)

No one can GUARANTEE spam-free delivery, but these records are what make legitimate mail
deliver reliably. Add them at your DNS provider (Where: whatever host serves your domain,
e.g. Cloudflare/GoDaddy/Route53):

- **SPF** - authorizes your SMTP provider to send for your domain.
  WHERE to get the value: your email provider's docs (Gmail: `v=spf1 include:_spf.google.com ~all`).
  Add as a TXT record on the domain root.
- **DKIM** - cryptographic signature proving the mail was not altered.
  WHERE: your provider generates the key. In Google Admin: Apps > Google Workspace >
  Gmail > Authenticate email > generate, then add the TXT record it shows
  (host like `google._domainkey`, long TXT value).
- **DMARC** - tells receivers what to do when SPF/DKIM fail.
  Start with monitoring: TXT record, host `_dmarc`, value
  `v=DMARC1; p=none; rua=mailto:dmarc@yourdomain.com` - then move to `p=quarantine`
  once reports look clean.
- **Practices already implemented in the CRM:** correct From + Reply-To headers, proper
  MIME (HTML + text alternative), UTF-8 subject encoding, suppression list enforcement
  (unsubscribed/bounced addresses are never emailed), per-account daily limits, campaign
  throttling, no fake/malformed headers.

## 6. CRM configuration (in the UI)

- **Countries:** one shared list in `frontend/src/lib/constants.ts` (Pakistan, United States,
  United Arab Emirates pinned at top, full list below). Used by leads, companies, imports.
- **Currencies:** `PKR`, `USD`, `AED` (+ EUR, GBP, SAR) in the same file; default currency is
  set in Admin > Settings; each deal can pick its own currency.
- **Lead stages:** Admin > Pipelines (create/rename/reorder). The default pipeline follows the
  flow: New > Qualification > Contacted > Follow-up > Meeting > Proposal > Negotiation > Won/Lost.
- **Roles & permissions:** Admin > Roles - 5 system roles, per-permission checkboxes, data
  scopes (ALL / ORG / TEAM / OWN). Sales Reps automatically see only their assigned leads.
- **Automations:** Admin > Automations (rules engine) + the built-in reminder: a lead that
  reaches score 50+ (HOT) automatically gets a 3-day no-reply reminder task for the lead's
  owner; after 3 days without an inbound reply the owner gets an email (subject
  `[BusinessName] Reminder`). Requires one VERIFIED SMTP sender account.
- **Campaigns:** Campaigns > New campaign (3-step dialog) > Add recipients > Start.
- **Meetings:** every meeting can store a meeting link (Meet/Zoom/Teams/custom). JOIN opens
  that exact URL.

## 7. Calling configuration (Android bridge)

Full step-by-step: **bridge/README.md**. Short version:

- Phone: Android with Developer options > USB debugging (or Wireless debugging), connected
  to the PC running the bridge.
- PC: Node.js + adb (SDK Platform Tools) + the bridge: `node bridge/android-bridge.js`
  configured via `bridge/.env` (BRIDGE_TOKEN = same as `CRM_BRIDGE_TOKEN`,
  BRIDGE_DEVICE_ID = device id from CRM > Calls > My calling devices, CRM_API_URL,
  BRIDGE_PUBLIC_URL).
- No Twilio/SIP/VoIP anywhere: calls are placed on your phone's physical SIM; audio is on
  the phone (Phone Link stays the optional PC-audio UX).
- Troubleshooting audio: audio is a phone/SIM matter (signal, headset). The CRM only triggers
  the dial; it never carries audio.

## 8. Import (CSV / XLSX)

- WHERE: Leads > Import (wizard).
- Format: first row = headers (any order - column mapping is suggested and editable).
- Handles: UTF-8 with or without BOM, comma / semicolon / tab delimited, quoted values with
  commas inside, empty fields, 50,000 rows max.
- Required: map at least **Business Name**. Recommended: Email, Phone.
- Duplicates: choose SKIP / UPDATE EXISTING / CREATE ANYWAY - checked inside the file AND
  against existing leads (email, phone, website, LinkedIn, business name).
- After import: totals for imported / duplicates / invalid / failed, per-row errors, and a
  downloadable error CSV.

## 9. Production deployment checklist

- [ ] PostgreSQL with backups (pg_dump cron or managed DB).
- [ ] `CRM_JWT_SECRET` and `CRM_ENCRYPTION_KEY` set to fresh random values (never the dev defaults).
- [ ] `CRM_ADMIN_PASSWORD` changed; seed admin's password known only to you.
- [ ] `CRM_CORS_ORIGINS` = your real frontend origin(s) only.
- [ ] HTTPS on both frontend and backend (reverse proxy: nginx/Caddy/ALB).
- [ ] `CRM_APP_URL` = public frontend URL (used in emails).
- [ ] SPF + DKIM + DMARC configured for the sending domain (section 5).
- [ ] Redis started if you want rate limiting (`docker compose up -d redis`, or any local Redis on 6379). Without it the app runs with the rate limiter failing OPEN (one warning per minute) - never disable rate limiting in production.
- [ ] Set `SPRING_PROFILES_ACTIVE=prod` and review `application.yml` overrides.
- [ ] File storage: `CRM_STORAGE_DIR` on a persistent volume (S3-compatible storage is the
      abstraction's production target).
- [ ] The Android bridge runs on the user's PC (it is a desktop companion, NOT a server).
- [ ] Audit logs are append-only - do not grant AUDIT_VIEW to reps.

## 10. Common problems

| Symptom | Cause / fix |
|---|---|
| `Calling provider ANDROID_BRIDGE_V1: NOT configured` in backend log | `CRM_BRIDGE_TOKEN` not set in the backend's environment (see section 2 - mvn does not read .env) |
| Bridge says `CRM rejected the bridge token (401)` | Backend `CRM_BRIDGE_TOKEN` != bridge `BRIDGE_TOKEN`. Make them identical, restart both |
| Bridge says HTTP 400 on heartbeat | `BRIDGE_DEVICE_ID` is not the UUID copied from Calls > My calling devices |
| Call button says Integration Required | Bridge not running or not configured - steps in bridge/README.md |
| Onboarding email not arriving | Mailpit (dev) catches mail at localhost:8025; for real mail connect a VERIFIED SMTP account in Emails > Accounts |
| Redis warning in logs | Optional rate limiter has no Redis: start Redis or set `CRM_RATE_LIMIT=false` |
| "Report could not be loaded" | Session expired or backend down - use Try again / re-login |
