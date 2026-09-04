import type { ReactNode } from 'react'
import { ChevronLeft, ChevronRight, Inbox, Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from './Button'

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn('h-5 w-5 animate-spin text-slate-400', className)} />
}

export function PageLoader() {
  return <div className="flex h-64 items-center justify-center"><Spinner className="h-8 w-8" /></div>
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded-md bg-slate-200/70', className)} />
}

export function EmptyState({ icon, title, subtitle, action }: { icon?: ReactNode; title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-14 text-center">
      <div className="mb-3 rounded-full bg-slate-100 p-3 text-slate-400">{icon ?? <Inbox className="h-6 w-6" />}</div>
      <h3 className="text-sm font-semibold text-slate-700">{title}</h3>
      {subtitle && <p className="mt-1 max-w-sm text-sm text-slate-500">{subtitle}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function Pagination({ page, totalPages, totalElements, onChange }: { page: number; totalPages: number; totalElements: number; onChange: (p: number) => void }) {
  if (totalElements === 0) return null
  return (
    <div className="flex items-center justify-between border-t border-slate-100 px-4 py-3 text-sm text-slate-500">
      <span>
        Showing <span className="font-medium text-slate-700">{page * 25 + 1}–{Math.min((page + 1) * 25, totalElements)}</span> of{' '}
        <span className="font-medium text-slate-700">{totalElements.toLocaleString()}</span>
      </span>
      <div className="flex items-center gap-1">
        <Button variant="secondary" size="icon" disabled={page === 0} onClick={() => onChange(page - 1)} aria-label="Previous page">
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <span className="px-2 text-xs">
          Page {page + 1} / {Math.max(totalPages, 1)}
        </span>
        <Button variant="secondary" size="icon" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)} aria-label="Next page">
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
    </div>
  )
}

export function Avatar({ name, className }: { name?: string | null; className?: string }) {
  const label = (name ?? '?')
    .split(/\s+/).slice(0, 2).map((p) => p[0]?.toUpperCase() ?? '').join('')
  const hue = ((name ?? '').split('').reduce((a, c) => a + c.charCodeAt(0), 0) * 37) % 360
  return (
    <span
      className={cn('inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-semibold text-white', className)}
      style={{ backgroundColor: `hsl(${hue} 55% 45%)` }}
      title={name ?? undefined}
    >
      {label}
    </span>
  )
}
