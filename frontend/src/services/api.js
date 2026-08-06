import { useAuthStore } from '../stores/authStore'

/**
 * fetch() con token JWT adjunto y manejo centralizado de 401.
 * Si el token es inválido o expiró, cierra la sesión automáticamente.
 */
export async function authFetch(url, options = {}) {
  const auth = useAuthStore()
  const headers = new Headers(options.headers ?? {})
  if (auth.token) headers.set('Authorization', `Bearer ${auth.token}`)

  const res = await fetch(url, { ...options, headers })

  if (res.status === 401) {
    auth.logout()
  }
  return res
}
