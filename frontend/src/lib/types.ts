// Shapes of the ClubHub API responses (mirrors the Java records).

export type ClubRole = 'CLUB_ADMIN' | 'CORE' | 'MEMBER'
export type PlanName = 'FREE' | 'PRO'

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresAt: string
  refreshToken: string
  refreshExpiresAt: string
  club: ActiveClub | null
}

export interface ActiveClub {
  slug: string
  role: ClubRole
}

export interface ClubTokenResponse {
  accessToken: string
  expiresAt: string
  club: ActiveClub
}

export interface Me {
  id: string
  email: string
  fullName: string
  platformAdmin: boolean
}

export interface MyClub {
  tenantId: string
  slug: string
  name: string
  role: ClubRole
  plan: PlanName
}

export interface ClubCard {
  slug: string
  name: string
}

export interface ClubProfile {
  displayName: string
  description: string | null
  contactEmail: string | null
  updatedAt: string
}

export interface Member {
  userId: string
  email: string
  fullName: string
  role: ClubRole
  joinedAt: string
}

// ---- recruitment
export type DriveStatus = 'DRAFT' | 'OPEN' | 'CLOSED'
export type ApplicationStatus = 'APPLIED' | 'SHORTLISTED' | 'INTERVIEW' | 'SELECTED' | 'REJECTED' | 'WITHDRAWN'

export interface DriveSummary {
  id: number
  title: string
  status: DriveStatus
  closesAt: string | null
  createdAt: string
}

export interface Question {
  id: number
  sortOrder: number
  prompt: string
  required: boolean
}

export interface Drive extends DriveSummary {
  description: string | null
  questions: Question[]
}

export interface Applicant {
  userId: string
  email: string | null
  fullName: string | null
}

export interface ApplicationSummary {
  id: number
  applicant: Applicant
  status: ApplicationStatus
  submittedAt: string
  updatedAt: string
}

export interface ApplicationDetail {
  id: number
  driveId: number
  applicant: Applicant
  status: ApplicationStatus
  submittedAt: string
  answers: { questionId: number; prompt: string | null; answer: string }[]
  history: { from: ApplicationStatus | null; to: ApplicationStatus; changedBy: string; note: string | null; changedAt: string }[]
}

export interface MyApplication {
  id: number
  driveId: number
  driveTitle: string | null
  status: ApplicationStatus
  submittedAt: string
  updatedAt: string
}

// ---- events
export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED'
export type Visibility = 'PUBLIC' | 'MEMBERS'

export interface ClubEvent {
  id: number
  title: string
  description: string | null
  venue: string
  startsAt: string
  endsAt: string
  capacity: number | null
  visibility: Visibility
  status: EventStatus
  registeredCount: number
}

export interface Registrant {
  userId: string
  email: string | null
  fullName: string | null
  registeredAt: string
  attended: boolean
}

export interface CheckIn {
  userId: string
  email: string | null
  fullName: string | null
  method: 'QR' | 'MANUAL'
  checkedInAt: string
}

export interface AttendanceSummary {
  eventId: number
  registered: number
  attended: number
  attendees: CheckIn[]
}

// ---- certificates
export interface Certificate {
  id: string
  eventId: number | null
  title: string
  recipientName: string
  description: string
  issuedAt: string
  revoked: boolean
  verifyUrl: string
}

export interface Verification {
  id: string
  valid: boolean
  clubName: string
  title: string
  recipientName: string
  description: string
  issuedAt: string
  revokedAt: string | null
}

// ---- notifications, plan, audit, platform
export interface Notification {
  id: number
  type: string
  title: string
  body: string
  link: string | null
  createdAt: string
  read: boolean
}

export type LimitName = 'MEMBERS' | 'UPCOMING_EVENTS' | 'OPEN_DRIVES'
export type FeatureName = 'CERTIFICATES' | 'EVENT_POSTERS' | 'EMAIL_NOTIFICATIONS'

export interface PlanView {
  plan: PlanName
  limits: Record<LimitName, number>
  usage: Record<LimitName, number>
  features: Record<FeatureName, boolean>
  requestsPerMinute: number
}

export interface AuditEntry {
  id: number
  actorId: string
  actorEmail: string | null
  action: string
  targetType: string
  targetId: string
  details: Record<string, unknown>
  occurredAt: string
}

export interface Tenant {
  id: string
  slug: string
  name: string
  status: 'ACTIVE' | 'SUSPENDED'
  plan: PlanName
  createdAt: string
}
