import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { KanbanSquare, MoreHorizontal } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import { useCan } from '@/stores/auth'
import type { LeadItem, PageResponse, PipelineItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { Select } from '@/components/ui/Input'
import { PageLoader } from '@/components/ui/Misc'
import { ScoreBadge } from '@/components/ui/Badge'
import { cn, fmtAgo } from '@/lib/utils'

const STAGE_TYPES: Record<string, { tint: string; dot: string }> = {
  OPEN: { tint: 'bg-slate-50', dot: 'bg-slate-400' },
  WON: { tint: 'bg-emerald-50/70', dot: 'bg-emerald-500' },
  LOST: { tint: 'bg-red-50/70', dot: 'bg-red-400' },
}

export default function PipelinePage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const canUpdate = useCan('LEAD_UPDATE')
  const [pipelineId, setPipelineId] = useState('')
  const [dragging, setDragging] = useState<LeadItem | null>(null)
  const [dragOverStage, setDragOverStage] = useState<string | null>(null)

  const { data: pipelines } = useQuery({ queryKey: ['pipelines'], queryFn: async () => (await api.get<PipelineItem[]>('/pipelines')).data })
  const active = pipelines?.find((p) => p.id === pipelineId) ?? pipelines?.find((p) => p.isDefault) ?? pipelines?.[0]

  const { data: leads, isFetching } = useQuery({
    queryKey: ['leads', 'pipeline', active?.id],
    queryFn: async () => (await api.get<PageResponse<LeadItem>>('/leads', { params: { pipelineId: active?.id, size: 500, sort: 'score,desc' } })).data,
    enabled: !!active,
  })

  const byStage = useMemo(() => {
    const map = new Map<string, LeadItem[]>()
    active?.stages.forEach((s) => map.set(s.id, []))
    leads?.content.forEach((l) => { if (l.stageId && map.has(l.stageId)) map.get(l.stageId)!.push(l) })
    return map
  }, [leads, active])

  const move = useMutation({
    mutationFn: async ({ leadId, stageId }: { leadId: string; stageId: string }) => api.post(`/leads/${leadId}/stage`, { stageId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['leads'] })
      qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (e) => { toast.push('error', 'Could not move lead', apiError(e).message); qc.invalidateQueries({ queryKey: ['leads'] }) },
  })

  if (!pipelines) return <PageLoader />

  return (
    <div>
      <PageHeader
        title="Pipeline"
        subtitle="Drag leads between stages. Every move is recorded in the lead timeline and stage history."
        actions={
          <Select value={active?.id ?? ''} onChange={(e) => setPipelineId(e.target.value)} className="w-56">
            {(pipelines ?? []).map((p) => <option key={p.id} value={p.id}>{p.name}{p.isDefault ? ' (default)' : ''}</option>)}
          </Select>
        }
      />

      {!active ? (
        <div className="card p-10 text-center text-sm text-slate-400">No pipelines configured yet.</div>
      ) : (
        <div className="flex gap-3 overflow-x-auto pb-4">
          {active.stages.map((stage) => {
            const items = byStage.get(stage.id) ?? []
            const type = STAGE_TYPES[stage.type] ?? STAGE_TYPES.OPEN
            return (
              <div
                key={stage.id}
                className={cn('flex w-64 shrink-0 flex-col rounded-xl border border-slate-200', type.tint, dragOverStage === stage.id && 'border-blue-400 ring-2 ring-blue-100')}
                onDragOver={(e) => { if (canUpdate) { e.preventDefault(); setDragOverStage(stage.id) } }}
                onDragLeave={() => setDragOverStage(null)}
                onDrop={(e) => {
                  e.preventDefault()
                  setDragOverStage(null)
                  if (dragging && canUpdate && dragging.stageId !== stage.id) move.mutate({ leadId: dragging.id, stageId: stage.id })
                  setDragging(null)
                }}
              >
                <div className="flex items-center justify-between px-3 py-2.5">
                  <div className="flex items-center gap-2">
                    <span className={cn('h-2 w-2 rounded-full', type.dot)} />
                    <span className="text-sm font-semibold text-slate-700">{stage.name}</span>
                    <span className="rounded-full bg-white px-1.5 text-xs font-medium text-slate-500 ring-1 ring-slate-200">{items.length}</span>
                  </div>
                  <span className="text-[11px] text-slate-400">{stage.probability}%</span>
                </div>
                <div className="min-h-24 space-y-2 px-2 pb-2">
                  {isFetching && items.length === 0 && <div className="rounded-lg bg-white/60 p-3 text-center text-xs text-slate-400">…</div>}
                  {items.map((l) => (
                    <div
                      key={l.id}
                      draggable={canUpdate}
                      onDragStart={() => setDragging(l)}
                      onClick={() => navigate(`/leads/${l.id}`)}
                      className={cn('cursor-pointer rounded-lg border border-slate-200 bg-white p-2.5 shadow-sm transition-shadow hover:shadow-md', canUpdate && 'active:cursor-grabbing', dragging?.id === l.id && 'opacity-40')}
                    >
                      <div className="flex items-start justify-between gap-1">
                        <p className="min-w-0 truncate text-sm font-medium text-slate-800">{l.businessName}</p>
                        <MoreHorizontal className="h-3.5 w-3.5 shrink-0 text-slate-300" />
                      </div>
                      <p className="mt-0.5 truncate text-xs text-slate-500">{l.contactName ?? l.email ?? l.city ?? ''}</p>
                      <div className="mt-1.5 flex items-center justify-between gap-1">
                        <ScoreBadge score={l.score} category={l.scoreCategory} />
                        <span className="text-[10px] text-slate-400">{l.lastContactedAt ? fmtAgo(l.lastContactedAt) : 'never'}</span>
                      </div>
                    </div>
                  ))}
                  {!isFetching && items.length === 0 && (
                    <div className="flex items-center justify-center rounded-lg border border-dashed border-slate-200 py-6 text-xs text-slate-300">
                      <KanbanSquare className="mr-1 h-3.5 w-3.5" /> Drop here
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
