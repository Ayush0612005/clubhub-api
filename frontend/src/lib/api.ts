import { session } from './session'
import type { ClubTokenResponse, TokenResponse } from './types'

/** An RFC 9457 problem response from the API, as an Error the UI can show. */
export class ApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(status: number, message: string, code?: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

interface Options {
  body?: unknown
  auth?: boolean
}

let refreshing: Promise<boolean> | null = null

/**
 * One refresh at a time: when several requests hit 401 together, they all wait for the same
 * refresh instead of each rotating the refresh token (which the server would treat as reuse).
 */
function refreshOnce(): Promise<boolean> {
  if (!refreshing) {
    refreshing = doRefresh().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

async function doRefresh(): Promise<boolean> {
  const current = session.get()
  if (!current) return false
  const res = await fetch('/api/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: current.refreshToken, clubSlug: current.club?.slug ?? null }),
  })
  if (!res.ok) {
    session.clear()
    return false
  }
  const tokens = (await res.json()) as TokenResponse
  session.set({
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    expiresAt: tokens.expiresAt,
    club: tokens.club,
  })
  return true
}

async function send(method: string, path: string, { body, auth = true }: Options, retried = false): Promise<Response> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = session.get()?.accessToken
  if (auth && token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })

  if (res.status === 401 && auth && !retried && session.get()) {
    if (await refreshOnce()) return send(method, path, { body, auth }, true)
  }
  if (!res.ok) {
    let message = res.statusText || 'Request failed'
    let code: string | undefined
    try {
      const problem = await res.json()
      message = problem.detail ?? problem.title ?? message
      code = problem.code
    } catch {
      // not JSON: keep the status text
    }
    if (res.status === 429) message = 'You are going a bit fast — try again in a moment.'
    throw new ApiError(res.status, message, code)
  }
  return res
}

async function json<T>(method: string, path: string, options: Options = {}): Promise<T> {
  const res = await send(method, path, options)
  if (res.status === 204 || res.headers.get('content-length') === '0') return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T>(path: string) => json<T>('GET', path),
  post: <T>(path: string, body?: unknown) => json<T>('POST', path, { body }),
  put: <T>(path: string, body?: unknown) => json<T>('PUT', path, { body }),
  patch: <T>(path: string, body?: unknown) => json<T>('PATCH', path, { body }),
  del: <T>(path: string) => json<T>('DELETE', path),
  publicGet: <T>(path: string) => json<T>('GET', path, { auth: false }),

  /** Authenticated binary download (QR images, PDFs) as an object URL. */
  async blobUrl(path: string): Promise<string> {
    const res = await send('GET', path, {})
    return URL.createObjectURL(await res.blob())
  },
}

// ---- auth flows

export async function login(email: string, password: string) {
  const tokens = await json<TokenResponse>('POST', '/api/auth/login', { body: { email, password }, auth: false })
  session.set({
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    expiresAt: tokens.expiresAt,
    club: tokens.club,
  })
}

/** Returns true when the user must click the emailed link first (no session is created then). */
export async function register(fullName: string, email: string, password: string): Promise<{ verificationRequired: boolean }> {
  const res = await json<{ verificationRequired: boolean }>('POST', '/api/auth/register', { body: { fullName, email, password }, auth: false })
  if (!res.verificationRequired) await login(email, password)
  return { verificationRequired: res.verificationRequired }
}

export const verifyEmail = (token: string) => json<void>('POST', '/api/auth/verify-email', { body: { token }, auth: false })
export const resendVerification = (email: string) => json<void>('POST', '/api/auth/resend-verification', { body: { email }, auth: false })
export const forgotPassword = (email: string) => json<void>('POST', '/api/auth/forgot-password', { body: { email }, auth: false })
export const resetPassword = (token: string, password: string) =>
  json<void>('POST', '/api/auth/reset-password', { body: { token, password }, auth: false })

export async function logout() {
  const current = session.get()
  session.clear()
  if (current) {
    await fetch('/api/auth/logout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: current.refreshToken }),
    }).catch(() => undefined)
  }
}

const switching = new Map<string, Promise<void>>()

/** Make the access token club-scoped for {@code slug} (needed for every /api/club/** call). */
export function ensureClub(slug: string): Promise<void> {
  if (session.get()?.club?.slug === slug) return Promise.resolve()
  let pending = switching.get(slug)
  if (!pending) {
    pending = api
      .post<ClubTokenResponse>('/api/auth/switch-club', { clubSlug: slug })
      .then((res) => session.update({ accessToken: res.accessToken, expiresAt: res.expiresAt, club: res.club }))
      .finally(() => switching.delete(slug))
    switching.set(slug, pending)
  }
  return pending
}
