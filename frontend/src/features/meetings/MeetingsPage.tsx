import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { CalendarDays, ExternalLink, Plus, Trash2 } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import type { MeetingItem, PageResponse } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable, type Column } from '@/components/shared/DataTable'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select, Textarea, Label } from '@/components/ui/Input'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { fmtDateTime } from '@/lib/utils'

export default function MeetingsPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const [page, setPage] = useState(0)
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState({ title: '', startAt: '', durationMinutes: '30', meetingLink: '', location: '', notes: '' })

  const { data, isFetching } = useQuery({
    queryKey: ['meetings', page],
    queryFn: async () => (await api.get<PageResponse<MeetingItem>>('/meetings', { params: { page, size: 25, sort: 'startAt,desc' } })).data,
  })

  const create = useMutation({
    mutationFn: async () =>
      api.post('/meetings', {
        title: form.title,
        startAt: new Date(form.startAt).toISOString(),
        durationMinutes: parseInt(form.durationMinutes, 10),
        meetingLink: form.meetingLink || null,
        location: form.location || null,
        notes: form.notes || null,
      }),
    onSuccess: () => { toast.push('success', 'Meeting scheduled'); setCreateOpen(false); qc.invalidateQueries({ queryKey: ['meetings'] }) },
    onError: (e) => toast.push('error', 'Could not schedule meeting', apiError(e).message),
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/meetings/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['meetings'] }) },
  })

  const columns: Column<MeetingItem>[] = [
    { key: 'title', header: 'Meeting', render: (m) => <span className="font-medium text-slate-800">{m.title}</span> },
    { key: 'lead', header: 'Lead', render: (m) => <span className="text-slate-600">{m.businessName ?? '—'}</span> },
    { key: 'when', header: 'When', render: (m) => <span className="text-slate-600">{fmtDateTime(m.startAt)} · {m.durationMinutes}m</span> },
    { key: 'owner', header: 'Owner', render: (m) => <span className="text-slate-600">{m.ownerName}</span> },
    { key: 'status', header: 'Status', render: (m) => <Badge tone={m.status === 'COMPLETED' ? 'green' : m.status === 'CANCELLED' ? 'gray' : 'blue'}>{m.status}</Badge> },
    { key: 'link', header: '', render: (m) => m.meetingLink ? <a href={m.meetingLink} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-xs font-medium text-blue-600 hover:underline" onClick={(e) => e.stopPropagation()}><ExternalLink className="h-3 w-3" /> Join</a> : <span /> },
    {
      key: 'actions', header: '', render: (m) => (
        <div className="text-right">
          <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); setDeleteId(m.id) }} aria-label="Delete meeting"><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button>
        </div>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="Meetings"
        subtitle="Calendar-ready scheduling. Sync to Google/Outlook is planned — see TODO in docs."
        actions={<Button onClick={() => setCreateOpen(true)}><Plus className="h-4 w-4" /> Schedule meeting</Button>}
      />
      <div className="card">
        <DataTable data={data} loading={isFetching} columns={columns} onPageChange={setPage} onRowClick={(m) => m.leadId && navigate(`/leads/${m.leadId}`)}
          empty={{ icon: <CalendarDays className="h-6 w-6" />, title: 'No meetings scheduled' }} />
      </div>

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} title="Schedule meeting">
        <div className="space-y-3">
          <div>
            <Label required>Title</Label>
            <Input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label required>Start</Label>
              <Input type="datetime-local" value={form.startAt} onChange={(e) => setForm({ ...form, startAt: e.target.value })} />
            </div>
            <div>
              <Label>Duration</Label>
              <Select value={form.durationMinutes} onChange={(e) => setForm({ ...form, durationMinutes: e.target.value })}>
                {['15', '30', '45', '60', '90', '120'].map((d) => <option key={d} value={d}>{d} min</option>)}
              </Select>
            </div>
          </div>
          <div>
            <Label>Meeting link</Label>
            <Input value={form.meetingLink} onChange={(e) => setForm({ ...form, meetingLink: e.target.value })} placeholder="https://meet…" />
          </div>
          <div>
            <Label>Location</Label>
            <Input value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} />
          </div>
          <div>
            <Label>Agenda / notes</Label>
            <Textarea value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button disabled={!form.title.trim() || !form.startAt} loading={create.isPending} onClick={() => create.mutate()}>Schedule</Button>
        </div>
      </Dialog>

      <ConfirmDialog open={!!deleteId} onClose={() => setDeleteId(null)} onConfirm={async () => { if (deleteId) await del.mutateAsync(deleteId) }} title="Delete meeting?" message="This removes the meeting from the CRM." confirmLabel="Delete" danger />
    </div>
  )
}
