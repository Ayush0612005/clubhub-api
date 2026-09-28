import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, CloudDownload, ExternalLink, Inbox, Pencil, Plus, X } from 'lucide-react'
import { useState } from 'react'
import { CategoryPill, EventFormModal, RecruitmentFormModal } from '../../components/campus'
import { useToast } from '../../components/toast'
import { Button, Card, EmptyState, ErrorNote, Modal, PageHeader, Skeleton, Textarea } from '../../components/ui'
import { campus, safeUrl, when, type CampusEvent, type CampusRecruitment } from '../../lib/campus'

/** Platform admins: approve what students suggested and what came in from SRM's feed. */
export default function Moderation() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const queue = useQuery({ queryKey: ['campus', 'queue'], queryFn: campus.queue })
  const [editingEvent, setEditingEvent] = useState<CampusEvent | null>(null)
  const [editingRecruitment, setEditingRecruitment] = useState<CampusRecruitment | null>(null)
  const [addingEvent, setAddingEvent] = useState(false)
  const [addingRecruitment, setAddingRecruitment] = useState(false)

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['campus'] })
  const [pasting, setPasting] = useState(false)
  const [feedXml, setFeedXml] = useState('')
  // srmist.edu.in's bot protection refuses our server, so the admin fetches the feed in their own browser
  const importSrm = useMutation({
    mutationFn: () => campus.importSrmPaste(feedXml),
    onSuccess: (r) => {
      refresh()
      setPasting(false)
      setFeedXml('')
      toast.success(
        r.added ? `${r.added} new from srmist.edu.in` : 'Nothing new on srmist.edu.in',
        `${r.inFeed} in the feed · ${r.skippedPast} already over · ${r.alreadyKnown} seen before`,
      )
    },
  })
  const reviewEvent = useMutation({
    mutationFn: ({ id, decision }: { id: string; decision: 'approve' | 'reject' }) => campus.reviewEvent(id, decision),
    onSuccess: refresh,
    onError: toast.error,
  })
  const reviewRecruitment = useMutation({
    mutationFn: ({ id, decision }: { id: string; decision: 'approve' | 'reject' }) => campus.reviewRecruitment(id, decision),
    onSuccess: refresh,
    onError: toast.error,
  })

  const pending = (queue.data?.events.length ?? 0) + (queue.data?.recruitments.length ?? 0)

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader
        eyebrow="Platform"
        title="Moderation"
        description="Nothing reaches students until it's approved here. Check dates and links; fix anything before approving."
        actions={
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" icon={<CloudDownload className="size-4" />} onClick={() => setPasting(true)}>
              Import SRM events
            </Button>
            <Button variant="outline" icon={<Plus className="size-4" />} onClick={() => setAddingRecruitment(true)}>Recruitment</Button>
            <Button icon={<Plus className="size-4" />} onClick={() => setAddingEvent(true)}>Event</Button>
          </div>
        }
      />

      {queue.isLoading && <div className="space-y-3"><Skeleton className="h-28" /><Skeleton className="h-28" /></div>}
      {queue.data && pending === 0 && (
        <EmptyState icon={<Inbox className="size-5" />} title="Queue is empty">
          Student suggestions and SRM events you import land here. Try “Import SRM events” once a week.
        </EmptyState>
      )}

      {!!queue.data?.events.length && (
        <section className="mb-10">
          <h2 className="mb-3 font-mono text-xs font-medium tracking-[0.14em] text-muted uppercase">Events · {queue.data.events.length}</h2>
          <div className="space-y-3">
            {queue.data.events.map(({ event, submittedBy }) => {
              const link = safeUrl(event.registrationUrl) ?? safeUrl(event.sourceUrl)
              return (
                <Card key={event.id} className="p-5">
                  <div className="flex flex-wrap items-center gap-2 text-xs text-muted">
                    <span>{submittedBy}</span>
                    {event.club && <CategoryPill category={event.club.category} />}
                    {event.club && <span className="font-medium text-ink-2">{event.club.name}</span>}
                  </div>
                  <h3 className="mt-1.5 font-bold">{event.title}</h3>
                  <p className={event.startsAt ? 'mt-1 text-sm text-ink-2' : 'mt-1 text-sm font-medium text-amber'}>
                    {event.startsAt ? when.event(event) : 'No date found. Edit to add one before approving.'}
                    {event.venue ? ` · ${event.venue}` : ''}
                  </p>
                  {event.description && <p className="mt-2 line-clamp-3 text-[13px] text-muted">{event.description}</p>}
                  <div className="mt-4 flex flex-wrap items-center gap-2">
                    <Button size="sm" icon={<Check className="size-4" />} disabled={!event.startsAt}
                      loading={reviewEvent.isPending && reviewEvent.variables?.id === event.id && reviewEvent.variables.decision === 'approve'}
                      onClick={() => reviewEvent.mutate({ id: event.id, decision: 'approve' })}>
                      Approve
                    </Button>
                    <Button size="sm" variant="outline" icon={<Pencil className="size-4" />} onClick={() => setEditingEvent(event)}>Edit</Button>
                    <Button size="sm" variant="ghost" icon={<X className="size-4" />} onClick={() => reviewEvent.mutate({ id: event.id, decision: 'reject' })}>Reject</Button>
                    {link && (
                      <a href={link} target="_blank" rel="noopener noreferrer" className="ml-auto inline-flex items-center gap-1 text-[13px] text-muted hover:text-ink">
                        Check link <ExternalLink className="size-3.5" />
                      </a>
                    )}
                  </div>
                </Card>
              )
            })}
          </div>
        </section>
      )}

      {!!queue.data?.recruitments.length && (
        <section>
          <h2 className="mb-3 font-mono text-xs font-medium tracking-[0.14em] text-muted uppercase">Recruitments · {queue.data.recruitments.length}</h2>
          <div className="space-y-3">
            {queue.data.recruitments.map(({ recruitment, submittedBy }) => {
              const link = safeUrl(recruitment.applyUrl)
              return (
                <Card key={recruitment.id} className="p-5">
                  <div className="flex flex-wrap items-center gap-2 text-xs text-muted">
                    <span>{submittedBy}</span>
                    {recruitment.club && <span className="font-medium text-ink-2">{recruitment.club.name}</span>}
                  </div>
                  <h3 className="mt-1.5 font-bold">{recruitment.title}</h3>
                  <p className="mt-1 text-sm text-ink-2">{recruitment.deadline ? when.deadline(recruitment.deadline) : 'No deadline'}</p>
                  <div className="mt-4 flex flex-wrap items-center gap-2">
                    <Button size="sm" icon={<Check className="size-4" />} onClick={() => reviewRecruitment.mutate({ id: recruitment.id, decision: 'approve' })}>Approve</Button>
                    <Button size="sm" variant="outline" icon={<Pencil className="size-4" />} onClick={() => setEditingRecruitment(recruitment)}>Edit</Button>
                    <Button size="sm" variant="ghost" icon={<X className="size-4" />} onClick={() => reviewRecruitment.mutate({ id: recruitment.id, decision: 'reject' })}>Reject</Button>
                    {link && (
                      <a href={link} target="_blank" rel="noopener noreferrer" className="ml-auto inline-flex items-center gap-1 text-[13px] text-muted hover:text-ink">
                        Check form <ExternalLink className="size-3.5" />
                      </a>
                    )}
                  </div>
                </Card>
              )
            })}
          </div>
        </section>
      )}

      {/* keyed by id so each edit starts from that item's values */}
      {editingEvent && (
        <EventFormModal key={editingEvent.id} open onClose={() => setEditingEvent(null)} mode="admin" initial={editingEvent}
          submit={(input) => campus.updateEvent(editingEvent.id, input)} />
      )}
      {editingRecruitment && (
        <RecruitmentFormModal key={editingRecruitment.id} open onClose={() => setEditingRecruitment(null)} mode="admin" initial={editingRecruitment}
          submit={(input) => campus.updateRecruitment(editingRecruitment.id, input)} />
      )}
      <Modal open={pasting} onClose={() => setPasting(false)} title="Import SRM events" description="New items land in this queue for you to check. Takes about 10 seconds." wide>
        <ol className="mb-4 list-decimal space-y-1.5 pl-5 text-sm text-ink-2">
          <li>
            Open <a href="https://www.srmist.edu.in/events/feed/" target="_blank" rel="noopener noreferrer" className="font-medium text-signal-ink underline underline-offset-2">SRM's events feed</a> in a new tab.
          </li>
          <li>Right-click → <span className="font-medium">View page source</span> (or Ctrl+U), then Ctrl+A and Ctrl+C.</li>
          <li>Paste it below.</li>
        </ol>
        <Textarea label="Feed source" rows={8} className="font-mono text-xs" placeholder='<?xml version="1.0" encoding="UTF-8"?>…' value={feedXml} onChange={(e) => setFeedXml(e.target.value)} />
        <div className="mt-4 space-y-3">
          <ErrorNote error={importSrm.error} />
          <Button className="w-full" loading={importSrm.isPending} disabled={!feedXml.trim()} onClick={() => importSrm.mutate()}>Import</Button>
        </div>
      </Modal>
      <EventFormModal open={addingEvent} onClose={() => setAddingEvent(false)} mode="admin" submit={campus.createEvent} />
      <RecruitmentFormModal open={addingRecruitment} onClose={() => setAddingRecruitment(false)} mode="admin" submit={campus.createRecruitment} />
    </div>
  )
}
