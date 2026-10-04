import { useQuery } from '@tanstack/react-query'
import { useSyncExternalStore } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { api } from '../lib/api'
import { session } from '../lib/session'
import type { Me, MyClub } from '../lib/types'

export function useSession() {
  return useSyncExternalStore(session.subscribe, session.get)
}

/**
 * Wraps an action that needs an account: guests are sent to log in and brought back to
 * the page they were on, instead of opening a form whose submit would 401.
 */
export function useSignedInAction() {
  const signedIn = !!useSession()
  const navigate = useNavigate()
  const location = useLocation()
  return (action: () => void) => () => {
    if (signedIn) action()
    else navigate('/login', { state: { from: location.pathname } })
  }
}

export function useMe() {
  const current = useSession()
  return useQuery({
    queryKey: ['me'],
    queryFn: () => api.get<Me>('/api/me'),
    enabled: !!current,
    staleTime: 5 * 60_000,
  })
}

export function useMyClubs() {
  const current = useSession()
  return useQuery({
    queryKey: ['my-clubs'],
    queryFn: () => api.get<MyClub[]>('/api/me/clubs'),
    enabled: !!current,
  })
}

/** My membership (and role) in one club, or undefined if I'm not a member. */
export function useMyClub(slug: string | undefined) {
  const clubs = useMyClubs()
  return { ...clubs, club: clubs.data?.find((c) => c.slug === slug) }
}

export const roleRank = { MEMBER: 0, CORE: 1, CLUB_ADMIN: 2 } as const
