import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ArrowDownRight, ArrowUpRight, Handshake, Phone, Target, Users } from 'lucide-react'
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api, apiError } from '@/api/client'
import { useAuth } from '@/stores/auth'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { PageLoader } from '@/components/ui/Misc'
import { fmtMoney } from '@/lib/utils'

const COLORS = ['#2563eb', '#0891b2', '#7c3aed', '#059669', '#d97706', '#dc2626', '#4f46e5', '#0d9488', '#be185d', '#65a30d']

export default function ExecutiveDashboard() {
  const { user } = useAuth()
  const { data: exec, isLoading, isError, error } = useQuery({ queryKey: ['dashboard', 'executive'], queryFn: async () => (await api.get('/dashboard/executive')).data as Record<string, never> })
  const { data: charts } = useQuery({ queryKey: ['dashboard', 'charts'], queryFn: async () => (await api.get('/dashboard/charts')).data as Record<string, never> })

  if (isError) {
        return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-6">
        <h2 className="text-lg font-bold text-red-700">Dashboard failed to load</h2>
        <p className="mt-1 text-sm text-red-700">{apiError(error).code}: {apiError(error).message}</p>
        <p className="mt-2 text-xs text-red-600">Open the browser console (F12), Network tab, and check the status of /api/v1/dashboard/executive.</p>
      </div>
    )
  }
  if (isLoading || !exec) return <PageLoader />

  const newLeads = Number(exec.newLeads30d ?? 0)
  const converted = Number(exec.converted30d ?? 0)
  const convRate = newLeads > 0 ? ((converted / newLeads) * 100).toFixed(1) : '0.0'
  // Defensive: if the API shape ever drifts, render an empty chart - never crash the screen.
  const arr = (x: unknown): unknown[] => (Array.isArray(x) ? x : [])
  const bySource = arr(charts?.leadsBySource) as { name: string; count: number }[]
  const byDay = arr(charts?.leadsPerDay) as { day: string; count: number }[]
  const pipeline = arr(charts?.pipelineByStage) as { stage: string; count: number; value: number }[]
  const statuses = arr(charts?.leadsByStatus) as { status: string; count: number }[]
  const openPipelineValue = Number((exec as Record<string, unknown>).openPipelineValue ?? (exec as Record<string, unknown>).pipelineValue ?? 0)

  return (
    <div>
      <PageHeader title={`Welcome back, ${user?.firstName ?? 'there'}`} subtitle="Organization performance at a glance." />

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi label="New leads (30d)" value={newLeads.toLocaleString()} icon={Users} tone="blue" />
        <Kpi label="Converted (30d)" value={converted.toLocaleString()} icon={Target} tone="green" hint={`${convRate}% of new`} />
        <Kpi label="Open pipeline" value={fmtMoney(openPipelineValue)} icon={Handshake} tone="purple" />
        <Kpi label="Calls (7d)" value={Number(exec.calls7d ?? 0).toLocaleString()} icon={Phone} tone="amber" />
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader title="Lead inflow — last 30 days" />
          <CardBody>
            <ResponsiveContainer width="100%" height={260}>
              <AreaChart data={byDay}>
                <defs>
                  <linearGradient id="leadFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#2563eb" stopOpacity={0.25} />
                    <stop offset="100%" stopColor="#2563eb" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
                <XAxis dataKey="day" tick={{ fontSize: 11 }} tickFormatter={(v: string) => v.slice(5)} />
                <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
                <Tooltip />
                <Area type="monotone" dataKey="count" stroke="#2563eb" strokeWidth={2} fill="url(#leadFill)" name="Leads" />
              </AreaChart>
            </ResponsiveContainer>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Leads by status" />
          <CardBody>
            <ResponsiveContainer width="100%" height={260}>
              <PieChart>
                <Pie data={statuses} dataKey="count" nameKey="status" innerRadius={55} outerRadius={90} paddingAngle={2}>
                  {statuses.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                </Pie>
                <Tooltip />
                <Legend wrapperStyle={{ fontSize: 12 }} />
              </PieChart>
            </ResponsiveContainer>
          </CardBody>
        </Card>

        <Card className="xl:col-span-2">
          <CardHeader title="Pipeline by stage" subtitle="Open deals — count and total value" />
          <CardBody>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={pipeline}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
                <XAxis dataKey="stage" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
                <Tooltip formatter={(v: number, name: string) => (name === 'value' ? fmtMoney(v) : v)} />
                <Bar dataKey="count" fill="#2563eb" radius={[4, 4, 0, 0]} name="Deals" />
                <Bar dataKey="value" fill="#7c3aed" radius={[4, 4, 0, 0]} name="Value" hide />
              </BarChart>
            </ResponsiveContainer>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Top lead sources (30d)" />
          <CardBody>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={bySource.slice(0, 6)} layout="vertical">
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" horizontal={false} />
                <XAxis type="number" tick={{ fontSize: 11 }} allowDecimals={false} />
                <YAxis type="category" dataKey="name" tick={{ fontSize: 11 }} width={90} />
                <Tooltip />
                <Bar dataKey="count" fill="#0891b2" radius={[0, 4, 4, 0]} name="Leads" />
              </BarChart>
            </ResponsiveContainer>
          </CardBody>
        </Card>
      </div>

      <div className="mt-4 flex flex-wrap gap-3 text-sm">
        <Link to="/my-day" className="font-medium text-blue-600 hover:underline">Go to My Day →</Link>
        <Link to="/pipeline" className="font-medium text-blue-600 hover:underline">Open pipeline board →</Link>
        <Link to="/reports" className="font-medium text-blue-600 hover:underline">Run reports →</Link>
      </div>
    </div>
  )
}

export function Kpi({ label, value, icon: Icon, tone, hint, trend }: {
  label: string
  value: string
  icon: typeof Users
  tone: 'blue' | 'green' | 'purple' | 'amber' | 'red'
  hint?: string
  trend?: number
}) {
  const tones: Record<string, string> = {
    blue: 'bg-blue-50 text-blue-600', green: 'bg-emerald-50 text-emerald-600',
    purple: 'bg-violet-50 text-violet-600', amber: 'bg-amber-50 text-amber-600', red: 'bg-red-50 text-red-600',
  }
  return (
    <Card>
      <CardBody className="flex items-start justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">{label}</p>
          <p className="mt-1 text-2xl font-bold tabular-nums text-slate-900">{value}</p>
          <p className="mt-0.5 flex items-center gap-1 text-xs text-slate-500">
            {trend !== undefined && (
              <span className={trend >= 0 ? 'flex items-center text-emerald-600' : 'flex items-center text-red-500'}>
                {trend >= 0 ? <ArrowUpRight className="h-3 w-3" /> : <ArrowDownRight className="h-3 w-3" />}
                {Math.abs(trend)}%
              </span>
            )}
            {hint}
          </p>
        </div>
        <span className={`rounded-lg p-2.5 ${tones[tone]}`}><Icon className="h-5 w-5" /></span>
      </CardBody>
    </Card>
  )
}
