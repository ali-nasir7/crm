import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Send } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import { Dialog } from '@/components/ui/Dialog'
import { Button } from '@/components/ui/Button'
import { Input, Select, Textarea, Label } from '@/components/ui/Input'
import type { AccountItem, TemplateItem } from '@/types'

export function EmailComposer({ open, onClose, leadId, leadName, toEmail }: {
  open: boolean
  onClose: () => void
  leadId: string
  leadName?: string
  toEmail?: string | null
}) {
  const qc = useQueryClient()
  const toast = useToast()
  const [accountId, setAccountId] = useState('')
  const [subject, setSubject] = useState('')
  const [body, setBody] = useState('')
  const [templateId, setTemplateId] = useState('')

  const { data: accounts } = useQuery({ queryKey: ['email-accounts'], queryFn: async () => (await api.get<AccountItem[]>('/email-accounts')).data, enabled: open })
  // GET /email-templates returns a bare array (NOT a PageResponse) - typing it as PageResponse
  // made `templates?.content` undefined and crashed the composer with "Cannot read properties of undefined (reading 'map')".
  const { data: templates } = useQuery({ queryKey: ['email-templates'], queryFn: async () => (await api.get<TemplateItem[]>('/email-templates', { params: { size: 100 } })).data, enabled: open })

  const accountList = accounts ?? []
  // Prefer a VERIFIED sender; fall back to the first account. Never send without one -
  // the backend rejects a missing accountId with a 400, so block it here with guidance.
  useEffect(() => {
    if (accountList.length > 0 && !accountId) {
      const verified = accountList.find((a) => a.status === 'VERIFIED')
      setAccountId((verified ?? accountList[0]).id)
    }
  }, [accountList, accountId])
  const noAccount = accountList.length === 0
  const selectedAccount = accountList.find((a) => a.id === accountId)

  const applyTemplate = useMutation({
    mutationFn: async (id: string) => (await api.post<{ subject: string; bodyHtml: string | null }>(`/email-templates/${id}/render`, { leadId })).data,
    onSuccess: (res) => { setSubject(res.subject); setBody(res.bodyHtml ?? '') },
    onError: (e) => toast.push('error', 'Could not render template', apiError(e).message),
  })

  const send = useMutation({
    mutationFn: async () =>
      (await api.post(`/leads/${leadId}/emails`, { accountId: accountId || null, subject, bodyHtml: body || null, bodyText: body || null })).data,
    onSuccess: () => {
      toast.push('success', 'Email sent', 'Open and reply tracking is active.')
      qc.invalidateQueries({ queryKey: ['lead-emails', leadId] })
      qc.invalidateQueries({ queryKey: ['lead-timeline', leadId] })
      qc.invalidateQueries({ queryKey: ['lead', leadId] })
      onClose()
    },
    onError: (e) => toast.push('error', 'Send failed', apiError(e).message),
  })

  return (
    <Dialog open={open} onClose={onClose} title={`Email — ${leadName ?? ''}`} description={toEmail ?? 'This lead has no email address on file.'} wide>
      <div className="space-y-3">
        {noAccount && (
          <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
            No sender email account is configured yet. Add and verify one under <strong>Emails &gt; Accounts</strong> first - sending is disabled until then.
          </p>
        )}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label required>Send from</Label>
            <Select value={accountId} onChange={(e) => setAccountId(e.target.value)} disabled={noAccount}>
              {accountList.length > 0 ? (
                accountList.map((a) => <option key={a.id} value={a.id}>{a.email} ({a.provider}){a.status !== 'VERIFIED' ? ' - not verified' : ''}</option>)
              ) : (
                <option value="">No account configured</option>
              )}
            </Select>
            {selectedAccount && selectedAccount.status !== 'VERIFIED' && (
              <p className="mt-1 text-[11px] text-amber-600">This account is not verified - use Emails &gt; Accounts &gt; Verify for reliable sending.</p>
            )}
          </div>
          <div>
            <Label>Start from template</Label>
            <Select value={templateId} onChange={(e) => { setTemplateId(e.target.value); if (e.target.value) applyTemplate.mutate(e.target.value) }}>
              <option value="">Blank…</option>
              {templates?.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
            </Select>
          </div>
        </div>
        <div>
          <Label required>Subject</Label>
          <Input value={subject} onChange={(e) => setSubject(e.target.value)} placeholder="Subject line" />
        </div>
        <div>
          <Label>Body (HTML supported)</Label>
          <Textarea value={body} onChange={(e) => setBody(e.target.value)} className="min-h-48 font-mono text-xs" placeholder={'Hi {{first_name}},\n\n…'} />
          <p className="mt-1 text-xs text-slate-400">Template variables like <code>{'{{first_name}}'}</code> are filled from lead data. Every send is logged to the audit trail; unsubscribe and suppression lists are always respected.</p>
        </div>
      </div>
      <div className="mt-5 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button disabled={!accountId || !subject.trim() || !toEmail} loading={send.isPending} onClick={() => send.mutate()}><Send className="h-4 w-4" /> Send</Button>
      </div>
    </Dialog>
  )
}
