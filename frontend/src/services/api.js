import { useAuthStore } from '../stores/authStore'

/**
 * URL base del backend.
 * En producción (Render) configura VITE_API_URL en las variables de entorno.
 * En local usa el proxy de Vite, que redirige /api → http://localhost:8080.
 */
export const API_BASE = import.meta.env.VITE_API_URL
  ? import.meta.env.VITE_API_URL.replace(/\/$/, '')  // quita barra final si existe
  : ''                                                 // vacío → rutas relativas (proxy Vite)

/**
 * Construye la URL completa para un path dado, evitando doble /.
 * apiUrl('/api/auth/login') → '' + '/api/auth/login'  (local, via proxy)
 *                           → 'https://x.render.com' + '/api/auth/login'  (producción)
 */
export function apiUrl(path) {
  const p = path.startsWith('/') ? path : `/${path}`
  return `${API_BASE}${p}`
}

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
