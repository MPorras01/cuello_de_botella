<template>
  <aside v-if="store.segments.length > 0" class="traffic-feed" :class="{ 'is-collapsed': !open }">
    <!-- Cabecera: clic para plegar/desplegar -->
    <header
      class="feed-header"
      role="button"
      tabindex="0"
      :aria-expanded="open"
      @click="open = !open"
      @keydown.enter="open = !open"
    >
      <span class="feed-title">🚦 Trancones en vivo</span>
      <span class="feed-count">{{ store.segments.length }}</span>
      <span class="feed-chevron" :class="{ open }" aria-hidden="true">▾</span>
    </header>

    <transition name="collapse">
      <div v-if="open" class="feed-body">
        <!-- Filtros / pestañas -->
        <div class="feed-tabs">
          <button
            class="feed-tab"
            :class="{ active: filter === 'todos' }"
            @click.stop="filter = 'todos'"
          >Todos</button>
          <button
            class="feed-tab"
            :class="{ active: filter === 'alertas' }"
            @click.stop="filter = 'alertas'"
          >⚠ Alertas</button>
          <button
            class="feed-tab"
            :class="{ active: filter === 'incidentes' }"
            @click.stop="filter = 'incidentes'"
          >🚨 Incidentes <span class="tab-count">{{ store.alerts.length }}</span></button>
        </div>

        <!-- Lista de incidentes (policía, accidentes, obras...) -->
        <ul v-if="filter === 'incidentes' && store.alerts.length > 0" class="feed-list">
          <li
            v-for="alert in store.alerts"
            :key="alert.id"
            class="feed-row"
            @click="store.requestFocus(alert.id)"
          >
            <span class="alert-ico" :class="'type-' + (alert.type ?? 'OTHER').toLowerCase()">
              {{ ALERT_ICONS[alert.type] ?? '🛈' }}
            </span>
            <div class="feed-info">
              <span class="feed-name">{{ alert.title }}</span>
              <span class="feed-desc">{{ alert.description }}</span>
            </div>
          </li>
        </ul>

        <!-- Lista de segmentos -->
        <ul v-else-if="filter !== 'incidentes' && filteredSegments.length > 0" class="feed-list">
          <li
            v-for="seg in filteredSegments"
            :key="seg.segmentId"
            class="feed-row"
            @click="store.requestFocus(seg.segmentId)"
          >
            <span class="feed-dot" :style="{ background: levelFromRatio(seg.speedRatio ?? 1).color }" />
            <div class="feed-info">
              <span class="feed-name">
                {{ seg.segmentName }}
                <span v-if="isBottleneck(seg.segmentId)" class="bn-chip" title="Cuello de botella">!</span>
              </span>
              <span class="feed-level" :style="{ color: levelFromRatio(seg.speedRatio ?? 1).color }">
                {{ levelFromRatio(seg.speedRatio ?? 1).label }}
                <em v-if="seg.speed != null">· {{ Math.round(seg.speed) }} km/h</em>
              </span>
            </div>
            <span class="feed-ratio">{{ (seg.speedRatio ?? 0).toFixed(2) }}</span>
          </li>
        </ul>

        <div v-else class="feed-empty">Sin alertas por ahora 🎉</div>
      </div>
    </transition>
  </aside>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useTrafficStore } from '../stores/trafficStore'
import { levelFromRatio } from '../utils/trafficLevels'

const store = useTrafficStore()
const open = ref(true)
const filter = ref('todos')

const ALERT_ICONS = {
  POLICE: '👮', ACCIDENT: '⚠️', WORKS: '🚧', CLOSURE: '⛔', HAZARD: '☢️', OTHER: '🛈'
}

/** Peor primero: menor speedRatio arriba. */
const sortedSegments = computed(() =>
  [...store.segments].sort((a, b) => (a.speedRatio ?? 1) - (b.speedRatio ?? 1))
)

/** Aplicar filtro de alertas (solo lento/congestión/trancón). */
const filteredSegments = computed(() => {
  if (filter.value === 'alertas') {
    return sortedSegments.value.filter((s) => (s.speedRatio ?? 1) < 0.75)
  }
  return sortedSegments.value
})

/** ¿Este segmento es un cuello de botella? */
function isBottleneck(segmentId) {
  return store.bottlenecks.some((b) => b.segmentId === segmentId)
}
</script>

<style scoped>
.traffic-feed {
  position: absolute;
  top: 0.9rem;
  left: 0.9rem;
  z-index: 20;
  width: 290px;
  max-width: calc(100% - 2rem);
  max-height: 58%;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 14px 40px rgba(15, 23, 42, 0.22);
  overflow: hidden;
  font-size: 0.8rem;
}

.traffic-feed.is-collapsed {
  max-height: none;
}

.feed-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.7rem 0.9rem;
  border-bottom: 1px solid #eef2f7;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}

.feed-header:hover {
  background: #f1f5f9;
}

.feed-title {
  font-weight: 800;
  color: #1e293b;
  letter-spacing: 0.01em;
}

.feed-count {
  background: #1a73e8;
  color: #fff;
  font-size: 0.68rem;
  font-weight: 800;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
}

.feed-chevron {
  margin-left: auto;
  color: #94a3b8;
  font-size: 0.85rem;
  transition: transform 0.25s;
}

.feed-chevron.open {
  transform: rotate(180deg);
}

.feed-body {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  max-height: var(--feed-max-h, 320px);
}

.feed-tabs {
  display: flex;
  gap: 0.3rem;
  padding: 0.45rem 0.6rem 0.1rem;
}

.feed-tab {
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #64748b;
  font-family: inherit;
  font-size: 0.68rem;
  font-weight: 800;
  padding: 0.25rem 0.7rem;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.15s;
}

.feed-tab:hover { color: #1e293b; border-color: #cbd5e1; }
.feed-tab.active {
  background: #1a73e8;
  border-color: #1a73e8;
  color: #fff;
}

.tab-count {
  background: rgba(255, 255, 255, 0.25);
  border-radius: 999px;
  padding: 0.05rem 0.4rem;
  font-size: 0.62rem;
}

.feed-tab:not(.active) .tab-count {
  background: #e2e8f0;
  color: #475569;
}

/* Chip de cuello de botella en la lista */
.bn-chip {
  display: inline-grid;
  place-items: center;
  width: 15px;
  height: 15px;
  border-radius: 50%;
  background: #dc2626;
  color: #fff;
  font-size: 0.62rem;
  font-weight: 900;
  margin-left: 0.25rem;
  vertical-align: middle;
}

/* Icono de alerta en la lista */
.alert-ico {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 0.95rem;
  border: 2px solid #64748b;
  background: #f8fafc;
}

.alert-ico.type-police { border-color: #2563eb; background: #eff6ff; }
.alert-ico.type-accident { border-color: #dc2626; background: #fef2f2; }
.alert-ico.type-works { border-color: #ea580c; background: #fff7ed; }
.alert-ico.type-closure { border-color: #7c3aed; background: #f5f3ff; }
.alert-ico.type-hazard { border-color: #d97706; background: #fffbeb; }

.feed-desc {
  font-size: 0.66rem;
  color: #94a3b8;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.feed-list {
  list-style: none;
  overflow-y: auto;
  padding: 0.3rem;
  flex: 1;
}

.feed-list::-webkit-scrollbar { width: 6px; }
.feed-list::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 3px; }

.feed-empty {
  padding: 1rem 0.8rem;
  text-align: center;
  color: #64748b;
  font-weight: 700;
  font-size: 0.78rem;
}

.feed-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.55rem 0.6rem;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.12s, transform 0.08s;
}

.feed-row:hover {
  background: #f1f5f9;
  transform: translateX(2px);
}

.feed-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 6px rgba(15, 23, 42, 0.25);
}

.feed-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.feed-name {
  font-weight: 700;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.feed-level {
  font-size: 0.68rem;
  font-weight: 800;
}

.feed-level em {
  font-style: normal;
  color: #94a3b8;
  font-weight: 700;
}

.feed-ratio {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.7rem;
  color: #64748b;
  background: #f1f5f9;
  padding: 0.15rem 0.4rem;
  border-radius: 6px;
}

/* Transición de plegado */
.collapse-enter-active,
.collapse-leave-active {
  transition: max-height 0.32s ease, opacity 0.25s ease;
}

.collapse-enter-from,
.collapse-leave-to {
  max-height: 0;
  opacity: 0;
}

.collapse-enter-to,
.collapse-leave-from {
  max-height: var(--feed-max-h, 320px);
  opacity: 1;
}</style>
