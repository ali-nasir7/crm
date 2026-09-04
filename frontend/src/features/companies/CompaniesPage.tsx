import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { Building2, Plus, Trash2 } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useAuth, useCan } from '@/stores/auth'
import { useToast } from '@/components/ui/Toast'
import { useDebounce } from '@/hooks/useDebounce'
import type  { CompanyItem, PageResponse }  from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable, type Column } from '@/components/shared/DataTable'
import { Button } from '@/components/ui/Button'
import { Input, Select, Textarea, Label } from '@/components/ui/Input'
import { Dialog } from '@/components/ui/Dialog'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Badge } from '@/components/ui/Badge'
import { fmtDate } from '@/lib/utils'

const EMPTY = { name: '', website: '', industry: '', phone: '', email: '', country: '', city: '', companySize: '', description: '' }

export default function CompaniesPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const { user } = useAuth()
  const canCreate = useCan('COMPANY_CREATE')
  const canDelete = useCan('COMPANY_DELETE')
  const [params] = useSearchParams()
  const [q, setQ] = useState('')
  const debounced = useDebounce(q)
  const [page, setPage] = useState(0)
  const [createOpen, setCreateOpen] = useState(params.get('new') === '1')
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState(EMPTY)

  const { data, isFetching } = useQuery({
    queryKey: ['companies', page, debounced],
    queryFn: async () => (await api.get<PageResponse<CompanyItem>>('/companies', { params: { page, size: 25, q: debounced || undefined, sort: 'createdAt,desc' } })).data,
  })

  const create = useMutation({
    mutationFn: async () => api.post('/companies', { ...form, ownerId: user?.id }),
    onSuccess: () => { toast.push('success', 'Company created'); setCreateOpen(false); setForm(EMPTY); qc.invalidateQueries({ queryKey: ['companies'] }) },
    onError: (e) => toast.push('error', 'Could not create company', apiError(e).message),
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/companies/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['companies'] }); toast.push('success', 'Company deleted') },
  })

  const columns: Column<CompanyItem>[] = [
    { key: 'name', header: 'Company', render: (c) => (
      <div>
        <p className="font-medium text-slate-800">{c.name}</p>
        <p className="text-xs text-slate-500">{c.website ?? c.industry ?? ''}</p>
      </div>
    ) },
    { key: 'industry', header: 'Industry', render: (c) => <span className="text-slate-600">{c.industry ?? '—'}</span> },
    { key: 'location', header: 'Location', render: (c) => <span className="text-slate-600">{[c.city, c.country].filter(Boolean).join(', ') || '—'}</span> },
    { key: 'size', header: 'Size', render: (c) => <Badge tone="gray">{c.companySize ?? '—'}</Badge> },
    { key: 'owner', header: 'Owner', render: (c) => <span className="text-slate-600">{c.ownerName ?? '—'}</span> },
    { key: 'created', header: 'Created', render: (c) => <span className="text-slate-500">{fmtDate(c.createdAt)}</span> },
    {
      key: 'actions', header: '', render: (c) => canDelete ? (
        <div className="text-right">
          <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); setDeleteId(c.id) }} aria-label="Delete company"><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button>
        </div>
      ) : <span />,
    },
  ]

  return (
    <div>
      <PageHeader title="Companies" actions={canCreate ? <Button onClick={() => setCreateOpen(true)}><Plus className="h-4 w-4" /> New company</Button> : undefined} />
      <div className="card mb-3 p-3">
        <Input value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} placeholder="Search companies…" className="max-w-md" />
      </div>
      <div className="card">
        <DataTable data={data} loading={isFetching} columns={columns} onPageChange={setPage}
          empty={{ icon: <Building2 className="h-6 w-6" />, title: 'No companies', subtitle: 'Companies are created automatically during lead conversion, or add them here.' }} />
      </div>

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} title="New company" wide>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <Label required>Name</Label>
            <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </div>
          <div>
            <Label>Website</Label>
            <Input value={form.website} onChange={(e) => setForm({ ...form, website: e.target.value })} />
          </div>
          <div>
            <Label>Industry</Label>
            <Input value={form.industry} onChange={(e) => setForm({ ...form, industry: e.target.value })} />
          </div>
          <div>
            <Label>Company size</Label>
            <Select value={form.companySize} onChange={(e) => setForm({ ...form, companySize: e.target.value })}>
              <option value="">Select…</option>
              {['1-10', '11-50', '51-200', '201-500', '500+'].map((s) => <option key={s}>{s}</option>)}
            </Select>
          </div>
          <div>
            <Label>Phone</Label>
            <Input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          </div>
          <div>
            <Label>Email</Label>
            <Input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </div>
          <div>
            <Label>Country</Label>
            <Input value={form.country} onChange={(e) => setForm({ ...form, country: e.target.value })} />
          </div>
          <div>
            <Label>City</Label>
            <Input value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} />
          </div>
          <div className="sm:col-span-2">
            <Label>Description</Label>
            <Textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button disabled={!form.name.trim()} loading={create.isPending} onClick={() => create.mutate()}>Create company</Button>
        </div>
      </Dialog>

      <ConfirmDialog open={!!deleteId} onClose={() => setDeleteId(null)} onConfirm={async () => { if (deleteId) await del.mutateAsync(deleteId) }} title="Delete company?" message="Clients and contacts linked to this company keep their records. The company itself is removed from lists." confirmLabel="Delete" danger />
    </div>
  )
}
