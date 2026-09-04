import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { PhoneCall } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import { Dialog } from '@/components/ui/Dialog'
import { Button } from '@/components/ui/Button'
import { Input, Select, Textarea, Label } from '@/components/ui/Input'

const OUTCOMES = ['CONNECTED', 'NO_ANSWER', 'VOICEMAIL', 'BUSY', 'WRONG_NUMBER', 'CALLBACK_REQUESTED']
const DIRECTIONS = ['OUTBOUND', 'INBOUND']

export function LogCallModal({ open, onClose, leadId, leadName }: { open: boolean; onClose: () => void; leadId: string; leadName?: string }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [outcome, setOutcome] = useState('CONNECTED')
  const [direction, setDirection] = useState('OUTBOUND')
  const [duration, setDuration] = useState('')
  const [notes, setNotes] = useState('')
  const [nextAction, setNextAction] = useState('')
  const [followUpAt, setFollowUpAt] = useState('')

  const log = useMutation({
    mutationFn: async () =>
      (
        await api.post(`/leads/${leadId}/calls`, {
          outcome,
          direction,
          durationSeconds: duration ? parseInt(duration, 10) : null,
          notes: notes || null,
          nextAction: nextAction || null,
          followUpAt: followUpAt ? new Date(followUpAt).toISOString() : null,
        })
      ).data,
    onSuccess: () => {
      toast.push('success', 'Call logged')
      qc.invalidateQueries({ queryKey: ['lead-calls', leadId] })
      qc.invalidateQueries({ queryKey: ['lead-timeline', leadId] })
      qc.invalidateQueries({ queryKey: ['lead', leadId] })
      qc.invalidateQueries({ queryKey: ['calls'] })
      onClose()
    },
    onError: (e) => toast.push('error', 'Could not log call', apiError(e).message),
  })

  return (
    <Dialog open={open} onClose={onClose} title={`Log call — ${leadName ?? 'lead'}`}>
      <div className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label required>Outcome</Label>
            <Select value={outcome} onChange={(e) => setOutcome(e.target.value)}>
              {OUTCOMES.map((o) => <option key={o}>{o}</option>)}
            </Select>
          </div>
          <div>
            <Label>Direction</Label>
            <Select value={direction} onChange={(e) => setDirection(e.target.value)}>
              {DIRECTIONS.map((d) => <option key={d}>{d}</option>)}
            </Select>
          </div>
          <div>
            <Label>Duration (seconds)</Label>
            <Input type="number" min="0" value={duration} onChange={(e) => setDuration(e.target.value)} placeholder="e.g. 340" />
          </div>
          <div>
            <Label>Follow-up at</Label>
            <Input type="datetime-local" value={followUpAt} onChange={(e) => setFollowUpAt(e.target.value)} />
          </div>
        </div>
        <div>
          <Label>Notes</Label>
          <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="What was discussed, objections, sentiment…" />
        </div>
        <div>
          <Label>Next action</Label>
          <Input value={nextAction} onChange={(e) => setNextAction(e.target.value)} placeholder="e.g. Send pricing on Thursday" />
        </div>
        <p className="text-xs text-slate-400">Logging a call updates the lead's “last contacted” timestamp and appears in the timeline. NO_REPLY automation triggers may fire from email activity, not calls.</p>
      </div>
      <div className="mt-5 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button loading={log.isPending} onClick={() => log.mutate()}><PhoneCall className="h-4 w-4" /> Save call</Button>
      </div>
    </Dialog>
  )
}
