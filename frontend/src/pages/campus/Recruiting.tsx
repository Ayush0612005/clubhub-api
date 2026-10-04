import { useQuery } from '@tanstack/react-query'
import { Megaphone, Plus } from 'lucide-react'
import { useState } from 'react'
import { RecruitmentCard, RecruitmentFormModal } from '../../components/campus'
import { Button, EmptyState, PageHeader, Skeleton } from '../../components/ui'
import { useSignedInAction } from '../../hooks/useAuth'
import { campus } from '../../lib/campus'

/** Every club currently taking applications, closing soonest first. */
export default function Recruiting() {
  const recruitments = useQuery({ queryKey: ['campus', 'recruitments'], queryFn: campus.recruitments })
  const [suggesting, setSuggesting] = useState(false)
  const withAccount = useSignedInAction()

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader
        eyebrow="Join a club"
        title="Recruiting now"
        description="Clubs taking applications, closing soonest first. You apply on the club's own form."
        actions={<Button variant="outline" icon={<Plus className="size-4" />} onClick={withAccount(() => setSuggesting(true))}>Tell us about one</Button>}
      />
      {recruitments.isLoading && <div className="grid gap-3 sm:grid-cols-2"><Skeleton className="h-36" /><Skeleton className="h-36" /></div>}
      {recruitments.data?.length === 0 && (
        <EmptyState icon={<Megaphone className="size-5" />} title="No open recruitments listed" action={<Button onClick={withAccount(() => setSuggesting(true))}>Tell us about one</Button>}>
          Clubs usually recruit at the start of each semester. Seen an application form? Share it here.
        </EmptyState>
      )}
      <div className="grid gap-3 sm:grid-cols-2">
        {recruitments.data?.map((r) => <RecruitmentCard key={r.id} recruitment={r} />)}
      </div>
      <RecruitmentFormModal open={suggesting} onClose={() => setSuggesting(false)} mode="suggest" />
    </div>
  )
}
