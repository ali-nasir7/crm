#!/usr/bin/env node
/**
 * db-verify - standalone database checker for Nexus CRM.
 *
 * Verifies, against a REAL PostgreSQL server:
 *   1. All Flyway migrations V1..V8 apply cleanly (only on a BLANK database)
 *   2. V8 is idempotent (re-applied without error)
 *   3. Schema inventory: tables, indexes, foreign keys
 *   4. Permission catalogue row count (84 keys seeded by V7)
 *   5. Audit columns exist on the 4 child tables (V8)
 *   6. Critical tenant/scoping columns the backend hard-depends on
 *
 * Safe on an existing Flyway-managed DB: if flyway_schema_history is present,
 * migrations are NOT re-applied (Flyway owns them); verification only.
 *
 * Usage:
 *   npm install pg          # once, in this folder (or any folder with node)
 *   node scripts/db-verify.mjs [postgres://crm:crm@localhost:5432/crm]
 *
 * Exit 0 = all checks passed, 1 = failures (prints which).
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import pg from 'pg';

const URL_ = process.argv[2] || process.env.CRM_DB_URL || 'postgres://crm:crm@localhost:5432/crm';
const MIG_DIR = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'backend', 'src', 'main', 'resources', 'db', 'migration');

let pass = 0, fail = 0;
const ok = (name, cond, detail = '') => {
  if (cond) { pass++; console.log(`  PASS  ${name}${detail ? '  (' + detail + ')' : ''}`); }
  else { fail++; console.log(`  FAIL  ${name}${detail ? '  (' + detail + ')' : ''}`); }
};

const client = new pg.Client({ connectionString: URL_ });

async function ensureDatabase() {
  const u = new URL(URL_);
  const dbName = u.pathname.replace('/', '');
  u.pathname = '/postgres';
  const admin = new pg.Client({ connectionString: u.toString() });
  await admin.connect();
  try { await admin.query(`CREATE DATABASE "${dbName}"`); console.log(`created database ${dbName}`); }
  catch (e) { if (e.code !== '42P04') throw e; }
  await admin.end();
}

async function main() {
  await ensureDatabase();
  await client.connect();

  // --- 1. migrations (only when Flyway does NOT own this DB yet) ---
  const flyway = await client.query(
    `SELECT count(*)::int AS n FROM information_schema.tables WHERE table_name='flyway_schema_history'`);
  // numeric version order (V10 must run AFTER V9) - same rule Flyway uses
  const files = fs.readdirSync(MIG_DIR).filter(f => f.endsWith('.sql'))
    .sort((a, b) => parseInt(a.match(/\d+/)[0], 10) - parseInt(b.match(/\d+/)[0], 10));
  if (flyway.rows[0].n === 0) {
    console.log('\n== migrations: blank database, applying V1..V' + files.length + ' ==');
    for (const f of files) {
      try { await client.query(fs.readFileSync(path.join(MIG_DIR, f), 'utf8')); ok(`apply ${f}`, true); }
      catch (e) { ok(`apply ${f}`, false, e.message.split('\n')[0]); }
    }
    const v8 = files.find(f => f.startsWith('V8'));
    if (v8) {
      try { await client.query(fs.readFileSync(path.join(MIG_DIR, v8), 'utf8')); ok(`re-apply ${v8} (idempotent)`, true); }
      catch (e) { ok(`re-apply ${v8} (idempotent)`, false, e.message.split('\n')[0]); }
    }
  } else {
    console.log('\n== migrations: Flyway owns this DB, skipping apply (verify only) ==');
    ok('flyway_schema_history present', true);
  }

  // --- 2. schema inventory ---
  console.log('\n== schema inventory ==');
  const tables = await client.query(
    `SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'`);
  ok('52 base tables', tables.rows.length === 52, `found ${tables.rows.length}`);
  const idx = await client.query(`SELECT count(*)::int AS n FROM pg_indexes WHERE schemaname='public'`);
  ok('indexes created (>100)', idx.rows[0].n > 100, `${idx.rows[0].n}`);
  const fks = await client.query(
    `SELECT count(*)::int AS n FROM information_schema.table_constraints WHERE constraint_type='FOREIGN KEY' AND table_schema='public'`);
  ok('foreign keys (>50)', fks.rows[0].n > 50, `${fks.rows[0].n}`);

  // --- 3. permission catalogue ---
  console.log('\n== RBAC catalogue ==');
  if (tables.rows.some(r => r.table_name === 'permissions')) {
    const perms = await client.query(`SELECT count(*)::int AS n FROM permissions`);
    ok('permissions seeded (84 keys)', perms.rows[0].n === 84, `${perms.rows[0].n}`);
    const cats = await client.query(`SELECT count(DISTINCT category)::int AS n FROM permissions`);
    ok('permission categories (15)', cats.rows[0].n === 15, `${cats.rows[0].n}`);
  } else {
    ok('permissions table exists', false);
  }

  // --- 4. V8 audit columns ---
  console.log('\n== audit columns (V8) ==');
  for (const t of ['campaign_steps', 'counters', 'pipeline_stages', 'proposal_items']) {
    const cols = await client.query(
      `SELECT column_name FROM information_schema.columns WHERE table_name='${t}'`);
    const names = new Set(cols.rows.map(r => r.column_name));
    const have = ['created_at', 'updated_at', 'created_by', 'updated_by'].filter(c => names.has(c));
    ok(`${t}: 4/4 audit columns`, have.length === 4, have.join(','));
  }

  // --- V9: calling + chat + onboarding ---
  console.log('\n== V9 (calling/chat/onboarding) ==');
  const mustHaveV9 = {
    calling_devices: ['organization_id', 'user_id', 'device_name', 'status', 'last_seen_at', 'is_default'],
    conversations: ['organization_id', 'type', 'last_message_at'],
    conversation_participants: ['conversation_id', 'user_id', 'last_read_at'],
    chat_messages: ['organization_id', 'conversation_id', 'sender_id', 'body', 'lead_id'],
  };
  for (const [t, cols] of Object.entries(mustHaveV9)) {
    const have = await client.query(`SELECT column_name FROM information_schema.columns WHERE table_name='${t}'`);
    const names = new Set(have.rows.map(r => r.column_name));
    const missing = cols.filter(c => !names.has(c));
    ok(`${t}: columns`, missing.length === 0, missing.length ? 'missing ' + missing.join(',') : 'all present');
  }
  const callsCols = await client.query(`SELECT column_name FROM information_schema.columns WHERE table_name='calls'`);
  const callsNames = new Set(callsCols.rows.map(r => r.column_name));
  ok('calls: live-state columns',
    ['device_id','status','started_at','answered_at','ended_at','provider_ref','contact_id'].every(c => callsNames.has(c)));
  const usersCols = await client.query(`SELECT column_name FROM information_schema.columns WHERE table_name='users'`);
  ok('users: must_change_password', usersCols.rows.some(r => r.column_name === 'must_change_password'));

  // --- V10: per-device bridge routing ---
  console.log('\n== V10 (bridge routing) ==');
  const v10Cols = await client.query(`SELECT column_name, data_type, character_maximum_length FROM information_schema.columns WHERE table_name='calling_devices' AND column_name='bridge_url'`);
  ok('calling_devices: bridge_url varchar(500)', v10Cols.rows.length === 1 && v10Cols.rows[0].data_type === 'character varying' && v10Cols.rows[0].character_maximum_length === 500,
    v10Cols.rows[0] ? `${v10Cols.rows[0].data_type}(${v10Cols.rows[0].character_maximum_length})` : 'missing');

  // --- 5. critical columns ---
  console.log('\n== critical columns ==');
  const mustHave = {
    leads: ['organization_id', 'business_name', 'email', 'phone', 'status', 'stage_id', 'assigned_user_id', 'score'],
    users: ['organization_id', 'email', 'password_hash', 'super_admin', 'locked_until'],
    activities: ['organization_id', 'lead_id', 'type', 'occurred_at'],
    import_jobs: ['organization_id', 'status', 'duplicate_strategy', 'total_rows'],
    bulk_jobs: ['organization_id', 'job_type', 'status'],
    emails: ['organization_id', 'tracking_id', 'status', 'replied_at', 'bounced_at', 'open_count'],
    audit_logs: ['organization_id', 'actor_id', 'action'],
    deals: ['organization_id', 'amount', 'status', 'stage_id', 'probability'],
    refresh_tokens: ['token_hash', 'user_id', 'expires_at'],
    suppressions: ['organization_id', 'email'],
  };
  for (const [t, cols] of Object.entries(mustHave)) {
    const have = await client.query(`SELECT column_name FROM information_schema.columns WHERE table_name='${t}'`);
    const names = new Set(have.rows.map(r => r.column_name));
    const missing = cols.filter(c => !names.has(c));
    ok(`${t}: ${cols.length} critical columns`, missing.length === 0, missing.length ? 'missing ' + missing.join(',') : 'all present');
  }

  await client.end();
  console.log(`\nRESULT: ${pass} passed, ${fail} failed`);
  process.exit(fail === 0 ? 0 : 1);
}

main().catch(e => { console.error('FATAL:', e.message); process.exit(1); });
