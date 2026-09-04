import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BadgeCheck, Mail, Plus, ShieldCheck, Trash2 } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import type { AccountItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Card, CardBody } from '@/components/ui/Card'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select, Label } from '@/components/ui/Input'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { EmptyState, PageLoader } from '@/components/ui/Misc'

export default function AccountsPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const [addOpen, setAddOpen] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [form, setForm] = useState({ provider: 'SMTP', email: '', displayName: '', smtpHost: '', smtpPort: '587', smtpUsername: '', smtpPassword: '', dailyLimit: '500' })

  const { data, isLoading } = useQuery({ queryKey: ['email-accounts-list'], queryFn: async () => (await api.get<AccountItem[]>('/email-accounts')).data })

  const create = useMutation({
    mutationFn: async () => {
      const body: Record<string, unknown> = { provider: form.provider, email: form.email, displayName: form.displayName || null, dailyLimit: parseInt(form.dailyLimit, 10) || 500 }
      if (form.provider === 'SMTP') {
        body.smtpHost = form.smtpHost
        body.smtpPort = parseInt(form.smtpPort, 10)
        body.smtpUsername = form.smtpUsername
        body.smtpPassword = form.smtpPassword
      }
      return api.post('/email-accounts', body)
    },
    onSuccess: () => { toast.push('success', 'Account connected', 'Run “Verify” to confirm credentials before sending.'); setAddOpen(false); qc.invalidateQueries({ queryKey: ['email-accounts-list'] }) },
    onError: (e) => toast.push('error', 'Could not connect account', apiError(e).message),
  })

  const verify = useMutation({
    mutationFn: async (id: string) => api.post(`/email-accounts/${id}/verify`),
    onSuccess: () => { toast.push('success', 'Account verified'); qc.invalidateQueries({ queryKey: ['email-accounts-list'] }) },
    onError: (e) => toast.push('error', 'Verification failed', apiError(e).message),
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/email-accounts/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['email-accounts-list'] }) },
  })

  if (isLoading) return <PageLoader />

  return (
    <div>
      <PageHeader
        title="Email accounts"
        subtitle="Connected sending identities. SMTP works out of the box; Gmail / Microsoft 365 use OAuth."
        actions={<Button onClick={() => setAddOpen(true)}><Plus className="h-4 w-4" /> Connect account</Button>}
      />

      <div className="mb-4 flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
        <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0" />
        <p>
          <span className="font-semibold">TODO / Integration Required:</span> Gmail &amp; Microsoft 365 OAuth connectors. SMTP is fully functional today;
          OAuth app registration (client ID/secret per organization) is required for Gmail/M365 and is intentionally not stubbed.
        </p>
      </div>

      {data && data.length > 0 ? (
        <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
          {data.map((a) => (
            <Card key={a.id}>
              <CardBody className="flex items-start justify-between gap-3">
                <div className="flex items-start gap-3">
                  <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-50 text-blue-600"><Mail className="h-4 w-4" /></span>
                  <div>
                    <p className="text-sm font-semibold text-slate-800">{a.email}</p>
                    <p className="mt-0.5 text-xs text-slate-500">{a.provider} · daily limit {a.dailyLimit}</p>
                    <div className="mt-1.5 flex gap-1.5">
                      <Badge tone={a.status === 'ACTIVE' || a.verifiedAt ? 'green' : 'yellow'}>{a.verifiedAt ? 'Verified' : a.status}</Badge>
                      {a.smtpHost && <Badge tone="gray">{a.smtpHost}:{a.smtpPort}</Badge>}
                    </div>
                  </div>
                </div>
                <div className="flex gap-1">
                  <Button variant="ghost" size="icon" title="Verify connection" loading={verify.isPending} onClick={() => verify.mutate(a.id)}><BadgeCheck className="h-4 w-4" /></Button>
                  <Button variant="ghost" size="icon" title="Remove" onClick={() => setDeleteId(a.id)}><Trash2 className="h-4 w-4 text-red-400" /></Button>
                </div>
              </CardBody>
            </Card>
          ))}
        </div>
      ) : (
        <div className="card"><EmptyState icon={<Mail className="h-6 w-6" />} title="No sending accounts" subtitle="Connect an SMTP account to start sending tracked email." action={<Button variant="secondary" onClick={() => setAddOpen(true)}>Connect account</Button>} /></div>
      )}

      <Dialog open={addOpen} onClose={() => setAddOpen(false)} title="Connect email account" wide>
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label required>Provider</Label>
              <Select value={form.provider} onChange={(e) => setForm({ ...form, provider: e.target.value })}>
                <option value="SMTP">SMTP (works today)</option>
                <option value="GMAIL" disabled>Gmail OAuth (TODO)</option>
                <option value="M365" disabled>Microsoft 365 OAuth (TODO)</option>
              </Select>
            </div>
            <div>
              <Label required>Email address</Label>
              <Input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} placeholder="sales@yourdomain.com" />
            </div>
            <div>
              <Label>Display name</Label>
              <Input value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} placeholder="Alex from Nexus" />
            </div>
            <div>
              <Label>Daily send limit</Label>
              <Input type="number" value={form.dailyLimit} onChange={(e) => setForm({ ...form, dailyLimit: e.target.value })} />
            </div>
          </div>
          {form.provider === 'SMTP' && (
            <div className="grid grid-cols-2 gap-3 rounded-lg border border-slate-200 bg-slate-50/60 p-3">
              <div>
                <Label required>SMTP host</Label>
                <Input value={form.smtpHost} onChange={(e) => setForm({ ...form, smtpHost: e.target.value })} placeholder="smtp.provider.com" />
              </div>
              <div>
                <Label>Port</Label>
                <Select value={form.smtpPort} onChange={(e) => setForm({ ...form, smtpPort: e.target.value })}>
                  {['25', '465', '587', '2525'].map((p) => <option key={p}>{p}</option>)}
                </Select>
              </div>
              <div>
                <Label required>Username</Label>
                <Input value={form.smtpUsername} onChange={(e) => setForm({ ...form, smtpUsername: e.target.value })} autoComplete="off" />
              </div>
              <div>
                <Label required>Password</Label>
                <Input type="password" value={form.smtpPassword} onChange={(e) => setForm({ ...form, smtpPassword: e.target.value })} autoComplete="new-password" />
              </div>
            </div>
          )}
          <p className="text-xs text-slate-400">Credentials are encrypted at rest with the organization encryption key and never returned by the API.</p>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setAddOpen(false)}>Cancel</Button>
          <Button disabled={!form.email.trim()} loading={create.isPending} onClick={() => create.mutate()}>Connect</Button>
        </div>
      </Dialog>

      <ConfirmDialog open={!!deleteId} onClose={() => setDeleteId(null)} onConfirm={async () => { if (deleteId) await del.mutateAsync(deleteId) }} title="Remove account?" message="The account is disconnected. Sent email history is preserved." confirmLabel="Remove" danger />
    </div>
  )
}
