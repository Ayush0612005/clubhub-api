import clsx from 'clsx'
import { LoaderCircle, X } from 'lucide-react'
import { useEffect, useId, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
import { Link, type LinkProps } from 'react-router'
import { colorFor, fmt } from '../lib/format'

// ---------------------------------------------------------------- buttons

type Variant = 'primary' | 'ink' | 'outline' | 'ghost' | 'danger'
type Size = 'sm' | 'md' | 'lg'

const variants: Record<Variant, string> = {
  primary: 'bg-signal text-white hover:bg-signal-600 shadow-[0_1px_0_rgb(0_0_0/0.08),inset_0_1px_0_rgb(255_255_255/0.2)]',
  ink: 'bg-ink text-paper hover:bg-ink-2',
  outline: 'border border-line bg-surface text-ink hover:border-ink/30 hover:bg-paper-2',
  ghost: 'text-ink-2 hover:bg-paper-2 hover:text-ink',
  danger: 'bg-berry text-white hover:brightness-110',
}

const sizes: Record<Size, string> = {
  sm: 'h-8 px-3 text-[13px] gap-1.5 rounded-lg',
  md: 'h-10 px-4 text-sm gap-2 rounded-xl',
  lg: 'h-12 px-6 text-[15px] gap-2 rounded-xl',
}

export function buttonClass(variant: Variant = 'primary', size: Size = 'md', extra?: string) {
  return clsx(
    'inline-flex items-center justify-center font-medium whitespace-nowrap transition-all duration-150',
    'active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50',
    variants[variant],
    sizes[size],
    extra,
  )
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
  icon?: ReactNode
}

export function Button({ variant, size, loading, icon, className, children, disabled, ...rest }: ButtonProps) {
  return (
    <button className={buttonClass(variant, size, className)} disabled={disabled || loading} {...rest}>
      {loading ? <LoaderCircle className="size-4 animate-spin" /> : icon}
      {children}
    </button>
  )
}

export function ButtonLink({ variant, size, className, icon, children, ...rest }: LinkProps & { variant?: Variant; size?: Size; icon?: ReactNode }) {
  return (
    <Link className={buttonClass(variant, size, className)} {...rest}>
      {icon}
      {children}
    </Link>
  )
}

// ---------------------------------------------------------------- form fields

const fieldBase =
  'w-full rounded-xl border border-line bg-surface px-3.5 text-sm text-ink placeholder:text-muted/70 transition ' +
  'focus:border-ink/40 focus:outline-none focus:ring-4 focus:ring-signal/10 disabled:opacity-60'

interface FieldProps {
  label?: string
  hint?: string
  error?: string
}

function Field({ label, hint, error, id, children }: FieldProps & { id: string; children: ReactNode }) {
  return (
    <div className="space-y-1.5">
      {label && (
        <label htmlFor={id} className="block text-[13px] font-medium text-ink-2">
          {label}
        </label>
      )}
      {children}
      {error ? <p className="text-xs text-berry">{error}</p> : hint && <p className="text-xs text-muted">{hint}</p>}
    </div>
  )
}

export function Input({ label, hint, error, className, ...rest }: FieldProps & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId()
  return (
    <Field id={id} label={label} hint={hint} error={error}>
      <input id={id} className={clsx(fieldBase, 'h-10', className)} {...rest} />
    </Field>
  )
}

export function Textarea({ label, hint, error, className, ...rest }: FieldProps & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId()
  return (
    <Field id={id} label={label} hint={hint} error={error}>
      <textarea id={id} className={clsx(fieldBase, 'min-h-24 py-2.5', className)} {...rest} />
    </Field>
  )
}

export function Select({ label, hint, error, className, children, ...rest }: FieldProps & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId()
  return (
    <Field id={id} label={label} hint={hint} error={error}>
      <select id={id} className={clsx(fieldBase, 'h-10 pr-8', className)} {...rest}>
        {children}
      </select>
    </Field>
  )
}

// ---------------------------------------------------------------- surfaces

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={clsx('rounded-[var(--radius-card)] border border-line bg-surface shadow-soft', className)}>{children}</div>
}

type Tone = 'neutral' | 'signal' | 'forest' | 'cobalt' | 'berry' | 'amber' | 'ink'

const tones: Record<Tone, string> = {
  neutral: 'bg-paper-2 text-ink-2 ring-line',
  signal: 'bg-signal-50 text-signal-600 ring-signal/20',
  forest: 'bg-forest-50 text-forest ring-forest/20',
  cobalt: 'bg-cobalt-50 text-cobalt ring-cobalt/20',
  berry: 'bg-berry-50 text-berry ring-berry/20',
  amber: 'bg-amber-50 text-amber ring-amber/20',
  ink: 'bg-ink text-paper ring-ink',
}

export function Badge({ tone = 'neutral', children, dot, className }: { tone?: Tone; children: ReactNode; dot?: boolean; className?: string }) {
  return (
    <span className={clsx('inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[12px] font-medium ring-1 ring-inset', tones[tone], className)}>
      {dot && <span className="size-1.5 rounded-full bg-current" />}
      {children}
    </span>
  )
}

const statusTones: Record<string, Tone> = {
  DRAFT: 'neutral',
  OPEN: 'forest',
  CLOSED: 'ink',
  PUBLISHED: 'forest',
  CANCELLED: 'berry',
  APPLIED: 'cobalt',
  SHORTLISTED: 'amber',
  INTERVIEW: 'signal',
  SELECTED: 'forest',
  REJECTED: 'berry',
  WITHDRAWN: 'neutral',
  CLUB_ADMIN: 'ink',
  CORE: 'signal',
  MEMBER: 'neutral',
  FREE: 'neutral',
  PRO: 'signal',
}

export function StatusBadge({ status }: { status: string }) {
  return (
    <Badge tone={statusTones[status] ?? 'neutral'} dot>
      {fmt.label(status)}
    </Badge>
  )
}

export function Avatar({ name, size = 36, square }: { name: string; size?: number; square?: boolean }) {
  return (
    <span
      className={clsx('inline-grid shrink-0 place-items-center font-display font-semibold text-white', square ? 'rounded-xl' : 'rounded-full')}
      style={{ width: size, height: size, background: colorFor(name), fontSize: size * 0.38 }}
      aria-hidden
    >
      {fmt.initials(name) || '?'}
    </span>
  )
}

export function Spinner({ className }: { className?: string }) {
  return <LoaderCircle className={clsx('size-5 animate-spin text-muted', className)} />
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={clsx('animate-pulse rounded-xl bg-paper-2', className)} />
}

export function EmptyState({ icon, title, children, action }: { icon: ReactNode; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-[var(--radius-card)] border border-dashed border-line px-6 py-14 text-center">
      <div className="mb-4 grid size-12 place-items-center rounded-2xl bg-paper-2 text-ink-2">{icon}</div>
      <h3 className="text-lg font-semibold">{title}</h3>
      {children && <p className="mt-1 max-w-sm text-sm text-muted">{children}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function PageHeader({ eyebrow, title, description, actions }: { eyebrow?: string; title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="animate-rise">
        {eyebrow && <p className="mb-2 font-mono text-[11px] uppercase tracking-[0.18em] text-muted">{eyebrow}</p>}
        <h1 className="text-3xl font-bold sm:text-[2.2rem]">{title}</h1>
        {description && <p className="mt-2 max-w-2xl text-[15px] text-muted">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>}
    </div>
  )
}

export function Stat({ label, value, hint, accent }: { label: string; value: ReactNode; hint?: ReactNode; accent?: boolean }) {
  if (accent) {
    // Not a <Card>: its bg-surface would win the cascade over an override class.
    return (
      <div className="relative overflow-hidden rounded-[var(--radius-card)] bg-signal p-5 text-white shadow-soft">
        <div className="pointer-events-none absolute -right-6 -top-6 size-24 rounded-full bg-white/10" />
        <p className="text-[13px] text-white/75">{label}</p>
        <p className="mt-2 font-display text-3xl font-bold tracking-tight">{value}</p>
        {hint && <p className="mt-1 truncate text-xs text-white/75">{hint}</p>}
      </div>
    )
  }
  return (
    <Card className="p-5">
      <p className="text-[13px] text-muted">{label}</p>
      <p className="mt-2 font-display text-3xl font-bold tracking-tight">{value}</p>
      {hint && <p className="mt-1 text-xs text-muted">{hint}</p>}
    </Card>
  )
}

/** Progress of a plan limit: turns amber near the cap and red at it. */
export function Meter({ used, max }: { used: number; max: number }) {
  const ratio = max > 0 ? Math.min(used / max, 1) : 0
  return (
    <div className="h-1.5 w-full overflow-hidden rounded-full bg-paper-2">
      <div
        className={clsx('h-full rounded-full transition-all', ratio >= 1 ? 'bg-berry' : ratio >= 0.8 ? 'bg-amber' : 'bg-forest')}
        style={{ width: `${Math.max(ratio * 100, 3)}%` }}
      />
    </div>
  )
}

// ---------------------------------------------------------------- modal

export function Modal({ open, onClose, title, description, children, wide }: { open: boolean; onClose: () => void; title: string; description?: string; children: ReactNode; wide?: boolean }) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/40 p-0 backdrop-blur-[2px] sm:items-center sm:p-6" onMouseDown={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={clsx('animate-rise max-h-[92vh] w-full overflow-y-auto rounded-t-3xl border border-line bg-surface p-6 shadow-lift sm:rounded-3xl', wide ? 'sm:max-w-2xl' : 'sm:max-w-lg')}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h2 className="text-xl font-bold">{title}</h2>
            {description && <p className="mt-1 text-sm text-muted">{description}</p>}
          </div>
          <button onClick={onClose} className="rounded-lg p-1.5 text-muted hover:bg-paper-2 hover:text-ink" aria-label="Close">
            <X className="size-5" />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof Error ? error.message : 'Something went wrong'
  return <p className="rounded-xl border border-berry/20 bg-berry-50 px-3.5 py-2.5 text-sm text-berry">{message}</p>
}
