import type { ReactNode } from 'react'
import {
  Phone, Mail, CheckSquare, FileText, StickyNote, Upload, ArrowRightLeft,
  Bot, Megaphone, Handshake, Briefcase, UserPlus, CalendarDays, Sparkles,
} from 'lucide-react'
import type { ActivityItem } from '@/types'
import { fmtAgo, fmtDateTime } from '@/lib/utils'
import { cn } from '@/lib/utils'

const icons: Record<string, typeof Phone> = {
  CALL: Phone,
  EMAIL: Mail,
  TASK: CheckSquare,
  MEETING: CalendarDays,
  NOTE: StickyNote,
  FILE: FileText,
  IMPORT: Upload,
  STAGE_CHANGE: ArrowRightLeft,
  STATUS_CHANGE: ArrowRightLeft,
  ASSIGNMENT: UserPlus,
  CONVERSION: Briefcase,
  DEAL: Handshake,
  CAMPAIGN: Megaphone,
  AUTOMATION: Bot,
  AI: Sparkles,
  SCORE_CHANGE: Sparkles,
  PROPOSAL: FileText,
}

const colors: Record<string, string> = {
  CALL: 'bg-blue-100 text-blue-600',
  EMAIL: 'bg-violet-100 text-violet-600',
  TASK: 'bg-emerald-100 text-emerald-600',
  NOTE: 'bg-amber-100 text-amber-600',
  STAGE_CHANGE: 'bg-cyan-100 text-cyan-600',
  CONVERSION: 'bg-emerald-100 text-emerald-600',
}

export function Timeline({ items, empty }: { items?: ActivityItem[]; empty?: ReactNode }) {
  if (!items || items.length === 0) return <>{empty ?? <p className="py-8 text-center text-sm text-slate-400">No activity yet.</p>}</>
  return (
    <ol className="relative space-y-5 border-l border-slate-200 pl-5">
      {items.map((a) => {
        const Icon = icons[a.type] ?? StickyNote
        return (
          <li key={a.id} className="relative">
            <span className={cn('absolute -left-[31px] flex h-6 w-6 items-center justify-center rounded-full ring-4 ring-white', colors[a.type] ?? 'bg-slate-100 text-slate-500')}>
              <Icon className="h-3 w-3" />
            </span>
            <div className="flex items-baseline justify-between gap-2">
              <p className="text-sm font-medium text-slate-800">
                {a.subject ?? a.type.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())}
                {a.actorName && <span className="ml-1.5 text-xs font-normal text-slate-400">· {a.actorName}</span>}
              </p>
              <time className="shrink-0 text-[11px] text-slate-400" title={fmtDateTime(a.occurredAt)}>{fmtAgo(a.occurredAt)}</time>
            </div>
            {a.body && <p className="mt-0.5 whitespace-pre-wrap text-sm text-slate-500">{a.body}</p>}
          </li>
        )
      })}
    </ol>
  )
}
