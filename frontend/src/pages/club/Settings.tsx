import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Minus } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Button, Card, ErrorNote, Input, Meter, Skeleton, StatusBadge, Textarea } from '../../components/ui'
import { useToast } from '../../components/toast'
import { useClub } from '../../layouts/ClubLayout'
import { clubApi } from '../../lib/club'
import type { ClubProfile, FeatureName, LimitName, PlanView } from '../../lib/types'

const featureLabels: Record<FeatureName, string> = {
  CERTIFICATES: 'Participation certificates',
  EVENT_POSTERS: 'Event poster uploads',
  EMAIL_NOTIFICATIONS: 'Email notifications',
}
const limitLabels: Record<LimitName, string> = { MEMBERS: 'Members', UPCOMING_EVENTS: 'Upcoming events', OPEN_DRIVES: 'Open drives' }

type ProfileForm = { displayName: string; description: string; contactEmail: string }

export default function Settings() {
  const { club, can } = useClub()
  const api = clubApi(club.slug)
  const toast = useToast()
  const queryClient = useQueryClient()
  const profile = useQuery({ queryKey: ['club', club.slug, 'profile'], queryFn: () => api.get<ClubProfile>('/profile') })
  const plan = useQuery({ queryKey: ['club', club.slug, 'plan'], queryFn: () => api.get<PlanView>('/plan') })
  // Unedited, the form mirrors the server profile; the first keystroke forks a local draft.
  const [draft, setForm] = useState<ProfileForm | null>(null)
  const form: ProfileForm = draft ?? {
    displayName: profile.data?.displayName ?? '',
    description: profile.data?.description ?? '',
    contactEmail: profile.data?.contactEmail ?? '',
  }

  const save = useMutation({
    mutationFn: () => api.put('/profile', { ...form, description: form.description || null, contactEmail: form.contactEmail || null }),
    onSuccess: () => {
      toast.success('Profile saved')
      setForm(null)
      queryClient.invalidateQueries({ queryKey: ['club', club.slug, 'profile'] })
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    save.mutate()
  }

  const editable = can('CORE')
  return (
    <div className="grid gap-6 lg:grid-cols-[1.3fr_1fr]">
      <Card className="p-6">
        <h2 className="text-lg font-bold">Club profile</h2>
        <p className="mb-5 text-sm text-muted">Shown on your workspace overview.</p>
        {profile.isLoading ? (
          <Skeleton className="h-48" />
        ) : (
          <form onSubmit={submit} className="space-y-4">
            <Input label="Display name" required maxLength={120} disabled={!editable} value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
            <Textarea label="About the club" maxLength={2000} disabled={!editable} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            <Input label="Contact email" type="email" disabled={!editable} value={form.contactEmail} onChange={(e) => setForm({ ...form, contactEmail: e.target.value })} />
            <ErrorNote error={save.error} />
            {editable && <Button type="submit" loading={save.isPending}>Save changes</Button>}
          </form>
        )}
      </Card>

      <Card className="overflow-hidden">
        <div className="bg-ink p-6 text-paper">
          <p className="font-mono text-[11px] tracking-widest text-paper/50 uppercase">Current plan</p>
          <div className="mt-1 flex items-center gap-3">
            <p className="font-display text-4xl font-bold">{plan.data?.plan ?? '…'}</p>
            {plan.data && <StatusBadge status={plan.data.plan} />}
          </div>
          <p className="mt-1 text-sm text-paper/60">{plan.data?.requestsPerMinute.toLocaleString()} API requests per minute</p>
        </div>
        {plan.data && (
          <div className="space-y-5 p-6">
            {(Object.keys(limitLabels) as LimitName[]).map((key) => (
              <div key={key}>
                <div className="mb-1.5 flex justify-between text-sm">
                  <span>{limitLabels[key]}</span>
                  <span className="font-mono text-xs text-muted">{plan.data.usage[key]} / {plan.data.limits[key]}</span>
                </div>
                <Meter used={plan.data.usage[key]} max={plan.data.limits[key]} />
              </div>
            ))}
            <ul className="space-y-2 border-t border-line pt-5">
              {(Object.keys(featureLabels) as FeatureName[]).map((f) => (
                <li key={f} className="flex items-center gap-2.5 text-sm">
                  {plan.data.features[f] ? <Check className="size-4 text-forest" /> : <Minus className="size-4 text-muted" />}
                  <span className={plan.data.features[f] ? '' : 'text-muted'}>{featureLabels[f]}</span>
                </li>
              ))}
            </ul>
            {plan.data.plan === 'FREE' && <p className="rounded-xl bg-signal-50 p-3 text-[13px] text-signal-600">Need more? Ask the ClubHub team to move your club to PRO.</p>}
          </div>
        )}
      </Card>
    </div>
  )
}
