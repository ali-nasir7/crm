import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell } from 'lucide-react'
import { api } from '@/api/client'
import type { NotificationItem, PageResponse } from '@/types'
import { fmtAgo } from '@/lib/utils'
import { cn } from '@/lib/utils'

export function NotificationsBell() {
  const [open, setOpen] = useState(false)
  const qc = useQueryClient()

  const { data: unreadData } = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: async () => (await api.get<{ count: number }>('/notifications/unread-count')).data,
    refetchInterval: 30_000,
  })

  const { data: list } = useQuery({
    queryKey: ['notifications', 'list'],
    queryFn: async () => (await api.get<PageResponse<NotificationItem>>('/notifications', { params: { size: 10 } })).data,
    enabled: open,
  })

  const markRead = useMutation({
    mutationFn: async (id: string) => api.post(`/notifications/${id}/read`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  })

  const unread = unreadData?.count ?? 0

  return (
    <div className="relative">
      <button onClick={() => setOpen(!open)} className="relative rounded-lg p-2 text-slate-500 hover:bg-slate-100" aria-label="Notifications">
        <Bell className="h-5 w-5" />
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute right-0 z-20 mt-2 w-96 max-w-[calc(100vw-2rem)] rounded-xl border border-slate-200 bg-white shadow-lg">
            <div className="border-b border-slate-100 px-4 py-3">
              <p className="text-sm font-semibold text-slate-800">Notifications</p>
            </div>
            <div className="max-h-96 overflow-y-auto">
              {!list || list.content.length === 0 ? (
                <p className="px-4 py-8 text-center text-sm text-slate-400">You're all caught up.</p>
              ) : (
                list.content.map((n) => (
                  <button
                    key={n.id}
                    onClick={() => { if (!n.readAt) markRead.mutate(n.id) }}
                    className={cn('block w-full border-b border-slate-50 px-4 py-3 text-left hover:bg-slate-50', !n.readAt && 'bg-blue-50/40')}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <p className="truncate text-sm font-medium text-slate-800">{n.title}</p>
                      <span className="shrink-0 text-[11px] text-slate-400">{fmtAgo(n.createdAt)}</span>
                    </div>
                    {n.body && <p className="mt-0.5 line-clamp-2 text-xs text-slate-500">{n.body}</p>}
                  </button>
                ))
              )}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
