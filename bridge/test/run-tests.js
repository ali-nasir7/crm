#!/usr/bin/env node
/**
 * Integration test for bridge/android-bridge.js - runs WITHOUT a real phone or CRM.
 * A fake adb reports scripted phone states; a mock CRM records every push.
 * POSIX only (spawns a shebang script as fake adb). Run: node bridge/test/run-tests.js
 */
'use strict'
const { execFile, spawn } = require('child_process')
const fs = require('fs')
const os = require('os')
const path = require('path')
const http = require('http')

const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-test-'))
const STATE = path.join(TMP, 'phone-state.txt')
const HANGUP = path.join(TMP, 'hangup-marker')
const CRMLOG = path.join(TMP, 'crm-log.json')
const TOKEN = 'test-token-123'
fs.writeFileSync(STATE, 'IDLE')
fs.writeFileSync(CRMLOG, '')

let pass = 0, fail = 0
function check(name, cond, extra) {
  if (cond) { pass++; console.log(`  PASS  ${name}`) } else { fail++; console.log(`  FAIL  ${name}${extra ? ' :: ' + extra : ''}`) }
}

// ---- fake adb ---------------------------------------------------------------
const FAKE_ADB = path.join(TMP, 'fake-adb')
fs.writeFileSync(FAKE_ADB, `#!/usr/bin/env node
const a = process.argv.slice(2)
const fs = require('fs')
if (a[0] === 'devices') {
  console.log('List of devices attached'); console.log('FAKESERIAL\\tdevice'); process.exit(0)
}
const cmd = a.join(' ')
if (cmd.includes('telephony.registry')) {
  const s = fs.readFileSync('${STATE}', 'utf8').trim()
  const mCall = s === 'IDLE' ? 0 : 2
  const fg = s === 'IDLE' ? 1 : s === 'DIALING' ? 4 : 2
  console.log('  mCallState=' + mCall)
  console.log('  mPreciseCallState=[mRingingCallState=0, mForegroundCallState=' + fg + ']')
  process.exit(0)
}
if (cmd.includes('am start')) {
  const num = (cmd.match(/tel:(\\S+)/) || [])[1] || ''
  if (num.includes('15550000')) { console.error('Error: unable to dial'); process.exit(1) }
  process.exit(0)
}
if (cmd.includes('keyevent')) { fs.writeFileSync('${HANGUP}', '1'); process.exit(0) }
process.exit(0)
`)
fs.chmodSync(FAKE_ADB, 0o755)

// ---- mock CRM ---------------------------------------------------------------
const CRM_PORT = 8091
const crm = http.createServer((req, res) => {
  let body = ''
  req.on('data', (c) => { body += c })
  req.on('end', () => {
    const entry = { path: req.url, token: req.headers['x-bridge-token'] || null, body: null, at: Date.now() }
    try { entry.body = JSON.parse(body) } catch { entry.body = body }
    fs.appendFileSync(CRMLOG, JSON.stringify(entry) + '\n')
    if (entry.token !== TOKEN) { res.writeHead(401); return res.end('{"error":"bad token"}') }
    if (req.url.includes('/bridge/heartbeat')) {
      const id = entry.body && entry.body.deviceId
      if (!id) { res.writeHead(404); return res.end('{}') }
      if (!/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(String(id))) {
        res.writeHead(400); return res.end('{"error":"invalid device id"}') // like real backend UUID parsing
      }
      res.writeHead(200); return res.end('{"ok":"true"}')
    }
    if (req.url.includes('/bridge/status')) {
      if (String(entry.body && entry.body.ref).startsWith('unknown-')) { res.writeHead(404); return res.end('{}') }
      res.writeHead(200); return res.end('{"ok":"true"}')
    }
    res.writeHead(404); res.end('{}')
  })
})

function crmLog() {
  return fs.readFileSync(CRMLOG, 'utf8').split('\n').filter(Boolean).map((l) => JSON.parse(l))
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function bridgeReq(method, pathName, body, token) {
  return new Promise((resolve) => {
    const data = body ? Buffer.from(JSON.stringify(body)) : null
    const req = http.request({ host: '127.0.0.1', port: PORT, path: pathName, method,
      headers: { ...(data ? { 'Content-Type': 'application/json', 'Content-Length': data.length } : {}),
        ...(token ? { 'X-Bridge-Token': token } : {}) } }, (res) => {
      let b = ''
      res.on('data', (c) => { b += c })
      res.on('end', () => { let j = null; try { j = JSON.parse(b) } catch {} resolve({ status: res.statusCode, json: j, raw: b }) })
    })
    req.on('error', (e) => resolve({ status: 0, error: e.message }))
    if (data) req.write(data)
    req.end()
  })
}

const PORT = 8092
async function main() {
  execFile('node', ['--check', path.join(__dirname, '..', 'android-bridge.js')], (e) => {
    if (e) { console.error('bridge syntax check failed'); process.exit(1) }
  })
  await new Promise((r) => crm.listen(CRM_PORT, '127.0.0.1', r))
  const bridge = spawn('node', [path.join(__dirname, '..', 'android-bridge.js')], {
    env: { ...process.env,
      BRIDGE_TOKEN: TOKEN, BRIDGE_DEVICE_ID: '11111111-2222-3333-4444-555555555555',
      CRM_API_URL: `http://127.0.0.1:${CRM_PORT}/api/v1`, BRIDGE_PORT: String(PORT),
      BRIDGE_HOST: '127.0.0.1', ADB_PATH: FAKE_ADB, BRIDGE_HEARTBEAT_SECONDS: '2',
      BRIDGE_PUBLIC_URL: `http://127.0.0.1:${PORT}` },
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  let bridgeLog = ''
  bridge.stdout.on('data', (d) => { bridgeLog += d })
  bridge.stderr.on('data', (d) => { bridgeLog += d })

  try {
    await sleep(3500)
    let log = crmLog()
    const st = await bridgeReq('GET', '/status', null, TOKEN)
    check('bridge started and reports UP', st.json?.status === 'UP', `status=${st.status} err=${st.error} log=${bridgeLog.slice(-400)}`)
    check('status endpoint rejects bad token', (await bridgeReq('GET', '/status', null, 'WRONG')).status === 401)
    check('phone presence visible in /status', (await bridgeReq('GET', '/status', null, TOKEN)).json?.phone?.present === true)
    check('heartbeat reached CRM with device id + url', log.some((e) => e.path.includes('heartbeat')
      && e.body?.deviceId === '11111111-2222-3333-4444-555555555555' && e.body?.url === `http://127.0.0.1:${PORT}`))
    check('heartbeat carried the bridge token', log.some((e) => e.path.includes('heartbeat') && e.token === TOKEN))

    check('call with bad token rejected', (await bridgeReq('POST', '/call', { number: '+923001234567' }, 'WRONG')).status === 401)
    check('call with invalid number rejected', (await bridgeReq('POST', '/call', { number: 'abc', deviceId: 'x' }, TOKEN)).status === 400)
    check('call for another device rejected (409)', (await bridgeReq('POST', '/call',
      { number: '+923001234567', deviceId: 'other-device' }, TOKEN)).status === 409)
    const failCall = await bridgeReq('POST', '/call',
      { number: '+15550000000', deviceId: '11111111-2222-3333-4444-555555555555' }, TOKEN)
    check('dial command failure surfaces as 502', failCall.status === 502, `got=${failCall.status} ${failCall.raw}`)

    fs.writeFileSync(STATE, 'DIALING')
    const call = await bridgeReq('POST', '/call', { number: '+923001234567',
      deviceId: '11111111-2222-3333-4444-555555555555', customerName: 'Test Lead' }, TOKEN)
    check('call accepted with ref + RINGING', call.status === 200 && !!call.json?.ref && call.json?.state === 'RINGING', `got=${call.status} ${call.raw}`)
    const ref = call.json?.ref
    await sleep(4000)
    log = crmLog()
    check('RINGING pushed to CRM', log.some((e) => e.path.includes('status') && e.body?.ref === ref && e.body?.state === 'RINGING'))

    fs.writeFileSync(STATE, 'ACTIVE')
    await sleep(4500)
    log = crmLog()
    check('CONNECTED pushed when phone goes off-hook active', log.some((e) => e.body?.ref === ref && e.body?.state === 'CONNECTED'))

    fs.writeFileSync(STATE, 'IDLE')
    await sleep(4500)
    log = crmLog()
    check('ENDED pushed when phone returns to idle', log.some((e) => e.body?.ref === ref && e.body?.state === 'ENDED'))

    fs.writeFileSync(STATE, 'DIALING')
    const call2 = await bridgeReq('POST', '/call', { number: '+923004445566', deviceId: '11111111-2222-3333-4444-555555555555' }, TOKEN)
    check('second call accepted', call2.status === 200)
    await sleep(2000)
    const hang = await bridgeReq('POST', `/calls/${call2.json?.ref}/end`, null, TOKEN)
    check('hang-up endpoint works', hang.status === 200 && fs.existsSync(HANGUP))
    fs.writeFileSync(STATE, 'IDLE')
    await sleep(4000)
    check('bridge still healthy after full cycle', (await bridgeReq('GET', '/status', null, TOKEN)).json?.status === 'UP')
    check('no crashes in bridge log', !/Error.*at |Unhandled|uncaught/i.test(bridgeLog), bridgeLog.slice(-300))
  } finally {
    bridge.kill('SIGINT')
  }

  // second instance: invalid BRIDGE_DEVICE_ID must produce the clear 400 warning
  const bad = spawn('node', [path.join(__dirname, '..', 'android-bridge.js')], {
    env: { ...process.env,
      BRIDGE_TOKEN: TOKEN, BRIDGE_DEVICE_ID: 'not-a-uuid',
      CRM_API_URL: `http://127.0.0.1:${CRM_PORT}/api/v1`, BRIDGE_PORT: String(PORT + 1),
      BRIDGE_HOST: '127.0.0.1', ADB_PATH: FAKE_ADB, BRIDGE_HEARTBEAT_SECONDS: '1',
      BRIDGE_PUBLIC_URL: `http://127.0.0.1:${PORT + 1}` },
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  let badLog = ''
  bad.stdout.on('data', (d) => { badLog += d })
  bad.stderr.on('data', (d) => { badLog += d })
  try {
    await sleep(3000)
    check('invalid BRIDGE_DEVICE_ID produces clear startup warning', badLog.includes('does not look like a device id'), badLog.slice(-300))
    check('and clear heartbeat 400 warning', badLog.includes('BRIDGE_DEVICE_ID is not a valid device id'), badLog.slice(-300))
  } finally {
    bad.kill('SIGINT')
    await new Promise((r) => crm.close(r))
  }
  console.log(`\n${pass} passed, ${fail} failed`)
  fs.rmSync(TMP, { recursive: true, force: true })
  process.exit(fail ? 1 : 0)
}
main()
