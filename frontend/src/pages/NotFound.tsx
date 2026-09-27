import { Logo } from '../components/brand'
import { ButtonLink } from '../components/ui'

export default function NotFound() {
  return (
    <div className="bg-dots grid min-h-screen place-items-center bg-paper px-5 text-center">
      <div className="animate-rise">
        <Logo className="mb-10" />
        <p className="font-display text-[8rem] leading-none font-extrabold text-signal">404</p>
        <h1 className="mt-4 text-2xl font-bold">This page skipped the meeting.</h1>
        <p className="mt-2 text-muted">The link may be old, or the page never existed.</p>
        <ButtonLink to="/" variant="ink" className="mt-8">Back to ClubHub</ButtonLink>
      </div>
    </div>
  )
}
