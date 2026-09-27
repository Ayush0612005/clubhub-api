# ClubHub

[![CI](https://github.com/Ayush0612005/clubhub-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Ayush0612005/clubhub-api/actions/workflows/ci.yml)
![Java 25](https://img.shields.io/badge/Java-25-orange)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F)
![PostgreSQL 18](https://img.shields.io/badge/PostgreSQL-18-336791)

Multi-tenant SaaS for college clubs at SRM KTR. Every club is a tenant with its own
isolated PostgreSQL schema: recruitment, events with QR attendance, certificates and
notifications, all running on one shared deployment, with a React web app on top.

> **Status:** Phases 1–9 complete (tenancy core, auth + RBAC, recruitment, events + QR attendance,
> S3 files + PDF certificates, notifications via Kafka → WebSocket + email, plans + rate limits +
> audit log, React frontend, containers + monitoring + free-tier deployment).
> See [Roadmap](#roadmap).

## Web app

`frontend/` is a React 19 + TypeScript + Vite + Tailwind v4 + TanStack Query client
([details](frontend/README.md)):

- **Students:** explore clubs, register for events and get a signed QR ticket, apply to recruitment
  drives with custom questions, follow applications, download certificates, a live notification inbox.
- **Club core team:** dashboard, event lifecycle (draft → publish → cancel), a **camera door scanner**
  that decodes tickets in the browser, walk-in check-in, one-click certificate issuing, a
  **recruitment kanban** (Applied → Shortlisted → Interview → Selected), member roles, audit log,
  plan usage meters.
- **Platform admin:** create clubs (each gets its own schema) and change plans.
- **Public:** landing page and a certificate verification page (the target of the QR on every PDF).

Session handling: access token in memory + localStorage, **one shared refresh promise** so a burst of
401s triggers a single `/refresh`, and club-scoped tokens obtained lazily per club via `switch-club`.
Live updates arrive over STOMP and invalidate the relevant TanStack queries instead of polling.
The door scanner (ZXing) is code-split so the main bundle stays around 130 kB gzipped.

## Architecture

**Schema-per-tenant.** Identity and platform data live in `public`; each club's data lives in its
own schema with identical tables.

```
PostgreSQL "clubhub"
├── public            tenants, users, memberships, refresh_tokens   (platform + identity)
├── club_coding_club  club_profile, recruitment_*, applications, events, event_attendance, ...  (Coding Club only)
└── club_robotics     club_profile, recruitment_*, applications, events, event_attendance, ...  (Robotics only)
```

A student has **one account** and can belong to many clubs with a **different role in each**
(`memberships`: user × club × `CLUB_ADMIN | CORE | MEMBER`).

### Recruitment

```
Club side (/api/club/recruitment, CORE+)        Student side (/api/clubs/{slug}/recruitment, any logged-in user)
  create drive (DRAFT) with questions             browse OPEN drives, read questions
  DRAFT → OPEN → CLOSED (→ OPEN again)            apply once per drive, answers validated
  review: APPLIED → SHORTLISTED → INTERVIEW       see "my applications", withdraw
          → SELECTED / REJECTED
  SELECTED ⇒ MEMBER membership, same transaction
```

- Both lifecycles are explicit state machines on the entities; illegal moves return `409`.
- Every pipeline move is appended to `application_status_changes` (who, when, from → to, note).
- `applications.applicant_user_id` is a **cross-schema foreign key** to `public.users`, so the
  database itself guarantees applicants are real accounts.
- Non-members reach a club through `ClubBySlugFilter`, which binds only the club's schema and never
  a member identity, so member-only endpoints stay closed to them.

### Events and QR attendance

```
CORE creates event (DRAFT) → publish → students register (PUBLIC events: anyone; MEMBERS: members only)
student: GET .../events/{id}/ticket/qr  →  PNG of  CH1.<club schema>.<eventId>.<userId>.<HMAC-SHA256>
door:    POST /api/club/events/{id}/check-ins {ticket}  →  verify signature, club, event, window,
                                                          still registered, not yet checked in
```

- **Capacity without overselling:** registration locks the event row (`SELECT ... FOR UPDATE`),
  so "count < capacity, then insert" is atomic. A test fires 12 concurrent registrations at a
  3-seat event and expects exactly 3 to succeed.
- **Tickets are signed, not stored:** HMAC-SHA256 with a key derived from the JWT secret (domain
  separated). The club schema is inside the signed payload because event ids repeat across clubs.
  Unregistering revokes a ticket; there is nothing to leak from the database.
- **Double scans are impossible:** `UNIQUE (event_id, user_id)` on `event_attendance` backs the
  application check, even for two doors scanning the same ticket at the same moment.
- Check-in opens 1 hour before the start and closes at the end. Walk-ins can be checked in by email.

### Files (S3) and certificates

```
upload:   POST /api/club/files/uploads   → PENDING row + pre-signed PUT URL (10 min, type + size signed)
          client PUTs bytes directly to S3 (the API never proxies file bytes)
          POST /api/club/files/{id}/confirm → HeadObject: exists, same size + type? → READY, attached
download: GET  .../events/{id}/poster     → 302 to a pre-signed GET URL (5 min)

certificates: POST /api/club/events/{id}/certificates → one per checked-in attendee (idempotent)
              PDF rendered on demand with OpenPDF, QR → GET /api/verify/certificates/{slug}/{id} (public)
```

- **Direct-to-S3 uploads** keep large bodies off the API servers; signing Content-Type and
  Content-Length stops a client from reusing an "image" URL for something else. `confirm` trusts
  S3's `HeadObject`, not the client.
- Object keys are prefixed by club schema (`clubs/club_x/...`), so isolation extends to storage.
- **Certificates store facts, not files:** the PDF is derived from the row, so fixing the template
  fixes every certificate. The id is a random UUID printed on the PDF; anyone (e.g. a recruiter)
  can verify it without logging in, and revocation shows up immediately.
- Credentials come from the AWS default chain (EC2 instance role in production); static keys are
  only for local emulators and tests. Tests pre-sign for real and mock only the network call.

### Notifications (Kafka → inbox, WebSocket, email)

```
service tx (e.g. shortlist applicant) ──commit──▶ DomainEventPublisher ──▶ Kafka topic clubhub.domain-events
                                                                            (key = club id, 3 partitions)
   consumer group clubhub-notifications ─▶ public.notifications (inbox) ─▶ STOMP /user/queue/notifications
   consumer group clubhub-email         ─▶ email_log + Amazon SES (personal events only)
   poison record: 3 retries, 1 s apart ─▶ clubhub.domain-events-dlt
```

- **Published after commit** (`@TransactionalEventListener(AFTER_COMMIT)`): a rolled-back change
  never notifies anyone. Known trade-off: a crash between commit and send loses that event; the
  full fix is a transactional outbox, not worth it for notifications yet.
- **At-least-once, deduplicated:** every event has its own UUID; `UNIQUE (source_event_id, user_id)`
  on the inbox and on `email_log` makes a redelivery a no-op.
- **Two consumer groups on one topic:** each gets every event, so a slow or failing email provider
  never delays in-app notifications.
- **Fan-out in the consumer:** "event published" carries the club id; the consumer looks up members,
  keeping the request fast no matter how big the club is. Only personal events are emailed.
- **WebSocket auth on STOMP CONNECT** (browsers can't send headers on the handshake): the same JWT
  decoder as the REST API; subscriptions are restricted to `/user/queue/**`. Simple in-memory broker
  = single instance; multiple instances would need a broker relay.

### Plans, rate limits and audit log

| | FREE | PRO |
|---|---|---|
| Members | 100 | 2000 |
| Upcoming events | 10 | 200 |
| Open recruitment drives | 2 | 50 |
| Requests per minute (per club) | 300 | 3000 |
| Certificates · Event posters · Email | ✔ · – · – | ✔ · ✔ · ✔ |

- **Limits are code, plans are data:** limits live in the `Plan` enum (reviewed and versioned like
  code); which plan a club is on is a column. Per-club exceptions go through `tenant_features`
  overrides set by the platform admin. Hitting a limit returns `409` with `"code": "PLAN_LIMIT"`,
  a missing feature `403` with `"code": "FEATURE_NOT_IN_PLAN"`, so the UI can offer an upgrade.
- **Rate limiting with Bucket4j token buckets in Redis:** shared across API instances. One bucket
  per club (sized by plan, so a busy club can't starve others) and one per IP on
  login/register/refresh (slows password guessing). `429` + `Retry-After`. The limiter **fails
  open**: if Redis is down, requests are allowed and a warning is logged.
- **Audit log per club,** written in the same transaction as the change (`Propagation.MANDATORY`):
  member added/removed/role changed, drives, pipeline moves, events, certificates, profile. A
  rejected change (e.g. demoting the last admin) leaves no audit row. Details stored as JSONB.

### Authentication and club access

```
POST /api/auth/login          → access token (JWT, 15 min) + refresh token (opaque, 14 days)
POST /api/auth/switch-club    → checks membership, returns an access token scoped to one club
                                (claims: sub, roles, tid, club, club_role)
GET  /api/club/...            → TenantFilter:
                                  1. club id from the VERIFIED token (not from a header)
                                  2. re-check membership + club status in the DB (removal is instant)
                                  3. bind club schema + live role (Java 25 ScopedValues)
                                → Hibernate opens the session in club_<slug> (search_path)
                                → @PreAuthorize("@clubAuthz.atLeast('CORE')") checks the live role
POST /api/auth/refresh        → rotates the refresh token; reuse of an old one revokes the session
```

### Key design decisions

| Decision | Why |
|---|---|
| Schema-per-tenant (not a `tenant_id` column) | Isolation enforced by the database, not by remembering a `WHERE`; per-club export/delete is trivial. Trade-off: migrations run N times, fine for hundreds of clubs. |
| PostgreSQL over MySQL | Real schemas plus transactional DDL: a failed migration rolls back cleanly in every club schema. |
| Club comes from a signed JWT claim, membership re-checked per request | A header can be forged; a claim can't. Re-checking the DB makes removals/demotions immediate instead of waiting for token expiry. |
| Role checked from the DB, not the token | A token issued before a demotion must not keep old powers. Tested explicitly. |
| Short JWT + rotating opaque refresh token | JWTs can't be revoked, so they live 15 min. Refresh tokens are revocable, stored as SHA-256 hashes, rotated on every use; reuse revokes that login's token family. |
| `ScopedValue` for tenant/member context | No `ThreadLocal.clear()` to forget, so no tenant leaking into the next request on a pooled thread. |
| Same response for "club doesn't exist" and "not a member" | Outsiders can't enumerate club slugs; same idea for login (no email enumeration, timing equalised with a dummy BCrypt check). |
| `DelegatingPasswordEncoder` (BCrypt) | Hash algorithm can be upgraded later without forcing password resets. |
| Testcontainers, not H2 | `search_path`, row locks and schema behaviour are PostgreSQL-specific; tests run against real Postgres 18. |

## Tech stack

Java 25 · Spring Boot 4.1 (Web MVC, Data JPA, Security, OAuth2 Resource Server, Validation, Actuator) ·
Hibernate 7 · PostgreSQL 18 · Flyway · Nimbus JOSE (JWT) · Kafka 4 (KRaft) · WebSocket (STOMP) · ZXing (QR) · OpenPDF ·
AWS SDK v2 (S3, SES) · Redis 8 + Bucket4j · springdoc-openapi · JUnit 5 · Mockito · Testcontainers ·
Docker Compose · GitHub Actions

Web: React 19 · TypeScript 6 · Vite 8 · Tailwind CSS 4 · TanStack Query 5 · React Router · STOMP.js · ZXing (browser)

Ops: Docker (multi-stage, layered, non-root) · nginx · Micrometer + Prometheus + Grafana · Render · Vercel.

## Run locally

Prerequisites: JDK 25, Docker Desktop.

```bash
docker compose up -d                                  # PostgreSQL 18 :5432, Kafka :9092, Redis :6379
./mvnw spring-boot:run                                # Windows: .\mvnw.cmd spring-boot:run
```

| Env var | Purpose | Default |
|---|---|---|
| `JWT_SECRET` | HS256 signing key, >= 32 bytes | dev-only value (never use in production) |
| `CLUBHUB_ADMIN_EMAIL` | existing account promoted to `PLATFORM_ADMIN` at startup | none |
| `CLUBHUB_S3_BUCKET` · `AWS_REGION` | S3 bucket and region for uploads | `clubhub-dev` · `ap-south-1` |
| `AWS_ACCESS_KEY_ID` · `AWS_SECRET_ACCESS_KEY` | S3/SES credentials locally (EC2 uses its instance role) | AWS default chain |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | `localhost:9092` |
| `CLUBHUB_MAIL_PROVIDER` · `CLUBHUB_MAIL_FROM` | `ses` to really send email; `log` only logs | `log` |
| `CLUBHUB_APP_URL` · `CLUBHUB_ALLOWED_ORIGINS` | frontend URL (email links, certificate verify QR) and WebSocket origins | `http://localhost:5173` |

Running from an IDE: add `-Duser.timezone=UTC` to the VM options.

Web app (Node 24):

```bash
cd frontend && npm install && npm run dev             # http://localhost:5173 (proxies /api and /ws to :8080)
```

Whole stack in containers (API, web, PostgreSQL, Kafka, Redis, Prometheus, Grafana):

```bash
docker compose --profile full up -d --build           # app http://localhost:8088 · Grafana http://localhost:3002
```

The API container runs under a 512 MB limit with the same JVM flags as the free cloud instance
(measured: ~380 MB under load, p95 ≈ 12 ms for directory reads).
nginx serves the SPA and reverse-proxies `/api` and `/ws`; the API port and `/actuator` are never
published, and Prometheus scrapes a separate management port on the private network.

### Deploy (free, no credit card)

Vercel (web) + Render (API, Docker) + Neon (PostgreSQL) + Upstash (Redis), all on free tiers. Without a
free broker, the cloud profile delivers domain events in-process behind the same publisher interface
(`clubhub.events.transport`); Kafka stays the default locally, in Docker and in tests. Step-by-step guide: **[docs/DEPLOY.md](docs/DEPLOY.md)** · Render blueprint: [`render.yaml`](render.yaml)
· cloud settings: [`application-cloud.yaml`](src/main/resources/application-cloud.yaml).

### Try it

```bash
# 1. register, then restart with CLUBHUB_ADMIN_EMAIL=you@srmist.edu.in to become platform admin
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"email":"you@srmist.edu.in","password":"a-long-password","fullName":"You"}'

# 2. log in (copy accessToken)
curl -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"you@srmist.edu.in","password":"a-long-password"}'

# 3. create a club (you become its CLUB_ADMIN, or pass "ownerEmail")
curl -X POST localhost:8080/api/platform/tenants -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"slug":"coding_club","name":"Coding Club"}'

# 4. switch into the club (copy the new accessToken) and use club endpoints
curl -X POST localhost:8080/api/auth/switch-club -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"clubSlug":"coding_club"}'
curl localhost:8080/api/club/profile -H "Authorization: Bearer $CLUB_TOKEN"
```

### Endpoints

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

## Tests

```bash
./mvnw verify    # requires Docker; spins up throwaway PostgreSQL 18, Kafka and Redis containers
```

161 tests against real PostgreSQL 18, Kafka (KRaft) and Redis containers, including: cross-club isolation
over HTTP (`TenantIsolationTest`), forged / expired / `alg:none` tokens, refresh-token replay
detection, live role changes, last-admin protection, recruitment state machines, concurrent
registrations against capacity, QR tickets decoded from the PNG like a door scanner would, Kafka
redelivery without duplicate notifications/emails, a real WebSocket client receiving a push, plan
limits, per-club and per-IP rate limits, and audit rows rolling back with a rejected change.

## Roadmap

- [x] **Phase 1** Tenancy core: schema-per-tenant, provisioning, per-tenant migrations, request routing (`v0.1.0`)
- [x] **Phase 2** Auth + RBAC: JWT + rotating refresh tokens, club switching, platform and club roles, member management (`v0.2.0`)
- [x] **Phase 3** Recruitment: drives with questions, student applications, audited review pipeline, auto-membership on selection (`v0.3.0`)
- [x] **Phase 4** Events + QR attendance: capacity-safe registration, signed QR tickets, door check-in (`v0.4.0`)
- [x] **Phase 5** S3 direct uploads with pre-signed URLs, PDF certificates with public verification (`v0.5.0`)
- [x] **Phase 6** Notifications: Kafka domain events → inbox, STOMP WebSocket push, SES email (`v0.6.0`)
- [x] **Phase 7** FREE/PRO plans + feature overrides, Redis/Bucket4j rate limits, audit log, OpenAPI docs (`v0.7.0`)
- [x] **Phase 8** React frontend: student app, club workspace with kanban + camera door scanner, live notifications (`v0.8.0`)
- [x] **Phase 9** Docker images, full-stack compose, Prometheus + Grafana, free-tier deployment (Vercel + Render + Neon + Upstash), pluggable event transport (`v0.9.0`)
- [ ] Onboard SRM clubs (target: April 2027)
