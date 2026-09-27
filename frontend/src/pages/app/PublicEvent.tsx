import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, CalendarDays, Clock, MapPin, Users } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { DateTile } from '../../components/art'
import { Ticket } from '../../components/Ticket'
import { Button, Card, ErrorNote, Skeleton } from '../../components/ui'
import { useToast } from '../../components/toast'
import { api, ApiError } from '../../lib/api'
import { publicClubApi } from '../../lib/club'
import { fmt } from '../../lib/format'
import type { ClubCard, ClubEvent } from '../../lib/types'

function Fact({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <div className="flex items-center gap-3 text-sm">
      <span className="grid size-9 place-items-center rounded-xl bg-paper-2 text-ink-2 [&>svg]:size-4">{icon}</span>
      <span>{children}</span>
    </div>
  )
}

export default function PublicEvent() {
  const { slug = '', eventId = '' } = useParams()
  const club = publicClubApi(slug)
  const queryClient = useQueryClient()
  const toast = useToast()
  const directory = useQuery({ queryKey: ['directory'], queryFn: () => api.get<ClubCard[]>('/api/clubs') })
  const clubName = directory.data?.find((c) => c.slug === slug)?.name ?? slug

  const event = useQuery({ queryKey: ['public-event', slug, eventId], queryFn: () => club.get<ClubEvent>(`/events/${eventId}`) })
  // 404 from the ticket endpoint simply means "not registered"
  const registered = useQuery({
    queryKey: ['registered', slug, eventId],
    queryFn: () =>
      club.get(`/events/${eventId}/ticket`).then(
        () => true,
        (e) => {
          if (e instanceof ApiError && e.status === 404) return false
          throw e
        },
      ),
  })

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['public-event', slug, eventId] })
    queryClient.invalidateQueries({ queryKey: ['registered', slug, eventId] })
    queryClient.invalidateQueries({ queryKey: ['public-events', slug] })
  }
  const register = useMutation({
    mutationFn: () => club.post(`/events/${eventId}/registration`),
    onSuccess: () => {
      toast.success("You're in!", 'Your ticket is ready below.')
      refresh()
    },
    onError: toast.error,
  })
  const unregister = useMutation({
    mutationFn: () => club.del(`/events/${eventId}/registration`),
    onSuccess: () => {
      toast.success('Registration cancelled')
      refresh()
    },
    onError: toast.error,
  })

  if (event.isLoading) return <Skeleton className="h-96" />
  if (event.error || !event.data) return <ErrorNote error={event.error ?? new Error('Event not found')} />
  const e = event.data
  const started = new Date(e.startsAt) <= new Date()
  const full = e.capacity != null && e.registeredCount >= e.capacity

  return (
    <div>
      <Link to={`/app/clubs/${slug}`} className="mb-6 inline-flex items-center gap-1.5 text-sm text-muted hover:text-ink">
        <ArrowLeft className="size-4" /> {clubName}
      </Link>
      <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
        <div className="animate-rise">
          <div className="flex items-start gap-5">
            <DateTile iso={e.startsAt} className="scale-110" />
            <div>
              <p className="font-mono text-xs tracking-widest text-muted uppercase">{clubName}</p>
              <h1 className="mt-1 text-4xl font-bold">{e.title}</h1>
            </div>
          </div>
          <Card className="mt-8 grid gap-4 p-6 sm:grid-cols-2">
            <Fact icon={<CalendarDays />}>{fmt.date(e.startsAt)}</Fact>
            <Fact icon={<Clock />}>{fmt.time(e.startsAt)} – {fmt.time(e.endsAt)}</Fact>
            <Fact icon={<MapPin />}>{e.venue}</Fact>
            <Fact icon={<Users />}>{e.registeredCount}{e.capacity != null ? ` of ${e.capacity} seats taken` : ' going'}</Fact>
          </Card>
          {e.description && <p className="mt-8 text-[15px] leading-relaxed whitespace-pre-line text-ink-2">{e.description}</p>}
        </div>

        <div>
          {registered.data ? (
            <div className="space-y-3">
              <Ticket event={e} clubName={clubName} loadQr={() => club.blobUrl(`/events/${eventId}/ticket/qr`)} />
              {!started && (
                <Button variant="ghost" className="w-full" loading={unregister.isPending} onClick={() => unregister.mutate()}>
                  Can't make it? Cancel registration
                </Button>
              )}
            </div>
          ) : (
            <Card className="p-6">
              <p className="font-display text-xl font-bold">{started ? 'Registration has closed' : full ? 'This event is full' : 'Save your seat'}</p>
              <p className="mt-1 text-sm text-muted">
                {started ? 'The event has already started.' : 'You’ll get a personal QR ticket to show at the door.'}
              </p>
              <Button size="lg" className="mt-6 w-full" disabled={started || full || registered.isLoading} loading={register.isPending} onClick={() => register.mutate()}>
                Register
              </Button>
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}
