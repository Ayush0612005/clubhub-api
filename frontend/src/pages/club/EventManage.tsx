import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { ArrowLeft, Award, Ban, Check, Clock, MapPin, Megaphone, ScanLine, UserPlus, Users } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { DateTile } from '../../components/art'
import { Ticket } from '../../components/Ticket'
import { Avatar, Badge, Button, ButtonLink, Card, EmptyState, ErrorNote, Input, Skeleton, Stat, StatusBadge } from '../../components/ui'
import { useToast } from '../../components/toast'
import { useClub } from '../../layouts/ClubLayout'
import { ApiError } from '../../lib/api'
import { clubApi } from '../../lib/club'
import { fmt } from '../../lib/format'
import type { AttendanceSummary, Certificate, ClubEvent, Registrant } from '../../lib/types'

type Tab = 'people' | 'checkins' | 'certificates'

export default function EventManage() {
  const { eventId = '' } = useParams()
  const { club, can } = useClub()
  const api = clubApi(club.slug)
  const queryClient = useQueryClient()
  const toast = useToast()
  const [tab, setTab] = useState<Tab>('people')
  const key = ['club', club.slug, 'event', eventId]

  const event = useQuery({ queryKey: key, queryFn: () => api.get<ClubEvent>(`/events/${eventId}`) })
  const registered = useQuery({
    queryKey: [...key, 'mine'],
    queryFn: () => api.get(`/events/${eventId}/ticket`).then(() => true, (e) => (e instanceof ApiError && e.status === 404 ? false : Promise.reject(e))),
    enabled: event.data?.status === 'PUBLISHED',
  })

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['club', club.slug] })
  const action = useMutation({
    mutationFn: (what: 'publish' | 'cancel' | 'register' | 'unregister') =>
      what === 'register'
        ? api.post(`/events/${eventId}/registration`)
        : what === 'unregister'
          ? api.del(`/events/${eventId}/registration`)
          : api.post(`/events/${eventId}/${what}`),
    onSuccess: (_, what) => {
      toast.success({ publish: 'Published — members are being notified', cancel: 'Event cancelled', register: "You're registered", unregister: 'Registration cancelled' }[what])
      refresh()
    },
    onError: toast.error,
  })

  if (event.isLoading) return <Skeleton className="h-96" />
  if (!event.data) return <ErrorNote error={event.error} />
  const e = event.data
  const core = can('CORE')

  return (
    <div>
      <Link to=".." relative="path" className="mb-6 inline-flex items-center gap-1.5 text-sm text-muted hover:text-ink">
        <ArrowLeft className="size-4" /> All events
      </Link>

      <div className="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex items-start gap-5">
          <DateTile iso={e.startsAt} />
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-3xl font-bold">{e.title}</h2>
              <StatusBadge status={e.status} />
              {e.visibility === 'MEMBERS' && <Badge tone="cobalt">Members only</Badge>}
            </div>
            <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-sm text-muted">
              <span className="inline-flex items-center gap-1.5"><Clock className="size-4" />{fmt.dateTime(e.startsAt)} – {fmt.time(e.endsAt)}</span>
              <span className="inline-flex items-center gap-1.5"><MapPin className="size-4" />{e.venue}</span>
            </div>
            {e.description && <p className="mt-4 max-w-2xl text-[15px] leading-relaxed whitespace-pre-line text-ink-2">{e.description}</p>}
          </div>
        </div>

        {core && (
          <div className="flex shrink-0 flex-wrap gap-2">
            {e.status === 'DRAFT' && <Button icon={<Megaphone className="size-4" />} loading={action.isPending} onClick={() => action.mutate('publish')}>Publish</Button>}
            {e.status === 'PUBLISHED' && <ButtonLink to="scan" variant="ink" icon={<ScanLine className="size-4" />}>Open door scanner</ButtonLink>}
            {e.status !== 'CANCELLED' && (
              <Button variant="outline" icon={<Ban className="size-4" />} onClick={() => confirm('Cancel this event? Registrations stop and tickets stop working.') && action.mutate('cancel')}>
                Cancel
              </Button>
            )}
          </div>
        )}
      </div>

      {e.status === 'PUBLISHED' && (
        <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_340px]">
          <div className="order-2 lg:order-1">{core ? <CoreTabs tab={tab} setTab={setTab} eventId={eventId} event={e} /> : <MemberInfo event={e} />}</div>
          <div className="order-1 lg:order-2">
            {registered.data ? (
              <div className="space-y-2">
                <Ticket event={e} clubName={club.name} loadQr={() => api.blobUrl(`/events/${eventId}/ticket/qr`)} />
                {new Date(e.startsAt) > new Date() && (
                  <Button variant="ghost" className="w-full" onClick={() => action.mutate('unregister')}>Cancel my registration</Button>
                )}
              </div>
            ) : (
              <Card className="p-6">
                <p className="font-display text-lg font-bold">Going?</p>
                <p className="mt-1 text-sm text-muted">Register to get your QR ticket.</p>
                <Button className="mt-4 w-full" loading={action.isPending} disabled={new Date(e.startsAt) <= new Date()} onClick={() => action.mutate('register')}>
                  Register
                </Button>
              </Card>
            )}
          </div>
        </div>
      )}
      {e.status === 'DRAFT' && (
        <Card className="mt-8 border-dashed p-6 text-sm text-muted">This is a draft. Only the core team can see it. Publish to open registrations and notify every member.</Card>
      )}
    </div>
  )
}

function MemberInfo({ event }: { event: ClubEvent }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Stat label="Going" value={event.registeredCount} hint={event.capacity ? `${event.capacity} seats` : 'No seat limit'} />
      <Stat label="Check-in opens" value={fmt.time(new Date(new Date(event.startsAt).getTime() - 3600_000).toISOString())} hint="One hour before the start" />
    </div>
  )
}

function CoreTabs({ tab, setTab, eventId, event }: { tab: Tab; setTab: (t: Tab) => void; eventId: string; event: ClubEvent }) {
  const tabs: [Tab, string][] = [
    ['people', 'Registrations'],
    ['checkins', 'Check-ins'],
    ['certificates', 'Certificates'],
  ]
  return (
    <div>
      <div className="mb-4 inline-flex rounded-xl bg-paper-2 p-1 text-sm">
        {tabs.map(([id, label]) => (
          <button key={id} onClick={() => setTab(id)} className={clsx('rounded-lg px-3.5 py-1.5 font-medium transition', tab === id ? 'bg-surface shadow-soft' : 'text-muted hover:text-ink')}>
            {label}
          </button>
        ))}
      </div>
      {tab === 'people' && <Registrants eventId={eventId} event={event} />}
      {tab === 'checkins' && <CheckIns eventId={eventId} />}
      {tab === 'certificates' && <Certificates eventId={eventId} />}
    </div>
  )
}

function Registrants({ eventId, event }: { eventId: string; event: ClubEvent }) {
  const { club } = useClub()
  const people = useQuery({ queryKey: ['club', club.slug, 'event', eventId, 'registrants'], queryFn: () => clubApi(club.slug).get<Registrant[]>(`/events/${eventId}/registrations`) })
  if (people.isLoading) return <Skeleton className="h-40" />
  if (!people.data?.length) return <EmptyState icon={<Users className="size-5" />} title="No registrations yet">Share the event — registrations appear here live.</EmptyState>
  const attended = people.data.filter((p) => p.attended).length
  return (
    <Card className="overflow-hidden">
      <div className="flex items-center justify-between border-b border-line px-5 py-3 text-sm">
        <span className="font-semibold">{people.data.length} registered{event.capacity ? ` of ${event.capacity}` : ''}</span>
        <span className="text-muted">{attended} checked in</span>
      </div>
      <ul className="divide-y divide-line">
        {people.data.map((p) => (
          <li key={p.userId} className="flex items-center gap-3 px-5 py-3">
            <Avatar name={p.fullName ?? p.email ?? '?'} size={32} />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{p.fullName}</p>
              <p className="truncate text-xs text-muted">{p.email}</p>
            </div>
            {p.attended ? <Badge tone="forest"><Check className="size-3" /> Attended</Badge> : <span className="text-xs text-muted">{fmt.ago(p.registeredAt)}</span>}
          </li>
        ))}
      </ul>
    </Card>
  )
}

function CheckIns({ eventId }: { eventId: string }) {
  const { club } = useClub()
  const api = clubApi(club.slug)
  const toast = useToast()
  const queryClient = useQueryClient()
  const [email, setEmail] = useState('')
  const key = ['club', club.slug, 'event', eventId, 'attendance']
  const summary = useQuery({ queryKey: key, queryFn: () => api.get<AttendanceSummary>(`/events/${eventId}/attendance`), refetchInterval: 10_000 })
  const walkIn = useMutation({
    mutationFn: () => api.post(`/events/${eventId}/check-ins/manual`, { email }),
    onSuccess: () => {
      toast.success('Checked in')
      setEmail('')
      queryClient.invalidateQueries({ queryKey: ['club', club.slug, 'event', eventId] })
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    walkIn.mutate()
  }

  const rate = summary.data && summary.data.registered > 0 ? Math.round((summary.data.attended / summary.data.registered) * 100) : 0
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-3 gap-3">
        <Stat label="Registered" value={summary.data?.registered ?? '·'} />
        <Stat label="Checked in" value={summary.data?.attended ?? '·'} />
        <Stat accent label="Turnout" value={`${rate}%`} />
      </div>
      <Card className="p-5">
        <form onSubmit={submit} className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <Input label="Walk-in (no ticket)" type="email" placeholder="their@srmist.edu.in" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </div>
          <Button type="submit" variant="ink" loading={walkIn.isPending} icon={<UserPlus className="size-4" />}>Check in</Button>
        </form>
        <div className="mt-3"><ErrorNote error={walkIn.error} /></div>
      </Card>
      {!!summary.data?.attendees.length && (
        <Card className="divide-y divide-line">
          {summary.data.attendees.map((a) => (
            <div key={a.userId} className="flex items-center gap-3 px-5 py-3">
              <Avatar name={a.fullName ?? '?'} size={30} />
              <span className="flex-1 text-sm font-medium">{a.fullName}</span>
              <Badge tone={a.method === 'QR' ? 'signal' : 'neutral'}>{a.method === 'QR' ? 'QR scan' : 'Manual'}</Badge>
              <span className="w-16 text-right text-xs text-muted">{fmt.time(a.checkedInAt)}</span>
            </div>
          ))}
        </Card>
      )}
    </div>
  )
}

function Certificates({ eventId }: { eventId: string }) {
  const { club } = useClub()
  const api = clubApi(club.slug)
  const toast = useToast()
  const queryClient = useQueryClient()
  const key = ['club', club.slug, 'event', eventId, 'certificates']
  const certs = useQuery({ queryKey: key, queryFn: () => api.get<Certificate[]>(`/events/${eventId}/certificates`) })
  const issue = useMutation({
    mutationFn: () => api.post<{ issued: number; alreadyIssued: number }>(`/events/${eventId}/certificates`),
    onSuccess: (r) => {
      toast.success(`${r.issued} certificate${r.issued === 1 ? '' : 's'} issued`, r.alreadyIssued ? `${r.alreadyIssued} already had one.` : 'Recipients are being notified.')
      queryClient.invalidateQueries({ queryKey: key })
    },
  })
  const revoke = useMutation({
    mutationFn: (id: string) => api.post(`/certificates/${id}/revoke`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key }),
    onError: toast.error,
  })

  return (
    <div className="space-y-4">
      <Card className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="font-semibold">Participation certificates</p>
          <p className="text-sm text-muted">Issues a verifiable PDF to everyone checked in. Safe to run again after late check-ins.</p>
        </div>
        <Button icon={<Award className="size-4" />} loading={issue.isPending} onClick={() => issue.mutate()}>Issue certificates</Button>
      </Card>
      <ErrorNote error={issue.error} />
      {!!certs.data?.length && (
        <Card className="divide-y divide-line">
          {certs.data.map((c) => (
            <div key={c.id} className="flex items-center gap-3 px-5 py-3">
              <Avatar name={c.recipientName} size={30} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{c.recipientName}</p>
                <p className="truncate font-mono text-[11px] text-muted">{c.id}</p>
              </div>
              {c.revoked ? (
                <Badge tone="berry">Revoked</Badge>
              ) : (
                <Button variant="ghost" size="sm" onClick={() => confirm(`Revoke ${c.recipientName}'s certificate?`) && revoke.mutate(c.id)}>Revoke</Button>
              )}
            </div>
          ))}
        </Card>
      )}
    </div>
  )
}
