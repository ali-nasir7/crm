import type { LeadFilters } from '@/types'

export function leadParams(f: LeadFilters): Record<string, unknown> {
  const p: Record<string, unknown> = { page: f.page ?? 0, size: f.size ?? 25, sort: f.sort ?? 'createdAt,desc' }
  if (f.q) p.q = f.q
  if (f.status) p.status = f.status
  if (f.stageId) p.stageId = f.stageId
  if (f.assignedTo) p.assignedTo = f.assignedTo
  if (f.sourceId) p.sourceId = f.sourceId
  if (f.country) p.country = f.country
  if (f.city) p.city = f.city
  if (f.industry) p.industry = f.industry
  if (f.tags?.length) p.tags = f.tags.join(',')
  if (f.minScore !== undefined) p.minScore = f.minScore
  if (f.uncontacted) p.uncontacted = true
  if (f.createdFrom) p.createdFrom = f.createdFrom
  if (f.createdTo) p.createdTo = f.createdTo
  return p
}
