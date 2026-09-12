import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, Plus, Trash2 } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import type { PipelineItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select, Label } from '@/components/ui/Input'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { PageLoader } from '@/components/ui/Misc'

export default function PipelinesPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const [createOpen, setCreateOpen] = useState(false)
  const [stageFor, setStageFor] = useState<PipelineItem | null>(null)
  const [deletePipeline, setDeletePipeline] = useState<PipelineItem | null>(null)
  const [form, setForm] = useState({ name: '', description: '' })
  const [stageForm, setStageForm] = useState({ name: '', type: 'OPEN', probability: '10' })

  const { data: pipelines, isLoading } = useQuery({ queryKey: ['pipelines'], queryFn: async () => (await api.get<PipelineItem[]>('/pipelines')).data })

  const create = useMutation({
    mutationFn: async () => api.post('/pipelines', { name: form.name, description: form.description || null }),
    onSuccess: () => { toast.push('success', 'Pipeline created'); setCreateOpen(false); setForm({ name: '', description: '' }); qc.invalidateQueries({ queryKey: ['pipelines'] }) },
    onError: (e) => toast.push('error', 'Could not create pipeline', apiError(e).message),
  })

  const addStage = useMutation({
    mutationFn: async () => api.post(`/pipelines/${stageFor!.id}/stages`, { name: stageForm.name, type: stageForm.type, probability: parseInt(stageForm.probability, 10) || 0 }),
    onSuccess: () => { toast.push('success', 'Stage added'); setStageFor(null); setStageForm({ name: '', type: 'OPEN', probability: '10' }); qc.invalidateQueries({ queryKey: ['pipelines'] }) },
    onError: (e) => toast.push('error', 'Could not add stage', apiError(e).message),
  })

  const moveStage = useMutation({
    mutationFn: async ({ pipeline, stageId, dir }: { pipeline: PipelineItem; stageId: string; dir: -1 | 1 }) => {
      const order = pipeline.stages.map((s) => s.id)
      const i = order.indexOf(stageId)
      const j = i + dir
      if (j < 0 || j >= order.length) return Promise.resolve()
      ;[order[i], order[j]] = [order[j], order[i]]
      return api.put(`/pipelines/${pipeline.id}/stages/reorder`, { stageIds: order })
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['pipelines'] }),
    onError: (e) => toast.push('error', 'Reorder failed', apiError(e).message),
  })

  const delStage = useMutation({
    mutationFn: async ({ pipelineId, stageId }: { pipelineId: string; stageId: string }) => api.delete(`/pipelines/${pipelineId}/stages/${stageId}`),
    onSuccess: () => { toast.push('success', 'Stage removed'); qc.invalidateQueries({ queryKey: ['pipelines'] }) },
    onError: (e) => toast.push('error', 'Could not remove stage', apiError(e).message),
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/pipelines/${id}`),
    onSuccess: () => { toast.push('success', 'Pipeline deleted'); qc.invalidateQueries({ queryKey: ['pipelines'] }) },
  })

  if (isLoading) return <PageLoader />

  return (
    <div>
      <PageHeader
        title="Pipelines & stages"
        subtitle="Sales processes differ per market — pipelines are fully configurable. The default pipeline drives kanban and conversion."
        actions={<Button onClick={() => setCreateOpen(true)}><Plus className="h-4 w-4" /> New pipeline</Button>}
      />
      <div className="space-y-4">
        {pipelines?.map((p) => (
          <Card key={p.id}>
            <CardHeader
              title={<span className="flex items-center gap-2">{p.name} {p.isDefault && <Badge tone="green">Default</Badge>}</span>}
              subtitle={p.description ?? undefined}
              action={<Button variant="ghost" size="icon" onClick={() => setDeletePipeline(p)} aria-label="Delete pipeline"><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button>}
            />
            <CardBody>
              <div className="flex flex-wrap items-center gap-1.5">
                {p.stages.map((s, i) => (
                  <span key={s.id} className="flex items-center gap-1.5">
                    <span className={`inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs ring-1 ${s.type === 'WON' ? 'bg-emerald-50 text-emerald-700 ring-emerald-200' : s.type === 'LOST' ? 'bg-red-50 text-red-600 ring-red-200' : 'bg-slate-50 text-slate-700 ring-slate-200'}`}>
                      {s.name}
                      <span className="text-[10px] text-slate-400">{s.probability}%</span>
                      <button onClick={() => moveStage.mutate({ pipeline: p, stageId: s.id, dir: -1 })} className="text-slate-300 hover:text-slate-600" aria-label="Move left"><ArrowLeft className="h-3 w-3" /></button>
                      <button onClick={() => moveStage.mutate({ pipeline: p, stageId: s.id, dir: 1 })} className="text-slate-300 hover:text-slate-600" aria-label="Move right"><ArrowRight className="h-3 w-3" /></button>
                      {p.stages.length > 2 && <button onClick={() => delStage.mutate({ pipelineId: p.id, stageId: s.id })} className="text-slate-300 hover:text-red-500" aria-label="Remove stage"><Trash2 className="h-3 w-3" /></button>}
                    </span>
                    {i < p.stages.length - 1 && <span className="text-slate-300">→</span>}
                  </span>
                ))}
                <button onClick={() => setStageFor(p)} className="inline-flex items-center gap-1 rounded-lg border border-dashed border-slate-300 px-2.5 py-1.5 text-xs text-slate-500 hover:border-blue-400 hover:text-blue-600">
                  <Plus className="h-3 w-3" /> Stage
                </button>
              </div>
            </CardBody>
          </Card>
        ))}
      </div>

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} title="New pipeline">
        <div className="space-y-3">
          <div>
            <Label required>Name</Label>
            <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="e.g. Equipment Sales — KSA" />
          </div>
          <div>
            <Label>Description</Label>
            <Input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
          <p className="text-xs text-slate-400">A starter stage set (New → Qualified → Proposal → Won/Lost) is created automatically; adjust it after.</p>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button disabled={!form.name.trim()} loading={create.isPending} onClick={() => create.mutate()}>Create pipeline</Button>
        </div>
      </Dialog>

      <Dialog open={!!stageFor} onClose={() => setStageFor(null)} title={`Add stage to ${stageFor?.name}`}>
        <div className="grid grid-cols-2 gap-3">
          <div className="col-span-2">
            <Label required>Stage name</Label>
            <Input value={stageForm.name} onChange={(e) => setStageForm({ ...stageForm, name: e.target.value })} placeholder="e.g. Contract review" />
          </div>
          <div>
            <Label>Type</Label>
            <Select value={stageForm.type} onChange={(e) => setStageForm({ ...stageForm, type: e.target.value })}>
              <option value="OPEN">Open</option>
              <option value="WON">Won</option>
              <option value="LOST">Lost</option>
            </Select>
          </div>
          <div>
            <Label>Win probability %</Label>
            <Input type="number" min="0" max="100" value={stageForm.probability} onChange={(e) => setStageForm({ ...stageForm, probability: e.target.value })} />
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setStageFor(null)}>Cancel</Button>
          <Button disabled={!stageForm.name.trim()} loading={addStage.isPending} onClick={() => addStage.mutate()}>Add stage</Button>
        </div>
      </Dialog>

      <ConfirmDialog open={!!deletePipeline} onClose={() => setDeletePipeline(null)} onConfirm={async () => { if (deletePipeline) await del.mutateAsync(deletePipeline.id) }} title={`Delete ${deletePipeline?.name}?`} message="Leads keep their history; their stage reference is cleared. The default pipeline cannot be deleted." confirmLabel="Delete pipeline" danger />
    </div>
  )
}
