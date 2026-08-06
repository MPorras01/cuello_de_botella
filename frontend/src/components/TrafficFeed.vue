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
        <!-- Filtros -->
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
        </div>

        <ul v-if="filteredSegments.length > 0" class="feed-list">
          <li
            v-for="seg in filteredSegments"
            :key="seg.segmentId"
            class="feed-row"
            @click="store.requestFocus(seg.segmentId)"
            @mouseenter="hovered = seg.segmentId"
            @mouseleave="hovered = null"
          >
            <span class="feed-dot" :style="{ background: levelFromRatio(seg.speedRatio ?? 1).color }" />
            <div class="feed-info">
              <span class="feed-name">{{ seg.segmentName }}</span>
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
const hovered = ref(null)
const open = ref(true)
const filter = ref('todos')

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
  max-height: 320px;
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
  max-height: 320px;
  opacity: 1;
}</style>
