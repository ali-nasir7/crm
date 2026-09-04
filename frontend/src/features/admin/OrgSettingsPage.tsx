import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Settings } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useAuth } from '@/stores/auth'
import { useToast } from '@/components/ui/Toast'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input, Label } from '@/components/ui/Input'
import { PageLoader } from '@/components/ui/Misc'

interface OrgSettings { name: string; slug: string; timezone: string; locale: string; currency: string; custom?: Record<string, string> }

export default function OrgSettingsPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const { refreshMe } = useAuth()
  const [form, setForm] = useState<OrgSettings | null>(null)

  const { data: org } = useQuery({ queryKey: ['org'], queryFn: async () => (await api.get<OrgSettings>('/org')).data })
  const { data: settings } = useQuery({ queryKey: ['settings'], queryFn: async () => (await api.get<OrgSettings>('/settings')).data })

  useEffect(() => {
    if (settings) setForm({ ...settings, name: org?.name ?? settings.name })
  }, [org, settings])

  const save = useMutation({
    mutationFn: async () => api.put('/settings', form),
    onSuccess: () => { toast.push('success', 'Settings saved'); refreshMe(); qc.invalidateQueries({ queryKey: ['org'] }); qc.invalidateQueries({ queryKey: ['settings'] }) },
    onError: (e) => toast.push('error', 'Save failed', apiError(e).message),
  })

  if (!form) return <PageLoader />

  return (
    <div>
      <PageHeader title="Organization settings" subtitle="Identity and defaults for your workspace." />
      <div className="max-w-2xl space-y-4">
        <Card>
          <CardHeader title="Organization" subtitle={form.slug ? `Slug: ${form.slug}` : undefined} />
          <CardBody className="space-y-3">
            <div>
              <Label required>Organization name</Label>
              <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label>Default currency</Label>
                <Input value={form.currency} onChange={(e) => setForm({ ...form, currency: e.target.value })} placeholder="USD" />
              </div>
              <div>
                <Label>Timezone</Label>
                <Input value={form.timezone} onChange={(e) => setForm({ ...form, timezone: e.target.value })} placeholder="Asia/Dubai" />
              </div>
            </div>
            <div>
              <Label>Locale</Label>
              <Input value={form.locale} onChange={(e) => setForm({ ...form, locale: e.target.value })} placeholder="en" />
            </div>
            <div className="flex justify-end">
              <Button loading={save.isPending} onClick={() => save.mutate()}>Save settings</Button>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Data isolation" />
          <CardBody className="space-y-2 text-sm text-slate-600">
            <p>Every record carries your organization id, enforced by a Hibernate filter bound to your session — queries can never cross tenants, even if code forgets a WHERE clause.</p>
            <p className="text-xs text-slate-400">Audit log keeps proof of every settings change. Tenant-level encryption keys protect email credentials and stored files.</p>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Integrations status" />
          <CardBody className="space-y-2 text-sm">
            <IntegrationRow name="SMTP email" status="Available" note="Configure accounts under Emails → Accounts." />
            <IntegrationRow name="Gmail / M365 OAuth" status="TODO / Integration Required" note="Requires OAuth app registration per deployment." />
            <IntegrationRow name="AI assistant" status="Env-based" note="Set CRM_AI_API_KEY to enable OpenAI-compatible providers; otherwise a deterministic rule-based assistant is used." />
            <IntegrationRow name="S3 document storage" status="TODO / Integration Required" note="Local storage is active. Set storage config for S3-compatible providers." />
          </CardBody>
        </Card>
      </div>
    </div>
  )
}

function IntegrationRow({ name, status, note }: { name: string; status: string; note: string }) {
  const ok = status === 'Available' || status.startsWith('Set')
  return (
    <div className="flex items-start justify-between gap-3 rounded-lg border border-slate-100 px-3 py-2.5">
      <div>
        <p className="font-medium text-slate-700">{name}</p>
        <p className="text-xs text-slate-400">{note}</p>
      </div>
      <span className={`shrink-0 rounded-full px-2 py-0.5 text-[11px] font-semibold ${ok ? 'bg-emerald-50 text-emerald-600' : 'bg-amber-50 text-amber-600'}`}>{status}</span>
    </div>
  )
}
