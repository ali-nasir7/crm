import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { CheckSquare, Flame, Phone, Snowflake, Sun } from 'lucide-react'
import { api } from '@/api/client'
import { useAuth } from '@/stores/auth'
import type { LeadItem, PageResponse, TaskItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Badge, ScoreBadge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Select } from '@/components/ui/Input'
import { PageLoader } from '@/components/ui/Misc'
import { Table, THead, TR, TH, TD } from '@/components/ui/Table'
import { cn, fmtDateTime, fmtAgo } from '@/lib/utils'
import { Kpi } from './ExecutiveDashboard'

export default function MyDayPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [taskFilter, setTaskFilter] = useState('OPEN')

  const { data: me } = useQuery({ queryKey: ['dashboard', 'me'], queryFn: async () => (await api.get('/dashboard/me')).data as Record<string, never> })
  const { data: tasks, isLoading } = useQuery({
    queryKey: ['tasks', 'my-day', taskFilter],
    queryFn: async () => (await api.get<PageResponse<TaskItem>>('/tasks', { params: { assignee: user?.id, status: taskFilter || undefined, size: 50 } })).data,
  })
  const { data: hotLeads } = useQuery({
    queryKey: ['leads', 'hot'],
    queryFn: async () => (await api.get<PageResponse<LeadItem>>('/leads', { params: { minScore: 75, size: 8, sort: 'score,desc' } })).data,
  })
  const { data: stale } = useQuery({
    queryKey: ['leads', 'stale'],
    queryFn: async () => (await api.get<PageResponse<LeadItem>>('/leads', { params: { uncontacted: true, size: 8, sort: 'createdAt,asc' } })).data,
  })

  if (isLoading || !me) return <PageLoader />

  return (
    <div>
      <PageHeader title="My Day" subtitle="Your follow-ups, hot leads, and untouched prospects." />

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi label="Tasks due today" value={String(me.tasksDueToday ?? 0)} icon={CheckSquare} tone="blue" />
        <Kpi label="Overdue tasks" value={String(me.tasksOverdue ?? 0)} icon={Flame} tone={Number(me.tasksOverdue ?? 0) > 0 ? 'red' : 'green'} />
        <Kpi label="My hot leads" value={String(me.hotLeads ?? 0)} icon={Flame} tone="amber" />
        <Kpi label="Never contacted" value={String(me.uncontacted ?? 0)} icon={Snowflake} tone="purple" />
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader
            title="My tasks"
            action={
              <Select value={taskFilter} onChange={(e) => setTaskFilter(e.target.value)} className="h-8 w-32 !py-0 text-xs">
                <option value="OPEN">Open</option>
                <option value="COMPLETED">Completed</option>
                <option value="">All</option>
              </Select>
            }
          />
          <CardBody className="!px-0">
            {tasks && tasks.content.length > 0 ? (
              <Table>
                <THead><TR><TH>Task</TH><TH>Due</TH><TH>Priority</TH><TH /></TR></THead>
                <tbody>
                  {tasks.content.map((t) => (
                    <TR key={t.id}>
                      <TD>
                        <p className={cn('font-medium', t.status === 'COMPLETED' ? 'text-slate-400 line-through' : 'text-slate-800')}>{t.title}</p>
                        {t.businessName && <p className="text-xs text-slate-500">{t.businessName}</p>}
                      </TD>
                      <TD className={cn('whitespace-nowrap text-xs', t.status === 'OPEN' && new Date(t.dueAt) < new Date() ? 'font-semibold text-red-500' : 'text-slate-500')}>{fmtDateTime(t.dueAt)}</TD>
                      <TD><Badge tone={t.priority === 'URGENT' || t.priority === 'HIGH' ? 'red' : t.priority === 'MEDIUM' ? 'yellow' : 'gray'}>{t.priority}</Badge></TD>
                      <TD>{t.leadId && <Button variant="ghost" size="sm" onClick={() => navigate(`/leads/${t.leadId}`)}>Open</Button>}</TD>
                    </TR>
                  ))}
                </tbody>
              </Table>
            ) : (
              <p className="px-5 py-8 text-center text-sm text-slate-400">No tasks — enjoy the clear runway.</p>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Hot leads" subtitle="Score ≥ 75 — strike while it's hot" />
          <CardBody className="!px-0">
            {hotLeads && hotLeads.content.length > 0 ? (
              <Table>
                <THead><TR><TH>Lead</TH><TH>Score</TH><TH>Last contact</TH></TR></THead>
                <tbody>
                  {hotLeads.content.map((l) => (
                    <TR key={l.id} onClick={() => navigate(`/leads/${l.id}`)}>
                      <TD><p className="font-medium text-slate-800">{l.businessName}</p><p className="text-xs text-slate-500">{l.city ?? l.country ?? ''}</p></TD>
                      <TD><ScoreBadge score={l.score} category={l.scoreCategory} /></TD>
                      <TD className="text-xs text-slate-500">{l.lastContactedAt ? fmtAgo(l.lastContactedAt) : <Badge tone="yellow">Never</Badge>}</TD>
                    </TR>
                  ))}
                </tbody>
              </Table>
            ) : <p className="px-5 py-8 text-center text-sm text-slate-400">No hot leads yet — keep importing and calling.</p>}
          </CardBody>
        </Card>

        <Card className="xl:col-span-2">
          <CardHeader title="Untouched leads" subtitle="Oldest first — nobody has called them yet" action={<Button variant="secondary" size="sm" onClick={() => navigate('/leads?status=NEW')}><Sun className="h-3.5 w-3.5" /> View all new</Button>} />
          <CardBody className="!px-0">
            {stale && stale.content.length > 0 ? (
              <Table>
                <THead><TR><TH>Lead</TH><TH>Score</TH><TH>Source</TH><TH>Created</TH></TR></THead>
                <tbody>
                  {stale.content.map((l) => (
                    <TR key={l.id} onClick={() => navigate(`/leads/${l.id}`)}>
                      <TD><p className="font-medium text-slate-800">{l.businessName}</p><p className="text-xs text-slate-500">{l.contactName ?? l.email ?? ''}</p></TD>
                      <TD><ScoreBadge score={l.score} category={l.scoreCategory} /></TD>
                      <TD className="text-slate-500">{l.sourceName ?? '—'}</TD>
                      <TD className="text-xs text-slate-500">{fmtDateTime(l.createdAt)}</TD>
                    </TR>
                  ))}
                </tbody>
              </Table>
            ) : <p className="px-5 py-8 text-center text-sm text-slate-400">Every lead has been contacted. Excellent.</p>}
          </CardBody>
        </Card>
      </div>

      <p className="mt-4 flex items-center gap-2 text-xs text-slate-400"><Phone className="h-3.5 w-3.5" /> Tip: log every call — scoring rules and NO_REPLY automations depend on accurate activity.</p>
    </div>
  )
}
