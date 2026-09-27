import { useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { QrArt } from '../components/art'
import { Logo, ThemeToggle } from '../components/brand'
import { Button, ErrorNote, Input } from '../components/ui'
import { login, register } from '../lib/api'

function AuthShell({ title, subtitle, children, footer }: { title: string; subtitle: string; children: ReactNode; footer: ReactNode }) {
  return (
    <div className="grid min-h-screen bg-paper lg:grid-cols-2">
      <aside className="relative hidden overflow-hidden bg-ink p-12 text-paper lg:flex lg:flex-col lg:justify-between">
        <div className="absolute -top-24 -right-24 size-96 rounded-full bg-signal/20 blur-3xl" />
        <Link to="/" className="relative inline-flex items-center gap-2.5">
          <span className="grid size-8 place-items-center rounded-[9px] bg-paper">
            <span className="size-3 rounded-full bg-signal" />
          </span>
          <span className="font-display text-xl font-bold">ClubHub</span>
        </Link>
        <div className="relative">
          <div className="mb-10 w-fit rotate-[-3deg] rounded-3xl bg-paper p-4 shadow-lift">
            <QrArt seed="auth" size={112} />
          </div>
          <p className="max-w-md font-display text-4xl leading-tight font-bold">
            “We replaced six Google Forms, two spreadsheets and a WhatsApp group.”
          </p>
          <p className="mt-4 text-paper/60">— what we want every club secretary to say</p>
        </div>
        <p className="relative font-mono text-xs text-paper/40">Recruitment · Events · QR attendance · Certificates</p>
      </aside>

      <main className="flex flex-col">
        <div className="flex items-center justify-between p-5 lg:justify-end">
          <span className="lg:hidden"><Logo /></span>
          <ThemeToggle />
        </div>
        <div className="flex flex-1 items-center justify-center px-5 pb-16">
          <div className="animate-rise w-full max-w-sm">
            <h1 className="text-3xl font-bold">{title}</h1>
            <p className="mt-2 text-muted">{subtitle}</p>
            <div className="mt-8">{children}</div>
            <p className="mt-6 text-center text-sm text-muted">{footer}</p>
          </div>
        </div>
      </main>
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

  async function submit(e: FormEvent) {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      await login(email, password)
      queryClient.clear()
      navigate((location.state as { from?: string } | null)?.from ?? '/app', { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setPending(false)
    }
  }

  return (
    <AuthShell
      title="Welcome back"
      subtitle="Log in to your clubs, events and applications."
      footer={
        <>
          New here? <Link to="/register" className="font-semibold text-ink underline decoration-signal underline-offset-4">Create an account</Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-4">
        <Input label="College email" type="email" autoComplete="email" placeholder="you@srmist.edu.in" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <Input label="Password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        <ErrorNote error={error} />
        <Button type="submit" className="w-full" size="lg" loading={pending}>Log in</Button>
      </form>
    </AuthShell>
  )
}

export function Register() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [form, setForm] = useState({ fullName: '', email: '', password: '' })
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<unknown>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      await register(form.fullName, form.email, form.password)
      queryClient.clear()
      navigate('/app/explore', { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setPending(false)
    }
  }

  return (
    <AuthShell
      title="Create your account"
      subtitle="One account for every club you join."
      footer={
        <>
          Already have one? <Link to="/login" className="font-semibold text-ink underline decoration-signal underline-offset-4">Log in</Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-4">
        <Input label="Full name" autoComplete="name" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required maxLength={120} />
        <Input label="College email" type="email" autoComplete="email" placeholder="you@srmist.edu.in" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
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
