# ClubHub API

[![CI](https://github.com/Ayush0612005/clubhub-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Ayush0612005/clubhub-api/actions/workflows/ci.yml)
![Java 25](https://img.shields.io/badge/Java-25-orange)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F)
![PostgreSQL 18](https://img.shields.io/badge/PostgreSQL-18-336791)

Multi-tenant SaaS backend for college clubs at SRM KTR. Every club is a tenant with its own
isolated PostgreSQL schema: recruitment, events with QR attendance, certificates and
notifications, all running on one shared deployment.

> **Status:** Phases 1–4 complete (tenancy core, auth + RBAC, recruitment, events + QR attendance).
> File uploads + certificates is next.
> See [Roadmap](#roadmap).

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
Hibernate 7 · PostgreSQL 18 · Flyway · Nimbus JOSE (JWT) · JUnit 5 · Testcontainers · Docker Compose ·
GitHub Actions

Planned: Redis + Bucket4j, Kafka, WebSocket (STOMP), AWS S3/SES, ZXing, OpenPDF, springdoc-openapi,
Micrometer/Prometheus/Grafana, React 19 frontend.

## Run locally

Prerequisites: JDK 25, Docker Desktop.

```bash
docker compose up -d                                  # PostgreSQL 18 on localhost:5432
./mvnw spring-boot:run                                # Windows: .\mvnw.cmd spring-boot:run
```

| Env var | Purpose | Default |
|---|---|---|
| `JWT_SECRET` | HS256 signing key, >= 32 bytes | dev-only value (never use in production) |
| `CLUBHUB_ADMIN_EMAIL` | existing account promoted to `PLATFORM_ADMIN` at startup | none |

Running from an IDE: add `-Duser.timezone=UTC` to the VM options.

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

Errors use RFC 9457 Problem Details (`application/problem+json`). 401 = not authenticated,
403 = authenticated but not allowed.

## Tests

```bash
./mvnw verify    # requires Docker; spins up a throwaway PostgreSQL 18 container
```

130 tests, including: cross-club isolation over HTTP (`TenantIsolationTest`), forged / expired /
`alg:none` tokens, refresh-token replay detection, live role changes, last-admin protection,
recruitment state machines, selection → membership end to end, concurrent registrations against
capacity, and QR tickets decoded from the PNG exactly like a door scanner would.

## Roadmap

- [x] **Phase 1** Tenancy core: schema-per-tenant, provisioning, per-tenant migrations, request routing (`v0.1.0`)
- [x] **Phase 2** Auth + RBAC: JWT + rotating refresh tokens, club switching, platform and club roles, member management (`v0.2.0`)
- [x] **Phase 3** Recruitment: drives with questions, student applications, audited review pipeline, auto-membership on selection (`v0.3.0`)
- [x] **Phase 4** Events + QR attendance: capacity-safe registration, signed QR tickets, door check-in (`v0.4.0`)
- [ ] **Phase 5** S3 file uploads + PDF certificates
- [ ] **Phase 6** Notifications: Kafka → WebSocket + email
- [ ] **Phase 7** Plans, feature flags, per-tenant rate limits, audit log
- [ ] **Phase 8** React frontend
- [ ] **Phase 9** Deploy on AWS (EC2 + RDS) and onboard SRM clubs
