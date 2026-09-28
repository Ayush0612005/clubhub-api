import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { Bell, CalendarDays, Compass, House, Inbox, LogOut, Megaphone, Menu, Plus, ShieldCheck, X } from 'lucide-react'
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
          'group flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition',
          isActive ? 'bg-signal-50 text-ink ring-1 ring-signal/25 ring-inset [&_svg]:text-signal-ink' : 'text-muted hover:bg-paper-2 hover:text-ink',
        )
      }
    >
      <span className="size-[18px] [&>svg]:size-[18px]">{icon}</span>
      <span className="flex-1">{children}</span>
      {!!badge && <span className="rounded-md bg-signal px-1.5 py-0.5 font-mono text-[11px] leading-none font-semibold text-on-signal">{badge > 99 ? '99+' : badge}</span>}
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
          <span className={clsx('size-1.5 rounded-full', live ? 'bg-forest shadow-[0_0_0_3px_rgb(52_211_153/0.18)]' : 'bg-muted/50')} />
          {live ? 'live' : '…'}
        </span>
      </div>

      <nav className="space-y-1">
        <NavItem to="/app" end icon={<House />}>Home</NavItem>
        <NavItem to="/app/events" icon={<CalendarDays />}>What's on</NavItem>
        <NavItem to="/app/clubs" icon={<Compass />}>Clubs</NavItem>
        <NavItem to="/app/recruiting" icon={<Megaphone />}>Recruiting</NavItem>
        <NavItem to="/app/notifications" icon={<Bell />} badge={unread.data?.unread}>Notifications</NavItem>
        {me.data?.platformAdmin && (
          <>
            <p className="px-3 pt-5 pb-1 font-mono text-[11px] tracking-[0.16em] text-muted uppercase">Admin</p>
            <NavItem to="/app/moderation" icon={<Inbox />}>Moderation</NavItem>
            <NavItem to="/app/platform" icon={<ShieldCheck />}>Workspaces</NavItem>
          </>
        )}
      </nav>

      {/* ClubHub workspaces the student belongs to: most students have none, so the section only appears when there is one */}
      <div className="-mx-1 mt-8 flex-1 space-y-0.5 overflow-y-auto px-1">
        {(!!clubs.data?.length || me.data?.platformAdmin) && (
          <div className="mb-2 flex items-center justify-between px-4">
            <p className="font-mono text-[11px] tracking-[0.16em] text-muted uppercase">Your workspaces</p>
            {me.data?.platformAdmin && (
              <button onClick={() => navigate('/app/platform')} className="rounded-md p-1 text-muted hover:bg-paper-2 hover:text-ink" aria-label="New workspace">
                <Plus className="size-4" />
              </button>
            )}
          </div>
        )}
        {clubs.data?.map((club) => (
          <NavLink
            key={club.slug}
            to={`/app/c/${club.slug}`}
            className={({ isActive }) =>
              clsx('flex items-center gap-3 rounded-md px-2 py-1.5 transition', isActive ? 'bg-paper-2 ring-1 ring-line' : 'hover:bg-paper-2')
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

      <div className="mt-4 flex items-center gap-3 rounded-lg border border-line bg-surface p-2.5">
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
      {/* z-20: <main> is positioned and comes later in the DOM, so without this it paints over the sidebar and eats its clicks */}
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-72 border-r border-line bg-paper p-4 lg:block">
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
        <div className="fixed inset-0 z-40 bg-black/60 lg:hidden" onClick={() => setOpen(false)}>
          <div className="animate-rise h-full w-80 max-w-[85vw] bg-paper p-4" onClick={(e) => e.stopPropagation()}>
            <button onClick={() => setOpen(false)} className="absolute top-4 right-4 rounded-xl p-2 text-paper" aria-label="Close menu">
              <X className="size-5" />
            </button>
            <Sidebar live={live} onNavigate={() => setOpen(false)} />
          </div>
        </div>
      )}

      <main className="relative lg:pl-72">
        <div className="glow pointer-events-none absolute inset-x-0 top-0 h-80 opacity-60" />
        <div className="relative mx-auto max-w-6xl px-4 py-8 sm:px-8 lg:py-10">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
