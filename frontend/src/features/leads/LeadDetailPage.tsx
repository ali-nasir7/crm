import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft, Bot, CalendarDays, CheckSquare, ExternalLink, FileText, Globe,
  Linkedin, Mail, MapPin, Pencil, Phone, RefreshCw, Send, Sparkles, Star, Trash2, UserPlus,
} from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useCan } from '@/stores/auth'
import { useToast } from '@/components/ui/Toast'
import { Button } from '@/components/ui/Button'
import { Badge, ScoreBadge } from '@/components/ui/Badge'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/Tabs'
import { Card, CardHeader, CardBody } from '@/components/ui/Card'
import { Dialog } from '@/components/ui/Dialog'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input, Select, Textarea, Label } from '@/components/ui/Input'
import { Avatar, EmptyState, PageLoader } from '@/components/ui/Misc'
import { Timeline } from '@/components/shared/Timeline'
import { Table, THead, TR, TH, TD } from '@/components/ui/Table'
import { fmtDateTime, fmtMoney, fmtAgo } from '@/lib/utils'
import type { ActivityItem, LeadItem, MeetingItem, PageResponse, TaskItem, EmailItem, DocumentItem, ProposalItem, DealItem, CallItem, UserItem } from '@/types'
import { LeadFormModal } from './LeadFormModal'
import { LogCallModal } from '../calls/LogCallModal'
import { CallNowModal } from '../calling/CallNowModal'
import { ConvertDialog } from './ConvertDialog'
import { EmailComposer } from '../emails/EmailComposer'

export default function LeadDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const canUpdate = useCan('LEAD_UPDATE')
  const canDelete = useCan('LEAD_DELETE')
  const canLogCall = useCan('CALL_CREATE')
  const [callNowOpen, setCallNowOpen] = useState(false)
  const canSendEmail = useCan('EMAIL_SEND')
  const canConvert = useCan('LEAD_CONVERT')
  const canAi = useCan('AI_USE')

  const [editOpen, setEditOpen] = useState(false)
  const [callOpen, setCallOpen] = useState(false)
  const [emailOpen, setEmailOpen] = useState(false)
  const [convertOpen, setConvertOpen] = useState(false)
  const [taskOpen, setTaskOpen] = useState(false)
  const [meetingOpen, setMeetingOpen] = useState(false)
  const [noteOpen, setNoteOpen] = useState(false)
  const [noteText, setNoteText] = useState('')
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [aiPanel, setAiPanel] = useState<string | null>(null)
  const [aiBusy, setAiBusy] = useState(false)

  const { data: lead, isLoading } = useQuery({
    queryKey: ['lead', id],
    queryFn: async () => (await api.get<LeadItem>(`/leads/${id}`)).data,
    enabled: !!id,
  })

  const { data: timeline } = useQuery({
    queryKey: ['lead-timeline', id],
    queryFn: async () => (await api.get<PageResponse<ActivityItem>>(`/leads/${id}/activities`, { params: { size: 50 } })).data,
    enabled: !!id,
  })

  const { data: tasks } = useQuery({
    queryKey: ['lead-tasks', id],
    queryFn: async () => (await api.get<PageResponse<TaskItem>>('/tasks', { params: { leadId: id, size: 20 } })).data,
    enabled: !!id,
  })

  const { data: emails } = useQuery({
    queryKey: ['lead-emails', id],
    queryFn: async () => (await api.get<PageResponse<EmailItem>>('/emails', { params: { leadId: id, size: 20 } })).data,
    enabled: !!id,
  })

  const { data: calls } = useQuery({
    queryKey: ['lead-calls', id],
    queryFn: async () => (await api.get<PageResponse<CallItem>>(`/leads/${id}/calls`, { params: { size: 20 } })).data,
    enabled: !!id,
  })

  const { data: meetings } = useQuery({
    queryKey: ['lead-meetings', id],
    queryFn: async () => (await api.get<PageResponse<MeetingItem>>('/meetings', { params: { leadId: id, size: 20 } })).data,
    enabled: !!id,
  })

  const { data: documents } = useQuery({
    queryKey: ['lead-documents', id],
    queryFn: async () => (await api.get<PageResponse<DocumentItem>>('/documents', { params: { leadId: id, size: 50 } })).data,
    enabled: !!id,
  })

  const { data: deals } = useQuery({
    queryKey: ['lead-deals', id],
    queryFn: async () => (await api.get<PageResponse<DealItem>>('/deals', { params: { leadId: id, size: 20, sort: 'createdAt,desc' } })).data,
    enabled: !!id,
  })

  const { data: proposals } = useQuery({
    queryKey: ['lead-proposals', id],
    queryFn: async () => (await api.get<PageResponse<ProposalItem>>('/proposals', { params: { leadId: id, size: 20 } })).data,
    enabled: !!id,
  })

  const { data: users } = useQuery({ queryKey: ['users-lite'], queryFn: async () => (await api.get<PageResponse<UserItem>>('/users', { params: { size: 200 } })).data })

  const patch = useMutation({
    mutationFn: async (body: Record<string, unknown>) => {
      if (body.assignedUserId !== undefined) return api.post(`/leads/${id}/assign`, { userId: body.assignedUserId })
      if (body.completeTaskId) return api.post(`/tasks/${body.completeTaskId}/complete`, {})
      return api.put(`/leads/${id}`, body)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['lead', id] })
      qc.invalidateQueries({ queryKey: ['lead-timeline', id] })
      qc.invalidateQueries({ queryKey: ['leads'] })
    },
    onError: (e) => toast.push('error', 'Update failed', apiError(e).message),
  })

  const runAi = async (kind: 'summary' | 'next-action' | 'email-draft') => {
    if (!id) return
    setAiBusy(true)
    try {
      if (kind === 'summary') {
        const res = await api.post(`/ai/lead-summary/${id}`)
        setAiPanel(JSON.stringify(res.data, null, 2))
      } else if (kind === 'next-action') {
        const res = await api.post(`/ai/next-action/${id}`)
        setAiPanel(JSON.stringify(res.data, null, 2))
      } else {
        const res = await api.post(`/ai/email-draft`, null, { params: { leadId: id, useCase: 'intro' } })
        setAiPanel(JSON.stringify(res.data, null, 2))
      }
    } catch (e) {
      toast.push('error', 'AI request failed', apiError(e).message)
    } finally {
      setAiBusy(false)
    }
  }

  const addNote = useMutation({
    mutationFn: async () => api.post(`/leads/${id}/activities`, { body: noteText }),
    onSuccess: () => {
      setNoteOpen(false); setNoteText('')
      qc.invalidateQueries({ queryKey: ['lead-timeline', id] })
    },
  })

  if (isLoading || !lead) return <PageLoader />

  return (
    <div>
      <button onClick={() => navigate('/leads')} className="mb-3 inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700">
        <ArrowLeft className="h-4 w-4" /> Back to leads
      </button>

      {/* Header */}
      <div className="card mb-4 p-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex items-start gap-4">
            <Avatar name={lead.businessName} className="h-12 w-12 text-base" />
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-xl font-bold text-slate-900">{lead.businessName}</h1>
                <Badge tone="blue">{lead.status}</Badge>
                <ScoreBadge score={lead.score} category={lead.scoreCategory} />
              </div>
              <p className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-slate-500">
                {lead.contactName && <span>{lead.contactName}{lead.jobTitle ? ` · ${lead.jobTitle}` : ''}</span>}
                {lead.city || lead.country ? <span className="inline-flex items-center gap-1"><MapPin className="h-3.5 w-3.5" />{[lead.city, lead.country].filter(Boolean).join(', ')}</span> : null}
                <span>Owner: {lead.assignedUserName ?? 'Unassigned'}</span>
                {lead.lastContactedAt ? <span>Last contact {fmtAgo(lead.lastContactedAt)}</span> : <Badge tone="yellow">Never contacted</Badge>}
              </p>
            </div>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {canLogCall && <Button variant="secondary" size="sm" onClick={() => setCallNowOpen(true)}><Phone className="h-3.5 w-3.5" /> Call</Button>}
            {canLogCall && <Button variant="secondary" size="sm" onClick={() => setCallOpen(true)}><Phone className="h-3.5 w-3.5" /> Log call</Button>}
            {canSendEmail && <Button variant="secondary" size="sm" onClick={() => setEmailOpen(true)}><Mail className="h-3.5 w-3.5" /> Email</Button>}
            <Button variant="secondary" size="sm" onClick={() => setTaskOpen(true)}><CheckSquare className="h-3.5 w-3.5" /> Task</Button>
            <Button variant="secondary" size="sm" onClick={() => setMeetingOpen(true)}><CalendarDays className="h-3.5 w-3.5" /> Meeting</Button>
            <Button variant="secondary" size="sm" onClick={() => setNoteOpen(true)}>Note</Button>
            {canAi && (
              <Button variant="secondary" size="sm" loading={aiBusy} onClick={() => runAi('summary')} title="Generate an AI summary (reviewable, logged)"><Sparkles className="h-3.5 w-3.5" /> AI summary</Button>
            )}
            {canConvert && lead.status !== 'CONVERTED' && (
              <Button size="sm" onClick={() => setConvertOpen(true)}><Star className="h-3.5 w-3.5" /> Convert to client</Button>
            )}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        {/* Left: details */}
        <div className="space-y-4">
          <Card>
            <CardHeader title="Details" action={canUpdate ? <Button variant="ghost" size="sm" onClick={() => setEditOpen(true)}><Pencil className="h-3.5 w-3.5" /> Edit</Button> : undefined} />
            <CardBody className="space-y-2.5 text-sm">
              <Row icon={Mail} label="Email" value={lead.email} href={lead.email ? `mailto:${lead.email}` : undefined} />
              <Row icon={Phone} label="Phone" value={lead.phone} />
              <Row icon={Phone} label="WhatsApp" value={lead.whatsapp} />
              <Row icon={Globe} label="Website" value={lead.website} href={lead.website ? `https://${lead.website.replace(/^https?:\/\//, '')}` : undefined} />
              <Row icon={Linkedin} label="LinkedIn" value={lead.linkedin} href={lead.linkedin ?? undefined} />
              <Row icon={MapPin} label="Location" value={[lead.address, lead.city, lead.country].filter(Boolean).join(', ') || null} />
              <Row icon={FileText} label="Industry" value={lead.industry} />
              <Row icon={FileText} label="Business type" value={lead.businessType} />
              <Row icon={FileText} label="Company size" value={lead.companySize} />
              <Row icon={FileText} label="Employees" value={lead.employeesCount?.toString() ?? null} />
              <Row icon={FileText} label="Source" value={lead.sourceName} />
              <Row icon={FileText} label="Stage" value={lead.stageName} />
              <div className="pt-1">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Tags</p>
                <div className="mt-1 flex flex-wrap gap-1">
                  {(lead.tags ?? []).length === 0 && <span className="text-sm text-slate-400">—</span>}
                  {lead.tags?.map((t) => <Badge key={t} tone="gray">{t}</Badge>)}
                </div>
              </div>
              {lead.customFields && Object.keys(lead.customFields).length > 0 && (
                <div className="pt-1">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Custom fields</p>
                  <dl className="mt-1 space-y-1">
                    {Object.entries(lead.customFields).map(([k, v]) => (
                      <div key={k} className="flex justify-between gap-3">
                        <dt className="text-slate-500">{k}</dt>
                        <dd className="text-right font-medium text-slate-700">{String(v)}</dd>
                      </div>
                    ))}
                  </dl>
                </div>
              )}
              {lead.notes && (
                <div className="pt-1">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Notes</p>
                  <p className="mt-1 whitespace-pre-wrap text-slate-600">{lead.notes}</p>
                </div>
              )}
            </CardBody>
          </Card>

          {/* Quick owner/stage controls */}
          {canUpdate && (
            <Card>
              <CardHeader title="Quick actions" />
              <CardBody className="grid grid-cols-1 gap-3">
                <div>
                  <Label>Owner</Label>
                  <Select value={lead.assignedUserId ?? ''} onChange={(e) => patch.mutate({ assignedUserId: e.target.value || null })}>
                    <option value="">Unassigned</option>
                    {users?.content.map((u) => <option key={u.id} value={u.id}>{u.displayName}</option>)}
                  </Select>
                </div>
                <div>
                  <Label>Status</Label>
                  <Select value={lead.status} onChange={(e) => patch.mutate({ status: e.target.value })}>
                    {['NEW', 'WORKING', 'NURTURE', 'QUALIFIED', 'UNQUALIFIED', 'CONVERTED'].map((s) => <option key={s} value={s}>{s}</option>)}
                  </Select>
                </div>
                <div>
                  <Label>Next follow-up</Label>
                  <Input type="datetime-local" value={lead.nextFollowUpAt ? lead.nextFollowUpAt.slice(0, 16) : ''} onChange={(e) => patch.mutate({ nextFollowUpAt: e.target.value ? new Date(e.target.value).toISOString() : null })} />
                </div>
              </CardBody>
            </Card>
          )}

          {canAi && (
            <Card>
              <CardHeader title="AI assistant" subtitle="Reviewable & auditable — nothing runs autonomously" />
              <CardBody className="space-y-2">
                <Button variant="secondary" size="sm" className="w-full !justify-start" loading={aiBusy} onClick={() => runAi('summary')}><Bot className="h-4 w-4" /> Summarize this lead</Button>
                <Button variant="secondary" size="sm" className="w-full !justify-start" loading={aiBusy} onClick={() => runAi('next-action')}><Sparkles className="h-4 w-4" /> Suggest next action</Button>
                <Button variant="secondary" size="sm" className="w-full !justify-start" loading={aiBusy} onClick={() => runAi('email-draft')}><Send className="h-4 w-4" /> Draft intro email</Button>
                {aiPanel && (
                  <pre className="mt-2 max-h-64 overflow-auto whitespace-pre-wrap rounded-lg bg-slate-900 p-3 text-xs leading-relaxed text-slate-100">{aiPanel}</pre>
                )}
              </CardBody>
            </Card>
          )}
        </div>

        {/* Right: tabs */}
        <div className="xl:col-span-2">
          <Card>
            <CardBody>
              <Tabs defaultValue="timeline">
                <TabsList>
                  <TabsTrigger value="timeline">Timeline</TabsTrigger>
                  <TabsTrigger value="calls">Calls ({calls?.totalElements ?? 0})</TabsTrigger>
                  <TabsTrigger value="emails">Emails ({emails?.totalElements ?? 0})</TabsTrigger>
                  <TabsTrigger value="tasks">Tasks ({tasks?.totalElements ?? 0})</TabsTrigger>
                  <TabsTrigger value="meetings">Meetings ({meetings?.totalElements ?? 0})</TabsTrigger>
                  <TabsTrigger value="deals">Deals ({deals?.totalElements ?? 0})</TabsTrigger>
                  <TabsTrigger value="proposals">Proposals ({proposals?.totalElements ?? 0})</TabsTrigger>
                  <TabsTrigger value="documents">Documents ({documents?.totalElements ?? 0})</TabsTrigger>
                </TabsList>

                <TabsContent value="timeline">
                  <Timeline items={timeline?.content} />
                </TabsContent>

                <TabsContent value="calls">
                  {calls && calls.content.length > 0 ? (
                    <Table>
                      <THead><TR><TH>When</TH><TH>Direction</TH><TH>Duration</TH><TH>Outcome</TH><TH>Notes</TH><TH>By</TH></TR></THead>
                      <tbody>
                        {calls.content.map((c) => (
                          <TR key={c.id}>
                            <TD className="whitespace-nowrap">{fmtDateTime(c.occurredAt)}</TD>
                            <TD><Badge tone={c.direction === 'OUTBOUND' ? 'blue' : 'purple'}>{c.direction}</Badge></TD>
                            <TD>{c.durationSeconds ? `${Math.floor(c.durationSeconds / 60)}m ${c.durationSeconds % 60}s` : '—'}</TD>
                            <TD>{c.outcome}</TD>
                            <TD className="max-w-64 truncate text-slate-500">{c.notes ?? '—'}</TD>
                            <TD className="text-slate-500">{c.userName}</TD>
                          </TR>
                        ))}
                      </tbody>
                    </Table>
                  ) : <EmptyState icon={<Phone className="h-6 w-6" />} title="No calls logged" subtitle="Log the first call to start the conversation history." />}
                </TabsContent>

                <TabsContent value="emails">
                  {emails && emails.content.length > 0 ? (
                    <div className="space-y-2">
                      {emails.content.map((m) => (
                        <div key={m.id} className="rounded-lg border border-slate-200 p-3">
                          <div className="flex items-center justify-between gap-2">
                            <p className="truncate text-sm font-medium text-slate-800">{m.subject ?? '(no subject)'}</p>
                            <div className="flex shrink-0 items-center gap-1.5">
                              <Badge tone={m.direction === 'OUTBOUND' ? 'blue' : 'purple'}>{m.direction}</Badge>
                              {m.status && <Badge tone={m.status === 'OPENED' ? 'green' : m.status === 'BOUNCED' ? 'red' : 'gray'}>{m.status}</Badge>}
                            </div>
                          </div>
                          <p className="mt-1 truncate text-xs text-slate-500">{m.fromEmail} → {m.toEmails?.join(', ')}</p>
                          {m.preview && <p className="mt-1 line-clamp-2 text-sm text-slate-600">{m.preview}</p>}
                          <p className="mt-1 text-[11px] text-slate-400">
                            {m.sentAt && `Sent ${fmtDateTime(m.sentAt)}`}
                            {m.openedAt && ` · Opened ${fmtDateTime(m.openedAt)}${m.openCount && m.openCount > 1 ? ` (${m.openCount}×)` : ''}`}
                            {m.repliedAt && ` · Replied ${fmtDateTime(m.repliedAt)}`}
                            {m.bouncedAt && ` · Bounced`}
                          </p>
                        </div>
                      ))}
                    </div>
                  ) : <EmptyState icon={<Mail className="h-6 w-6" />} title="No emails" subtitle="Emails sent through connected accounts appear here with open and reply tracking." />}
                </TabsContent>

                <TabsContent value="tasks">
                  {tasks && tasks.content.length > 0 ? (
                    <div className="space-y-2">
                      {tasks.content.map((t) => (
                        <div key={t.id} className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 p-3">
                          <div className="min-w-0">
                            <p className={`truncate text-sm font-medium ${t.status === 'COMPLETED' ? 'text-slate-400 line-through' : 'text-slate-800'}`}>{t.title}</p>
                            <p className="text-xs text-slate-500">Due {fmtDateTime(t.dueAt)} · {t.priority} · {t.assignedUserName}</p>
                          </div>
                          {t.status !== 'COMPLETED' && canUpdate && (
                            <Button size="sm" variant="secondary" onClick={() => patch.mutate({ completeTaskId: t.id })} title="Complete task"><CheckSquare className="h-3.5 w-3.5" /></Button>
                          )}
                        </div>
                      ))}
                    </div>
                  ) : <EmptyState icon={<CheckSquare className="h-6 w-6" />} title="No tasks" subtitle="Create follow-up tasks so nothing slips." />}
                </TabsContent>

                <TabsContent value="meetings">
                  {meetings && meetings.content.length > 0 ? (
                    <div className="space-y-2">
                      {meetings.content.map((m) => (
                        <div key={m.id} className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 p-3">
                          <div>
                            <p className="text-sm font-medium text-slate-800">{m.title}</p>
                            <p className="text-xs text-slate-500">{fmtDateTime(m.startAt)} · {m.durationMinutes} min · {m.ownerName}</p>
                          </div>
                          {m.meetingLink && <a href={m.meetingLink} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-xs font-medium text-blue-600 hover:underline"><ExternalLink className="h-3 w-3" /> Join</a>}
                        </div>
                      ))}
                    </div>
                  ) : <EmptyState icon={<CalendarDays className="h-6 w-6" />} title="No meetings" />}
                </TabsContent>

                <TabsContent value="deals">
                  {deals && deals.content.length > 0 ? (
                    <div className="space-y-2">
                      {deals.content.map((d) => (
                        <div key={d.id} className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 p-3">
                          <div>
                            <p className="text-sm font-medium text-slate-800">{d.title}</p>
                            <p className="text-xs text-slate-500">{d.stageName} · {fmtMoney(d.amount, d.currency)} · {d.probability}%</p>
                          </div>
                          <Badge tone={d.status === 'WON' ? 'green' : d.status === 'LOST' ? 'red' : 'blue'}>{d.status}</Badge>
                        </div>
                      ))}
                    </div>
                  ) : <EmptyState icon={<Star className="h-6 w-6" />} title="No deals" subtitle="Deals are created during lead conversion or from the Deals page." />}
                </TabsContent>

                <TabsContent value="proposals">
                  {proposals && proposals.content.length > 0 ? (
                    <div className="space-y-2">
                      {proposals.content.map((p) => (
                        <div key={p.id} className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 p-3">
                          <div>
                            <p className="text-sm font-medium text-slate-800">{p.proposalNumber} · {p.title}</p>
                            <p className="text-xs text-slate-500">{fmtMoney(p.total, p.currency)}{p.sentAt ? ` · Sent ${fmtAgo(p.sentAt)}` : ' · Draft'}</p>
                          </div>
                          <Badge tone={p.status === 'ACCEPTED' ? 'green' : p.status === 'REJECTED' ? 'red' : p.status === 'SENT' || p.status === 'VIEWED' ? 'blue' : 'gray'}>{p.status}</Badge>
                        </div>
                      ))}
                    </div>
                  ) : <EmptyState icon={<FileText className="h-6 w-6" />} title="No proposals" />}
                </TabsContent>

                <TabsContent value="documents">
                  <DocumentsTab docId={id ?? ''} documents={documents} />
                </TabsContent>
              </Tabs>
            </CardBody>
          </Card>
        </div>
      </div>

      {editOpen && <LeadFormModal open onClose={() => setEditOpen(false)} lead={lead} />}
      {callOpen && <LogCallModal open onClose={() => setCallOpen(false)} leadId={lead.id} leadName={lead.businessName ?? undefined} />}
      {callNowOpen && <CallNowModal open onClose={() => setCallNowOpen(false)} leadId={lead.id} leadName={lead.businessName ?? undefined} defaultNumber={lead.phone ?? ''} />}
      {emailOpen && <EmailComposer open onClose={() => setEmailOpen(false)} leadId={lead.id} leadName={lead.businessName ?? undefined} toEmail={lead.email} />}
      {convertOpen && <ConvertDialog open onClose={() => setConvertOpen(false)} lead={lead} onConverted={(cid: string) => navigate(`/clients/${cid}`)} />}
      {taskOpen && <TaskModal open onClose={() => setTaskOpen(false)} leadId={lead.id} />}
      {meetingOpen && <MeetingModal open onClose={() => setMeetingOpen(false)} leadId={lead.id} />}

      <Dialog open={noteOpen} onClose={() => setNoteOpen(false)} title="Add note">
        <Textarea value={noteText} onChange={(e) => setNoteText(e.target.value)} placeholder="Write a note — it becomes part of the lead timeline." className="min-h-32" />
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setNoteOpen(false)}>Cancel</Button>
          <Button disabled={!noteText.trim()} loading={addNote.isPending} onClick={() => addNote.mutate()}>Save note</Button>
        </div>
      </Dialog>

      <ConfirmDialog
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        onConfirm={async () => { await api.delete(`/leads/${id}`); toast.push('success', 'Lead deleted'); navigate('/leads') }}
        title="Delete this lead?"
        message="The lead is soft-deleted and hidden from all views. Audit history is preserved."
        confirmLabel="Delete lead"
        danger
      />
      {canDelete && (
        <button onClick={() => setDeleteOpen(true)} className="mt-6 inline-flex items-center gap-1.5 text-sm text-red-500 hover:text-red-600">
          <Trash2 className="h-4 w-4" /> Delete lead
        </button>
      )}
    </div>
  )
}

function Row({ icon: Icon, label, value, href }: { icon: typeof Mail; label: string; value: string | null; href?: string }) {
  if (!value) return null
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="flex items-center gap-2 text-slate-500"><Icon className="h-3.5 w-3.5" /> {label}</span>
      {href ? (
        <a href={href} target={href.startsWith('http') ? '_blank' : undefined} rel="noreferrer" className="max-w-56 truncate font-medium text-blue-600 hover:underline">{value}</a>
      ) : (
        <span className="max-w-56 truncate text-right font-medium text-slate-700">{value}</span>
      )}
    </div>
  )
}

export function DocumentsTab({ docId, documents }: { docId: string; documents?: PageResponse<DocumentItem> }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [file, setFile] = useState<File | null>(null)

  const upload = useMutation({
    mutationFn: async (f: File) => {
      const fd = new FormData()
      fd.append('file', f)
      return api.post('/documents', fd, { params: { leadId: docId } })
    },
    onSuccess: () => { setFile(null); toast.push('success', 'Document uploaded'); qc.invalidateQueries({ queryKey: ['lead-documents', docId] }) },
    onError: (e) => toast.push('error', 'Upload failed', apiError(e).message),
  })

  return (
    <div>
      <div className="mb-3 flex items-center gap-2">
        <input type="file" className="text-sm" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        <Button size="sm" disabled={!file} loading={upload.isPending} onClick={() => file && upload.mutate(file)}>Upload</Button>
        <span className="text-xs text-slate-400">Max 10 MB. Executables are rejected.</span>
      </div>
      {documents && documents.content.length > 0 ? (
        <div className="space-y-1.5">
          {documents.content.map((d) => (
            <div key={d.id} className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 px-3 py-2">
              <div className="flex min-w-0 items-center gap-2">
                <FileText className="h-4 w-4 shrink-0 text-slate-400" />
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-slate-700">{d.name}</p>
                  <p className="text-xs text-slate-400">{(d.sizeBytes / 1024).toFixed(0)} KB · {fmtDateTime(d.createdAt)}</p>
                </div>
              </div>
              <a href={`/api/v1/documents/${d.id}/download`} className="text-xs font-medium text-blue-600 hover:underline">Download</a>
            </div>
          ))}
        </div>
      ) : <EmptyState icon={<FileText className="h-6 w-6" />} title="No documents" subtitle="Upload proposals, contracts, or any supporting file." />}
    </div>
  )
}

function TaskModal({ open, onClose, leadId }: { open: boolean; onClose: () => void; leadId: string }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [title, setTitle] = useState('')
  const [dueAt, setDueAt] = useState('')
  const [priority, setPriority] = useState('MEDIUM')
  const [taskType, setTaskType] = useState('FOLLOW_UP')

  const create = useMutation({
    mutationFn: async () => api.post('/tasks', { title, leadId, dueAt: new Date(dueAt).toISOString(), priority, taskType }),
    onSuccess: () => { toast.push('success', 'Task created'); qc.invalidateQueries({ queryKey: ['lead-tasks', leadId] }); onClose() },
    onError: (e) => toast.push('error', 'Could not create task', apiError(e).message),
  })

  return (
    <Dialog open={open} onClose={onClose} title="Create task">
      <div className="space-y-3">
        <div>
          <Label required>Title</Label>
          <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Follow up on proposal" />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label required>Due</Label>
            <Input type="datetime-local" value={dueAt} onChange={(e) => setDueAt(e.target.value)} />
          </div>
          <div>
            <Label>Priority</Label>
            <Select value={priority} onChange={(e) => setPriority(e.target.value)}>
              {['LOW', 'MEDIUM', 'HIGH', 'URGENT'].map((p) => <option key={p}>{p}</option>)}
            </Select>
          </div>
          <div className="col-span-2">
            <Label>Type</Label>
            <Select value={taskType} onChange={(e) => setTaskType(e.target.value)}>
              {['FOLLOW_UP', 'CALL', 'EMAIL', 'MEETING', 'REMINDER', 'OTHER'].map((p) => <option key={p}>{p}</option>)}
            </Select>
          </div>
        </div>
      </div>
      <div className="mt-5 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button disabled={!title.trim() || !dueAt} loading={create.isPending} onClick={() => create.mutate()}>Create</Button>
      </div>
    </Dialog>
  )
}

function MeetingModal({ open, onClose, leadId }: { open: boolean; onClose: () => void; leadId: string }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [title, setTitle] = useState('')
  const [startAt, setStartAt] = useState('')
  const [duration, setDuration] = useState('30')
  const [link, setLink] = useState('')

  const create = useMutation({
    mutationFn: async () => api.post('/meetings', { title, leadId, startAt: new Date(startAt).toISOString(), durationMinutes: Number(duration), meetingLink: link || null }),
    onSuccess: () => { toast.push('success', 'Meeting scheduled'); qc.invalidateQueries({ queryKey: ['lead-meetings', leadId] }); onClose() },
    onError: (e) => toast.push('error', 'Could not schedule meeting', apiError(e).message),
  })

  return (
    <Dialog open={open} onClose={onClose} title="Schedule meeting">
      <div className="space-y-3">
        <div>
          <Label required>Title</Label>
          <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Demo call" />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label required>Start</Label>
            <Input type="datetime-local" value={startAt} onChange={(e) => setStartAt(e.target.value)} />
          </div>
          <div>
            <Label>Duration (min)</Label>
            <Select value={duration} onChange={(e) => setDuration(e.target.value)}>
              {['15', '30', '45', '60', '90'].map((d) => <option key={d}>{d}</option>)}
            </Select>
          </div>
        </div>
        <div>
          <Label>Meeting link</Label>
          <Input value={link} onChange={(e) => setLink(e.target.value)} placeholder="https://meet.google.com/…" />
        </div>
        <p className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500">The meeting is stored in the CRM and included in the lead timeline. Use “Copy details” from the calendar invite to keep your external calendar in sync. <span className="font-medium">TODO / Integration Required:</span> two-way Google/Outlook calendar sync.</p>
      </div>
      <div className="mt-5 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button disabled={!title.trim() || !startAt} loading={create.isPending} onClick={() => create.mutate()}>Schedule</Button>
      </div>
    </Dialog>
  )
}
