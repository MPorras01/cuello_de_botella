<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <div class="modal" role="dialog" aria-modal="true" aria-label="Seguridad de la cuenta">
      <header>
        <div>
          <h2>🛡️ Seguridad de la cuenta</h2>
          <p class="sub">{{ auth.displayName || auth.username }}</p>
        </div>
        <button class="close" @click="$emit('close')" aria-label="Cerrar">✕</button>
      </header>

      <div class="body">
        <!-- Paso 1: estado + activar -->
        <template v-if="!enabled && !setup && !recoveryCodes">
          <div class="row">
            <div class="row-icon">🔐</div>
            <div>
              <p class="row-title">Autenticación de dos factores</p>
              <p class="row-text">
                Añade una capa extra de seguridad: además de tu contraseña, se pedirá un
                código de 6 dígitos de tu app de autenticación (Google Authenticator, Authy…).
              </p>
            </div>
          </div>
          <button class="primary" @click="startSetup" :disabled="loading">
            {{ loading ? 'Generando…' : 'Activar 2FA' }}
          </button>
          <p v-if="error" class="error">{{ error }}</p>
        </template>

        <!-- Paso 2: escanear QR + confirmar -->
        <template v-else-if="setup && !recoveryCodes">
          <h3>1 · Escanea el código QR</h3>
          <p class="step-hint">Ábrelo con tu app de autenticación o ingresa el secreto manualmente.</p>
          <div class="qr-wrap">
            <img v-if="qrDataUrl" :src="qrDataUrl" alt="Código QR del 2FA" class="qr" />
            <div v-else class="qr placeholder">Generando QR…</div>
          </div>
          <div class="secret-row">
            <code>{{ setup.secret }}</code>
            <button class="copy" @click="copy(setup.secret)">Copiar</button>
          </div>

          <h3>2 · Confirma el código</h3>
          <p class="step-hint">Ingresa el código que muestra tu app para activar el 2FA.</p>
          <div class="code-line">
            <input v-model="confirmCode" type="text" inputmode="numeric" maxlength="6" placeholder="000000" />
            <button class="primary" @click="confirmSetup" :disabled="loading || confirmCode.length < 6">
              {{ loading ? 'Activando…' : 'Activar' }}
            </button>
          </div>
          <button class="link" @click="cancelSetup">← Cancelar</button>
          <p v-if="error" class="error">{{ error }}</p>
        </template>

        <!-- Paso 3: códigos de respaldo -->
        <template v-else-if="recoveryCodes">
          <h3>🎉 ¡2FA activado!</h3>
          <p class="step-hint">
            Guarda estos <b>códigos de respaldo</b> en un lugar seguro. Cada uno se usa una sola
            vez para entrar si pierdes el acceso a tu app de autenticación.
          </p>
          <div class="codes">
            <span v-for="c in recoveryCodes" :key="c" class="code-chip">{{ c }}</span>
          </div>
          <button class="primary" @click="copy(recoveryCodes.join('\n'))">Copiar todos</button>
          <button class="link" @click="finish">Hecho ✓</button>
        </template>

        <!-- 2FA activo: desactivar -->
        <template v-else>
          <div class="row">
            <div class="row-icon">✅</div>
            <div>
              <p class="row-title">2FA activo</p>
              <p class="row-text">Tu cuenta está protegida con autenticación de dos factores.</p>
            </div>
          </div>
          <p class="step-hint">Para desactivarlo ingresa el código actual de tu autenticador:</p>
          <div class="code-line">
            <input v-model="confirmCode" type="text" inputmode="numeric" maxlength="6" placeholder="000000" />
            <button class="danger" @click="disable" :disabled="loading || confirmCode.length < 6">
              {{ loading ? 'Desactivando…' : 'Desactivar 2FA' }}
            </button>
          </div>
          <p v-if="error" class="error">{{ error }}</p>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import QRCode from 'qrcode'
import { useAuthStore } from '../stores/authStore'
import { authFetch } from '../services/api'

const emit = defineEmits(['close'])
const auth = useAuthStore()

const enabled = ref(false)
const setup = ref(null)
const recoveryCodes = ref(null)
const qrDataUrl = ref('')
const confirmCode = ref('')
const loading = ref(false)
const error = ref('')

async function loadStatus() {
  try {
    const res = await authFetch('/api/auth/2fa/status')
    const data = await res.json()
    enabled.value = data.enabled
  } catch { /* ignorar */ }
}

async function startSetup() {
  loading.value = true
  error.value = ''
  try {
    const res = await authFetch('/api/auth/2fa/setup', { method: 'POST' })
    const data = await res.json()
    if (!res.ok) throw new Error(data?.error ?? 'No se pudo iniciar el setup')
    setup.value = data
    qrDataUrl.value = await QRCode.toDataURL(data.otpauthUrl, { width: 220, margin: 1 })
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function confirmSetup() {
  loading.value = true
  error.value = ''
  try {
    const res = await authFetch('/api/auth/2fa/confirm', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: confirmCode.value.trim() })
    })
    const data = await res.json()
    if (!res.ok) throw new Error(data?.error ?? 'Código inválido')
    recoveryCodes.value = data.recoveryCodes
    enabled.value = true
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function disable() {
  loading.value = true
  error.value = ''
  try {
    const res = await authFetch('/api/auth/2fa/disable', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: confirmCode.value.trim() })
    })
    if (!res.ok) {
      const data = await res.json().catch(() => null)
      throw new Error(data?.error ?? 'No se pudo desactivar')
    }
    enabled.value = false
    confirmCode.value = ''
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function cancelSetup() {
  setup.value = null
  qrDataUrl.value = ''
  confirmCode.value = ''
}

function finish() {
  setup.value = null
  recoveryCodes.value = null
  qrDataUrl.value = ''
  confirmCode.value = ''
}

function copy(text) {
  navigator.clipboard?.writeText(text).catch(() => {})
}

onMounted(loadStatus)
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
  backdrop-filter: blur(3px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  z-index: 100;
}
.modal {
  width: 100%;
  max-width: 430px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 30px 80px rgba(15, 23, 42, 0.3);
  overflow: hidden;
}
header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.1rem 1.3rem;
  background: linear-gradient(135deg, #0f172a, #1e3a8a);
  color: #fff;
}
header h2 { font-family: 'Baloo 2', sans-serif; font-size: 1.05rem; font-weight: 800; }
.sub { font-size: 0.72rem; opacity: 0.75; font-weight: 700; margin-top: 0.15rem; }
.close {
  border: none;
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 0.85rem;
}
.body { padding: 1.3rem; display: flex; flex-direction: column; gap: 0.9rem; }

.row { display: flex; gap: 0.8rem; align-items: flex-start; }
.row-icon { font-size: 1.5rem; }
.row-title { font-weight: 800; font-size: 0.92rem; color: #1e293b; }
.row-text { font-size: 0.8rem; color: #64748b; line-height: 1.5; margin-top: 0.2rem; }

h3 { font-size: 0.88rem; font-weight: 800; color: #1e293b; margin-top: 0.2rem; }
.step-hint { font-size: 0.78rem; color: #64748b; line-height: 1.5; }

.qr-wrap { display: flex; justify-content: center; }
.qr { border: 1px solid #e2e8f0; border-radius: 12px; padding: 8px; background: #fff; }
.qr.placeholder {
  width: 220px; height: 220px;
  display: flex; align-items: center; justify-content: center;
  color: #94a3b8; font-size: 0.8rem; font-weight: 700;
  background: #f8fafc; border-radius: 12px;
}

.secret-row { display: flex; align-items: center; justify-content: center; gap: 0.5rem; }
.secret-row code {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.75rem;
  background: #f1f5f9;
  border-radius: 8px;
  padding: 0.35rem 0.55rem;
  letter-spacing: 0.08em;
  word-break: break-all;
}

.code-line { display: flex; gap: 0.5rem; }
.code-line input {
  flex: 1;
  padding: 0.6rem 0.8rem;
  border-radius: 10px;
  border: 1.5px solid #e2e8f0;
  font-size: 0.95rem;
  font-family: 'IBM Plex Mono', monospace;
  text-align: center;
  letter-spacing: 0.3em;
  outline: none;
}
.code-line input:focus { border-color: #1a73e8; box-shadow: 0 0 0 4px rgba(26, 115, 232, 0.15); }

.primary {
  border: none;
  border-radius: 12px;
  padding: 0.7rem;
  background: linear-gradient(135deg, #1a73e8, #1557c9);
  color: #fff;
  font-weight: 800;
  font-size: 0.9rem;
  font-family: inherit;
  cursor: pointer;
}
.primary:disabled { opacity: 0.55; cursor: not-allowed; }

.danger {
  border: none;
  border-radius: 12px;
  padding: 0.7rem 1rem;
  background: #ef4444;
  color: #fff;
  font-weight: 800;
  font-size: 0.85rem;
  font-family: inherit;
  cursor: pointer;
  white-space: nowrap;
}
.danger:disabled { opacity: 0.55; cursor: not-allowed; }

.link {
  border: none;
  background: none;
  color: #1a73e8;
  font-weight: 700;
  font-size: 0.8rem;
  font-family: inherit;
  cursor: pointer;
  align-self: flex-start;
}
.link:hover { text-decoration: underline; }

.codes { display: flex; flex-wrap: wrap; gap: 0.4rem; }
.code-chip {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.78rem;
  font-weight: 700;
  background: #eff6ff;
  color: #1d4ed8;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  padding: 0.35rem 0.6rem;
  letter-spacing: 0.05em;
}

.copy {
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  border-radius: 8px;
  padding: 0.3rem 0.6rem;
  font-size: 0.72rem;
  font-weight: 800;
  font-family: inherit;
  color: #475569;
  cursor: pointer;
  white-space: nowrap;
}

.error {
  color: #dc2626;
  font-size: 0.78rem;
  font-weight: 700;
  text-align: center;
  background: #fef2f2;
  border-radius: 10px;
  padding: 0.5rem;
}
</style>
