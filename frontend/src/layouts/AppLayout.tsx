import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { Bell, Compass, House, LogOut, Menu, Plus, ShieldCheck, X } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { Logo, ThemeToggle } from '../components/brand'
import { Avatar, StatusBadge } from '../components/ui'
import { useMe, useMyClubs } from '../hooks/useAuth'
import { useLiveNotifications } from '../hooks/useLiveNotifications'
import { api, logout } from '../lib/api'

function NavItem({ to, icon, children, end, badge }: { to: string; icon: ReactNode; children: ReactNode; end?: boolean; badge?: number }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        clsx(
          'group flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition',
          isActive ? 'bg-ink text-paper' : 'text-ink-2 hover:bg-paper-2 hover:text-ink',
        )
      }
    >
      <span className="size-[18px] [&>svg]:size-[18px]">{icon}</span>
      <span className="flex-1">{children}</span>
      {!!badge && <span className="rounded-full bg-signal px-1.5 py-0.5 text-[11px] leading-none font-semibold text-white">{badge > 99 ? '99+' : badge}</span>}
    </NavLink>
  )
}

function Sidebar({ live, onNavigate }: { live: boolean; onNavigate?: () => void }) {
  const me = useMe()
  const clubs = useMyClubs()
  const navigate = useNavigate()
  const unread = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => api.get<{ unread: number }>('/api/notifications/unread-count'),
    refetchInterval: 60_000,
  })

  return (
    <div className="flex h-full flex-col" onClick={(e) => (e.target as HTMLElement).closest('a') && onNavigate?.()}>
      <div className="flex items-center justify-between px-2 pt-1 pb-6">
        <Logo />
        <span className="flex items-center gap-1.5 font-mono text-[10px] tracking-wider text-muted uppercase" title={live ? 'Live updates on' : 'Connecting…'}>
          <span className={clsx('size-1.5 rounded-full', live ? 'bg-forest shadow-[0_0_0_3px_rgb(31_111_74/0.15)]' : 'bg-muted/50')} />
          {live ? 'live' : '…'}
        </span>
      </div>

      <nav className="space-y-1">
        <NavItem to="/app" end icon={<House />}>Home</NavItem>
        <NavItem to="/app/explore" icon={<Compass />}>Explore clubs</NavItem>
        <NavItem to="/app/notifications" icon={<Bell />} badge={unread.data?.unread}>Notifications</NavItem>
        {me.data?.platformAdmin && <NavItem to="/app/platform" icon={<ShieldCheck />}>Platform</NavItem>}
      </nav>

      <div className="mt-8 mb-2 flex items-center justify-between px-3">
        <p className="font-mono text-[11px] tracking-[0.16em] text-muted uppercase">Your clubs</p>
        {me.data?.platformAdmin && (
          <button onClick={() => navigate('/app/platform')} className="rounded-md p-1 text-muted hover:bg-paper-2 hover:text-ink" aria-label="New club">
            <Plus className="size-4" />
          </button>
        )}
      </div>
      <div className="-mx-1 flex-1 space-y-0.5 overflow-y-auto px-1">
        {clubs.data?.length === 0 && <p className="px-3 py-2 text-[13px] text-muted">Join a club from Explore — selected applicants show up here.</p>}
        {clubs.data?.map((club) => (
          <NavLink
            key={club.slug}
            to={`/app/c/${club.slug}`}
            className={({ isActive }) =>
              clsx('flex items-center gap-3 rounded-xl px-2 py-1.5 transition', isActive ? 'bg-surface shadow-soft ring-1 ring-line' : 'hover:bg-paper-2')
            }
          >
            <Avatar name={club.name} size={30} square />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium">{club.name}</span>
              <span className="block text-[11px] text-muted">{club.role === 'CLUB_ADMIN' ? 'Admin' : club.role === 'CORE' ? 'Core team' : 'Member'}</span>
            </span>
            {club.plan === 'PRO' && <StatusBadge status="PRO" />}
          </NavLink>
        ))}
      </div>

      <div className="mt-4 flex items-center gap-3 rounded-2xl border border-line bg-surface p-2.5">
        <Avatar name={me.data?.fullName ?? '…'} size={34} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold">{me.data?.fullName ?? ' '}</p>
          <p className="truncate text-[11px] text-muted">{me.data?.email}</p>
        </div>
        <ThemeToggle />
        <button
          onClick={async () => {
            await logout()
            navigate('/')
          }}
          className="grid size-9 place-items-center rounded-xl text-ink-2 hover:bg-berry-50 hover:text-berry"
          aria-label="Log out"
        >
          <LogOut className="size-[18px]" />
        </button>
      </div>
    </div>
  )
}

export default function AppLayout() {
  const [open, setOpen] = useState(false)
  const live = useLiveNotifications() // one WebSocket per tab, owned by the layout

  return (
    <div className="min-h-screen bg-paper">
      <aside className="fixed inset-y-0 left-0 hidden w-72 border-r border-line bg-paper p-4 lg:block">
        <Sidebar live={live} />
      </aside>

      {/* mobile top bar + drawer */}
      <header className="sticky top-0 z-30 flex items-center justify-between border-b border-line bg-paper/85 px-4 py-3 backdrop-blur lg:hidden">
        <Logo />
        <button onClick={() => setOpen(true)} className="rounded-xl p-2 hover:bg-paper-2" aria-label="Open menu">
          <Menu className="size-5" />
        </button>
      </header>
      {open && (
        <div className="fixed inset-0 z-40 bg-ink/40 lg:hidden" onClick={() => setOpen(false)}>
          <div className="animate-rise h-full w-80 max-w-[85vw] bg-paper p-4" onClick={(e) => e.stopPropagation()}>
            <button onClick={() => setOpen(false)} className="absolute top-4 right-4 rounded-xl p-2 text-paper" aria-label="Close menu">
              <X className="size-5" />
            </button>
            <Sidebar live={live} onNavigate={() => setOpen(false)} />
          </div>
        </div>
      )}

      <main className="lg:pl-72">
        <div className="mx-auto max-w-6xl px-4 py-8 sm:px-8 lg:py-10">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
