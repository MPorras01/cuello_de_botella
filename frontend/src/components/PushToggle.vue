<template>
  <div class="push-toggle">
    <button
      :class="['push-btn', subscribed ? 'active' : '']"
      :disabled="loading"
      @click="toggle"
      :aria-pressed="subscribed"
    >
      {{ loading ? 'Procesando…' : subscribed ? '🔔 Desactivar alertas' : '🔕 Activar alertas' }}
    </button>
    <p v-if="mensaje" class="push-msg" :class="{ error: esError }">{{ mensaje }}</p>
  </div>
</template>

<script setup>
/**
 * PushToggle.vue — botón para suscribir/desuscribir notificaciones push VAPID.
 * Requisitos: 8.1, 8.2, 8.3, 8.4
 */
import { ref, onMounted } from 'vue'
import { subscribe, unsubscribe } from '../services/pushService'

const subscribed = ref(false)
const loading = ref(false)
const mensaje = ref('')
const esError = ref(false)

/** Verifica si ya hay una suscripción activa al montar el componente. */
onMounted(async () => {
  if (!('serviceWorker' in navigator) || !('PushManager' in window)) return
  try {
    const reg = await navigator.serviceWorker.ready
    const sub = await reg.pushManager.getSubscription()
    subscribed.value = !!sub
  } catch {
    // silencioso
  }
})

async function toggle() {
  loading.value = true
  mensaje.value = ''
  esError.value = false

  try {
    if (subscribed.value) {
      await unsubscribe()
      subscribed.value = false
      mensaje.value = 'Alertas desactivadas correctamente.'
    } else {
      await subscribe()
      subscribed.value = true
      mensaje.value = 'Alertas activadas. Recibirás notificaciones de trancones severos.'
    }
  } catch (err) {
    esError.value = true
    if (err.message === 'PERMISSION_DENIED') {
      // Requisito 8.4
      mensaje.value = 'Permiso de notificaciones denegado. Actívalo en la configuración del navegador.'
    } else {
      mensaje.value = 'Error al gestionar las alertas. Intenta de nuevo.'
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.push-toggle {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.4rem;
  padding: 0.5rem 1rem;
}

.push-btn {
  padding: 0.4rem 1.1rem;
  border-radius: 6px;
  border: 1px solid #475569;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  font-size: 0.85rem;
  transition: all 0.15s;
}

.push-btn.active {
  background: #16a34a;
  border-color: #16a34a;
  color: #fff;
}

.push-btn:hover:not(:disabled) {
  border-color: #3b82f6;
  color: #3b82f6;
}

.push-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.push-msg {
  font-size: 0.78rem;
  color: #94a3b8;
}

.push-msg.error {
  color: #f87171;
}
</style>
