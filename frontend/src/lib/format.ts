const dateTime = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })
const date = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
const time = new Intl.DateTimeFormat('en-IN', { hour: 'numeric', minute: '2-digit' })
const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

export const fmt = {
  dateTime: (iso: string) => dateTime.format(new Date(iso)),
  date: (iso: string) => date.format(new Date(iso)),
  time: (iso: string) => time.format(new Date(iso)),
  day: (iso: string) => new Date(iso).getDate().toString(),
  month: (iso: string) => new Date(iso).toLocaleString('en-IN', { month: 'short' }).toUpperCase(),
  weekday: (iso: string) => new Date(iso).toLocaleString('en-IN', { weekday: 'short' }),

  ago(iso: string) {
    const seconds = (new Date(iso).getTime() - Date.now()) / 1000
    const units: [Intl.RelativeTimeFormatUnit, number][] = [
      ['day', 86400],
      ['hour', 3600],
      ['minute', 60],
    ]
    for (const [unit, size] of units) {
      if (Math.abs(seconds) >= size) return relative.format(Math.round(seconds / size), unit)
    }
    return 'just now'
  },

  /** "SHORTLISTED" → "Shortlisted", "CLUB_ADMIN" → "Club admin" */
  label: (value: string) => value.charAt(0) + value.slice(1).toLowerCase().replaceAll('_', ' '),

  initials(name: string) {
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase())
      .join('')
  },
}

/** Converts a <input type="datetime-local"> value to an ISO instant (and back). */
export const localInput = {
  toIso: (value: string) => new Date(value).toISOString(),
  fromDate: (d: Date) => {
    const pad = (n: number) => n.toString().padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
  },
}

/** Stable colour for a club/person from its name, so avatars are recognisable. */
const palette = ['#ff5a1f', '#2f54eb', '#1f6f4a', '#c8264f', '#b7791f', '#6d3fd1', '#0f7c8c', '#16181d']
export function colorFor(seed: string) {
  let hash = 0
  for (let i = 0; i < seed.length; i++) hash = (hash * 31 + seed.charCodeAt(i)) | 0
  return palette[Math.abs(hash) % palette.length]
}
