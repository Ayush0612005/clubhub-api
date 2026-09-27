import clsx from 'clsx'
import { ArrowUpRight, Lock } from 'lucide-react'
import { NavLink, Outlet, useOutletContext, useParams } from 'react-router'
import { Avatar, ButtonLink, EmptyState, Skeleton, StatusBadge } from '../components/ui'
import { roleRank, useMyClub } from '../hooks/useAuth'
import type { ClubRole, MyClub } from '../lib/types'

export interface ClubContext {
  club: MyClub
  can: (role: ClubRole) => boolean
}

/** For pages inside /app/c/:slug. */
export function useClub() {
  return useOutletContext<ClubContext>()
}

const tabs: { to: string; label: string; min?: ClubRole; end?: boolean }[] = [
  { to: '', label: 'Overview', end: true },
  { to: 'events', label: 'Events' },
  { to: 'recruitment', label: 'Recruitment' },
  { to: 'members', label: 'Members' },
  { to: 'audit', label: 'Audit log', min: 'CLUB_ADMIN' },
  { to: 'settings', label: 'Settings' },
]

export default function ClubLayout() {
  const { slug = '' } = useParams()
  const { club, isLoading } = useMyClub(slug)

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-16 w-72" />
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }
  if (!club) {
    return (
      <EmptyState
        icon={<Lock className="size-5" />}
        title="This workspace is for members"
        action={<ButtonLink to={`/app/clubs/${slug}`} variant="outline">See the club's public page</ButtonLink>}
      >
        You're not a member of this club. You can still browse its public events and open recruitment drives.
      </EmptyState>
    )
  }

  const can = (role: ClubRole) => roleRank[club.role] >= roleRank[role]

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-center gap-4">
        <Avatar name={club.name} size={52} square />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="truncate text-2xl font-bold sm:text-3xl">{club.name}</h1>
            <StatusBadge status={club.plan} />
          </div>
          <p className="mt-0.5 text-sm text-muted">
            You're <span className="font-medium text-ink-2">{club.role === 'CLUB_ADMIN' ? 'an admin' : club.role === 'CORE' ? 'on the core team' : 'a member'}</span> ·{' '}
            <span className="font-mono text-xs">/{club.slug}</span>
          </p>
        </div>
        <ButtonLink to={`/app/clubs/${slug}`} variant="ghost" size="sm" icon={<ArrowUpRight className="size-4" />}>
          Public page
        </ButtonLink>
      </div>

      <nav className="-mx-4 mb-8 flex gap-1 no-scrollbar overflow-x-auto overflow-y-hidden border-b border-line px-4 sm:mx-0 sm:px-0">
        {tabs
          .filter((tab) => !tab.min || can(tab.min))
          .map((tab) => (
            <NavLink
              key={tab.label}
              to={tab.to}
              end={tab.end}
              className={({ isActive }) =>
                clsx(
                  '-mb-px border-b-2 px-3 py-2.5 text-sm font-medium whitespace-nowrap transition',
                  isActive ? 'border-signal text-ink' : 'border-transparent text-muted hover:text-ink',
                )
              }
            >
              {tab.label}
            </NavLink>
          ))}
      </nav>

      <Outlet context={{ club, can } satisfies ClubContext} />
    </div>
  )
}
