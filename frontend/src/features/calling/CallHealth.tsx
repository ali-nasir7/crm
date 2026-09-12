import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Activity, Mic, RefreshCw } from 'lucide-react'
import { api } from '@/api/client'
import type { CallingHealth } from '@/types'
import { Button } from '@/components/ui/Button'
import { fmtAgo } from '@/lib/utils'

/**
 * Call Health panel: six honest status lines (Microphone / Calling Device / Phone Bridge /
 * Android / SIM / Call Audio). Everything here is a REAL probe or an explicit
 * "cannot be measured" - we never guess and never claim a call will sound good.
 */

type Tone = 'green' | 'yellow' | 'red' | 'gray'

const DOT: Record<Tone, string> = {
  green: 'bg-green-500',
  yellow: 'bg-amber-500',
  red: 'bg-red-500',
  gray: 'bg-slate-300',
}

type MicState =
  | { kind: 'unchecked' }
  | { kind: 'no-device' }
  | { kind: 'no-permission'; count: number }
  | { kind: 'granted'; count: number; label: string | null }
  | { kind: 'denied' }
  | { kind: 'error'; message: string }

function Line({ label, tone, detail }: { label: string; tone: Tone; detail: string }) {
  return (
    <div className="flex items-start gap-2.5 py-1.5">
      <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${DOT[tone]}`} aria-hidden />
      <div className="min-w-0">
        <p className="text-sm font-medium text-slate-800">{label}</p>
        <p className="text-xs text-slate-500">{detail}</p>
      </div>
    </div>
  )
}

async function probeMic(): Promise<MicState> {
  if (!navigator.mediaDevices?.enumerateDevices) return { kind: 'unchecked' }
  try {
    const devs = await navigator.mediaDevices.enumerateDevices()
    const inputs = devs.filter((d) => d.kind === 'audioinput')
    if (inputs.length === 0) return { kind: 'no-device' }
    const named = inputs.some((d) => d.label)
    if (!named) return { kind: 'no-permission', count: inputs.length }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const label = stream.getAudioTracks()[0]?.label ?? null
      stream.getTracks().forEach((t) => t.stop())
      return { kind: 'granted', count: inputs.length, label }
    } catch (e) {
      const name = e instanceof DOMException ? e.name : ''
      if (name === 'NotAllowedError') return { kind: 'denied' }
      return { kind: 'error', message: e instanceof Error ? e.message : 'Microphone check failed' }
    }
  } catch {
    return { kind: 'unchecked' }
  }
}

function micLine(mic: MicState | null): { tone: Tone; detail: string } {
  if (!mic || mic.kind === 'unchecked') return { tone: 'gray', detail: 'Not checked yet - run "Check microphone" below.' }
  switch (mic.kind) {
    case 'no-device':
      return { tone: 'red', detail: 'No microphone found on this PC. Check Windows Settings > System > Sound > Input.' }
    case 'no-permission':
      return { tone: 'yellow', detail: `${mic.count} input device(s) found, but this page has no microphone permission yet - run "Check microphone" to grant it.` }
    case 'granted':
      return { tone: 'green', detail: `Working${mic.label ? `: ${mic.label}` : ''} (${mic.count} input device(s)).` }
    case 'denied':
      return { tone: 'red', detail: 'Permission denied. Click the lock/padlock icon in the address bar and allow the microphone, then re-run the check.' }
    case 'error':
      return { tone: 'red', detail: `Mic check failed: ${mic.message}` }
  }
}

export function CallHealth() {
  const [mic, setMic] = useState<MicState | null>(null)
  const [checking, setChecking] = useState(false)

  useEffect(() => {
    // Passive check only (no permission prompt on page load): device labels are visible
    // only when this origin already has microphone permission - that IS the signal.
    probeMic().then((s) => {
      if (s.kind === 'granted' || s.kind === 'no-device') setMic(s)
    })
  }, [])

  const { data, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['calling-health'],
    queryFn: async () => (await api.get<CallingHealth>('/calling/health')).data,
    refetchInterval: 20_000,
    retry: false,
  })

  const runMicCheck = async () => {
    setChecking(true)
    try { setMic(await probeMic()) } finally { setChecking(false) }
  }

  const device = data?.devices.find((d) => d.isDefault) ?? data?.devices[0]
  const b = data?.bridge

  let bridgeTone: Tone = 'gray'
  let bridgeDetail = isLoading ? 'Checking…' : 'Unknown'
  if (data) {
    if (data.mode === 'NOT_CONFIGURED') {
      bridgeTone = 'red'
      bridgeDetail = 'Not configured. Set CRM_BRIDGE_TOKEN (and optionally CRM_BRIDGE_BASE_URL) on the server, put the same token in bridge/.env, and run bridge/android-bridge.js.'
    } else if (data.mode === 'DEVICE_BRIDGE') {
      bridgeTone = device?.status === 'ONLINE' ? 'green' : 'yellow'
      bridgeDetail = 'Per-device bridge: health shows through the device heartbeat below (the backend may not be able to reach the bridge URL directly).'
    } else if (b?.reachable) {
      bridgeTone = 'green'
      bridgeDetail = `Running${b.version ? ` v${b.version}` : ''}${b.latencyMs != null ? ` · ${b.latencyMs} ms` : ''}${b.activeCalls ? ` · ${b.activeCalls} active call(s)` : ''}.`
    } else {
      bridgeTone = 'red'
      bridgeDetail = b?.error ?? 'Bridge did not answer its /status endpoint.'
    }
  }

  let androidTone: Tone = 'gray'
  let androidDetail = 'Unknown - bridge not reachable.'
  if (b?.reachable) {
    if (b.phonePresent === true) {
      androidTone = 'green'
      androidDetail = `Phone connected via adb (state: ${b.adbState ?? 'device'}). Place calls from any lead.`
    } else {
      androidTone = 'red'
      androidDetail = 'Phone NOT detected on adb. Connect the phone (USB + debugging or wireless debugging), open Phone Link once, then refresh.'
    }
  }

  let simTone: Tone = 'gray'
  let simDetail = 'Unknown - bridge not reachable.'
  if (b?.reachable) {
    if (b.simState === 'READY') { simTone = 'green'; simDetail = 'SIM ready for calls.' }
    else if (b.simState === 'ABSENT' || b.simState === 'NOT_READY') { simTone = 'red'; simDetail = `SIM state: ${b.simState}. Insert/enable the SIM, or check airplane mode.` }
    else { simTone = 'yellow'; simDetail = `SIM state: ${b.simState ?? 'UNKNOWN'} (read via adb getprop gsm.sim.state).` }
  }

  return (
    <div className="card p-4">
      <div className="flex items-center justify-between">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-slate-800"><Activity className="h-4 w-4 text-blue-600" /> Call health</p>
          <p className="mt-0.5 text-xs text-slate-500">Live diagnostics for calling. Every line is a real check - if something is red, calling will fail until it is fixed.</p>
        </div>
        <div className="flex items-center gap-1.5">
          <Button variant="secondary" size="sm" loading={checking} onClick={runMicCheck}><Mic className="h-3.5 w-3.5" /> Check microphone</Button>
          <Button variant="secondary" size="sm" onClick={() => refetch()} title="Refresh"><RefreshCw className={`h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} /></Button>
        </div>
      </div>

      <div className="mt-2 grid gap-x-8 md:grid-cols-2">
        <Line label="Microphone" tone={micLine(mic).tone} detail={micLine(mic).detail} />
        <Line
          label="Calling device"
          tone={device ? (device.status === 'ONLINE' ? 'green' : device.status === 'BUSY' ? 'yellow' : 'red') : 'red'}
          detail={
            !device
              ? 'No calling device registered. Add your phone under "My calling devices" below.'
              : device.status === 'ONLINE'
                ? `${device.deviceName} - ONLINE, seen ${device.lastSeenAt ? fmtAgo(device.lastSeenAt) : 'never'}.`
                : `${device.deviceName} - ${device.status}. Start bridge/android-bridge.js on the PC next to the phone (it keeps this ONLINE).`
          }
        />
        <Line label="Phone bridge" tone={bridgeTone} detail={bridgeDetail} />
        <Line label="Android phone (adb)" tone={androidTone} detail={androidDetail} />
        <Line label="SIM" tone={simTone} detail={simDetail} />
        <Line
          label="Call audio"
          tone="gray"
          detail={'Software cannot measure call audio. Phone Link owns the audio path - make a 15-second test call and confirm BOTH directions (you hear them, they hear you). Tuning: docs/CALL_AUDIO_TUNING.md.'}
        />
      </div>
      <p className="mt-1 border-t border-slate-100 pt-2 text-[11px] text-slate-400">
        Microphone check covers the browser only. Calling dials through Phone Link, which uses the Windows microphone settings - see docs/CALL_AUDIO_TUNING.md.
      </p>
    </div>
  )
}
