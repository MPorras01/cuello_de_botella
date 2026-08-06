import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * Store central de tráfico.
 * Requisitos: 3.6, 4.4
 */
export const useTrafficStore = defineStore('traffic', () => {
  /** @type {import('vue').Ref<Array>} Lista de segmentos del ciclo actual */
  const segments = ref([])

  /** @type {import('vue').Ref<Object|null>} Cuello de botella del ciclo actual */
  const bottleneck = ref(null)

  /** @type {import('vue').Ref<boolean>} Estado de la conexión SSE */
  const connected = ref(false)

  /** @type {import('vue').Ref<Array>} Histórico de congestión (HistorySummary[]) */
  const history = ref([])

  /** @type {import('vue').Ref<boolean>} Indica si se muestran datos demo (sin API keys) */
  const demoActive = ref(false)

  /** @type {import('vue').Ref<string|null>} Segmento solicitado para centrar el mapa */
  const focusSegmentId = ref(null)

  /**
   * Actualiza segmentos y cuello de botella desde un snapshot SSE.
   * @param {{ segments: Array, bottleneck: Object|null }} snapshot
   */
  function updateFromSnapshot(snapshot) {
    segments.value = snapshot.segments ?? []
    bottleneck.value = snapshot.bottleneck ?? null
  }

  /**
   * Actualiza el histórico de congestión.
   * @param {Array} data - Array de HistorySummary
   */
  function setHistory(data) {
    history.value = data ?? []
  }

  /** Pide centrar el mapa en un segmento. */
  function requestFocus(segmentId) {
    focusSegmentId.value = segmentId
  }

  return { segments, bottleneck, connected, history, demoActive, focusSegmentId, updateFromSnapshot, setHistory, requestFocus }
})
