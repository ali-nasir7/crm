import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { MessageSquare, Plus, Send, UserPlus } from 'lucide-react'
import { api } from '@/api/client'
import type { ChatConversationItem, ChatMessageItem, PageResponse, UserItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select } from '@/components/ui/Input'
import { EmptyState } from '@/components/ui/Misc'
import { PageLoader } from '@/components/ui/Misc'
import { apiError } from '@/api/client'
import { fmtAgo, fmtDateTime, cn } from '@/lib/utils'
import { useAuth } from '@/stores/auth'

/**
 * Internal team chat. Rules enforced by the backend: rep<->rep needs a shared team,
 * everyone can talk to managers/admins of the same organization. Messages poll every
 * 5s (the notification bell already streams via SSE for instant awareness).
 */
export default function ChatPage() {
  const { user } = useAuth()
  const qc = useQueryClient()
  const [activeId, setActiveId] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [newOpen, setNewOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const scrollRef = useRef<HTMLDivElement>(null)

  const { data: conversations, isLoading } = useQuery({
    queryKey: ['chat-conversations'],
    queryFn: async () => (await api.get<ChatConversationItem[]>('/chat/conversations')).data,
    refetchInterval: 8000,
  })

  const { data: messages, isLoading: messagesLoading } = useQuery({
    queryKey: ['chat-messages', activeId],
    queryFn: async () => (await api.get<PageResponse<ChatMessageItem>>(`/chat/conversations/${activeId}/messages`, { params: { page: 0, size: 50 } })).data,
    enabled: !!activeId,
    refetchInterval: 5000,
  })

  const { data: users } = useQuery({
    queryKey: ['chat-users'],
    queryFn: async () => (await api.get<PageResponse<UserItem>>('/users', { params: { size: 200 } })).data,
  })

  const openWith = useMutation({
    mutationFn: async (userId: string) => (await api.post<ChatConversationItem>('/chat/conversations', { userId })).data,
    onSuccess: (c) => {
      setNewOpen(false)
      setError(null)
      qc.invalidateQueries({ queryKey: ['chat-conversations'] })
      setActiveId(c.id)
    },
    onError: (e) => setError(apiError(e).message),
  })

  const send = useMutation({
    mutationFn: async () => (await api.post<ChatMessageItem>(`/chat/conversations/${activeId}/messages`, { body: draft })).data,
    onSuccess: () => {
      setDraft('')
      qc.invalidateQueries({ queryKey: ['chat-messages', activeId] })
      qc.invalidateQueries({ queryKey: ['chat-conversations'] })
    },
    onError: (e) => setError(apiError(e).message),
  })

  useEffect(() => {
    if (activeId) api.post(`/chat/conversations/${activeId}/read`).catch(() => undefined)
  }, [activeId, messages?.totalElements])

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight })
  }, [messages?.totalElements])

  const active = conversations?.find((c) => c.id === activeId)
  const ordered = messages ? [...messages.content].reverse() : []
  const others = (c: ChatConversationItem) => c.participantNames.join(', ')

  return (
    <div>
      <PageHeader title="Team Chat" subtitle="Coordinate with your team and managers. CRM links stay in context."
        actions={<Button onClick={() => setNewOpen(true)}><Plus className="h-4 w-4" /> New conversation</Button>} />

      {error && <div className="mb-3 rounded-lg border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700">{error}</div>}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[320px_1fr]">
        <div className="card !p-0">
          <div className="border-b border-slate-100 px-4 py-3">
            <p className="text-sm font-semibold text-slate-800">Conversations</p>
          </div>
          <div className="max-h-[60vh] overflow-y-auto">
            {isLoading ? <PageLoader /> : !conversations || conversations.length === 0 ? (
              <EmptyState icon={<MessageSquare className="h-6 w-6" />} title="No conversations yet"
                subtitle="Start one with a teammate or your manager." />
            ) : conversations.map((c) => (
              <button key={c.id} onClick={() => setActiveId(c.id)}
                className={cn('flex w-full items-center justify-between gap-2 border-b border-slate-50 px-4 py-3 text-left hover:bg-slate-50',
                  activeId === c.id && 'bg-blue-50/50')}>
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-slate-800">{others(c)}</p>
                  <p className="truncate text-xs text-slate-500">{c.lastMessage ?? 'No messages yet'}</p>
                </div>
                {c.unreadCount > 0 && (
                  <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-blue-600 px-1.5 text-[10px] font-bold text-white">{c.unreadCount}</span>
                )}
              </button>
            ))}
          </div>
        </div>

        <div className="card flex min-h-[60vh] flex-col !p-0">
          {!active ? (
            <EmptyState icon={<MessageSquare className="h-6 w-6" />} title="Pick a conversation"
              subtitle="Select one on the left, or start a new one." />
          ) : (
            <>
              <div className="border-b border-slate-100 px-4 py-3">
                <p className="text-sm font-semibold text-slate-800">{others(active)}</p>
              </div>
              <div ref={scrollRef} className="flex-1 space-y-2 overflow-y-auto p-4">
                {messagesLoading ? <PageLoader /> : ordered.map((m) => {
                  const mine = m.senderId === user?.id
                  return (
                    <div key={m.id} className={cn('flex', mine ? 'justify-end' : 'justify-start')}>
                      <div className={cn('max-w-[75%] rounded-2xl px-3.5 py-2 text-sm',
                        mine ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-800')}>
                        {!mine && <p className="text-[11px] font-semibold text-slate-500">{m.senderName}</p>}
                        <p className="whitespace-pre-wrap break-words">{m.body}</p>
                        <div className="mt-0.5 flex items-center justify-between gap-3">
                          <span className={cn('text-[10px]', mine ? 'text-blue-100' : 'text-slate-400')}>{fmtDateTime(m.createdAt)}</span>
                          {m.leadId && <Link to={`/leads/${m.leadId}`} className={cn('text-[10px] underline', mine ? 'text-blue-100' : 'text-blue-600')}>open lead</Link>}
                        </div>
                      </div>
                    </div>
                  )
                })}
              </div>
              <form className="flex items-center gap-2 border-t border-slate-100 p-3"
                onSubmit={(e) => { e.preventDefault(); if (draft.trim()) send.mutate() }}>
                <Input value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="Write a message… (max 4000 chars)" maxLength={4000} />
                <Button type="submit" loading={send.isPending} disabled={!draft.trim()}><Send className="h-4 w-4" /></Button>
              </form>
            </>
          )}
        </div>
      </div>

      <Dialog open={newOpen} onClose={() => setNewOpen(false)} title="New conversation"
        description="Rep-to-rep chat needs a shared team. Everyone can message managers and admins.">
        <div className="space-y-3">
          <Select defaultValue="" onChange={(e) => { if (e.target.value) openWith.mutate(e.target.value) }}>
            <option value="">Select a colleague…</option>
            {(users?.content ?? []).filter((u) => u.id !== user?.id).map((u) => (
              <option key={u.id} value={u.id}>{u.displayName} ({u.email})</option>
            ))}
          </Select>
          {openWith.isPending && <p className="text-xs text-slate-500">Opening conversation…</p>}
          <Button variant="secondary" className="w-full" onClick={() => setNewOpen(false)}><UserPlus className="h-4 w-4" /> Close</Button>
        </div>
      </Dialog>
    </div>
  )
}
