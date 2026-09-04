import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, FileText, Trash2, Upload } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import type { DocumentItem, PageResponse } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable, type Column } from '@/components/shared/DataTable'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { fmtDateTime } from '@/lib/utils'

export default function DocumentsPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const [page, setPage] = useState(0)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [file, setFile] = useState<File | null>(null)

  const { data, isFetching } = useQuery({
    queryKey: ['documents', page],
    queryFn: async () => (await api.get<PageResponse<DocumentItem>>('/documents', { params: { page, size: 25 } })).data,
  })

  const upload = useMutation({
    mutationFn: async (f: File) => {
      const fd = new FormData()
      fd.append('file', f)
      return api.post('/documents', fd)
    },
    onSuccess: () => { toast.push('success', 'Document uploaded'); setFile(null); qc.invalidateQueries({ queryKey: ['documents'] }) },
    onError: (e) => toast.push('error', 'Upload failed', apiError(e).message),
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/documents/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['documents'] }) },
  })

  const columns: Column<DocumentItem>[] = [
    { key: 'name', header: 'Document', render: (d) => (
      <div className="flex items-center gap-2.5">
        <FileText className="h-4 w-4 shrink-0 text-slate-400" />
        <div>
          <p className="font-medium text-slate-800">{d.name}</p>
          <p className="text-xs text-slate-500">{(d.sizeBytes / 1024).toFixed(0)} KB · {d.contentType ?? 'file'}</p>
        </div>
      </div>
    ) },
    { key: 'lead', header: 'Linked to', render: (d) => <span className="text-slate-500">{[d.leadId && 'Lead', d.companyId && 'Company', d.dealId && 'Deal', d.proposalId && 'Proposal', d.clientId && 'Client'].filter(Boolean).join(', ') || '—'}</span> },
    { key: 'created', header: 'Uploaded', render: (d) => <span className="text-slate-500">{fmtDateTime(d.createdAt)}</span> },
    { key: 'download', header: '', render: (d) => <a href={`/api/v1/documents/${d.id}/download`} className="text-xs font-medium text-blue-600 hover:underline"><Download className="mr-1 inline h-3 w-3" />Download</a> },
    {
      key: 'actions', header: '', render: (d) => (
        <div className="text-right">
          <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); setDeleteId(d.id) }} aria-label="Delete document"><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button>
        </div>
      ),
    },
  ]

  return (
    <div>
      <PageHeader title="Documents" subtitle="Files are stored via the storage abstraction (local disk in dev, S3-compatible in production)." />
      <div className="card mb-3 flex items-center gap-3 p-3">
        <input type="file" className="text-sm" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        <Button size="sm" disabled={!file} loading={upload.isPending} onClick={() => file && upload.mutate(file)}><Upload className="h-3.5 w-3.5" /> Upload</Button>
        <span className="text-xs text-slate-400">Max 10 MB · executables blocked · link documents to leads from the lead page.</span>
      </div>
      <div className="card">
        <DataTable data={data} loading={isFetching} columns={columns} onPageChange={setPage}
          empty={{ icon: <FileText className="h-6 w-6" />, title: 'No documents' }} />
      </div>
      <ConfirmDialog open={!!deleteId} onClose={() => setDeleteId(null)} onConfirm={async () => { if (deleteId) await del.mutateAsync(deleteId); toast.push('success', 'Document deleted') }} title="Delete document?" message="The file is removed from storage. This cannot be undone." confirmLabel="Delete" danger />
    </div>
  )
}
