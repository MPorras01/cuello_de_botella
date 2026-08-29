<template>
  <div class="login-screen">
    <div class="login-card">
      <div class="brand">🚦</div>
      <h1>Trancones Medellín</h1>
      <p class="subtitle">
        {{ view === 'mfa' ? 'Verificación en dos pasos' : 'Acceso restringido — ingresa o crea tu cuenta' }}
      </p>

      <!-- ── Paso 2FA ─────────────────────────────────────────────────── -->
      <template v-if="view === 'mfa'">
        <div class="mfa-note">
          <span class="mfa-icon">🔐</span>
          Ingresa el código de 6 dígitos de tu app de autenticación
          (o un código de respaldo) para <b>{{ auth.mfaUsername }}</b>.
        </div>
        <label class="field">
          <span>Código de autenticación</span>
          <input
            v-model="mfaCode"
            type="text"
            inputmode="numeric"
            autocomplete="one-time-code"
            placeholder="000000"
            maxlength="10"
            required
          />
        </label>
        <button class="login-btn" @click="submitMfa" :disabled="loading">
          {{ loading ? 'Verificando…' : 'Verificar' }}
        </button>
        <button class="link-btn" @click="cancelMfa" :disabled="loading">← Volver a iniciar sesión</button>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
      </template>

      <!-- ── Formularios normales ─────────────────────────────────────── -->
      <template v-else>
        <div class="tabs" role="tablist">
          <button
            v-for="t in tabs"
            :key="t.id"
            class="tab"
            :class="{ active: tab === t.id }"
            :aria-selected="tab === t.id"
            role="tab"
            @click="switchTab(t.id)"
          >
            {{ t.icon }} {{ t.label }}
          </button>
        </div>

        <!-- Correo -->
        <form v-if="tab === 'email'" class="form" @submit.prevent="submitEmail">
          <template v-if="view === 'login'">
            <label class="field">
              <span>Correo o usuario</span>
              <input v-model="identifier" type="text" autocomplete="username" placeholder="tu@correo.com" required />
            </label>
            <label class="field">
              <span>Contraseña</span>
              <input v-model="password" type="password" autocomplete="current-password" placeholder="••••••••" required />
            </label>
            <button class="login-btn" type="submit" :disabled="loading">
              {{ loading ? 'Ingresando…' : 'Ingresar' }}
            </button>
            <button type="button" class="link-btn" @click="view = 'register'">¿No tienes cuenta? <b>Regístrate</b></button>
          </template>

          <template v-else>
            <label class="field">
              <span>Nombre</span>
              <input v-model="regName" type="text" autocomplete="name" placeholder="Tu nombre" required />
            </label>
            <label class="field">
              <span>Correo</span>
              <input v-model="regEmail" type="email" autocomplete="email" placeholder="tu@correo.com" required />
            </label>
            <label class="field">
              <span>Contraseña <small>(mínimo 8 caracteres)</small></span>
              <input v-model="regPassword" type="password" autocomplete="new-password" placeholder="••••••••" required />
            </label>
            <label class="field">
              <span>Confirmar contraseña</span>
              <input v-model="regPassword2" type="password" autocomplete="new-password" placeholder="••••••••" required />
            </label>
            <button class="login-btn" type="submit" :disabled="loading">
              {{ loading ? 'Creando…' : 'Crear cuenta' }}
            </button>
            <button type="button" class="link-btn" @click="view = 'login'">← Ya tengo cuenta</button>
          </template>
        </form>

        <!-- Teléfono -->
        <form v-else-if="tab === 'phone'" class="form" @submit.prevent="submitPhone">
          <template v-if="!phoneCodeSent">
            <label class="field">
              <span>Teléfono</span>
              <input v-model="phone" type="tel" autocomplete="tel" placeholder="+57 300 123 4567" required />
            </label>
            <button class="login-btn" type="submit" :disabled="loading">
              {{ loading ? 'Enviando…' : 'Enviar código' }}
            </button>
          </template>
          <template v-else>
            <div v-if="devCode" class="dev-code">
              Modo desarrollo — tu código es: <b>{{ devCode }}</b>
            </div>
            <label class="field">
              <span>Código de 6 dígitos</span>
              <input v-model="phoneCode" type="text" inputmode="numeric" autocomplete="one-time-code" placeholder="000000" maxlength="6" required />
            </label>
            <button class="login-btn" type="submit" :disabled="loading">
              {{ loading ? 'Verificando…' : 'Ingresar' }}
            </button>
            <button type="button" class="link-btn" @click="phoneCodeSent = false">← Cambiar teléfono</button>
          </template>
        </form>

        <!-- Google -->
        <div v-else class="form google-pane">
          <template v-if="googleAvailable === null">
            <p class="google-hint">Verificando disponibilidad…</p>
          </template>
          <template v-else-if="googleAvailable === false">
            <div class="google-unavailable">
              <span class="google-unavailable-icon">⚠️</span>
              <p class="google-unavailable-title">Google OAuth no disponible</p>
              <p class="google-unavailable-desc">
                El administrador debe configurar las credenciales de Google en el servidor.
              </p>
              <ol class="google-unavailable-steps">
                <li>Ve a <a href="https://console.cloud.google.com/apis/credentials" target="_blank">Google Cloud Console</a></li>
                <li>Crea un proyecto y habilita "Google+ API"</li>
                <li>Crea credenciales OAuth 2.0 (Client ID Web)</li>
                <li>Añade <code>http://localhost:8080/api/auth/google/callback</code> como URI autorizada</li>
                <li>Configura <code>GOOGLE_OAUTH_CLIENT_ID</code> y <code>GOOGLE_OAUTH_CLIENT_SECRET</code></li>
              </ol>
            </div>
          </template>
          <template v-else>
            <p class="google-hint">Entra con tu cuenta de Google en un solo clic.</p>
            <button class="google-btn" @click="loginGoogle" :disabled="loading">
              <svg viewBox="0 0 48 48" width="20" height="20" aria-hidden="true">
                <path fill="#FFC107" d="M43.6 20.1H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.9 1.2 8 3l5.7-5.7C34.1 6.1 29.3 4 24 4 13 4 4 13 4 24s9 20 20 20 20-9 20-20c0-1.3-.1-2.6-.4-3.9z"/>
                <path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.9 1.2 8 3l5.7-5.7C34.1 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z"/>
                <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z"/>
                <path fill="#1976D2" d="M43.6 20.1H42V20H24v8h11.3c-.8 2.3-2.3 4.3-4.1 5.7l6.2 5.2C36.9 39.2 44 34 44 24c0-1.3-.1-2.6-.4-3.9z"/>
              </svg>
              {{ loading ? 'Conectando…' : 'Continuar con Google' }}
            </button>
          </template>
        </div>

        <p v-if="auth.loginError" class="error" role="alert">{{ auth.loginError }}</p>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAuthStore } from '../stores/authStore'

const auth = useAuthStore()

const tabs = [
  { id: 'email', icon: '📧', label: 'Correo' },
  { id: 'phone', icon: '📱', label: 'Teléfono' },
  { id: 'google', icon: 'G', label: 'Google' }
]

const tab = ref('email')
const view = ref('login') // login | register | mfa
const loading = ref(false)
const error = ref('')

// Correo
const identifier = ref('')
const password = ref('')
const regName = ref('')
const regEmail = ref('')
const regPassword = ref('')
const regPassword2 = ref('')

// Teléfono
const phone = ref('')
const phoneCode = ref('')
const phoneCodeSent = ref(false)
const devCode = ref('')

// 2FA
const mfaCode = ref('')
const googleAvailable = ref(null) // null = checking, true/false = checked

const isMfa = computed(() => auth.pendingMfa)

async function checkGoogle() {
  googleAvailable.value = null
  try {
    const res = await fetch('/api/auth/google/url')
    googleAvailable.value = res.ok
    if (!res.ok && res.status === 503) {
      error.value = 'Google OAuth no está configurado en el servidor. Solicita al administrador que configure GOOGLE_OAUTH_CLIENT_ID y GOOGLE_OAUTH_CLIENT_SECRET en Google Cloud Console.'
    }
  } catch {
    googleAvailable.value = false
  }
}

function switchTab(id) {
  tab.value = id
  error.value = ''
  phoneCodeSent.value = false
  devCode.value = ''
  if (id === 'google') checkGoogle()
}

async function postJson(url, body) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })
  const data = await res.json().catch(() => null)
  if (!res.ok) throw new Error(data?.error ?? 'Error de autenticación')
  return data
}

function handleAuth(data) {
  if (data.requiresMfa) {
    auth.setMfaChallenge(data.mfaToken, data.username)
    view.value = 'mfa'
    return
  }
  auth.setAuth(data.token, data.username, data.displayName ?? '')
}

async function submitEmail() {
  loading.value = true
  error.value = ''
  try {
    if (view.value === 'login') {
      const data = await postJson('/api/auth/login', {
        username: identifier.value.trim(),
        password: password.value
      })
      handleAuth(data)
    } else {
      if (regPassword.value !== regPassword2.value) throw new Error('Las contraseñas no coinciden')
      const data = await postJson('/api/auth/register', {
        displayName: regName.value.trim(),
        email: regEmail.value.trim(),
        password: regPassword.value
      })
      handleAuth(data)
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function submitPhone() {
  loading.value = true
  error.value = ''
  try {
    if (!phoneCodeSent.value) {
      const data = await postJson('/api/auth/phone/request', { phone: phone.value.trim() })
      devCode.value = data.devCode ?? ''
      phoneCodeSent.value = true
    } else {
      const data = await postJson('/api/auth/phone/verify', {
        phone: phone.value.trim(),
        code: phoneCode.value
      })
      handleAuth(data)
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function loginGoogle() {
  loading.value = true
  error.value = ''
  try {
    const res = await fetch('/api/auth/google/url')
    const data = await res.json().catch(() => null)
    if (!res.ok) {
      if (res.status === 503) {
        throw new Error('Google OAuth no está configurado. Ve a Google Cloud Console, crea un proyecto, activa OAuth 2.0 y configura GOOGLE_OAUTH_CLIENT_ID y GOOGLE_OAUTH_CLIENT_SECRET en el backend.')
      }
      throw new Error(data?.error ?? 'No se pudo iniciar con Google')
    }
    window.location.href = data.url
  } catch (e) {
    error.value = e.message
    loading.value = false
  }
}

async function submitMfa() {
  loading.value = true
  error.value = ''
  try {
    const data = await postJson('/api/auth/2fa/verify', {
      mfaToken: auth.mfaToken,
      code: mfaCode.value.trim()
    })
    auth.setAuth(data.token, data.username, data.displayName ?? '')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function cancelMfa() {
  auth.clearMfa()
  view.value = 'login'
  mfaCode.value = ''
}
</script>

<style scoped>
.login-screen {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background:
    radial-gradient(1000px 500px at 85% -10%, rgba(59, 130, 246, 0.25) 0%, transparent 60%),
    radial-gradient(900px 500px at 0% 110%, rgba(34, 197, 94, 0.18) 0%, transparent 55%),
    linear-gradient(160deg, #eaf4ff 0%, #f2f6fb 45%, #eefbf3 100%);
}

.login-card {
  width: 100%;
  max-width: 400px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 22px;
  padding: 2rem 1.8rem;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.14);
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

.brand { font-size: 2.6rem; text-align: center; }
h1 {
  font-family: 'Baloo 2', sans-serif;
  font-size: 1.35rem;
  font-weight: 800;
  text-align: center;
  color: #1e293b;
}
.subtitle {
  font-size: 0.82rem;
  font-weight: 600;
  text-align: center;
  color: #64748b;
  margin-bottom: 0.3rem;
}

/* Pestañas */
.tabs {
  display: flex;
  gap: 0.35rem;
  background: #f1f5f9;
  border-radius: 12px;
  padding: 0.3rem;
}
.tab {
  flex: 1;
  border: none;
  background: transparent;
  padding: 0.5rem 0.2rem;
  border-radius: 9px;
  font-size: 0.78rem;
  font-weight: 800;
  font-family: inherit;
  color: #64748b;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.tab.active {
  background: #ffffff;
  color: #1a73e8;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.1);
}

.form { display: flex; flex-direction: column; gap: 0.85rem; }

.field { display: flex; flex-direction: column; gap: 0.35rem; }
.field span { font-size: 0.78rem; font-weight: 800; color: #475569; }
.field small { font-weight: 600; color: #94a3b8; }
.field input {
  padding: 0.65rem 0.85rem;
  border-radius: 12px;
  border: 1.5px solid #e2e8f0;
  background: #f8fafc;
  color: #1e293b;
  font-size: 0.95rem;
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}
.field input:focus {
  border-color: #1a73e8;
  background: #ffffff;
  box-shadow: 0 0 0 4px rgba(26, 115, 232, 0.15);
}

.login-btn {
  padding: 0.72rem;
  border-radius: 14px;
  border: none;
  background: linear-gradient(135deg, #1a73e8 0%, #1557c9 100%);
  color: #fff;
  font-size: 0.95rem;
  font-weight: 800;
  font-family: inherit;
  cursor: pointer;
  box-shadow: 0 8px 22px rgba(26, 115, 232, 0.35);
  transition: transform 0.1s, box-shadow 0.15s;
}
.login-btn:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 12px 28px rgba(26, 115, 232, 0.4); }
.login-btn:active:not(:disabled) { transform: scale(0.98); }
.login-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.link-btn {
  border: none;
  background: transparent;
  color: #1a73e8;
  font-size: 0.8rem;
  font-weight: 700;
  font-family: inherit;
  cursor: pointer;
  padding: 0.2rem;
}
.link-btn:hover { text-decoration: underline; }

.error {
  color: #dc2626;
  font-size: 0.78rem;
  font-weight: 600;
  text-align: left;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 10px;
  padding: 0.6rem 0.8rem;
  line-height: 1.5;
  word-wrap: break-word;
}

/* Google */
.google-hint { font-size: 0.82rem; font-weight: 600; color: #64748b; text-align: center; }
.google-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.6rem;
  padding: 0.72rem;
  border-radius: 14px;
  border: 1.5px solid #e2e8f0;
  background: #ffffff;
  color: #1e293b;
  font-size: 0.92rem;
  font-weight: 800;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s, box-shadow 0.15s, border-color 0.15s;
}
.google-btn:hover:not(:disabled) {
  background: #f8fafc;
  border-color: #d1d5db;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.1);
}
.google-btn:disabled { opacity: 0.6; cursor: not-allowed; }

/* Teléfono / dev code */
.dev-code {
  background: #fefce8;
  border: 1px solid #fde68a;
  color: #854d0e;
  font-size: 0.8rem;
  font-weight: 700;
  border-radius: 10px;
  padding: 0.55rem;
  text-align: center;
}

/* Google no disponible */
.google-unavailable {
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 14px;
  padding: 1.2rem;
  text-align: left;
}
.google-unavailable-icon { font-size: 1.8rem; display: block; text-align: center; margin-bottom: 0.5rem; }
.google-unavailable-title {
  font-size: 0.9rem;
  font-weight: 800;
  color: #92400e;
  text-align: center;
  margin-bottom: 0.3rem;
}
.google-unavailable-desc {
  font-size: 0.78rem;
  font-weight: 600;
  color: #78716c;
  text-align: center;
  margin-bottom: 0.8rem;
}
.google-unavailable-steps {
  font-size: 0.75rem;
  font-weight: 600;
  color: #57534e;
  margin: 0;
  padding-left: 1.2rem;
  line-height: 1.8;
}
.google-unavailable-steps code {
  background: #fef3c7;
  padding: 0.1rem 0.3rem;
  border-radius: 4px;
  font-size: 0.7rem;
  word-break: break-all;
}
.google-unavailable-steps a {
  color: #2563eb;
  text-decoration: underline;
}

/* 2FA */
.mfa-note {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1d4ed8;
  font-size: 0.82rem;
  font-weight: 600;
  border-radius: 12px;
  padding: 0.7rem;
  line-height: 1.5;
  display: flex;
  gap: 0.5rem;
  align-items: flex-start;
}
.mfa-icon { font-size: 1.2rem; }
</style>
