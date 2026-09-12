import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Tag, Trash2, Upload } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import type { SourceItem, TagItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Label } from '@/components/ui/Input'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { PageLoader } from '@/components/ui/Misc'

export default function TagsSourcesPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const [tagOpen, setTagOpen] = useState(false)
  const [sourceOpen, setSourceOpen] = useState(false)
  const [deleteTag, setDeleteTag] = useState<TagItem | null>(null)
  const [deleteSource, setDeleteSource] = useState<SourceItem | null>(null)
  const [tag, setTag] = useState({ name: '', color: '#2563eb' })
  const [source, setSource] = useState({ key: '', name: '', description: '' })

  const { data: tags, isLoading: tagsLoading } = useQuery({ queryKey: ['tags'], queryFn: async () => (await api.get<TagItem[]>('/tags')).data })
  const { data: sources, isLoading: sourcesLoading } = useQuery({ queryKey: ['sources'], queryFn: async () => (await api.get<SourceItem[]>('/lead-sources')).data })

  const createTag = useMutation({
    mutationFn: async () => api.post('/tags', tag),
    onSuccess: () => { toast.push('success', 'Tag created'); setTagOpen(false); setTag({ name: '', color: '#2563eb' }); qc.invalidateQueries({ queryKey: ['tags'] }) },
    onError: (e) => toast.push('error', 'Failed', apiError(e).message),
  })
  const createSource = useMutation({
    mutationFn: async () => api.post('/lead-sources', source),
    onSuccess: () => { toast.push('success', 'Source created'); setSourceOpen(false); setSource({ key: '', name: '', description: '' }); qc.invalidateQueries({ queryKey: ['sources'] }) },
    onError: (e) => toast.push('error', 'Failed', apiError(e).message),
  })
  const delTag = useMutation({ mutationFn: async (id: string) => api.delete(`/tags/${id}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['tags'] }) })
  const delSource = useMutation({ mutationFn: async (id: string) => api.delete(`/lead-sources/${id}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['sources'] }) })

  if (tagsLoading || sourcesLoading) return <PageLoader />

  return (
    <div>
      <PageHeader title="Tags & lead sources" subtitle="Taxonomy used across leads, scoring rules, and reporting." />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader title="Tags" action={<Button size="sm" variant="secondary" onClick={() => setTagOpen(true)}><Plus className="h-3.5 w-3.5" /> New tag</Button>} />
          <CardBody>
            <div className="flex flex-wrap gap-1.5">
              {tags?.map((t) => (
                <span key={t.id} className="group inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium text-white ring-1 ring-inset" style={{ backgroundColor: t.color ?? '#64748b' }}>
                  <Tag className="h-3 w-3" /> {t.name}
                  <button onClick={() => setDeleteTag(t)} className="opacity-60 hover:opacity-100" aria-label={`Delete ${t.name}`}><Trash2 className="h-3 w-3" /></button>
                </span>
              ))}
              {tags && tags.length === 0 && <p className="text-sm text-slate-400">No tags yet.</p>}
            </div>
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="Lead sources" action={<Button size="sm" variant="secondary" onClick={() => setSourceOpen(true)}><Plus className="h-3.5 w-3.5" /> New source</Button>} />
          <CardBody className="!px-0">
            <table className="w-full text-sm">
              <tbody>
                {sources?.map((s) => (
                  <tr key={s.id} className="border-b border-slate-50 last:border-0">
                    <td className="px-5 py-2"><span className="font-medium text-slate-700">{s.name}</span> <span className="ml-1 font-mono text-[10px] text-slate-400">{s.key}</span></td>
                    <td className="px-3 py-2 text-xs text-slate-500">{s.description}</td>
                    <td className="px-5 py-2 text-right"><button onClick={() => setDeleteSource(s)} className="text-slate-300 hover:text-red-500" aria-label={`Delete ${s.name}`}><Trash2 className="h-3.5 w-3.5" /></button></td>
                  </tr>
                ))}
              </tbody>
            </table>
            {sources && sources.length === 0 && <p className="px-5 py-6 text-sm text-slate-400">No sources yet.</p>}
          </CardBody>
        </Card>
      </div>

      <Dialog open={tagOpen} onClose={() => setTagOpen(false)} title="New tag">
        <div className="space-y-3">
          <div>
            <Label required>Name</Label>
            <Input value={tag.name} onChange={(e) => setTag({ ...tag, name: e.target.value })} placeholder="e.g. VIP" />
          </div>
          <div>
            <Label>Color</Label>
            <input type="color" value={tag.color} onChange={(e) => setTag({ ...tag, color: e.target.value })} className="h-9 w-16 cursor-pointer rounded border border-slate-200" />
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setTagOpen(false)}>Cancel</Button>
          <Button disabled={!tag.name.trim()} loading={createTag.isPending} onClick={() => createTag.mutate()}>Create</Button>
        </div>
      </Dialog>

      <Dialog open={sourceOpen} onClose={() => setSourceOpen(false)} title="New lead source">
        <div className="space-y-3">
          <div>
            <Label required>Key</Label>
            <Input value={source.key} onChange={(e) => setSource({ ...source, key: e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '_') })} placeholder="REFERRAL" />
          </div>
          <div>
            <Label required>Display name</Label>
            <Input value={source.name} onChange={(e) => setSource({ ...source, name: e.target.value })} placeholder="Referral" />
          </div>
          <div>
            <Label>Description</Label>
            <Input value={source.description} onChange={(e) => setSource({ ...source, description: e.target.value })} />
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setSourceOpen(false)}>Cancel</Button>
          <Button disabled={!source.key || !source.name} loading={createSource.isPending} onClick={() => createSource.mutate()}>Create</Button>
        </div>
      </Dialog>

      <ConfirmDialog open={!!deleteTag} onClose={() => setDeleteTag(null)} onConfirm={async () => { if (deleteTag) await delTag.mutateAsync(deleteTag.id); toast.push('success', 'Tag deleted') }} title={`Delete tag “${deleteTag?.name}”?`} message="The tag is removed from all leads that use it." confirmLabel="Delete" danger />
      <ConfirmDialog open={!!deleteSource} onClose={() => setDeleteSource(null)} onConfirm={async () => { if (deleteSource) await delSource.mutateAsync(deleteSource.id); toast.push('success', 'Source deleted') }} title={`Delete source “${deleteSource?.name}”?`} message="Leads keep working; historical source reporting keeps past values." confirmLabel="Delete" danger />
      <p className="mt-4 flex items-center gap-2 text-xs text-slate-400"><Upload className="h-3.5 w-3.5" /> Sources with key IMPORTED_… are created automatically by the import wizard when a file column maps to source.</p>
    </div>
  )
}
