import { useQueryClient } from '@tanstack/react-query'
import { Play } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { demoLogin } from '../lib/api'
import { useToast } from './toast'
import { Button } from './ui'

/**
 * One click into the demo club as its admin: no account, no password. The sandbox is shared and
 * rebuilds itself every hour, so visitors can change anything.
 */
export function DemoButton({ size = 'lg', variant = 'primary', label = 'Try the live demo', className }: {
  size?: 'sm' | 'md' | 'lg'
  variant?: 'primary' | 'outline'
  label?: string
  className?: string
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useToast()
  const [pending, setPending] = useState(false)

  async function start() {
    setPending(true)
    try {
      const slug = await demoLogin()
      queryClient.clear() // nothing from a previous session may leak into the demo
      navigate(slug ? `/app/c/${slug}` : '/app', { replace: true })
    } catch (err) {
      toast.error(err)
      setPending(false)
    }
  }

  return (
    <Button size={size} variant={variant} className={className} loading={pending} icon={<Play className="size-4" />} onClick={start}>
      {pending ? 'Setting up the demo…' : label}
    </Button>
  )
}
