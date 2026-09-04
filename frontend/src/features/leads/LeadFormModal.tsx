import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select, Textarea, Label, FieldError } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import type { CustomFieldItem, LeadItem, PipelineItem, SourceItem, TagItem, UserItem, PageResponse } from '@/types'

const COUNTRIES = ['UAE', 'Saudi Arabia', 'Qatar', 'Kuwait', 'Oman', 'Bahrain', 'Egypt', 'Jordan', 'Other']

export function LeadFormModal({ open, onClose, lead, onSaved }: {
  open: boolean
  onClose: () => void
  lead?: LeadItem | null
  onSaved?: (id: string) => void
}) {
  const qc = useQueryClient()
  const toast = useToast()
  const [form, setForm] = useState<Record<string, string>>({})
  const [custom, setCustom] = useState<Record<string, string>>({})
  const [tags, setTags] = useState<string[]>([])
  const [errors, setErrors] = useState<Record<string, string>>({})

  const { data: fields } = useQuery({ queryKey: ['custom-fields'], queryFn: async () => (await api.get<CustomFieldItem[]>('/custom-fields')).data, enabled: open })
  const { data: sources } = useQuery({ queryKey: ['sources'], queryFn: async () => (await api.get<SourceItem[]>('/lead-sources')).data, enabled: open })
  const { data: pipelines } = useQuery({ queryKey: ['pipelines'], queryFn: async () => (await api.get<PipelineItem[]>('/pipelines')).data, enabled: open })
  const { data: users } = useQuery({ queryKey: ['users-lite'], queryFn: async () => (await api.get<PageResponse<UserItem>>('/users', { params: { size: 200 } })).data, enabled: open })
  const { data: allTags } = useQuery({ queryKey: ['tags'], queryFn: async () => (await api.get<TagItem[]>('/tags')).data, enabled: open })

  useEffect(() => {
    if (!open) return
    setErrors({})
    if (lead) {
      setForm({
        businessName: lead.businessName ?? '',
        firstName: lead.firstName ?? '',
        lastName: lead.lastName ?? '',
        jobTitle: lead.jobTitle ?? '',
        email: lead.email ?? '',
        phone: lead.phone ?? '',
        whatsapp: lead.whatsapp ?? '',
        website: lead.website ?? '',
        linkedin: lead.linkedin ?? '',
        country: lead.country ?? '',
        city: lead.city ?? '',
        industry: lead.industry ?? '',
        businessType: lead.businessType ?? '',
        companySize: lead.companySize ?? '',
        notes: lead.notes ?? '',
        sourceId: lead.sourceId ?? '',
        assignedUserId: lead.assignedUserId ?? '',
        pipelineId: lead.pipelineId ?? '',
        stageId: lead.stageId ?? '',
      })
      setTags(lead.tags ?? [])
      setCustom(Object.fromEntries(Object.entries(lead.customFields ?? {}).map(([k, v]) => [k, v == null ? '' : String(v)])))
    } else {
      setForm({ businessName: '', firstName: '', lastName: '', jobTitle: '', email: '', phone: '', whatsapp: '', website: '', linkedin: '', country: '', city: '', industry: '', businessType: '', companySize: '', notes: '', sourceId: '', assignedUserId: '', pipelineId: '', stageId: '' })
      setTags([])
      setCustom({})
    }
  }, [open, lead])

  const set = (k: string, v: string) => setForm((f) => ({ ...f, [k]: v }))

  const save = useMutation({
    mutationFn: async () => {
      const customFields = Object.fromEntries(Object.entries(custom).filter(([, v]) => v !== ''))
      const body = { ...form, tags, customFields }
      if (lead) return (await api.put<LeadItem>(`/leads/${lead.id}`, body)).data
      return (await api.post<LeadItem>('/leads', body)).data
    },
    onSuccess: (saved) => {
      toast.push('success', lead ? 'Lead updated' : 'Lead created')
      qc.invalidateQueries({ queryKey: ['leads'] })
      qc.invalidateQueries({ queryKey: ['lead', saved.id] })
      onSaved?.(saved.id)
      onClose()
    },
    onError: (e) => {
      const err = apiError(e)
      if (err.details) setErrors(err.details)
      toast.push('error', lead ? 'Update failed' : 'Could not create lead', err.message)
    },
  })

  const submit = () => {
    const errs: Record<string, string> = {}
    if (!form.businessName.trim()) errs.businessName = 'Business name is required'
    if (form.email && !/^\S+@\S+\.\S+$/.test(form.email)) errs.email = 'Enter a valid email'
    setErrors(errs)
    if (Object.keys(errs).length === 0) save.mutate()
  }

  const defaultPipeline = pipelines?.find((p) => p.isDefault) ?? pipelines?.[0]
  const stages = pipelines?.find((p) => p.id === (form.pipelineId || defaultPipeline?.id))?.stages ?? []

  return (
    <Dialog open={open} onClose={onClose} title={lead ? 'Edit lead' : 'New lead'} description="Core identity is used for duplicate detection on import." wide>
      <div className="space-y-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <Label required>Business name</Label>
            <Input value={form.businessName} onChange={(e) => set('businessName', e.target.value)} placeholder="e.g. Bright Smile Dental Clinic" />
            <FieldError message={errors.businessName} />
          </div>
          <div>
            <Label>Industry</Label>
            <Input value={form.industry} onChange={(e) => set('industry', e.target.value)} placeholder="e.g. Healthcare" />
          </div>
          <div>
            <Label>First name</Label>
            <Input value={form.firstName} onChange={(e) => set('firstName', e.target.value)} placeholder="Contact first name" />
          </div>
          <div>
            <Label>Last name</Label>
            <Input value={form.lastName} onChange={(e) => set('lastName', e.target.value)} />
          </div>
          <div>
            <Label>Job title</Label>
            <Input value={form.jobTitle} onChange={(e) => set('jobTitle', e.target.value)} />
          </div>
          <div>
            <Label>Email</Label>
            <Input type="email" value={form.email} onChange={(e) => set('email', e.target.value)} />
            <FieldError message={errors.email} />
          </div>
          <div>
            <Label>Phone</Label>
            <Input value={form.phone} onChange={(e) => set('phone', e.target.value)} placeholder="+971 50 123 4567" />
            <FieldError message={errors.phone} />
          </div>
          <div>
            <Label>WhatsApp</Label>
            <Input value={form.whatsapp} onChange={(e) => set('whatsapp', e.target.value)} />
          </div>
          <div>
            <Label>Website</Label>
            <Input value={form.website} onChange={(e) => set('website', e.target.value)} placeholder="clinic.com" />
          </div>
          <div>
            <Label>LinkedIn</Label>
            <Input value={form.linkedin} onChange={(e) => set('linkedin', e.target.value)} />
          </div>
          <div>
            <Label>Country</Label>
            <Select value={form.country} onChange={(e) => set('country', e.target.value)}>
              <option value="">Select…</option>
              {COUNTRIES.map((c) => <option key={c} value={c}>{c}</option>)}
            </Select>
          </div>
          <div>
            <Label>City</Label>
            <Input value={form.city} onChange={(e) => set('city', e.target.value)} />
          </div>
          <div>
            <Label>Business type</Label>
            <Input value={form.businessType} onChange={(e) => set('businessType', e.target.value)} placeholder="e.g. Clinic / Trading" />
          </div>
          <div>
            <Label>Company size</Label>
            <Select value={form.companySize} onChange={(e) => set('companySize', e.target.value)}>
              <option value="">Select…</option>
              {['1-10', '11-50', '51-200', '201-500', '500+'].map((s) => <option key={s} value={s}>{s}</option>)}
            </Select>
          </div>
          <div>
            <Label>Source</Label>
            <Select value={form.sourceId} onChange={(e) => set('sourceId', e.target.value)}>
              <option value="">Select…</option>
              {sources?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </Select>
          </div>
          <div>
            <Label>Owner</Label>
            <Select value={form.assignedUserId} onChange={(e) => set('assignedUserId', e.target.value)}>
              <option value="">Unassigned</option>
              {users?.content.map((u) => <option key={u.id} value={u.id}>{u.displayName}</option>)}
            </Select>
          </div>
          {!lead && (
            <>
              <div>
                <Label>Pipeline</Label>
                <Select
                  value={form.pipelineId || defaultPipeline?.id || ''}
                  onChange={(e) => set('pipelineId', e.target.value)}
                >
                  {pipelines?.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                </Select>
              </div>
              <div>
                <Label>Stage</Label>
                <Select value={form.stageId || stages[0]?.id || ''} onChange={(e) => set('stageId', e.target.value)}>
                  {stages.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </Select>
              </div>
            </>
          )}
        </div>

        {/* Custom fields */}
        {fields && fields.length > 0 && (
          <div className="rounded-lg border border-slate-200 bg-slate-50/60 p-3">
            <p className="mb-2 text-xs font-bold uppercase tracking-wide text-slate-500">Custom fields</p>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {(fields ?? []).map((f) => (
                <div key={f.key}>
                  <Label>{f.label}</Label>
                  {f.type === 'SELECT' && f.options ? (
                    <Select value={custom[f.key] ?? ''} onChange={(e) => setCustom((c) => ({ ...c, [f.key]: e.target.value }))}>
                      <option value="">Select…</option>
                      {(f.options ?? []).map((o) => <option key={o} value={o}>{o}</option>)}
                    </Select>
                  ) : f.type === 'NUMBER' ? (
                    <Input type="number" value={custom[f.key] ?? ''} onChange={(e) => setCustom((c) => ({ ...c, [f.key]: e.target.value }))} />
                  ) : f.type === 'BOOLEAN' ? (
                    <Select value={custom[f.key] ?? ''} onChange={(e) => setCustom((c) => ({ ...c, [f.key]: e.target.value }))}>
                      <option value="">—</option>
                      <option value="true">Yes</option>
                      <option value="false">No</option>
                    </Select>
                  ) : (
                    <Input value={custom[f.key] ?? ''} onChange={(e) => setCustom((c) => ({ ...c, [f.key]: e.target.value }))} />
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        <div>
          <Label>Tags</Label>
          <div className="flex flex-wrap items-center gap-1.5">
            {(tags ?? []).map((t) => (
              <button key={t} onClick={() => setTags(tags.filter((x) => x !== t))} className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700 ring-1 ring-blue-200 hover:bg-blue-100">
                {t} ×
              </button>
            ))}
            <Select value="" className="h-7 w-36 !py-0 text-xs" onChange={(e) => { if (e.target.value && !tags.includes(e.target.value)) setTags([...tags, e.target.value]) }}>
              <option value="">+ Add tag</option>
              {allTags?.filter((t) => !tags.includes(t.name)).map((t) => <option key={t.id} value={t.name}>{t.name}</option>)}
            </Select>
          </div>
        </div>

        <div>
          <Label>Notes</Label>
          <Textarea value={form.notes} onChange={(e) => set('notes', e.target.value)} placeholder="Anything worth remembering about this lead…" />
        </div>

        <div className="flex justify-end gap-2 border-t border-slate-100 pt-4">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={save.isPending} onClick={submit}>{lead ? 'Save changes' : 'Create lead'}</Button>
        </div>
      </div>
    </Dialog>
  )
}
