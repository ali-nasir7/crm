# Configuration Checklist

Tick every box in order. Details for each step live in `SETUP_AND_CONFIGURATION.md`
(repo root); calling/audio details live in `docs/CALL_AUDIO_TUNING.md`. Do not move
to the next section until the current one is fully ticked.

## 1. Machine prerequisites

- [ ] JDK 17 installed (`java -version` prints 17.x)
- [ ] Maven installed (`mvn -version` works in PowerShell)
- [ ] PostgreSQL 14+ installed and running (`psql --version`)
- [ ] Node.js 18+ installed (`node -v`)
- [ ] adb (SDK Platform Tools) downloaded, path noted (`C:\platform-tools\adb.exe` is fine)

## 2. Database

- [ ] Database created (e.g. `CREATE DATABASE crm;`)
- [ ] Dedicated login user created (NOT the superuser)
- [ ] Credentials stored for step 3

## 3. Backend configuration (`.env` next to `backend/`, or PowerShell `$env:` for one run)

- [ ] `DB_URL`, `DB_USER`, `DB_PASSWORD` set (real values, file saved)
- [ ] `JWT_SECRET` set to a long random string (never committed to Git)
- [ ] `CRM_MAIL_HOST` / `CRM_MAIL_PORT` / `CRM_MAIL_USER` / `CRM_MAIL_PASS` set ONLY if you
      will send onboarding emails before configuring an SMTP account in the UI
      (UI-configured accounts always win; see USER_GUIDE PART 16)
- [ ] `CRM_BRIDGE_TOKEN` set to a long random string (shared with the bridge in step 6)
- [ ] `CRM_BRIDGE_BASE_URL` set only for a single-bridge deployment (e.g. `http://127.0.0.1:8081`);
      leave empty when each user runs their own bridge (per-device URLs are announced by heartbeat)
- [ ] Terminal vs file understood: values in `.env` survive reboots; `$env:` values die
      with the PowerShell window (use `setx` to persist those instead)

## 4. Backend build & first run

- [ ] `mvn clean compile` finishes with BUILD SUCCESS (fix errors before continuing)
- [ ] `mvn test` green
- [ ] `mvn spring-boot:run` starts; Flyway migrates V1..V11 automatically (first run creates schema)
- [ ] `http://localhost:8080/api/v1/health` (or the actuator/health route) answers UP

## 5. First login & admin

- [ ] Onboarding flow completed (first-run admin creation; credentials arrived by email
      or were shown once on screen - store them in a password manager)
- [ ] Logged in; changed the temp password
- [ ] Organization name set as you want it to appear in emails

## 6. Android phone bridge (calling)

- [ ] `bridge/.env` created from the README table: `BRIDGE_TOKEN` = SAME value as the
      backend `CRM_BRIDGE_TOKEN`; `BRIDGE_DEVICE_ID` = copied from Calls > My calling
      devices; `ADB_PATH` set if adb is not on PATH
- [ ] Phone: Developer options + USB debugging enabled; "Allow USB debugging" accepted
- [ ] `adb devices` lists the phone with state `device`
- [ ] `node bridge/android-bridge.js` runs and prints "Phone connected via adb"
- [ ] Device shows ONLINE in the CRM (Calls page > My calling devices)
- [ ] Call Health panel (Calls page): Phone bridge / Android / SIM lines all green
- [ ] `docs/CALL_AUDIO_TUNING.md` scenarios 1-8 ticked (calling logic verified)
- [ ] At least one audio configuration (scenarios 9-14) ticked with both directions OK

## 7. Frontend

- [ ] `cd frontend && npm install`
- [ ] `npm run dev` starts and the login page opens
- [ ] Login works; the footer shows "Built by Ali Nasir"

## 8. Email deliverability (no guarantees - correct config only)

- [ ] SMTP account added in the UI (Admin/Emails > Accounts) with real host/port/user
- [ ] "Verify" clicked -> VERIFIED status shown
- [ ] "Send test email" received in a real inbox
- [ ] SPF record checked at your DNS provider (`v=spf1 include:_spf.google.com ~all`
      style, matching YOUR sender domain)
- [ ] DKIM enabled where the provider offers it (Gmail: default)
- [ ] DMARC DNS record added for your domain
- [ ] Understood: inbox placement is carrier/provider-dependent; this setup gives you
      CORRECT authenticated sending, not a placement guarantee

## 9. Data import

- [ ] Sample CSV imported (leads) with correct column mapping
- [ ] Row counts in the CRM match the file (imported + skipped shown honestly)

## 10. Final verification

- [ ] `bash scripts/smoke-test.sh` run TWICE, both times fully green (second run proves
      idempotency)
- [ ] `node scripts/db-verify.mjs` green against a VERIFIED database
- [ ] Bridge integration test: `node bridge/test/run-tests.js` -> all pass
- [ ] Users created via Admin > Users; reps can log in and see My Day but not Reports/Insights

## When something fails

- Backend compile errors after a manual edit: `git checkout -- .` then `git pull` (restores
  known-good files), or apply the exact file+line fix given in chat - never paste code into
  a "similar looking" file.
- Calling fails: Call Health panel first (every line explains its own fix), then
  `docs/CALL_AUDIO_TUNING.md` section 6 matrix.
- Email fails: Emails > Accounts > Verify, then `docs/` deliverability guide.
