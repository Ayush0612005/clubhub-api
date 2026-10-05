# ClubHub API reference

Interactive docs with request/response schemas: **Swagger UI at `/swagger-ui.html`** ([live](https://clubhub-api-9sw2.onrender.com/swagger-ui.html)), OpenAPI spec at `/v3/api-docs`.


| Endpoint | Who | Description |
|---|---|---|
| `POST /api/auth/register` | public | Create an account |
| `POST /api/auth/login` | public | Access + refresh token |
| `POST /api/auth/refresh` | refresh token | Rotate tokens (optional `clubSlug` keeps the active club) |
| `POST /api/auth/logout` | refresh token | Revoke this login session |
| `POST /api/auth/switch-club` | any user | Club-scoped access token (members only) |
| `GET /api/auth/me` | any user | Identity and active club from the token |
| `POST /api/platform/tenants` | `PLATFORM_ADMIN` | Create a club (`ownerEmail` optional) |
| `GET /api/platform/tenants` | `PLATFORM_ADMIN` | List clubs |
| `GET /api/club/profile` | club member | Club profile |
| `PUT /api/club/profile` | `CORE`+ | Update club profile |
| `GET /api/club/members` | club member | List members and roles |
| `POST /api/club/members` | `CLUB_ADMIN` | Add a registered user with a role |
| `PATCH /api/club/members/{userId}` | `CLUB_ADMIN` | Change role (last admin protected) |
| `DELETE /api/club/members/{userId}` | `CLUB_ADMIN` | Remove member (last admin protected) |
| `GET /api/club/recruitment/drives` | club member | List drives (drafts only for `CORE`+) |
| `GET /api/club/recruitment/drives/{id}` | club member | Drive with questions |
| `POST /api/club/recruitment/drives` | `CORE`+ | Create a drive with questions (DRAFT) |
| `PATCH /api/club/recruitment/drives/{id}/status` | `CORE`+ | Open / close / reopen |
| `GET /api/club/recruitment/drives/{id}/applications` | `CORE`+ | Applicants, paged, `?status=` filter |
| `GET /api/club/recruitment/applications/{id}` | `CORE`+ | Answers + status history |
| `POST /api/club/recruitment/applications/{id}/transitions` | `CORE`+ | Move in the pipeline (SELECTED adds a MEMBER) |
| `GET /api/clubs/{slug}/recruitment/drives` | any user | Open drives of a club |
| `GET /api/clubs/{slug}/recruitment/drives/{id}` | any user | Open drive with questions |
| `POST /api/clubs/{slug}/recruitment/drives/{id}/applications` | any user | Apply |
| `GET /api/clubs/{slug}/recruitment/applications/mine` | any user | My applications to this club |
| `POST /api/clubs/{slug}/recruitment/applications/{id}/withdraw` | applicant | Withdraw own application |
| `GET /api/club/events` | club member | Upcoming events (drafts for `CORE`+) |
| `POST /api/club/events` | `CORE`+ | Create an event (DRAFT) |
| `POST /api/club/events/{id}/publish` · `/cancel` | `CORE`+ | Publish / cancel |
| `GET /api/club/events/{id}/registrations` | `CORE`+ | Registrants with attended flag |
| `POST` · `DELETE /api/club/events/{id}/registration` | club member | Register / unregister |
| `GET /api/club/events/{id}/ticket` · `/ticket/qr` | registrant | Signed ticket (JSON / PNG) |
| `POST /api/club/events/{eventId}/check-ins` | `CORE`+ | Scan a QR ticket at the door |
| `POST /api/club/events/{eventId}/check-ins/manual` | `CORE`+ | Check in a walk-in by email |
| `GET /api/club/events/{eventId}/attendance` | `CORE`+ | Registered vs attended, attendee list |
| `GET /api/clubs/{slug}/events` · `/{id}` | any user | A club's public upcoming events |
| `POST` · `DELETE /api/clubs/{slug}/events/{id}/registration` | any user | Register / unregister |
| `GET /api/clubs/{slug}/events/{id}/ticket` · `/ticket/qr` | registrant | Signed ticket (JSON / PNG) |
| `POST /api/club/files/uploads` | `CORE`+ | Pre-signed S3 upload URL (event posters) |
| `POST /api/club/files/{id}/confirm` | `CORE`+ | Verify the upload in S3 and attach it |
| `GET /api/club/files/{id}` | club member | 302 to a short-lived download URL |
| `GET /api/club/events/{id}/poster` · `/api/clubs/{slug}/events/{id}/poster` | member / any user | 302 to the poster |
| `POST /api/club/events/{id}/certificates` | `CORE`+ | Issue participation certificates to attendees |
| `GET /api/club/events/{id}/certificates` | `CORE`+ | Certificates issued for an event |
| `POST /api/club/certificates/{id}/revoke` | `CORE`+ | Revoke a certificate |
| `GET /api/clubs/{slug}/certificates/mine` | any user | My certificates from this club |
| `GET /api/clubs/{slug}/certificates/{id}/pdf` | recipient | Download the PDF |
| `GET /api/verify/certificates/{slug}/{id}` | **public** | Verify a certificate (link/QR on the PDF) |
| `GET /api/notifications` | any user | My inbox across all clubs (paged, `?unreadOnly=true`) |
| `GET /api/notifications/unread-count` | any user | Badge count |
| `POST /api/notifications/{id}/read` · `/read-all` | any user | Mark read |
| `WS /ws` → `SUBSCRIBE /user/queue/notifications` | any user | Live notifications (STOMP, JWT in CONNECT) |
| `GET /api/me` · `/api/me/clubs` | any user | My profile; my clubs with my role in each |
| `GET /api/clubs` | any user | Directory of active clubs |
| `GET /api/club/plan` | club member | Plan, limits, current usage, features |
| `GET /api/club/audit` | `CLUB_ADMIN` | Audit trail (paged, `?action=`) |
| `PATCH /api/platform/tenants/{id}/plan` | `PLATFORM_ADMIN` | Change a club's plan |
| `PUT /api/platform/tenants/{id}/features/{feature}` | `PLATFORM_ADMIN` | Per-club feature override |

Interactive docs: **Swagger UI at `/swagger-ui.html`** (OpenAPI spec at `/v3/api-docs`).

Errors use RFC 9457 Problem Details (`application/problem+json`). 401 = not authenticated,
403 = authenticated but not allowed.


### Campus directory and demo

| Endpoint | Who | Description |
|---|---|---|
| `POST /api/auth/demo` | public | Sign in as the demo visitor, admin of a sandbox club that resets hourly |
| `GET /api/campus/clubs` · `/clubs/{slug}` | **public** | All campus clubs; one club with its approved events and recruitments |
| `GET /api/campus/events` · `/recruitments` | **public** | Approved upcoming events; open recruitments |
| `POST /api/campus/suggestions/events` · `/recruitments` | any user | Suggest something (PENDING until approved, max 5 pending each) |
| `GET /api/platform/campus/queue` | `PLATFORM_ADMIN` | Moderation queue |
| `POST /api/platform/campus/import/srm-paste` | `PLATFORM_ADMIN` | Import a pasted SRM events RSS feed |
| `POST` · `PUT` · `DELETE /api/platform/campus/events` · `/recruitments` (`/{id}`, `/{id}/approve`, `/{id}/reject`) | `PLATFORM_ADMIN` | Create, edit, approve, reject, delete |
