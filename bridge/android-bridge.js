#!/usr/bin/env node
/**
 * Nexus CRM - Android Phone Bridge (V1, real implementation, zero npm dependencies).
 *
 * Run this on the PC that is connected (USB or wireless debugging) to YOUR Android
 * phone. The CRM backend dials through it; call audio plays on the phone itself
 * (for PC audio use the Phone Link app separately - it is the audio UX, not this API).
 *
 * Contract (must stay in sync with backend AndroidBridgeTelephonyService / CallingController):
 *   CRM  ->  POST /call           X-Bridge-Token   {deviceId, number, customerName}
 *            200 -> {ref, state:"RINGING"}   4xx/5xx -> call was NOT dialed
 *   CRM  ->  POST /calls/{ref}/end X-Bridge-Token   hang up (keyevent 6)
 *   CRM  ->  GET  /status          X-Bridge-Token   health for manual curl checks
 *   Bridge -> CRM POST {CRM_API_URL}/calling/bridge/heartbeat {deviceId, url}
 *            (marks the CRM device ONLINE and announces the URL the backend dials through)
 *   Bridge -> CRM POST {CRM_API_URL}/calling/bridge/status {ref, state}
 *            state in RINGING | CONNECTED | ENDED | NO_ANSWER | BUSY | FAILED
 *
 * Phone control is done with adb only:
 *   dial:      adb shell am start -a android.intent.action.CALL -d tel:<number>
 *   hang up:   adb shell input keyevent 6
 *   state:     adb shell dumpsys telephony.registry  (mCallState + mPreciseCallState)
 *
 * Configuration: environment variables, or a .env file next to this script (see .env.example).
 */
'use strict'

const http = require('http')
const https = require('https')
const { execFile } = require('child_process')
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

// ---------------------------------------------------------------- config -----
function loadEnvFile() {
  const p = path.join(__dirname, '.env')
  if (!fs.existsSync(p)) return
  for (const line of fs.readFileSync(p, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*(?:export\s+)?([A-Za-z0-9_]+)\s*=\s*(.*?)\s*$/)
    if (m && process.env[m[1]] === undefined) {
      process.env[m[1]] = m[2].replace(/^["']|["']$/g, '')
    }
  }
}
loadEnvFile()

const int = (v, d) => { const n = parseInt(v, 10); return Number.isFinite(n) && n > 0 ? n : d }
const stripTrailingSlash = (v) => (v || '').trim().replace(/\/+$/, '')

const CFG = {
  port: int(process.env.BRIDGE_PORT, 9090),
  host: (process.env.BRIDGE_HOST || '0.0.0.0').trim(),
  token: (process.env.BRIDGE_TOKEN || '').trim(),
  deviceId: (process.env.BRIDGE_DEVICE_ID || '').trim(),
  crmApiUrl: stripTrailingSlash(process.env.CRM_API_URL),      // e.g. http://localhost:8080/api/v1
  publicUrl: stripTrailingSlash(process.env.BRIDGE_PUBLIC_URL),// URL the CRM dials; default http://127.0.0.1:port
  adb: (process.env.ADB_PATH || 'adb').trim(),
  serial: (process.env.ANDROID_SERIAL || '').trim(),
  adbConnect: (process.env.ADB_CONNECT || '').trim(),          // wireless: ip:port
  heartbeatSec: int(process.env.BRIDGE_HEARTBEAT_SECONDS, 60),
}
CFG.publicUrl = CFG.publicUrl || `http://127.0.0.1:${CFG.port}`

const VERSION = '1.0.0'
const STARTED_AT = Date.now()

function log(kind, msg) {
  const tag = { info: '[bridge]', warn: '[bridge WARN]', error: '[bridge ERROR]' }[kind] || '[bridge]'
  console.log(`${new Date().toISOString()} ${tag} ${msg}`)
}

// --------------------------------------------------------------- helpers -----
function tokenOk(presented) {
  if (!CFG.token || !presented) return false
  const a = Buffer.from(CFG.token), b = Buffer.from(String(presented))
  return a.length === b.length && crypto.timingSafeEqual(a, b)
}

function adb(args, timeoutMs = 10000) {
  return new Promise((resolve) => {
    const full = CFG.serial ? ['-s', CFG.serial, ...args] : [...args]
    execFile(CFG.adb, full, { timeout: timeoutMs, windowsHide: true }, (err, stdout, stderr) => {
      resolve({
        ok: !err,
        stdout: String(stdout || ''),
        stderr: String(stderr || ''),
        err: err ? String(err.message || err) : null,
      })
    })
  })
}

function parseDevices(out) {
  const list = []
  for (const line of String(out).split(/\r?\n/)) {
    const m = line.trim().match(/^(\S+)\s+(\S+)$/)
    if (m && m[1] !== 'List') list.push({ serial: m[1], state: m[2] })
  }
  return list
}

let presence = { checkedAt: 0, present: false, serial: '', state: 'none', multiWarned: false }

async function phonePresent(force) {
  if (!force && presence.checkedAt && Date.now() - presence.checkedAt < 8000) return presence
  if (CFG.adbConnect) await adb(['connect', CFG.adbConnect], 6000)
  const r = await adb(['devices'], 6000)
  const list = r.ok ? parseDevices(r.stdout) : []
  let sel = null
  if (CFG.serial) {
    sel = list.find((d) => d.serial === CFG.serial && d.state === 'device') || null
  } else {
    const ready = list.filter((d) => d.state === 'device')
    if (ready.length === 1) sel = ready[0]
    else if (ready.length > 1) {
      sel = ready[0]
      if (!presence.multiWarned) {
        presence.multiWarned = true
        log('warn', `Multiple adb devices (${ready.map((d) => d.serial).join(', ')}). ` +
          'Set ANDROID_SERIAL to pin the right phone; using the first for now.')
      }
    }
  }
  presence = {
    ...presence,
    checkedAt: Date.now(),
    present: !!sel,
    serial: sel ? sel.serial : CFG.serial || '',
    state: sel ? 'device' : (list[0] ? list[0].state : 'no-device'),
  }
  return presence
}

/** Strict dial-string: digits and optional + only. Mirrors the backend's validation
 *  and makes shell/URI injection impossible (everything else is stripped). */
function sanitizeNumber(raw) {
  const n = String(raw || '').replace(/[^\d+]/g, '')
  return /^\+?\d{7,15}$/.test(n) ? n : null
}

function httpPostJson(urlStr, body, token, timeoutMs = 8000) {
  return new Promise((resolve) => {
    let u
    try { u = new URL(urlStr) } catch { return resolve({ ok: false, status: 0, error: 'bad url' }) }
    const mod = u.protocol === 'https:' ? https : http
    const data = Buffer.from(JSON.stringify(body))
    const req = mod.request({
      hostname: u.hostname, port: u.port || (u.protocol === 'https:' ? 443 : 80),
      path: u.pathname + u.search, method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Content-Length': data.length,
        ...(token ? { 'X-Bridge-Token': token } : {}) },
      timeout: timeoutMs,
    }, (res) => {
      let buf = ''
      res.on('data', (c) => { if (buf.length < 65536) buf += c })
      res.on('end', () => resolve({ ok: res.statusCode >= 200 && res.statusCode < 300,
        status: res.statusCode, body: buf }))
    })
    req.on('timeout', () => { req.destroy(new Error('timeout')) })
    req.on('error', (e) => resolve({ ok: false, status: 0, error: e.message }))
    req.write(data)
    req.end()
  })
}

async function pushToCrm(pathName, body) {
  if (!CFG.crmApiUrl) return { ok: false, status: 0, error: 'CRM_API_URL not configured' }
  return httpPostJson(`${CFG.crmApiUrl}${pathName}`, body, CFG.token)
}

// ------------------------------------------------------------ call state -----
// ref -> { ref, number, startedAt, phase: DIALING|CONNECTED, connectedAt, pendingTerminal }
const calls = new Map()
const pushQueue = [] // { ref, state, attempts }
let lastWarn = {} // rate-limit repeated warnings

function warnOnce(key, minutes, msg) {
  const now = Date.now()
  if (!lastWarn[key] || now - lastWarn[key] > minutes * 60000) {
    lastWarn[key] = now
    log('warn', msg)
  }
}

function enqueuePush(ref, state) {
  pushQueue.push({ ref, state, attempts: 0 })
}

async function flushPushQueue() {
  while (pushQueue.length) {
    const item = pushQueue[0]
    const res = await pushToCrm('/calling/bridge/status', { ref: item.ref, state: item.state })
    if (res.ok) { pushQueue.shift(); continue }
    if (res.status === 404) {
      warnOnce('status404', 5, `CRM does not know call ${item.ref} (backend restarted?). Dropping state ${item.state}.`)
      pushQueue.shift(); continue
    }
    if (res.status === 401) {
      warnOnce('status401', 5, 'CRM rejected the bridge token (401). Check that BRIDGE_TOKEN here equals CRM_BRIDGE_TOKEN on the backend.')
      pushQueue.shift(); continue
    }
    if (++item.attempts >= 20) {
      log('error', `Giving up pushing state ${item.state} for ${item.ref} after ${item.attempts} attempts: ${res.error || res.status}`)
      pushQueue.shift(); continue
    }
    break // network error -> retry on next tick
  }
}

/** Read the phone's telephony state and advance every tracked call. */
let observing = false
async function observeCalls() {
  if (observing || calls.size === 0) return
  observing = true
  try {
    const p = await phonePresent()
    for (const call of [...calls.values()]) {
      const elapsed = Date.now() - call.startedAt
      if (elapsed > 2 * 60 * 60 * 1000) { // 2h safety valve
        finalize(call, call.connectedAt ? 'ENDED' : 'FAILED')
        continue
      }
      if (!p.present) {
        warnOnce('absent', 2, 'Phone not connected via adb; cannot observe call state. Reconnect the phone.')
        continue
      }
      const r = await adb(['shell', 'dumpsys', 'telephony.registry'], 8000)
      if (!r.ok) { warnOnce('dumpsys', 2, `dumpsys failed: ${r.err || r.stderr}`); continue }
      const out = r.stdout
      const mCall = /mCallState\s*[=:]\s*(\d+)/.exec(out)
      const mFg = /mForegroundCallState\s*[=:]\s*(\d+)/.exec(out) ||
                  /foregroundCallState\s*[=:]\s*(\d+)/i.exec(out)
      const callState = mCall ? parseInt(mCall[1], 10) : null
      const fg = mFg ? parseInt(mFg[1], 10) : null

      // PreciseCallState: 1 IDLE, 2 ACTIVE, 3 HOLDING, 4 DIALING, 5 ALERTING,
      //                   6 INCOMING, 7 WAITING, 8 DISCONNECTED
      let phase
      if (fg !== null) {
        if (fg === 2 || fg === 3) phase = 'ACTIVE'
        else if (fg >= 4 && fg <= 7) phase = 'DIALING'
        else phase = 'IDLE'
      } else if (callState !== null) {
        phase = callState === 0 ? 'IDLE' : 'OFFHOOK' // degraded: cannot tell dialing from active
        if (phase === 'OFFHOOK' && elapsed > 7000 && call.phase === 'DIALING') {
          if (!call.degradedNote) {
            call.degradedNote = true
            log('warn', 'This Android build does not expose mPreciseCallState; using an ' +
              'elapsed-time heuristic to guess when the call was answered. Outcome may need manual fixing in the CRM.')
          }
          call.phase = 'CONNECTED'; call.connectedAt = Date.now()
          enqueuePush(call.ref, 'CONNECTED')
        }
      } else {
        continue
      }

      if (phase === 'ACTIVE' && call.phase === 'DIALING') {
        call.phase = 'CONNECTED'
        call.connectedAt = Date.now()
        log('info', `Call ${call.ref} CONNECTED to ${call.number}`)
        enqueuePush(call.ref, 'CONNECTED')
      } else if (phase === 'IDLE' && elapsed > 3000) { // 3s grace so a just-dialed idle is not misread
        if (call.connectedAt) {
          log('info', `Call ${call.ref} ENDED after ${Math.round((Date.now() - call.connectedAt) / 1000)}s talk time`)
          finalize(call, 'ENDED')
        } else if (elapsed >= 10000) {
          log('info', `Call ${call.ref} was not answered (NO_ANSWER). Note: BUSY cannot be distinguished from NO_ANSWER via public APIs.`)
          finalize(call, 'NO_ANSWER')
        } else {
          log('info', `Call ${call.ref} failed immediately after dialing`)
          finalize(call, 'FAILED')
        }
      }
    }
  } finally {
    observing = false
  }
}

function finalize(call, state) {
  calls.delete(call.ref)
  enqueuePush(call.ref, state)
}

// -------------------------------------------------------------- heartbeat -----
let beating = false
async function heartbeat() {
  if (beating || !CFG.deviceId || !CFG.crmApiUrl) return
  beating = true
  try {
    const p = await phonePresent()
    if (!p.present) return // device intentionally goes stale/OFFLINE in the CRM - "online" must mean "dialable"
    const res = await pushToCrm('/calling/bridge/heartbeat', { deviceId: CFG.deviceId, url: CFG.publicUrl })
    if (res.ok) {
      if (!heartbeat.succeededOnce) {
        heartbeat.succeededOnce = true
        log('info', `CRM heartbeat OK - device ${CFG.deviceId} is ONLINE and calls will dial ${CFG.publicUrl}`)
      }
    } else if (res.status === 404) {
      warnOnce('hb404', 5, `CRM has no calling device with id ${CFG.deviceId}. Copy the Device ID from Calls > My calling devices into BRIDGE_DEVICE_ID.`)
    } else if (res.status === 400) {
      warnOnce('hb400', 5, 'CRM rejected the heartbeat body (400) - almost always BRIDGE_DEVICE_ID is not a valid device id. '
        + 'It must be a UUID like 3f2a1b4c-... copied from CRM > Calls > My calling devices (click the small id: text). '
        + `Current value: "${CFG.deviceId}"`)
    } else if (res.status === 401) {
      warnOnce('hb401', 5, 'CRM rejected the bridge token (401). BRIDGE_TOKEN here must equal CRM_BRIDGE_TOKEN on the backend.')
    } else {
      warnOnce('hbErr', 2, `CRM heartbeat failed: ${res.error || 'HTTP ' + res.status} (will retry)`)
    }
  } finally {
    beating = false
  }
}

// ----------------------------------------------------------------- server -----
function readBody(req, limit = 16384) {
  return new Promise((resolve, reject) => {
    let size = 0
    const chunks = []
    req.on('data', (c) => {
      size += c.length
      if (size > limit) { reject(new Error('body too large')); req.destroy(); return }
      chunks.push(c)
    })
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
    req.on('error', reject)
  })
}

function send(res, code, obj) {
  const body = JSON.stringify(obj)
  res.writeHead(code, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) })
  res.end(body)
}

async function dial(number) {
  // args are passed verbatim to adb (no host shell); the number is [0-9+] only, so the
  // on-device shell never sees a metacharacter either.
  const r = await adb(['shell', 'am', 'start', '-a', 'android.intent.action.CALL', '-d', `tel:${number}`], 15000)
  const output = (r.stderr + '\n' + r.stdout).trim()
  if (!r.ok || /error|exception|not started|unable to/i.test(r.stderr)) {
    return { ok: false, error: output.split('\n').filter(Boolean).slice(-1)[0] || `adb exited ${r.err}` }
  }
  return { ok: true }
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost')
  const token = req.headers['x-bridge-token']
  try {
    if (url.pathname === '/status' && req.method === 'GET') {
      if (!tokenOk(token)) return send(res, 401, { error: 'Invalid bridge token' })
      const p = await phonePresent()
      return send(res, 200, {
        status: 'UP', version: VERSION, uptimeSeconds: Math.round((Date.now() - STARTED_AT) / 1000),
        phone: { present: p.present, serial: p.serial || null, adbState: p.state },
        activeCalls: calls.size, queuedStatePushes: pushQueue.length,
        config: { deviceIdSet: !!CFG.deviceId, crmApiUrlSet: !!CFG.crmApiUrl, publicUrl: CFG.publicUrl },
      })
    }

    if (url.pathname === '/call' && req.method === 'POST') {
      if (!tokenOk(token)) return send(res, 401, { error: 'Invalid bridge token' })
      let parsed
      try { parsed = JSON.parse(await readBody(req)) } catch { return send(res, 400, { error: 'Invalid JSON' }) }
      const number = sanitizeNumber(parsed.number)
      if (!number) return send(res, 400, { error: 'Invalid phone number (expected 7-15 digits, optional +)' })
      if (CFG.deviceId && parsed.deviceId && String(parsed.deviceId) !== CFG.deviceId) {
        return send(res, 409, { error: `This bridge serves device ${CFG.deviceId}, not ${parsed.deviceId}` })
      }
      if (calls.size > 0) {
        log('warn', `Rejected /call: ${calls.size} call(s) already tracked: ${[...calls.keys()].join(', ')}`)
        return send(res, 409, { error: 'Bridge already has an active call; hang up first' })
      }
      const p = await phonePresent(true)
      if (!p.present) {
        return send(res, 503, { error: 'Phone not connected via adb. Plug it in / enable USB debugging, or run: adb devices' })
      }
      const d = await dial(number)
      if (!d.ok) {
        log('error', `Dial ${number} failed: ${d.error}`)
        return send(res, 502, { error: `Phone refused to dial: ${d.error}` })
      }
      const ref = crypto.randomUUID()
      calls.set(ref, { ref, number, startedAt: Date.now(), phase: 'DIALING', connectedAt: null })
      enqueuePush(ref, 'RINGING')
      log('info', `Dialing ${number}${parsed.customerName ? ' (' + parsed.customerName + ')' : ''} ref=${ref} serial=${p.serial}`)
      return send(res, 200, { ref, state: 'RINGING' })
    }

    const endMatch = url.pathname.match(/^\/calls\/([\w-]+)\/end$/)
    if (endMatch && req.method === 'POST') {
      if (!tokenOk(token)) return send(res, 401, { error: 'Invalid bridge token' })
      const call = calls.get(endMatch[1])
      if (!call) return send(res, 404, { error: 'Unknown call reference' })
      await adb(['shell', 'input', 'keyevent', '6'], 8000) // KEYCODE_ENDCALL
      log('info', `Hang-up requested for ${call.ref}`)
      return send(res, 200, { ok: true })
    }

    send(res, 404, { error: 'Not found' })
  } catch (e) {
    log('error', `Request error: ${e.message}`)
    try { send(res, 500, { error: 'Internal bridge error' }) } catch { /* ignore */ }
  }
})

// ---------------------------------------------------------------- startup -----
async function main() {
  if (!CFG.token) {
    console.error('BRIDGE_TOKEN is required. Copy bridge/.env.example to bridge/.env, set a token,')
    console.error('and use the SAME value as CRM_BRIDGE_TOKEN on the backend. Refusing to start an')
    console.error('unauthenticated dialer.')
    process.exit(1)
  }
  if (!CFG.deviceId || !CFG.crmApiUrl) {
    log('warn', 'BRIDGE_DEVICE_ID and/or CRM_API_URL are not set: heartbeat and call-state push are DISABLED. Calls placed manually via /call will work but the CRM will never see states.')
  } else if (!/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(CFG.deviceId)) {
    log('warn', `BRIDGE_DEVICE_ID "${CFG.deviceId}" does not look like a device id (expected UUID format). `
      + 'Copy the id from CRM > Calls > My calling devices (click the small id: text under the device name).')
  }
  const p = await phonePresent(true)
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(CFG.port, CFG.host, resolve)
  })
  log('info', `Android bridge v${VERSION} listening on ${CFG.host}:${CFG.port} (announced as ${CFG.publicUrl})`)
  log('info', p.present
    ? `Phone connected via adb (serial ${p.serial}). Place calls from the CRM.`
    : 'No phone connected yet. Enable Developer options > USB debugging, connect the phone, accept the RSA prompt, then check: adb devices')

  let lastBeat = 0
  setInterval(() => {
    flushPushQueue()
    observeCalls()
    if (Date.now() - lastBeat >= CFG.heartbeatSec * 1000) {
      lastBeat = Date.now()
      heartbeat()
    }
  }, 1500)
  heartbeat() // announce immediately if the phone is already connected
}
process.on('SIGINT', () => { log('info', 'Shutting down.'); process.exit(0) })
main()
