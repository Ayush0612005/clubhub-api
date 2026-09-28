import { api } from './api'

// ---- types (mirror com.clubhub.campus.CampusDtos)

export type ModerationStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type CampusSource = 'SRM_FEED' | 'ADMIN' | 'STUDENT'

export interface ClubRef {
  slug: string
  name: string
  category: string
}

export interface CampusClub {
  slug: string
  name: string
  category: string
  kind: string
  home: string | null
  description: string | null
  onClubHub: boolean
  upcomingEvents: number
  recruiting: boolean
}

export interface CampusEvent {
  id: string
  title: string
  description: string | null
  startsAt: string | null
  endsAt: string | null
  venue: string | null
  registrationUrl: string | null
  sourceUrl: string | null
  club: ClubRef | null
  source: CampusSource
  status: ModerationStatus
}

export interface CampusRecruitment {
  id: string
  title: string
  description: string | null
  applyUrl: string | null
  deadline: string | null
  club: ClubRef | null
  status: ModerationStatus
}

export interface CampusClubDetail extends Omit<CampusClub, 'onClubHub' | 'upcomingEvents' | 'recruiting'> {
  officialUrl: string | null
  sourceUrl: string | null
  workspaceSlug: string | null
  events: CampusEvent[]
  recruitments: CampusRecruitment[]
}

export interface EventInput {
  clubSlug: string | null
  title: string
  description: string | null
  startsAt: string
  endsAt: string | null
  venue: string | null
  registrationUrl: string | null
  sourceUrl: string | null
}

export interface RecruitmentInput {
  clubSlug: string
  title: string
  description: string | null
  applyUrl: string | null
  deadline: string | null
}

export interface ModerationQueue {
  events: { event: CampusEvent; submittedBy: string }[]
  recruitments: { recruitment: CampusRecruitment; submittedBy: string }[]
}

export interface ImportResult {
  inFeed: number
  added: number
  skippedPast: number
  alreadyKnown: number
}

// ---- api

export const campus = {
  clubs: () => api.get<CampusClub[]>('/api/campus/clubs'),
  club: (slug: string) => api.get<CampusClubDetail>(`/api/campus/clubs/${encodeURIComponent(slug)}`),
  events: () => api.get<CampusEvent[]>('/api/campus/events'),
  recruitments: () => api.get<CampusRecruitment[]>('/api/campus/recruitments'),
  suggestEvent: (input: EventInput) => api.post<CampusEvent>('/api/campus/suggestions/events', input),
  suggestRecruitment: (input: RecruitmentInput) => api.post<CampusRecruitment>('/api/campus/suggestions/recruitments', input),

  queue: () => api.get<ModerationQueue>('/api/platform/campus/queue'),
  importSrm: () => api.post<ImportResult>('/api/platform/campus/import/srm'),
  importSrmPaste: (xml: string) => api.post<ImportResult>('/api/platform/campus/import/srm-paste', { xml }),
  createEvent: (input: EventInput) => api.post<CampusEvent>('/api/platform/campus/events', input),
  updateEvent: (id: string, input: EventInput) => api.put<CampusEvent>(`/api/platform/campus/events/${id}`, input),
  reviewEvent: (id: string, decision: 'approve' | 'reject') => api.post<CampusEvent>(`/api/platform/campus/events/${id}/${decision}`),
  deleteEvent: (id: string) => api.del(`/api/platform/campus/events/${id}`),
  createRecruitment: (input: RecruitmentInput) => api.post<CampusRecruitment>('/api/platform/campus/recruitments', input),
  updateRecruitment: (id: string, input: RecruitmentInput) =>
    api.put<CampusRecruitment>(`/api/platform/campus/recruitments/${id}`, input),
  reviewRecruitment: (id: string, decision: 'approve' | 'reject') =>
    api.post<CampusRecruitment>(`/api/platform/campus/recruitments/${id}/${decision}`),
  deleteRecruitment: (id: string) => api.del(`/api/platform/campus/recruitments/${id}`),
}

// ---- dates (campus time is IST whatever the viewer's device says)

const IST = 'Asia/Kolkata'
const dayFmt = new Intl.DateTimeFormat('en-IN', { timeZone: IST, weekday: 'short', day: 'numeric', month: 'short' })
const timeFmt = new Intl.DateTimeFormat('en-IN', { timeZone: IST, hour: 'numeric', minute: '2-digit' })
const dayKeyFmt = new Intl.DateTimeFormat('en-CA', { timeZone: IST, year: 'numeric', month: '2-digit', day: '2-digit' })
const longDayFmt = new Intl.DateTimeFormat('en-IN', { timeZone: IST, weekday: 'long', day: 'numeric', month: 'long' })
const monthFmt = new Intl.DateTimeFormat('en-IN', { timeZone: IST, month: 'short' })
const dateNumFmt = new Intl.DateTimeFormat('en-IN', { timeZone: IST, day: 'numeric' })

/** Imported events have no time: they are stored at 00:00 IST, which we show as "date only". */
export function hasTime(iso: string) {
  return timeFmt.format(new Date(iso)) !== '12:00 am'
}

export const when = {
  /** "Sat, 5 Oct · 5:00 pm" or "Sat, 5 Oct" for date-only events; ranges collapse sensibly. */
  event(e: Pick<CampusEvent, 'startsAt' | 'endsAt'>) {
    if (!e.startsAt) return 'Date to be announced'
    const start = new Date(e.startsAt)
    const startDay = dayFmt.format(start)
    const startLabel = hasTime(e.startsAt) ? `${startDay} · ${timeFmt.format(start)}` : startDay
    if (!e.endsAt) return startLabel
    const end = new Date(e.endsAt)
    if (dayKeyFmt.format(start) === dayKeyFmt.format(end)) return `${startLabel} – ${timeFmt.format(end)}`
    return `${startDay} – ${dayFmt.format(end)}`
  },
  dayKey: (iso: string) => dayKeyFmt.format(new Date(iso)),
  dayHeading(iso: string) {
    const key = dayKeyFmt.format(new Date(iso))
    const today = dayKeyFmt.format(new Date())
    const tomorrow = dayKeyFmt.format(new Date(Date.now() + 86_400_000))
    if (key === today) return 'Today'
    if (key === tomorrow) return 'Tomorrow'
    return longDayFmt.format(new Date(iso))
  },
  month: (iso: string) => monthFmt.format(new Date(iso)).toUpperCase(),
  date: (iso: string) => dateNumFmt.format(new Date(iso)),
  deadline(isoDate: string) {
    // a LocalDate from the API ("2026-10-05"): parse as a campus-time date, not UTC midnight
    const days = Math.round((new Date(`${isoDate}T23:59:59+05:30`).getTime() - Date.now()) / 86_400_000)
    const label = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short' }).format(new Date(`${isoDate}T12:00:00+05:30`))
    if (days <= 0) return `Closes today`
    if (days === 1) return `Closes tomorrow (${label})`
    if (days <= 7) return `Closes in ${days} days (${label})`
    return `Apply by ${label}`
  },
}

// ---- add to calendar (.ics download: works with Google Calendar, Outlook and Apple Calendar)

function icsDate(iso: string, dateOnly: boolean) {
  if (dateOnly) return dayKeyFmt.format(new Date(iso)).replaceAll('-', '')
  return new Date(iso).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')
}

function icsText(s: string) {
  return s.replace(/\\/g, '\\\\').replace(/\n/g, '\\n').replace(/([,;])/g, '\\$1')
}

export function downloadIcs(e: CampusEvent) {
  if (!e.startsAt) return
  const dateOnly = !hasTime(e.startsAt)
  const end = e.endsAt ?? (dateOnly ? new Date(new Date(e.startsAt).getTime() + 86_400_000).toISOString() : new Date(new Date(e.startsAt).getTime() + 2 * 3_600_000).toISOString())
  const prop = dateOnly ? ';VALUE=DATE' : ''
  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//ClubHub//SRM KTR//EN',
    'BEGIN:VEVENT',
    `UID:${e.id}@clubhub`,
    `DTSTAMP:${icsDate(new Date().toISOString(), false)}`,
    `DTSTART${prop}:${icsDate(e.startsAt, dateOnly)}`,
    `DTEND${prop}:${icsDate(end, dateOnly)}`,
    `SUMMARY:${icsText(e.title)}`,
    e.venue ? `LOCATION:${icsText(e.venue)}` : '',
    `DESCRIPTION:${icsText([e.club?.name, e.registrationUrl ?? e.sourceUrl].filter(Boolean).join('\n'))}`,
    'END:VEVENT',
    'END:VCALENDAR',
  ].filter(Boolean)
  const url = URL.createObjectURL(new Blob([lines.join('\r\n')], { type: 'text/calendar' }))
  const a = document.createElement('a')
  a.href = url
  a.download = `${e.title.replace(/[^\w-]+/g, '-').slice(0, 60)}.ics`
  a.click()
  URL.revokeObjectURL(url)
}

/** Only ever put http(s) links into an href (the API validates too; this is belt and braces). */
export function safeUrl(url: string | null | undefined) {
  return url && /^https?:\/\//i.test(url) ? url : undefined
}
