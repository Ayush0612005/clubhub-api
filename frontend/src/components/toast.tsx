import clsx from 'clsx'
import { Bell, CircleAlert, CircleCheck } from 'lucide-react'
import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'

type Kind = 'success' | 'error' | 'info'

interface Toast {
  id: number
  kind: Kind
  title: string
  body?: string
}

const ToastContext = createContext<(kind: Kind, title: string, body?: string) => void>(() => undefined)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const push = useCallback((kind: Kind, title: string, body?: string) => {
    const id = Date.now() + Math.random()
    setToasts((all) => [...all.slice(-3), { id, kind, title, body }])
    setTimeout(() => setToasts((all) => all.filter((t) => t.id !== id)), 4500)
  }, [])

  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="pointer-events-none fixed right-4 bottom-4 z-[60] flex w-[min(92vw,380px)] flex-col gap-2" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className="animate-rise pointer-events-auto flex gap-3 rounded-2xl border border-line bg-surface p-4 shadow-lift">
            <span className={clsx('mt-0.5', t.kind === 'success' && 'text-forest', t.kind === 'error' && 'text-berry', t.kind === 'info' && 'text-signal')}>
              {t.kind === 'success' ? <CircleCheck className="size-5" /> : t.kind === 'error' ? <CircleAlert className="size-5" /> : <Bell className="size-5" />}
            </span>
            <div className="min-w-0">
              <p className="text-sm font-semibold">{t.title}</p>
              {t.body && <p className="mt-0.5 text-sm text-muted">{t.body}</p>}
            </div>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const push = useContext(ToastContext)
  return {
    success: (title: string, body?: string) => push('success', title, body),
    error: (err: unknown) => push('error', 'That didn’t work', err instanceof Error ? err.message : String(err)),
    info: (title: string, body?: string) => push('info', title, body),
  }
}
