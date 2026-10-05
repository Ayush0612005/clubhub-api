import { useSyncExternalStore } from 'react'
import { serverWaking } from '../lib/api'
import { Spinner } from './ui'

/** Shown while a request waits for the free-tier API to boot, so a cold start reads as "loading", not "broken". */
export function WakingNotice() {
  const waking = useSyncExternalStore(serverWaking.subscribe, serverWaking.get)
  if (!waking) return null
  return (
    <div role="status" aria-live="polite" className="fixed inset-x-0 bottom-5 z-50 flex justify-center px-4">
      <div className="flex max-w-md items-center gap-3 rounded-lg border border-line bg-surface px-4 py-3 text-sm shadow-lift">
        <Spinner />
        <span>
          <span className="font-semibold">Waking up the server…</span>{' '}
          <span className="text-muted">It sleeps when nobody is using it; this takes about a minute.</span>
        </span>
      </div>
    </div>
  )
}
