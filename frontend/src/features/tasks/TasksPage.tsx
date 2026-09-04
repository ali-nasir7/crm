import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckSquare, Plus, Trash2 } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useAuth } from '@/stores/auth'
import { useToast } from '@/components/ui/Toast'
import type { PageResponse, TaskItem, UserItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable, type Column } from '@/components/shared/DataTable'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Checkbox } from '@/components/ui/Checkbox'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select, Textarea, Label } from '@/components/ui/Input'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { cn, fmtDateTime } from '@/lib/utils'

const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT']
const TYPES = ['FOLLOW_UP', 'CALL', 'EMAIL', 'MEETING', 'REMINDER', 'OTHER']

export default function TasksPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const { user } = useAuth()
  const [params] = useSearchParams()
  const [page, setPage] = useState(0)
  const [assignee, setAssignee] = useState(params.get('mine') === '1' ? (user?.id ?? '') : '')
  const [status, setStatus] = useState('OPEN')
  const [createOpen, setCreateOpen] = useState(params.get('new') === '1')
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState({ title: '', description: '', dueAt: '', priority: 'MEDIUM', taskType: 'FOLLOW_UP' })

  const { data, isFetching } = useQuery({
    queryKey: ['tasks', page, assignee, status],
    queryFn: async () => (await api.get<PageResponse<TaskItem>>('/tasks', { params: { page, size: 25, assignee: assignee || undefined, status: status || undefined } })).data,
  })

  const { data: users } = useQuery({ queryKey: ['users-lite'], queryFn: async () => (await api.get<PageResponse<UserItem>>('/users', { params: { size: 200 } })).data })

  const complete = useMutation({
    mutationFn: async (id: string) => api.post(`/tasks/${id}/complete`, {}),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['tasks'] }); toast.push('success', 'Task completed') },
    onError: (e) => toast.push('error', 'Failed', apiError(e).message),
  })

  const create = useMutation({
    mutationFn: async () => api.post('/tasks', { ...form, dueAt: form.dueAt ? new Date(form.dueAt).toISOString() : null, description: form.description || null }),
    onSuccess: () => { toast.push('success', 'Task created'); setCreateOpen(false); setForm({ title: '', description: '', dueAt: '', priority: 'MEDIUM', taskType: 'FOLLOW_UP' }); qc.invalidateQueries({ queryKey: ['tasks'] }) },
    onError: (e) => toast.push('error', 'Could not create task', apiError(e).message),
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/tasks/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['tasks'] }) },
  })

  const columns: Column<TaskItem>[] = [
    {
      key: 'done', header: '', render: (t) =>
        t.status === 'COMPLETED'
          ? <Checkbox checked disabled />
          : <Checkbox checked={false} onChange={() => complete.mutate(t.id)} onClick={(e) => e.stopPropagation()} aria-label="Complete task" />,
      className: 'w-8', headerClassName: 'w-8',
    },
    {
      key: 'title', header: 'Task', render: (t) => (
        <div>
          <p className={cn('font-medium', t.status === 'COMPLETED' ? 'text-slate-400 line-through' : 'text-slate-800')}>{t.title}</p>
          {t.businessName && t.leadId && (
            <button onClick={(e) => { e.stopPropagation(); navigate(`/leads/${t.leadId}`) }} className="text-xs text-blue-600 hover:underline">{t.businessName}</button>
          )}
        </div>
      ),
    },
    { key: 'type', header: 'Type', render: (t) => <Badge tone="gray">{t.taskType}</Badge> },
    {
      key: 'due', header: 'Due', render: (t) => {
        const overdue = t.status === 'OPEN' && new Date(t.dueAt) < new Date()
        return <span className={cn('text-xs', overdue ? 'font-semibold text-red-500' : 'text-slate-500')}>{fmtDateTime(t.dueAt)}{overdue ? ' · overdue' : ''}</span>
      },
    },
    { key: 'priority', header: 'Priority', render: (t) => <Badge tone={t.priority === 'URGENT' ? 'red' : t.priority === 'HIGH' ? 'yellow' : 'gray'}>{t.priority}</Badge> },
    { key: 'assignee', header: 'Assignee', render: (t) => <span className="text-slate-600">{t.assignedUserName}</span> },
    { key: 'status', header: 'Status', render: (t) => <Badge tone={t.status === 'COMPLETED' ? 'green' : t.status === 'CANCELLED' ? 'gray' : 'blue'}>{t.status}</Badge> },
    {
      key: 'actions', header: '', render: (t) => (
        <div className="text-right">
          <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); setDeleteId(t.id) }} aria-label="Delete task"><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button>
        </div>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="Tasks & follow-ups"
        actions={<Button onClick={() => setCreateOpen(true)}><Plus className="h-4 w-4" /> New task</Button>}
      />
      <div className="card mb-3 flex flex-wrap gap-2 p-3">
        <Select value={assignee} onChange={(e) => { setAssignee(e.target.value); setPage(0) }} className="w-48">
          <option value="">Everyone</option>
          <option value={user?.id}>Assigned to me</option>
          {users?.content.map((u) => <option key={u.id} value={u.id}>{u.displayName}</option>)}
        </Select>
        <Select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }} className="w-40">
          <option value="">All statuses</option>
          <option value="OPEN">Open</option>
          <option value="COMPLETED">Completed</option>
          <option value="CANCELLED">Cancelled</option>
        </Select>
      </div>
      <div className="card">
        <DataTable data={data} loading={isFetching} columns={columns} onPageChange={setPage} onRowClick={(t) => t.leadId && navigate(`/leads/${t.leadId}`)}
          empty={{ icon: <CheckSquare className="h-6 w-6" />, title: 'No tasks', subtitle: 'Create follow-ups so no lead goes cold.' }} />
      </div>

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} title="New task">
        <div className="space-y-3">
          <div>
            <Label required>Title</Label>
            <Input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="e.g. Follow up on proposal" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>Due</Label>
              <Input type="datetime-local" value={form.dueAt} onChange={(e) => setForm({ ...form, dueAt: e.target.value })} />
            </div>
            <div>
              <Label>Priority</Label>
              <Select value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value })}>
                {PRIORITIES.map((p) => <option key={p}>{p}</option>)}
              </Select>
            </div>
            <div>
              <Label>Type</Label>
              <Select value={form.taskType} onChange={(e) => setForm({ ...form, taskType: e.target.value })}>
                {TYPES.map((t) => <option key={t}>{t}</option>)}
              </Select>
            </div>
          </div>
          <div>
            <Label>Description</Label>
            <Textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button disabled={!form.title.trim()} loading={create.isPending} onClick={() => create.mutate()}>Create task</Button>
        </div>
      </Dialog>

      <ConfirmDialog open={!!deleteId} onClose={() => setDeleteId(null)} onConfirm={async () => { if (deleteId) await del.mutateAsync(deleteId) }} title="Delete task?" message="This removes the task permanently." confirmLabel="Delete" danger />
    </div>
  )
}
