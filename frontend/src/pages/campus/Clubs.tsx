import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { CalendarDays, Megaphone, Search, Users } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import { CategoryPill } from '../../components/campus'
import { Avatar, EmptyState, PageHeader, Skeleton } from '../../components/ui'
import { campus } from '../../lib/campus'

/** All SRM KTR clubs, chapters and associations, whether or not they use ClubHub. */
export default function Clubs() {
  const clubs = useQuery({ queryKey: ['campus', 'clubs'], queryFn: campus.clubs })
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('All')
  const [recruitingOnly, setRecruitingOnly] = useState(false)

  const categories = useMemo(() => {
    const counts = new Map<string, number>()
    clubs.data?.forEach((c) => counts.set(c.category, (counts.get(c.category) ?? 0) + 1))
    return ['All', ...[...counts.entries()].sort((a, b) => b[1] - a[1]).map(([c]) => c)]
  }, [clubs.data])

  const shown = clubs.data?.filter((c) => {
    const q = query.trim().toLowerCase()
    return (
      (category === 'All' || c.category === category) &&
      (!recruitingOnly || c.recruiting) &&
      (!q || [c.name, c.home, c.description, c.kind].some((f) => f?.toLowerCase().includes(q)))
    )
  })

  return (
    <div>
      <PageHeader
        eyebrow="Directory"
        title="Clubs at SRM KTR"
        description={clubs.data ? `${clubs.data.length} clubs, chapters and student associations. Find their events and open recruitments.` : 'Every club, chapter and student association.'}
      />

      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative flex-1 sm:max-w-sm">
          <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by name, department or topic"
            className="h-10 w-full rounded-lg border border-line bg-surface pr-4 pl-10 text-sm focus:border-ink/40 focus:ring-4 focus:ring-signal/10 focus:outline-none"
          />
        </div>
        <label className="flex cursor-pointer items-center gap-2 text-sm text-ink-2 select-none">
          <input type="checkbox" checked={recruitingOnly} onChange={(e) => setRecruitingOnly(e.target.checked)} className="size-4 accent-[var(--color-signal)]" />
          Recruiting now
        </label>
      </div>
      <div className="-mx-4 mb-6 flex gap-1.5 overflow-x-auto px-4 no-scrollbar sm:mx-0 sm:flex-wrap sm:px-0">
        {categories.map((c) => (
          <button
            key={c}
            onClick={() => setCategory(c)}
            className={clsx(
              'h-8 shrink-0 rounded-md px-3 text-[13px] font-medium transition',
              category === c ? 'bg-signal-50 text-ink ring-1 ring-signal/30 ring-inset' : 'text-muted hover:bg-paper-2 hover:text-ink',
            )}
          >
            {c}
          </button>
        ))}
      </div>

      {clubs.isLoading && (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 9 }).map((_, i) => <Skeleton key={i} className="h-40" />)}
        </div>
      )}
      {shown?.length === 0 && (
        <EmptyState icon={<Users className="size-5" />} title="No clubs match">Try another name, category or turn off “Recruiting now”.</EmptyState>
      )}

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {shown?.map((club) => (
          <Link
            key={club.slug}
            to={`/app/directory/${club.slug}`}
            className="group flex flex-col rounded-[var(--radius-card)] border border-line bg-surface p-5 shadow-soft transition hover:-translate-y-0.5 hover:border-signal/40 hover:shadow-lift"
          >
            <div className="flex items-start gap-3">
              <Avatar name={club.name} size={42} square />
              <div className="min-w-0 flex-1">
                <p className="line-clamp-2 leading-snug font-bold group-hover:text-signal-ink">{club.name}</p>
                <p className="mt-0.5 truncate text-xs text-muted">{club.kind}{club.home ? ` · ${club.home.replace('Dept. of ', '')}` : ''}</p>
              </div>
            </div>
            {club.description && <p className="mt-3 line-clamp-2 text-[13px] text-ink-2">{club.description}</p>}
            <div className="mt-auto flex flex-wrap items-center gap-2 pt-4">
              <CategoryPill category={club.category} />
              {club.recruiting && (
                <span className="flex items-center gap-1 rounded-md bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber ring-1 ring-amber/20 ring-inset">
                  <Megaphone className="size-3" /> Recruiting
                </span>
              )}
              {club.upcomingEvents > 0 && (
                <span className="flex items-center gap-1 text-[11px] text-muted">
                  <CalendarDays className="size-3" /> {club.upcomingEvents} upcoming
                </span>
              )}
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}
