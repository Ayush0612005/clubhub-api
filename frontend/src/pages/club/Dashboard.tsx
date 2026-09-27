import { useQuery } from '@tanstack/react-query'
import { CalendarPlus, Megaphone, UserPlus } from 'lucide-react'
import { Link } from 'react-router'
import { EventRow } from '../../components/EventRow'
import { ButtonLink, Card, EmptyState, Meter, Skeleton, Stat, StatusBadge } from '../../components/ui'
import { useClub } from '../../layouts/ClubLayout'
import { clubApi } from '../../lib/club'
import { fmt } from '../../lib/format'
import type { ClubEvent, ClubProfile, DriveSummary, LimitName, PlanView } from '../../lib/types'

const limitLabels: Record<LimitName, string> = {
  MEMBERS: 'Members',
  UPCOMING_EVENTS: 'Upcoming events',
  OPEN_DRIVES: 'Open drives',
}

export default function Dashboard() {
  const { club, can } = useClub()
  const api = clubApi(club.slug)
  const plan = useQuery({ queryKey: ['club', club.slug, 'plan'], queryFn: () => api.get<PlanView>('/plan') })
  const profile = useQuery({ queryKey: ['club', club.slug, 'profile'], queryFn: () => api.get<ClubProfile>('/profile') })
  const events = useQuery({ queryKey: ['club', club.slug, 'events'], queryFn: () => api.get<ClubEvent[]>('/events') })
  const drives = useQuery({ queryKey: ['club', club.slug, 'drives'], queryFn: () => api.get<DriveSummary[]>('/recruitment/drives') })

  const openDrives = drives.data?.filter((d) => d.status === 'OPEN') ?? []
  const nextEvent = events.data?.find((e) => e.status === 'PUBLISHED')

  return (
    <div className="space-y-8">
      {profile.data?.description && <p className="max-w-3xl text-[15px] leading-relaxed text-ink-2">{profile.data.description}</p>}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat accent label="Next up" value={nextEvent ? fmt.date(nextEvent.startsAt) : '—'} hint={nextEvent?.title ?? 'No published events'} />
        <Stat label="Members" value={plan.data?.usage.MEMBERS ?? '·'} hint={`of ${plan.data?.limits.MEMBERS ?? '…'} on ${plan.data?.plan ?? ''}`} />
        <Stat label="Upcoming events" value={plan.data?.usage.UPCOMING_EVENTS ?? '·'} />
        <Stat label="Open drives" value={openDrives.length} hint={openDrives.length ? 'Accepting applications' : 'Not recruiting'} />
      </div>

      {can('CORE') && (
        <div className="grid gap-3 sm:grid-cols-3">
          {[
            { to: 'events?new=1', icon: CalendarPlus, title: 'Create an event', body: 'Draft it, then publish when ready.' },
            { to: 'recruitment?new=1', icon: Megaphone, title: 'Start recruiting', body: 'Open a drive with your questions.' },
            { to: 'members', icon: UserPlus, title: 'Add a member', body: 'Bring your team into the workspace.' },
          ].map(({ to, icon: Icon, title, body }) => (
            <Link key={title} to={to} className="group flex items-start gap-4 rounded-[var(--radius-card)] border border-line bg-surface p-5 transition hover:border-signal/40 hover:shadow-soft">
              <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-signal-50 text-signal transition group-hover:bg-signal group-hover:text-white">
                <Icon className="size-5" />
              </span>
              <span>
                <span className="block font-semibold">{title}</span>
                <span className="block text-sm text-muted">{body}</span>
              </span>
            </Link>
          ))}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-[1.5fr_1fr]">
        <section>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-lg font-bold">Upcoming events</h2>
            <Link to="events" className="text-sm text-muted hover:text-ink">All events</Link>
          </div>
          {events.isLoading && <Skeleton className="h-24" />}
          {events.data?.length === 0 && <EmptyState icon={<CalendarPlus className="size-5" />} title="Nothing scheduled">{can('CORE') ? 'Create your first event.' : 'Your core team hasn’t published anything yet.'}</EmptyState>}
          <div className="space-y-3">
            {events.data?.slice(0, 4).map((e) => <EventRow key={e.id} event={e} to={`events/${e.id}`} showStatus />)}
          </div>
        </section>

        <section className="space-y-6">
          <Card className="p-6">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold">Plan & usage</h2>
              {plan.data && <StatusBadge status={plan.data.plan} />}
            </div>
            <div className="mt-5 space-y-4">
              {plan.data &&
                (Object.keys(limitLabels) as LimitName[]).map((key) => (
                  <div key={key}>
                    <div className="mb-1.5 flex justify-between text-sm">
                      <span className="text-ink-2">{limitLabels[key]}</span>
                      <span className="font-mono text-xs text-muted">{plan.data.usage[key]} / {plan.data.limits[key]}</span>
                    </div>
                    <Meter used={plan.data.usage[key]} max={plan.data.limits[key]} />
                  </div>
                ))}
            </div>
          </Card>

          <Card className="p-6">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-lg font-bold">Recruitment</h2>
              <Link to="recruitment" className="text-sm text-muted hover:text-ink">Manage</Link>
            </div>
            {openDrives.length === 0 && <p className="text-sm text-muted">No open drives.</p>}
            <ul className="space-y-2">
              {openDrives.map((d) => (
                <li key={d.id}>
                  <Link to={`recruitment/${d.id}`} className="flex items-center justify-between rounded-xl px-3 py-2 hover:bg-paper-2">
                    <span className="font-medium">{d.title}</span>
                    <StatusBadge status={d.status} />
                  </Link>
                </li>
              ))}
            </ul>
            {can('CORE') && <ButtonLink to="recruitment?new=1" variant="outline" size="sm" className="mt-4">New drive</ButtonLink>}
          </Card>
        </section>
      </div>
    </div>
  )
}
