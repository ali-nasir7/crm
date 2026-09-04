import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'
import { format, formatDistanceToNow, parseISO } from 'date-fns'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function fmtDate(value?: string | null): string {
  if (!value) return '—'
  try { return format(parseISO(value), 'MMM d, yyyy') } catch { return value }
}

export function fmtDateTime(value?: string | null): string {
  if (!value) return '—'
  try { return format(parseISO(value), 'MMM d, yyyy · HH:mm') } catch { return value }
}

export function fmtAgo(value?: string | null): string {
  if (!value) return '—'
  try { return formatDistanceToNow(parseISO(value), { addSuffix: true }) } catch { return value }
}

export function fmtMoney(value?: number | string | null, currency = 'USD'): string {
  if (value === null || value === undefined || value === '') return '—'
  const n = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(n)) return String(value)
  return new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: 0 }).format(n)
}

export function initials(name?: string | null): string {
  if (!name) return '?'
  return name.split(/\s+/).slice(0, 2).map((p) => p[0]?.toUpperCase() ?? '').join('')
}

export function downloadCsv(filename: string, text: string) {
  const blob = new Blob([text], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
