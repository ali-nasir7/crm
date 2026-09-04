import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Star } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import { Dialog } from '@/components/ui/Dialog'
import { Button } from '@/components/ui/Button'
import { Input, Select, Label } from '@/components/ui/Input'
import type { LeadItem, PipelineItem } from '@/types'

export function ConvertDialog({ open, onClose, lead, onConverted }: {
  open: boolean
  onClose: () => void
  lead: LeadItem
  onConverted?: (clientId: string) => void
}) {
  const qc = useQueryClient()
  const toast = useToast()
  const [dealStageId, setDealStageId] = useState('')
  const [amount, setAmount] = useState('')
  const [currency, setCurrency] = useState('USD')

  const { data: pipelines } = useQuery({ queryKey: ['pipelines'], queryFn: async () => (await api.get<PipelineItem[]>('/pipelines')).data, enabled: open })
  const stages = (pipelines?.find((p) => p.isDefault) ?? pipelines?.[0])?.stages ?? []

  const convert = useMutation({
    mutationFn: async () =>
      (
        await api.post<{ clientId: string; companyId: string; contactId: string; dealId: string }>(`/leads/${lead.id}/convert`, {
          dealStageId: dealStageId || null,
          amount: amount ? parseFloat(amount) : null,
          currency: currency || null,
        })
      ).data,
    onSuccess: (res) => {
      toast.push('success', `${lead.businessName} converted`, 'Company, contact, and client were created. History is preserved on the original lead.')
      qc.invalidateQueries({ queryKey: ['leads'] })
      qc.invalidateQueries({ queryKey: ['clients'] })
      qc.invalidateQueries({ queryKey: ['lead', lead.id] })
      onConverted?.(res.clientId)
      onClose()
    },
    onError: (e) => toast.push('error', 'Conversion failed', apiError(e).message),
  })

  return (
    <Dialog open={open} onClose={onClose} title={`Convert “${lead.businessName}” to client`}
      description="Creates a company, a person contact, and a client record. The lead is marked CONVERTED and its full history stays intact.">
      <div className="space-y-3">
        <div>
          <Label>Opening deal stage (optional)</Label>
          <Select value={dealStageId} onChange={(e) => setDealStageId(e.target.value)}>
            <option value="">No deal</option>
            {stages.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </Select>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label>Deal value</Label>
            <Input type="number" min="0" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="e.g. 12000" />
          </div>
          <div>
            <Label>Currency</Label>
            <Select value={currency} onChange={(e) => setCurrency(e.target.value)}>
              {['USD', 'EUR', 'GBP', 'AED', 'SAR'].map((c) => <option key={c}>{c}</option>)}
            </Select>
          </div>
        </div>
        <p className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500">
          A duplicate check runs on company website/name — if the company already exists in your organization, the client links to it instead of creating a copy.
        </p>
      </div>
      <div className="mt-5 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button loading={convert.isPending} onClick={() => convert.mutate()}><Star className="h-4 w-4" /> Convert to client</Button>
      </div>
    </Dialog>
  )
}
