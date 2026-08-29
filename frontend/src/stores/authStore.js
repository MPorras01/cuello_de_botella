import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const TOKEN_KEY = 'trancones_token'
const USER_KEY = 'trancones_user'
const NAME_KEY = 'trancones_display_name'

/**
 * Store de autenticación. Persiste el token JWT en localStorage y mantiene
 * el estado del desafío de segundo factor (2FA) pendiente.
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) ?? '')
  const username = ref(localStorage.getItem(USER_KEY) ?? '')
  const displayName = ref(localStorage.getItem(NAME_KEY) ?? '')

  // Desafío 2FA pendiente (primer factor OK, falta el código del autenticador)
  const mfaToken = ref('')
  const mfaUsername = ref('')
  const loginError = ref('')

  const isAuthenticated = computed(() => !!token.value)
  const pendingMfa = computed(() => !!mfaToken.value)

  function setAuth(newToken, newUsername, newDisplayName = '') {
    token.value = newToken
    username.value = newUsername
    displayName.value = newDisplayName
    localStorage.setItem(TOKEN_KEY, newToken)
    localStorage.setItem(USER_KEY, newUsername)
    localStorage.setItem(NAME_KEY, newDisplayName)
    clearMfa()
    loginError.value = ''
  }

  /** Guarda el desafío 2FA entregado por el login (o el redirect de Google). */
  function setMfaChallenge(newMfaToken, newUsername) {
    mfaToken.value = newMfaToken
    mfaUsername.value = newUsername
    loginError.value = ''
  }

  function clearMfa() {
    mfaToken.value = ''
    mfaUsername.value = ''
  }

  function setLoginError(message) {
    loginError.value = message
  }

  function logout() {
    token.value = ''
    username.value = ''
    displayName.value = ''
    clearMfa()
    loginError.value = ''
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(NAME_KEY)
  }

  return {
    token, username, displayName,
    mfaToken, mfaUsername, loginError,
    isAuthenticated, pendingMfa,
    setAuth, setMfaChallenge, clearMfa, setLoginError, logout
  }
})
