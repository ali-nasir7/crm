import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Contact2, Plus, Trash2 } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useAuth, useCan } from '@/stores/auth'
import { useToast } from '@/components/ui/Toast'
import { useDebounce } from '@/hooks/useDebounce'
import type { CompanyItem, ContactItem, PageResponse } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable, type Column } from '@/components/shared/DataTable'
import { Button } from '@/components/ui/Button'
import { Input, Select, Label } from '@/components/ui/Input'
import { Dialog } from '@/components/ui/Dialog'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Avatar } from '@/components/ui/Misc'
import { fmtDate } from '@/lib/utils'

const EMPTY = { firstName: '', lastName: '', jobTitle: '', email: '', phone: '', companyId: '' }

export default function ContactsPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const { user } = useAuth()
  const canCreate = useCan('CONTACT_CREATE')
  const canDelete = useCan('CONTACT_DELETE')
  const [params] = useSearchParams()
  const [q, setQ] = useState('')
  const debounced = useDebounce(q)
  const [page, setPage] = useState(0)
  const [createOpen, setCreateOpen] = useState(params.get('new') === '1')
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState(EMPTY)

  const { data, isFetching } = useQuery({
    queryKey: ['contacts', page, debounced],
    queryFn: async () => (await api.get<PageResponse<ContactItem>>('/contacts', { params: { page, size: 25, q: debounced || undefined, sort: 'createdAt,desc' } })).data,
  })
  const { data: companies } = useQuery({ queryKey: ['companies-lite'], queryFn: async () => (await api.get<PageResponse<CompanyItem>>('/companies', { params: { size: 200 } })).data })

  const create = useMutation({
    mutationFn: async () => api.post('/contacts', { ...form, companyId: form.companyId || null, ownerId: user?.id }),
    onSuccess: () => { toast.push('success', 'Contact created'); setCreateOpen(false); setForm(EMPTY); qc.invalidateQueries({ queryKey: ['contacts'] }) },
    onError: (e) => toast.push('error', 'Could not create contact', apiError(e).message),
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/contacts/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['contacts'] }); toast.push('success', 'Contact deleted') },
  })

  const columns: Column<ContactItem>[] = [
    { key: 'name', header: 'Contact', render: (c) => (
      <div className="flex items-center gap-2.5">
        <Avatar name={c.displayName} />
        <div>
          <p className="font-medium text-slate-800">{c.displayName}</p>
          <p className="text-xs text-slate-500">{c.jobTitle ?? ''}</p>
        </div>
      </div>
    ) },
    { key: 'company', header: 'Company', render: (c) => <span className="text-slate-600">{c.companyName ?? '—'}</span> },
    { key: 'email', header: 'Email', render: (c) => <span className="text-slate-600">{c.email ?? '—'}</span> },
    { key: 'phone', header: 'Phone', render: (c) => <span className="text-slate-600">{c.phone ?? '—'}</span> },
    { key: 'owner', header: 'Owner', render: (c) => <span className="text-slate-600">{c.ownerName ?? '—'}</span> },
    { key: 'created', header: 'Created', render: (c) => <span className="text-slate-500">{fmtDate(c.createdAt)}</span> },
    {
      key: 'actions', header: '', render: (c) => canDelete ? (
        <div className="text-right">
          <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); setDeleteId(c.id) }} aria-label="Delete contact"><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button>
        </div>
      ) : <span />,
    },
  ]

  return (
    <div>
      <PageHeader title="Contacts" actions={canCreate ? <Button onClick={() => setCreateOpen(true)}><Plus className="h-4 w-4" /> New contact</Button> : undefined} />
      <div className="card mb-3 p-3">
        <Input value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} placeholder="Search by name, email, or company…" className="max-w-md" />
      </div>
      <div className="card">
        <DataTable data={data} loading={isFetching} columns={columns} onPageChange={setPage}
          empty={{ icon: <Contact2 className="h-6 w-6" />, title: 'No contacts', subtitle: 'People tied to companies and leads — created on conversion or manually.' }} />
      </div>

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} title="New contact">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <Label required>First name</Label>
            <Input value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} />
          </div>
          <div>
            <Label required>Last name</Label>
            <Input value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} />
          </div>
          <div>
            <Label>Job title</Label>
            <Input value={form.jobTitle} onChange={(e) => setForm({ ...form, jobTitle: e.target.value })} />
          </div>
          <div>
            <Label>Company</Label>
            <Select value={form.companyId} onChange={(e) => setForm({ ...form, companyId: e.target.value })}>
              <option value="">None</option>
              {companies?.content.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </Select>
          </div>
          <div>
            <Label>Email</Label>
            <Input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </div>
          <div>
            <Label>Phone</Label>
            <Input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button disabled={!form.firstName.trim() || !form.lastName.trim()} loading={create.isPending} onClick={() => create.mutate()}>Create contact</Button>
        </div>
      </Dialog>

      <ConfirmDialog open={!!deleteId} onClose={() => setDeleteId(null)} onConfirm={async () => { if (deleteId) await del.mutateAsync(deleteId) }} title="Delete contact?" message="The contact record is removed. Lead and deal history is unaffected." confirmLabel="Delete" danger />
    </div>
  )
}
