import { useMutation, useQuery } from '@tanstack/react-query'
import { ArrowLeft, Send } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { Button, Card, ErrorNote, Skeleton, Textarea } from '../../components/ui'
import { useToast } from '../../components/toast'
import { publicClubApi } from '../../lib/club'
import { fmt } from '../../lib/format'
import type { Drive } from '../../lib/types'

export default function ApplyDrive() {
  const { slug = '', driveId = '' } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const [answers, setAnswers] = useState<Record<number, string>>({})
  const drive = useQuery({ queryKey: ['public-drive', slug, driveId], queryFn: () => publicClubApi(slug).get<Drive>(`/recruitment/drives/${driveId}`) })

  const apply = useMutation({
    mutationFn: () =>
      publicClubApi(slug).post(`/recruitment/drives/${driveId}/applications`, {
        answers: Object.entries(answers)
          .filter(([, value]) => value.trim())
          .map(([questionId, answer]) => ({ questionId: Number(questionId), answer })),
      }),
    onSuccess: () => {
      toast.success('Application sent', 'We’ll notify you as it moves through the pipeline.')
      navigate(`/app/clubs/${slug}/recruitment/applications/mine`)
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    apply.mutate()
  }

  if (drive.isLoading) return <Skeleton className="h-96" />
  if (!drive.data) return <ErrorNote error={drive.error} />

  return (
    <div className="mx-auto max-w-2xl">
      <Link to={`/app/clubs/${slug}/recruitment`} className="mb-6 inline-flex items-center gap-1.5 text-sm text-muted hover:text-ink">
        <ArrowLeft className="size-4" /> Back to drives
      </Link>
      <p className="font-mono text-xs tracking-widest text-signal uppercase">Application</p>
      <h1 className="mt-2 text-4xl font-bold">{drive.data.title}</h1>
      {drive.data.description && <p className="mt-3 leading-relaxed whitespace-pre-line text-ink-2">{drive.data.description}</p>}
      {drive.data.closesAt && <p className="mt-2 text-sm text-muted">Closes {fmt.dateTime(drive.data.closesAt)}</p>}

      <Card className="mt-8 p-6 sm:p-8">
        <form onSubmit={submit} className="space-y-6">
          {drive.data.questions.map((q, i) => (
            <Textarea
              key={q.id}
              label={`${i + 1}. ${q.prompt}${q.required ? '' : ' (optional)'}`}
              required={q.required}
              maxLength={5000}
              value={answers[q.id] ?? ''}
              onChange={(e) => setAnswers({ ...answers, [q.id]: e.target.value })}
            />
          ))}
          <ErrorNote error={apply.error} />
          <Button type="submit" size="lg" loading={apply.isPending} icon={<Send className="size-4" />}>
            Submit application
          </Button>
        </form>
      </Card>
    </div>
  )
}
