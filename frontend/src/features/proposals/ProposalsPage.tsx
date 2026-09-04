import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, FileText, Plus, Send, Trash2 } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useAuth, useCan } from '@/stores/auth'
import { useToast } from '@/components/ui/Toast'
import type { PageResponse, ProposalItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable, type Column } from '@/components/shared/DataTable'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select, Textarea, Label } from '@/components/ui/Input'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { fmtDate, fmtMoney, fmtDateTime } from '@/lib/utils'

const STATUS_TONES: Record<string, 'gray' | 'blue' | 'green' | 'red' | 'yellow'> = {
  DRAFT: 'gray', SENT: 'blue', VIEWED: 'yellow', NEGOTIATING: 'yellow', ACCEPTED: 'green', REJECTED: 'red', EXPIRED: 'gray',
}

export default function ProposalsPage() {
  return <ProposalsList />
}

function ProposalsList() {
  const qc = useQueryClient()
  const toast = useToast()
  const { user } = useAuth()
  const canCreate = useCan('PROPOSAL_CREATE')
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState({ title: '', description: '', currency: 'USD', validUntil: '', terms: '' })

  const { data, isFetching } = useQuery({
    queryKey: ['proposals', page, status],
    queryFn: async () => (await api.get<PageResponse<ProposalItem>>('/proposals', { params: { page, size: 25, status: status || undefined, sort: 'createdAt,desc' } })).data,
  })

  const create = useMutation({
    mutationFn: async () =>
      api.post('/proposals', {
        title: form.title,
        description: form.description || null,
        currency: form.currency,
        validUntil: form.validUntil ? new Date(form.validUntil).toISOString() : null,
        terms: form.terms || null,
        ownerId: user?.id,
      }),
    onSuccess: () => { toast.push('success', 'Proposal created', 'Add line items, then send it.'); setCreateOpen(false); qc.invalidateQueries({ queryKey: ['proposals'] }) },
    onError: (e) => toast.push('error', 'Could not create proposal', apiError(e).message),
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/proposals/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['proposals'] }) },
  })

  const columns: Column<ProposalItem>[] = [
    { key: 'number', header: 'Number', render: (p) => <span className="font-mono text-xs text-slate-500">{p.proposalNumber}</span> },
    { key: 'title', header: 'Proposal', render: (p) => <span className="font-medium text-slate-800">{p.title}</span> },
    { key: 'amount', header: 'Total', render: (p) => <span className="font-medium tabular-nums">{fmtMoney(p.total, p.currency)}</span> },
    { key: 'lead', header: 'Lead', render: (p) => <span className="text-slate-600">{p.businessName ?? '—'}</span> },
    { key: 'status', header: 'Status', render: (p) => <Badge tone={STATUS_TONES[p.status] ?? 'gray'}>{p.status}</Badge> },
    { key: 'sent', header: 'Sent', render: (p) => <span className="text-slate-500">{p.sentAt ? fmtDateTime(p.sentAt) : '—'}</span> },
    { key: 'valid', header: 'Valid until', render: (p) => <span className="text-slate-500">{fmtDate(p.validUntil)}</span> },
    {
      key: 'actions', header: '', render: (p) => (
        <div className="text-right">
          <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); setDeleteId(p.id) }} aria-label="Delete proposal"><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button>
        </div>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="Proposals"
        subtitle="Line-item proposals with PDF generation and status tracking."
        actions={canCreate ? <Button onClick={() => setCreateOpen(true)}><Plus className="h-4 w-4" /> New proposal</Button> : undefined}
      />
      <div className="card mb-3 p-3">
        <Select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }} className="w-44">
          <option value="">All statuses</option>
          {Object.keys(STATUS_TONES).map((s) => <option key={s}>{s}</option>)}
        </Select>
      </div>
      <div className="card">
        <DataTable data={data} loading={isFetching} columns={columns} onPageChange={setPage}
          empty={{ icon: <FileText className="h-6 w-6" />, title: 'No proposals yet' }} />
      </div>

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} title="New proposal">
        <div className="space-y-3">
          <div>
            <Label required>Title</Label>
            <Input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="e.g. Dental equipment supply — annual" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>Currency</Label>
              <Select value={form.currency} onChange={(e) => setForm({ ...form, currency: e.target.value })}>
                {['USD', 'EUR', 'GBP', 'AED', 'SAR'].map((c) => <option key={c}>{c}</option>)}
              </Select>
            </div>
            <div>
              <Label>Valid until</Label>
              <Input type="date" value={form.validUntil} onChange={(e) => setForm({ ...form, validUntil: e.target.value })} />
            </div>
          </div>
          <div>
            <Label>Terms</Label>
            <Textarea value={form.terms} onChange={(e) => setForm({ ...form, terms: e.target.value })} placeholder="Payment terms, delivery, warranty…" />
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button disabled={!form.title.trim()} loading={create.isPending} onClick={() => create.mutate()}>Create proposal</Button>
        </div>
      </Dialog>

      <ConfirmDialog open={!!deleteId} onClose={() => setDeleteId(null)} onConfirm={async () => { if (deleteId) await del.mutateAsync(deleteId) }} title="Delete proposal?" message="The proposal and its line items are removed." confirmLabel="Delete" danger />
    </div>
  )
}

export function ProposalDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const [item, setItem] = useState({ name: '', quantity: '1', unitPrice: '', description: '' })

  const { data: p } = useQuery({
    queryKey: ['proposal', id],
    queryFn: async () => (await api.get<ProposalItem>(`/proposals/${id}`)).data,
    enabled: !!id,
  })

  const addItem = useMutation({
    mutationFn: async () => api.post(`/proposals/${id}/items`, { name: item.name, quantity: parseFloat(item.quantity) || 1, unitPrice: parseFloat(item.unitPrice) || 0, description: item.description || null }),
    onSuccess: () => { setItem({ name: '', quantity: '1', unitPrice: '', description: '' }); qc.invalidateQueries({ queryKey: ['proposal', id] }) },
    onError: (e) => toast.push('error', 'Could not add item', apiError(e).message),
  })

  const removeItem = useMutation({
    mutationFn: async (itemId: string) => api.delete(`/proposals/${id}/items/${itemId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['proposal', id] }),
  })

  const send = useMutation({
    mutationFn: async () => api.post(`/proposals/${id}/send`),
    onSuccess: () => { toast.push('success', 'Proposal sent', 'The client receives a PDF by email.'); qc.invalidateQueries({ queryKey: ['proposal', id] }); qc.invalidateQueries({ queryKey: ['proposals'] }) },
    onError: (e) => toast.push('error', 'Send failed', apiError(e).message),
  })

  const setStatus_ = useMutation({
    mutationFn: async (status: string) => api.post(`/proposals/${id}/status`, { status }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['proposal', id] }),
  })

  if (!id || !p) return null

  return (
    <div>
      <button onClick={() => navigate('/proposals')} className="mb-3 inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700">
        <ArrowLeft className="h-4 w-4" /> Back to proposals
      </button>
      <PageHeader
        title={`${p.proposalNumber} — ${p.title}`}
        subtitle={`${p.companyName ?? p.businessName ?? 'No account linked'} · created ${fmtDate(p.createdAt)}`}
        actions={
          <>
            <a href={`/api/v1/proposals/${p.id}/pdf`} target="_blank" rel="noreferrer"><Button variant="secondary">View PDF</Button></a>
            {p.status === 'DRAFT' && <Button loading={send.isPending} onClick={() => send.mutate()}><Send className="h-4 w-4" /> Send</Button>}
            {p.status === 'SENT' && <Button variant="secondary" onClick={() => setStatus_.mutate('ACCEPTED')}>Mark accepted</Button>}
            <Badge tone={STATUS_TONES[p.status] ?? 'gray'}>{p.status}</Badge>
          </>
        }
      />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Line items" />
          <CardBody className="!px-0">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-100 text-xs font-semibold uppercase text-slate-400">
                <tr><th className="px-5 py-2 text-left">Item</th><th className="px-3 py-2 text-right">Qty</th><th className="px-3 py-2 text-right">Unit price</th><th className="px-3 py-2 text-right">Total</th><th /></tr>
              </thead>
              <tbody>
                {p.items.map((it) => (
                  <tr key={it.id} className="border-b border-slate-50">
                    <td className="px-5 py-2.5"><p className="font-medium text-slate-700">{it.name}</p>{it.description && <p className="text-xs text-slate-400">{it.description}</p>}</td>
                    <td className="px-3 py-2.5 text-right tabular-nums">{it.quantity}</td>
                    <td className="px-3 py-2.5 text-right tabular-nums">{fmtMoney(it.unitPrice, p.currency)}</td>
                    <td className="px-3 py-2.5 text-right font-medium tabular-nums">{fmtMoney(it.total, p.currency)}</td>
                    <td className="px-3 py-2.5 text-right"><Button variant="ghost" size="icon" onClick={() => removeItem.mutate(it.id)}><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button></td>
                  </tr>
                ))}
                {p.items.length === 0 && <tr><td colSpan={5} className="px-5 py-8 text-center text-sm text-slate-400">No items yet — add the first one below.</td></tr>}
              </tbody>
            </table>
          </CardBody>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader title="Add line item" />
            <CardBody className="space-y-3">
              <div>
                <Label required>Name</Label>
                <Input value={item.name} onChange={(e) => setItem({ ...item, name: e.target.value })} placeholder="e.g. Annual license" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label>Quantity</Label>
                  <Input type="number" min="0" step="0.01" value={item.quantity} onChange={(e) => setItem({ ...item, quantity: e.target.value })} />
                </div>
                <div>
                  <Label>Unit price</Label>
                  <Input type="number" min="0" step="0.01" value={item.unitPrice} onChange={(e) => setItem({ ...item, unitPrice: e.target.value })} />
                </div>
              </div>
              <Button className="w-full" disabled={!item.name.trim() || !item.unitPrice} loading={addItem.isPending} onClick={() => addItem.mutate()}><Plus className="h-4 w-4" /> Add item</Button>
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Totals" />
            <CardBody className="space-y-1.5 text-sm">
              <Line label="Subtotal" value={fmtMoney(p.subtotal, p.currency)} />
              {p.discountPercent && <Line label={`Discount (${p.discountPercent}%)`} value={`− ${fmtMoney(p.discountAmount, p.currency)}`} />}
              {p.taxPercent && <Line label={`Tax (${p.taxPercent}%)`} value={fmtMoney(p.taxAmount, p.currency)} />}
              <div className="flex justify-between border-t border-slate-100 pt-2 text-base font-bold">
                <span>Total</span><span className="tabular-nums">{fmtMoney(p.total, p.currency)}</span>
              </div>
              {p.viewedAt && <p className="pt-1 text-xs text-emerald-600">Client viewed {fmtDateTime(p.viewedAt)}</p>}
            </CardBody>
          </Card>
        </div>
      </div>
    </div>
  )
}

function Line({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between text-slate-600">
      <span>{label}</span><span className="tabular-nums">{value}</span>
    </div>
  )
}
