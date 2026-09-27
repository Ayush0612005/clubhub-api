import clsx from 'clsx'

/** One of the three corner "eyes" of a QR code, in brand colours. */
function Finder({ x, y }: { x: number; y: number }) {
  return (
    <g>
      <rect x={x} y={y} width="7" height="7" rx="1.6" className="fill-ink" />
      <rect x={x + 1} y={y + 1} width="5" height="5" rx="1.1" className="fill-surface" />
      <rect x={x + 2} y={y + 2} width="3" height="3" rx="0.8" className="fill-signal" />
    </g>
  )
}

/** A decorative QR-like grid (deterministic from the seed). Real tickets use the server's PNG. */
export function QrArt({ seed = 'clubhub', size = 120, className }: { seed?: string; size?: number; className?: string }) {
  const n = 21
  let h = 2166136261
  for (let i = 0; i < seed.length; i++) h = Math.imul(h ^ seed.charCodeAt(i), 16777619)
  const rand = () => {
    h ^= h << 13
    h ^= h >>> 17
    h ^= h << 5
    return ((h >>> 0) % 1000) / 1000
  }
  const finder = (x: number, y: number) => (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)
  const cells: { x: number; y: number }[] = []
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) if (!finder(x, y) && rand() > 0.52) cells.push({ x, y })

  return (
    <svg viewBox={`0 0 ${n} ${n}`} width={size} height={size} className={clsx('shrink-0', className)} aria-hidden>
      {cells.map((c) => (
        <rect key={`${c.x}-${c.y}`} x={c.x + 0.08} y={c.y + 0.08} width="0.84" height="0.84" rx="0.22" className="fill-ink" />
      ))}
      <Finder x={0} y={0} />
      <Finder x={n - 7} y={0} />
      <Finder x={0} y={n - 7} />
    </svg>
  )
}

/** Calendar-style date tile used for events. */
export function DateTile({ iso, className }: { iso: string; className?: string }) {
  const d = new Date(iso)
  return (
    <div className={clsx('w-14 shrink-0 overflow-hidden rounded-2xl border border-line bg-surface text-center shadow-soft', className)}>
      <div className="bg-signal py-0.5 font-mono text-[10px] font-medium tracking-widest text-white">
        {d.toLocaleString('en-IN', { month: 'short' }).toUpperCase()}
      </div>
      <div className="font-display text-2xl leading-9 font-bold">{d.getDate()}</div>
    </div>
  )
}
