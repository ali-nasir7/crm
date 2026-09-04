import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2, UsersRound, X } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useCan } from '@/stores/auth'
import { useToast } from '@/components/ui/Toast'
import type { PageResponse, TeamItem, UserItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select, Label } from '@/components/ui/Input'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Avatar, PageLoader } from '@/components/ui/Misc'

export default function TeamsPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const canManage = useCan('TEAM_UPDATE')
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteTeam, setDeleteTeam] = useState<TeamItem | null>(null)
  const [memberTeam, setMemberTeam] = useState<TeamItem | null>(null)
  const [form, setForm] = useState({ name: '', description: '', managerId: '' })

  const { data: teams, isLoading } = useQuery({ queryKey: ['teams'], queryFn: async () => (await api.get<TeamItem[]>('/teams')).data })
  const { data: users } = useQuery({ queryKey: ['users-lite'], queryFn: async () => (await api.get<PageResponse<UserItem>>('/users', { params: { size: 200 } })).data })

  const create = useMutation({
    mutationFn: async () => api.post('/teams', { name: form.name, description: form.description || null, managerId: form.managerId || null }),
    onSuccess: () => { toast.push('success', 'Team created'); setCreateOpen(false); setForm({ name: '', description: '', managerId: '' }); qc.invalidateQueries({ queryKey: ['teams'] }) },
    onError: (e) => toast.push('error', 'Could not create team', apiError(e).message),
  })

  const addMember = useMutation({
    mutationFn: async (userId: string) => api.post(`/teams/${memberTeam!.id}/members`, { userId }),
    onSuccess: () => { toast.push('success', 'Member added'); qc.invalidateQueries({ queryKey: ['teams'] }) },
    onError: (e) => toast.push('error', 'Could not add member', apiError(e).message),
  })

  const removeMember = useMutation({
    mutationFn: async ({ teamId, userId }: { teamId: string; userId: string }) => api.delete(`/teams/${teamId}/members/${userId}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['teams'] }) },
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/teams/${id}`),
    onSuccess: () => { toast.push('success', 'Team deleted'); qc.invalidateQueries({ queryKey: ['teams'] }) },
  })

  if (isLoading) return <PageLoader />

  return (
    <div>
      <PageHeader
        title="Teams"
        subtitle="Team managers see all their members' leads; data scope TEAM."
        actions={canManage ? <Button onClick={() => setCreateOpen(true)}><Plus className="h-4 w-4" /> New team</Button> : undefined}
      />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 xl:grid-cols-3">
        {teams?.map((t) => (
          <Card key={t.id}>
            <CardHeader
              title={t.name}
              subtitle={t.description ?? undefined}
              action={canManage ? <Button variant="ghost" size="icon" onClick={() => setDeleteTeam(t)} aria-label="Delete team"><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button> : undefined}
            />
            <CardBody>
              <p className="text-xs text-slate-400">Manager: {t.managerName ?? '—'}</p>
              <div className="mt-2 space-y-1.5">
                {t.members.map((m) => (
                  <div key={m.id} className="flex items-center justify-between gap-2 rounded-lg bg-slate-50 px-2.5 py-1.5">
                    <div className="flex min-w-0 items-center gap-2">
                      <Avatar name={m.displayName} className="h-6 w-6 text-[10px]" />
                      <div className="min-w-0">
                        <p className="truncate text-sm text-slate-700">{m.displayName}</p>
                        <p className="truncate text-[11px] text-slate-400">{m.email}</p>
                      </div>
                    </div>
                    {canManage && (
                      <button onClick={() => removeMember.mutate({ teamId: t.id, userId: m.id })} className="text-slate-300 hover:text-red-500" aria-label="Remove member"><X className="h-3.5 w-3.5" /></button>
                    )}
                  </div>
                ))}
              </div>
              {canManage && (
                <Button variant="secondary" size="sm" className="mt-3 w-full" onClick={() => setMemberTeam(t)}><UsersRound className="h-3.5 w-3.5" /> Add member</Button>
              )}
            </CardBody>
          </Card>
        ))}
        {teams && teams.length === 0 && <div className="card col-span-full p-10 text-center text-sm text-slate-400">No teams yet.</div>}
      </div>

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} title="New team">
        <div className="space-y-3">
          <div>
            <Label required>Name</Label>
            <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="e.g. UAE Outbound" />
          </div>
          <div>
            <Label>Description</Label>
            <Input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
          <div>
            <Label>Team manager</Label>
            <Select value={form.managerId} onChange={(e) => setForm({ ...form, managerId: e.target.value })}>
              <option value="">None</option>
              {users?.content.map((u) => <option key={u.id} value={u.id}>{u.displayName}</option>)}
            </Select>
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button disabled={!form.name.trim()} loading={create.isPending} onClick={() => create.mutate()}>Create team</Button>
        </div>
      </Dialog>

      <Dialog open={!!memberTeam} onClose={() => setMemberTeam(null)} title={`Add member to ${memberTeam?.name}`}>
        <Select defaultValue="" onChange={(e) => { if (e.target.value) { addMember.mutate(e.target.value); setMemberTeam(null) } }}>
          <option value="" disabled>Select user…</option>
          {users?.content.filter((u) => !memberTeam?.members.some((m) => m.id === u.id)).map((u) => <option key={u.id} value={u.id}>{u.displayName} ({u.email})</option>)}
        </Select>
      </Dialog>

      <ConfirmDialog open={!!deleteTeam} onClose={() => setDeleteTeam(null)} onConfirm={async () => { if (deleteTeam) await del.mutateAsync(deleteTeam.id) }} title={`Delete ${deleteTeam?.name}?`} message="Members keep their records. Team-scoped visibility stops immediately." confirmLabel="Delete team" danger />
    </div>
  )
}
