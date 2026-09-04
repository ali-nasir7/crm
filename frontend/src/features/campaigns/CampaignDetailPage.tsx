import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import  { ArrowLeft, Pause, Play, Upload, XCircle }  from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import type { CampaignItem } from '@/types'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Dialog } from '@/components/ui/Dialog'
import  { Textarea }  from '@/components/ui/Input'
import { PageLoader, EmptyState } from '@/components/ui/Misc'
import { Table, THead, TR, TH, TD } from '@/components/ui/Table'
import { fmtDateTime } from '@/lib/utils'

interface Recipient { id: string; email: string; leadId: string | null; businessName: string | null; status: string; nextSendAt: string | null; stepIndex: number; lastError: string | null }

export default function CampaignDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const [addOpen, setAddOpen] = useState(false)
  const [emailsText, setEmailsText] = useState('')

  const { data: campaign, isLoading } = useQuery({
    queryKey: ['campaign', id],
    queryFn: async () => (await api.get<CampaignItem>(`/campaigns/${id}`)).data,
    enabled: !!id,
    refetchInterval: (q) => (q.state.data?.status === 'RUNNING' ? 5000 : false),
  })

  const { data: recipients } = useQuery({
    queryKey: ['campaign-recipients', id],
    queryFn: async () => (await api.get<Recipient[]>(`/campaigns/${id}/recipients`)).data,
    enabled: !!id,
  })

  const act = useMutation({
    mutationFn: async (action: 'start' | 'pause' | 'resume' | 'cancel') => api.post(`/campaigns/${id}/${action}`),
    onSuccess: (_, action) => { toast.push('success', `Campaign ${action}ed`); qc.invalidateQueries({ queryKey: ['campaign', id] }) },
    onError: (e) => toast.push('error', 'Campaign action failed', apiError(e).message),
  })

  const addRecipients = useMutation({
    mutationFn: async () => {
      const emails = emailsText.split(/[\n,;]+/).map((e) => e.trim().toLowerCase()).filter((e) => /.+@.+\..+/.test(e))
      return api.post(`/campaigns/${id}/recipients`, { emails })
    },
    onSuccess: (res) => {
      const data = res.data as { added: number; skipped: number }
      toast.push('success', `${data.added} recipients added`, data.skipped > 0 ? `${data.skipped} skipped (invalid or suppressed).` : undefined)
      setAddOpen(false)
      setEmailsText('')
      qc.invalidateQueries({ queryKey: ['campaign-recipients', id] })
      qc.invalidateQueries({ queryKey: ['campaign', id] })
    },
    onError: (e) => toast.push('error', 'Could not add recipients', apiError(e).message),
  })

  if (isLoading || !campaign) return <PageLoader />

  const openRates = campaign.sentCount > 0 ? ((campaign.openCount / campaign.sentCount) * 100).toFixed(1) : '0.0'
  const replyRates = campaign.sentCount > 0 ? ((campaign.replyCount / campaign.sentCount) * 100).toFixed(1) : '0.0'

  return (
    <div>
      <button onClick={() => navigate('/campaigns')} className="mb-3 inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700">
        <ArrowLeft className="h-4 w-4" /> Back to campaigns
      </button>

      <div className="card mb-4 p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-bold text-slate-900">{campaign.name}</h1>
              <Badge tone="blue">{campaign.status}</Badge>
            </div>
            <p className="mt-1 text-sm text-slate-500">{campaign.description}</p>
          </div>
          <div className="flex flex-wrap gap-2">
            {campaign.status === 'DRAFT' && <Button onClick={() => act.mutate('start')} loading={act.isPending}><Play className="h-4 w-4" /> Start</Button>}
            {campaign.status === 'RUNNING' && <Button variant="secondary" onClick={() => act.mutate('pause')}><Pause className="h-4 w-4" /> Pause</Button>}
            {campaign.status === 'PAUSED' && <Button onClick={() => act.mutate('resume')}><Play className="h-4 w-4" /> Resume</Button>}
            {['DRAFT', 'RUNNING', 'PAUSED', 'SCHEDULED'].includes(campaign.status) && (
              <Button variant="danger" onClick={() => act.mutate('cancel')}><XCircle className="h-4 w-4" /> Cancel</Button>
            )}
            <Button variant="secondary" onClick={() => setAddOpen(true)}><Upload className="h-4 w-4" /> Add recipients</Button>
          </div>
        </div>
        <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-6">
          <Stat label="Recipients" value={campaign.totalRecipients.toLocaleString()} />
          <Stat label="Sent" value={campaign.sentCount.toLocaleString()} />
          <Stat label="Opens" value={`${campaign.openCount.toLocaleString()} (${openRates}%)`} />
          <Stat label="Replies" value={`${campaign.replyCount.toLocaleString()} (${replyRates}%)`} />
          <Stat label="Bounces" value={campaign.bounceCount.toLocaleString()} />
          <Stat label="Unsubscribed" value={campaign.unsubscribeCount.toLocaleString()} />
        </div>
        <div className="mt-3 flex flex-wrap gap-2 text-xs text-slate-500">
          {campaign.steps.map((s, i) => (
            <span key={s.id} className="rounded-full bg-slate-100 px-2.5 py-1">Step {i + 1}: day +{s.delayDays}</span>
          ))}
        </div>
      </div>

      <Card>
        <CardHeader title="Recipients" subtitle="Suppressed addresses are blocked at dispatch time — they can't be added or sent to." />
        <CardBody className="!px-0">
          {recipients && recipients.length > 0 ? (
            <Table>
              <THead><TR><TH>Email</TH><TH>Lead</TH><TH>Step</TH><TH>Status</TH><TH>Next send</TH><TH>Last error</TH></TR></THead>
              <tbody>
                {(recipients ?? []).map((r) => (
                  <TR key={r.id}>
                    <TD className="font-medium text-slate-700">{r.email}</TD>
                    <TD>{r.leadId ? <button className="text-blue-600 hover:underline" onClick={() => navigate(`/leads/${r.leadId}`)}>{r.businessName ?? 'View lead'}</button> : '—'}</TD>
                    <TD className="tabular-nums">#{r.stepIndex + 1}</TD>
                    <TD><Badge tone={r.status === 'SENT' ? 'green' : r.status === 'OPENED' || r.status === 'REPLIED' ? 'blue' : r.status === 'BOUNCED' ? 'red' : r.status === 'SUPPRESSED' ? 'gray' : 'yellow'}>{r.status}</Badge></TD>
                    <TD className="text-slate-500">{r.nextSendAt ? fmtDateTime(r.nextSendAt) : '—'}</TD>
                    <TD className="max-w-48 truncate text-xs text-red-500">{r.lastError ?? ''}</TD>
                  </TR>
                ))}
              </tbody>
            </Table>
          ) : (
            <EmptyState title="No recipients" subtitle="Paste email addresses or import leads first, then add them by email." />
          )}
        </CardBody>
      </Card>

      <Dialog open={addOpen} onClose={() => setAddOpen(false)} title="Add recipients" description="One email per line, or comma-separated. Duplicates and suppressed addresses are skipped.">
        <Textarea value={emailsText} onChange={(e) => setEmailsText(e.target.value)} className="min-h-40 font-mono text-xs" placeholder={'buyer@clinic1.com\nmanager@clinic2.com'} />
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setAddOpen(false)}>Cancel</Button>
          <Button disabled={!emailsText.trim()} loading={addRecipients.isPending} onClick={() => addRecipients.mutate()}>Add recipients</Button>
        </div>
      </Dialog>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-slate-50 px-3 py-2">
      <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">{label}</p>
      <p className="mt-0.5 text-base font-bold tabular-nums text-slate-800">{value}</p>
    </div>
  )
}
