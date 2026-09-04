import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ScrollText } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import type { RoleItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { PageLoader } from '@/components/ui/Misc'
import { Badge } from '@/components/ui/Badge'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'

const SCOPE_TONES: Record<string, 'purple' | 'blue' | 'green' | 'gray'> = { ALL: 'purple', ORG: 'blue', TEAM: 'green', OWN: 'gray' }

export default function RolesPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const [editing, setEditing] = useState<RoleItem | null>(null)

  const { data: roles, isLoading } = useQuery({ queryKey: ['roles'], queryFn: async () => (await api.get<RoleItem[]>('/roles')).data })
  const { data: permissions } = useQuery({ queryKey: ['permissions'], queryFn: async () => (await api.get<string[]>('/permissions')).data })

  const update = useMutation({
    mutationFn: async () => api.put(`/roles/${editing!.id}`, { name: editing!.name, description: editing!.description, permissionKeys: editing!.permissionKeys }),
    onSuccess: () => { toast.push('success', 'Role updated', 'Users see new permissions on next sign-in.'); setEditing(null); qc.invalidateQueries({ queryKey: ['roles'] }) },
    onError: (e) => toast.push('error', 'Update failed', apiError(e).message),
  })

  if (isLoading) return <PageLoader />

  return (
    <div>
      <PageHeader title="Roles & permissions" subtitle="5 system roles with granular permissions and data visibility. System roles can be tuned but not deleted." />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {roles?.map((r) => (
          <Card key={r.id}>
            <CardHeader
              title={<span className="flex items-center gap-2">{r.name} <Badge tone={SCOPE_TONES[r.dataScope] ?? 'gray'}>{r.dataScope}</Badge>{r.system && <Badge tone="gray">System</Badge>}</span>}
              subtitle={`${r.description ?? ''} · ${r.userCount} user(s)`}
              action={<Button variant="secondary" size="sm" onClick={() => setEditing({ ...r, permissionKeys: [...r.permissionKeys] })}>Edit</Button>}
            />
            <CardBody>
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">{r.permissionKeys.length} permissions</p>
              <div className="mt-1.5 flex max-h-32 flex-wrap gap-1 overflow-y-auto">
                {r.permissionKeys.slice(0, 14).map((p) => <span key={p} className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[10px] text-slate-500">{p}</span>)}
                {r.permissionKeys.length > 14 && <span className="text-[10px] text-slate-400">+{r.permissionKeys.length - 14} more</span>}
              </div>
            </CardBody>
          </Card>
        ))}
      </div>

      <Dialog open={!!editing} onClose={() => setEditing(null)} title={`Edit role — ${editing?.name ?? ''}`} description="Uncheck a permission to remove it from this role." wide>
        {editing && permissions && (
          <div className="max-h-96 overflow-y-auto rounded-lg border border-slate-200 p-3">
            <div className="grid grid-cols-2 gap-x-4 sm:grid-cols-3">
              {permissions.map((p) => (
                <label key={p} className="flex cursor-pointer items-center gap-2 py-0.5 text-xs text-slate-600">
                  <input
                    type="checkbox"
                    className="h-3.5 w-3.5 rounded border-slate-300 text-blue-600"
                    checked={editing.permissionKeys.includes(p)}
                    onChange={(e) => setEditing({ ...editing, permissionKeys: e.target.checked ? [...editing.permissionKeys, p] : editing.permissionKeys.filter((k) => k !== p) })}
                  />
                  <span className="font-mono">{p}</span>
                </label>
              ))}
            </div>
          </div>
        )}
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setEditing(null)}>Cancel</Button>
          <Button loading={update.isPending} onClick={() => update.mutate()}>Save role</Button>
        </div>
      </Dialog>

      <p className="mt-4 flex items-center gap-2 text-xs text-slate-400"><ScrollText className="h-3.5 w-3.5" /> Data scope legend: ALL/ORG = entire organization · TEAM = own teams · OWN = own records only.</p>
    </div>
  )
}
