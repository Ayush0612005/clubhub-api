import { ChevronRight, MapPin, Users } from 'lucide-react'
import { Link } from 'react-router'
import { fmt } from '../lib/format'
import type { ClubEvent } from '../lib/types'
import { DateTile } from './art'
import { Badge, StatusBadge } from './ui'

export function EventRow({ event, to, showStatus }: { event: ClubEvent; to: string; showStatus?: boolean }) {
  const full = event.capacity != null && event.registeredCount >= event.capacity
  return (
    <Link to={to} className="group flex items-center gap-4 rounded-[var(--radius-card)] border border-line bg-surface p-4 shadow-soft transition hover:-translate-y-0.5 hover:shadow-lift">
      <DateTile iso={event.startsAt} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate font-display text-lg font-bold">{event.title}</p>
          {showStatus && event.status !== 'PUBLISHED' && <StatusBadge status={event.status} />}
          {event.visibility === 'MEMBERS' && <Badge tone="cobalt">Members only</Badge>}
          {full && <Badge tone="berry">Full</Badge>}
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-[13px] text-muted">
          <span>{fmt.weekday(event.startsAt)} · {fmt.time(event.startsAt)} – {fmt.time(event.endsAt)}</span>
          <span className="inline-flex items-center gap-1"><MapPin className="size-3.5" />{event.venue}</span>
          <span className="inline-flex items-center gap-1">
            <Users className="size-3.5" />
            {event.registeredCount}
            {event.capacity != null && ` / ${event.capacity}`} going
          </span>
        </div>
      </div>
      <ChevronRight className="size-5 shrink-0 text-muted transition group-hover:translate-x-0.5 group-hover:text-ink" />
    </Link>
  )
}
