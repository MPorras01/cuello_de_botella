<template>
  <div class="map-wrapper">
    <div ref="mapContainer" class="map-container" />

    <!-- Feed de trancones en vivo (estilo Waze, plegable) -->
    <TrafficFeed />

    <!-- Leyenda compacta -->
    <div class="legend">
      <div v-for="lvl in LEVELS" :key="lvl.key" class="legend-item">
        <i class="swatch" :style="{ background: lvl.color }" />
        <span>{{ lvl.label }}</span>
      </div>
      <div class="legend-item legend-bn">
        <span class="bn-mini">🚨</span>
        <span>Cuello de botella</span>
      </div>
    </div>

    <!-- Badge modo demo -->
    <div v-if="store.demoActive" class="demo-badge" title="Las API keys no están configuradas; se muestran datos simulados">
      🧪 Modo demo · sin API keys
    </div>

    <!-- Botón: reportar (demo) -->
    <button class="report-fab" @click="report" aria-label="Reportar un incidente">+ 🚧</button>

    <!-- Toast de reporte -->
    <transition name="toast">
      <div v-if="toast" class="toast">¡Gracias! 🚦 Tu reporte ayuda a otros conductores <em>(demo)</em></div>
    </transition>

    <!-- Badge del cuello de botella -->
    <div v-if="store.bottleneck" class="bottleneck-badge" :class="levelFromRatio(store.bottleneck.speedRatio ?? 1).key">
      <span class="badge-pulse" />
      <span class="badge-text">{{ labelNivel(store.bottleneck.speedRatio) }} en {{ store.bottleneck.segmentName }}</span>
      <button class="badge-focus" title="Ver en el mapa" @click="store.requestFocus(store.bottleneck.segmentId)">⌖</button>
    </div>

    <!-- Botón: volver al área metropolitana -->
    <button class="metro-btn" @click="focusMetro">⬤ Área metropolitana</button>

    <!-- Mensaje sin conexión -->
    <div v-if="!store.connected" class="offline-banner">
      Sin conexión — mostrando último estado disponible
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { useTrafficStore } from '../stores/trafficStore'
import TrafficFeed from './TrafficFeed.vue'
import { LEVELS, levelFromRatio, colorFromRatio, labelNivel } from '../utils/trafficLevels'

const store = useTrafficStore()
const mapContainer = ref(null)
const toast = ref('')

let map = null
let bottleneckMarker = null
let popup = null
let toastTimer = null

// ─── Geografía: Medellín y área metropolitana (Valle de Aburrá) ─────────────
const METRO_CENTER = [-75.5748, 6.2442]
const METRO_BOUNDS = [[-75.74, 6.08], [-75.36, 6.44]]
const MAX_BOUNDS = [[-76.40, 5.80], [-74.60, 7.20]]
const FALLBACK_STYLES = [
  'https://tiles.openfreemap.org/styles/liberty',
  'https://basemaps.cartocdn.com/gl/positron-gl-style/style.json'
]

/** Escapa HTML para popups — los nombres vienen de fuentes externas. */
function esc(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;').replaceAll("'", '&#39;')
}

/** Aclara un color hex (pct 0..1 hacia blanco) — para los gradientes de vía. */
function shade(hex, pct) {
  const n = parseInt(hex.slice(1), 16)
  const r = Math.round(((n >> 16) & 255) + (255 - ((n >> 16) & 255)) * pct)
  const g = Math.round(((n >> 8) & 255) + (255 - ((n >> 8) & 255)) * pct)
  const b = Math.round((n & 255) + (255 - (n & 255)) * pct)
  return `#${((1 << 24) | (r << 16) | (g << 8) | b).toString(16).slice(1)}`
}

/** Construye un GeoJSON FeatureCollection desde los segmentos del store. */
function buildGeoJSON(segs) {
  return {
    type: 'FeatureCollection',
    features: segs.map((s) => {
      const color = colorFromRatio(s.speedRatio ?? 1)
      return {
        type: 'Feature',
        properties: {
          segmentId: s.segmentId,
          segmentName: s.segmentName,
          speedRatio: s.speedRatio,
          speed: s.currentSpeed,
          congestionLevel: s.congestionLevel,
          color,
          colorFrom: shade(color, 0.45),
          colorTo: color
        },
        geometry: s.geometry ?? {
          type: 'Point',
          coordinates: [s.lng ?? METRO_CENTER[0], s.lat ?? METRO_CENTER[1]]
        }
      }
    })
  }
}

function focusMetro() {
  if (!map) return
  map.fitBounds(METRO_BOUNDS, { padding: 28, maxZoom: 13.5, duration: 900 })
}

function showSegmentPopup(seg, lngLat) {
  if (!map) return
  const ratio = seg.speedRatio != null ? Number(seg.speedRatio).toFixed(2) : '—'
  const speed = seg.speed != null ? Math.round(seg.speed) : null
  const lvl = levelFromRatio(seg.speedRatio ?? 1)
  if (!popup) popup = new maplibregl.Popup({ offset: 14, closeButton: false })
  popup.setLngLat(lngLat).setHTML(`
    <div class="wz-popup">
      <strong>${esc(seg.segmentName)}</strong>
      <span class="wz-popup-level" style="color:${lvl.color}">● ${esc(lvl.label)}</span>
      <span class="wz-popup-meta">
        ${speed != null ? `⚡ ${esc(speed)} km/h · ` : ''}speedRatio <b>${esc(ratio)}</b>
      </span>
    </div>
  `).addTo(map)
}

function report() {
  toast.value = true
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toast.value = false }, 2800)
}

onMounted(() => {
  map = new maplibregl.Map({
    container: mapContainer.value,
    style: FALLBACK_STYLES[0],
    center: METRO_CENTER,
    zoom: 12,
    maxBounds: MAX_BOUNDS,
    locale: {
      'NavigationControl.ZoomIn': 'Acercar',
      'NavigationControl.ZoomOut': 'Alejar',
      'NavigationControl.ResetBearing': 'Restablecer orientación'
    }
  })

  map.addControl(new maplibregl.NavigationControl(), 'top-right')
  map.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-left')

  // Si el estilo principal falla, probar con un respaldo
  let styleIdx = 0
  map.on('error', (e) => {
    if (e?.error && !e.source && !e.tile && styleIdx < FALLBACK_STYLES.length - 1) {
      styleIdx++
      console.warn('[Map] Estilo no disponible, probando respaldo:', FALLBACK_STYLES[styleIdx])
      map.setStyle(FALLBACK_STYLES[styleIdx])
    }
  })

  map.on('load', () => {
    focusMetro()

    map.addSource('traffic', {
      type: 'geojson',
      data: buildGeoJSON([])
    })

    // Halo difuso bajo la vía
    map.addLayer({
      id: 'traffic-glow',
      type: 'line',
      source: 'traffic',
      filter: ['==', ['geometry-type'], 'LineString'],
      paint: {
        'line-color': ['get', 'color'],
        'line-width': 16,
        'line-opacity': 0.3,
        'line-blur': 8
      }
    })

    // Contorno oscuro (casing) para resaltar sobre el mapa
    map.addLayer({
      id: 'traffic-casing',
      type: 'line',
      source: 'traffic',
      filter: ['==', ['geometry-type'], 'LineString'],
      paint: {
        'line-color': '#334155',
        'line-width': 10,
        'line-opacity': 0.55,
        'line-cap': 'round',
        'line-join': 'round'
      }
    })

    // Línea principal: gradiente de color a lo largo de la vía según congestión.
    // Nota: line-gradient exige line-width constante (6) y no soporta
    // line-dasharray ni line-blur en la misma capa — no mezclar.
    map.addLayer({
      id: 'traffic-segments',
      type: 'line',
      source: 'traffic',
      filter: ['==', ['geometry-type'], 'LineString'],
      paint: {
        'line-gradient': [
          'interpolate', ['linear'], ['line-progress'],
          0, ['get', 'colorFrom'],
          1, ['get', 'colorTo']
        ],
        'line-width': 6,
        'line-opacity': 0.95,
        'line-cap': 'round',
        'line-join': 'round'
      }
    })

    // Flechas de dirección del flujo (estilo Waze)
    const arrowCanvas = document.createElement('canvas')
    arrowCanvas.width = 32
    arrowCanvas.height = 32
    const actx = arrowCanvas.getContext('2d')
    actx.fillStyle = '#ffffff'
    actx.strokeStyle = 'rgba(15, 23, 42, 0.5)'
    actx.lineWidth = 3
    actx.beginPath()
    actx.moveTo(6, 4)
    actx.lineTo(27, 16)
    actx.lineTo(6, 28)
    actx.closePath()
    actx.fill()
    actx.stroke()
    map.addImage('flow-arrow', arrowCanvas)

    map.addLayer({
      id: 'traffic-arrows',
      type: 'symbol',
      source: 'traffic',
      filter: ['==', ['geometry-type'], 'LineString'],
      layout: {
        'symbol-placement': 'line',
        'symbol-spacing': 130,
        'icon-image': 'flow-arrow',
        'icon-size': 0.5,
        'icon-rotation-alignment': 'map',
        'icon-offset': [0, -4]
      },
      paint: { 'icon-opacity': 0.9 }
    })

    // Puntos para segmentos sin geometría de línea
    map.addLayer({
      id: 'traffic-points',
      type: 'circle',
      source: 'traffic',
      filter: ['==', ['geometry-type'], 'Point'],
      paint: {
        'circle-color': ['get', 'color'],
        'circle-radius': 8,
        'circle-stroke-width': 3,
        'circle-stroke-color': '#ffffff',
        'circle-opacity': 0.95
      }
    })

    // Capas de resaltado del cuello de botella (casing blanco + núcleo intermitente)
    map.addLayer({
      id: 'bottleneck-casing',
      type: 'line',
      source: 'traffic',
      filter: ['==', ['get', 'segmentId'], '__none__'],
      paint: {
        'line-color': '#ffffff',
        'line-width': 13,
        'line-opacity': 0.95,
        'line-cap': 'round',
        'line-join': 'round'
      }
    })
    map.addLayer({
      id: 'bottleneck-core',
      type: 'line',
      source: 'traffic',
      filter: ['==', ['get', 'segmentId'], '__none__'],
      paint: {
        'line-color': ['get', 'color'],
        'line-width': 7.5,
        'line-opacity': 1,
        'line-cap': 'round',
        'line-join': 'round',
        'line-dasharray': [3, 1.4]
      }
    })

    const targetLayers = ['traffic-segments', 'traffic-points', 'bottleneck-core']
    map.on('click', targetLayers, (e) => {
      const f = e.features?.[0]
      if (!f) return
      const p = f.properties
      showSegmentPopup(
        {
          segmentName: p.segmentName, speedRatio: p.speedRatio,
          segmentId: p.segmentId, congestionLevel: p.congestionLevel,
          speed: p.speed
        },
        e.lngLat
      )
    })
    map.on('mouseenter', targetLayers, () => { map.getCanvas().style.cursor = 'pointer' })
    map.on('mouseleave', targetLayers, () => { map.getCanvas().style.cursor = '' })

    if (store.segments.length > 0) updateTrafficLayer(store.segments)
    if (store.bottleneck) updateBottleneck(store.bottleneck)
  })
})

onUnmounted(() => {
  if (toastTimer) clearTimeout(toastTimer)
  if (map) {
    map.remove()
    map = null
  }
})

function updateTrafficLayer(segs) {
  const source = map?.getSource('traffic')
  if (source) source.setData(buildGeoJSON(segs))
}

function updateBottleneck(bn) {
  updateBottleneckHighlight(bn)
  updateBottleneckMarker(bn)
}

/** Resalta el segmento del cuello de botella sobre el mapa. */
function updateBottleneckHighlight(bn) {
  if (!map) return
  const filter = ['==', ['get', 'segmentId'], bn?.segmentId ?? '__none__']
  for (const layerId of ['bottleneck-casing', 'bottleneck-core']) {
    if (map.getLayer(layerId)) map.setFilter(layerId, filter)
  }
}

/** Marcador del cuello de botella: pin con anillo pulsante y etiqueta. */
function updateBottleneckMarker(bn) {
  if (!map) return
  if (bottleneckMarker) {
    bottleneckMarker.remove()
    bottleneckMarker = null
  }
  if (!bn) return

  const el = document.createElement('div')
  el.className = 'bottleneck-marker'
  el.innerHTML = `
    <span class="bn-ring"></span>
    <span class="bn-pin">🚨</span>
    <span class="bn-tag">Cuello de botella</span>
  `
  el.addEventListener('click', () => {
    showSegmentPopup(bn, { lng: bn.lng ?? METRO_CENTER[0], lat: bn.lat ?? METRO_CENTER[1] })
  })

  bottleneckMarker = new maplibregl.Marker({ element: el })
    .setLngLat([bn.lng ?? METRO_CENTER[0], bn.lat ?? METRO_CENTER[1]])
    .addTo(map)
}

// Centrar el mapa en el segmento solicitado desde el feed
watch(() => store.focusSegmentId, (id) => {
  if (!map || !id) return
  const seg = store.segments.find((s) => s.segmentId === id)
  if (!seg) return
  const coords = seg.geometry?.coordinates ?? [[seg.lng ?? METRO_CENTER[0], seg.lat ?? METRO_CENTER[1]]]
  if (coords.length > 1) {
    const bounds = coords.reduce(
      (b, c) => [
        [Math.min(b[0][0], c[0]), Math.min(b[0][1], c[1])],
        [Math.max(b[1][0], c[0]), Math.max(b[1][1], c[1])]
      ],
      [[coords[0][0], coords[0][1]], [coords[0][0], coords[0][1]]]
    )
    map.fitBounds(bounds, { padding: 90, duration: 700 })
  } else {
    map.flyTo({ center: coords[0], zoom: 14, duration: 700 })
  }
  showSegmentPopup(seg, { lng: coords[0][0], lat: coords[0][1] })
  store.focusSegmentId = null
})

watch(() => store.segments, updateTrafficLayer, { deep: true })
watch(() => store.bottleneck, updateBottleneck)
</script>

<style scoped>
.map-wrapper {
  position: relative;
  width: 100%;
  height: calc(100vh - 3.75rem);
}

.map-container {
  width: 100%;
  height: 100%;
}

/* ─── Leyenda compacta ─────────────────────────────────────────────────── */
.legend {
  position: absolute;
  bottom: 2.4rem;
  left: 0.9rem;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 0.8rem;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(6px);
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 999px;
  padding: 0.4rem 0.9rem;
  font-size: 0.7rem;
  font-weight: 700;
  color: #334155;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.12);
  user-select: none;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  white-space: nowrap;
}

.swatch {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.legend-bn {
  border-left: 1px solid #e2e8f0;
  padding-left: 0.8rem;
}

.bn-mini {
  font-size: 0.8rem;
  line-height: 1;
}

/* ─── Badge modo demo ──────────────────────────────────────────────────── */
.demo-badge {
  position: absolute;
  top: 7.2rem;
  right: 0.9rem;
  z-index: 10;
  padding: 0.4rem 0.8rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(148, 163, 184, 0.4);
  color: #475569;
  font-size: 0.72rem;
  font-weight: 700;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.14);
  user-select: none;
}

/* ─── FAB reportar ─────────────────────────────────────────────────────── */
.report-fab {
  position: absolute;
  right: 0.9rem;
  bottom: 5.2rem;
  z-index: 10;
  width: 3.3rem;
  height: 3.3rem;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #1a73e8 0%, #1557c9 100%);
  color: #fff;
  font-size: 1.35rem;
  font-weight: 800;
  cursor: pointer;
  box-shadow: 0 8px 22px rgba(26, 115, 232, 0.45);
  transition: transform 0.12s, box-shadow 0.15s;
}

.report-fab:hover {
  transform: scale(1.08);
  box-shadow: 0 10px 28px rgba(26, 115, 232, 0.55);
}

.report-fab:active {
  transform: scale(0.94);
}

.toast {
  position: absolute;
  bottom: 9.4rem;
  right: 0.9rem;
  z-index: 12;
  background: #1e293b;
  color: #f1f5f9;
  border-radius: 12px;
  padding: 0.6rem 0.9rem;
  font-size: 0.8rem;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.4);
  max-width: 240px;
}

.toast em {
  color: #93c5fd;
  font-style: normal;
}

.toast-enter-active, .toast-leave-active { transition: opacity 0.25s, transform 0.25s; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateY(8px); }

/* ─── Badge del cuello de botella ──────────────────────────────────────── */
.bottleneck-badge {
  position: absolute;
  bottom: 1.4rem;
  left: 50%;
  transform: translateX(-50%);
  padding: 0.55rem 1.25rem;
  border-radius: 999px;
  font-weight: 800;
  color: #fff;
  font-size: 0.85rem;
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.35);
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.badge-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 40vw;
}

.badge-focus {
  flex-shrink: 0;
  width: 1.6rem;
  height: 1.6rem;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
  font-size: 1rem;
  font-weight: 800;
  cursor: pointer;
  transition: background 0.15s, transform 0.08s;
}

.badge-focus:hover { background: rgba(255, 255, 255, 0.45); }
.badge-focus:active { transform: scale(0.9); }

.bottleneck-badge.severo        { background: #dc2626; }
.bottleneck-badge.congestionado { background: #ea580c; }
.bottleneck-badge.lento         { background: #ca8a04; }
.bottleneck-badge.fluido        { background: #16a34a; }

.badge-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
  flex-shrink: 0;
  animation: pulse 1.2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50%      { opacity: 0.35; transform: scale(0.75); }
}

/* ─── Botón área metropolitana ─────────────────────────────────────────── */
.metro-btn {
  position: absolute;
  bottom: 5.2rem;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
  padding: 0.5rem 1rem;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(6px);
  color: #1e293b;
  font-size: 0.78rem;
  font-weight: 800;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.16);
  transition: border-color 0.15s, transform 0.05s;
}

.metro-btn:hover {
  border-color: #1a73e8;
  color: #1a73e8;
}

.metro-btn:active {
  transform: translateX(-50%) scale(0.96);
}

/* ─── Sin conexión ─────────────────────────────────────────────────────── */
.offline-banner {
  position: absolute;
  top: 0.9rem;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(220, 38, 38, 0.94);
  color: #fff;
  padding: 0.4rem 1rem;
  border-radius: 10px;
  font-size: 0.78rem;
  font-weight: 700;
  z-index: 10;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.3);
}

@media (max-width: 640px) {
  .legend { display: none; }
}
</style>

<style>
/* ─── Marcador del cuello de botella ───────────────────────────────────── */
.bottleneck-marker {
  position: relative;
  width: 40px;
  height: 40px;
  cursor: pointer;
}

.bn-pin {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 1.8rem;
  z-index: 2;
  filter: drop-shadow(0 3px 8px rgba(0, 0, 0, 0.45));
  animation: marker-bounce 1.6s ease-in-out infinite;
}

.bn-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(239, 68, 68, 0.55) 0%, rgba(239, 68, 68, 0) 70%);
  animation: bn-pulse 1.6s ease-out infinite;
}

.bn-tag {
  position: absolute;
  top: calc(100% - 4px);
  left: 50%;
  transform: translateX(-50%);
  white-space: nowrap;
  background: #dc2626;
  color: #fff;
  font-size: 0.62rem;
  font-weight: 800;
  font-family: 'Nunito', sans-serif;
  padding: 0.2rem 0.55rem;
  border-radius: 999px;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.35);
  z-index: 3;
}

@keyframes bn-pulse {
  0%   { transform: scale(0.5); opacity: 0.95; }
  70%  { transform: scale(2.1); opacity: 0; }
  100% { transform: scale(2.1); opacity: 0; }
}

@keyframes marker-bounce {
  0%, 100% { transform: translateY(0); }
  50%      { transform: translateY(-6px); }
}

/* ─── Popups de MapLibre — tema claro ──────────────────────────────────── */
.maplibregl-popup-content {
  background: #ffffff;
  color: #1e293b;
  border-radius: 14px;
  padding: 0.65rem 0.8rem;
  font-size: 0.8rem;
  font-family: 'Nunito', sans-serif;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.25);
}

.maplibregl-popup-tip {
  border-top-color: #ffffff !important;
}

.wz-popup {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.wz-popup-level {
  font-weight: 800;
}

.wz-popup-meta {
  color: #64748b;
  font-size: 0.72rem;
}
</style>
