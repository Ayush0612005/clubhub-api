import { useQuery, useQueryClient } from '@tanstack/react-query'
import { BrowserQRCodeReader, type IScannerControls } from '@zxing/browser'
import clsx from 'clsx'
import { ArrowLeft, CameraOff, CircleCheckBig, CircleX, ScanLine } from 'lucide-react'
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { Button, Card, Input } from '../../components/ui'
import { useClub } from '../../layouts/ClubLayout'
import { clubApi } from '../../lib/club'
import { fmt } from '../../lib/format'
import type { AttendanceSummary, CheckIn, ClubEvent } from '../../lib/types'

type Result = { ok: true; checkIn: CheckIn } | { ok: false; message: string }

/**
 * Door scanner: the phone camera decodes the attendee's QR (ZXing in the browser) and posts the
 * ticket to the API, which verifies the signature, club, event, time window and duplicates.
 */
export default function Scanner() {
  const { eventId = '' } = useParams()
  const { club } = useClub()
  const api = clubApi(club.slug)
  const queryClient = useQueryClient()
  const video = useRef<HTMLVideoElement>(null)
  const lastScan = useRef<{ text: string; at: number } | null>(null)
  const [result, setResult] = useState<Result | null>(null)
  const [cameraError, setCameraError] = useState<string | null>(null)
  const [manual, setManual] = useState('')

  const event = useQuery({ queryKey: ['club', club.slug, 'event', eventId], queryFn: () => api.get<ClubEvent>(`/events/${eventId}`) })
  const summary = useQuery({
    queryKey: ['club', club.slug, 'event', eventId, 'attendance'],
    queryFn: () => api.get<AttendanceSummary>(`/events/${eventId}/attendance`),
  })

  const submit = useCallback(
    async (ticket: string) => {
      try {
        const checkIn = await clubApi(club.slug).post<CheckIn>(`/events/${eventId}/check-ins`, { ticket })
        setResult({ ok: true, checkIn })
        navigator.vibrate?.(80)
        queryClient.invalidateQueries({ queryKey: ['club', club.slug, 'event', eventId] })
      } catch (err) {
        setResult({ ok: false, message: err instanceof Error ? err.message : 'Scan failed' })
        navigator.vibrate?.([60, 40, 60])
      }
    },
    [club.slug, eventId, queryClient],
  )

  useEffect(() => {
    let controls: IScannerControls | undefined
    const reader = new BrowserQRCodeReader()
    if (!video.current) return
    reader
      .decodeFromVideoDevice(undefined, video.current, (res) => {
        if (!res) return
        const text = res.getText()
        const now = Date.now()
        // the camera sees the same code many times a second: submit it once per 4 s
        if (lastScan.current && lastScan.current.text === text && now - lastScan.current.at < 4000) return
        lastScan.current = { text, at: now }
        void submit(text)
      })
      .then((c) => (controls = c))
      .catch(() => setCameraError('Camera unavailable — allow camera access, or paste tickets below.'))
    return () => controls?.stop()
  }, [submit])

  function submitManual(e: FormEvent) {
    e.preventDefault()
    if (manual.trim()) void submit(manual.trim())
    setManual('')
  }

  return (
    <div className="mx-auto max-w-5xl">
      <Link to=".." relative="path" className="mb-6 inline-flex items-center gap-1.5 text-sm text-muted hover:text-ink">
        <ArrowLeft className="size-4" /> {event.data?.title ?? 'Event'}
      </Link>
      <div className="grid gap-6 lg:grid-cols-[1.2fr_1fr]">
        <div className="relative aspect-square overflow-hidden rounded-[2rem] bg-ink shadow-lift sm:aspect-[4/3]">
          <video ref={video} className="size-full object-cover" muted playsInline />
          {cameraError ? (
            <div className="absolute inset-0 grid place-items-center p-8 text-center text-paper/80">
              <div>
                <CameraOff className="mx-auto mb-3 size-8" />
                {cameraError}
              </div>
            </div>
          ) : (
            <div className="pointer-events-none absolute inset-0 grid place-items-center">
              <div className="relative size-56 rounded-3xl border-2 border-white/80 shadow-[0_0_0_9999px_rgb(0_0_0/0.35)]">
                <div className="absolute inset-x-4 top-1/2 h-0.5 animate-pulse bg-signal" />
              </div>
            </div>
          )}
          <div className="absolute top-4 left-4 flex items-center gap-2 rounded-full bg-black/50 px-3 py-1.5 text-xs font-medium text-white backdrop-blur">
            <ScanLine className="size-3.5" /> Scanning
          </div>
        </div>

        <div className="space-y-4">
          <Card className={clsx('p-6 transition', result?.ok && 'border-forest/40 bg-forest-50', result && !result.ok && 'border-berry/40 bg-berry-50')}>
            {!result && <p className="text-muted">Point the camera at an attendee's ticket. The result shows here.</p>}
            {result?.ok && (
              <div className="animate-rise flex items-start gap-4">
                <CircleCheckBig className="size-10 shrink-0 text-forest" />
                <div>
                  <p className="font-display text-2xl font-bold">{result.checkIn.fullName}</p>
                  <p className="text-sm text-ink-2">Checked in at {fmt.time(result.checkIn.checkedInAt)}</p>
                </div>
              </div>
            )}
            {result && !result.ok && (
              <div className="animate-rise flex items-start gap-4">
                <CircleX className="size-10 shrink-0 text-berry" />
                <div>
                  <p className="font-display text-xl font-bold">Not admitted</p>
                  <p className="text-sm text-ink-2">{result.message}</p>
                </div>
              </div>
            )}
          </Card>

          <Card className="grid grid-cols-2 divide-x divide-line">
            <div className="p-5">
              <p className="text-sm text-muted">Checked in</p>
              <p className="font-display text-4xl font-bold">{summary.data?.attended ?? '·'}</p>
            </div>
            <div className="p-5">
              <p className="text-sm text-muted">Registered</p>
              <p className="font-display text-4xl font-bold">{summary.data?.registered ?? '·'}</p>
            </div>
          </Card>

          <Card className="p-5">
            <form onSubmit={submitManual} className="space-y-3">
              <Input label="Paste a ticket code" placeholder="CH1.club_…" value={manual} onChange={(e) => setManual(e.target.value)} className="font-mono text-xs" />
              <Button type="submit" variant="outline" className="w-full">Check in</Button>
            </form>
          </Card>
        </div>
      </div>
    </div>
  )
}
