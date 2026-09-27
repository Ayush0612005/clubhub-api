import { useQuery } from '@tanstack/react-query'
import { ArrowUpRight, Bell, Compass, Sparkles } from 'lucide-react'
import { Link } from 'react-router'
import { Avatar, ButtonLink, Card, EmptyState, PageHeader, Skeleton, StatusBadge } from '../../components/ui'
import { useMe, useMyClubs } from '../../hooks/useAuth'
import { api } from '../../lib/api'
import { fmt } from '../../lib/format'
import type { Notification, Page } from '../../lib/types'

function greeting() {
  const hour = new Date().getHours()
  return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening'
}

export default function Home() {
  const me = useMe()
  const clubs = useMyClubs()
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
        description="Your clubs, what's new, and where to go next."
        actions={<ButtonLink to="/app/explore" variant="outline" icon={<Compass className="size-4" />}>Explore clubs</ButtonLink>}
      />

      <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        <section>
          <h2 className="mb-3 text-lg font-bold">Your clubs</h2>
          {clubs.isLoading && (
            <div className="grid gap-3 sm:grid-cols-2">
              <Skeleton className="h-28" />
              <Skeleton className="h-28" />
            </div>
          )}
          {clubs.data?.length === 0 && (
            <EmptyState
              icon={<Sparkles className="size-5" />}
              title="You're not in a club yet"
              action={<ButtonLink to="/app/explore">Find a club</ButtonLink>}
            >
              Browse clubs, register for their public events or apply to an open recruitment drive.
            </EmptyState>
          )}
          <div className="grid gap-3 sm:grid-cols-2">
            {clubs.data?.map((club, i) => (
              <Link
                key={club.slug}
                to={`/app/c/${club.slug}`}
                className="group animate-rise rounded-[var(--radius-card)] border border-line bg-surface p-5 shadow-soft transition hover:-translate-y-0.5 hover:shadow-lift"
                style={{ animationDelay: `${i * 50}ms` }}
              >
                <div className="flex items-start justify-between">
                  <Avatar name={club.name} size={44} square />
                  <ArrowUpRight className="size-5 text-muted transition group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:text-signal" />
                </div>
                <p className="mt-4 font-display text-lg font-bold">{club.name}</p>
                <div className="mt-2 flex gap-2">
                  <StatusBadge status={club.role} />
                  <StatusBadge status={club.plan} />
                </div>
              </Link>
            ))}
          </div>
        </section>

        <section>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-lg font-bold">Latest</h2>
            <Link to="/app/notifications" className="text-sm text-muted hover:text-ink">See all</Link>
          </div>
          <Card className="divide-y divide-line">
            {recent.data?.content.length === 0 && (
              <div className="flex flex-col items-center p-8 text-center text-sm text-muted">
                <Bell className="mb-2 size-5" />
                Nothing yet — updates from your clubs appear here instantly.
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
  )
}
