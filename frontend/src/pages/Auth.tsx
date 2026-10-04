import { useQueryClient } from '@tanstack/react-query'
import { CalendarCheck, CheckCircle2, Mail, QrCode, ScrollText, Users, XCircle } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { Logo, ThemeToggle } from '../components/brand'
import { DemoButton } from '../components/DemoButton'
import { Button, ButtonLink, ErrorNote, Input, Spinner } from '../components/ui'
import { ApiError, forgotPassword, login, register, resendVerification, resetPassword, verifyEmail } from '../lib/api'

const linkClass = 'font-semibold text-ink underline decoration-signal underline-offset-4'

const highlights = [
  { icon: <Users className="size-4" />, text: 'Recruitment drives with a kanban pipeline' },
  { icon: <CalendarCheck className="size-4" />, text: 'Events with registrations and capacity' },
  { icon: <QrCode className="size-4" />, text: 'QR tickets and door check-in' },
  { icon: <ScrollText className="size-4" />, text: 'Certificates anyone can verify' },
]

function AuthShell({ title, subtitle, children, footer }: { title: string; subtitle: ReactNode; children: ReactNode; footer?: ReactNode }) {
  return (
    <div className="grid min-h-screen bg-paper lg:grid-cols-2">
      <aside className="relative hidden overflow-hidden border-r border-line bg-paper-2 p-12 lg:flex lg:flex-col lg:justify-between">
        <div className="bg-dots pointer-events-none absolute inset-0 opacity-40" />
        <div className="pointer-events-none absolute -top-32 -right-32 size-[28rem] rounded-full bg-signal/15 blur-3xl" />
        <Logo className="relative w-fit" />
        <div className="relative max-w-md">
          <p className="eyebrow">For SRM KTR clubs</p>
          <h2 className="mt-4 text-4xl leading-tight font-bold tracking-tight">
            Run your club from one place, <span className="text-signal-ink">not six Google Forms.</span>
          </h2>
          <ul className="mt-10 space-y-3">
            {highlights.map((item) => (
              <li key={item.text} className="flex items-center gap-3 text-ink-2">
                <span className="grid size-8 place-items-center rounded-md border border-line bg-surface text-signal-ink">{item.icon}</span>
                <span className="text-sm">{item.text}</span>
              </li>
            ))}
          </ul>
        </div>
        <p className="relative font-mono text-xs text-muted">Sign in with your @srmist.edu.in email</p>
      </aside>

      <main className="flex flex-col">
        <div className="flex items-center justify-between p-5 lg:justify-end">
          <Logo className="lg:hidden" />
          <ThemeToggle />
        </div>
        <div className="flex flex-1 items-center justify-center px-5 pb-16">
          <div className="animate-rise w-full max-w-sm">
            <h1 className="text-3xl font-bold tracking-tight">{title}</h1>
            <p className="mt-2 text-muted">{subtitle}</p>
            <div className="mt-8">{children}</div>
            {footer && <p className="mt-6 text-center text-sm text-muted">{footer}</p>}
          </div>
        </div>
      </main>
    </div>
  )
}

/** Shown after sign-up (and when an unverified user tries to log in). */
function CheckInbox({ email }: { email: string }) {
  const [sent, setSent] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<unknown>(null)

  async function resend() {
    setPending(true)
    setError(null)
    try {
      await resendVerification(email)
      setSent(true)
    } catch (err) {
      setError(err)
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex gap-3 rounded-lg border border-line bg-surface p-4">
        <Mail className="mt-0.5 size-5 shrink-0 text-signal-ink" />
        <p className="text-sm text-ink-2">
          We sent a confirmation link to <span className="font-semibold text-ink">{email}</span>. Open it on any device to activate your
          account. It can take a minute; check spam or Outlook's "Other" tab too.
        </p>
      </div>
      <ErrorNote error={error} />
      <Button variant="outline" className="w-full" onClick={resend} loading={pending} disabled={sent}>
        {sent ? 'Sent. Check your inbox' : 'Resend the link'}
      </Button>
    </div>
  )
}

export function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [unverified, setUnverified] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      await login(email, password)
      queryClient.clear()
      navigate((location.state as { from?: string } | null)?.from ?? '/app', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.code === 'EMAIL_NOT_VERIFIED') setUnverified(true)
      else setError(err)
    } finally {
      setPending(false)
    }
  }

  if (unverified) {
    return (
      <AuthShell title="Confirm your email" subtitle="One last step before you can log in.">
        <CheckInbox email={email} />
        <button onClick={() => setUnverified(false)} className="mt-6 w-full text-center text-sm text-muted hover:text-ink">
          ← Back to log in
        </button>
      </AuthShell>
    )
  }

  return (
    <AuthShell
      title="Welcome back"
      subtitle="Log in to your clubs, events and applications."
      footer={
        <>
          New here? <Link to="/register" className={linkClass}>Create an account</Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-4">
        <Input label="College email" type="email" autoComplete="email" placeholder="you@srmist.edu.in" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <div>
          <Input label="Password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          <Link to="/forgot-password" state={{ email }} className="mt-2 inline-block text-xs text-muted hover:text-ink">
            Forgot password?
          </Link>
        </div>
        <ErrorNote error={error} />
        <Button type="submit" className="w-full" size="lg" loading={pending}>Log in</Button>
      </form>
      <div className="mt-6 border-t border-line pt-6 text-center">
        <p className="mb-3 text-sm text-muted">Just looking around? No account needed.</p>
        <DemoButton variant="outline" size="md" className="w-full" />
      </div>
    </AuthShell>
  )
}

export function Register() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [form, setForm] = useState({ fullName: '', email: '', password: '' })
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [awaitingEmail, setAwaitingEmail] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      const { verificationRequired } = await register(form.fullName, form.email, form.password)
      if (verificationRequired) {
        setAwaitingEmail(true)
      } else {
        queryClient.clear()
        navigate('/app', { replace: true })
      }
    } catch (err) {
      setError(err)
    } finally {
      setPending(false)
    }
  }

  if (awaitingEmail) {
    return (
      <AuthShell title="Check your inbox" subtitle="Your account is almost ready." footer={<>Confirmed it? <Link to="/login" className={linkClass}>Log in</Link></>}>
        <CheckInbox email={form.email} />
      </AuthShell>
    )
  }

  return (
    <AuthShell
      title="Create your account"
      subtitle="One account for every club you join."
      footer={
        <>
          Already have one? <Link to="/login" className={linkClass}>Log in</Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-4">
        <Input label="Full name" autoComplete="name" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required maxLength={120} />
        <Input
          label="College email"
          type="email"
          autoComplete="email"
          placeholder="you@srmist.edu.in"
          hint="Only @srmist.edu.in addresses can join."
          value={form.email}
          onChange={(e) => setForm({ ...form, email: e.target.value })}
          required
        />
        <Input
          label="Password"
          type="password"
          autoComplete="new-password"
          hint="At least 8 characters."
          minLength={8}
          maxLength={72}
          value={form.password}
          onChange={(e) => setForm({ ...form, password: e.target.value })}
          required
        />
        <ErrorNote error={error} />
        <Button type="submit" className="w-full" size="lg" loading={pending}>Create account</Button>
      </form>
    </AuthShell>
  )
}

/** Landing page of the link in the confirmation email: /verify-email?token=... */
export function VerifyEmail() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [state, setState] = useState<'working' | 'done' | 'failed'>(token ? 'working' : 'failed')
  const [error, setError] = useState<unknown>(token ? null : new Error('This link is missing its token. Open it straight from the email.'))
  const started = useRef(false) // tokens are single-use: StrictMode's double effect must not spend it twice

  useEffect(() => {
    if (!token || started.current) return
    started.current = true
    verifyEmail(token)
      .then(() => setState('done'))
      .catch((err) => {
        setError(err)
        setState('failed')
      })
  }, [token])

  return (
    <AuthShell title={state === 'done' ? 'Email confirmed' : state === 'failed' ? 'Link not valid' : 'Confirming…'} subtitle={state === 'done' ? 'Your account is active.' : ' '}>
      {state === 'working' && <div className="grid place-items-center py-8"><Spinner /></div>}
      {state === 'done' && (
        <div className="space-y-5">
          <p className="flex items-center gap-2 text-sm text-ink-2"><CheckCircle2 className="size-5 text-forest" /> You can log in now.</p>
          <ButtonLink to="/login" className="w-full" size="lg">Log in</ButtonLink>
        </div>
      )}
      {state === 'failed' && (
        <div className="space-y-5">
          <p className="flex items-center gap-2 text-sm text-ink-2"><XCircle className="size-5 text-berry" /> It may have expired or already been used.</p>
          <ErrorNote error={error} />
          <p className="text-sm text-muted">Log in with your email and password, and we'll offer to send a fresh link.</p>
          <ButtonLink to="/login" variant="outline" className="w-full">Go to log in</ButtonLink>
        </div>
      )}
    </AuthShell>
  )
}

export function ForgotPassword() {
  const location = useLocation()
  const [email, setEmail] = useState((location.state as { email?: string } | null)?.email ?? '')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [sent, setSent] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      await forgotPassword(email)
      setSent(true)
    } catch (err) {
      setError(err)
    } finally {
      setPending(false)
    }
  }

  return (
    <AuthShell
      title="Reset your password"
      subtitle="We'll email you a link to choose a new one."
      footer={<>Remembered it? <Link to="/login" className={linkClass}>Log in</Link></>}
    >
      {sent ? (
        <div className="flex gap-3 rounded-lg border border-line bg-surface p-4">
          <Mail className="mt-0.5 size-5 shrink-0 text-signal-ink" />
          <p className="text-sm text-ink-2">
            If <span className="font-semibold text-ink">{email}</span> has a ClubHub account, a reset link is on its way. It expires in 30 minutes.
          </p>
        </div>
      ) : (
        <form onSubmit={submit} className="space-y-4">
          <Input label="College email" type="email" autoComplete="email" placeholder="you@srmist.edu.in" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <ErrorNote error={error} />
          <Button type="submit" className="w-full" size="lg" loading={pending}>Send reset link</Button>
        </form>
      )}
    </AuthShell>
  )
}

/** Landing page of the link in the reset email: /reset-password?token=... */
export function ResetPassword() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<unknown>(token ? null : new Error('This link is missing its token. Open it straight from the email.'))
  const [done, setDone] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (password !== confirm) {
      setError(new Error("The two passwords don't match."))
      return
    }
    setPending(true)
    setError(null)
    try {
      await resetPassword(token, password)
      setDone(true)
    } catch (err) {
      setError(err)
    } finally {
      setPending(false)
    }
  }

  if (done) {
    return (
      <AuthShell title="Password changed" subtitle="You've been logged out everywhere else.">
        <ButtonLink to="/login" className="w-full" size="lg">Log in with your new password</ButtonLink>
      </AuthShell>
    )
  }

  return (
    <AuthShell title="Choose a new password" subtitle="At least 8 characters." footer={<><Link to="/forgot-password" className={linkClass}>Get a new link</Link></>}>
      <form onSubmit={submit} className="space-y-4">
        <Input label="New password" type="password" autoComplete="new-password" minLength={8} maxLength={72} value={password} onChange={(e) => setPassword(e.target.value)} required />
        <Input label="Confirm password" type="password" autoComplete="new-password" minLength={8} maxLength={72} value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
        <ErrorNote error={error} />
        <Button type="submit" className="w-full" size="lg" loading={pending} disabled={!token}>Save new password</Button>
      </form>
    </AuthShell>
  )
}
