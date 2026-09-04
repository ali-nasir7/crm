import { createContext, useContext, useState, type ReactNode } from 'react'
import { cn } from '@/lib/utils'

const TabsContext = createContext<{ value: string; setValue: (v: string) => void } | null>(null)

export function Tabs({ defaultValue, children, className }: { defaultValue: string; children: ReactNode; className?: string }) {
  const [value, setValue] = useState(defaultValue)
  return <TabsContext.Provider value={{ value, setValue }}><div className={className}>{children}</div></TabsContext.Provider>
}

export function TabsList({ children }: { children: ReactNode }) {
  return <div className="flex flex-wrap gap-1 border-b border-slate-200">{children}</div>
}

export function TabsTrigger({ value, children }: { value: string; children: ReactNode }) {
  const ctx = useContext(TabsContext)!
  return (
    <button
      onClick={() => ctx.setValue(value)}
      className={cn('border-b-2 px-3.5 py-2 text-sm font-medium transition-colors', ctx.value === value ? 'border-blue-600 text-blue-700' : 'border-transparent text-slate-500 hover:text-slate-700')}
    >
      {children}
    </button>
  )
}

export function TabsContent({ value, children, className }: { value: string; children: ReactNode; className?: string }) {
  const ctx = useContext(TabsContext)!
  if (ctx.value !== value) return null
  return <div className={cn('pt-4', className)}>{children}</div>
}
