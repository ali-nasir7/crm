import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { BarChart3, Download } from 'lucide-react'
import { api } from '@/api/client'
import { useCan } from '@/stores/auth'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input, Select, Label } from '@/components/ui/Input'
import { Table, THead, TR, TH, TD } from '@/components/ui/Table'
import { EmptyState, PageLoader } from '@/components/ui/Misc'
import { downloadCsv } from '@/lib/utils'

interface ReportTable { columns: string[]; rows: (string | number)[][] }

const REPORTS = [
  { type: 'pipeline', label: 'Pipeline by stage' },
  { type: 'lead-sources', label: 'Lead sources' },
  { type: 'rep-performance', label: 'Rep performance' },
  { type: 'team-performance', label: 'Team performance' },
  { type: 'campaign-performance', label: 'Campaign performance' },
  { type: 'conversion-funnel', label: 'Conversion funnel' },
  { type: 'activity-summary', label: 'Activity summary' },
]

function toCsv(t: ReportTable): string {
  const esc = (v: string | number) => {
    const s = String(v ?? '')
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  return [t.columns.map(esc).join(','), ...(t.rows ?? []).map((r) => (Array.isArray(r) ? r : []).map(esc).join(','))].join('\n')
}

export default function ReportsPage() {
  const can = useCan('REPORT_VIEW')
  const [type, setType] = useState('pipeline')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')

  const { data, isFetching } = useQuery({
    queryKey: ['report', type, from, to],
    queryFn: async () => (await api.get<ReportTable>(`/reports/${type}`, { params: { from: from || undefined, to: to || undefined } })).data,
    enabled: can,
  })

  if (!can) return <EmptyState title="No access" subtitle="You don't have permission to view reports (REPORT_VIEW required)." />

  return (
    <div>
      <PageHeader title="Reports" subtitle="Operational reporting with CSV / Excel / PDF export." />
      <div className="card mb-4 flex flex-wrap items-end gap-3 p-4">
        <div className="min-w-52">
          <Label>Report</Label>
          <Select value={type} onChange={(e) => setType(e.target.value)}>
            {REPORTS.map((r) => <option key={r.type} value={r.type}>{r.label}</option>)}
          </Select>
        </div>
        <div>
          <Label>From</Label>
          <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div>
          <Label>To</Label>
          <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
        <Button variant="secondary" className="mb-0.5" onClick={() => data && downloadCsv(`${type}-report.csv`, toCsv(data))}>
          <Download className="h-4 w-4" /> Export CSV
        </Button>
        <a href={`/api/v1/reports/${type}?format=xlsx${from ? `&from=${from}` : ''}${to ? `&to=${to}` : ''}`} className="mb-0.5">
          <Button variant="secondary"><Download className="h-4 w-4" /> Excel</Button>
        </a>
        <a href={`/api/v1/reports/${type}?format=pdf${from ? `&from=${from}` : ''}${to ? `&to=${to}` : ''}`} target="_blank" rel="noreferrer" className="mb-0.5">
          <Button variant="secondary"><Download className="h-4 w-4" /> PDF</Button>
        </a>
      </div>

      <Card>
        <CardHeader title={REPORTS.find((r) => r.type === type)?.label ?? type} subtitle={isFetching ? 'Loading…' : `${data?.rows.length ?? 0} rows`} />
        <CardBody className="!px-0">
          {isFetching ? <PageLoader /> : data && data.rows.length > 0 ? (
            <Table>
              <THead>
                <TR>{data.columns.map((c, i) => <TH key={i} className={i > 0 ? 'text-right' : ''}>{c}</TH>)}</TR>
              </THead>
              <tbody>
                {data.rows.map((row, ri) => (
                  <TR key={ri}>
                    {(Array.isArray(row) ? row : []).map((cell, ci) => (
                      <TD key={ci} className={ci > 0 ? 'text-right tabular-nums' : 'font-medium text-slate-700'}>{cell}</TD>
                    ))}
                  </TR>
                ))}
              </tbody>
            </Table>
          ) : (
            <EmptyState icon={<BarChart3 className="h-6 w-6" />} title="No data for this range" subtitle="Adjust the date range or pick another report." />
          )}
        </CardBody>
      </Card>
    </div>
  )
}
