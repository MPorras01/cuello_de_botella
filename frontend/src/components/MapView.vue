<template>
  <div class="map-wrapper">
    <div ref="mapContainer" class="map-container" />

    <!-- Feed de trancones en vivo (estilo Waze, plegable) -->
    <TrafficFeed />

    <!-- Leyenda compacta -->
    <div class="legend" :class="{ dark: darkMode }">
      <div v-for="lvl in LEVELS" :key="lvl.key" class="legend-item">
        <i class="swatch" :style="{ background: lvl.color }" />
        <span>{{ lvl.label }}</span>
      </div>
      <div class="legend-item legend-bn">
        <span class="bn-mini">!</span>
        <span>Cuello de botella</span>
      </div>
      <div class="legend-item legend-alerts">
        <span class="alert-mini">👮</span>
        <span>Policía</span>
        <span class="alert-mini">⚠️</span>
        <span>Accidente</span>
        <span class="alert-mini">🚧</span>
        <span>Obras</span>
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

    <!-- Badge del cuello de botella (el peor, con contador si hay varios) -->
    <div v-if="store.bottlenecks.length" class="bottleneck-badge" :class="levelFromRatio(store.bottlenecks[0].speedRatio ?? 1).key">
      <span class="badge-pulse" />
      <span class="badge-text">
        {{ store.bottlenecks.length > 1 ? store.bottlenecks.length + ' cuellos de botella · ' : '' }}{{ labelNivel(store.bottlenecks[0].speedRatio) }} en {{ store.bottlenecks[0].segmentName }}
      </span>
      <button class="badge-focus" title="Ver en el mapa" @click="store.requestFocus(store.bottlenecks[0].segmentId)">⌖</button>
    </div>

    <!-- Botón: volver al área metropolitana -->
    <button class="metro-btn" @click="focusMetro">⬤ Área metropolitana</button>

    <!-- Botón: modo día/noche -->
    <button class="night-toggle" :title="darkMode ? 'Cambiar a modo día' : 'Cambiar a modo noche'" @click="toggleDarkMode">
      {{ darkMode ? '☀️' : '🌙' }}
    </button>

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
const darkMode = ref(
  localStorage.getItem('map-theme')
    ? localStorage.getItem('map-theme') === 'dark'
    : isNightTime()
)

let map = null
let popup = null
let toastTimer = null
let firstLoad = true
let fallbackIdx = 0
let alertMarkers = []
let bottleneckMarkers = []

// ─── Geografía: Medellín y área metropolitana (Valle de Aburrá) ─────────────
const METRO_CENTER = [-75.5748, 6.2442]
const METRO_BOUNDS = [[-75.74, 6.08], [-75.36, 6.44]]
const MAX_BOUNDS = [[-76.40, 5.80], [-74.60, 7.20]]

// Estilos claro / oscuro (noche)
const LIGHT_STYLES = [
  'https://tiles.openfreemap.org/styles/liberty',
  'https://basemaps.cartocdn.com/gl/positron-gl-style/style.json'
]
const DARK_STYLES = [
  'https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json',
  'https://tiles.openfreemap.org/styles/dark-matter'
]

const ALERT_ICONS = {
  POLICE: '👮', ACCIDENT: '⚠️', WORKS: '🚧', CLOSURE: '⛔', HAZARD: '☢️', OTHER: '🛈'
}

/** ¿Es de noche? (19:00 – 06:00 hora local). */
function isNightTime() {
  const h = new Date().getHours()
  return h >= 19 || h < 6
}

function currentStyles() {
  return darkMode.value ? DARK_STYLES : LIGHT_STYLES
}

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

function showAlertPopup(alert) {
  if (!map) return
  if (!popup) popup = new maplibregl.Popup({ offset: 16, closeButton: false })
  popup.setLngLat([alert.lng, alert.lat]).setHTML(`
    <div class="wz-popup">
      <strong>${esc(ALERT_ICONS[alert.type] ?? '🛈')} ${esc(alert.title)}</strong>
      <span class="wz-popup-meta">${esc(alert.description)}</span>
    </div>
  `).addTo(map)
}

function report() {
  toast.value = true
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toast.value = false }, 2800)
}

function toggleDarkMode() {
  darkMode.value = !darkMode.value
  localStorage.setItem('map-theme', darkMode.value ? 'dark' : 'light')
  fallbackIdx = 0
  map?.setStyle(currentStyles()[0])
}

// ─── Capas de tráfico (se recrean tras cada cambio de estilo) ───────────────

function onSegmentClick(e) {
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
}

const onMouseEnter = () => { map.getCanvas().style.cursor = 'pointer' }
const onMouseLeave = () => { map.getCanvas().style.cursor = '' }

function initTrafficLayers() {
  if (!map) return

  if (!map.getSource('traffic')) {
    map.addSource('traffic', { type: 'geojson', data: buildGeoJSON([]) })
  }

  if (!map.hasImage('flow-arrow')) {
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
  }

  const addLayer = (layer) => { if (!map.getLayer(layer.id)) map.addLayer(layer) }

  // Halo difuso bajo la vía
  addLayer({
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
  addLayer({
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
  addLayer({
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
  addLayer({
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
  addLayer({
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

  // Capas de resaltado de cuellos de botella (casing blanco + núcleo intermitente)
  addLayer({
    id: 'bottleneck-casing',
    type: 'line',
    source: 'traffic',
    filter: ['in', ['get', 'segmentId'], ['literal', []]],
    paint: {
      'line-color': '#ffffff',
      'line-width': 13,
      'line-opacity': 0.95,
      'line-cap': 'round',
      'line-join': 'round'
    }
  })
  addLayer({
    id: 'bottleneck-core',
    type: 'line',
    source: 'traffic',
    filter: ['in', ['get', 'segmentId'], ['literal', []]],
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
  map.off('click', targetLayers, onSegmentClick)
  map.on('click', targetLayers, onSegmentClick)
  map.off('mouseenter', targetLayers, onMouseEnter)
  map.on('mouseenter', targetLayers, onMouseEnter)
  map.off('mouseleave', targetLayers, onMouseLeave)
  map.on('mouseleave', targetLayers, onMouseLeave)

  // Reaplicar datos tras un cambio de estilo
  updateTrafficLayer(store.segments)
  updateBottleneckHighlight(store.bottlenecks)
  updateBottleneckMarkers(store.bottlenecks)
  updateAlertMarkers(store.alerts)
}

onMounted(() => {
  map = new maplibregl.Map({
    container: mapContainer.value,
    style: currentStyles()[0],
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

  // Fallback de estilo: probar los respaldos del grupo actual
  map.on('error', (e) => {
    if (e?.error && !e.source && !e.tile) {
      const styles = currentStyles()
      if (fallbackIdx < styles.length - 1) {
        fallbackIdx++
        console.warn('[Map] Estilo no disponible, probando respaldo:', styles[fallbackIdx])
        map.setStyle(styles[fallbackIdx])
      } else if (styles !== DARK_STYLES) {
        darkMode.value = true
        fallbackIdx = 0
        map.setStyle(DARK_STYLES[0])
      }
    }
  })

  map.on('load', () => {
    initTrafficLayers()
    if (firstLoad) {
      firstLoad = false
      focusMetro()
    }
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

/** Resalta en el mapa todos los segmentos que son cuellos de botella. */
function updateBottleneckHighlight(bottlenecks) {
  if (!map) return
  const ids = (bottlenecks ?? []).map((b) => b.segmentId)
  const filter = ['in', ['get', 'segmentId'], ['literal', ids]]
  for (const layerId of ['bottleneck-casing', 'bottleneck-core']) {
    if (map.getLayer(layerId)) map.setFilter(layerId, filter)
  }
}

/** Marcadores de los cuellos de botella: badge redondo con '!' y anillo pulsante. */
function updateBottleneckMarkers(bottlenecks) {
  if (!map) return
  bottleneckMarkers.forEach((m) => m.remove())
  bottleneckMarkers = []
  for (const bn of bottlenecks ?? []) {
    if (bn.lat == null || bn.lng == null) continue
    const lvl = levelFromRatio(bn.speedRatio ?? 1)
    const el = document.createElement('div')
    el.className = `bn-marker lvl-${lvl.key}`
    el.title = bn.segmentName
    el.innerHTML = `
      <span class="bn-ring"></span>
      <span class="bn-badge">!</span>
    `
    el.addEventListener('click', () => {
      showSegmentPopup(bn, { lng: bn.lng, lat: bn.lat })
    })
    bottleneckMarkers.push(new maplibregl.Marker({ element: el })
      .setLngLat([bn.lng, bn.lat])
      .addTo(map))
  }
}

/** Marcadores de alertas (policía, accidentes, obras, cierres). */
function updateAlertMarkers(alerts) {
  if (!map) return
  alertMarkers.forEach((m) => m.remove())
  alertMarkers = []
  for (const alert of alerts ?? []) {
    if (alert.lat == null || alert.lng == null) continue
    const el = document.createElement('div')
    el.className = `alert-marker type-${(alert.type ?? 'OTHER').toLowerCase()}`
    el.innerHTML = `<span class="alert-ico">${esc(ALERT_ICONS[alert.type] ?? '🛈')}</span>`
    el.title = alert.title
    el.addEventListener('click', () => showAlertPopup(alert))
    alertMarkers.push(new maplibregl.Marker({ element: el })
      .setLngLat([alert.lng, alert.lat])
      .addTo(map))
  }
}

function updateBottlenecks(bottlenecks) {
  updateBottleneckHighlight(bottlenecks)
  updateBottleneckMarkers(bottlenecks)
}

// Centrar el mapa en el segmento o alerta solicitado desde el feed
watch(() => store.focusSegmentId, (id) => {
  if (!map || !id) return
  const seg = store.segments.find((s) => s.segmentId === id)
  if (seg) {
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
  } else {
    const alert = store.alerts.find((a) => a.id === id)
    if (alert) {
      map.flyTo({ center: [alert.lng, alert.lat], zoom: 15, duration: 700 })
      showAlertPopup(alert)
    }
  }
  store.focusSegmentId = null
})

watch(() => store.segments, updateTrafficLayer, { deep: true })
watch(() => store.bottlenecks, updateBottlenecks, { deep: true })
watch(() => store.alerts, updateAlertMarkers, { deep: true })
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
  transition: background 0.25s, color 0.25s;
}

.legend.dark {
  background: rgba(15, 23, 42, 0.85);
  color: #e2e8f0;
  border-color: rgba(255, 255, 255, 0.12);
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

.legend.dark .legend-bn {
  border-left-color: rgba(255, 255, 255, 0.15);
}

.bn-mini {
  width: 13px;
  height: 13px;
  border-radius: 50%;
  background: #dc2626;
  color: #fff;
  font-size: 0.62rem;
  font-weight: 900;
  display: grid;
  place-items: center;
  line-height: 1;
}

.legend-alerts {
  border-left: 1px solid #e2e8f0;
  padding-left: 0.8rem;
  gap: 0.45rem;
}

.legend.dark .legend-alerts {
  border-left-color: rgba(255, 255, 255, 0.15);
}

.alert-mini {
  font-size: 0.78rem;
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

/* ─── Botón modo día/noche ─────────────────────────────────────────────── */
.night-toggle {
  position: absolute;
  top: 4.6rem;
  right: 0.9rem;
  z-index: 10;
  width: 2.6rem;
  height: 2.6rem;
  border-radius: 50%;
  border: 1px solid rgba(15, 23, 42, 0.1);
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(6px);
  font-size: 1.1rem;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.18);
  transition: transform 0.12s, box-shadow 0.15s;
}

.night-toggle:hover { transform: scale(1.1); }
.night-toggle:active { transform: scale(0.92); }

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
/* ─── Marcador de cuello de botella (badge '!' + anillo pulsante) ───────── */
.bn-marker {
  position: relative;
  width: 38px;
  height: 38px;
  cursor: pointer;
}

.bn-badge {
  position: absolute;
  top: 2px;
  left: 50%;
  transform: translateX(-50%);
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  font-family: 'Baloo 2', 'Nunito', sans-serif;
  font-size: 1.15rem;
  font-weight: 900;
  border: 3px solid #ffffff;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.45);
  z-index: 2;
  background: linear-gradient(135deg, #f87171 0%, #dc2626 70%);
}

.bn-marker.lvl-lento .bn-badge { background: linear-gradient(135deg, #fde047 0%, #ca8a04 75%); }
.bn-marker.lvl-congestionado .bn-badge { background: linear-gradient(135deg, #fb923c 0%, #ea580c 75%); }
.bn-marker.lvl-fluido .bn-badge { background: linear-gradient(135deg, #4ade80 0%, #16a34a 75%); }

/* Punta inferior estilo Waze */
.bn-badge::after {
  content: '';
  position: absolute;
  bottom: -9px;
  left: 50%;
  transform: translateX(-50%);
  border-left: 7px solid transparent;
  border-right: 7px solid transparent;
  border-top: 10px solid #dc2626;
}

.bn-marker.lvl-lento .bn-badge::after { border-top-color: #ca8a04; }
.bn-marker.lvl-congestionado .bn-badge::after { border-top-color: #ea580c; }
.bn-marker.lvl-fluido .bn-badge::after { border-top-color: #16a34a; }

.bn-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(239, 68, 68, 0.55) 0%, rgba(239, 68, 68, 0) 70%);
  animation: bn-pulse 1.6s ease-out infinite;
}

@keyframes bn-pulse {
  0%   { transform: scale(0.5); opacity: 0.95; }
  70%  { transform: scale(2.1); opacity: 0; }
  100% { transform: scale(2.1); opacity: 0; }
}

/* ─── Marcador de alertas (policía, accidentes, obras...) ───────────────── */
.alert-marker {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #fff;
  border: 2.5px solid #64748b;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.35);
  cursor: pointer;
  font-size: 0.98rem;
  transition: transform 0.12s;
}

.alert-marker:hover { transform: scale(1.18); }

.alert-marker.type-police { border-color: #2563eb; background: #eff6ff; }
.alert-marker.type-accident { border-color: #dc2626; background: #fef2f2; }
.alert-marker.type-works { border-color: #ea580c; background: #fff7ed; }
.alert-marker.type-closure { border-color: #7c3aed; background: #f5f3ff; }
.alert-marker.type-hazard { border-color: #d97706; background: #fffbeb; }
.alert-marker.type-other { border-color: #64748b; background: #f8fafc; }

.alert-ico {
  line-height: 1;
  filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.25));
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
