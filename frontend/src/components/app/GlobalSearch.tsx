import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Search, X, Loader2, Building2, Contact2, Users, Handshake } from 'lucide-react'
import { api } from '@/api/client'

interface SearchGroup {
  group: string
  items: { id: string; label: string; sublabel: string | null }[]
}

const groupMeta: Record<string, { icon: typeof Users; label: string; path: string }> = {
  LEAD: { icon: Users, label: 'Leads', path: '/leads' },
  COMPANY: { icon: Building2, label: 'Companies', path: '/companies' },
  CONTACT: { icon: Contact2, label: 'Contacts', path: '/contacts' },
  DEAL: { icon: Handshake, label: 'Deals', path: '/deals' },
}

export function GlobalSearch({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [q, setQ] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)
  const navigate = useNavigate()

  useEffect(() => {
    inputRef.current?.focus()
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [onClose])

  const { data, isFetching } = useQuery({
    queryKey: ['global-search', q],
    queryFn: async () => (await api.get<SearchGroup[]>('/search', { params: { q } })).data,
    enabled: open && q.trim().length >= 2,
  })

  if (!open) return null

  const go = (group: string, id: string) => {
    const meta = groupMeta[group]
    onClose()
    if (meta) navigate(`${meta.path}/${id}`)
  }

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/50 p-4 pt-[10vh]" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <div className="card mx-auto max-w-xl overflow-hidden !p-0">
        <div className="flex items-center gap-2 border-b border-slate-100 px-4">
          <Search className="h-4 w-4 text-slate-400" />
          <input
            ref={inputRef}
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search leads, companies, contacts, deals…"
            className="h-12 flex-1 bg-transparent text-sm outline-none placeholder:text-slate-400"
          />
          {isFetching && <Loader2 className="h-4 w-4 animate-spin text-slate-400" />}
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600" aria-label="Close search"><X className="h-4 w-4" /></button>
        </div>
        <div className="max-h-80 overflow-y-auto p-2">
          {q.trim().length < 2 && <p className="px-3 py-6 text-center text-sm text-slate-400">Type at least 2 characters</p>}
          {q.trim().length >= 2 && data && data.every((g) => g.items.length === 0) && (
            <p className="px-3 py-6 text-center text-sm text-slate-400">No results for “{q}”</p>
          )}
          {data?.map((g) =>
            g.items.length > 0 ? (
              <div key={g.group} className="mb-1">
                <p className="px-3 py-1 text-[11px] font-bold uppercase tracking-wider text-slate-400">
                  {groupMeta[g.group]?.label ?? g.group}
                </p>
                {g.items.map((item) => {
                  const meta = groupMeta[g.group]
                  const Icon = meta?.icon ?? Search
                  return (
                    <button
                      key={item.id}
                      onClick={() => go(g.group, item.id)}
                      className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left hover:bg-slate-50"
                    >
                      <span className="flex h-7 w-7 items-center justify-center rounded-md bg-slate-100 text-slate-500"><Icon className="h-3.5 w-3.5" /></span>
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium text-slate-800">{item.label}</span>
                        {item.sublabel && <span className="block truncate text-xs text-slate-500">{item.sublabel}</span>}
                      </span>
                    </button>
                  )
                })}
              </div>
            ) : null,
          )}
        </div>
      </div>
    </div>
  )
}
