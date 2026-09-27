import { api, ensureClub } from './api'

/**
 * API calls for the club workspace (/api/club/**). Each call first makes sure the access token is
 * scoped to this club (switch-club), so navigating between clubs can never hit the wrong one.
 */
export function clubApi(slug: string) {
  return {
    get: <T>(path: string) => ensureClub(slug).then(() => api.get<T>(`/api/club${path}`)),
    post: <T>(path: string, body?: unknown) => ensureClub(slug).then(() => api.post<T>(`/api/club${path}`, body)),
    put: <T>(path: string, body?: unknown) => ensureClub(slug).then(() => api.put<T>(`/api/club${path}`, body)),
    patch: <T>(path: string, body?: unknown) => ensureClub(slug).then(() => api.patch<T>(`/api/club${path}`, body)),
    del: <T>(path: string) => ensureClub(slug).then(() => api.del<T>(`/api/club${path}`)),
    blobUrl: (path: string) => ensureClub(slug).then(() => api.blobUrl(`/api/club${path}`)),
  }
}

/** Student-facing calls for any club (/api/clubs/{slug}/**); membership not required. */
export function publicClubApi(slug: string) {
  const base = `/api/clubs/${slug}`
  return {
    get: <T>(path: string) => api.get<T>(base + path),
    post: <T>(path: string, body?: unknown) => api.post<T>(base + path, body),
    del: <T>(path: string) => api.del<T>(base + path),
    blobUrl: (path: string) => api.blobUrl(base + path),
  }
}
