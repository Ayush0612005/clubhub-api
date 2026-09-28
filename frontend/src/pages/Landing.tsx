import { ArrowRight, BadgeCheck, Bell, CalendarCheck2, Code2, KanbanSquare, ScanLine, ShieldCheck, Users } from 'lucide-react'
import type { ReactNode } from 'react'
import { QrArt } from '../components/art'
import { Logo, ThemeToggle } from '../components/brand'
import { ButtonLink } from '../components/ui'
import { useSession } from '../hooks/useAuth'

// kinds of clubs, not real club names: listing a real club here would suggest it uses ClubHub
const clubs = ['Coding clubs', 'Robotics teams', 'Music', 'Entrepreneurship cells', 'Dance', 'Quiz', 'IEEE & ACM chapters', 'Literature', 'Social service', 'Department associations']

/** Nested outlines stepping towards one corner: the page's line-art motif. */
function ConcentricArt({ className }: { className?: string }) {
  const steps = 16
  return (
    <svg viewBox="0 0 640 260" preserveAspectRatio="xMaxYMin slice" className={className} aria-hidden>
      {Array.from({ length: steps }, (_, i) => (
        <rect
          key={i}
          x={i * 16 + 0.5}
          y={i * 9 + 0.5}
          width={640 - i * 16}
          height={260 - i * 9}
          fill="none"
          className="stroke-line"
          strokeWidth="1"
        />
      ))}
    </svg>
  )
}

/** Section label like "02 / Proven impact". */
function Eyebrow({ n, children }: { n: number; children: ReactNode }) {
  return (
    <p className="eyebrow">
      {String(n).padStart(2, '0')} / {children}
    </p>
  )
}

/** Two-tone heading: a muted lead-in line and a bright payoff line. */
function Heading({ lead, children, className }: { lead: string; children: ReactNode; className?: string }) {
  return (
    <h2 className={`mt-5 text-4xl leading-[1.1] font-semibold sm:text-5xl ${className ?? ''}`}>
      <span className="text-muted">{lead}</span>
      <br />
      {children}
    </h2>
  )
}

function Section({ id, children, className }: { id?: string; children: ReactNode; className?: string }) {
  return (
    <section id={id} className="border-b border-line">
      <div className={`rails relative py-20 md:py-24 ${className ?? ''}`}>{children}</div>
    </section>
  )
}

function HeroVisual() {
  return (
    <div className="relative mx-auto h-[420px] w-full max-w-[460px] select-none">
      <div className="absolute top-2 left-0 w-[270px] -rotate-2 rounded-xl border border-line bg-surface p-4 shadow-lift">
        <p className="font-mono text-[10px] tracking-widest text-muted uppercase">Tech team · recruitment</p>
        <div className="mt-3 grid grid-cols-3 gap-2 text-[11px]">
          {[
            ['Applied', 42, 'bg-cobalt'],
            ['Interview', 11, 'bg-signal'],
            ['Selected', 6, 'bg-forest'],
          ].map(([label, count, color]) => (
            <div key={label as string} className="rounded-md border border-line bg-paper-2 p-2">
              <div className="flex items-center gap-1.5">
                <span className={`size-1.5 rounded-full ${color}`} />
                <span className="text-muted">{label}</span>
              </div>
              <p className="mt-1 font-mono text-xl font-bold">{count}</p>
            </div>
          ))}
        </div>
      </div>

      <div className="absolute top-[118px] right-0 w-[300px] rotate-2 rounded-xl border border-line bg-surface p-5 shadow-lift">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="font-mono text-[10px] tracking-widest text-signal-ink uppercase">Admit one</p>
            <p className="mt-1 text-xl leading-tight font-semibold tracking-tight">Hack Night 2026</p>
            <p className="mt-1 text-xs text-muted">TP Ganesan Auditorium · 7:00 PM</p>
          </div>
          <span className="rounded-md bg-forest-50 px-2 py-0.5 font-mono text-[10px] font-medium tracking-wider text-forest uppercase">Registered</span>
        </div>
        <div className="ticket-notch my-4 border-t border-dashed border-line" />
        <div className="flex items-center gap-4">
          <QrArt seed="hack-night" size={84} />
          <p className="text-xs leading-relaxed text-muted">Signed ticket. Scanned at the door, useless at any other club.</p>
        </div>
      </div>

      <div className="absolute bottom-0 left-4 flex w-[290px] -rotate-1 gap-3 rounded-xl border border-line bg-surface p-4 shadow-lift">
        <span className="grid size-9 shrink-0 place-items-center rounded-md bg-signal-50 text-signal-ink">
          <Bell className="size-[18px]" />
        </span>
        <div>
          <p className="text-sm font-semibold">You're shortlisted</p>
          <p className="text-xs text-muted">Coding Club moved your application to Interview.</p>
        </div>
      </div>
    </div>
  )
}

const features: { icon: ReactNode; title: string; body: string }[] = [
  {
    icon: <KanbanSquare className="size-5" />,
    title: 'Recruitment pipeline',
    body: 'Open a drive with your own questions. Move applicants from Applied to Interview to Selected on a board; selected students become members automatically.',
  },
  {
    icon: <ScanLine className="size-5" />,
    title: 'QR check-in',
    body: 'Every registrant gets a signed ticket. Scan it at the door from any phone; duplicates and forgeries bounce.',
  },
  {
    icon: <BadgeCheck className="size-5" />,
    title: 'Verifiable certificates',
    body: 'One click issues PDFs to everyone who attended. Recruiters verify them with the QR code, no login needed.',
  },
  {
    icon: <Bell className="size-5" />,
    title: 'Live notifications',
    body: 'Shortlisted? New event? It lands instantly in the app, and important updates arrive by email too.',
  },
  {
    icon: <ShieldCheck className="size-5" />,
    title: 'Roles & audit trail',
    body: 'Admins, core team and members see exactly what they should. Every change is recorded with who and when.',
  },
  {
    icon: <Users className="size-5" />,
    title: 'One account, many clubs',
    body: 'Students sign up once and join any number of clubs, with a different role in each.',
  },
]

export default function Landing() {
  const signedIn = !!useSession()

  return (
    <div className="min-h-screen overflow-x-hidden bg-paper">
      {/* nav */}
      <header className="sticky top-0 z-30 border-b border-line bg-paper/80 backdrop-blur-md">
        <div className="rails flex h-14 items-center justify-between">
          <div className="flex items-center gap-10">
            <Logo />
            <nav className="hidden items-center gap-6 font-mono text-[13px] tracking-wider text-muted uppercase md:flex">
              <a href="#features" className="transition hover:text-ink">Features</a>
              <a href="#how" className="transition hover:text-ink">How it works</a>
              <a href="#engineering" className="transition hover:text-ink">Under the hood</a>
            </nav>
          </div>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            {signedIn ? (
              <ButtonLink to="/app" size="sm">Open app</ButtonLink>
            ) : (
              <>
                <ButtonLink to="/login" variant="outline" size="sm">Sign in</ButtonLink>
                <ButtonLink to="/register" size="sm">Get started</ButtonLink>
              </>
            )}
          </div>
        </div>
      </header>

      {/* hero */}
      <section className="relative border-b border-line">
        <div className="glow pointer-events-none absolute inset-x-0 top-0 h-[520px]" />
        <div className="bg-dots pointer-events-none absolute inset-x-0 top-0 h-[520px]" />
        <div className="rails relative grid items-center gap-12 py-16 md:py-24 lg:grid-cols-[1.15fr_1fr]">
          <div className="animate-rise">
            <Eyebrow n={1}>Built for college clubs at SRM KTR</Eyebrow>
            <h1 className="mt-6 text-[2.5rem] leading-[1.08] font-bold sm:text-6xl md:text-[66px]">
              <span className="text-muted">Run your club</span>
              <br />
              like it's a <span className="text-signal-ink">startup.</span>
            </h1>
            <p className="mt-7 max-w-xl text-lg leading-relaxed text-ink-2">
              Recruitment drives, events with QR check-in, verifiable certificates and live notifications, in one
              workspace per club with its data isolated from every other club.
            </p>
            <div className="mt-9 flex flex-wrap gap-3">
              <ButtonLink to={signedIn ? '/app' : '/register'} size="lg" icon={<ArrowRight className="size-4" />} className="flex-row-reverse">
                {signedIn ? 'Go to your clubs' : 'Start for free'}
              </ButtonLink>
              <ButtonLink to={signedIn ? '/app/explore' : '/login'} variant="outline" size="lg">
                Explore clubs
              </ButtonLink>
            </div>
          </div>
          <HeroVisual />
        </div>
      </section>

      {/* club strip */}
      <div className="border-b border-line">
        <div className="rails overflow-hidden py-6">
          <div className="flex w-max animate-marquee gap-12 pr-12 font-mono text-sm tracking-wider text-muted uppercase">
            {[...clubs, ...clubs].map((club, i) => (
              <span key={i} className="flex items-center gap-12">
                {club}
                <span className="text-signal-ink">/</span>
              </span>
            ))}
          </div>
        </div>
      </div>

      {/* numbers */}
      <Section>
        <ConcentricArt className="pointer-events-none absolute top-0 right-0 hidden h-[240px] w-[55%] md:block" />
        <div className="relative">
          <Eyebrow n={2}>Built like production</Eyebrow>
          <Heading lead="Where club chaos turns into">one clean workspace.</Heading>
        </div>
        {/* 1px gaps over a line-coloured background draw the dividers at every breakpoint */}
        <dl className="relative mt-16 grid gap-px border-y border-line bg-line sm:grid-cols-2 lg:grid-cols-4">
          {[
            ['1', 'PostgreSQL schema per club'],
            ['180+', 'Integration tests on real infra'],
            ['4', 'Roles, from member to platform admin'],
            ['0', 'Paper forms at the door'],
          ].map(([value, label]) => (
            <div key={label} className="bg-paper px-6 py-10">
              <dt className="font-mono text-4xl font-bold tracking-tight sm:text-[2.6rem]">{value}</dt>
              <dd className="mt-5 max-w-[14rem] font-mono text-[13px] leading-relaxed font-medium tracking-wider text-ink-2 uppercase">{label}</dd>
            </div>
          ))}
        </dl>
      </Section>

      <div className="hatch" />

      {/* features */}
      <Section id="features">
        <Eyebrow n={3}>Everything a club does</Eyebrow>
        <Heading lead="From the first application">to the last certificate.</Heading>
        <div className="mt-14 grid overflow-hidden rounded-[var(--radius-card)] border border-line sm:grid-cols-2 lg:grid-cols-3">
          {features.map((f) => (
            <div key={f.title} className="group -mt-px -ml-px border-t border-l border-line bg-surface/40 p-7 transition hover:bg-paper-2">
              <div className="mb-6 grid size-10 place-items-center rounded-md border border-line text-ink-2 transition group-hover:border-signal/50 group-hover:text-signal-ink">
                {f.icon}
              </div>
              <h3 className="text-lg font-semibold tracking-tight">{f.title}</h3>
              <p className="mt-2 text-[15px] leading-relaxed text-muted">{f.body}</p>
            </div>
          ))}
        </div>
      </Section>

      {/* how it works */}
      <Section id="how">
        <Eyebrow n={4}>How it works</Eyebrow>
        <Heading lead="Three steps.">One semester of chaos, gone.</Heading>
        <div className="mt-14 grid gap-px overflow-hidden rounded-[var(--radius-card)] border border-line bg-line md:grid-cols-3">
          {[
            [Users, 'Set up your club', 'Your club gets its own private space. Invite your core team and give everyone the right role.'],
            [CalendarCheck2, 'Recruit & run events', 'Publish drives and events, review applicants on a board, check people in with QR codes.'],
            [BadgeCheck, 'Recognise your people', 'Certificates for attendees, notifications for everyone, a clean history for next year’s team.'],
          ].map(([Icon, title, body], i) => {
            const StepIcon = Icon as typeof Users
            return (
              <div key={title as string} className="bg-paper p-7">
                <div className="flex items-center justify-between">
                  <span className="font-mono text-sm font-medium text-signal-ink">STEP 0{i + 1}</span>
                  <StepIcon className="size-5 text-muted" />
                </div>
                <h3 className="mt-8 text-2xl font-semibold tracking-tight">{title as string}</h3>
                <p className="mt-3 leading-relaxed text-muted">{body as string}</p>
              </div>
            )
          })}
        </div>
      </Section>

      <div className="hatch" />

      {/* engineering */}
      <Section id="engineering">
        <div className="grid gap-12 lg:grid-cols-[1fr_1.2fr]">
          <div>
            <Eyebrow n={5}>Under the hood</Eyebrow>
            <Heading lead="A real multi-tenant SaaS," className="sm:text-[2.6rem]">not a template.</Heading>
            <p className="mt-6 leading-relaxed text-muted">
              Isolated tenants, signed tokens, event-driven notifications and 180+ integration tests against real
              PostgreSQL, Kafka and Redis.
            </p>
            <a
              href="https://github.com/Ayush0612005/clubhub-api"
              className="mt-8 inline-flex items-center gap-2 font-mono text-[13px] font-medium tracking-wider text-ink uppercase underline decoration-signal decoration-2 underline-offset-[6px] hover:text-signal-ink"
              target="_blank"
              rel="noreferrer"
            >
              <Code2 className="size-4" /> Read the source
            </a>
          </div>
          <div className="overflow-hidden rounded-[var(--radius-card)] border border-line bg-surface font-mono text-[13px]">
            <div className="flex items-center gap-1.5 border-b border-line px-4 py-3">
              <span className="size-2.5 rounded-full bg-line" />
              <span className="size-2.5 rounded-full bg-line" />
              <span className="size-2.5 rounded-full bg-line" />
              <span className="ml-3 text-xs text-muted">clubhub/architecture</span>
            </div>
            {[
              ['tenancy', 'PostgreSQL schema per club · Hibernate multi-tenancy'],
              ['auth', 'JWT access + rotating refresh tokens · per-club roles'],
              ['events', 'Kafka domain events → inbox · WebSocket · email'],
              ['limits', 'Redis token buckets per club and per IP'],
              ['files', 'S3 pre-signed uploads · OpenPDF certificates'],
              ['stack', 'Java 25 · Spring Boot 4 · React 19 · Tailwind'],
            ].map(([key, value]) => (
              <div key={key} className="flex gap-4 border-b border-line px-5 py-3.5 last:border-0">
                <span className="w-20 shrink-0 text-signal-ink">{key}</span>
                <span className="text-ink-2">{value}</span>
              </div>
            ))}
          </div>
        </div>
      </Section>

      {/* call to action */}
      <section className="relative border-b border-line">
        <div className="glow pointer-events-none absolute inset-0 opacity-70" />
        <div className="rails relative py-24 text-center">
          <Eyebrow n={6}>Get started</Eyebrow>
          <h2 className="mx-auto mt-5 max-w-2xl text-4xl leading-[1.1] font-semibold sm:text-5xl">
            <span className="text-muted">Your club's next semester</span>
            <br />
            starts here.
          </h2>
          <div className="mt-10 flex flex-wrap justify-center gap-3">
            <ButtonLink to={signedIn ? '/app' : '/register'} size="lg">Create your account</ButtonLink>
            <ButtonLink to={signedIn ? '/app/explore' : '/login'} variant="outline" size="lg">Explore clubs</ButtonLink>
          </div>
        </div>
      </section>

      <footer>
        <div className="rails flex flex-wrap items-center justify-between gap-4 py-8">
          <Logo />
          <p className="font-mono text-xs tracking-wider text-muted uppercase">Made at SRM KTR · {new Date().getFullYear()}</p>
        </div>
      </footer>
    </div>
  )
}
