import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Download, Filter, Plus, Upload, X, Tag, UserCog, Trash2, Bookmark, BookmarkPlus,
  ArrowUpDown, LayoutList,
} from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useCan } from '@/stores/auth'
import { useToast } from '@/components/ui/Toast'
import { useDebounce } from '@/hooks/useDebounce'
import type { LeadItem, LeadFilters, PageResponse, PipelineItem, UserItem, SavedViewItem, TagItem, SourceItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable, type Column } from '@/components/shared/DataTable'
import  { Input, Select, Label }  from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { Badge, ScoreBadge } from '@/components/ui/Badge'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Avatar } from '@/components/ui/Misc'
import { fmtDate, fmtAgo } from '@/lib/utils'
import { leadParams } from './leadApi'
import { ImportWizard } from './ImportWizard'
import { LeadFormModal } from './LeadFormModal'

const STATUSES = ['NEW', 'WORKING', 'NURTURE', 'QUALIFIED', 'UNQUALIFIED', 'CONVERTED']

const STATUS_TONES: Record<string, 'gray' | 'blue' | 'yellow' | 'green' | 'red' | 'purple'> = {
  NEW: 'blue', WORKING: 'yellow', NURTURE: 'purple', QUALIFIED: 'green', UNQUALIFIED: 'red', CONVERTED: 'green',
}

const DEFAULT_FILTERS: LeadFilters = { status: '', stageId: '', assignedTo: '', sourceId: '', minScore: undefined, uncontacted: false, tags: [] }

export default function LeadsPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const can = useCan('LEAD_CREATE')
  const canExport = useCan('LEAD_EXPORT')
  const canImport = useCan('LEAD_IMPORT')
  const canBulk = useCan('LEAD_UPDATE')
  const canBulkDelete = useCan('LEAD_DELETE')
  const [params, setParams] = useSearchParams()

  const [q, setQ] = useState('')
  const debouncedQ = useDebounce(q)
  const [filters, setFilters] = useState<LeadFilters>(DEFAULT_FILTERS)
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState('createdAt,desc')
  const [showFilters, setShowFilters] = useState(false)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [importOpen, setImportOpen] = useState(false)
  const [createOpen, setCreateOpen] = useState(params.get('new') === '1')
  const [bulkTagOpen, setBulkTagOpen] = useState(false)
  const [bulkAssignOpen, setBulkAssignOpen] = useState(false)
  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false)
  const [saveViewOpen, setSaveViewOpen] = useState(false)
  const [viewName, setViewName] = useState('')

  useEffect(() => { if (params.get('new') === '1') setCreateOpen(true) }, [params])

  const activeFilters = useMemo<LeadFilters>(
    () => ({ ...filters, q: debouncedQ || undefined, page, sort }),
    [filters, debouncedQ, page, sort],
  )

  const { data, isFetching } = useQuery({
    queryKey: ['leads', activeFilters],
    queryFn: async () => (await api.get<PageResponse<LeadItem>>('/leads', { params: leadParams(activeFilters) })).data,
  })

  const { data: pipelines } = useQuery({ queryKey: ['pipelines'], queryFn: async () => (await api.get<PipelineItem[]>('/pipelines')).data })
  const { data: users } = useQuery({ queryKey: ['users-lite'], queryFn: async () => (await api.get<PageResponse<UserItem>>('/users', { params: { size: 200 } })).data })
  const { data: sources } = useQuery({ queryKey: ['sources'], queryFn: async () => (await api.get<SourceItem[]>('/lead-sources')).data })
  const { data: tags } = useQuery({ queryKey: ['tags'], queryFn: async () => (await api.get<TagItem[]>('/tags')).data })
  const { data: savedViews } = useQuery({
    queryKey: ['saved-views'],
    queryFn: async () => (await api.get<SavedViewItem[]>('/lead-views')).data,
  })

  const setFilter = (patch: Partial<LeadFilters>) => { setFilters((f) => ({ ...f, ...patch })); setPage(0) }

  const applySavedView = async (view: SavedViewItem) => {
    setQ('')
    setFilters({ ...DEFAULT_FILTERS, ...(view.filters as LeadFilters) })
    if (view.sort) setSort(view.sort)
    setPage(0)
  }

  const saveView = useMutation({
    mutationFn: async () => (await api.post('/lead-views', { name: viewName, filters, sort, shared: false })).data,
    onSuccess: () => {
      toast.push('success', 'View saved')
      setSaveViewOpen(false)
      setViewName('')
      qc.invalidateQueries({ queryKey: ['saved-views'] })
    },
    onError: (e) => toast.push('error', 'Could not save view', apiError(e).message),
  })

  const deleteView = useMutation({
    mutationFn: async (id: string) => api.delete(`/lead-views/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['saved-views'] }),
  })

  const exportCsv = useMutation({
    mutationFn: async () => {
      const res = await api.get('/leads/export', { params: leadParams({ ...activeFilters, page: 0, size: 100000 }), responseType: 'blob' })
      const url = URL.createObjectURL(res.data as Blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `leads-export-${new Date().toISOString().slice(0, 10)}.csv`
      a.click()
      URL.revokeObjectURL(url)
    },
    onError: (e) => toast.push('error', 'Export failed', apiError(e).message),
  })

  const bulkMut = useMutation({
    mutationFn: async (body: Record<string, unknown>) => api.post('/leads/bulk', { leadIds: Array.from(selected), ...body }),
    onSuccess: () => {
      toast.push('success', `Bulk job started for ${selected.size} lead(s)`, 'Track progress in the background-jobs panel.')
      setSelected(new Set())
      qc.invalidateQueries({ queryKey: ['leads'] })
    },
    onError: (e) => toast.push('error', 'Bulk action failed', apiError(e).message),
  })

  const columns: Column<LeadItem>[] = [
    {
      key: 'businessName',
      header: 'Lead',
      render: (l) => (
        <div className="flex items-center gap-2.5">
          <Avatar name={l.businessName} />
          <div className="min-w-0">
            <p className="truncate font-medium text-slate-800">{l.businessName}</p>
            <p className="truncate text-xs text-slate-500">{l.contactName ?? l.email ?? l.country ?? '—'}</p>
          </div>
        </div>
      ),
    },
    { key: 'status', header: 'Status', render: (l) => <Badge tone={STATUS_TONES[l.status] ?? 'gray'}>{l.status}</Badge> },
    { key: 'stage', header: 'Stage', render: (l) => <span className="text-slate-600">{l.stageName ?? '—'}</span> },
    { key: 'score', header: 'Score', render: (l) => <ScoreBadge score={l.score} category={l.scoreCategory} /> },
    { key: 'source', header: 'Source', render: (l) => <span className="text-slate-600">{l.sourceName ?? '—'}</span> },
    {
      key: 'assignee',
      header: 'Owner',
      render: (l) => <span className="text-slate-600">{l.assignedUserName ?? <span className="text-slate-400">Unassigned</span>}</span>,
    },
    { key: 'lastContact', header: 'Last contact', render: (l) => <span className="text-slate-500">{l.lastContactedAt ? fmtAgo(l.lastContactedAt) : <Badge tone="yellow">Never</Badge>}</span> },
    { key: 'tags', header: 'Tags', render: (l) => (
      <div className="flex max-w-40 flex-wrap gap-1">
        {(l.tags ?? []).slice(0, 2).map((t) => <Badge key={t} tone="gray">{t}</Badge>)}
        {(l.tags?.length ?? 0) > 2 && <Badge tone="gray">+{l.tags.length - 2}</Badge>}
      </div>
    ) },
    { key: 'created', header: 'Created', render: (l) => <span className="text-slate-500">{fmtDate(l.createdAt)}</span> },
  ]

  const hasActiveFilters = !!filters.status || !!filters.stageId || !!filters.assignedTo || !!filters.sourceId || !!filters.country || !!filters.city || !!filters.industry || !!filters.minScore || filters.uncontacted || (filters.tags?.length ?? 0) > 0 || !!filters.createdFrom || !!filters.createdTo

  return (
    <div>
      <PageHeader
        title="Leads"
        subtitle={data ? `${data.totalElements.toLocaleString()} leads` : undefined}
        actions={
          <>
            {canExport && (
              <Button variant="secondary" loading={exportCsv.isPending} onClick={() => exportCsv.mutate()}>
                <Download className="h-4 w-4" /> Export
              </Button>
            )}
            {canImport && <Button variant="secondary" onClick={() => setImportOpen(true)}><Upload className="h-4 w-4" /> Import</Button>}
            {can && <Button onClick={() => setCreateOpen(true)}><Plus className="h-4 w-4" /> New lead</Button>}
          </>
        }
      />

      {/* Saved views strip */}
      {savedViews && savedViews.length > 0 && (
        <div className="mb-3 flex flex-wrap items-center gap-1.5">
          <LayoutList className="h-4 w-4 text-slate-400" />
          {savedViews.map((v) => (
            <span key={v.id} className="group inline-flex items-center overflow-hidden rounded-full border border-slate-200 bg-white text-xs">
              <button onClick={() => applySavedView(v)} className="px-2.5 py-1 font-medium text-slate-600 hover:bg-slate-50">
                {v.mine ? <Bookmark className="mr-1 inline h-3 w-3 text-blue-500" /> : null}
                {v.name}
              </button>
              {v.mine && (
                <button onClick={() => deleteView.mutate(v.id)} className="border-l border-slate-200 px-1.5 py-1 text-slate-300 hover:bg-red-50 hover:text-red-500" aria-label={`Delete view ${v.name}`}>
                  <X className="h-3 w-3" />
                </button>
              )}
            </span>
          ))}
          <button onClick={() => setSaveViewOpen(true)} className="inline-flex items-center gap-1 rounded-full border border-dashed border-slate-300 px-2.5 py-1 text-xs text-slate-500 hover:border-blue-400 hover:text-blue-600">
            <BookmarkPlus className="h-3 w-3" /> Save current view
          </button>
        </div>
      )}

      {/* Search + filter bar */}
      <div className="card mb-3 flex flex-wrap items-center gap-2 p-3">
        <div className="relative min-w-56 flex-1">
          <Input value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} placeholder="Search business name, contact, email, phone…" className="pl-8" />
          <svg className="absolute left-2.5 top-2.5 h-4 w-4 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-4.35-4.35M17 11a6 6 0 11-12 0 6 6 0 0112 0z" /></svg>
        </div>
        <Select value={filters.status} onChange={(e) => setFilter({ status: e.target.value })} className="w-36">
          <option value="">All statuses</option>
          {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
        </Select>
        <Select value={filters.stageId} onChange={(e) => setFilter({ stageId: e.target.value })} className="w-40">
          <option value="">All stages</option>
          {pipelines?.flatMap((p) => p.stages).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </Select>
        <Select value={filters.assignedTo} onChange={(e) => setFilter({ assignedTo: e.target.value })} className="w-40">
          <option value="">Any owner</option>
          {users?.content.map((u) => <option key={u.id} value={u.id}>{u.displayName}</option>)}
        </Select>
        <Button variant={showFilters || hasActiveFilters ? 'primary' : 'secondary'} onClick={() => setShowFilters(!showFilters)}>
          <Filter className="h-4 w-4" /> Filters{hasActiveFilters ? ' •' : ''}
        </Button>
        <Button variant="secondary" size="icon" title={`Sort: ${sort}`} onClick={() => setSort(sort === 'createdAt,desc' ? 'score,desc' : sort === 'score,desc' ? 'lastContactedAt,asc' : 'createdAt,desc')}>
          <ArrowUpDown className="h-4 w-4" />
        </Button>
      </div>

      {showFilters && (
        <div className="card mb-3 grid grid-cols-1 gap-3 p-4 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <Label>Source</Label>
            <Select value={filters.sourceId} onChange={(e) => setFilter({ sourceId: e.target.value })}>
              <option value="">Any source</option>
              {sources?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </Select>
          </div>
          <div>
            <Label>Min score</Label>
            <Input type="number" min={0} max={100} value={filters.minScore ?? ''} onChange={(e) => setFilter({ minScore: e.target.value ? Number(e.target.value) : undefined })} placeholder="0–100" />
          </div>
          <div>
            <Label>Country</Label>
            <Input value={filters.country ?? ''} onChange={(e) => setFilter({ country: e.target.value })} placeholder="e.g. UAE" />
          </div>
          <div>
            <Label>City</Label>
            <Input value={filters.city ?? ''} onChange={(e) => setFilter({ city: e.target.value })} />
          </div>
          <div>
            <Label>Industry</Label>
            <Input value={filters.industry ?? ''} onChange={(e) => setFilter({ industry: e.target.value })} />
          </div>
          <div>
            <Label>Tags</Label>
            <Select value="" onChange={(e) => { if (e.target.value) setFilter({ tags: [...(filters.tags ?? []), e.target.value] }) }}>
              <option value="">Add tag filter…</option>
              {tags?.filter((t) => !(filters.tags ?? []).includes(t.name)).map((t) => <option key={t.id} value={t.name}>{t.name}</option>)}
            </Select>
            <div className="mt-1.5 flex flex-wrap gap-1">
              {(filters.tags ?? []).map((t) => (
                <button key={t} onClick={() => setFilter({ tags: (filters.tags ?? []).filter((x) => x !== t) })} className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2 py-0.5 text-xs text-blue-700 ring-1 ring-blue-200">
                  {t} <X className="h-3 w-3" />
                </button>
              ))}
            </div>
          </div>
          <div>
            <Label>Created from</Label>
            <Input type="date" value={filters.createdFrom ?? ''} onChange={(e) => setFilter({ createdFrom: e.target.value })} />
          </div>
          <div className="flex items-end">
            <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-600">
              <input type="checkbox" className="h-4 w-4 rounded border-slate-300 text-blue-600" checked={filters.uncontacted ?? false} onChange={(e) => setFilter({ uncontacted: e.target.checked })} />
              Never contacted
            </label>
          </div>
        </div>
      )}

      {/* Bulk bar */}
      {canBulk && selected.size > 0 && (
        <div className="mb-3 flex flex-wrap items-center gap-2 rounded-xl border border-blue-200 bg-blue-50 px-4 py-2.5 text-sm">
          <span className="font-medium text-blue-800">{selected.size} selected</span>
          <Button size="sm" variant="secondary" onClick={() => setBulkAssignOpen(true)}><UserCog className="h-3.5 w-3.5" /> Assign</Button>
          <Button size="sm" variant="secondary" onClick={() => setBulkTagOpen(true)}><Tag className="h-3.5 w-3.5" /> Add tag</Button>
          <Button size="sm" variant="secondary" onClick={() => { bulkMut.mutate({ action: 'STATUS', params: { status: 'NURTURE' } }) }}>Move to nurture</Button>
          {canBulkDelete && <Button size="sm" variant="danger" onClick={() => setBulkDeleteOpen(true)}><Trash2 className="h-3.5 w-3.5" /> Delete</Button>}
          <Button size="sm" variant="ghost" onClick={() => setSelected(new Set())}>Clear</Button>
          {bulkMut.isPending && <span className="text-blue-600">Starting job…</span>}
        </div>
      )}

      <div className="card">
        <DataTable
          data={data}
          loading={isFetching}
          columns={columns}
          onRowClick={(l) => navigate(`/leads/${l.id}`)}
          selectable={canBulk}
          selected={selected}
          onSelectedChange={setSelected}
          onPageChange={setPage}
          empty={
            <div className="py-10 text-center">
              <p className="text-sm font-medium text-slate-600">No leads match your filters</p>
              <p className="mt-1 text-sm text-slate-400">Adjust filters, import a file, or create your first lead.</p>
            </div>
          }
        />
      </div>

      {importOpen && <ImportWizard open={importOpen} onClose={() => setImportOpen(false)} />}
      <LeadFormModal open={createOpen} onClose={() => { setCreateOpen(false); params.delete('new'); setParams(params, { replace: true }) }} />

      {/* Bulk tag */}
      <BulkTagDialog open={bulkTagOpen} onClose={() => setBulkTagOpen(false)} onSubmit={(tag) => bulkMut.mutate({ action: 'ADD_TAG', params: { tag } })} busy={bulkMut.isPending} />
      {/* Bulk assign */}
      <Dialog open={bulkAssignOpen} onClose={() => setBulkAssignOpen(false)} title={`Assign ${selected.size} lead(s)`}>
        <div>
          <Label>New owner</Label>
          <Select id="bulk-assignee" defaultValue="">
            <option value="" disabled>Select user…</option>
            {users?.content.map((u) => <option key={u.id} value={u.id}>{u.displayName}</option>)}
          </Select>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setBulkAssignOpen(false)}>Cancel</Button>
          <Button
            onClick={() => {
              const el = document.getElementById('bulk-assignee') as HTMLSelectElement | null
              if (el?.value) { bulkMut.mutate({ action: 'ASSIGN', params: { userId: el.value } }); setBulkAssignOpen(false) }
            }}
          >
            Reassign
          </Button>
        </div>
      </Dialog>
      <ConfirmDialog
        open={bulkDeleteOpen}
        onClose={() => setBulkDeleteOpen(false)}
        onConfirm={async () => { bulkMut.mutate({ action: 'DELETE' }) }}
        title={`Delete ${selected.size} lead(s)?`}
        message="Leads are soft-deleted and removed from all views. This runs as a background job."
        confirmLabel="Delete leads"
        danger
      />

      {/* Save view dialog */}
      <Dialog open={saveViewOpen} onClose={() => setSaveViewOpen(false)} title="Save current view">
        <div>
          <Label required>View name</Label>
          <Input value={viewName} onChange={(e) => setViewName(e.target.value)} placeholder="e.g. Hot UAE clinics — never contacted" />
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setSaveViewOpen(false)}>Cancel</Button>
          <Button disabled={!viewName.trim()} loading={saveView.isPending} onClick={() => saveView.mutate()}>Save view</Button>
        </div>
      </Dialog>
    </div>
  )
}

function BulkTagDialog({ open, onClose, onSubmit, busy }: { open: boolean; onClose: () => void; onSubmit: (tag: string) => void; busy: boolean }) {
  const [tag, setTag] = useState('')
  return (
    <Dialog open={open} onClose={onClose} title="Add tag to selection">
      <div>
        <Label required>Tag</Label>
        <Input value={tag} onChange={(e) => setTag(e.target.value)} placeholder="e.g. Q3-push" />
      </div>
      <div className="mt-5 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button disabled={!tag.trim()} loading={busy} onClick={() => { onSubmit(tag.trim()); onClose() }}>Apply</Button>
      </div>
    </Dialog>
  )
}
