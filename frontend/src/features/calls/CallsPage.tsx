import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Phone } from 'lucide-react'
import { api } from '@/api/client'
import type { CallItem, PageResponse, UserItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable, type Column } from '@/components/shared/DataTable'
import { Badge } from '@/components/ui/Badge'
import { Select } from '@/components/ui/Input'
import { useAuth } from '@/stores/auth'
import { fmtDateTime } from '@/lib/utils'
import { MyCallingDevices } from '../calling/MyCallingDevices'

const OUTCOME_TONES: Record<string, 'green' | 'yellow' | 'red' | 'gray'> = {
  CONNECTED: 'green', NO_ANSWER: 'yellow', VOICEMAIL: 'gray', BUSY: 'yellow', WRONG_NUMBER: 'red', CALLBACK_REQUESTED: 'green',
}

export default function CallsPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [params] = useSearchParams()
  const [page, setPage] = useState(0)
  const [userId, setUserId] = useState(params.get('mine') === '1' ? (user?.id ?? '') : '')
  const [outcome, setOutcome] = useState('')

  const { data, isFetching } = useQuery({
    queryKey: ['calls', page, userId, outcome],
    queryFn: async () => (await api.get<PageResponse<CallItem>>('/calls', { params: { page, size: 25, userId: userId || undefined, outcome: outcome || undefined, sort: 'occurredAt,desc' } })).data,
  })
  const { data: users } = useQuery({ queryKey: ['users-lite'], queryFn: async () => (await api.get<PageResponse<UserItem>>('/users', { params: { size: 200 } })).data })

  const columns: Column<CallItem>[] = [
    { key: 'lead', header: 'Lead', render: (c) => <span className="font-medium text-slate-800">{c.businessName ?? '—'}</span> },
    { key: 'when', header: 'When', render: (c) => <span className="text-slate-600">{fmtDateTime(c.occurredAt)}</span> },
    { key: 'direction', header: 'Direction', render: (c) => <Badge tone={c.direction === 'OUTBOUND' ? 'blue' : 'purple'}>{c.direction}</Badge> },
    { key: 'duration', header: 'Duration', render: (c) => <span className="tabular-nums text-slate-600">{c.durationSeconds ? `${Math.floor(c.durationSeconds / 60)}m ${c.durationSeconds % 60}s` : '—'}</span> },
    { key: 'outcome', header: 'Outcome', render: (c) => <Badge tone={OUTCOME_TONES[c.outcome] ?? 'gray'}>{c.outcome}</Badge> },
    { key: 'notes', header: 'Notes', render: (c) => <span className="line-clamp-1 max-w-72 text-slate-500">{c.notes ?? '—'}</span> },
    { key: 'by', header: 'By', render: (c) => <span className="text-slate-600">{c.userName}</span> },
  ]

  return (
    <div>
      <PageHeader title="Calls" subtitle="Every logged call with outcome and follow-up. Log calls from any lead page." />
      <MyCallingDevices />
      <div className="card mb-3 flex flex-wrap gap-2 p-3">
        <Select value={userId} onChange={(e) => { setUserId(e.target.value); setPage(0) }} className="w-48">
          <option value="">Everyone</option>
          <option value={user?.id}>My calls</option>
          {users?.content.map((u) => <option key={u.id} value={u.id}>{u.displayName}</option>)}
        </Select>
        <Select value={outcome} onChange={(e) => { setOutcome(e.target.value); setPage(0) }} className="w-48">
          <option value="">All outcomes</option>
          {Object.keys(OUTCOME_TONES).map((o) => <option key={o}>{o}</option>)}
        </Select>
      </div>
      <div className="card">
        <DataTable data={data} loading={isFetching} columns={columns} onPageChange={setPage} onRowClick={(c) => c.leadId && navigate(`/leads/${c.leadId}`)}
          empty={{ icon: <Phone className="h-6 w-6" />, title: 'No calls logged', subtitle: 'Open a lead and use “Log call” to build call history.' }} />
      </div>
    </div>
  )
}
