import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { ArrowLeft, ArrowRight, Check, Lock, X } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { Avatar, Button, Card, EmptyState, ErrorNote, Modal, Skeleton, StatusBadge, Textarea } from '../../components/ui'
import { useToast } from '../../components/toast'
import { useClub } from '../../layouts/ClubLayout'
import { clubApi } from '../../lib/club'
import { fmt } from '../../lib/format'
import type { ApplicationDetail, ApplicationStatus, ApplicationSummary, Drive, DriveStatus, Page } from '../../lib/types'

const columns: { status: ApplicationStatus; label: string; accent: string }[] = [
  { status: 'APPLIED', label: 'Applied', accent: 'bg-cobalt' },
  { status: 'SHORTLISTED', label: 'Shortlisted', accent: 'bg-amber' },
  { status: 'INTERVIEW', label: 'Interview', accent: 'bg-signal' },
  { status: 'SELECTED', label: 'Selected', accent: 'bg-forest' },
  { status: 'REJECTED', label: 'Rejected', accent: 'bg-berry' },
]

// mirrors ApplicationStatus.nextStatuses() on the server (the server still enforces it)
const next: Partial<Record<ApplicationStatus, ApplicationStatus>> = {
  APPLIED: 'SHORTLISTED',
  SHORTLISTED: 'INTERVIEW',
  INTERVIEW: 'SELECTED',
}

export default function Pipeline() {
  const { driveId = '' } = useParams()
  const { club, can } = useClub()
  const api = clubApi(club.slug)
  const toast = useToast()
  const queryClient = useQueryClient()
  const [openId, setOpenId] = useState<number | null>(null)
  const core = can('CORE')

  const drive = useQuery({ queryKey: ['club', club.slug, 'drive', driveId], queryFn: () => api.get<Drive>(`/recruitment/drives/${driveId}`) })
  const lanes = useQueries({
    queries: columns.map((c) => ({
      queryKey: ['club', club.slug, 'drive', driveId, 'apps', c.status],
      queryFn: () => api.get<Page<ApplicationSummary>>(`/recruitment/drives/${driveId}/applications?status=${c.status}&size=100`),
      enabled: core,
    })),
  })

  const status = useMutation({
    mutationFn: (to: DriveStatus) => api.patch(`/recruitment/drives/${driveId}/status`, { status: to }),
    onSuccess: (_, to) => {
      toast.success(to === 'OPEN' ? 'Drive is open — students can apply' : 'Drive closed')
      queryClient.invalidateQueries({ queryKey: ['club', club.slug] })
    },
    onError: toast.error,
  })

  if (drive.isLoading) return <Skeleton className="h-96" />
  if (!drive.data) return <ErrorNote error={drive.error} />
  const d = drive.data
  const total = lanes.reduce((sum, l) => sum + (l.data?.totalElements ?? 0), 0)

  return (
    <div>
      <Link to=".." relative="path" className="mb-6 inline-flex items-center gap-1.5 text-sm text-muted hover:text-ink">
        <ArrowLeft className="size-4" /> All drives
      </Link>
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-3xl font-bold">{d.title}</h2>
            <StatusBadge status={d.status} />
          </div>
          <p className="mt-1 text-sm text-muted">
            {d.questions.length} question{d.questions.length === 1 ? '' : 's'} · {total} applicant{total === 1 ? '' : 's'}
            {d.closesAt && ` · closes ${fmt.dateTime(d.closesAt)}`}
          </p>
        </div>
        {core && (
          <div className="flex gap-2">
            {d.status !== 'OPEN' && <Button loading={status.isPending} onClick={() => status.mutate('OPEN')}>{d.status === 'DRAFT' ? 'Open drive' : 'Reopen'}</Button>}
            {d.status === 'OPEN' && <Button variant="outline" loading={status.isPending} onClick={() => status.mutate('CLOSED')}>Close drive</Button>}
          </div>
        )}
      </div>

      {!core ? (
        <EmptyState icon={<Lock className="size-5" />} title="Applicants are visible to the core team">Their answers are personal data, so only CORE and admins can review them.</EmptyState>
      ) : (
        <div className="-mx-4 flex gap-4 overflow-x-auto px-4 pb-4 sm:mx-0 sm:px-0">
          {columns.map((col, i) => {
            const lane = lanes[i]
            return (
              <div key={col.status} className="w-72 shrink-0">
                <div className="mb-3 flex items-center gap-2 px-1">
                  <span className={clsx('size-2 rounded-full', col.accent)} />
                  <span className="text-sm font-semibold">{col.label}</span>
                  <span className="ml-auto rounded-full bg-paper-2 px-2 py-0.5 font-mono text-[11px] text-muted">{lane.data?.totalElements ?? 0}</span>
                </div>
                <div className="min-h-40 space-y-2 rounded-2xl bg-paper-2/60 p-2">
                  {lane.isLoading && <Skeleton className="h-16 bg-surface" />}
                  {lane.data?.content.map((a) => (
                    <button key={a.id} onClick={() => setOpenId(a.id)} className="w-full rounded-xl border border-line bg-surface p-3 text-left shadow-soft transition hover:-translate-y-0.5 hover:shadow-lift">
                      <div className="flex items-center gap-2.5">
                        <Avatar name={a.applicant.fullName ?? '?'} size={28} />
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium">{a.applicant.fullName}</p>
                          <p className="truncate text-[11px] text-muted">{a.applicant.email}</p>
                        </div>
                      </div>
                      <p className="mt-2 text-[11px] text-muted">Applied {fmt.ago(a.submittedAt)}</p>
                    </button>
                  ))}
                  {lane.data?.content.length === 0 && <p className="px-2 py-6 text-center text-xs text-muted">Nobody here</p>}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {openId != null && <ApplicationModal id={openId} driveId={driveId} onClose={() => setOpenId(null)} />}
    </div>
  )
}

function ApplicationModal({ id, driveId, onClose }: { id: number; driveId: string; onClose: () => void }) {
  const { club } = useClub()
  const api = clubApi(club.slug)
  const toast = useToast()
  const queryClient = useQueryClient()
  const [note, setNote] = useState('')
  const app = useQuery({ queryKey: ['club', club.slug, 'application', id], queryFn: () => api.get<ApplicationDetail>(`/recruitment/applications/${id}`) })
  const move = useMutation({
    mutationFn: (status: ApplicationStatus) => api.post<ApplicationDetail>(`/recruitment/applications/${id}/transitions`, { status, note: note || null }),
    onSuccess: (updated) => {
      toast.success(`Moved to ${fmt.label(updated.status)}`, updated.status === 'SELECTED' ? `${updated.applicant.fullName} is now a member.` : 'The applicant has been notified.')
      setNote('')
      queryClient.invalidateQueries({ queryKey: ['club', club.slug, 'drive', driveId] })
      queryClient.invalidateQueries({ queryKey: ['club', club.slug, 'application', id] })
      if (updated.status === 'SELECTED') queryClient.invalidateQueries({ queryKey: ['club', club.slug, 'members'] })
    },
  })

  const a = app.data
  const forward = a && next[a.status]
  const terminal = a && ['SELECTED', 'REJECTED', 'WITHDRAWN'].includes(a.status)

  return (
    <Modal open onClose={onClose} title={a?.applicant.fullName ?? 'Application'} description={a?.applicant.email ?? undefined} wide>
      {!a ? (
        <Skeleton className="h-64" />
      ) : (
        <div className="space-y-6">
          <div className="flex items-center gap-2">
            <StatusBadge status={a.status} />
            <span className="text-xs text-muted">Applied {fmt.dateTime(a.submittedAt)}</span>
          </div>

          <div className="space-y-4">
            {a.answers.map((ans) => (
              <div key={ans.questionId}>
                <p className="text-[13px] font-medium text-muted">{ans.prompt}</p>
                <p className="mt-1 whitespace-pre-line">{ans.answer}</p>
              </div>
            ))}
          </div>

          <Card className="p-4 shadow-none">
            <p className="mb-3 text-[13px] font-medium text-muted">History</p>
            <ol className="space-y-2.5">
              {a.history.map((h, i) => (
                <li key={i} className="flex items-start gap-3 text-sm">
                  <span className="mt-1.5 size-2 shrink-0 rounded-full bg-signal" />
                  <span className="flex-1">
                    {h.from ? <>{fmt.label(h.from)} → <b>{fmt.label(h.to)}</b></> : <b>Applied</b>}
                    {h.note && <span className="block text-muted">“{h.note}”</span>}
                  </span>
                  <span className="shrink-0 text-xs text-muted">{fmt.ago(h.changedAt)}</span>
                </li>
              ))}
            </ol>
          </Card>

          {!terminal && (
            <div className="space-y-3 border-t border-line pt-5">
              <Textarea label="Note (optional, kept in the audit trail)" className="min-h-16" maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
              <ErrorNote error={move.error} />
              <div className="flex flex-wrap gap-2">
                {forward && (
                  <Button loading={move.isPending && move.variables === forward} icon={forward === 'SELECTED' ? <Check className="size-4" /> : <ArrowRight className="size-4" />} onClick={() => move.mutate(forward)}>
                    {forward === 'SELECTED' ? 'Select & add as member' : `Move to ${fmt.label(forward)}`}
                  </Button>
                )}
                <Button variant="outline" loading={move.isPending && move.variables === 'REJECTED'} icon={<X className="size-4" />} onClick={() => move.mutate('REJECTED')}>
                  Reject
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
    </Modal>
  )
}
