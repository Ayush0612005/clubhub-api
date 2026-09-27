import { useQuery } from '@tanstack/react-query'
import { useSyncExternalStore } from 'react'
import { api } from '../lib/api'
import { session } from '../lib/session'
import type { Me, MyClub } from '../lib/types'

export function useSession() {
  return useSyncExternalStore(session.subscribe, session.get)
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
