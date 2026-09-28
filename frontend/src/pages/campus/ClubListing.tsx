import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, ArrowUpRight, CalendarDays, ExternalLink, Megaphone, Plus } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { CategoryPill, EventCard, EventFormModal, RecruitmentCard, RecruitmentFormModal } from '../../components/campus'
import { Avatar, Button, ButtonLink, EmptyState, Skeleton } from '../../components/ui'
import { campus, safeUrl } from '../../lib/campus'

/** One club's page in the campus directory: what it is, what it has coming up, whether it's recruiting. */
export default function ClubListing() {
  const { slug = '' } = useParams()
  const club = useQuery({ queryKey: ['campus', 'club', slug], queryFn: () => campus.club(slug) })
  const [suggestEvent, setSuggestEvent] = useState(false)
  const [suggestRecruitment, setSuggestRecruitment] = useState(false)

  if (club.isLoading) {
    return <div className="space-y-4"><Skeleton className="h-24" /><Skeleton className="h-40" /></div>
  }
  if (!club.data) {
    return <EmptyState icon={<CalendarDays className="size-5" />} title="Club not found" action={<ButtonLink to="/app/clubs">All clubs</ButtonLink>} />
  }
  const c = club.data
  const official = safeUrl(c.officialUrl)
  const source = safeUrl(c.sourceUrl)

  return (
    <div className="mx-auto max-w-4xl">
      <Link to="/app/clubs" className="mb-5 inline-flex items-center gap-1.5 text-sm text-muted hover:text-ink">
        <ArrowLeft className="size-4" /> All clubs
      </Link>

      <header className="flex flex-col gap-5 sm:flex-row sm:items-start">
        <Avatar name={c.name} size={64} square />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <CategoryPill category={c.category} />
            <span className="text-xs text-muted">{c.kind}{c.home ? ` · ${c.home}` : ''}</span>
          </div>
          <h1 className="mt-2 text-3xl font-bold tracking-tight">{c.name}</h1>
          {c.description && <p className="mt-2 text-ink-2">{c.description}</p>}
          <div className="mt-4 flex flex-wrap gap-2">
            {c.workspaceSlug && (
              <ButtonLink to={`/app/clubs/${c.workspaceSlug}`} size="sm" icon={<ArrowUpRight className="size-4" />}>Open on ClubHub</ButtonLink>
            )}
            {official && (
              <a href={official} target="_blank" rel="noopener noreferrer" className="inline-flex h-8 items-center gap-1.5 rounded-md border border-line px-2.5 text-[13px] text-ink hover:bg-paper-2">
                Official page <ExternalLink className="size-3.5" />
              </a>
            )}
            {source && (
              <a href={source} target="_blank" rel="noopener noreferrer" className="inline-flex h-8 items-center gap-1.5 rounded-md px-2.5 text-[13px] text-muted hover:bg-paper-2 hover:text-ink">
                Listed on srmist.edu.in <ExternalLink className="size-3.5" />
              </a>
            )}
          </div>
        </div>
      </header>

      <section className="mt-10">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-bold">Recruiting</h2>
          <Button variant="ghost" size="sm" icon={<Plus className="size-4" />} onClick={() => setSuggestRecruitment(true)}>Tell us</Button>
        </div>
        {c.recruitments.length === 0 ? (
          <p className="rounded-lg border border-dashed border-line p-5 text-sm text-muted">
            <Megaphone className="mr-2 inline size-4" />No open recruitment we know of. Seen their form? Use “Tell us”.
          </p>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2">{c.recruitments.map((r) => <RecruitmentCard key={r.id} recruitment={r} showClub={false} />)}</div>
        )}
      </section>

      <section className="mt-10">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-bold">Upcoming events</h2>
          <Button variant="ghost" size="sm" icon={<Plus className="size-4" />} onClick={() => setSuggestEvent(true)}>Suggest one</Button>
        </div>
        {c.events.length === 0 ? (
          <p className="rounded-lg border border-dashed border-line p-5 text-sm text-muted">
            <CalendarDays className="mr-2 inline size-4" />Nothing listed yet. Saw a poster? Suggest it and everyone will see it once it’s checked.
          </p>
        ) : (
          <div className="space-y-3">{c.events.map((e) => <EventCard key={e.id} event={e} showClub={false} />)}</div>
        )}
      </section>

      {!c.workspaceSlug && (
        <p className="mt-12 border-t border-line pt-6 text-sm text-muted">
          Run {c.name}? Your club can claim this page and get its own ClubHub workspace: recruitment pipeline, QR
          check-in and certificates, free.
        </p>
      )}

      <EventFormModal open={suggestEvent} onClose={() => setSuggestEvent(false)} mode="suggest" defaultClubSlug={slug} key={`e-${slug}`} />
      <RecruitmentFormModal open={suggestRecruitment} onClose={() => setSuggestRecruitment(false)} mode="suggest" defaultClubSlug={slug} key={`r-${slug}`} />
    </div>
  )
}
