import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Copy, Phone, Plus, Radio, Star, Trash2 } from 'lucide-react'
import { api, apiError } from '@/api/client'
import type { CallingDeviceItem } from '@/types'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { useAuth } from '@/stores/auth'
import { fmtAgo } from '@/lib/utils'

const statusTone: Record<string, 'green' | 'red' | 'yellow' | 'gray'> = {
  ONLINE: 'green', OFFLINE: 'gray', BUSY: 'yellow', DISCONNECTED: 'red',
}

export function MyCallingDevices() {
  const { user } = useAuth()
  const qc = useQueryClient()
  const [addOpen, setAddOpen] = useState(false)
  const [form, setForm] = useState({ deviceName: '', phoneNumber: '', platform: 'ANDROID' })
  const [error, setError] = useState<string | null>(null)
  const [copiedId, setCopiedId] = useState<string | null>(null)

  const { data: devices, isLoading } = useQuery({
    queryKey: ['calling-devices'],
    queryFn: async () => (await api.get<CallingDeviceItem[]>('/calling/devices')).data,
    refetchInterval: 30_000,
  })

  const refresh = () => qc.invalidateQueries({ queryKey: ['calling-devices'] })

  const add = useMutation({
    mutationFn: async () => (await api.post<CallingDeviceItem>('/calling/devices', form)).data,
    onSuccess: () => { setAddOpen(false); setForm({ deviceName: '', phoneNumber: '', platform: 'ANDROID' }); setError(null); refresh() },
    onError: (e) => setError(apiError(e).message),
  })
  const del = useMutation({
    mutationFn: async (id: string) => { await api.delete(`/calling/devices/${id}`); },
    onSuccess: refresh,
  })
  const makeDefault = useMutation({
    mutationFn: async (id: string) => { await api.post(`/calling/devices/${id}/default`); },
    onSuccess: refresh,
  })
  const heartbeat = useMutation({
    mutationFn: async (id: string) => { await api.post(`/calling/devices/${id}/heartbeat`); },
    onSuccess: refresh,
  })

  const copyId = (id: string) => {
    navigator.clipboard?.writeText(id).catch(() => {})
    setCopiedId(id)
    window.setTimeout(() => setCopiedId((cur) => (cur === id ? null : cur)), 2000)
  }

  return (
    <div className="card p-4">
      <div className="flex items-center justify-between">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-slate-800"><Phone className="h-4 w-4 text-blue-600" /> My calling devices</p>
          <p className="mt-0.5 text-xs text-slate-500">Your own Android phone + SIM via the bridge app (see bridge/README.md). Copy the Device ID below into the bridge .env - the bridge then keeps this device ONLINE automatically.</p>
        </div>
        <Button size="sm" onClick={() => setAddOpen(true)}><Plus className="h-3.5 w-3.5" /> Register device</Button>
      </div>

      {isLoading ? <p className="py-4 text-sm text-slate-500">Loading…</p> : !devices || devices.length === 0 ? (
        <p className="py-4 text-sm text-slate-500">No devices yet. Register your Android phone to place calls from the CRM.</p>
      ) : (
        <div className="mt-3 space-y-2">
          {devices.map((d) => (
            <div key={d.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-100 px-3 py-2">
              <div>
                <p className="text-sm font-medium text-slate-800">
                  {d.deviceName} {d.isDefault && <span className="ml-1 rounded bg-blue-50 px-1.5 py-0.5 text-[10px] font-bold text-blue-700">DEFAULT</span>}
                </p>
                <p className="text-xs text-slate-500">{d.phoneNumber ?? 'no number'} · {d.platform} · seen {d.lastSeenAt ? fmtAgo(d.lastSeenAt) : 'never'}</p>
                <button onClick={() => copyId(d.id)} title={`Copy Device ID: ${d.id}`}
                  className="mt-0.5 inline-flex items-center gap-1 font-mono text-[10px] text-slate-400 hover:text-blue-600">
                  id: {d.id.slice(0, 8)}...{d.id.slice(-4)}
                  {copiedId === d.id ? <Check className="h-3 w-3 text-green-600" /> : <Copy className="h-3 w-3" />}
                </button>
              </div>
              <div className="flex items-center gap-1.5">
                <Badge tone={statusTone[d.status] ?? 'gray'}>{d.status}</Badge>
                {!d.isDefault && <Button variant="secondary" size="sm" onClick={() => makeDefault.mutate(d.id)}><Star className="h-3.5 w-3.5" /> Default</Button>}
                <Button variant="secondary" size="sm" onClick={() => heartbeat.mutate(d.id)} title="Simulate bridge heartbeat (the real bridge app does this automatically)"><Radio className="h-3.5 w-3.5" /> Heartbeat</Button>
                <Button variant="secondary" size="sm" onClick={() => del.mutate(d.id)}><Trash2 className="h-3.5 w-3.5" /></Button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Dialog open={addOpen} onClose={() => setAddOpen(false)} title="Register calling device"
        description="Name your phone and (optionally) its SIM number. Only you can call through your devices.">
        <div className="space-y-3">
          <Input placeholder="Device name, e.g. Ali's Samsung" value={form.deviceName} onChange={(e) => setForm({ ...form, deviceName: e.target.value })} maxLength={80} />
          <Input placeholder="SIM number (optional), e.g. +923001234567" value={form.phoneNumber} onChange={(e) => setForm({ ...form, phoneNumber: e.target.value })} maxLength={32} />
          <Input placeholder="Platform" value={form.platform} onChange={(e) => setForm({ ...form, platform: e.target.value })} maxLength={24} />
          {error && <p className="text-sm text-red-600">{error}</p>}
          <Button className="w-full" loading={add.isPending} onClick={() => add.mutate()}>Register</Button>
          {user && <p className="text-[11px] text-slate-400">Devices are private to {user.email}.</p>}
        </div>
      </Dialog>
    </div>
  )
}
