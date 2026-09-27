import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Award, BellOff, CalendarPlus, CheckCheck, UserRoundCheck } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Button, Card, EmptyState, PageHeader, Skeleton } from '../../components/ui'
import { api } from '../../lib/api'
import { fmt } from '../../lib/format'
import type { Notification, Page } from '../../lib/types'

const icons: Record<string, typeof Award> = {
  APPLICATION_STATUS_CHANGED: UserRoundCheck,
  CERTIFICATE_ISSUED: Award,
  EVENT_PUBLISHED: CalendarPlus,
}

export default function Notifications() {
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const inbox = useQuery({
    queryKey: ['notifications', 'inbox', unreadOnly, page],
    queryFn: () => api.get<Page<Notification>>(`/api/notifications?unreadOnly=${unreadOnly}&page=${page}&size=20`),
  })
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['notifications'] })
  const readAll = useMutation({ mutationFn: () => api.post('/api/notifications/read-all'), onSuccess: invalidate })
  const read = useMutation({ mutationFn: (id: number) => api.post(`/api/notifications/${id}/read`), onSuccess: invalidate })

  function open(n: Notification) {
    if (!n.read) read.mutate(n.id)
    if (n.link) navigate(`/app${n.link}`)
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        eyebrow="Inbox"
        title="Notifications"
        description="Everything from all your clubs, in real time."
        actions={<Button variant="outline" icon={<CheckCheck className="size-4" />} loading={readAll.isPending} onClick={() => readAll.mutate()}>Mark all read</Button>}
      />

      <div className="mb-4 inline-flex rounded-xl bg-paper-2 p-1 text-sm">
        {[false, true].map((value) => (
          <button
            key={String(value)}
            onClick={() => {
              setUnreadOnly(value)
              setPage(0)
            }}
            className={clsx('rounded-lg px-3.5 py-1.5 font-medium transition', unreadOnly === value ? 'bg-surface shadow-soft' : 'text-muted hover:text-ink')}
          >
            {value ? 'Unread' : 'All'}
          </button>
        ))}
      </div>

      {inbox.isLoading && <div className="space-y-2"><Skeleton className="h-20" /><Skeleton className="h-20" /><Skeleton className="h-20" /></div>}
      {inbox.data?.content.length === 0 && <EmptyState icon={<BellOff className="size-5" />} title={unreadOnly ? 'All caught up' : 'No notifications yet'} />}

      {!!inbox.data?.content.length && (
        <Card className="divide-y divide-line overflow-hidden">
          {inbox.data.content.map((n) => {
            const Icon = icons[n.type] ?? UserRoundCheck
            return (
              <button key={n.id} onClick={() => open(n)} className={clsx('flex w-full gap-4 p-5 text-left transition hover:bg-paper-2/60', !n.read && 'bg-signal-50/40')}>
                <span className={clsx('grid size-10 shrink-0 place-items-center rounded-xl', n.read ? 'bg-paper-2 text-muted' : 'bg-signal text-white')}>
                  <Icon className="size-[18px]" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex items-center justify-between gap-3">
                    <span className="truncate font-semibold">{n.title}</span>
                    <span className="shrink-0 text-xs text-muted">{fmt.ago(n.createdAt)}</span>
                  </span>
                  <span className="mt-0.5 block text-sm text-muted">{n.body}</span>
                </span>
              </button>
            )
          })}
        </Card>
      )}

      {inbox.data && inbox.data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-muted">
          <span>Page {page + 1} of {inbox.data.totalPages}</span>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
            <Button variant="outline" size="sm" disabled={page + 1 >= inbox.data.totalPages} onClick={() => setPage(page + 1)}>Next</Button>
          </div>
        </div>
      )}
    </div>
  )
}
