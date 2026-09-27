import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarPlus, Plus } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { EventRow } from '../../components/EventRow'
import { Button, EmptyState, ErrorNote, Input, Modal, Select, Skeleton, Textarea } from '../../components/ui'
import { useToast } from '../../components/toast'
import { useClub } from '../../layouts/ClubLayout'
import { clubApi } from '../../lib/club'
import { localInput } from '../../lib/format'
import type { ClubEvent, Visibility } from '../../lib/types'

function inDays(days: number, hour: number) {
  const d = new Date()
  d.setDate(d.getDate() + days)
  d.setHours(hour, 0, 0, 0)
  return localInput.fromDate(d)
}

export default function Events() {
  const { club, can } = useClub()
  const api = clubApi(club.slug)
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [params, setParams] = useSearchParams()
  const creating = params.get('new') === '1'
  const [form, setForm] = useState({
    title: '',
    description: '',
    venue: '',
    startsAt: inDays(7, 17),
    endsAt: inDays(7, 19),
    capacity: '',
    visibility: 'PUBLIC' as Visibility,
  })

  const events = useQuery({ queryKey: ['club', club.slug, 'events'], queryFn: () => api.get<ClubEvent[]>('/events') })
  const create = useMutation({
    mutationFn: () =>
      api.post<ClubEvent>('/events', {
        title: form.title,
        description: form.description || null,
        venue: form.venue,
        startsAt: localInput.toIso(form.startsAt),
        endsAt: localInput.toIso(form.endsAt),
        capacity: form.capacity ? Number(form.capacity) : null,
        visibility: form.visibility,
      }),
    onSuccess: (event) => {
      toast.success('Draft created', 'Publish it when you’re ready — members get notified.')
      queryClient.invalidateQueries({ queryKey: ['club', club.slug] })
      navigate(`${event.id}`)
    },
  })

  const close = () => setParams({})
  function submit(e: FormEvent) {
    e.preventDefault()
    create.mutate()
  }

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-xl font-bold">Upcoming events</h2>
        {can('CORE') && <Button icon={<Plus className="size-4" />} onClick={() => setParams({ new: '1' })}>New event</Button>}
      </div>

      {events.isLoading && <div className="space-y-3"><Skeleton className="h-24" /><Skeleton className="h-24" /></div>}
      {events.data?.length === 0 && (
        <EmptyState icon={<CalendarPlus className="size-5" />} title="No upcoming events" action={can('CORE') && <Button onClick={() => setParams({ new: '1' })}>Plan one</Button>}>
          Workshops, hack nights, meetups — create one and publish it to your members.
        </EmptyState>
      )}
      <div className="space-y-3">
        {events.data?.map((e) => <EventRow key={e.id} event={e} to={`${e.id}`} showStatus />)}
      </div>

      <Modal open={creating} onClose={close} title="New event" description="It starts as a draft only the core team can see." wide>
        <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <Input label="Title" required maxLength={150} placeholder="Hack Night 2026" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
          </div>
          <div className="sm:col-span-2">
            <Input label="Venue" required maxLength={200} placeholder="TP Ganesan Auditorium" value={form.venue} onChange={(e) => setForm({ ...form, venue: e.target.value })} />
          </div>
          <Input label="Starts" type="datetime-local" required value={form.startsAt} onChange={(e) => setForm({ ...form, startsAt: e.target.value })} />
          <Input label="Ends" type="datetime-local" required value={form.endsAt} onChange={(e) => setForm({ ...form, endsAt: e.target.value })} />
          <Input label="Capacity" type="number" min={1} placeholder="Unlimited" value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} />
          <Select label="Who can see it" value={form.visibility} onChange={(e) => setForm({ ...form, visibility: e.target.value as Visibility })}>
            <option value="PUBLIC">Everyone (public)</option>
            <option value="MEMBERS">Members only</option>
          </Select>
          <div className="sm:col-span-2">
            <Textarea label="Description" maxLength={5000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
          <div className="space-y-3 sm:col-span-2">
            <ErrorNote error={create.error} />
            <Button type="submit" className="w-full" loading={create.isPending}>Create draft</Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
