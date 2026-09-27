# ClubHub Web

React 19 + TypeScript + Vite + Tailwind v4 + TanStack Query client for the ClubHub API.

```bash
npm install
npm run dev      # http://localhost:5173, proxies /api and /ws to the API on :8080
npm run lint     # oxlint
npm run build    # typecheck + production bundle in dist/
```

## Structure

| Path | What lives there |
|---|---|
| `src/lib/api.ts` | fetch wrapper: bearer token, one shared refresh on 401, RFC 9457 errors as `ApiError`, club switching |
| `src/lib/club.ts` | `clubApi(slug)` for `/api/club/**` (club-scoped token) and `publicClubApi(slug)` for `/api/clubs/{slug}/**` |
| `src/hooks/useLiveNotifications.ts` | STOMP over WebSocket; pushes toasts and refreshes the inbox |
| `src/layouts/` | app shell (sidebar, live indicator) and the club workspace (role-aware tabs) |
| `src/pages/app/` | student side: explore clubs, register for events, apply to drives, certificates, inbox |
| `src/pages/club/` | club workspace: dashboard, events + door scanner, recruitment kanban, members, audit log, plan |
| `src/components/` | design system (`ui.tsx`), toasts, brand, ticket |

Design tokens (colours, fonts, radius) are in `src/index.css` under `@theme`; dark mode swaps the same
token names under `.dark`, so components never branch on theme.
