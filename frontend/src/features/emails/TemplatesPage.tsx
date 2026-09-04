import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Copy, FileText, Plus, Trash2 } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import type { PageResponse, TemplateItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable, type Column } from '@/components/shared/DataTable'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Textarea, Label } from '@/components/ui/Input'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { fmtDate } from '@/lib/utils'

export default function TemplatesPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const [page, setPage] = useState(0)
  const [editOpen, setEditOpen] = useState(false)
  const [editing, setEditing] = useState<TemplateItem | null>(null)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState({ name: '', subject: '', bodyHtml: '', category: '' })

  const { data, isFetching } = useQuery({
    queryKey: ['email-templates', page],
    queryFn: async () => (await api.get<PageResponse<TemplateItem>>('/email-templates', { params: { page, size: 20, sort: 'createdAt,desc' } })).data,
  })

  const openNew = () => { setEditing(null); setForm({ name: '', subject: '', bodyHtml: '', category: '' }); setEditOpen(true) }
  const openEdit = (t: TemplateItem) => { setEditing(t); setForm({ name: t.name, subject: t.subject, bodyHtml: t.bodyHtml ?? '', category: t.category ?? '' }); setEditOpen(true) }

  const save = useMutation({
    mutationFn: async () => {
      if (editing) return api.put(`/email-templates/${editing.id}`, form)
      return api.post('/email-templates', form)
    },
    onSuccess: () => { toast.push('success', 'Template saved'); setEditOpen(false); qc.invalidateQueries({ queryKey: ['email-templates'] }) },
    onError: (e) => toast.push('error', 'Save failed', apiError(e).message),
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/email-templates/${id}`),
    onSuccess: () => { toast.push('success', 'Template deleted'); qc.invalidateQueries({ queryKey: ['email-templates'] }) },
    onError: (e) => toast.push('error', 'Delete failed', apiError(e).message),
  })

  const duplicate = useMutation({
    mutationFn: async (id: string) => api.post(`/email-templates/${id}/duplicate`),
    onSuccess: () => { toast.push('success', 'Template duplicated'); qc.invalidateQueries({ queryKey: ['email-templates'] }) },
  })

  const columns: Column<TemplateItem>[] = [
    { key: 'name', header: 'Name', render: (t) => <button className="font-medium text-blue-600 hover:underline" onClick={() => openEdit(t)}>{t.name}</button> },
    { key: 'subject', header: 'Subject', render: (t) => <span className="text-slate-600">{t.subject}</span> },
    { key: 'category', header: 'Category', render: (t) => (t.category ? <Badge tone="gray">{t.category}</Badge> : '—') },
    { key: 'vars', header: 'Variables', render: (t) => <span className="text-xs text-slate-400">{t.variables.slice(0, 4).map((v) => `{{${v}}}`).join(' ')}{t.variables.length > 4 ? '…' : ''}</span> },
    { key: 'active', header: 'Active', render: (t) => <Badge tone={t.active ? 'green' : 'gray'}>{t.active ? 'Yes' : 'No'}</Badge> },
    { key: 'created', header: 'Created', render: (t) => <span className="text-slate-500">{fmtDate(t.createdAt)}</span> },
    {
      key: 'actions', header: '', render: (t) => (
        <div className="flex justify-end gap-1">
          <Button variant="ghost" size="icon" title="Duplicate" onClick={(e) => { e.stopPropagation(); duplicate.mutate(t.id) }}><Copy className="h-3.5 w-3.5" /></Button>
          <Button variant="ghost" size="icon" title="Delete" onClick={(e) => { e.stopPropagation(); setDeleteId(t.id) }}><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button>
        </div>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="Email templates"
        subtitle="Reusable {{variable}} templates for one-off sends and campaign sequences."
        actions={<Button onClick={openNew}><Plus className="h-4 w-4" /> New template</Button>}
      />
      <div className="card">
        <DataTable data={data} loading={isFetching} columns={columns} onPageChange={setPage}
          empty={{ icon: <FileText className="h-6 w-6" />, title: 'No templates', subtitle: 'Create a template with variables like {{first_name}} and {{business_name}}.' } }/>
      </div>

      <Dialog open={editOpen} onClose={() => setEditOpen(false)} title={editing ? 'Edit template' : 'New template'} wide>
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label required>Name</Label>
              <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </div>
            <div>
              <Label>Category</Label>
              <Input value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} placeholder="e.g. intro / follow-up" />
            </div>
          </div>
          <div>
            <Label required>Subject</Label>
            <Input value={form.subject} onChange={(e) => setForm({ ...form, subject: e.target.value })} placeholder="Quick question for {{business_name}}" />
          </div>
          <div>
            <Label>Body (HTML)</Label>
            <Textarea value={form.bodyHtml} onChange={(e) => setForm({ ...form, bodyHtml: e.target.value })} className="min-h-48 font-mono text-xs" placeholder={'<p>Hi {{first_name}},</p><p>…</p>'} />
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setEditOpen(false)}>Cancel</Button>
          <Button disabled={!form.name.trim() || !form.subject.trim()} loading={save.isPending} onClick={() => save.mutate()}>Save template</Button>
        </div>
      </Dialog>

      <ConfirmDialog
        open={!!deleteId}
        onClose={() => setDeleteId(null)}
        onConfirm={async () => { if (deleteId) await del.mutateAsync(deleteId) }}
        title="Delete template?"
        message="Campaign steps referencing this template will fail to render. Consider deactivating it instead."
        confirmLabel="Delete"
        danger
      />
    </div>
  )
}
