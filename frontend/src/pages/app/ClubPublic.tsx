import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Award, CalendarDays, Download, ExternalLink, FileText, Inbox, Megaphone } from 'lucide-react'
import { Link, useLocation, useParams } from 'react-router'
import { EventRow } from '../../components/EventRow'
import { Avatar, Badge, Button, ButtonLink, Card, EmptyState, Skeleton, StatusBadge } from '../../components/ui'
import { useToast } from '../../components/toast'
import { useMyClub } from '../../hooks/useAuth'
import { api } from '../../lib/api'
import { publicClubApi } from '../../lib/club'
import { fmt } from '../../lib/format'
import type { Certificate, ClubCard, ClubEvent, DriveSummary, MyApplication } from '../../lib/types'

type Tab = 'events' | 'recruitment' | 'applications' | 'certificates'

function tabFrom(path: string): Tab {
  if (path.includes('/applications')) return 'applications'
  if (path.includes('/certificates')) return 'certificates'
  if (path.includes('/recruitment')) return 'recruitment'
  return 'events'
}

const tabs: { id: Tab; label: string; path: string }[] = [
  { id: 'events', label: 'Events', path: '' },
  { id: 'recruitment', label: 'Recruitment', path: '/recruitment' },
  { id: 'applications', label: 'My applications', path: '/recruitment/applications/mine' },
  { id: 'certificates', label: 'My certificates', path: '/certificates' },
]

export default function ClubPublic() {
  const { slug = '' } = useParams()
  const location = useLocation()
  const tab = tabFrom(location.pathname)
  const directory = useQuery({ queryKey: ['directory'], queryFn: () => api.get<ClubCard[]>('/api/clubs') })
  const { club: membership } = useMyClub(slug)
  const name = directory.data?.find((c) => c.slug === slug)?.name ?? slug

  return (
    <div>
      <div className="relative mb-8 overflow-hidden rounded-[2rem] bg-ink p-8 text-paper sm:p-10">
        <div className="absolute -top-20 -right-10 size-72 rounded-full bg-signal/25 blur-3xl" />
        <div className="relative flex flex-wrap items-end justify-between gap-6">
          <div className="flex items-center gap-5">
            <Avatar name={name} size={72} square />
            <div>
              <p className="font-mono text-xs tracking-widest text-paper/50 uppercase">Club</p>
              <h1 className="text-3xl font-bold sm:text-4xl">{name}</h1>
            </div>
          </div>
          {membership && (
            <ButtonLink to={`/app/c/${slug}`} variant="primary">Open workspace</ButtonLink>
          )}
        </div>
      </div>

      <nav className="mb-6 flex gap-2 no-scrollbar overflow-x-auto overflow-y-hidden">
        {tabs.map((t) => (
          <Link
            key={t.id}
            to={`/app/clubs/${slug}${t.path}`}
            className={clsx('rounded-full px-4 py-2 text-sm font-medium whitespace-nowrap transition', tab === t.id ? 'bg-ink text-paper' : 'bg-surface text-ink-2 ring-1 ring-line hover:ring-ink/30')}
          >
            {t.label}
          </Link>
        ))}
      </nav>

      {tab === 'events' && <EventsTab slug={slug} />}
      {tab === 'recruitment' && <DrivesTab slug={slug} />}
      {tab === 'applications' && <ApplicationsTab slug={slug} />}
      {tab === 'certificates' && <CertificatesTab slug={slug} />}
    </div>
  )
}

function EventsTab({ slug }: { slug: string }) {
  const events = useQuery({ queryKey: ['public-events', slug], queryFn: () => publicClubApi(slug).get<ClubEvent[]>('/events') })
  if (events.isLoading) return <div className="space-y-3"><Skeleton className="h-24" /><Skeleton className="h-24" /></div>
  if (!events.data?.length) return <EmptyState icon={<CalendarDays className="size-5" />} title="No upcoming public events">Check back soon.</EmptyState>
  return (
    <div className="space-y-3">
      {events.data.map((e) => <EventRow key={e.id} event={e} to={`/app/clubs/${slug}/events/${e.id}`} />)}
    </div>
  )
}

function DrivesTab({ slug }: { slug: string }) {
  const drives = useQuery({ queryKey: ['public-drives', slug], queryFn: () => publicClubApi(slug).get<DriveSummary[]>('/recruitment/drives') })
  if (drives.isLoading) return <Skeleton className="h-32" />
  if (!drives.data?.length) return <EmptyState icon={<Megaphone className="size-5" />} title="Not recruiting right now">When this club opens a drive, you can apply here.</EmptyState>
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {drives.data.map((d) => (
        <Card key={d.id} className="flex flex-col p-6">
          <div className="flex items-center gap-2">
            <span className="relative flex size-2"><span className="absolute inline-flex size-full animate-ping rounded-full bg-forest opacity-60" /><span className="relative size-2 rounded-full bg-forest" /></span>
            <span className="text-xs font-medium text-forest">Accepting applications</span>
          </div>
          <p className="mt-3 font-display text-xl font-bold">{d.title}</p>
          <p className="mt-1 text-sm text-muted">{d.closesAt ? `Closes ${fmt.dateTime(d.closesAt)}` : 'Open until the team closes it'}</p>
          <ButtonLink to={`/app/clubs/${slug}/recruitment/drives/${d.id}`} className="mt-6 self-start">Apply now</ButtonLink>
        </Card>
      ))}
    </div>
  )
}

function ApplicationsTab({ slug }: { slug: string }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const apps = useQuery({ queryKey: ['my-applications', slug], queryFn: () => publicClubApi(slug).get<MyApplication[]>('/recruitment/applications/mine') })
  const withdraw = useMutation({
    mutationFn: (id: number) => publicClubApi(slug).post(`/recruitment/applications/${id}/withdraw`),
    onSuccess: () => {
      toast.success('Application withdrawn')
      queryClient.invalidateQueries({ queryKey: ['my-applications', slug] })
    },
    onError: toast.error,
  })

  if (apps.isLoading) return <Skeleton className="h-24" />
  if (!apps.data?.length) return <EmptyState icon={<Inbox className="size-5" />} title="No applications yet">Apply to an open drive and track it here.</EmptyState>
  const steps = ['APPLIED', 'SHORTLISTED', 'INTERVIEW', 'SELECTED']
  return (
    <div className="space-y-3">
      {apps.data.map((a) => {
        const reached = steps.indexOf(a.status)
        return (
          <Card key={a.id} className="p-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="font-display text-lg font-bold">{a.driveTitle ?? 'Recruitment drive'}</p>
                <p className="text-[13px] text-muted">Applied {fmt.ago(a.submittedAt)} · updated {fmt.ago(a.updatedAt)}</p>
              </div>
              <div className="flex items-center gap-2">
                <StatusBadge status={a.status} />
                {['APPLIED', 'SHORTLISTED', 'INTERVIEW'].includes(a.status) && (
                  <Button variant="ghost" size="sm" loading={withdraw.isPending && withdraw.variables === a.id} onClick={() => withdraw.mutate(a.id)}>
                    Withdraw
                  </Button>
                )}
              </div>
            </div>
            {reached >= 0 && (
              <div className="mt-5 grid grid-cols-4 gap-2">
                {steps.map((s, i) => (
                  <div key={s}>
                    <div className={clsx('h-1.5 rounded-full', i <= reached ? (a.status === 'SELECTED' ? 'bg-forest' : 'bg-signal') : 'bg-paper-2')} />
                    <p className={clsx('mt-1.5 text-[11px]', i <= reached ? 'text-ink-2' : 'text-muted')}>{fmt.label(s)}</p>
                  </div>
                ))}
              </div>
            )}
          </Card>
        )
      })}
    </div>
  )
}

function CertificatesTab({ slug }: { slug: string }) {
  const toast = useToast()
  const certs = useQuery({ queryKey: ['my-certificates', slug], queryFn: () => publicClubApi(slug).get<Certificate[]>('/certificates/mine') })

  async function download(cert: Certificate) {
    try {
      const url = await publicClubApi(slug).blobUrl(`/certificates/${cert.id}/pdf`)
      const a = document.createElement('a')
      a.href = url
      a.download = `certificate-${cert.id}.pdf`
      a.click()
      setTimeout(() => URL.revokeObjectURL(url), 5000)
    } catch (err) {
      toast.error(err)
    }
  }

  if (certs.isLoading) return <Skeleton className="h-24" />
  if (!certs.data?.length) return <EmptyState icon={<Award className="size-5" />} title="No certificates yet">Attend this club's events — certificates are issued to everyone checked in.</EmptyState>
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {certs.data.map((c) => (
        <Card key={c.id} className="relative overflow-hidden p-6">
          <div className="absolute top-0 right-0 h-full w-1.5 bg-signal" />
          <FileText className="size-6 text-signal" />
          <p className="mt-4 font-mono text-[11px] tracking-widest text-muted uppercase">{c.title}</p>
          <p className="mt-1 font-display text-lg font-bold">{c.recipientName}</p>
          <p className="mt-1 text-sm text-muted">{c.description}</p>
          <div className="mt-5 flex flex-wrap items-center gap-2">
            {c.revoked ? (
              <Badge tone="berry" dot>Revoked</Badge>
            ) : (
              <Button size="sm" variant="ink" icon={<Download className="size-4" />} onClick={() => download(c)}>PDF</Button>
            )}
            <ButtonLink size="sm" variant="ghost" to={`/verify/${slug}/${c.id}`} target="_blank" icon={<ExternalLink className="size-4" />}>Verify page</ButtonLink>
          </div>
        </Card>
      ))}
    </div>
  )
}
