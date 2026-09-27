import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { BadgeCheck, ShieldX } from 'lucide-react'
import { useParams } from 'react-router'
import { Logo } from '../components/brand'
import { Spinner } from '../components/ui'
import { api } from '../lib/api'
import { fmt } from '../lib/format'
import type { Verification } from '../lib/types'

/** Public page behind the QR code printed on every certificate. No login. */
export default function Verify() {
  const { slug = '', id = '' } = useParams()
  const result = useQuery({
    queryKey: ['verify', slug, id],
    queryFn: () => api.publicGet<Verification>(`/api/verify/certificates/${slug}/${id}`),
    retry: false,
  })

  return (
    <div className="bg-dots grid min-h-screen place-items-center bg-paper px-5 py-16">
      <div className="w-full max-w-lg">
        <div className="mb-8 flex justify-center"><Logo /></div>
        {result.isLoading && <div className="flex justify-center"><Spinner /></div>}
        {result.isError && (
          <div className="rounded-3xl border border-line bg-surface p-8 text-center shadow-lift">
            <ShieldX className="mx-auto size-12 text-berry" />
            <h1 className="mt-4 text-2xl font-bold">No such certificate</h1>
            <p className="mt-2 text-muted">This ID wasn't issued by a ClubHub club. Check the link or QR code.</p>
          </div>
        )}
        {result.data && (
          <div className="animate-rise overflow-hidden rounded-3xl border border-line bg-surface shadow-lift">
            <div className={clsx('flex items-center gap-3 px-8 py-5 text-paper', result.data.valid ? 'bg-forest' : 'bg-berry')}>
              {result.data.valid ? <BadgeCheck className="size-7" /> : <ShieldX className="size-7" />}
              <div>
                <p className="font-display text-lg font-bold">{result.data.valid ? 'Verified certificate' : 'Certificate revoked'}</p>
                <p className="text-sm text-paper/80">
                  {result.data.valid ? `Issued by ${result.data.clubName}` : `Revoked on ${fmt.date(result.data.revokedAt!)}`}
                </p>
              </div>
            </div>
            <div className="p-8">
              <p className="font-mono text-[11px] tracking-widest text-muted uppercase">{result.data.title}</p>
              <p className="mt-3 font-display text-3xl font-bold">{result.data.recipientName}</p>
              <p className="mt-2 text-ink-2">{result.data.description}</p>
              <dl className="mt-8 grid grid-cols-2 gap-4 border-t border-line pt-6 text-sm">
                <div>
                  <dt className="text-muted">Club</dt>
                  <dd className="font-medium">{result.data.clubName}</dd>
                </div>
                <div>
                  <dt className="text-muted">Issued</dt>
                  <dd className="font-medium">{fmt.date(result.data.issuedAt)}</dd>
                </div>
                <div className="col-span-2">
                  <dt className="text-muted">Certificate ID</dt>
                  <dd className="font-mono text-xs break-all">{result.data.id}</dd>
                </div>
              </dl>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
