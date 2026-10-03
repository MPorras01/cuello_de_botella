import { useTrafficStore } from '../stores/trafficStore'
import { useAuthStore } from '../stores/authStore'
import { buildDemoSegments, buildDemoAlerts } from './demoTraffic'
import { apiUrl } from './api'

/**
 * Conexión SSE al backend usando fetch + ReadableStream.
 * Requisitos: 3.5, 3.6, 3.7
 * A diferencia de EventSource, fetch permite enviar el token JWT en el
 * header Authorization (el token nunca viaja en la URL).
 */

let controller = null
let retryTimeout = null
let stopped = false

export async function connectSSE() {
  const store = useTrafficStore()
  const auth = useAuthStore()

  stopped = false

  if (retryTimeout) {
    clearTimeout(retryTimeout)
    retryTimeout = null
  }
  if (controller) controller.abort()

  if (!auth.token) {
    store.connected = false
    return
  }

  controller = new AbortController()

  try {
    const response = await fetch(apiUrl('/api/stream/traffic'), {
      headers: { Authorization: `Bearer ${auth.token}` },
      signal: controller.signal
    })

    if (response.status === 401) {
      auth.logout()
      store.connected = false
      scheduleReconnect()
      return
    }
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    if (!response.body) throw new Error('Stream no soportado por el navegador')

    store.connected = true
    console.info('[SSE] Conectado a /api/stream/traffic')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // Los eventos SSE se separan por línea en blanco
      const events = buffer.split('\n\n')
      buffer = events.pop() ?? ''

      for (const rawEvent of events) {
        const data = parseSSEData(rawEvent)
        if (data) {
          try {
            const snapshot = JSON.parse(data)
            const hasData = (snapshot.segments && snapshot.segments.length > 0)
              || (snapshot.alerts && snapshot.alerts.length > 0)
            if (hasData) {
              store.demoActive = false
              store.updateFromSnapshot(snapshot)
            } else {
              // Sin datos de ninguna fuente (API keys ausentes o cuota agotada
              // sin caché previa): se muestra el modo demo para visualizar.
              store.demoActive = true
              const demo = buildDemoSegments()
              const bottlenecks = demo
                .filter((s) => s.speedRatio < 0.5)
                .sort((a, b) => a.speedRatio - b.speedRatio)
              store.updateFromSnapshot({
                segments: demo,
                bottlenecks,
                alerts: buildDemoAlerts()
              })
            }
          } catch (err) {
            console.error('[SSE] Error deserializando snapshot:', err)
          }
        }
      }
    }

    // El servidor cerró el stream → reconectar
    store.connected = false
    scheduleReconnect()
  } catch (err) {
    if (err.name === 'AbortError') {
      // Cierre intencional (logout o reconexión)
      return
    }
    console.warn('[SSE] Conexión interrumpida. Reintentando en 5 s...', err)
    store.connected = false
    scheduleReconnect()
  }
}

/** Extrae el contenido de la(s) línea(s) "data:" de un evento SSE. */
function parseSSEData(rawEvent) {
  let data = null
  for (const line of rawEvent.split('\n')) {
    if (line.startsWith('data:')) {
      data = line.slice(5).trim()
    }
  }
  return data
}

/** Reintenta la conexión tras 5 s. Requisito 3.7 */
function scheduleReconnect() {
  if (stopped || retryTimeout) return
  retryTimeout = setTimeout(() => {
    retryTimeout = null
    connectSSE()
  }, 5000)
}

/**
 * Cierra la conexión SSE y cancela cualquier reintento pendiente.
 */
export function disconnectSSE() {
  stopped = true
  if (retryTimeout) {
    clearTimeout(retryTimeout)
    retryTimeout = null
  }
  if (controller) {
    controller.abort()
    controller = null
  }
  const store = useTrafficStore()
  store.connected = false
}
