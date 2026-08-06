<template>
  <transition name="modal">
    <div v-if="store.pendingReport" class="report-overlay" @click.self="store.clearPendingReport()">
      <div class="report-card">
        <div class="report-head">
          <h3>📍 Confirmar informe</h3>
          <button class="x-btn" @click="store.clearPendingReport()" aria-label="Cerrar">✕</button>
        </div>
        <p class="coords">
          {{ store.pendingReport.lat.toFixed(5) }}, {{ store.pendingReport.lng.toFixed(5) }}
        </p>

        <div class="type-grid">
          <button
            v-for="t in TYPES"
            :key="t.key"
            class="type-chip"
            :class="{ active: type === t.key }"
            @click="type = t.key"
          >
            <span class="type-ico">{{ t.icon }}</span>
            <span>{{ t.label }}</span>
          </button>
        </div>

        <textarea
          v-model="description"
          class="desc-input"
          rows="3"
          maxlength="500"
          placeholder="Describe qué está pasando (opcional)..."
        />

        <p v-if="error" class="form-error">{{ error }}</p>

        <div class="actions">
          <button class="btn cancel" @click="store.clearPendingReport()">Cancelar</button>
          <button class="btn send" :disabled="sending" @click="submit">
            {{ sending ? 'Enviando…' : 'Publicar informe' }}
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref } from 'vue'
import { useReportsStore } from '../stores/reportsStore'
import { useAuthStore } from '../stores/authStore'

const store = useReportsStore()
const auth = useAuthStore()

const TYPES = [
  { key: 'POLICE', icon: '👮', label: 'Policía' },
  { key: 'ACCIDENT', icon: '⚠️', label: 'Accidente' },
  { key: 'WORKS', icon: '🚧', label: 'Obras' },
  { key: 'CLOSURE', icon: '⛔', label: 'Cierre' },
  { key: 'HAZARD', icon: '☢️', label: 'Peligro' },
  { key: 'OTHER', icon: '🛈', label: 'Otro' }
]

const type = ref('OTHER')
const description = ref('')
const sending = ref(false)
const error = ref('')

async function submit() {
  const p = store.pendingReport
  if (!p) return
  sending.value = true
  error.value = ''
  try {
    await store.createReport({
      type: type.value,
      description: description.value.trim(),
      lat: p.lat,
      lng: p.lng
    })
    type.value = 'OTHER'
    description.value = ''
    store.clearPendingReport()
  } catch (e) {
    error.value = e.message
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.report-overlay {
  position: absolute;
  inset: 0;
  z-index: 40;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(3px);
  display: grid;
  place-items: center;
}

.report-card {
  width: min(400px, calc(100vw - 2rem));
  background: #fff;
  border-radius: 18px;
  padding: 1.1rem 1.15rem;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.35);
}

.report-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.15rem;
}

.report-head h3 {
  font-family: 'Baloo 2', sans-serif;
  font-size: 1.05rem;
  font-weight: 800;
  color: #1e293b;
}

.x-btn {
  border: none;
  background: #f1f5f9;
  color: #64748b;
  width: 1.8rem;
  height: 1.8rem;
  border-radius: 50%;
  cursor: pointer;
  font-size: 0.8rem;
  transition: background 0.15s, color 0.15s;
}

.x-btn:hover { background: #fee2e2; color: #b91c1c; }

.coords {
  font-size: 0.72rem;
  font-weight: 700;
  color: #64748b;
  font-family: monospace;
  margin-bottom: 0.7rem;
}

.type-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.45rem;
  margin-bottom: 0.7rem;
}

.type-chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.2rem;
  padding: 0.5rem 0.3rem;
  border-radius: 12px;
  border: 2px solid #e2e8f0;
  background: #f8fafc;
  font-size: 0.68rem;
  font-weight: 800;
  color: #475569;
  cursor: pointer;
  transition: all 0.15s;
}

.type-chip:hover { border-color: #cbd5e1; transform: translateY(-1px); }

.type-chip.active {
  border-color: #1a73e8;
  background: #eff6ff;
  color: #1a73e8;
  box-shadow: 0 4px 12px rgba(26, 115, 232, 0.2);
}

.type-ico { font-size: 1.15rem; line-height: 1; }

.desc-input {
  width: 100%;
  resize: vertical;
  border: 1.5px solid #e2e8f0;
  border-radius: 12px;
  padding: 0.55rem 0.7rem;
  font-family: inherit;
  font-size: 0.82rem;
  color: #1e293b;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.desc-input:focus {
  border-color: #1a73e8;
  box-shadow: 0 0 0 3px rgba(26, 115, 232, 0.15);
}

.form-error {
  margin-top: 0.5rem;
  color: #b91c1c;
  font-size: 0.75rem;
  font-weight: 700;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 0.9rem;
}

.btn {
  padding: 0.5rem 1rem;
  border-radius: 999px;
  border: none;
  font-family: inherit;
  font-size: 0.8rem;
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.08s, opacity 0.15s;
}

.btn:active { transform: scale(0.96); }

.btn.cancel {
  background: #f1f5f9;
  color: #475569;
}

.btn.cancel:hover { background: #e2e8f0; }

.btn.send {
  background: linear-gradient(135deg, #1a73e8, #1557c9);
  color: #fff;
  box-shadow: 0 6px 16px rgba(26, 115, 232, 0.35);
}

.btn.send:hover { opacity: 0.92; }

.btn.send:disabled { opacity: 0.5; cursor: default; }

.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
