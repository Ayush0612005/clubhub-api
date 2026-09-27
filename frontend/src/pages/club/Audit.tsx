import { useQuery } from '@tanstack/react-query'
import { History } from 'lucide-react'
import { useState } from 'react'
import { Avatar, Button, Card, EmptyState, Skeleton } from '../../components/ui'
import { useClub } from '../../layouts/ClubLayout'
import { clubApi } from '../../lib/club'
import { fmt } from '../../lib/format'
import type { AuditEntry, Page } from '../../lib/types'

const actions = [
  'MEMBER_ADDED', 'MEMBER_ROLE_CHANGED', 'MEMBER_REMOVED', 'DRIVE_CREATED', 'DRIVE_STATUS_CHANGED', 'APPLICATION_MOVED',
  'EVENT_CREATED', 'EVENT_PUBLISHED', 'EVENT_CANCELLED', 'CERTIFICATES_ISSUED', 'CERTIFICATE_REVOKED', 'CLUB_PROFILE_UPDATED',
]

function describe(e: AuditEntry) {
  const d = e.details as Record<string, string | number>
  switch (e.action) {
    case 'MEMBER_ADDED': return <>added <b>{d.email}</b> as {fmt.label(String(d.role))}</>
    case 'MEMBER_ROLE_CHANGED': return <>changed a member's role from {fmt.label(String(d.from))} to <b>{fmt.label(String(d.to))}</b></>
    case 'MEMBER_REMOVED': return <>removed a {fmt.label(String(d.role)).toLowerCase()}</>
    case 'DRIVE_CREATED': return <>created the drive <b>{d.title}</b></>
    case 'DRIVE_STATUS_CHANGED': return <>moved drive #{e.targetId} from {fmt.label(String(d.from))} to <b>{fmt.label(String(d.to))}</b></>
    case 'APPLICATION_MOVED': return <>moved application #{e.targetId} to <b>{fmt.label(String(d.to))}</b></>
    case 'EVENT_CREATED': return <>created the event <b>{d.title}</b></>
    case 'EVENT_PUBLISHED': return <>published <b>{d.title}</b></>
    case 'EVENT_CANCELLED': return <>cancelled <b>{d.title}</b></>
    case 'CERTIFICATES_ISSUED': return <>issued <b>{d.issued}</b> certificates for event #{e.targetId}</>
    case 'CERTIFICATE_REVOKED': return <>revoked {d.recipient}'s certificate</>
    case 'CLUB_PROFILE_UPDATED': return <>updated the club profile</>
    default: return <>{fmt.label(e.action).toLowerCase()}</>
  }
}

export default function Audit() {
  const { club } = useClub()
  const [action, setAction] = useState('')
  const [page, setPage] = useState(0)
  const log = useQuery({
    queryKey: ['club', club.slug, 'audit', action, page],
    queryFn: () => clubApi(club.slug).get<Page<AuditEntry>>(`/audit?page=${page}&size=30${action ? `&action=${action}` : ''}`),
  })

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold">Audit log</h2>
          <p className="text-sm text-muted">Every change in this club, who made it and when. Entries can't be edited.</p>
        </div>
        <select
          value={action}
          onChange={(e) => {
            setAction(e.target.value)
            setPage(0)
          }}
          className="h-10 rounded-xl border border-line bg-surface px-3 text-sm"
          aria-label="Filter by action"
        >
          <option value="">All actions</option>
          {actions.map((a) => <option key={a} value={a}>{fmt.label(a)}</option>)}
        </select>
      </div>

      {log.isLoading && <Skeleton className="h-64" />}
      {log.data?.content.length === 0 && <EmptyState icon={<History className="size-5" />} title="Nothing recorded yet" />}
      {!!log.data?.content.length && (
        <Card className="p-2">
          <ol className="relative">
            {log.data.content.map((e) => (
              <li key={e.id} className="flex gap-4 rounded-xl px-4 py-3 hover:bg-paper-2/50">
                <Avatar name={e.actorEmail ?? '?'} size={32} />
                <div className="min-w-0 flex-1 text-sm">
                  <p>
                    <span className="font-semibold">{e.actorEmail ?? 'Someone'}</span> {describe(e)}
                  </p>
                  <p className="mt-0.5 font-mono text-[11px] text-muted">{e.action} · {fmt.dateTime(e.occurredAt)}</p>
                </div>
              </li>
            ))}
          </ol>
        </Card>
      )}
      {log.data && log.data.totalPages > 1 && (
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</Button>
          <Button variant="outline" size="sm" disabled={page + 1 >= log.data.totalPages} onClick={() => setPage(page + 1)}>Older</Button>
        </div>
      )}
    </div>
  )
}
