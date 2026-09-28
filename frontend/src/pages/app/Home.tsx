import { useQuery } from '@tanstack/react-query'
import { ArrowRight, ArrowUpRight, Bell, CalendarDays, Megaphone } from 'lucide-react'
import { Link } from 'react-router'
import { EventCard, RecruitmentCard } from '../../components/campus'
import { Avatar, ButtonLink, Card, EmptyState, PageHeader, Skeleton, StatusBadge } from '../../components/ui'
import { useMe, useMyClubs } from '../../hooks/useAuth'
import { api } from '../../lib/api'
import { campus } from '../../lib/campus'
import { fmt } from '../../lib/format'
import type { Notification, Page } from '../../lib/types'

function greeting() {
  const hour = new Date().getHours()
  return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening'
}

function SectionTitle({ title, to, cta }: { title: string; to: string; cta: string }) {
  return (
    <div className="mb-3 flex items-center justify-between">
      <h2 className="text-lg font-bold">{title}</h2>
      <Link to={to} className="flex items-center gap-1 text-sm text-muted hover:text-ink">{cta} <ArrowRight className="size-3.5" /></Link>
    </div>
  )
}

export default function Home() {
  const me = useMe()
  const clubs = useMyClubs()
  const events = useQuery({ queryKey: ['campus', 'events'], queryFn: campus.events })
  const recruitments = useQuery({ queryKey: ['campus', 'recruitments'], queryFn: campus.recruitments })
  const recent = useQuery({
    queryKey: ['notifications', 'recent'],
    queryFn: () => api.get<Page<Notification>>('/api/notifications?size=5'),
  })
  const firstName = me.data?.fullName.split(' ')[0]

  return (
    <div>
      <PageHeader
        eyebrow={new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' })}
        title={firstName ? `${greeting()}, ${firstName}.` : greeting()}
        description="What's happening at SRM KTR, and which clubs are looking for people."
        actions={<ButtonLink to="/app/events" variant="outline" icon={<CalendarDays className="size-4" />}>All events</ButtonLink>}
      />

      <div className="grid gap-8 lg:grid-cols-[1.6fr_1fr]">
        <section>
          <SectionTitle title="Coming up" to="/app/events" cta="What's on" />
          {events.isLoading && <div className="space-y-3"><Skeleton className="h-36" /><Skeleton className="h-36" /></div>}
          {events.data?.length === 0 && (
            <EmptyState icon={<CalendarDays className="size-5" />} title="No events listed yet" action={<ButtonLink to="/app/events">Suggest one</ButtonLink>}>
              Seen a poster or an Instagram post? Add it so everyone can find it.
            </EmptyState>
          )}
          <div className="space-y-3">
            {events.data?.slice(0, 4).map((e) => <EventCard key={e.id} event={e} />)}
          </div>
        </section>

        <div className="space-y-8">
          <section>
            <SectionTitle title="Recruiting now" to="/app/recruiting" cta="All" />
            {recruitments.isLoading && <Skeleton className="h-32" />}
            {recruitments.data?.length === 0 && (
              <Card className="flex flex-col items-center p-6 text-center text-sm text-muted">
                <Megaphone className="mb-2 size-5" />
                No open recruitments listed right now.
              </Card>
            )}
            <div className="space-y-3">
              {recruitments.data?.slice(0, 3).map((r) => <RecruitmentCard key={r.id} recruitment={r} />)}
            </div>
          </section>

          {!!clubs.data?.length && (
            <section>
              <h2 className="mb-3 text-lg font-bold">Your workspaces</h2>
              <div className="space-y-2">
                {clubs.data.map((club) => (
                  <Link
                    key={club.slug}
                    to={`/app/c/${club.slug}`}
                    className="group flex items-center gap-3 rounded-[var(--radius-card)] border border-line bg-surface p-3 shadow-soft transition hover:shadow-lift"
                  >
                    <Avatar name={club.name} size={36} square />
                    <span className="min-w-0 flex-1 truncate font-semibold">{club.name}</span>
                    <StatusBadge status={club.role} />
                    <ArrowUpRight className="size-4 text-muted group-hover:text-signal" />
                  </Link>
                ))}
              </div>
            </section>
          )}

          <section>
            <SectionTitle title="Latest" to="/app/notifications" cta="See all" />
            <Card className="divide-y divide-line">
              {recent.data?.content.length === 0 && (
                <div className="flex flex-col items-center p-6 text-center text-sm text-muted">
                  <Bell className="mb-2 size-5" />
                  Nothing yet. Updates about your applications and events appear here instantly.
                </div>
              )}
              {recent.isLoading && <div className="space-y-2 p-4"><Skeleton className="h-12" /><Skeleton className="h-12" /></div>}
              {recent.data?.content.map((n) => (
                <Link key={n.id} to={n.link ? `/app${n.link}` : '/app/notifications'} className="flex gap-3 p-4 transition hover:bg-paper-2/60">
                  <span className={`mt-1.5 size-2 shrink-0 rounded-full ${n.read ? 'bg-line' : 'bg-signal'}`} />
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold">{n.title}</p>
                    <p className="line-clamp-2 text-[13px] text-muted">{n.body}</p>
                    <p className="mt-1 text-[11px] text-muted">{fmt.ago(n.createdAt)}</p>
                  </div>
                </Link>
              ))}
            </Card>
          </section>
        </div>
      </div>
    </div>
  )
}
