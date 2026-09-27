import { Client } from '@stomp/stompjs'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useToast } from '../components/toast'
import { session } from '../lib/session'
import type { Notification } from '../lib/types'
import { useSession } from './useAuth'

/**
 * Opens one STOMP connection per signed-in tab and listens on /user/queue/notifications.
 * The JWT goes in the STOMP CONNECT headers (browsers can't set headers on the WS handshake);
 * beforeConnect re-reads it so reconnects after a token refresh use the fresh token.
 */
export function useLiveNotifications() {
  const signedIn = !!useSession()
  const queryClient = useQueryClient()
  const toast = useToast()
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (!signedIn) return
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      brokerURL: `${protocol}://${window.location.host}/ws`,
      reconnectDelay: 5000,
      beforeConnect: (c) => {
        c.connectHeaders = { Authorization: `Bearer ${session.get()?.accessToken ?? ''}` }
      },
      onConnect: () => {
        setConnected(true)
        client.subscribe('/user/queue/notifications', (frame) => {
          const n = JSON.parse(frame.body) as Notification
          toast.info(n.title, n.body)
          queryClient.invalidateQueries({ queryKey: ['notifications'] })
        })
      },
      onWebSocketClose: () => setConnected(false),
    })
    client.activate()
    return () => {
      void client.deactivate()
      setConnected(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signedIn])

  return connected
}
