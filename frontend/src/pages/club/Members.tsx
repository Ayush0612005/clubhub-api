import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Search, UserMinus, UserPlus } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Avatar, Button, Card, ErrorNote, Input, Modal, Select, Skeleton, StatusBadge } from '../../components/ui'
import { useToast } from '../../components/toast'
import { useMe } from '../../hooks/useAuth'
import { useClub } from '../../layouts/ClubLayout'
import { clubApi } from '../../lib/club'
import { fmt } from '../../lib/format'
import type { ClubRole, Member } from '../../lib/types'

const roles: ClubRole[] = ['CLUB_ADMIN', 'CORE', 'MEMBER']

export default function Members() {
  const { club, can } = useClub()
  const api = clubApi(club.slug)
  const me = useMe()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState('')
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState({ email: '', role: 'MEMBER' as ClubRole })
  const admin = can('CLUB_ADMIN')
  const key = ['club', club.slug, 'members']

  const members = useQuery({ queryKey: key, queryFn: () => api.get<Member[]>('/members') })
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: key })
    queryClient.invalidateQueries({ queryKey: ['club', club.slug, 'plan'] })
  }
  const add = useMutation({
    mutationFn: () => api.post('/members', form),
    onSuccess: () => {
      toast.success('Member added')
      setAdding(false)
      setForm({ email: '', role: 'MEMBER' })
      invalidate()
    },
  })
  const changeRole = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: ClubRole }) => api.patch(`/members/${userId}`, { role }),
    onSuccess: () => {
      toast.success('Role updated')
      invalidate()
    },
    onError: toast.error,
  })
  const remove = useMutation({
    mutationFn: (userId: string) => api.del(`/members/${userId}`),
    onSuccess: () => {
      toast.success('Member removed')
      invalidate()
    },
    onError: toast.error,
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    add.mutate()
  }

  const shown = members.data?.filter((m) => `${m.fullName} ${m.email}`.toLowerCase().includes(filter.toLowerCase()))
  const counts = roles.map((r) => [r, members.data?.filter((m) => m.role === r).length ?? 0] as const)

  return (
    <div>
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-wrap gap-2">
          {counts.map(([role, count]) => (
            <span key={role} className="rounded-full bg-surface px-3 py-1 text-sm ring-1 ring-line">
              <b className="font-display">{count}</b> <span className="text-muted">{fmt.label(role).toLowerCase()}{count === 1 ? '' : 's'}</span>
            </span>
          ))}
        </div>
        <div className="flex gap-2">
          <div className="relative">
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted" />
            <input value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="Search" className="h-10 w-48 rounded-xl border border-line bg-surface pr-3 pl-9 text-sm focus:outline-none focus:ring-4 focus:ring-signal/10" />
          </div>
          {admin && <Button icon={<UserPlus className="size-4" />} onClick={() => setAdding(true)}>Add</Button>}
        </div>
      </div>

      {members.isLoading && <Skeleton className="h-64" />}
      {shown && (
        <Card className="divide-y divide-line overflow-hidden">
          {shown.map((m) => {
            const isMe = m.userId === me.data?.id
            return (
              <div key={m.userId} className="flex items-center gap-4 px-5 py-3.5">
                <Avatar name={m.fullName} size={38} />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">
                    {m.fullName} {isMe && <span className="text-xs font-normal text-muted">(you)</span>}
                  </p>
                  <p className="truncate text-[13px] text-muted">{m.email}</p>
                </div>
                <span className="hidden text-xs text-muted sm:block">Joined {fmt.date(m.joinedAt)}</span>
                {admin && !isMe ? (
                  <>
                    <select
                      value={m.role}
                      onChange={(e) => changeRole.mutate({ userId: m.userId, role: e.target.value as ClubRole })}
                      className="rounded-lg border border-line bg-surface px-2 py-1.5 text-sm"
                      aria-label={`Role of ${m.fullName}`}
                    >
                      {roles.map((r) => <option key={r} value={r}>{fmt.label(r)}</option>)}
                    </select>
                    <button
                      onClick={() => confirm(`Remove ${m.fullName} from ${club.name}?`) && remove.mutate(m.userId)}
                      className="rounded-lg p-2 text-muted hover:bg-berry-50 hover:text-berry"
                      aria-label={`Remove ${m.fullName}`}
                    >
                      <UserMinus className="size-4" />
                    </button>
                  </>
                ) : (
                  <StatusBadge status={m.role} />
                )}
              </div>
            )
          })}
        </Card>
      )}

      <Modal open={adding} onClose={() => setAdding(false)} title="Add a member" description="They need a ClubHub account first. Members also join automatically when selected in recruitment.">
        <form onSubmit={submit} className="space-y-4">
          <Input label="Email" type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          <Select label="Role" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as ClubRole })}>
            <option value="MEMBER">Member — sees club events and drives</option>
            <option value="CORE">Core — runs events and recruitment</option>
            <option value="CLUB_ADMIN">Admin — manages members and settings</option>
          </Select>
          <ErrorNote error={add.error} />
          <Button type="submit" className="w-full" loading={add.isPending}>Add member</Button>
        </form>
      </Modal>
    </div>
  )
}
