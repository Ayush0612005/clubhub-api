import clsx from 'clsx'
import { Moon, Sun } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'

/** The ClubHub mark: an orange "member" dot meeting a paper "ticket" — people + events. */
export function Logo({ className, compact }: { className?: string; compact?: boolean }) {
  return (
    <Link to="/" className={clsx('inline-flex items-center gap-2.5', className)} aria-label="ClubHub home">
      <svg viewBox="0 0 32 32" className="size-8 shrink-0" aria-hidden>
        <rect width="32" height="32" rx="9" className="fill-ink" />
        <circle cx="12" cy="16" r="5" className="fill-signal" />
        <rect x="17" y="10.5" width="6.5" height="11" rx="3.25" className="fill-paper" />
      </svg>
      {!compact && <span className="font-display text-[19px] font-bold tracking-tight">ClubHub</span>}
    </Link>
  )
}

const THEME_KEY = 'clubhub.theme'

export function applyStoredTheme() {
  let stored: string | null = null
  try {
    stored = localStorage.getItem(THEME_KEY)
  } catch {
    // storage blocked: fall back to the default
  }
  const dark = stored !== 'light' // dark is the brand default
  document.documentElement.classList.toggle('dark', dark)
}

export function ThemeToggle({ className }: { className?: string }) {
  const [dark, setDark] = useState(() => document.documentElement.classList.contains('dark'))

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark)
    localStorage.setItem(THEME_KEY, dark ? 'dark' : 'light')
  }, [dark])

  return (
    <button
      onClick={() => setDark((d) => !d)}
      className={clsx('grid size-9 place-items-center rounded-xl text-ink-2 transition hover:bg-paper-2 hover:text-ink', className)}
      aria-label={dark ? 'Switch to light theme' : 'Switch to dark theme'}
    >
      {dark ? <Sun className="size-[18px]" /> : <Moon className="size-[18px]" />}
    </button>
  )
}
