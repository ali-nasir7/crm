import { isValidElement, useState, type ReactNode } from 'react'
import { AlertTriangle, RotateCcw } from 'lucide-react'
import type { PageResponse } from '@/types'
import { Pagination, EmptyState, Skeleton } from '@/components/ui/Misc'
import { Table, THead, TR, TH, TD } from '@/components/ui/Table'
import { Checkbox } from '@/components/ui/Checkbox'
import { Button } from '@/components/ui/Button'

export interface Column<T> {
  key: string
  header: ReactNode
  render: (row: T) => ReactNode
  className?: string
  headerClassName?: string
}

export function DataTable<T extends { id: string }>({ data, loading, columns, onRowClick, empty, selectable, selected, onSelectedChange, onPageChange, footer, hidePagination, error, onRetry }: {
  data?: PageResponse<T>
  loading: boolean
  /** Query error message - renders an honest error state (with retry) instead of an empty table. */
  error?: string | null
  onRetry?: () => void
  columns: Column<T>[]
  onRowClick?: (row: T) => void
  empty?: ReactNode | { icon?: ReactNode; title: string; subtitle?: string } | null
  selectable?: boolean
  selected?: Set<string>
  onSelectedChange?: (s: Set<string>) => void
  onPageChange?: (p: number) => void
  footer?: (data: PageResponse<T>) => ReactNode
  hidePagination?: boolean
}) {
  const [localSelected, setLocalSelected] = useState<Set<string>>(new Set())
  const sel = selectable ? (selected ?? localSelected) : new Set<string>()
  const setSel = onSelectedChange ?? setLocalSelected

  // Defensive: the contract is a PageResponse; a truthy payload with a missing/invalid
  // content array (e.g. a bare-array response) is treated as empty, never a crash.
  const content = Array.isArray(data?.content) ? data.content : []

  const toggleAll = () => {
    if (sel.size === content.length) setSel(new Set())
    else setSel(new Set(content.map((r) => r.id)))
  }

  if (error) {
    return (
      <div className="flex flex-col items-center gap-3 p-8 text-center">
        <AlertTriangle className="h-6 w-6 text-red-400" />
        <div>
          <p className="text-sm font-medium text-slate-800">Unable to load data</p>
          <p className="mt-0.5 text-xs text-slate-500">{error}</p>
        </div>
        {onRetry && (
          <Button variant="secondary" size="sm" onClick={onRetry}><RotateCcw className="h-3.5 w-3.5" /> Try again</Button>
        )}
      </div>
    )
  }

  if (loading) {
    return (
      <div className="space-y-2 p-4">
        {Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}
      </div>
    )
  }

  if (!data || content.length === 0) {
    if (empty === undefined || empty === null) return <EmptyState title="Nothing here yet" />
    if (isValidElement(empty) || typeof empty !== 'object') return <>{empty}</>
    const meta = empty as { icon?: ReactNode; title: string; subtitle?: string }
    return <EmptyState icon={meta.icon} title={meta.title} subtitle={meta.subtitle} />
  }

  return (
    <div>
      <Table>
        <THead>
          <TR>
            {selectable && (
              <TH className="w-10">
                <Checkbox checked={content.length > 0 && sel.size === content.length} onChange={toggleAll} aria-label="Select all" />
              </TH>
            )}
            {columns.map((c) => <TH key={c.key} className={c.headerClassName}>{c.header}</TH>)}
          </TR>
        </THead>
        <tbody>
          {content.map((row) => (
            <TR key={row.id} onClick={onRowClick ? () => onRowClick(row) : undefined}>
              {selectable && (
                <TD>
                  <Checkbox
                    checked={sel.has(row.id)}
                    onClick={(e) => e.stopPropagation()}
                    onChange={(e) => {
                      const next = new Set(sel)
                      if (e.target.checked) next.add(row.id)
                      else next.delete(row.id)
                      setSel(next)
                    }}
                    aria-label="Select row"
                  />
                </TD>
              )}
              {columns.map((c) => <TD key={c.key} className={c.className}>{c.render(row)}</TD>)}
            </TR>
          ))}
        </tbody>
      </Table>
      {footer?.(data)}
      {!hidePagination && onPageChange && (
        <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={onPageChange} />
      )}
    </div>
  )
}
