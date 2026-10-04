import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { CalendarDays, Plus, Search } from 'lucide-react'
import { useMemo, useState } from 'react'
import { EventCard, EventFormModal } from '../../components/campus'
import { Button, EmptyState, PageHeader, Skeleton } from '../../components/ui'
import { useSignedInAction } from '../../hooks/useAuth'
import { campus, when, type CampusEvent } from '../../lib/campus'

const filters = ['All', 'Technical', 'Cultural', 'Sports', 'University'] as const
type Filter = (typeof filters)[number]

function matches(e: CampusEvent, filter: Filter, query: string) {
  if (filter === 'University' && e.club) return false
  if (filter !== 'All' && filter !== 'University') {
    const cat = e.club?.category
    const group = filter === 'Cultural' ? ['Cultural', 'Literary', 'Talks'] : filter === 'Sports' ? ['Sports', 'Recreation'] : [filter]
    if (!cat || !group.includes(cat)) return false
  }
  const q = query.trim().toLowerCase()
  return !q || [e.title, e.club?.name, e.venue, e.description].some((f) => f?.toLowerCase().includes(q))
}

/** Every approved event on campus, grouped by day. */
export default function WhatsOn() {
  const events = useQuery({ queryKey: ['campus', 'events'], queryFn: campus.events })
  const [filter, setFilter] = useState<Filter>('All')
  const [query, setQuery] = useState('')
  const [suggesting, setSuggesting] = useState(false)
  const withAccount = useSignedInAction()

  const days = useMemo(() => {
    const groups = new Map<string, CampusEvent[]>()
    for (const e of events.data ?? []) {
      if (!e.startsAt || !matches(e, filter, query)) continue
      const key = when.dayKey(e.startsAt)
      groups.set(key, [...(groups.get(key) ?? []), e])
    }
    return [...groups.values()]
  }, [events.data, filter, query])

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader
        eyebrow="SRM KTR"
        title="What's on"
        description="Every club, department and university event in one place. Register on the organiser's own form."
        actions={<Button variant="outline" icon={<Plus className="size-4" />} onClick={withAccount(() => setSuggesting(true))}>Suggest an event</Button>}
      />

      <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative flex-1 sm:max-w-xs">
          <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search events, clubs, venues"
            className="h-10 w-full rounded-lg border border-line bg-surface pr-4 pl-10 text-sm focus:border-ink/40 focus:ring-4 focus:ring-signal/10 focus:outline-none"
          />
        </div>
        <div className="-mx-4 flex gap-1.5 overflow-x-auto px-4 no-scrollbar sm:mx-0 sm:px-0">
          {filters.map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={clsx(
                'h-9 shrink-0 rounded-md px-3 text-sm font-medium transition',
                filter === f ? 'bg-signal-50 text-ink ring-1 ring-signal/30 ring-inset' : 'text-muted hover:bg-paper-2 hover:text-ink',
              )}
            >
              {f}
            </button>
          ))}
        </div>
      </div>

      {events.isLoading && <div className="space-y-3"><Skeleton className="h-36" /><Skeleton className="h-36" /><Skeleton className="h-36" /></div>}

      {events.data && days.length === 0 && (
        <EmptyState
          icon={<CalendarDays className="size-5" />}
          title={events.data.length === 0 ? 'Nothing listed yet' : 'No events match'}
          action={<Button onClick={withAccount(() => setSuggesting(true))}>Suggest an event</Button>}
        >
          {events.data.length === 0
            ? 'Know about something coming up? Add it and everyone at SRM KTR will see it once it’s checked.'
            : 'Try another filter or search term.'}
        </EmptyState>
      )}

      <div className="space-y-8">
        {days.map((dayEvents) => (
          <section key={when.dayKey(dayEvents[0].startsAt!)}>
            <h2 className="sticky top-14 z-10 -mx-1 mb-3 bg-paper/90 px-1 py-1.5 font-mono text-xs font-medium tracking-[0.14em] text-muted uppercase backdrop-blur lg:top-0">
              {when.dayHeading(dayEvents[0].startsAt!)}
            </h2>
            <div className="space-y-3">
              {dayEvents.map((e) => <EventCard key={e.id} event={e} />)}
            </div>
          </section>
        ))}
      </div>

      <EventFormModal open={suggesting} onClose={() => setSuggesting(false)} mode="suggest" />
    </div>
  )
}
