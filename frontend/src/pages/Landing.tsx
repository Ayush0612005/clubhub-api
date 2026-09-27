import { ArrowRight, BadgeCheck, Bell, CalendarCheck2, Code2, KanbanSquare, ScanLine, ShieldCheck, Sparkles, Users } from 'lucide-react'
import type { ReactNode } from 'react'
import { QrArt } from '../components/art'
import { Logo, ThemeToggle } from '../components/brand'
import { ButtonLink } from '../components/ui'
import { useSession } from '../hooks/useAuth'

const clubs = ['Coding Club', 'Robotics Society', 'Music Club', 'E-Cell', 'Photography Club', 'Dance Crew', 'Quiz Society', 'AI/ML Guild', 'Drama Club', 'Rotaract']

function HeroVisual() {
  return (
    <div className="relative mx-auto h-[430px] w-full max-w-[460px] select-none">
      {/* pipeline card */}
      <div className="absolute top-2 left-0 w-[270px] -rotate-3 rounded-3xl border border-line bg-surface p-4 shadow-lift">
        <p className="font-mono text-[10px] tracking-widest text-muted uppercase">Tech team · recruitment</p>
        <div className="mt-3 grid grid-cols-3 gap-2 text-[11px]">
          {[
            ['Applied', 42, 'bg-cobalt'],
            ['Interview', 11, 'bg-signal'],
            ['Selected', 6, 'bg-forest'],
          ].map(([label, count, color]) => (
            <div key={label as string} className="rounded-xl bg-paper-2 p-2">
              <div className="flex items-center gap-1.5">
                <span className={`size-1.5 rounded-full ${color}`} />
                <span className="text-muted">{label}</span>
              </div>
              <p className="mt-1 font-display text-xl font-bold">{count}</p>
            </div>
          ))}
        </div>
      </div>

      {/* ticket */}
      <div className="absolute top-[120px] right-0 w-[300px] rotate-2 rounded-3xl bg-ink p-1 shadow-lift">
        <div className="rounded-[20px] bg-surface p-5">
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="font-mono text-[10px] tracking-widest text-signal uppercase">Admit one</p>
              <p className="mt-1 font-display text-xl leading-tight font-bold">Hack Night 2026</p>
              <p className="mt-1 text-xs text-muted">TP Ganesan Auditorium · 7:00 PM</p>
            </div>
            <span className="rounded-full bg-forest-50 px-2 py-0.5 text-[11px] font-medium text-forest">Registered</span>
          </div>
          <div className="ticket-notch my-4 border-t-2 border-dashed border-line" />
          <div className="flex items-center gap-4">
            <QrArt seed="hack-night" size={92} />
            <p className="text-xs leading-relaxed text-muted">
              Signed ticket. Scanned at the door, verified in constant time, useless at any other club.
            </p>
          </div>
        </div>
      </div>

      {/* toast */}
      <div className="absolute bottom-0 left-6 flex w-[290px] -rotate-1 gap-3 rounded-2xl border border-line bg-surface p-4 shadow-lift">
        <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-signal-50 text-signal">
          <Bell className="size-[18px]" />
        </span>
        <div>
          <p className="text-sm font-semibold">You're shortlisted 🎉</p>
          <p className="text-xs text-muted">Coding Club moved your application to Interview.</p>
        </div>
      </div>
    </div>
  )
}

function Feature({ icon, title, children, className }: { icon: ReactNode; title: string; children: ReactNode; className?: string }) {
  return (
    <div className={`group rounded-[var(--radius-card)] border border-line bg-surface p-6 transition hover:-translate-y-0.5 hover:shadow-lift ${className ?? ''}`}>
      <div className="mb-5 grid size-11 place-items-center rounded-2xl bg-paper-2 text-ink transition group-hover:bg-signal group-hover:text-white">{icon}</div>
      <h3 className="text-lg font-bold">{title}</h3>
      <p className="mt-2 text-[15px] leading-relaxed text-muted">{children}</p>
    </div>
  )
}

export default function Landing() {
  const signedIn = !!useSession()

  return (
    <div className="min-h-screen overflow-x-hidden bg-paper">
      <header className="sticky top-0 z-30 border-b border-line/70 bg-paper/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-5 py-3.5">
          <Logo />
          <nav className="hidden items-center gap-7 text-sm text-ink-2 md:flex">
            <a href="#features" className="hover:text-ink">Features</a>
            <a href="#how" className="hover:text-ink">How it works</a>
            <a href="#engineering" className="hover:text-ink">Under the hood</a>
          </nav>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            {signedIn ? (
              <ButtonLink to="/app" variant="ink" size="sm">Open app</ButtonLink>
            ) : (
              <>
                <ButtonLink to="/login" variant="ghost" size="sm">Log in</ButtonLink>
                <ButtonLink to="/register" variant="ink" size="sm">Get started</ButtonLink>
              </>
            )}
          </div>
        </div>
      </header>

      {/* hero */}
      <section className="bg-dots relative">
        <div className="mx-auto grid max-w-6xl items-center gap-12 px-5 pt-16 pb-20 lg:grid-cols-[1.1fr_1fr] lg:pt-24">
          <div className="animate-rise">
            <span className="inline-flex items-center gap-2 rounded-full border border-line bg-surface px-3 py-1 text-xs font-medium text-ink-2 shadow-soft">
              <Sparkles className="size-3.5 text-signal" /> Built for college clubs at SRM KTR
            </span>
            <h1 className="mt-6 text-5xl leading-[1.02] font-extrabold sm:text-6xl lg:text-7xl">
              Run your club
              <br />
              like it's a <span className="relative inline-block text-signal">startup<svg viewBox="0 0 200 12" className="absolute -bottom-2 left-0 w-full" aria-hidden><path d="M2 9c40-6 110-8 196-3" stroke="currentColor" strokeWidth="4" fill="none" strokeLinecap="round" /></svg></span>.
            </h1>
            <p className="mt-7 max-w-xl text-lg leading-relaxed text-ink-2">
              Recruitment drives, events with QR check-in, verifiable certificates and live notifications — in one
              workspace per club, with its data completely isolated from every other club.
            </p>
            <div className="mt-9 flex flex-wrap gap-3">
              <ButtonLink to={signedIn ? '/app' : '/register'} size="lg" icon={<ArrowRight className="size-4" />} className="flex-row-reverse">
                {signedIn ? 'Go to your clubs' : 'Start for free'}
              </ButtonLink>
              <ButtonLink to={signedIn ? '/app/explore' : '/login'} variant="outline" size="lg">
                Explore clubs
              </ButtonLink>
            </div>
            <dl className="mt-12 grid max-w-md grid-cols-3 gap-6">
              {[
                ['1 schema', 'per club'],
                ['< 1s', 'live updates'],
                ['0', 'paper forms'],
              ].map(([value, label]) => (
                <div key={label}>
                  <dt className="font-display text-2xl font-bold">{value}</dt>
                  <dd className="text-sm text-muted">{label}</dd>
                </div>
              ))}
            </dl>
          </div>
          <HeroVisual />
        </div>

        {/* marquee */}
        <div className="border-y border-line bg-surface/70 py-4">
          <div className="flex w-max animate-marquee gap-10 pr-10 font-display text-lg font-semibold text-muted/80">
            {[...clubs, ...clubs].map((club, i) => (
              <span key={i} className="flex items-center gap-10">
                {club}
                <span className="size-1.5 rounded-full bg-signal" />
              </span>
            ))}
          </div>
        </div>
      </section>

      {/* features */}
      <section id="features" className="mx-auto max-w-6xl px-5 py-24">
        <p className="font-mono text-xs tracking-[0.2em] text-signal uppercase">Everything a club does</p>
        <h2 className="mt-3 max-w-2xl text-4xl font-bold sm:text-5xl">From the first application to the last certificate.</h2>
        <div className="mt-12 grid gap-4 md:grid-cols-3">
          <Feature icon={<KanbanSquare className="size-5" />} title="Recruitment pipeline" className="md:col-span-2">
            Open a drive with your own questions. Students apply in a minute; your core team moves them from Applied to
            Interview to Selected — every move audited, and selected students become members automatically.
          </Feature>
          <Feature icon={<ScanLine className="size-5" />} title="QR check-in">
            Every registrant gets a signed ticket. Scan it at the door from any phone; duplicates and forgeries bounce.
          </Feature>
          <Feature icon={<BadgeCheck className="size-5" />} title="Verifiable certificates">
            One click issues PDFs to everyone who attended. Recruiters verify them with the QR code — no login needed.
          </Feature>
          <Feature icon={<Bell className="size-5" />} title="Live notifications">
            Shortlisted? New event? It lands instantly in the app, and important updates arrive by email too.
          </Feature>
          <Feature icon={<ShieldCheck className="size-5" />} title="Roles & audit trail">
            Admins, core team and members see exactly what they should. Every change is recorded with who and when.
          </Feature>
        </div>
      </section>

      {/* how it works */}
      <section id="how" className="border-y border-line bg-ink py-24 text-paper">
        <div className="mx-auto max-w-6xl px-5">
          <h2 className="max-w-xl text-4xl font-bold sm:text-5xl">Three steps. One semester of chaos, gone.</h2>
          <div className="mt-14 grid gap-10 md:grid-cols-3">
            {[
              [Users, 'Set up your club', 'Your club gets its own private space. Invite your core team and give everyone the right role.'],
              [CalendarCheck2, 'Recruit & run events', 'Publish drives and events, review applicants on a board, check people in with QR codes.'],
              [BadgeCheck, 'Recognise your people', 'Certificates for attendees, notifications for everyone, a clean history for next year’s team.'],
            ].map(([Icon, title, body], i) => {
              const StepIcon = Icon as typeof Users
              return (
                <div key={title as string}>
                  <div className="flex items-center gap-3">
                    <span className="font-mono text-sm text-signal">0{i + 1}</span>
                    <span className="h-px flex-1 bg-paper/15" />
                    <StepIcon className="size-5 text-paper/60" />
                  </div>
                  <h3 className="mt-5 text-2xl font-bold">{title as string}</h3>
                  <p className="mt-3 leading-relaxed text-paper/65">{body as string}</p>
                </div>
              )
            })}
          </div>
        </div>
      </section>

      {/* engineering */}
      <section id="engineering" className="mx-auto max-w-6xl px-5 py-24">
        <div className="grid gap-12 lg:grid-cols-[1fr_1.2fr]">
          <div>
            <p className="font-mono text-xs tracking-[0.2em] text-signal uppercase">Under the hood</p>
            <h2 className="mt-3 text-4xl font-bold">A real multi-tenant SaaS, not a template.</h2>
            <p className="mt-4 leading-relaxed text-muted">
              ClubHub is a portfolio project engineered like production software: isolated tenants, signed tokens,
              event-driven notifications and 160+ integration tests against real PostgreSQL, Kafka and Redis.
            </p>
            <a href="https://github.com/Ayush0612005/clubhub-api" className="mt-6 inline-flex items-center gap-2 text-sm font-semibold underline decoration-signal decoration-2 underline-offset-4" target="_blank" rel="noreferrer">
              <Code2 className="size-4" /> Read the source
            </a>
          </div>
          <div className="overflow-hidden rounded-[var(--radius-card)] border border-line bg-surface font-mono text-[13px] shadow-soft">
            {[
              ['tenancy', 'PostgreSQL schema per club · Hibernate multi-tenancy'],
              ['auth', 'JWT access + rotating refresh tokens · per-club roles'],
              ['events', 'Kafka domain events → inbox · WebSocket · email'],
              ['limits', 'Redis token buckets per club and per IP'],
              ['files', 'S3 pre-signed uploads · OpenPDF certificates'],
              ['stack', 'Java 25 · Spring Boot 4 · React 19 · Tailwind'],
            ].map(([key, value]) => (
              <div key={key} className="flex gap-4 border-b border-line px-5 py-3.5 last:border-0">
                <span className="w-20 shrink-0 text-signal">{key}</span>
                <span className="text-ink-2">{value}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-5 pb-24">
        <div className="relative overflow-hidden rounded-[2rem] bg-signal px-8 py-14 text-white sm:px-14">
          <div className="absolute -top-16 -right-16 size-64 rounded-full bg-white/10" />
          <div className="absolute -bottom-24 right-40 size-48 rounded-full bg-white/10" />
          <h2 className="relative max-w-xl text-4xl font-bold">Your club's next semester starts here.</h2>
          <div className="relative mt-8 flex flex-wrap gap-3">
            <ButtonLink to={signedIn ? '/app' : '/register'} variant="ink" size="lg">Create your account</ButtonLink>
          </div>
        </div>
      </section>

      <footer className="border-t border-line">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-5 py-8 text-sm text-muted">
          <Logo />
          <p>Made at SRM KTR · {new Date().getFullYear()}</p>
        </div>
      </footer>
    </div>
  )
}
