import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { ArrowUpRight, CalendarPlus, Clock, ExternalLink, MapPin } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { campus, downloadIcs, safeUrl, when, type CampusEvent, type CampusRecruitment, type EventInput } from '../lib/campus'
import { localInput } from '../lib/format'
import { useToast } from './toast'
import { Button, ErrorNote, Input, Modal, Select, Textarea } from './ui'

/** Category → accent, so a technical event and a cultural one look different at a glance. */
function categoryTone(category?: string | null) {
  switch (category) {
    case 'Technical':
      return 'bg-signal-50 text-signal-ink ring-signal/25'
    case 'Cultural':
    case 'Talks':
      return 'bg-berry-50 text-berry ring-berry/20'
    case 'Sports':
    case 'Recreation':
      return 'bg-forest-50 text-forest ring-forest/20'
    case 'Social service':
    case 'Entrepreneurship':
      return 'bg-amber-50 text-amber ring-amber/20'
    default:
      return 'bg-paper-2 text-ink-2 ring-line'
  }
}

export function CategoryPill({ category }: { category: string }) {
  return <span className={clsx('rounded-md px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset', categoryTone(category))}>{category}</span>
}

function DateBlock({ iso }: { iso: string | null }) {
  return (
    <div className="grid w-14 shrink-0 self-start overflow-hidden rounded-lg border border-line text-center">
      <span className="bg-signal py-0.5 font-mono text-[10px] font-bold tracking-wider text-on-signal">{iso ? when.month(iso) : 'TBA'}</span>
      <span className="bg-surface py-1.5 font-display text-xl font-bold">{iso ? when.date(iso) : '?'}</span>
    </div>
  )
}

export function EventCard({ event, showClub = true }: { event: CampusEvent; showClub?: boolean }) {
  const register = safeUrl(event.registrationUrl)
  const source = safeUrl(event.sourceUrl)
  return (
    <article className="flex gap-4 rounded-[var(--radius-card)] border border-line bg-surface p-4 shadow-soft sm:p-5">
      <DateBlock iso={event.startsAt} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2 text-xs text-muted">
          {showClub && event.club ? (
            <Link to={`/app/directory/${event.club.slug}`} className="font-medium text-ink-2 hover:text-signal-ink">{event.club.name}</Link>
          ) : showClub ? (
            <span className="font-medium text-ink-2">SRM KTR</span>
          ) : null}
          {event.club && <CategoryPill category={event.club.category} />}
          {event.source === 'SRM_FEED' && <span className="font-mono text-[10px] tracking-wider uppercase">via srmist.edu.in</span>}
        </div>
        <h3 className="mt-1.5 text-base leading-snug font-bold sm:text-lg">{event.title}</h3>
        <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-[13px] text-ink-2">
          <span className="flex items-center gap-1.5"><Clock className="size-3.5 text-muted" />{when.event(event)}</span>
          {event.venue && <span className="flex items-center gap-1.5"><MapPin className="size-3.5 text-muted" />{event.venue}</span>}
        </div>
        {event.description && <p className="mt-2 line-clamp-2 text-[13px] text-muted">{event.description}</p>}
        <div className="mt-3 flex flex-wrap gap-2">
          {register && (
            <a href={register} target="_blank" rel="noopener noreferrer" className="inline-flex h-8 items-center gap-1.5 rounded-md bg-signal px-3 text-[13px] font-medium text-on-signal gloss hover:bg-signal-600">
              Register <ArrowUpRight className="size-3.5" />
            </a>
          )}
          <Button variant="outline" size="sm" icon={<CalendarPlus className="size-3.5" />} onClick={() => downloadIcs(event)}>Add to calendar</Button>
          {source && source !== register && (
            <a href={source} target="_blank" rel="noopener noreferrer" className="inline-flex h-8 items-center gap-1.5 rounded-md px-2.5 text-[13px] text-ink-2 hover:bg-paper-2 hover:text-ink">
              Details <ExternalLink className="size-3.5" />
            </a>
          )}
        </div>
      </div>
    </article>
  )
}

export function RecruitmentCard({ recruitment, showClub = true }: { recruitment: CampusRecruitment; showClub?: boolean }) {
  const apply = safeUrl(recruitment.applyUrl)
  return (
    <article className="rounded-[var(--radius-card)] border border-line bg-surface p-4 shadow-soft sm:p-5">
      <div className="flex flex-wrap items-center gap-2 text-xs text-muted">
        {showClub && recruitment.club && (
          <Link to={`/app/directory/${recruitment.club.slug}`} className="font-medium text-ink-2 hover:text-signal-ink">{recruitment.club.name}</Link>
        )}
        {recruitment.club && <CategoryPill category={recruitment.club.category} />}
      </div>
      <h3 className="mt-1.5 text-base font-bold">{recruitment.title}</h3>
      {recruitment.description && <p className="mt-1 line-clamp-3 text-[13px] text-muted">{recruitment.description}</p>}
      <div className="mt-3 flex flex-wrap items-center gap-3">
        {apply && (
          <a href={apply} target="_blank" rel="noopener noreferrer" className="inline-flex h-8 items-center gap-1.5 rounded-md bg-signal px-3 text-[13px] font-medium text-on-signal gloss hover:bg-signal-600">
            Apply <ArrowUpRight className="size-3.5" />
          </a>
        )}
        <span className={clsx('text-[13px]', recruitment.deadline ? 'font-medium text-amber' : 'text-muted')}>
          {recruitment.deadline ? when.deadline(recruitment.deadline) : 'Open until filled'}
        </span>
      </div>
    </article>
  )
}

const emptyEvent = { clubSlug: '', title: '', startsAt: '', endsAt: '', venue: '', registrationUrl: '', description: '' }

/** Shared by students ("suggest") and admins ("add"): same form, different endpoint. */
export function EventFormModal({
  open,
  onClose,
  mode,
  initial,
  onSaved,
  submit,
  defaultClubSlug,
}: {
  open: boolean
  onClose: () => void
  mode: 'suggest' | 'admin'
  initial?: CampusEvent
  onSaved?: () => void
  submit?: (input: EventInput) => Promise<unknown>
  defaultClubSlug?: string
}) {
  const blank = { ...emptyEvent, clubSlug: defaultClubSlug ?? '' }
  const toast = useToast()
  const queryClient = useQueryClient()
  const clubs = useQuery({ queryKey: ['campus', 'clubs'], queryFn: campus.clubs, enabled: open })
  const [form, setForm] = useState(() =>
    initial
      ? {
          clubSlug: initial.club?.slug ?? '',
          title: initial.title,
          startsAt: initial.startsAt ? localInput.fromDate(new Date(initial.startsAt)) : '',
          endsAt: initial.endsAt ? localInput.fromDate(new Date(initial.endsAt)) : '',
          venue: initial.venue ?? '',
          registrationUrl: initial.registrationUrl ?? '',
          description: initial.description ?? '',
        }
      : blank,
  )
  const save = useMutation({
    mutationFn: () => {
      const input: EventInput = {
        clubSlug: form.clubSlug || null,
        title: form.title,
        description: form.description || null,
        startsAt: localInput.toIso(form.startsAt),
        endsAt: form.endsAt ? localInput.toIso(form.endsAt) : null,
        venue: form.venue || null,
        registrationUrl: form.registrationUrl || null,
        sourceUrl: initial?.sourceUrl ?? null,
      }
      return submit ? submit(input) : campus.suggestEvent(input)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['campus'] })
      toast.success(mode === 'suggest' ? 'Thanks! Sent for review' : 'Saved', mode === 'suggest' ? 'It shows up for everyone once the ClubHub team checks it.' : undefined)
      setForm(blank)
      onSaved?.()
      onClose()
    },
  })

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    save.mutate()
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={mode === 'suggest' ? 'Suggest an event' : initial ? 'Edit event' : 'Add an event'}
      description={mode === 'suggest' ? 'Saw a poster or an Instagram post? Add it here so everyone can find it.' : undefined}
      wide
    >
      <form onSubmit={onSubmit} className="space-y-4">
        <Input label="Event name" required maxLength={200} placeholder="e.g. HackSRM 5.0" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
        <Select label="Club" value={form.clubSlug} onChange={(e) => setForm({ ...form, clubSlug: e.target.value })}>
          <option value="">University / department event (no club)</option>
          {clubs.data?.map((c) => <option key={c.slug} value={c.slug}>{c.name}</option>)}
        </Select>
        <div className="grid gap-4 sm:grid-cols-2">
          <Input label="Starts" type="datetime-local" required value={form.startsAt} onChange={(e) => setForm({ ...form, startsAt: e.target.value })} />
          <Input label="Ends (optional)" type="datetime-local" value={form.endsAt} onChange={(e) => setForm({ ...form, endsAt: e.target.value })} />
        </div>
        <Input label="Venue" maxLength={200} placeholder="e.g. TP Ganesan Auditorium" value={form.venue} onChange={(e) => setForm({ ...form, venue: e.target.value })} />
        <Input
          label="Registration link"
          type="url"
          maxLength={500}
          placeholder="https://forms.gle/…  or the Instagram post"
          hint="Where students actually sign up."
          value={form.registrationUrl}
          onChange={(e) => setForm({ ...form, registrationUrl: e.target.value })}
        />
        <Textarea label="Details (optional)" rows={3} maxLength={2000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <ErrorNote error={save.error} />
        <Button type="submit" className="w-full" loading={save.isPending}>{mode === 'suggest' ? 'Send for review' : 'Save'}</Button>
      </form>
    </Modal>
  )
}

export function RecruitmentFormModal({
  open,
  onClose,
  mode,
  initial,
  submit,
  defaultClubSlug,
}: {
  open: boolean
  onClose: () => void
  mode: 'suggest' | 'admin'
  initial?: CampusRecruitment
  submit?: (input: { clubSlug: string; title: string; description: string | null; applyUrl: string | null; deadline: string | null }) => Promise<unknown>
  defaultClubSlug?: string
}) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const clubs = useQuery({ queryKey: ['campus', 'clubs'], queryFn: campus.clubs, enabled: open })
  const blank = { clubSlug: defaultClubSlug ?? '', title: '', description: '', applyUrl: '', deadline: '' }
  const [form, setForm] = useState(() =>
    initial
      ? { clubSlug: initial.club?.slug ?? '', title: initial.title, description: initial.description ?? '', applyUrl: initial.applyUrl ?? '', deadline: initial.deadline ?? '' }
      : blank,
  )
  const save = useMutation({
    mutationFn: () => {
      const input = { clubSlug: form.clubSlug, title: form.title, description: form.description || null, applyUrl: form.applyUrl || null, deadline: form.deadline || null }
      return submit ? submit(input) : campus.suggestRecruitment(input)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['campus'] })
      toast.success(mode === 'suggest' ? 'Thanks! Sent for review' : 'Saved')
      setForm(blank)
      onClose()
    },
  })

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={mode === 'suggest' ? 'Tell us a club is recruiting' : initial ? 'Edit recruitment' : 'Add a recruitment'}
      description={mode === 'suggest' ? 'Share the application form so other students don’t miss it.' : undefined}
    >
      <form
        onSubmit={(e) => {
          e.preventDefault()
          save.mutate()
        }}
        className="space-y-4"
      >
        <Select label="Club" required value={form.clubSlug} onChange={(e) => setForm({ ...form, clubSlug: e.target.value })}>
          <option value="" disabled>Choose a club</option>
          {clubs.data?.map((c) => <option key={c.slug} value={c.slug}>{c.name}</option>)}
        </Select>
        <Input label="Recruiting for" required maxLength={200} placeholder="e.g. Tech, design and events teams" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
        <Input label="Application link" type="url" maxLength={500} placeholder="https://forms.gle/…" value={form.applyUrl} onChange={(e) => setForm({ ...form, applyUrl: e.target.value })} />
        <Input label="Last date to apply (optional)" type="date" value={form.deadline} onChange={(e) => setForm({ ...form, deadline: e.target.value })} />
        <Textarea label="Details (optional)" rows={3} maxLength={2000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <ErrorNote error={save.error} />
        <Button type="submit" className="w-full" loading={save.isPending}>{mode === 'suggest' ? 'Send for review' : 'Save'}</Button>
      </form>
    </Modal>
  )
}
