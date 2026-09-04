import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Briefcase } from 'lucide-react'
import { api } from '@/api/client'
import type { ClientItem, PageResponse } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable, type Column } from '@/components/shared/DataTable'
import { Badge } from '@/components/ui/Badge'
import { Select } from '@/components/ui/Input'
import { fmtDate, fmtMoney } from '@/lib/utils'

export default function ClientsPage() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('')

  const { data, isFetching } = useQuery({
    queryKey: ['clients', page, status],
    queryFn: async () => (await api.get<PageResponse<ClientItem>>('/clients', { params: { page, size: 25, status: status || undefined, sort: 'createdAt,desc' } })).data,
  })

  const columns: Column<ClientItem>[] = [
    { key: 'company', header: 'Client', render: (c) => (
      <div>
        <p className="font-medium text-slate-800">{c.companyName ?? '—'}</p>
        <p className="text-xs text-slate-500">{c.website ?? ''}</p>
      </div>
    ) },
    { key: 'contact', header: 'Primary contact', render: (c) => <span className="text-slate-600">{c.primaryContactName ?? '—'}</span> },
    { key: 'manager', header: 'Account manager', render: (c) => <span className="text-slate-600">{c.accountManagerName ?? '—'}</span> },
    { key: 'ltv', header: 'Lifetime value', render: (c) => <span className="font-medium tabular-nums">{fmtMoney(c.lifetimeValue)}</span> },
    { key: 'status', header: 'Status', render: (c) => <Badge tone={c.status === 'ACTIVE' ? 'green' : c.status === 'CHURNED' ? 'red' : 'gray'}>{c.status}</Badge> },
    { key: 'converted', header: 'Converted', render: (c) => <span className="text-slate-500">{fmtDate(c.convertedAt)}</span> },
  ]

  return (
    <div>
      <PageHeader title="Clients" subtitle="Converted leads. Full pre-conversion history remains on the original lead." />
      <div className="card mb-3 p-3">
        <Select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }} className="w-44">
          <option value="">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="PROSPECT">Prospect</option>
          <option value="FORMER">Former</option>
        </Select>
      </div>
      <div className="card">
        <DataTable data={data} loading={isFetching} columns={columns} onPageChange={setPage} onRowClick={(c) => c.convertedFromLeadId && navigate(`/leads/${c.convertedFromLeadId}`)}
          empty={{ icon: <Briefcase className="h-6 w-6" />, title: 'No clients yet', subtitle: 'Convert a lead to create your first client.' }} />
      </div>
    </div>
  )
}
