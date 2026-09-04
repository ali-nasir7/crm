import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ScrollText } from 'lucide-react'
import { api } from '@/api/client'
import type { AuditItem, PageResponse } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable, type Column } from '@/components/shared/DataTable'
import { Badge } from '@/components/ui/Badge'
import { Input, Select } from '@/components/ui/Input'
import { fmtDateTime } from '@/lib/utils'

const ENTITY_TYPES = ['LEAD', 'USER', 'ROLE', 'TEAM', 'PIPELINE', 'DEAL', 'CLIENT', 'PROPOSAL', 'CAMPAIGN', 'EMAIL', 'IMPORT', 'DOCUMENT', 'ORGANIZATION', 'SETTINGS']

export default function AuditPage() {
  const [page, setPage] = useState(0)
  const [entityType, setEntityType] = useState('')
  const [action, setAction] = useState('')

  const { data, isFetching } = useQuery({
    queryKey: ['audit', page, entityType, action],
    queryFn: async () => (await api.get<PageResponse<AuditItem>>('/audit-logs', { params: { page, size: 30, entityType: entityType || undefined, action: action || undefined, sort: 'createdAt,desc' } })).data,
  })

  const columns: Column<AuditItem>[] = [
    { key: 'when', header: 'When', render: (a) => <span className="whitespace-nowrap text-slate-600">{fmtDateTime(a.createdAt)}</span> },
    { key: 'actor', header: 'Actor', render: (a) => <span className="font-medium text-slate-700">{a.actorEmail}</span> },
    { key: 'action', header: 'Action', render: (a) => <Badge tone={a.action.includes('DELETE') ? 'red' : a.action.includes('CREATE') ? 'green' : 'blue'}>{a.action}</Badge> },
    { key: 'entity', header: 'Entity', render: (a) => (
      <div>
        <p className="text-xs font-semibold text-slate-600">{a.entityType}</p>
        <p className="text-xs text-slate-400">{a.entityLabel || a.entityId.slice(0, 8)}</p>
      </div>
    ) },
    {
      key: 'changes', header: 'Changes', render: (a) => {
        const oldV = a.oldValues ?? {}
        const newV = a.newValues ?? {}
        const keys = [...new Set([...Object.keys(oldV), ...Object.keys(newV)])].filter((k) => !['updatedAt'].includes(k))
        if (keys.length === 0) return <span className="text-xs text-slate-400">—</span>
        return (
          <div className="space-y-0.5 text-xs">
            {keys.slice(0, 3).map((k) => (
              <p key={k} className="text-slate-500">
                <span className="font-medium text-slate-600">{k}:</span> <span className="text-red-400 line-through">{String(oldV[k] ?? '∅')}</span> → <span className="text-emerald-600">{String(newV[k] ?? '∅')}</span>
              </p>
            ))}
            {keys.length > 3 && <p className="text-slate-400">+{keys.length - 3} more</p>}
          </div>
        )
      },
    },
    { key: 'ip', header: 'IP', render: (a) => <span className="font-mono text-xs text-slate-400">{a.ip}</span> },
  ]

  return (
    <div>
      <PageHeader title="Audit log" subtitle="Immutable, append-only record of every consequential change. Normal users cannot edit or delete entries — by design." />
      <div className="card mb-3 flex flex-wrap gap-2 p-3">
        <Select value={entityType} onChange={(e) => { setEntityType(e.target.value); setPage(0) }} className="w-44">
          <option value="">All entity types</option>
          {ENTITY_TYPES.map((t) => <option key={t}>{t}</option>)}
        </Select>
        <Input value={action} onChange={(e) => { setAction(e.target.value); setPage(0) }} placeholder="Filter by action, e.g. LEAD_UPDATE" className="max-w-xs" />
      </div>
      <div className="card">
        <DataTable data={data} loading={isFetching} columns={columns} onPageChange={setPage}
          empty={{ icon: <ScrollText className="h-6 w-6" />, title: 'No audit entries match' }} />
      </div>
    </div>
  )
}
