import type { ActiveClub } from './types'

/**
 * The signed-in session: a short-lived access token (15 min, possibly scoped to one club) and the
 * rotating refresh token. Kept in localStorage so a reload doesn't log you out.
 *
 * Trade-off: an XSS bug could read localStorage. The mitigations are the short access-token
 * lifetime, refresh-token rotation with reuse detection on the server, and React escaping output.
 * An httpOnly refresh cookie would be the next hardening step.
 */
export interface Session {
  accessToken: string
  refreshToken: string
  expiresAt: string
  club: ActiveClub | null
}

const KEY = 'clubhub.session'
const listeners = new Set<() => void>()

function load(): Session | null {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

let current: Session | null = load()

function emit() {
  listeners.forEach((listener) => listener())
}

export const session = {
  get: () => current,

  set(next: Session) {
    current = next
    localStorage.setItem(KEY, JSON.stringify(next))
    emit()
  },

  update(patch: Partial<Session>) {
    if (current) session.set({ ...current, ...patch })
  },

  clear() {
    current = null
    localStorage.removeItem(KEY)
    emit()
  },

  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
}
