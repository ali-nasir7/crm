import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Megaphone, Plus, Trash2 } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useCan } from '@/stores/auth'
import { useToast } from '@/components/ui/Toast'
import type { AccountItem, CampaignItem, PageResponse, TemplateItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable, type Column } from '@/components/shared/DataTable'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select, Label } from '@/components/ui/Input'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { fmtDate } from '@/lib/utils'

const STATUS_TONES: Record<string, 'gray' | 'blue' | 'green' | 'red' | 'yellow'> = {
  DRAFT: 'gray', SCHEDULED: 'yellow', RUNNING: 'blue', PAUSED: 'yellow', COMPLETED: 'green', CANCELLED: 'red',
}

export default function CampaignsPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const canCreate = useCan('CAMPAIGN_CREATE')
  const [page, setPage] = useState(0)
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState({ name: '', description: '', accountId: '', templateId: '', scheduledAt: '' })

  const { data, isFetching } = useQuery({
    queryKey: ['campaigns', page],
    queryFn: async () => (await api.get<PageResponse<CampaignItem>>('/campaigns', { params: { page, size: 20, sort: 'createdAt,desc' } })).data,
  })
  const { data: accounts } = useQuery({ queryKey: ['email-accounts'], queryFn: async () => (await api.get<AccountItem[]>('/email-accounts')).data })
  const { data: templates } = useQuery({ queryKey: ['email-templates'], queryFn: async () => (await api.get<PageResponse<TemplateItem>>('/email-templates', { params: { size: 100 } })).data })

  const create = useMutation({
    mutationFn: async () =>
      api.post('/campaigns', {
        name: form.name,
        description: form.description || null,
        accountId: form.accountId || null,
        steps: form.templateId ? [{ templateId: form.templateId, delayDays: 0 }] : [],
        scheduledAt: form.scheduledAt ? new Date(form.scheduledAt).toISOString() : null,
      }),
    onSuccess: () => { toast.push('success', 'Campaign created', 'Add recipients, then start it.'); setCreateOpen(false); qc.invalidateQueries({ queryKey: ['campaigns'] }) },
    onError: (e) => toast.push('error', 'Could not create campaign', apiError(e).message),
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/campaigns/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['campaigns'] }) },
  })

  const columns: Column<CampaignItem>[] = [
    { key: 'name', header: 'Campaign', render: (c) => (
      <div>
        <p className="font-medium text-slate-800">{c.name}</p>
        <p className="text-xs text-slate-500">{c.accountEmail ?? 'default account'}</p>
      </div>
    ) },
    { key: 'status', header: 'Status', render: (c) => <Badge tone={STATUS_TONES[c.status] ?? 'gray'}>{c.status}</Badge> },
    { key: 'recipients', header: 'Recipients', render: (c) => <span className="tabular-nums text-slate-700">{c.totalRecipients.toLocaleString()}</span> },
    { key: 'sent', header: 'Sent', render: (c) => <span className="tabular-nums text-slate-600">{c.sentCount.toLocaleString()}</span> },
    { key: 'opens', header: 'Opens', render: (c) => <span className="tabular-nums text-emerald-600">{c.openCount.toLocaleString()}</span> },
    { key: 'replies', header: 'Replies', render: (c) => <span className="tabular-nums text-blue-600">{c.replyCount.toLocaleString()}</span> },
    { key: 'bounces', header: 'Bounces', render: (c) => <span className="tabular-nums text-red-500">{c.bounceCount.toLocaleString()}</span> },
    { key: 'created', header: 'Created', render: (c) => <span className="text-slate-500">{fmtDate(c.createdAt)}</span> },
    {
      key: 'actions', header: '', render: (c) => (
        <div className="text-right">
          <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); setDeleteId(c.id) }} aria-label="Delete campaign"><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button>
        </div>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="Campaigns"
        subtitle="Multi-step email sequences with {{variables}}, per-recipient throttling, and mandatory suppression handling."
        actions={canCreate ? <Button onClick={() => setCreateOpen(true)}><Plus className="h-4 w-4" /> New campaign</Button> : undefined}
      />
      <div className="card">
        <DataTable data={data} loading={isFetching} columns={columns} onPageChange={setPage} onRowClick={(c) => navigate(`/campaigns/${c.id}`)}
          empty={{ icon: <Megaphone className="h-6 w-6" />, title: 'No campaigns', subtitle: 'Create a campaign, add recipients, and start the sequence.' }} />
      </div>

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} title="New campaign">
        <div className="space-y-3">
          <div>
            <Label required>Name</Label>
            <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="e.g. UAE clinics — Q3 outreach" />
          </div>
          <div>
            <Label>Description</Label>
            <Input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>Send from</Label>
              <Select value={form.accountId} onChange={(e) => setForm({ ...form, accountId: e.target.value })}>
                <option value="">Default</option>
                {accounts?.map((a) => <option key={a.id} value={a.id}>{a.email}</option>)}
              </Select>
            </div>
            <div>
              <Label>First-step template</Label>
              <Select value={form.templateId} onChange={(e) => setForm({ ...form, templateId: e.target.value })}>
                <option value="">Select…</option>
                {templates?.content.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
              </Select>
            </div>
          </div>
          <div>
            <Label>Schedule (optional)</Label>
            <Input type="datetime-local" value={form.scheduledAt} onChange={(e) => setForm({ ...form, scheduledAt: e.target.value })} />
          </div>
          <p className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500">
            Recipients are only contacted with proper authority — campaigns must use opted-in, business-contact lists from your CRM. Suppressed addresses are skipped automatically.
          </p>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button disabled={!form.name.trim() || !form.templateId} loading={create.isPending} onClick={() => create.mutate()}>Create campaign</Button>
        </div>
      </Dialog>

      <ConfirmDialog open={!!deleteId} onClose={() => setDeleteId(null)} onConfirm={async () => { if (deleteId) await del.mutateAsync(deleteId) }} title="Delete campaign?" message="Scheduled sends are cancelled. Sent email history stays in activity records." confirmLabel="Delete" danger />
    </div>
  )
}
