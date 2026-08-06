import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authFetch } from '../services/api'

/**
 * Store de la capa de usuario: informes colocados en el mapa.
 * Hace polling cada 20 s para ver los informes de los demás.
 */
export const useReportsStore = defineStore('reports', () => {
  const reports = ref([])
  const loading = ref(false)
  const error = ref('')
  /** true → el siguiente clic en el mapa coloca un informe */
  const placing = ref(false)
  /** coordenadas del informe pendiente de confirmar en el modal */
  const pendingReport = ref(null)

  let pollTimer = null

  async function fetchReports() {
    try {
      const res = await authFetch('/api/reports')
      if (res.ok) {
        reports.value = await res.json()
        return true
      }
      return false
    } catch {
      return false
    }
  }

  async function createReport(payload) {
    const res = await authFetch('/api/reports', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    if (!res.ok) {
      const body = await res.json().catch(() => null)
      throw new Error(body?.error ?? body?.message ?? 'No se pudo guardar el informe')
    }
    const created = await res.json()
    reports.value.unshift(created)
    return created
  }

  async function deleteReport(id) {
    const res = await authFetch(`/api/reports/${id}`, { method: 'DELETE' })
    if (res.ok) {
      reports.value = reports.value.filter((r) => r.id !== id)
      return true
    }
    return false
  }

  /** Inicia el modo colocación: el siguiente clic en el mapa pide la coordenada. */
  function startPlacing() {
    placing.value = true
  }

  function stopPlacing() {
    placing.value = false
  }

  function setPendingReport(lat, lng) {
    pendingReport.value = { lat, lng }
  }

  function clearPendingReport() {
    pendingReport.value = null
    placing.value = false
  }

  function startPolling() {
    stopPolling()
    fetchReports()
    pollTimer = setInterval(fetchReports, 20000)
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  return {
    reports, loading, error, placing, pendingReport,
    fetchReports, createReport, deleteReport,
    startPlacing, stopPlacing, setPendingReport, clearPendingReport,
    startPolling, stopPolling
  }
})
