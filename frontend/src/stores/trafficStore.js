import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

/**
 * Store central de tráfico.
 * Requisitos: 3.6, 4.4
 */
export const useTrafficStore = defineStore('traffic', () => {
  /** @type {import('vue').Ref<Array>} Lista de segmentos del ciclo actual */
  const segments = ref([])

  /** @type {import('vue').Ref<Array>} Cuellos de botella del ciclo (peor primero, puede ser vacío) */
  const bottlenecks = ref([])

  /** @type {import('vue').Ref<Array>} Alertas de tráfico (policía, accidentes, obras, cierres) */
  const alerts = ref([])

  /** @type {import('vue').Ref<boolean>} Estado de la conexión SSE */
  const connected = ref(false)

  /** @type {import('vue').Ref<Array>} Histórico de congestión (HistorySummary[]) */
  const history = ref([])

  /** @type {import('vue').Ref<boolean>} Indica si se muestran datos demo (sin API keys) */
  const demoActive = ref(false)

  /** @type {import('vue').Ref<string|null>} Segmento solicitado para centrar el mapa */
  const focusSegmentId = ref(null)

  /** El cuello de botella más grave (compatibilidad con rutas alternativas). */
  const bottleneck = computed(() => bottlenecks.value[0] ?? null)

  /**
   * Actualiza segmentos, cuellos de botella y alertas desde un snapshot SSE.
   * @param {{ segments: Array, bottlenecks: Array, alerts: Array }} snapshot
   */
  function updateFromSnapshot(snapshot) {
    segments.value = snapshot.segments ?? []
    bottlenecks.value = snapshot.bottlenecks ?? []
    alerts.value = snapshot.alerts ?? []
  }

  /**
   * Actualiza el histórico de congestión.
   * @param {Array} data - Array de HistorySummary
   */
  function setHistory(data) {
    history.value = data ?? []
  }

  /** Pide centrar el mapa en un segmento o alerta. */
  function requestFocus(segmentId) {
    focusSegmentId.value = segmentId
  }

  return { segments, bottlenecks, bottleneck, alerts, connected, history, demoActive, focusSegmentId, updateFromSnapshot, setHistory, requestFocus }
})
