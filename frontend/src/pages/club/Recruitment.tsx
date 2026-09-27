import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronRight, GripVertical, Megaphone, Plus, Trash2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Button, EmptyState, ErrorNote, Input, Modal, Skeleton, StatusBadge, Textarea } from '../../components/ui'
import { useToast } from '../../components/toast'
import { useClub } from '../../layouts/ClubLayout'
import { clubApi } from '../../lib/club'
import { fmt, localInput } from '../../lib/format'
import type { Drive, DriveSummary } from '../../lib/types'

interface QuestionDraft {
  prompt: string
  required: boolean
}

const starterQuestions: QuestionDraft[] = [
  { prompt: 'Why do you want to join us?', required: true },
  { prompt: 'Share something you have built or done (a link is fine).', required: false },
]

export default function Recruitment() {
  const { club, can } = useClub()
  const api = clubApi(club.slug)
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [params, setParams] = useSearchParams()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [closesAt, setClosesAt] = useState('')
  const [questions, setQuestions] = useState<QuestionDraft[]>(starterQuestions)

  const drives = useQuery({ queryKey: ['club', club.slug, 'drives'], queryFn: () => api.get<DriveSummary[]>('/recruitment/drives') })
  const create = useMutation({
    mutationFn: () =>
      api.post<Drive>('/recruitment/drives', {
        title,
        description: description || null,
        closesAt: closesAt ? localInput.toIso(closesAt) : null,
        questions: questions.filter((q) => q.prompt.trim()),
      }),
    onSuccess: (drive) => {
      toast.success('Drive created as a draft', 'Open it when you’re ready to accept applications.')
      queryClient.invalidateQueries({ queryKey: ['club', club.slug] })
      navigate(`${drive.id}`)
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    create.mutate()
  }

  const update = (i: number, patch: Partial<QuestionDraft>) => setQuestions(questions.map((q, j) => (j === i ? { ...q, ...patch } : q)))

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-xl font-bold">Recruitment drives</h2>
        {can('CORE') && <Button icon={<Plus className="size-4" />} onClick={() => setParams({ new: '1' })}>New drive</Button>}
      </div>

      {drives.isLoading && <Skeleton className="h-40" />}
      {drives.data?.length === 0 && (
        <EmptyState icon={<Megaphone className="size-5" />} title="No drives yet" action={can('CORE') && <Button onClick={() => setParams({ new: '1' })}>Start recruiting</Button>}>
          A drive is a recruitment round: your questions, an application form, and a pipeline to review applicants.
        </EmptyState>
      )}
      <div className="grid gap-3 md:grid-cols-2">
        {drives.data?.map((d) => (
          <Link key={d.id} to={`${d.id}`} className="group flex items-center gap-4 rounded-[var(--radius-card)] border border-line bg-surface p-5 shadow-soft transition hover:-translate-y-0.5 hover:shadow-lift">
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <p className="truncate font-display text-lg font-bold">{d.title}</p>
                <StatusBadge status={d.status} />
              </div>
              <p className="mt-1 text-[13px] text-muted">
                Created {fmt.date(d.createdAt)}{d.closesAt && ` · closes ${fmt.dateTime(d.closesAt)}`}
              </p>
            </div>
            <ChevronRight className="size-5 text-muted transition group-hover:translate-x-0.5 group-hover:text-ink" />
          </Link>
        ))}
      </div>

      <Modal open={params.get('new') === '1'} onClose={() => setParams({})} title="New recruitment drive" description="Students see the title, description and your questions." wide>
        <form onSubmit={submit} className="space-y-5">
          <Input label="Title" required maxLength={150} placeholder="Tech team — odd semester 2026" value={title} onChange={(e) => setTitle(e.target.value)} />
          <Textarea label="Description" maxLength={5000} placeholder="What the role involves, time commitment, perks…" value={description} onChange={(e) => setDescription(e.target.value)} />
          <Input label="Closes (optional)" type="datetime-local" value={closesAt} onChange={(e) => setClosesAt(e.target.value)} />

          <div>
            <p className="mb-2 text-[13px] font-medium text-ink-2">Questions</p>
            <div className="space-y-2">
              {questions.map((q, i) => (
                <div key={i} className="flex items-center gap-2 rounded-xl border border-line bg-paper-2/40 p-2">
                  <GripVertical className="size-4 shrink-0 text-muted" />
                  <input
                    value={q.prompt}
                    onChange={(e) => update(i, { prompt: e.target.value })}
                    placeholder={`Question ${i + 1}`}
                    maxLength={500}
                    className="h-9 min-w-0 flex-1 rounded-lg border border-transparent bg-surface px-3 text-sm focus:border-line focus:outline-none"
                  />
                  <label className="flex items-center gap-1.5 px-1 text-xs text-muted">
                    <input type="checkbox" checked={q.required} onChange={(e) => update(i, { required: e.target.checked })} className="accent-signal" />
                    Required
                  </label>
                  <button type="button" onClick={() => setQuestions(questions.filter((_, j) => j !== i))} className="rounded-lg p-2 text-muted hover:bg-berry-50 hover:text-berry" aria-label="Remove question">
                    <Trash2 className="size-4" />
                  </button>
                </div>
              ))}
            </div>
            {questions.length < 20 && (
              <Button type="button" variant="ghost" size="sm" className="mt-2" icon={<Plus className="size-4" />} onClick={() => setQuestions([...questions, { prompt: '', required: false }])}>
                Add question
              </Button>
            )}
          </div>

          <ErrorNote error={create.error} />
          <Button type="submit" className="w-full" loading={create.isPending} disabled={!questions.some((q) => q.prompt.trim())}>
            Create drive
          </Button>
        </form>
      </Modal>
    </div>
  )
}
