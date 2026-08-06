<template>
  <!-- Este componente no tiene template propio: dibuja sobre el mapa vía props -->
  <div v-if="noRoutes" class="no-routes-msg">
    No hay rutas alternativas disponibles para este segmento
  </div>
</template>

<script setup>
/**
 * AlternativeRoutes.vue
 * Dibuja rutas alternativas como polylines azules sobre el mapa de MapView.
 * Requisitos: 5.2, 5.3, 5.4, 5.5
 */
import { ref, watch, onUnmounted } from 'vue'
import { useTrafficStore } from '../stores/trafficStore'
import { authFetch } from '../services/api'

const props = defineProps({
  /** Instancia del mapa MapLibre GL — pasada desde App.vue o MapView */
  map: { type: Object, default: null }
})

const store = useTrafficStore()
const noRoutes = ref(false)

const ROUTE_LAYER_PREFIX = 'alt-route-'
const ROUTE_SOURCE_PREFIX = 'alt-route-src-'
let activeRouteLayers = []

/** Elimina las polylines anteriores del mapa. Requisito 5.4 */
function clearRoutes() {
  if (!props.map) return
  activeRouteLayers.forEach((id) => {
    if (props.map.getLayer(id)) props.map.removeLayer(id)
    const srcId = id.replace(ROUTE_LAYER_PREFIX, ROUTE_SOURCE_PREFIX)
    if (props.map.getSource(srcId)) props.map.removeSource(srcId)
  })
  activeRouteLayers = []
}

/** Dibuja las rutas alternativas como LineStrings azules. Requisito 5.3 */
function drawRoutes(routes) {
  clearRoutes()
  noRoutes.value = false

  if (!props.map || !routes || routes.length === 0) {
    noRoutes.value = true  // Requisito 5.5
    return
  }

  routes.slice(0, 2).forEach((route, idx) => {
    const srcId = `${ROUTE_SOURCE_PREFIX}${idx}`
    const layerId = `${ROUTE_LAYER_PREFIX}${idx}`

    props.map.addSource(srcId, {
      type: 'geojson',
      data: {
        type: 'Feature',
        geometry: route  // GeoJSON LineString
      }
    })

    props.map.addLayer({
      id: layerId,
      type: 'line',
      source: srcId,
      paint: {
        'line-color': '#3b82f6',
        'line-width': 4,
        'line-opacity': 0.8,
        'line-dasharray': [2, 1]
      }
    })

    activeRouteLayers.push(layerId)
  })
}

/** Consulta rutas alternativas cuando cambia el cuello de botella. Requisito 5.2 */
async function fetchAndDraw(bottleneck) {
  if (!bottleneck || !props.map) {
    clearRoutes()
    return
  }

  try {
    const res = await authFetch(`/api/routes/alternatives?segmentId=${encodeURIComponent(bottleneck.segmentId)}`)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const data = await res.json()
    drawRoutes(data.routes ?? [])
  } catch (err) {
    console.warn('[AlternativeRoutes] Error obteniendo rutas:', err)
    clearRoutes()
    noRoutes.value = true
  }
}

watch(() => store.bottleneck, fetchAndDraw, { immediate: true })
watch(() => props.map, (newMap) => {
  if (newMap && store.bottleneck) fetchAndDraw(store.bottleneck)
})

onUnmounted(clearRoutes)
</script>

<style scoped>
.no-routes-msg {
  position: fixed;
  bottom: 5rem;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(30, 41, 59, 0.92);
  color: #cbd5e1;
  padding: 0.5rem 1.25rem;
  border-radius: 8px;
  font-size: 0.85rem;
  z-index: 20;
}
</style>
