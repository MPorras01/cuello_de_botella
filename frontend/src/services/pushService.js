/**
 * Módulo de gestión de suscripciones Web Push VAPID.
 * Requisitos: 8.2, 8.3, 8.4
 */
import { authFetch } from './api'

const VAPID_PUBLIC_KEY = import.meta.env.VITE_VAPID_PUBLIC_KEY

/**
 * Convierte una clave VAPID en base64url a Uint8Array.
 */
function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = window.atob(base64)
  return Uint8Array.from([...rawData].map((c) => c.charCodeAt(0)))
}

/**
 * Solicita permiso al navegador y registra la suscripción VAPID en el servidor.
 * @returns {Promise<void>}
 */
export async function subscribe() {
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    throw new Error('PERMISSION_DENIED')
  }

  const registration = await navigator.serviceWorker.ready
  const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(VAPID_PUBLIC_KEY)
  })

  const { endpoint, keys } = subscription.toJSON()
  const res = await authFetch('/api/push/subscribe', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ endpoint, p256dh: keys.p256dh, auth: keys.auth })
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

/**
 * Cancela la suscripción Web Push en el servidor y en el navegador.
 * @returns {Promise<void>}
 */
export async function unsubscribe() {
  const registration = await navigator.serviceWorker.ready
  const subscription = await registration.pushManager.getSubscription()
  if (!subscription) return

  const res = await authFetch(
    `/api/push/unsubscribe?endpoint=${encodeURIComponent(subscription.endpoint)}`,
    { method: 'DELETE' }
  )
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  await subscription.unsubscribe()
}
