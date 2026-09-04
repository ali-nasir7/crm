import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Handshake, Plus, Trash2 } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useAuth, useCan } from '@/stores/auth'
import { useToast } from '@/components/ui/Toast'
import type { DealItem, DealSummary, PageResponse, PipelineItem, UserItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable, type Column } from '@/components/shared/DataTable'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select, Label } from '@/components/ui/Input'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { fmtDate, fmtMoney } from '@/lib/utils'

export default function DealsPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const { user } = useAuth()
  const canCreate = useCan('DEAL_CREATE')
  const [params] = useSearchParams()
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('')
  const [stageId, setStageId] = useState('')
  const [createOpen, setCreateOpen] = useState(params.get('new') === '1')
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState({ title: '', companyId: '', leadId: '', amount: '', currency: 'USD', stageId: '', expectedCloseDate: '', notes: '' })

  const { data: pipelines } = useQuery({ queryKey: ['pipelines'], queryFn: async () => (await api.get<PipelineItem[]>('/pipelines')).data })
  const { data: summary } = useQuery({ queryKey: ['deals-summary'], queryFn: async () => (await api.get<DealSummary>('/deals/summary')).data })
  const { data, isFetching } = useQuery({
    queryKey: ['deals', page, status, stageId],
    queryFn: async () => (await api.get<PageResponse<DealItem>>('/deals', { params: { page, size: 25, status: status || undefined, stageId: stageId || undefined, sort: 'createdAt,desc' } })).data,
  })
  const { data: users } = useQuery({ queryKey: ['users-lite'], queryFn: async () => (await api.get<PageResponse<UserItem>>('/users', { params: { size: 200 } })).data })

  const allStages = pipelines?.flatMap((p) => p.stages.map((s) => ({ ...s, pipeline: p.name }))) ?? []

  const create = useMutation({
    mutationFn: async () =>
      api.post('/deals', {
        title: form.title,
        companyId: form.companyId || null,
        leadId: form.leadId || null,
        amount: form.amount ? parseFloat(form.amount) : null,
        currency: form.currency,
        stageId: form.stageId || null,
        expectedCloseDate: form.expectedCloseDate ? new Date(form.expectedCloseDate).toISOString() : null,
        notes: form.notes || null,
        ownerId: user?.id,
      }),
    onSuccess: () => { toast.push('success', 'Deal created'); setCreateOpen(false); qc.invalidateQueries({ queryKey: ['deals'] }); qc.invalidateQueries({ queryKey: ['deals-summary'] }) },
    onError: (e) => toast.push('error', 'Could not create deal', apiError(e).message),
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/deals/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['deals'] }); qc.invalidateQueries({ queryKey: ['deals-summary'] }) },
  })

  const columns: Column<DealItem>[] = [
    { key: 'title', header: 'Deal', render: (d) => (
      <div>
        <p className="font-medium text-slate-800">{d.title}</p>
        <p className="text-xs text-slate-500">{d.businessName ?? d.companyName ?? '—'}</p>
      </div>
    ) },
    { key: 'amount', header: 'Value', render: (d) => <span className="font-medium tabular-nums">{fmtMoney(d.amount, d.currency)}</span> },
    { key: 'stage', header: 'Stage', render: (d) => <Badge tone="blue">{d.stageName ?? '—'}</Badge> },
    { key: 'prob', header: 'Probability', render: (d) => <span className="tabular-nums text-slate-600">{d.probability}%</span> },
    { key: 'weighted', header: 'Weighted', render: (d) => <span className="tabular-nums text-slate-500">{fmtMoney((parseFloat(String(d.amount ?? '0')) * d.probability) / 100, d.currency)}</span> },
    { key: 'owner', header: 'Owner', render: (d) => <span className="text-slate-600">{d.ownerName}</span> },
    { key: 'close', header: 'Expected close', render: (d) => <span className="text-slate-500">{fmtDate(d.expectedCloseDate)}</span> },
    { key: 'status', header: 'Status', render: (d) => <Badge tone={d.status === 'WON' ? 'green' : d.status === 'LOST' ? 'red' : 'blue'}>{d.status}</Badge> },
    {
      key: 'actions', header: '', render: (d) => (
        <div className="text-right">
          <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); setDeleteId(d.id) }} aria-label="Delete deal"><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button>
        </div>
      ),
    },
  ]

  return (
    <div>
      <PageHeader title="Deals" actions={canCreate ? <Button onClick={() => setCreateOpen(true)}><Plus className="h-4 w-4" /> New deal</Button> : undefined} />

      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatBox label="Open pipeline" value={fmtMoney(summary?.openValue)} sub={`${summary?.openCount ?? 0} deals`} />
        <StatBox label="Weighted forecast" value={fmtMoney(summary?.weightedValue)} sub="probability-adjusted" />
        <StatBox label="Won revenue" value={fmtMoney(summary?.wonRevenue)} sub={`${summary?.wonCount ?? 0} won`} tone="text-emerald-600" />
        <StatBox label="Lost" value={fmtMoney(summary?.lostRevenue)} sub={`${summary?.lostCount ?? 0} lost`} tone="text-red-500" />
      </div>

      <div className="card mb-3 flex flex-wrap gap-2 p-3">
        <Select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }} className="w-36">
          <option value="">All statuses</option>
          <option value="OPEN">Open</option>
          <option value="WON">Won</option>
          <option value="LOST">Lost</option>
        </Select>
        <Select value={stageId} onChange={(e) => { setStageId(e.target.value); setPage(0) }} className="w-48">
          <option value="">All stages</option>
          {allStages.map((s) => <option key={s.id} value={s.id}>{s.name} ({s.pipeline})</option>)}
        </Select>
      </div>

      <div className="card">
        <DataTable data={data} loading={isFetching} columns={columns} onPageChange={setPage} onRowClick={(d) => d.leadId && navigate(`/leads/${d.leadId}`)}
          empty={{ icon: <Handshake className="h-6 w-6" />, title: 'No deals yet', subtitle: 'Deals are created on conversion, or directly here.' }} />
      </div>

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} title="New deal">
        <div className="space-y-3">
          <div>
            <Label required>Title</Label>
            <Input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="e.g. Clinic group — annual package" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>Amount</Label>
              <Input type="number" min="0" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} />
            </div>
            <div>
              <Label>Currency</Label>
              <Select value={form.currency} onChange={(e) => setForm({ ...form, currency: e.target.value })}>
                {['USD', 'EUR', 'GBP', 'AED', 'SAR'].map((c) => <option key={c}>{c}</option>)}
              </Select>
            </div>
            <div>
              <Label>Stage</Label>
              <Select value={form.stageId} onChange={(e) => setForm({ ...form, stageId: e.target.value })}>
                <option value="">First stage</option>
                {allStages.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </Select>
            </div>
            <div>
              <Label>Expected close</Label>
              <Input type="date" value={form.expectedCloseDate} onChange={(e) => setForm({ ...form, expectedCloseDate: e.target.value })} />
            </div>
          </div>
          <p className="text-xs text-slate-400">Owner defaults to you. Link a company/lead by converting from a lead for full history linking.</p>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button disabled={!form.title.trim()} loading={create.isPending} onClick={() => create.mutate()}>Create deal</Button>
        </div>
      </Dialog>

      <ConfirmDialog open={!!deleteId} onClose={() => setDeleteId(null)} onConfirm={async () => { if (deleteId) await del.mutateAsync(deleteId); toast.push('success', 'Deal deleted') }} title="Delete deal?" message="The deal is soft-deleted and hidden from lists and forecasts." confirmLabel="Delete" danger />
    </div>
  )
}

function StatBox({ label, value, sub, tone }: { label: string; value: string; sub?: string; tone?: string }) {
  return (
    <div className="card px-4 py-3">
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">{label}</p>
      <p className={`mt-1 text-lg font-bold tabular-nums ${tone ?? 'text-slate-900'}`}>{value ?? '—'}</p>
      {sub && <p className="text-xs text-slate-400">{sub}</p>}
    </div>
  )
}
