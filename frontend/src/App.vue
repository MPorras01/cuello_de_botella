<template>
  <LoginPanel v-if="!auth.isAuthenticated" />
  <template v-else>
    <header>
      <div class="brand">
        <span class="logo">🚦</span>
        <div class="titles">
          <h1>Trancones Medellín</h1>
          <p>Área Metropolitana del Valle de Aburrá · Antioquia</p>
        </div>
      </div>
      <div class="status" :class="store.connected ? 'live' : 'down'">
        <span class="dot" />
        {{ store.connected ? 'EN VIVO' : 'SIN SEÑAL' }}
        <span v-if="store.connected && store.lastUpdate" class="status-time" title="Última actualización del snapshot">
          · {{ fmtTime(store.lastUpdate) }}
        </span>
      </div>
      <button class="logout-btn" @click="logout">Salir · {{ auth.username }}</button>
    </header>
    <main>
      <MapView />
    </main>
  </template>
</template>

<script setup>
import { watch, onUnmounted } from 'vue'
import { useTrafficStore } from './stores/trafficStore'
import { useAuthStore } from './stores/authStore'
import MapView from './components/MapView.vue'
import LoginPanel from './components/LoginPanel.vue'
import { connectSSE, disconnectSSE } from './services/trafficSSE'

const store = useTrafficStore()
const auth = useAuthStore()

// Conectar el stream SSE solo cuando hay sesión activa
watch(
  () => auth.token,
  (token) => {
    if (token) connectSSE()
    else disconnectSSE()
  },
  { immediate: true }
)

function logout() {
  auth.logout()
}

/** Formatea un timestamp como HH:MM:SS local. */
function fmtTime(ts) {
  return new Date(ts).toLocaleTimeString('es-CO', {
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
  })
}

onUnmounted(disconnectSSE)
</script>

<style>
:root {
  --bg: #f2f6fb;
  --panel: #ffffff;
  --border: #e2e8f0;
  --muted: #64748b;
  --text: #1e293b;
  --accent: #1a73e8;
}

* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  font-family: 'Nunito', system-ui, sans-serif;
  background: var(--bg);
  color: var(--text);
}

header {
  display: flex;
  align-items: center;
  gap: 1rem;
  height: 3.75rem;
  padding: 0 1.25rem;
  background: #ffffff;
  border-bottom: 1px solid var(--border);
  box-shadow: 0 1px 6px rgba(15, 23, 42, 0.06);
  position: relative;
  z-index: 30;
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  min-width: 0;
}

.logo {
  font-size: 1.5rem;
}

.titles { display: flex; flex-direction: column; line-height: 1.15; min-width: 0; }

h1 {
  font-family: 'Baloo 2', sans-serif;
  font-size: 1.08rem;
  font-weight: 800;
  color: var(--text);
  letter-spacing: 0.005em;
  white-space: nowrap;
}

.titles p {
  font-size: 0.64rem;
  font-weight: 700;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.status {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.32rem 0.8rem;
  border-radius: 999px;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.1em;
}

.status.live {
  background: #dcfce7;
  color: #15803d;
}

.status.down {
  background: #fee2e2;
  color: #b91c1c;
}

.status .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-time {
  font-weight: 800;
  letter-spacing: 0;
  opacity: 0.8;
}

.status.live .dot { background: #22c55e; box-shadow: 0 0 6px rgba(34, 197, 94, 0.8); }
.status.down .dot { background: #ef4444; box-shadow: 0 0 6px rgba(239, 68, 68, 0.8); }

.logout-btn {
  padding: 0.4rem 0.85rem;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: #f8fafc;
  color: #475569;
  font-size: 0.76rem;
  font-weight: 700;
  font-family: inherit;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}

.logout-btn:hover {
  border-color: #f87171;
  color: #b91c1c;
  background: #fff1f2;
}

@media (max-width: 560px) {
  .titles p, .status { display: none; }
}
</style>
