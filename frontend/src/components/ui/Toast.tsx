import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'
import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react'
import { cn } from '@/lib/utils'

type Tone = 'success' | 'error' | 'info'
interface Toast { id: number; tone: Tone; title: string; message?: string }

const ToastContext = createContext<{ push: (tone: Tone, title: string, message?: string) => void } | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const push = useCallback((tone: Tone, title: string, message?: string) => {
    const id = Date.now() + Math.random()
    setToasts((t) => [...t, { id, tone, title, message }])
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 5000)
  }, [])

  const icons: Record<Tone, ReactNode> = {
    success: <CheckCircle2 className="h-5 w-5 text-emerald-500" />,
    error: <AlertCircle className="h-5 w-5 text-red-500" />,
    info: <Info className="h-5 w-5 text-blue-500" />,
  }

  return (
    <ToastContext.Provider value={{ push }}>
      {children}
      <div className="pointer-events-none fixed bottom-4 right-4 z-[100] flex w-96 max-w-[calc(100vw-2rem)] flex-col gap-2">
        {toasts.map((t) => (
          <div key={t.id} className={cn('pointer-events-auto flex items-start gap-3 rounded-xl border bg-white p-4 shadow-lg', t.tone === 'error' ? 'border-red-200' : 'border-slate-200')}>
            {icons[t.tone]}
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-slate-800">{t.title}</p>
              {t.message && <p className="mt-0.5 break-words text-sm text-slate-500">{t.message}</p>}
            </div>
            <button onClick={() => setToasts((x) => x.filter((y) => y.id !== t.id))} className="text-slate-400 hover:text-slate-600" aria-label="Dismiss">
              <X className="h-4 w-4" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used within ToastProvider')
  return ctx
}
