import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trophy, Trash2 } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import type { CustomFieldItem, ScoringRuleItem, TagItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select, Label } from '@/components/ui/Input'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { PageLoader } from '@/components/ui/Misc'

const CRITERIA = [
  { id: 'SOURCE_IS', label: 'Source is', operands: 'source keys' },
  { id: 'HAS_TAG', label: 'Has tag', operands: 'tag names' },
  { id: 'STATUS_IS', label: 'Status is', operands: 'NEW, WORKING…' },
  { id: 'CITY_IN', label: 'City in', operands: 'comma-separated cities' },
  { id: 'COUNTRY_IN', label: 'Country in', operands: 'comma-separated countries' },
  { id: 'HAS_EMAIL', label: 'Has email', operands: '—' },
  { id: 'HAS_PHONE', label: 'Has phone', operands: '—' },
  { id: 'CUSTOM_FIELD_IS', label: 'Custom field is', operands: 'key=value' },
]

export default function ScoringPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteRule, setDeleteRule] = useState<ScoringRuleItem | null>(null)
  const [form, setForm] = useState({ criterion: 'SOURCE_IS', operand: '', points: '10', label: '' })

  const { data: rules, isLoading } = useQuery({ queryKey: ['scoring-rules'], queryFn: async () => (await api.get<ScoringRuleItem[]>('/scoring-rules')).data })
  const { data: tags } = useQuery({ queryKey: ['tags'], queryFn: async () => (await api.get<TagItem[]>('/tags')).data })
  const { data: fields } = useQuery({ queryKey: ['custom-fields'], queryFn: async () => (await api.get<CustomFieldItem[]>('/custom-fields')).data })

  const create = useMutation({
    mutationFn: async () => api.post('/scoring-rules', { criterion: form.criterion, operand: form.operand || null, points: parseInt(form.points, 10) || 0, label: form.label || `${form.criterion} ${form.operand}`.trim() }),
    onSuccess: () => { toast.push('success', 'Rule added', 'Scores recalculate on the next lead update or manual rescore.'); setCreateOpen(false); qc.invalidateQueries({ queryKey: ['scoring-rules'] }) },
    onError: (e) => toast.push('error', 'Failed', apiError(e).message),
  })

  const toggle = useMutation({
    mutationFn: async (r: ScoringRuleItem) => api.put(`/scoring-rules/${r.id}`, { ...r, active: !r.active }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['scoring-rules'] }),
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/scoring-rules/${id}`),
    onSuccess: () => { toast.push('success', 'Rule removed'); qc.invalidateQueries({ queryKey: ['scoring-rules'] }) },
  })

  if (isLoading) return <PageLoader />

  return (
    <div>
      <PageHeader
        title="Lead scoring"
        subtitle="Additive rules, clamped to 0–100. VERY_HOT ≥ 75 · HOT ≥ 50 · WARM ≥ 25 · COLD."
        actions={<Button onClick={() => setCreateOpen(true)}><Plus className="h-4 w-4" /> New rule</Button>}
      />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title={`Rules (${rules?.length ?? 0})`} subtitle="Higher position = evaluated first. Points may be negative." />
          <CardBody className="!px-0">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-100 bg-slate-50/60 text-xs font-semibold uppercase text-slate-500">
                <tr><th className="px-5 py-2 text-left">Criterion</th><th className="px-3 py-2 text-left">Match</th><th className="px-3 py-2 text-right">Points</th><th className="px-3 py-2 text-left">Active</th><th /></tr>
              </thead>
              <tbody>
                {rules?.map((r) => (
                  <tr key={r.id} className="border-b border-slate-50 last:border-0">
                    <td className="px-5 py-2.5">
                      <p className="font-medium text-slate-700">{r.label}</p>
                      <p className="font-mono text-[10px] text-slate-400">{r.criterion}{r.operand ? ` ${r.operand}` : ''}</p>
                    </td>
                    <td className="px-3 py-2.5 text-xs text-slate-500">{r.operand ?? 'any'}</td>
                    <td className="px-3 py-2.5 text-right"><Badge tone={r.points >= 0 ? 'green' : 'red'}>{r.points >= 0 ? '+' : ''}{r.points}</Badge></td>
                    <td className="px-3 py-2.5">
                      <button onClick={() => toggle.mutate(r)} className={`relative h-5 w-9 rounded-full transition-colors ${r.active ? 'bg-blue-600' : 'bg-slate-300'}`} aria-label="Toggle rule">
                        <span className={`absolute top-0.5 h-4 w-4 rounded-full bg-white transition-all ${r.active ? 'left-4' : 'left-0.5'}`} />
                      </button>
                    </td>
                    <td className="px-5 py-2.5 text-right"><Button variant="ghost" size="icon" onClick={() => setDeleteRule(r)} aria-label="Delete rule"><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="How scoring works" />
          <CardBody className="space-y-2 text-sm text-slate-600">
            <p>Every matching rule adds its points. The sum is clamped to <span className="font-mono text-xs">0–100</span>.</p>
            <p>Scores recalculate when leads are created or updated, when activities are logged (e.g. after a call), and on demand.</p>
            <p className="text-xs text-slate-400">Categories: VERY_HOT ≥ 75 · HOT ≥ 50 · WARM ≥ 25 · otherwise COLD. Categories drive sorting, views, and automation triggers.</p>
            <div className="rounded-lg bg-slate-50 p-3 text-xs">
              <p className="font-semibold text-slate-600">Existing tags</p>
              <p className="mt-1 text-slate-500">{tags?.map((t) => t.name).join(' · ') || 'none'}</p>
              <p className="mt-2 font-semibold text-slate-600">Custom field keys</p>
              <p className="mt-1 font-mono text-[11px] text-slate-500">{fields?.map((f) => f.key).join(' · ') || 'none'}</p>
            </div>
          </CardBody>
        </Card>
      </div>

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} title="New scoring rule">
        <div className="space-y-3">
          <div>
            <Label required>Criterion</Label>
            <Select value={form.criterion} onChange={(e) => setForm({ ...form, criterion: e.target.value })}>
              {CRITERIA.map((c) => <option key={c.id} value={c.id}>{c.label} — {c.operands}</option>)}
            </Select>
          </div>
          <div>
            <Label>Match value</Label>
            <Input value={form.operand} onChange={(e) => setForm({ ...form, operand: e.target.value })} placeholder={form.criterion === 'CUSTOM_FIELD_IS' ? 'chairs=5' : form.criterion === 'CITY_IN' ? 'Dubai, Abu Dhabi' : 'e.g. REFERRAL'} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label required>Points (can be negative)</Label>
              <Input type="number" value={form.points} onChange={(e) => setForm({ ...form, points: e.target.value })} />
            </div>
            <div>
              <Label>Label</Label>
              <Input value={form.label} onChange={(e) => setForm({ ...form, label: e.target.value })} placeholder="Auto if empty" />
            </div>
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button loading={create.isPending} onClick={() => create.mutate()}>Add rule</Button>
        </div>
      </Dialog>

      <ConfirmDialog open={!!deleteRule} onClose={() => setDeleteRule(null)} onConfirm={async () => { if (deleteRule) await del.mutateAsync(deleteRule.id) }} title={`Delete rule “${deleteRule?.label}”?`} message="Scores recalculate without this rule's contribution." confirmLabel="Delete" danger />
      <p className="mt-4 flex items-center gap-2 text-xs text-slate-400"><Trophy className="h-3.5 w-3.5" /> Rules apply per organization; the scoring engine never loads more than the leads being scored.</p>
    </div>
  )
}
