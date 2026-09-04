import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, ShieldOff, Trash2 } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import { PageHeader } from '@/components/shared/PageHeader'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select, Label } from '@/components/ui/Input'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { DataTable, type Column } from '@/components/shared/DataTable'
import { fmtDate } from '@/lib/utils'
import type { PageResponse } from '@/types'

interface SuppressionRow { id: string; email: string; reason: string; note: string | null; createdAt: string }

export default function SuppressionsPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const [page, setPage] = useState(0)
  const [addOpen, setAddOpen] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState({ email: '', reason: 'UNSUBSCRIBE', note: '' })

  const { data, isFetching } = useQuery({
    queryKey: ['suppressions', page],
    queryFn: async () => (await api.get<PageResponse<SuppressionRow>>('/suppressions', { params: { page, size: 25, sort: 'createdAt,desc' } })).data,
  })

  const add = useMutation({
    mutationFn: async () => api.post('/suppressions', { email: form.email, reason: form.reason, note: form.note || null }),
    onSuccess: () => { toast.push('success', 'Address suppressed', 'They will never receive email from this organization.'); setAddOpen(false); qc.invalidateQueries({ queryKey: ['suppressions'] }) },
    onError: (e) => toast.push('error', 'Failed', apiError(e).message),
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/suppressions/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['suppressions'] }) },
  })

  const columns: Column<SuppressionRow>[] = [
    { key: 'email', header: 'Email', render: (r) => <span className="font-medium text-slate-700">{r.email}</span> },
    { key: 'reason', header: 'Reason', render: (r) => <Badge tone={r.reason === 'UNSUBSCRIBE' ? 'gray' : r.reason === 'BOUNCE' ? 'red' : 'yellow'}>{r.reason}</Badge> },
    { key: 'note', header: 'Note', render: (r) => <span className="text-slate-500">{r.note ?? '—'}</span> },
    { key: 'created', header: 'Since', render: (r) => <span className="text-slate-500">{fmtDate(r.createdAt)}</span> },
    {
      key: 'actions', header: '', render: (r) => (
        <div className="text-right">
          <Button variant="ghost" size="icon" title="Remove suppression" onClick={() => setDeleteId(r.id)}><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button>
        </div>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="Suppression list"
        subtitle="Unsubscribes, bounces, and complaints. The dispatcher blocks these addresses at send time — always."
        actions={<Button onClick={() => setAddOpen(true)}><Plus className="h-4 w-4" /> Suppress address</Button>}
      />
      <div className="card">
        <DataTable data={data} loading={isFetching} columns={columns} onPageChange={setPage}
          empty={{ icon: <ShieldOff className="h-6 w-6" />, title: 'Empty suppression list', subtitle: 'Addresses unsubscribe via campaign links or are added here manually.' }} />
      </div>

      <Dialog open={addOpen} onClose={() => setAddOpen(false)} title="Suppress an address">
        <div className="space-y-3">
          <div>
            <Label required>Email</Label>
            <Input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </div>
          <div>
            <Label>Reason</Label>
            <Select value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })}>
              {['UNSUBSCRIBE', 'BOUNCE', 'COMPLAINT', 'MANUAL'].map((r) => <option key={r}>{r}</option>)}
            </Select>
          </div>
          <div>
            <Label>Note</Label>
            <Input value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} />
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setAddOpen(false)}>Cancel</Button>
          <Button loading={add.isPending} onClick={() => add.mutate()}>Suppress</Button>
        </div>
      </Dialog>

      <ConfirmDialog
        open={!!deleteId}
        onClose={() => setDeleteId(null)}
        onConfirm={async () => { if (deleteId) await del.mutateAsync(deleteId); toast.push('success', 'Suppression removed') }}
        title="Remove suppression?"
        message="This address becomes contactable again. Only remove it if you're certain the recipient consents."
        confirmLabel="Remove"
        danger
      />
    </div>
  )
}
