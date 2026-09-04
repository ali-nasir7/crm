import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bot, History, Plus, Trash2 } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import type { AutomationItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import  { Card, CardBody }  from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select, Label } from '@/components/ui/Input'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { PageLoader } from '@/components/ui/Misc'
import { fmtDateTime } from '@/lib/utils'

const TRIGGERS = ['LEAD_CREATED', 'LEAD_STAGE_CHANGED', 'CALL_LOGGED', 'EMAIL_SENT', 'NO_REPLY_AFTER', 'TASK_OVERDUE']
const ACTIONS = [
  { id: 'CREATE_TASK', label: 'Create a task' },
  { id: 'ADD_TAG', label: 'Add a tag' },
  { id: 'NOTIFY', label: 'Notify the owner' },
  { id: 'CHANGE_STAGE', label: 'Move lead to stage' },
  { id: 'SEND_EMAIL', label: 'Send email (requires review)' },
]

interface Run { id: string; automationId: string; leadId: string | null; status: string; detail: string | null; executedAt: string }

export default function AutomationsPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const [createOpen, setCreateOpen] = useState(false)
  const [runsFor, setRunsFor] = useState<AutomationItem | null>(null)
  const [deleteAutomation, setDeleteAutomation] = useState<AutomationItem | null>(null)
  const [form, setForm] = useState({ name: '', trigger: 'LEAD_CREATED', action: 'NOTIFY', config: '', conditions: '' })

  const { data: automations, isLoading } = useQuery({ queryKey: ['automations'], queryFn: async () => (await api.get<AutomationItem[]>('/automations')).data })
  const { data: runs } = useQuery({
    queryKey: ['automation-runs', runsFor?.id],
    queryFn: async () => (await api.get<Run[]>(`/automations/${runsFor!.id}/runs`)).data,
    enabled: !!runsFor,
  })

  const create = useMutation({
    mutationFn: async () => {
      let actionConfig: Record<string, unknown> = {}
      let conditions: Record<string, unknown> | null = null
      try {
        if (form.config.trim()) actionConfig = JSON.parse(form.config)
        if (form.conditions.trim()) conditions = JSON.parse(form.conditions)
      } catch { throw new Error('Config and conditions must be valid JSON') }
      return api.post('/automations', { name: form.name, trigger: form.trigger, action: form.action, actionConfig, conditions })
    },
    onSuccess: () => { toast.push('success', 'Automation created'); setCreateOpen(false); setForm({ name: '', trigger: 'LEAD_CREATED', action: 'NOTIFY', config: '', conditions: '' }); qc.invalidateQueries({ queryKey: ['automations'] }); qc.invalidateQueries({ queryKey: ['automation-runs'] }) },
    onError: (e) => toast.push('error', 'Failed', apiError(e).message),
  })

  const toggle = useMutation({
    mutationFn: async (a: AutomationItem) => api.put(`/automations/${a.id}`, { ...a, active: !a.active }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['automations'] }); qc.invalidateQueries({ queryKey: ['automation-runs'] }) },
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/automations/${id}`),
    onSuccess: () => { toast.push('success', 'Automation deleted'); qc.invalidateQueries({ queryKey: ['automations'] }); qc.invalidateQueries({ queryKey: ['automation-runs'] }) },
  })

  if (isLoading) return <PageLoader />

  return (
    <div>
      <PageHeader
        title="Automations"
        subtitle="Event-triggered workflow rules. Every execution is recorded and auditable — SEND_EMAIL runs are marked for review and never sent silently."
        actions={<Button onClick={() => setCreateOpen(true)}><Plus className="h-4 w-4" /> New automation</Button>}
      />

      <div className="space-y-3">
        {automations?.map((a) => (
          <Card key={a.id}>
            <CardBody className="flex flex-wrap items-center justify-between gap-3 !py-3.5">
              <div className="min-w-0">
                <p className="text-sm font-semibold text-slate-800">{a.name}</p>
                <p className="mt-0.5 text-xs text-slate-500">
                  When <Badge tone="blue">{a.trigger}</Badge> then <Badge tone="purple">{a.action}</Badge>
                  {a.runCount > 0 && <span className="ml-2 text-slate-400">{a.runCount} run(s)</span>}
                </p>
              </div>
              <div className="flex items-center gap-1.5">
                <button onClick={() => setRunsFor(a)} className="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium text-slate-500 hover:bg-slate-100"><History className="h-3.5 w-3.5" /> Runs</button>
                <button onClick={() => toggle.mutate(a)} className={`relative h-5 w-9 rounded-full transition-colors ${a.active ? 'bg-blue-600' : 'bg-slate-300'}`} aria-label="Toggle automation">
                  <span className={`absolute top-0.5 h-4 w-4 rounded-full bg-white transition-all ${a.active ? 'left-4' : 'left-0.5'}`} />
                </button>
                <Button variant="ghost" size="icon" onClick={() => setDeleteAutomation(a)} aria-label="Delete automation"><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button>
              </div>
            </CardBody>
          </Card>
        ))}
        {automations && automations.length === 0 && (
          <div className="card p-10 text-center">
            <Bot className="mx-auto mb-2 h-8 w-8 text-slate-300" />
            <p className="text-sm font-medium text-slate-600">No automations yet</p>
            <p className="mt-1 text-sm text-slate-400">Example: when NO_REPLY_AFTER 3 days → create a follow-up task.</p>
          </div>
        )}
      </div>

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} title="New automation" wide>
        <div className="space-y-3">
          <div>
            <Label required>Name</Label>
            <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="e.g. Re-engage silent leads" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label required>Trigger</Label>
              <Select value={form.trigger} onChange={(e) => setForm({ ...form, trigger: e.target.value })}>
                {TRIGGERS.map((t) => <option key={t}>{t}</option>)}
              </Select>
            </div>
            <div>
              <Label required>Action</Label>
              <Select value={form.action} onChange={(e) => setForm({ ...form, action: e.target.value })}>
                {ACTIONS.map((a) => <option key={a.id} value={a.id}>{a.label}</option>)}
              </Select>
            </div>
          </div>
          <div>
            <Label>Action config (JSON)</Label>
            <Input value={form.config} onChange={(e) => setForm({ ...form, config: e.target.value })} placeholder={'{"tag":"needs-attention"} or {"title":"Call back"} or {"days":3}'} className="font-mono text-xs" />
          </div>
          <div>
            <Label>Conditions (JSON, optional)</Label>
            <Input value={form.conditions} onChange={(e) => setForm({ ...form, conditions: e.target.value })} placeholder={'{"stage":"NEW"}'} className="font-mono text-xs" />
          </div>
          <p className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500">
            {form.action === 'SEND_EMAIL'
              ? 'SEND_EMAIL automations are queued in SKIPPED state pending review — the platform never sends autonomous email. An operator promotes each run after reading it.'
              : 'Conditions compare against lead fields (stage, status, custom fields). NO_REPLY_AFTER uses {"days": N} — default 3.'}
          </p>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button disabled={!form.name.trim()} loading={create.isPending} onClick={() => create.mutate()}>Create automation</Button>
        </div>
      </Dialog>

      <Dialog open={!!runsFor} onClose={() => setRunsFor(null)} title={`Runs — ${runsFor?.name}`} wide>
        {runs && runs.length > 0 ? (
          <div className="max-h-96 overflow-y-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-100 text-xs font-semibold uppercase text-slate-400">
                <tr><th className="px-3 py-2 text-left">When</th><th className="px-3 py-2 text-left">Status</th><th className="px-3 py-2 text-left">Detail</th></tr>
              </thead>
              <tbody>
                {(runs ?? []).map((r) => (
                  <tr key={r.id} className="border-b border-slate-50">
                    <td className="px-3 py-2 whitespace-nowrap text-slate-600">{fmtDateTime(r.executedAt)}</td>
                    <td className="px-3 py-2"><Badge tone={r.status === 'EXECUTED' ? 'green' : r.status === 'SKIPPED' ? 'yellow' : 'red'}>{r.status}</Badge></td>
                    <td className="px-3 py-2 text-xs text-slate-500">{r.detail ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : <p className="py-8 text-center text-sm text-slate-400">No runs recorded yet.</p>}
      </Dialog>

      <ConfirmDialog open={!!deleteAutomation} onClose={() => setDeleteAutomation(null)} onConfirm={async () => { if (deleteAutomation) await del.mutateAsync(deleteAutomation.id) }} title={`Delete “${deleteAutomation?.name}”?`} message="The rule stops firing immediately. Run history is retained for audit." confirmLabel="Delete" danger />
    </div>
  )
}
