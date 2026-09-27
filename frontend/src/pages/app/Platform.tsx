import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2, Plus, ShieldAlert } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Avatar, Badge, Button, Card, EmptyState, ErrorNote, Input, Modal, PageHeader, Skeleton } from '../../components/ui'
import { useToast } from '../../components/toast'
import { useMe } from '../../hooks/useAuth'
import { api } from '../../lib/api'
import { fmt } from '../../lib/format'
import type { PlanName, Tenant } from '../../lib/types'

export default function Platform() {
  const me = useMe()
  const queryClient = useQueryClient()
  const toast = useToast()
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState({ name: '', slug: '', ownerEmail: '' })
  const tenants = useQuery({ queryKey: ['tenants'], queryFn: () => api.get<Tenant[]>('/api/platform/tenants'), enabled: !!me.data?.platformAdmin })

  const create = useMutation({
    mutationFn: () => api.post<Tenant>('/api/platform/tenants', { ...form, ownerEmail: form.ownerEmail || null }),
    onSuccess: (t) => {
      toast.success(`${t.name} is live`, `Its own schema club_${t.slug} was created and migrated.`)
      setCreating(false)
      setForm({ name: '', slug: '', ownerEmail: '' })
      queryClient.invalidateQueries({ queryKey: ['tenants'] })
      queryClient.invalidateQueries({ queryKey: ['my-clubs'] })
      queryClient.invalidateQueries({ queryKey: ['directory'] })
    },
  })
  const changePlan = useMutation({
    mutationFn: ({ id, plan }: { id: string; plan: PlanName }) => api.patch(`/api/platform/tenants/${id}/plan`, { plan }),
    onSuccess: () => {
      toast.success('Plan updated')
      queryClient.invalidateQueries({ queryKey: ['tenants'] })
    },
    onError: toast.error,
  })

  if (me.data && !me.data.platformAdmin) {
    return <EmptyState icon={<ShieldAlert className="size-5" />} title="Platform admins only">This area manages every club on ClubHub.</EmptyState>
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    create.mutate()
  }

  return (
    <div>
      <PageHeader
        eyebrow="Platform"
        title="Clubs on ClubHub"
        description="Every club is a tenant with its own PostgreSQL schema. Create clubs and manage their plans."
        actions={<Button icon={<Plus className="size-4" />} onClick={() => setCreating(true)}>New club</Button>}
      />

      {tenants.isLoading && <Skeleton className="h-64" />}
      {tenants.data?.length === 0 && <EmptyState icon={<Building2 className="size-5" />} title="No clubs yet" action={<Button onClick={() => setCreating(true)}>Create the first club</Button>} />}
      {!!tenants.data?.length && (
        <Card className="overflow-hidden">
          <table className="w-full text-sm">
            <thead className="border-b border-line bg-paper-2/60 text-left text-[12px] tracking-wide text-muted uppercase">
              <tr>
                <th className="px-5 py-3 font-medium">Club</th>
                <th className="hidden px-5 py-3 font-medium sm:table-cell">Schema</th>
                <th className="px-5 py-3 font-medium">Status</th>
                <th className="px-5 py-3 font-medium">Plan</th>
                <th className="hidden px-5 py-3 font-medium md:table-cell">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {tenants.data.map((t) => (
                <tr key={t.id} className="hover:bg-paper-2/40">
                  <td className="px-5 py-3.5">
                    <div className="flex items-center gap-3">
                      <Avatar name={t.name} size={32} square />
                      <span className="font-medium">{t.name}</span>
                    </div>
                  </td>
                  <td className="hidden px-5 py-3.5 font-mono text-xs text-muted sm:table-cell">club_{t.slug}</td>
                  <td className="px-5 py-3.5">
                    <Badge tone={t.status === 'ACTIVE' ? 'forest' : 'berry'} dot>{fmt.label(t.status)}</Badge>
                  </td>
                  <td className="px-5 py-3.5">
                    <select
                      value={t.plan}
                      onChange={(e) => changePlan.mutate({ id: t.id, plan: e.target.value as PlanName })}
                      className="rounded-lg border border-line bg-surface px-2 py-1 text-sm"
                      aria-label={`Plan for ${t.name}`}
                    >
                      <option value="FREE">Free</option>
                      <option value="PRO">Pro</option>
                    </select>
                  </td>
                  <td className="hidden px-5 py-3.5 text-muted md:table-cell">{fmt.date(t.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      <Modal open={creating} onClose={() => setCreating(false)} title="Create a club" description="Provisions a schema, runs its migrations and makes the owner its first admin.">
        <form onSubmit={submit} className="space-y-4">
          <Input
            label="Club name"
            required
            value={form.name}
            onChange={(e) => {
              const name = e.target.value
              const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '').replace(/^(\d)/, 'c_$1').slice(0, 40)
              setForm({ ...form, name, slug })
            }}
          />
          <Input label="Slug" required pattern="^[a-z][a-z0-9_]{2,39}$" hint="Lowercase letters, digits and _. Used in URLs and the schema name." value={form.slug} onChange={(e) => setForm({ ...form, slug: e.target.value })} />
          <Input label="Owner email (optional)" type="email" hint="Must already have an account. Defaults to you." value={form.ownerEmail} onChange={(e) => setForm({ ...form, ownerEmail: e.target.value })} />
          <ErrorNote error={create.error} />
          <Button type="submit" className="w-full" loading={create.isPending}>Create club</Button>
        </form>
      </Modal>
    </div>
  )
}
