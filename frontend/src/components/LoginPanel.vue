<template>
  <div class="login-screen">
    <form class="login-card" @submit.prevent="submit">
      <div class="brand">🚦</div>
      <h1>Trancones Medellín</h1>
      <p class="subtitle">Acceso restringido — ingresa tus credenciales</p>

      <label class="field">
        <span>Usuario</span>
        <input
          v-model="username"
          type="text"
          autocomplete="username"
          placeholder="admin"
          required
        />
      </label>

      <label class="field">
        <span>Contraseña</span>
        <input
          v-model="password"
          type="password"
          autocomplete="current-password"
          placeholder="••••••••"
          required
        />
      </label>

      <button class="login-btn" type="submit" :disabled="loading">
        {{ loading ? 'Ingresando…' : 'Ingresar' }}
      </button>

      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useAuthStore } from '../stores/authStore'

const auth = useAuthStore()
const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  loading.value = true
  error.value = ''
  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.value, password: password.value })
    })
    const data = await res.json().catch(() => null)
    if (!res.ok) {
      throw new Error(data?.error ?? 'Error de autenticación')
    }
    auth.setAuth(data.token, data.username)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
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
  max-width: 390px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 22px;
  padding: 2.2rem 2rem;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.14);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.brand {
  font-size: 2.6rem;
  text-align: center;
}

h1 {
  font-family: 'Baloo 2', sans-serif;
  font-size: 1.4rem;
  font-weight: 800;
  text-align: center;
  color: #1e293b;
}

.subtitle {
  font-size: 0.85rem;
  font-weight: 600;
  text-align: center;
  color: #64748b;
  margin-bottom: 0.5rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.field span {
  font-size: 0.78rem;
  font-weight: 800;
  color: #475569;
}

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
  margin-top: 0.5rem;
  padding: 0.75rem;
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

.login-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(26, 115, 232, 0.4);
}

.login-btn:active:not(:disabled) {
  transform: scale(0.98);
}

.login-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error {
  color: #dc2626;
  font-size: 0.8rem;
  font-weight: 700;
  text-align: center;
  background: #fef2f2;
  border-radius: 10px;
  padding: 0.5rem;
}
</style>
