# Nexus CRM - Operations Manual (How to actually use this software)

Audience: you (owner/operator) and everyone who will log in.
Read Part 1-2 once (15 minutes). Then keep Part 3-5 open your first week.

---

## PART 1 - THE BIG PICTURE (what this machine does)

You built a Sales Operating System. Its whole job is to answer four questions every minute
of the workday:

1. WHO do I contact next?          -> My Day, Hot leads, Tasks, Pipeline board
2. WHAT happened with them?        -> Lead detail: Timeline, Calls, Emails, Tasks tabs
3. WHAT should happen next?        -> Next action + follow-up date + automations
4. HOW IS THE BUSINESS DOING?      -> Executive dashboard, Reports, Deals forecast

The core object is a LEAD (a clinic/business that might buy). Everything hangs off it:

```
LEAD  --gets-->  ACTIVITIES (calls, notes, emails, meetings, tasks)
      --moves--> STAGES of the PIPELINE (New Lead ... Won/Lost)
      --has-->   SCORE (how hot), TAGS, SOURCE, OWNER (assigned user)
      --becomes--> COMPANY + CONTACT + CLIENT (+ DEAL) when won
```

Everything else (users, roles, scoring rules, custom fields, automations, campaigns,
templates, documents, audit log) exists to support that one journey.

### The one golden rule of the system
A lead must always have THREE things: an OWNER (who works it), a STAGE (where it is),
and a NEXT STEP (task, follow-up date, or call outcome). If a lead has no next step,
it is already dead - the CRM just has not told you yet.

---

## PART 2 - THE PIPELINE (your 14 stages, what each one MEANS)

Seeded for you (Pipelines page, editable by admin):

| # | Stage | Meaning | Move here when... |
|---|-------|---------|--------------------|
| 1 | New Lead | Untouched raw record | It was created/imported |
| 2 | Qualified | Fits our ideal profile | Clinic in target country, has real contact info |
| 3 | Assigned | An owner picked it up | Rep opens it and plans first touch |
| 4 | Contacted | First outbound made | Call logged or email sent (1st touch) |
| 5 | Connected | Two-way contact | A human replied / picked up |
| 6 | Interested | Verbal interest | They asked about pricing/features |
| 7 | Discovery Call | Deep needs call done | You know their pain, staff count, software |
| 8 | Demo | Product demo done | They SAW the solution |
| 9 | Offer Sent | Quote/offer delivered | Offer document sent |
| 10 | Proposal Sent | Formal proposal in | Proposal created + SENT in Proposals page |
| 11 | Negotiation | Terms being discussed | They are bargaining, legal, procurement |
| 12 | Won | Paying customer | Money confirmed -> CONVERT the lead |
| 13 | Lost | Dead | Lost with a reason (see Reports) |
| 14 | Nurture | Not now, later | "Call me next quarter" - keep warm |

Each stage also carries a PROBABILITY (5%..100%) used for the weighted forecast on the
manager dashboard. Edit per stage on the Pipelines admin page.

---

## PART 3 - ADMIN: the builder role (first-day checklist)

You log in as Admin. Your job: build the factory, then let sales run it. Do these once,
in order (Admin section in the left sidebar):

1. **Users** (Admin > Users): create every rep and manager with email + password + role.
   Roles: SUPER_ADMIN (you), SALES_MANAGER, SALES_REP, VIEWER (read-only).
   The "reset password" button generates a temp password - it is shown once in a green
   toast, copy it and give it to the person.
2. **Teams** (Admin > Teams): create "Dubai Team", "Abu Dhabi Team" etc. Teams power the
   TEAM data-visibility mode (a rep can see teammates' leads, not the whole org).
3. **Pipelines & Stages** (Admin > Pipelines): your 14 stages are ready. Rename/reorder/
   change probabilities to match your real sales motion. Do not create 5 pipelines for
   fun - one company usually needs ONE.
4. **Custom Fields** (Admin > Custom Fields): your clinic-niche fields are seeded
   (Clinic Type, Services, Locations count, Doctor/Owner, Website quality, Existing
   software, CRM used, Estimated opportunity, Pain points, Automation opportunity).
   Add/edit to match what you NEED to know before a demo. These appear on the lead form.
5. **Lead Scoring** (Admin > Lead Scoring): 8 rules seeded (see cheat sheet below).
   Scoring decides who appears under "Hot leads". Tune points to your reality.
6. **Tags & Sources** (Admin > Tags & Sources): tags (Hot Lead, Decision Maker...) label
   leads; sources (LinkedIn, Website, Cold Outreach...) come from marketing and power the
   "leads by source" chart.
7. **Automations** (Admin > Automations): 2 rules seeded:
   - Call logged with NO_ANSWER -> auto-create "Retry call" task in 2 days
   - Lead enters "Contacted" stage -> auto-create "Check for reply" task in 3 days
   These are your memory. Add more later (triggers: lead created, stage changed, call
   logged, task overdue; actions: create task, add tag, notify).
8. **Org Settings**: company name and defaults.
9. **Audit Log**: read-only trail of who did what. Check it when something looks off.

Then STOP touching config. Admin is 5% of the work after day one.

---

## PART 4 - SALES MANAGER: the coach role (daily rhythm)

Login: manager@nexuscrm.local. Your day is 20 minutes of system + coaching off it.

**Morning (10 min) - Dashboard > Executive**
- New leads (30d), Converted (30d), Open pipeline value, Calls (7d): the four vital signs.
- Charts: lead inflow per day (is marketing feeding us?), leads by source (what channel
  works?), pipeline by stage (where is money stuck?), leads by status.
- **Team dashboard** (Dashboard > Team): per-person leads/calls/connected/emails/deals won.
  Compare activity, not just results - a rep with 0 calls has a discipline problem, a rep
  with 50 calls and 0 wins has a coaching problem.

**Assignment (5 min) - Leads page**
- Filter Status = NEW. Select leads (checkboxes) > Bulk action ASSIGN to a rep.
  Rule of thumb: every new lead gets an owner within 24 hours.

**Pipeline hygiene (5 min) - Pipeline page**
- The board shows every open deal grouped by stage. Look for:
  - Stage with 40+ leads and no movement (usually "Contacted" graveyards) -> nudge owners
  - Deals stuck in Negotiation > 2 weeks -> join the call yourself
- Click any card to open the lead.

**Weekly**
- **Reports** (Insights > Reports): pick type + date range, export CSV for your 1:1s.
- **Proposals** (Engage > Proposals): review proposals in DRAFT, set them SENT.
- **Campaigns** (Engage > Campaigns): build sequences with templates, start/pause,
  watch open/reply counts.
- **Deals** (Deals page): create/confirm deals for serious opportunities - the weighted
  forecast (amount x stage probability) feeds your board deck.

---

## PART 5 - SALES REP: the money role (the daily loop)

Login: rep@nexuscrm.local. Your ENTIRE day is one loop, 8-15 times:

```
1. My Day            -> what does the system owe me today?
2. Open a lead       -> read timeline (never cold-read a contact)
3. DO the action     -> call / email / meeting
4. LOG it            -> outcome + notes + NEXT action + follow-up date
5. MOVE the stage    -> the board reflects reality
6. Next lead. Repeat.
```

**My Day page explained**
- Tasks due today / Overdue tasks: your promise list. Complete them with a note.
- My hot leads (score >= 75): your gold. Call these FIRST.
- Never contacted: oldest first - nobody ever called them. Clear these daily.
- Targets (calls/emails/meetings vs done): your personal scoreboard.

**Working one lead (Leads > click a lead)**
Left side: full profile + your clinic custom fields. Right/top: actions.
Tabs: Timeline (everything in order), Calls, Emails, Tasks, Meetings, Deals.

- **Log a call** (Log call): pick outcome:
  - CONNECTED / INTERESTED / QUALIFIED / MEETING_BOOKED - good outcomes (counted as
    "connected" in reports)
  - NO_ANSWER - the seeded automation will create a retry task in 2 days automatically
  - Add duration, notes, next action, and a follow-up date. 30 seconds of typing now
    saves 30 minutes of confusion next week.
- **Add note**: anything that is not a call (WhatsApp reply, in-person chat).
- **Tasks** (create task): "Send contract by Friday" - with due date and priority.
- **Meeting** (schedule): title, date, link; shows on the lead's timeline.
- **Email**: pick a template (Initial Outreach / Follow-up 2 days / Final Break-up are
  seeded with {{firstName}}, {{companyName}}, {{city}}, {{industry}} placeholders that
  auto-fill from the lead), edit, send (needs an SMTP email account configured -
  Accounts page; otherwise marked Integration Required).
- **Tags**: tag "Decision Maker" when you confirm you talk to the owner. Tags are how
  you filter later.
- **Stage**: move it forward every single interaction. Stages are the team's shared truth.
- **Documents**: attach the signed proposal, the clinic's price list, whatever.

**Convert: the endgame (the most important button)**
When a lead WINS: open lead > **Convert**. The system creates a Company + Contact +
Client (+ optionally a Deal with value) and locks the lead as CONVERTED - full history
preserved, never worked again by mistake. The lead now lives in Clients (for ongoing
relationship: status, account manager, notes).

**Search and views**
- Top search bar: instantly find leads/companies/contacts/deals.
- Filters on Leads (status, source, country, score, tags...) + "Save view" to store your
  favorite filter combo (e.g. "UAE clinics score>50 no follow-up"). Views are yours.

**Bulk actions**
Leads list > select multiple > bulk: assign, change stage, change status, add/remove tag,
delete. Runs as a background job (Bulk page shows progress) - safe for thousands.

---

## PART 6 - IMPORTING YOUR REAL DATA (most admins start here)

Leads page > **Import** (uploads Excel .xlsx or CSV):

1. **Upload** your file (first row must be headers).
2. **Map columns**: for each column of your file, pick which CRM field it fills.
   The system suggests a mapping. `business_name` is the only required target.
3. **Choose duplicate strategy** - duplicates are detected on email, phone, website
   AND LinkedIn:
   - SKIP (default, recommended): existing leads stay untouched, new rows import
   - UPDATE_EXISTING: refresh existing leads with new file values
   - CREATE_ANYWAY: never dedupe (messy - avoid)
4. **Run**: background job processes rows with validation; you get counts
   (total / valid / duplicates / invalid) and an errors.csv for rows that failed and why.
5. Import history keeps every batch. Then: assign the new leads (manager) and start calls.

---

## PART 7 - CHEAT SHEETS (what is seeded in your instance)

**Scoring rules (lead becomes hot automatically):**
| Rule | Points |
|---|---|
| Has email | +10 |
| Has phone | +10 |
| Job title looks like decision maker | +15 |
| Industry is medical clinic / healthcare / longevity / wellness | +20 |
| Country is UAE / Dubai / USA / Saudi Arabia | +15 |
| Has website | +10 |
| Status = INTERESTED | +20 |
| Custom field services includes "IV Therapy" | +10 |
| Max 100. Bands: WARM 25-49, HOT 50-74, VERY_HOT 75+ |

**Email templates (Engage > Emails > Templates):**
1. Initial Outreach - first touch, references their city/industry
2. Follow-up 2 days - the polite bump
3. Final Break-up - "should I close your file?" (reply rates on this one are famously high)

**Campaigns**: chain templates into steps (Step 1: Initial Outreach day 0, Step 2:
Follow-up day 2, Step 3: Break-up day 7), add leads as recipients, Start. Requires an
SMTP email account (Engage > Emails > Accounts) - otherwise the system honestly tells
you email dispatch is Integration Required. Open/reply/bounce tracking is built in.

---

## PART 8 - YOUR FIRST-WEEK ROLLOUT PLAN

Day 1 (you, admin): run the Part 3 checklist. Import your real lead list.
Day 2 (managers): assign every lead. Verify every lead has owner + stage.
Day 3 (reps): My Day loop all day. Goal: 100% of "Never contacted" cleared.
Day 4: first pipeline review on the board. Fix stage truths together.
Day 5: first look at Executive dashboard + Team dashboard. Tune scoring rules if hot
leads do not match your gut.

---

## PART 9 - CALLING WITH YOUR OWN PHONE (new)

1. Calls page > **My calling devices** > Register device: name YOUR Android phone.
   Devices are private - nobody can ring your phone but you.
2. The bridge app on your PC (next to your phone) heartbeats automatically; the badge
   turns ONLINE. (For manual testing there is a Heartbeat button.)
3. Open any lead > **Call** button > pick device (or leave Default) > Place call.
   Your phone dials via your SIM; talk on your headset through your phone link as usual.
4. The modal shows live state (RINGING / CONNECTED + timer). When done, pick the outcome
   (No Answer, Busy, Wrong Number, Interested, Not Interested, Call Back, Meeting Booked),
   add notes; "Call Back" can auto-create a follow-up task. Everything lands on the lead
   timeline + Calls page + your analytics.
5. Setup (one-time, per deployment): set `CRM_BRIDGE_URL` + `CRM_BRIDGE_TOKEN` env vars and
   run the bridge app. Without them the Call button explains exactly what is missing -
   nothing breaks. Future providers (SIP/cloud) plug into the same TelephonyService.

### 9c. Running the bridge (makes the Call button actually dial)

Full details in `bridge/README.md`. Short version:

1. Install adb (Google "SDK Platform Tools"), unzip, e.g. to `C:\platform-tools`.
2. Phone: Settings > About > tap Build number 7x > Developer options > USB debugging ON.
   Connect the cable, accept the debugging prompt. Check: `adb devices` lists your phone.
3. CRM > Calls > My calling devices > register your phone > copy its **Device ID**
   (small copy button under the device name).
4. `cd bridge && cp .env.example .env` and fill: `BRIDGE_TOKEN` (same value as
   `CRM_BRIDGE_TOKEN` in the backend .env), `BRIDGE_DEVICE_ID` (the copied id),
   `CRM_API_URL` (e.g. `http://localhost:8080/api/v1`), and `BRIDGE_PUBLIC_URL`
   (`http://host.docker.internal:9090` if the backend runs in Docker, otherwise
   `http://127.0.0.1:9090`).
5. `node bridge/android-bridge.js` - within a minute your device badge turns ONLINE
   by itself. Click **Call** on a lead. Talk on the phone (or Phone Link for PC audio).
6. Busy vs No Answer cannot be told apart by public Android APIs - the bridge records
   No Answer; correct it in the modal if it was actually busy.

---

## PART 9b - TEAM CHAT (new)

- Sidebar > **Team Chat**. Start a conversation with any teammate or manager/admin.
- Rules (enforced by the server): rep<->rep needs a shared team; everyone can talk to
  managers and admins of the same organization; cross-organization is impossible.
- Messages support links to CRM records (open lead directly from a message), unread
  badges, read marking, pagination, and bell notifications for new messages.
- Message history is paginated (50 at a time) - the system never loads thousands at once.

---

## PART 10 - FAQ / GOOD TO KNOW

- **What is the Redis warning in backend logs?** Optional rate-limit counter missing on
  your dev machine; harmless, auth lockout still works (it lives in the database).
- **"Integration Required" messages**: honest markers where the feature needs an external
  account: Gmail/M365 OAuth, real SMTP for sending, calendar sync, AI key
  (CRM_AI_API_KEY enables the OpenAI-compatible AI assistant; without it a rule-based
  assistant answers locally).
- **Change my own password?** The API supports it; say the word and a Change Password
  screen gets added to the profile menu (small task).
- **Who can see what?** Rep: own leads (+team if configured). Manager: whole team.
  Admin: everything + audit log. One org can never see another org's data (enforced in
  every database query).
- **Deleted data?** Tags soft-delete (history safe). Leads have full audit + timeline.
- **Docker later**: `docker compose up -d --build` runs the whole stack (Postgres, Redis,
  Mailpit for fake email at port 8025, backend, frontend).

---

*Generated from the actual codebase: every page, tab, button and rule described here
exists in the app on branch arena/01a062b4-crm.*
