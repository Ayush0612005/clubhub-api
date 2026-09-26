# ClubHub API

[![CI](https://github.com/Ayush0612005/clubhub-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Ayush0612005/clubhub-api/actions/workflows/ci.yml)
![Java 25](https://img.shields.io/badge/Java-25-orange)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F)
![PostgreSQL 18](https://img.shields.io/badge/PostgreSQL-18-336791)

Multi-tenant SaaS backend for college clubs at SRM KTR. Every club is a tenant with its own
isolated PostgreSQL schema: recruitment, events with QR attendance, certificates and
notifications, all running on one shared deployment.

> **Status:** Phase 1 (tenancy core) complete. Auth and RBAC are next. See [Roadmap](#roadmap).

## Architecture

**Schema-per-tenant.** Platform data (clubs, and later users and memberships) lives in `public`;
each club's data lives in its own schema with identical tables.

```
PostgreSQL "clubhub"
├── public                  tenants, flyway_schema_history   (platform)
├── club_coding_club        club_profile, flyway_schema_history
└── club_robotics           club_profile, flyway_schema_history
```

**Request flow for a club-scoped call**

```
GET /api/club/profile   X-Tenant-ID: coding_club
  → TenantFilter         validates slug, looks up public.tenants (404 unknown, 403 suspended)
  → TenantContext        binds schema "club_coding_club" (Java 25 ScopedValue, request-scoped)
  → Hibernate            CurrentTenantIdentifierResolver → MultiTenantConnectionProvider
                         borrows a pooled connection and sets search_path to the club schema
  → ClubProfileService   plain JPA code, no tenant logic
  → connection reset to "public" before returning to the pool
```

**Onboarding a club** (`POST /api/platform/tenants`): validate slug → Flyway creates
`club_<slug>` and applies `db/migration/tenant/*` → seed club profile → register in
`public.tenants`. On every startup, all active club schemas are migrated to the latest version.

### Key design decisions

| Decision | Why |
|---|---|
| Schema-per-tenant (not a `tenant_id` column) | Isolation enforced by the database, not by remembering a `WHERE`; per-club export/delete is trivial. Trade-off: migrations run N times, fine for hundreds of clubs. |
| PostgreSQL over MySQL | Real schemas plus transactional DDL: a failed migration rolls back cleanly in every club schema. |
| `ScopedValue` for tenant context | No `ThreadLocal.clear()` to forget, so no tenant leaking into the next request on a pooled thread. |
| Flyway creates schemas | Identifiers are quoted by Flyway; no hand-built `CREATE SCHEMA` strings (no injection). Slugs are also constrained by a DB `CHECK`. |
| Build schema, then register tenant | A club is never visible without its tables; failed provisioning is safely retryable. |
| JVM runs in UTC | Consistent timestamps (`TIMESTAMPTZ`); convert to IST only in the UI. |
| Testcontainers, not H2 | `search_path` and schema behaviour are PostgreSQL-specific; tests run against real Postgres 18. |

## Tech stack

Java 25 · Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator) · Hibernate 7 · PostgreSQL 18 ·
Flyway · JUnit 5 · Testcontainers · Docker Compose · GitHub Actions

Planned: Spring Security + JWT, Redis + Bucket4j, Kafka, WebSocket (STOMP), AWS S3/SES, ZXing,
OpenPDF, springdoc-openapi, Micrometer/Prometheus/Grafana, React 19 frontend.

## Run locally

Prerequisites: JDK 25, Docker Desktop.

```bash
docker compose up -d            # PostgreSQL 18 on localhost:5432
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

Running from an IDE: add `-Duser.timezone=UTC` to the VM options.

### Try the API

```bash
# create a club (provisions schema club_coding_club)
curl -X POST localhost:8080/api/platform/tenants \
  -H "Content-Type: application/json" \
  -d '{"slug":"coding_club","name":"Coding Club"}'

# read / update that club's profile
curl localhost:8080/api/club/profile -H "X-Tenant-ID: coding_club"
curl -X PUT localhost:8080/api/club/profile -H "X-Tenant-ID: coding_club" \
  -H "Content-Type: application/json" \
  -d '{"displayName":"SRM Coding Club","contactEmail":"coding@srmist.edu.in"}'
```

| Endpoint | Scope | Description |
|---|---|---|
| `POST /api/platform/tenants` | platform | Create a club (`201`, `409` duplicate slug, `400` invalid) |
| `GET /api/platform/tenants` | platform | List clubs |
| `GET /api/club/profile` | club (`X-Tenant-ID`) | Get the club's profile |
| `PUT /api/club/profile` | club (`X-Tenant-ID`) | Update the club's profile |

Errors use RFC 9457 Problem Details (`application/problem+json`).

> `X-Tenant-ID` and the unsecured platform endpoints are Phase 1 scaffolding. Phase 2 resolves the
> tenant from a signed JWT claim and restricts platform endpoints to `PLATFORM_ADMIN`.

## Tests

```bash
./mvnw verify    # requires Docker; spins up a throwaway PostgreSQL 18 container
```

Includes an end-to-end acceptance test (`TenantIsolationTest`) proving one club can never read or
modify another club's data through the API.

## Roadmap

- [x] **Phase 1** Tenancy core: schema-per-tenant, provisioning, per-tenant migrations, request routing
- [ ] **Phase 2** Auth + RBAC: JWT access/refresh tokens, `CLUB_ADMIN` / `CORE` / `MEMBER` / `PLATFORM_ADMIN`
- [ ] **Phase 3** Recruitment pipeline
- [ ] **Phase 4** Events + QR attendance
- [ ] **Phase 5** S3 file uploads + PDF certificates
- [ ] **Phase 6** Notifications: Kafka → WebSocket + email
- [ ] **Phase 7** Plans, feature flags, per-tenant rate limits, audit log
- [ ] **Phase 8** React frontend
- [ ] **Phase 9** Deploy on AWS (EC2 + RDS) and onboard SRM clubs
