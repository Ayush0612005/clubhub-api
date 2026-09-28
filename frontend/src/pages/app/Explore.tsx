import { useQuery } from '@tanstack/react-query'
import { ArrowUpRight, Compass, Search } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { Avatar, EmptyState, PageHeader, Skeleton } from '../../components/ui'
import { useMyClubs } from '../../hooks/useAuth'
import { api } from '../../lib/api'
import type { ClubCard } from '../../lib/types'

export default function Explore() {
  const [query, setQuery] = useState('')
  const directory = useQuery({ queryKey: ['directory'], queryFn: () => api.get<ClubCard[]>('/api/clubs') })
  const mine = useMyClubs()
  const memberOf = new Set(mine.data?.map((c) => c.slug))
  const shown = directory.data?.filter((c) => c.name.toLowerCase().includes(query.toLowerCase()))

  return (
    <div>
      <PageHeader
        eyebrow="Discover"
        title="Explore clubs"
        description="See what every club is up to: public events you can register for and open recruitment drives."
      />

      <div className="relative mb-6 max-w-md">
        <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted" />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search clubs"
          className="h-11 w-full rounded-xl border border-line bg-surface pr-4 pl-10 text-sm focus:border-ink/40 focus:outline-none focus:ring-4 focus:ring-signal/10"
        />
      </div>

      {directory.isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-36" />)}
        </div>
      )}
      {directory.data?.length === 0 && (
        <EmptyState icon={<Compass className="size-5" />} title="No clubs on ClubHub yet">
          SRM KTR clubs are being onboarded. Once your club joins, its events and recruitment drives show up here.
        </EmptyState>
      )}
      {!!directory.data?.length && shown?.length === 0 && (
        <EmptyState icon={<Search className="size-5" />} title={`No clubs match "${query}"`}>Try a different name.</EmptyState>
      )}
      {directory.isError && <EmptyState icon={<Search className="size-5" />} title="Couldn't load clubs">Check your connection and refresh the page.</EmptyState>}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {shown?.map((club, i) => (
          <Link
            key={club.slug}
            to={`/app/clubs/${club.slug}`}
            className="group animate-rise relative overflow-hidden rounded-[var(--radius-card)] border border-line bg-surface p-5 shadow-soft transition hover:-translate-y-0.5 hover:shadow-lift"
            style={{ animationDelay: `${Math.min(i, 8) * 40}ms` }}
          >
            <div className="flex items-start justify-between">
              <Avatar name={club.name} size={48} square />
              {memberOf.has(club.slug) ? (
                <span className="rounded-full bg-forest-50 px-2 py-0.5 text-[11px] font-medium text-forest">Member</span>
              ) : (
                <ArrowUpRight className="size-5 text-muted transition group-hover:text-signal" />
              )}
            </div>
            <p className="mt-5 font-display text-xl font-bold">{club.name}</p>
            <p className="mt-1 font-mono text-xs text-muted">/{club.slug}</p>
          </Link>
        ))}
      </div>
    </div>
  )
}
