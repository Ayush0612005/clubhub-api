import { useQuery } from '@tanstack/react-query'
import { Maximize2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { fmt } from '../lib/format'
import type { ClubEvent } from '../lib/types'
import { Modal, Skeleton } from './ui'

/**
 * The attendee's ticket: the server renders the signed ticket as a QR PNG (ZXing); we fetch it with
 * the bearer token as a blob. Tap to open it full-screen for the door scanner.
 */
export function Ticket({ event, clubName, loadQr }: { event: ClubEvent; clubName: string; loadQr: () => Promise<string> }) {
  const [big, setBig] = useState(false)
  const qr = useQuery({ queryKey: ['ticket-qr', event.id, clubName], queryFn: loadQr, staleTime: Infinity })

  useEffect(() => () => {
    if (qr.data) URL.revokeObjectURL(qr.data)
  }, [qr.data])

  return (
    <>
      <div className="rounded-[1.75rem] bg-ink p-1.5 shadow-lift">
        <div className="rounded-[1.4rem] bg-surface p-6">
          <p className="font-mono text-[10px] tracking-[0.2em] text-signal uppercase">Admit one · {clubName}</p>
          <p className="mt-2 font-display text-2xl leading-tight font-bold">{event.title}</p>
          <p className="mt-1 text-sm text-muted">{fmt.dateTime(event.startsAt)} · {event.venue}</p>
          <div className="ticket-notch my-5 border-t-2 border-dashed border-line" />
          <button onClick={() => setBig(true)} className="group relative mx-auto block rounded-2xl bg-white p-3" aria-label="Show ticket full screen">
            {qr.data ? <img src={qr.data} alt="Your ticket QR code" className="size-44" /> : <Skeleton className="size-44" />}
            <span className="absolute right-2 bottom-2 rounded-lg bg-ink/80 p-1 text-white opacity-0 transition group-hover:opacity-100">
              <Maximize2 className="size-3.5" />
            </span>
          </button>
          <p className="mt-4 text-center text-xs text-muted">Show this at the door. It only works for this event.</p>
        </div>
      </div>
      <Modal open={big} onClose={() => setBig(false)} title={event.title} description="Turn your screen brightness up for the scanner.">
        {qr.data && <img src={qr.data} alt="Your ticket QR code" className="mx-auto w-full max-w-xs rounded-2xl bg-white p-4" />}
      </Modal>
    </>
  )
}
