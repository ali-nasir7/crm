import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Database, Plus, Trash2 } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import type { CustomFieldItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Label } from '@/components/ui/Input'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { PageLoader } from '@/components/ui/Misc'

const TYPES = ['TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT']

export default function CustomFieldsPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteField, setDeleteField] = useState<CustomFieldItem | null>(null)
  const [form, setForm] = useState({ key: '', label: '', type: 'TEXT', options: '' })

  const { data: fields, isLoading } = useQuery({ queryKey: ['custom-fields'], queryFn: async () => (await api.get<CustomFieldItem[]>('/custom-fields')).data })

  const create = useMutation({
    mutationFn: async () => api.post('/custom-fields', { key: form.key, label: form.label, type: form.type, options: form.type === 'SELECT' ? form.options.split(',').map((o) => o.trim()).filter(Boolean) : null }),
    onSuccess: () => { toast.push('success', 'Custom field created', 'It appears on lead forms and in the import wizard.'); setCreateOpen(false); qc.invalidateQueries({ queryKey: ['custom-fields'] }) },
    onError: (e) => toast.push('error', 'Failed', apiError(e).message),
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/custom-fields/${id}`),
    onSuccess: () => { toast.push('success', 'Field removed', 'Values remain in lead JSON for audit.'); qc.invalidateQueries({ queryKey: ['custom-fields'] }) },
  })

  if (isLoading) return <PageLoader />

  return (
    <div>
      <PageHeader
        title="Custom lead fields"
        subtitle="Clinic-niche or market-specific fields, stored as indexed JSON on leads and available in imports, scoring, and reports."
        actions={<Button onClick={() => setCreateOpen(true)}><Plus className="h-4 w-4" /> New field</Button>}
      />
      <Card>
        <CardHeader title={`Configured fields (${fields?.length ?? 0})`} />
        <CardBody className="!px-0">
          <table className="w-full text-sm">
            <thead className="border-b border-slate-100 bg-slate-50/60 text-xs font-semibold uppercase text-slate-500">
              <tr><th className="px-5 py-2 text-left">Key</th><th className="px-5 py-2 text-left">Label</th><th className="px-5 py-2 text-left">Type</th><th className="px-5 py-2 text-left">Options</th><th className="px-5 py-2 text-right">Position</th><th /></tr>
            </thead>
            <tbody>
              {fields?.map((f) => (
                <tr key={f.id} className="border-b border-slate-50 last:border-0">
                  <td className="px-5 py-2.5 font-mono text-xs text-slate-600">{f.key}</td>
                  <td className="px-5 py-2.5 font-medium text-slate-700">{f.label}</td>
                  <td className="px-5 py-2.5"><Badge tone="gray">{f.type}</Badge></td>
                  <td className="px-5 py-2.5 text-xs text-slate-500">{f.options?.join(', ') ?? '—'}</td>
                  <td className="px-5 py-2.5 text-right tabular-nums text-slate-500">{f.position}</td>
                  <td className="px-5 py-2.5 text-right"><Button variant="ghost" size="icon" onClick={() => setDeleteField(f)} aria-label={`Delete ${f.label}`}><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button></td>
                </tr>
              ))}
              {fields && fields.length === 0 && <tr><td colSpan={6} className="px-5 py-10 text-center text-sm text-slate-400">No custom fields — add ones like “number_of_chairs”, “license_expiry”.</td></tr>}
            </tbody>
          </table>
        </CardBody>
      </Card>

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} title="New custom field">
        <div className="space-y-3">
          <div>
            <Label required>Key</Label>
            <Input value={form.key} onChange={(e) => setForm({ ...form, key: e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, '_') })} placeholder="number_of_chairs" />
          </div>
          <div>
            <Label required>Label</Label>
            <Input value={form.label} onChange={(e) => setForm({ ...form, label: e.target.value })} placeholder="Number of chairs" />
          </div>
          <div>
            <Label>Type</Label>
            <TypeSelect value={form.type} onChange={(v) => setForm({ ...form, type: v })} options={TYPES} />
          </div>
          {form.type === 'SELECT' && (
            <div>
              <Label required>Options (comma-separated)</Label>
              <Input value={form.options} onChange={(e) => setForm({ ...form, options: e.target.value })} placeholder="Option A, Option B, Option C" />
            </div>
          )}
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button disabled={!form.key || !form.label} loading={create.isPending} onClick={() => create.mutate()}>Create field</Button>
        </div>
      </Dialog>

      <ConfirmDialog open={!!deleteField} onClose={() => setDeleteField(null)} onConfirm={async () => { if (deleteField) await del.mutateAsync(deleteField.id) }} title={`Remove “${deleteField?.label}”?`} message="The field definition is removed. Existing lead values stay in stored JSON but stop appearing on forms." confirmLabel="Remove" danger />
      <p className="mt-4 flex items-center gap-2 text-xs text-slate-400"><Database className="h-3.5 w-3.5" /> Custom fields live in a GIN-indexed JSONB column — filtering stays fast at 100k+ leads.</p>
    </div>
  )
}

function TypeSelect({ value, onChange, options }: { value: string; onChange: (v: string) => void; options: string[] }) {
  return (
    <select value={value} onChange={(e) => onChange(e.target.value)} className="flex h-9 w-full rounded-lg border border-slate-200 bg-white px-2.5 text-sm">
      {(options ?? []).map((o) => <option key={o}>{o}</option>)}
    </select>
  )
}
